package com.linernotes.app.presentation.booklet

import android.bluetooth.BluetoothDevice
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.data.local.entity.TrackEntity
import com.linernotes.app.data.remote.TranslationService
import com.linernotes.app.domain.model.BilingualLyricLine
import com.linernotes.app.domain.model.LyricDisplayMode
import com.linernotes.app.domain.repository.AlbumRepository
import com.linernotes.app.presentation.booklet.model.BookletUiState
import com.linernotes.app.core.translation.BatchTranslationManager
import com.linernotes.app.core.translation.BatchTranslationState
import com.linernotes.app.core.util.ChineseConverter
import com.linernotes.app.core.bluetooth.CdConnectionState
import com.linernotes.app.core.bluetooth.ShanlingBluetoothManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LyricBookletViewModel @Inject constructor(
    private val repository: AlbumRepository,
    val aiPreferences: AiPreferences,
    private val translationService: TranslationService,
    private val batchTranslationManager: BatchTranslationManager,
    val shanlingBluetoothManager: ShanlingBluetoothManager
) : ViewModel() {

    private var currentAlbumId: String = ""

    private val _uiState = MutableStateFlow(BookletUiState())
    val uiState: StateFlow<BookletUiState> = _uiState.asStateFlow()

    val batchTranslationState: StateFlow<BatchTranslationState> = batchTranslationManager.state

    init {
        viewModelScope.launch {
            batchTranslationManager.state.collect { bState ->
                if (!bState.userMessage.isNullOrBlank() && bState.albumId == currentAlbumId) {
                    _uiState.update { it.copy(userMessage = bState.userMessage) }
                }
            }
        }
        viewModelScope.launch {
            shanlingBluetoothManager.cdState.collect { cdState ->
                _uiState.update {
                    it.copy(
                        cdConnectionState = cdState.connectionState,
                        cdDeviceName = cdState.deviceName,
                        cdTotalTracks = cdState.totalTracks,
                        cdCurrentTrackNumber = cdState.currentTrackNumber,
                        userMessage = cdState.errorMessage ?: it.userMessage
                    )
                }
                if (cdState.connectionState == CdConnectionState.CONNECTED) {
                    onExternalCdStateReceived(
                        trackNo = cdState.currentTrackNumber,
                        posMs = cdState.currentPositionMs,
                        isPlaying = cdState.isPlaying
                    )
                }
            }
        }
    }

    val allShelfAlbums: StateFlow<List<com.linernotes.app.data.local.entity.AlbumEntity>> = repository.getCollectionStream()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    fun setAlbumId(id: String) {
        if (currentAlbumId != id) {
            currentAlbumId = id
            loadBooklet(id)
        }
    }

    private fun loadBooklet(id: String) {
        viewModelScope.launch {
            repository.getAlbumBookletStream(id).collect { albumWithTracks ->
                if (albumWithTracks != null) {
                    val tracks = albumWithTracks.tracks.sortedBy { it.trackNumber }
                    val safeIndex = _uiState.value.currentTrackIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
                    val currentTrack = tracks.getOrNull(safeIndex)

                    val aligned = LyricAligner.align(
                        currentTrack?.originalLyrics,
                        currentTrack?.translatedLyrics
                    )
                    val duration = computeTrackDuration(currentTrack, aligned)

                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            albumWithTracks = albumWithTracks.copy(tracks = tracks),
                            currentTrackIndex = safeIndex,
                            alignedLyrics = aligned,
                            trackDurationMs = duration
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun setDisplayMode(mode: LyricDisplayMode) {
        _uiState.update { it.copy(displayMode = mode) }
    }

    private fun computeTrackDuration(track: TrackEntity?, aligned: List<BilingualLyricLine>): Long {
        val dbDuration = track?.durationMs ?: 0L
        if (dbDuration > 0L) return dbDuration
        val lastTimed = aligned.lastOrNull { it.startTimeMs != null }?.startTimeMs ?: 0L
        return if (lastTimed > 0L) lastTimed + 8000L else 180_000L
    }

    private fun findActiveLineIndex(lyrics: List<BilingualLyricLine>, posMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        var lastIdx = -1
        for (i in lyrics.indices) {
            val t = lyrics[i].startTimeMs
            if (t != null && t <= posMs) {
                lastIdx = i
            }
        }
        return lastIdx
    }

    fun selectTrack(index: Int, notifyCdPlayer: Boolean = true) {
        val tracks = _uiState.value.albumWithTracks?.tracks ?: return
        if (index in tracks.indices) {
            val track = tracks[index]
            val aligned = LyricAligner.align(track.originalLyrics, track.translatedLyrics)
            val duration = computeTrackDuration(track, aligned)
            _uiState.update {
                it.copy(
                    currentTrackIndex = index,
                    alignedLyrics = aligned,
                    currentPositionMs = 0L,
                    trackDurationMs = duration,
                    activeLineIndex = -1
                )
            }
            if (notifyCdPlayer && _uiState.value.cdConnectionState == CdConnectionState.CONNECTED) {
                // Shanling SL_CD_PLAY_REQ takes 0-based queue index (0 for 1st song)
                val queueIndex = (track.trackNumber - 1).coerceAtLeast(0)
                shanlingBluetoothManager.playTrack(queueIndex)
            }
        }
    }

    private var companionJob: kotlinx.coroutines.Job? = null

    fun toggleCompanionPlay() {
        val willPlay = !_uiState.value.isCompanionPlaying
        if (willPlay) {
            startCompanionInternal()
            if (_uiState.value.cdConnectionState == CdConnectionState.CONNECTED) {
                shanlingBluetoothManager.play()
            }
        } else {
            pauseCompanionInternal()
            if (_uiState.value.cdConnectionState == CdConnectionState.CONNECTED) {
                shanlingBluetoothManager.pause()
            }
        }
    }

    fun startCompanion() {
        startCompanionInternal()
        if (_uiState.value.cdConnectionState == CdConnectionState.CONNECTED) {
            shanlingBluetoothManager.play()
        }
    }

    private fun startCompanionInternal() {
        companionJob?.cancel()
        _uiState.update { it.copy(isCompanionPlaying = true) }
        companionJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(100L)
                val currentState = _uiState.value
                if (!currentState.isCompanionPlaying) break

                val currentPos = currentState.currentPositionMs + 100L
                val duration = currentState.trackDurationMs.coerceAtLeast(10_000L)

                if (currentPos >= duration) {
                    val tracks = currentState.albumWithTracks?.tracks ?: emptyList()
                    val nextIndex = currentState.currentTrackIndex + 1
                    if (nextIndex in tracks.indices) {
                        selectTrack(nextIndex, notifyCdPlayer = false)
                        continue
                    } else {
                        _uiState.update {
                            it.copy(
                                isCompanionPlaying = false,
                                currentPositionMs = 0L,
                                activeLineIndex = -1
                            )
                        }
                        break
                    }
                } else {
                    val activeIdx = findActiveLineIndex(currentState.alignedLyrics, currentPos)
                    _uiState.update {
                        it.copy(
                            currentPositionMs = currentPos,
                            activeLineIndex = activeIdx
                        )
                    }
                }
            }
        }
    }

    fun pauseCompanion() {
        if (_uiState.value.cdConnectionState == CdConnectionState.CONNECTED) {
            shanlingBluetoothManager.pause()
        }
        pauseCompanionInternal()
    }

    private fun pauseCompanionInternal() {
        companionJob?.cancel()
        companionJob = null
        _uiState.update { it.copy(isCompanionPlaying = false) }
    }

    fun seekCompanion(targetMs: Long, notifyCdPlayer: Boolean = true) {
        val duration = _uiState.value.trackDurationMs.coerceAtLeast(1000L)
        val clamped = targetMs.coerceIn(0L, duration)
        val activeIdx = findActiveLineIndex(_uiState.value.alignedLyrics, clamped)
        _uiState.update {
            it.copy(
                currentPositionMs = clamped,
                activeLineIndex = activeIdx
            )
        }
        if (notifyCdPlayer && _uiState.value.cdConnectionState == CdConnectionState.CONNECTED) {
            shanlingBluetoothManager.seekTo((clamped / 1000).toInt())
        }
    }

    fun adjustCompanionOffset(deltaMs: Long) {
        seekCompanion(_uiState.value.currentPositionMs + deltaMs)
    }

    fun onLyricLineClicked(line: BilingualLyricLine) {
        if (line.startTimeMs != null) {
            seekCompanion(line.startTimeMs)
        }
    }

    fun toggleCalibrationBar() {
        _uiState.update { it.copy(showCalibrationBar = !it.showCalibrationBar) }
    }

    fun onExternalCdStateReceived(trackNo: Int, posMs: Long, isPlaying: Boolean) {
        val tracks = _uiState.value.albumWithTracks?.tracks ?: return
        val targetIndex = resolveTrackIndex(tracks, trackNo)
        if (targetIndex != -1 && targetIndex != _uiState.value.currentTrackIndex) {
            selectTrack(targetIndex, notifyCdPlayer = false)
        }
        if (posMs > 0L) {
            seekCompanion(posMs, notifyCdPlayer = false)
        }
        if (isPlaying && !_uiState.value.isCompanionPlaying) {
            startCompanionInternal()
        } else if (!isPlaying && _uiState.value.isCompanionPlaying) {
            pauseCompanionInternal()
        }
    }

    private fun resolveTrackIndex(tracks: List<TrackEntity>, cdTrackNo: Int): Int {
        if (tracks.isEmpty()) return -1

        // 1. 精确匹配 trackNumber（例如实体 CD 标号 1..N）
        val exactMatch = tracks.indexOfFirst { it.trackNumber == cdTrackNo }
        if (exactMatch != -1) return exactMatch

        // 2. 0-based 队列索引对齐（CD 上报 0 对应本地 trackNumber == 1）
        val zeroBasedMatch = tracks.indexOfFirst { it.trackNumber == cdTrackNo + 1 }
        if (zeroBasedMatch != -1) return zeroBasedMatch

        // 3. 容错边界兜底
        if (cdTrackNo in tracks.indices) return cdTrackNo
        if (cdTrackNo - 1 in tracks.indices) return cdTrackNo - 1

        return 0
    }

    fun openCdTracklist(isOpen: Boolean) {
        _uiState.update { it.copy(isCdTracklistOpen = isOpen) }
    }

    fun openCdMatchAlbum(isOpen: Boolean) {
        _uiState.update { it.copy(isCdMatchAlbumOpen = isOpen) }
    }

    fun playCdTrack(trackNumberOrIndex: Int) {
        val tracks = _uiState.value.albumWithTracks?.tracks ?: emptyList()
        val targetIdx = resolveTrackIndex(tracks, trackNumberOrIndex)
        if (targetIdx in tracks.indices) {
            selectTrack(targetIdx, notifyCdPlayer = true)
        } else {
            val cdQueueIdx = (trackNumberOrIndex - 1).coerceAtLeast(0)
            shanlingBluetoothManager.playTrack(cdQueueIdx)
        }
        startCompanionInternal()
    }

    fun switchAlbum(albumId: String) {
        if (currentAlbumId != albumId) {
            currentAlbumId = albumId
            loadBooklet(albumId)
            _uiState.update { it.copy(isCdTracklistOpen = false, isCdMatchAlbumOpen = false) }
        }
    }

    fun saveAndBindMatchedAlbum(album: com.linernotes.app.data.local.entity.AlbumEntity, tracks: List<TrackEntity>) {
        viewModelScope.launch {
            repository.saveAlbum(album, tracks)
            switchAlbum(album.id)
            _uiState.update {
                it.copy(
                    isCdMatchAlbumOpen = false,
                    isCdTracklistOpen = false,
                    userMessage = "已成功为当前 CD 匹配唱片《${album.title}》"
                )
            }
            batchFetchOfficialLyricsAlbum()
        }
    }

    fun openCdSheet(isOpen: Boolean) {
        _uiState.update { it.copy(isCdSheetOpen = isOpen) }
    }

    fun getPairedBluetoothDevices(): List<BluetoothDevice> {
        return shanlingBluetoothManager.getAllPairedDevices()
    }

    fun connectCdPlayer(device: BluetoothDevice? = null) {
        shanlingBluetoothManager.connectToDevice(device)
    }

    fun disconnectCdPlayer() {
        shanlingBluetoothManager.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        companionJob?.cancel()
        shanlingBluetoothManager.disconnect()
    }

    fun previousTrack() {
        if (_uiState.value.cdConnectionState == CdConnectionState.CONNECTED) {
            shanlingBluetoothManager.previous()
        }
        selectTrack(_uiState.value.currentTrackIndex - 1, notifyCdPlayer = false)
    }

    fun nextTrack() {
        if (_uiState.value.cdConnectionState == CdConnectionState.CONNECTED) {
            shanlingBluetoothManager.next()
        }
        selectTrack(_uiState.value.currentTrackIndex + 1, notifyCdPlayer = false)
    }

    fun updateAmbientColor(color: Color) {
        _uiState.update { it.copy(ambientCoverColor = color) }
    }

    fun openEditSheet(isOpen: Boolean) {
        _uiState.update { it.copy(isEditingSheetOpen = isOpen) }
    }

    fun openSettings(isOpen: Boolean) {
        _uiState.update { it.copy(isSettingsOpen = isOpen) }
    }

    fun saveManualEdits(trackId: Long, newTitleZh: String?, newOriginal: String?, newTranslated: String?) {
        viewModelScope.launch {
            repository.updateTrackTranslation(
                trackId = trackId,
                translatedTitle = newTitleZh,
                originalLyrics = newOriginal,
                translatedLyrics = newTranslated
            )
            _uiState.update { it.copy(isEditingSheetOpen = false, userMessage = "校对已成功保存至本地") }
        }
    }

    fun setTranslateMenuOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isTranslateMenuOpen = isOpen) }
    }

    fun onAiTranslateClicked() {
        _uiState.update { it.copy(isTranslateMenuOpen = true) }
    }

    fun fetchOfficialLyricsCurrentTrack() {
        _uiState.update { it.copy(isTranslateMenuOpen = false) }
        val currentTrack = getCurrentTrack() ?: return
        val artist = _uiState.value.albumWithTracks?.album?.artist ?: ""

        viewModelScope.launch {
            _uiState.update { it.copy(isTranslating = true, userMessage = "正在检索多源官方歌词...") }
            try {
                val result = com.linernotes.app.data.remote.UnifiedLyricsService.fetchLyrics(
                    trackTitle = currentTrack.title,
                    artistName = artist,
                    sourcePref = aiPreferences.lyricsSource
                )
                if (result != null && result.originalLyrics.isNotBlank()) {
                    repository.updateTrackTranslation(
                        trackId = currentTrack.id,
                        translatedTitle = currentTrack.translatedTitle,
                        originalLyrics = result.originalLyrics,
                        translatedLyrics = result.translatedLyrics
                    )

                    // 若官方库仅检索到原版（无官方译文），且启用了智能回退，则自动调用翻译服务补全翻译
                    if (!result.isBilingual && aiPreferences.lyricsSource == AiPreferences.LyricsSourcePreference.AUTO_FIRST.code) {
                        _uiState.update { it.copy(userMessage = "已匹配官方原版歌词，正在自动翻译...") }
                        val transResult = translationService.translateTrack(
                            trackTitle = currentTrack.title,
                            originalLyrics = result.originalLyrics
                        )
                        repository.updateTrackTranslation(
                            trackId = currentTrack.id,
                            translatedTitle = transResult.translatedTitle ?: currentTrack.translatedTitle,
                            originalLyrics = result.originalLyrics,
                            translatedLyrics = transResult.translatedLyrics
                        )
                        _uiState.update { it.copy(isTranslating = false, userMessage = "官方原版歌词已入库，翻译已同步补全！") }
                    } else {
                        val msg = if (result.isBilingual) "官方双语歌词已匹配并同步入库！" else "已检索到官方原版歌词（暂无官方译文）"
                        _uiState.update { it.copy(isTranslating = false, userMessage = msg) }
                    }
                } else {
                    // 若官方库未搜到且启用了智能回退，则尝试翻译当前已有歌词
                    if (aiPreferences.lyricsSource == AiPreferences.LyricsSourcePreference.AUTO_FIRST.code && !currentTrack.originalLyrics.isNullOrBlank()) {
                        _uiState.update { it.copy(userMessage = "在线歌词库未检索到，正在自动回退机器翻译...") }
                        val transResult = translationService.translateTrack(
                            trackTitle = currentTrack.title,
                            originalLyrics = currentTrack.originalLyrics
                        )
                        repository.updateTrackTranslation(
                            trackId = currentTrack.id,
                            translatedTitle = transResult.translatedTitle ?: currentTrack.translatedTitle,
                            originalLyrics = currentTrack.originalLyrics,
                            translatedLyrics = transResult.translatedLyrics
                        )
                        _uiState.update { it.copy(isTranslating = false, userMessage = "翻译完成并已保存！") }
                    } else {
                        _uiState.update { it.copy(isTranslating = false, userMessage = "未检索到该歌曲歌词，可尝试切换歌词源或手动添加") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isTranslating = false, userMessage = "匹配歌词遇到问题: ${e.message}") }
            }
        }
    }

    fun batchFetchOfficialLyricsAlbum() {
        _uiState.update { it.copy(isTranslateMenuOpen = false) }
        val tracks = _uiState.value.albumWithTracks?.tracks ?: return
        val artist = _uiState.value.albumWithTracks?.album?.artist ?: ""
        if (tracks.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isTranslating = true, userMessage = "正在极速多源检索全辑官方歌词 (共 ${tracks.size} 首)...") }
            var matchedCount = 0
            try {
                for ((index, track) in tracks.withIndex()) {
                    _uiState.update { it.copy(userMessage = "正在检索 [${index + 1}/${tracks.size}] ${track.title}...") }
                    val result = com.linernotes.app.data.remote.UnifiedLyricsService.fetchLyrics(
                        trackTitle = track.title,
                        artistName = artist,
                        sourcePref = aiPreferences.lyricsSource
                    )
                    if (result != null && result.originalLyrics.isNotBlank()) {
                        repository.updateTrackTranslation(
                            trackId = track.id,
                            translatedTitle = track.translatedTitle,
                            originalLyrics = result.originalLyrics,
                            translatedLyrics = result.translatedLyrics
                        )
                        matchedCount++
                    }
                }
                _uiState.update {
                    it.copy(
                        isTranslating = false,
                        userMessage = "全辑官方歌词匹配完成！已成功收录 $matchedCount / ${tracks.size} 首曲目"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isTranslating = false, userMessage = "全辑匹配中途出错: ${e.message}") }
            }
        }
    }

    fun startBatchAlbumTranslation() {
        _uiState.update { it.copy(isTranslateMenuOpen = false) }
        val album = _uiState.value.albumWithTracks?.album ?: return
        batchTranslationManager.startBatchTranslation(album.id, album.title)
    }

    fun cancelBatchAlbumTranslation() {
        batchTranslationManager.cancelBatchTranslation()
    }

    fun retranslateCurrentTrack() {
        _uiState.update { it.copy(isTranslateMenuOpen = false) }
        val currentTrack = getCurrentTrack() ?: return
        if (currentTrack.originalLyrics.isNullOrBlank()) {
            _uiState.update { it.copy(userMessage = "当前曲目无歌词，无法进行翻译") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTranslating = true, userMessage = "正在逐行翻译歌词中...") }
            try {
                val result = translationService.translateTrack(
                    trackTitle = currentTrack.title,
                    originalLyrics = currentTrack.originalLyrics
                )

                repository.updateTrackTranslation(
                    trackId = currentTrack.id,
                    translatedTitle = result.translatedTitle ?: currentTrack.translatedTitle,
                    originalLyrics = currentTrack.originalLyrics,
                    translatedLyrics = result.translatedLyrics
                )
                _uiState.update { it.copy(isTranslating = false, userMessage = "翻译完成并已对齐保存！") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isTranslating = false, userMessage = "翻译遇到问题: ${e.message}") }
            }
        }
    }

    fun convertCurrentTrackTranslation(toTraditional: Boolean) {
        _uiState.update { it.copy(isTranslateMenuOpen = false) }
        val currentTrack = getCurrentTrack() ?: return
        if (currentTrack.translatedLyrics.isNullOrBlank() && currentTrack.translatedTitle.isNullOrBlank()) {
            _uiState.update { it.copy(userMessage = "当前曲目暂无译文可转换") }
            return
        }

        viewModelScope.launch {
            val newTitle = if (toTraditional) {
                ChineseConverter.toTraditional(currentTrack.translatedTitle)
            } else {
                ChineseConverter.toSimplified(currentTrack.translatedTitle)
            }
            val newLyrics = if (toTraditional) {
                ChineseConverter.toTraditional(currentTrack.translatedLyrics)
            } else {
                ChineseConverter.toSimplified(currentTrack.translatedLyrics)
            }

            repository.updateTrackTranslation(
                trackId = currentTrack.id,
                translatedTitle = newTitle.ifBlank { null },
                originalLyrics = currentTrack.originalLyrics,
                translatedLyrics = newLyrics
            )

            val aligned = LyricAligner.align(currentTrack.originalLyrics, newLyrics)
            _uiState.update { state ->
                state.copy(
                    alignedLyrics = aligned,
                    userMessage = if (toTraditional) "当前曲目译文已成功转换为繁体中文" else "当前曲目译文已成功转换为简体中文"
                )
            }
        }
    }

    fun convertAlbumTranslation(toTraditional: Boolean) {
        _uiState.update { it.copy(isTranslateMenuOpen = false) }
        val albumWithTracks = _uiState.value.albumWithTracks ?: return
        val tracks = albumWithTracks.tracks
        if (tracks.isEmpty()) return

        viewModelScope.launch {
            var convertedCount = 0
            val album = albumWithTracks.album
            if (!album.translatedTitle.isNullOrBlank()) {
                val newAlbumTitle = if (toTraditional) {
                    ChineseConverter.toTraditional(album.translatedTitle)
                } else {
                    ChineseConverter.toSimplified(album.translatedTitle)
                }
                repository.updateAlbumTranslation(album.id, newAlbumTitle.ifBlank { null })
            }

            for (track in tracks) {
                if (!track.translatedLyrics.isNullOrBlank() || !track.translatedTitle.isNullOrBlank()) {
                    val newTitle = if (toTraditional) {
                        ChineseConverter.toTraditional(track.translatedTitle)
                    } else {
                        ChineseConverter.toSimplified(track.translatedTitle)
                    }
                    val newLyrics = if (toTraditional) {
                        ChineseConverter.toTraditional(track.translatedLyrics)
                    } else {
                        ChineseConverter.toSimplified(track.translatedLyrics)
                    }

                    repository.updateTrackTranslation(
                        trackId = track.id,
                        translatedTitle = newTitle.ifBlank { null },
                        originalLyrics = track.originalLyrics,
                        translatedLyrics = newLyrics
                    )
                    convertedCount++
                }
            }

            val currentTrack = getCurrentTrack()
            val newAligned = if (currentTrack != null) {
                val newLyrics = if (toTraditional) {
                    ChineseConverter.toTraditional(currentTrack.translatedLyrics)
                } else {
                    ChineseConverter.toSimplified(currentTrack.translatedLyrics)
                }
                LyricAligner.align(currentTrack.originalLyrics, newLyrics)
            } else {
                _uiState.value.alignedLyrics
            }

            val targetType = if (toTraditional) "繁体中文" else "简体中文"
            _uiState.update {
                it.copy(
                    alignedLyrics = newAligned,
                    userMessage = "全辑共 $convertedCount 首曲目译文已成功转换为$targetType"
                )
            }
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun getCurrentTrack(): TrackEntity? {
        val state = _uiState.value
        return state.albumWithTracks?.tracks?.getOrNull(state.currentTrackIndex)
    }
}
