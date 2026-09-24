package com.linernotes.app.presentation.booklet

import android.bluetooth.BluetoothDevice
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.lyric.LyricSanitizer
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
    private var userTrackSelectionTimestamp = 0L
    private var userTrackSelectionLockoutMs = 3500L
    private var userSelectedTrackIndex = -1
    private var userSeekTimestamp = 0L
    private var userWantsPlay = true

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
                if (cdState.connectionState == CdConnectionState.CONNECTED && cdState.currentQueueIndex >= 0) {
                    onExternalCdStateReceived(
                        queueIndex = cdState.currentQueueIndex,
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
                    val rawTracks = albumWithTracks.tracks.sortedBy { it.trackNumber }
                    val tracks = rawTracks.map { t ->
                        val cleanTitle = if (LyricSanitizer.hasCensorship(t.title)) LyricSanitizer.decensorTitle(t.title) else t.title
                        val cleanLyrics = if (LyricSanitizer.hasCensorship(t.originalLyrics)) LyricSanitizer.decensorLyrics(t.originalLyrics ?: "") else t.originalLyrics
                        if (cleanTitle != t.title || cleanLyrics != t.originalLyrics) {
                            t.copy(title = cleanTitle, originalLyrics = cleanLyrics)
                        } else {
                            t
                        }
                    }
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
            val previousTrackIndex = _uiState.value.currentTrackIndex
            val delta = kotlin.math.abs(index - previousTrackIndex)
            if (notifyCdPlayer) {
                userWantsPlay = true
                userTrackSelectionLockoutMs = (3500L + delta * 600L).coerceAtMost(10000L)
                userTrackSelectionTimestamp = System.currentTimeMillis()
                userSelectedTrackIndex = index
            }
            val track = tracks[index]
            val cleanLyrics = if (LyricSanitizer.hasCensorship(track.originalLyrics)) LyricSanitizer.decensorLyrics(track.originalLyrics ?: "") else track.originalLyrics
            val aligned = LyricAligner.align(cleanLyrics, track.translatedLyrics)
            val duration = computeTrackDuration(track, aligned)
            _uiState.update {
                it.copy(
                    currentTrackIndex = index,
                    alignedLyrics = aligned,
                    currentPositionMs = 0L,
                    trackDurationMs = duration,
                    activeLineIndex = -1,
                    isCdTracklistOpen = false
                )
            }
            if (notifyCdPlayer && _uiState.value.cdConnectionState == CdConnectionState.CONNECTED) {
                // CdPlayQueueReq.index is 0-based (0 for 1st song, 1 for 2nd song...)
                val cdQueueIndex = if (track.trackNumber > 0) track.trackNumber - 1 else index
                val prevCdQueueIndex = if (previousTrackIndex in tracks.indices) {
                    val prevTrack = tracks[previousTrackIndex]
                    if (prevTrack.trackNumber > 0) prevTrack.trackNumber - 1 else previousTrackIndex
                } else {
                    previousTrackIndex
                }
                shanlingBluetoothManager.playTrack(cdQueueIndex, prevCdQueueIndex)
            }
        }
    }

    private var companionJob: kotlinx.coroutines.Job? = null

    fun toggleCompanionPlay() {
        val willPlay = !_uiState.value.isCompanionPlaying
        userWantsPlay = willPlay
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
        userWantsPlay = true
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
        userWantsPlay = false
        pauseCompanionInternal()
        if (_uiState.value.cdConnectionState == CdConnectionState.CONNECTED) {
            shanlingBluetoothManager.pause()
        }
    }

    private fun pauseCompanionInternal() {
        companionJob?.cancel()
        companionJob = null
        _uiState.update { it.copy(isCompanionPlaying = false) }
    }

    fun seekCompanion(targetMs: Long, notifyCdPlayer: Boolean = true) {
        if (notifyCdPlayer) {
            userSeekTimestamp = System.currentTimeMillis()
        }
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

    fun onExternalCdStateReceived(queueIndex: Int, posMs: Long, isPlaying: Boolean) {
        if (queueIndex < 0) return
        val tracks = _uiState.value.albumWithTracks?.tracks
        val targetIndex = if (tracks.isNullOrEmpty()) {
            queueIndex
        } else {
            resolveTrackIndex(tracks, queueIndex)
        }

        val now = System.currentTimeMillis()
        val isWithinLockout = (now - userTrackSelectionTimestamp) < userTrackSelectionLockoutMs

        if (isWithinLockout) {
            if (targetIndex != userSelectedTrackIndex) {
                // CD 机光头物理寻道中（通常需 1.5~2.5 秒），严格忽略旧音轨帧上报，防止 UI 闪跳回退
                return
            }
            // 物理光头已到达目标曲目，若 CD 此时处于暂停状态且用户期望播放，主动唤醒 CD 伺服起播
            if (!isPlaying && userWantsPlay) {
                shanlingBluetoothManager.play()
            }
            // 收到新音轨确认后，仅在距用户操作满 1.5 秒后才解除保护罩，确保物理硬件稳态
            if (now - userTrackSelectionTimestamp > 1500L) {
                userTrackSelectionTimestamp = 0L
            }
        }

        if (targetIndex != -1 && targetIndex != _uiState.value.currentTrackIndex) {
            if (!tracks.isNullOrEmpty() && targetIndex in tracks.indices) {
                selectTrack(targetIndex, notifyCdPlayer = false)
            } else {
                _uiState.update { it.copy(currentTrackIndex = targetIndex) }
            }
        }
        if (now - userSeekTimestamp >= 1500L && posMs > 0L) {
            seekCompanion(posMs, notifyCdPlayer = false)
        }
        if (isPlaying && !_uiState.value.isCompanionPlaying) {
            startCompanionInternal()
        } else if (!isPlaying && _uiState.value.isCompanionPlaying) {
            if (userWantsPlay) {
                // 用户期望播放但 CD 处于暂停（如机械跳轨到位瞬间），主动指令 CD 机起播并保持伴奏播放
                shanlingBluetoothManager.play()
            } else {
                pauseCompanionInternal()
            }
        }
    }

    private fun resolveTrackIndex(tracks: List<TrackEntity>, cdQueueIndex: Int): Int {
        if (tracks.isEmpty()) return -1
        val physicalCdTrackNo = cdQueueIndex + 1

        // 1. 0-based 物理队列位置与已排好序的曲目直接对应（首选标准 Red Book 1:1 映射）
        if (cdQueueIndex in tracks.indices && tracks[cdQueueIndex].trackNumber == physicalCdTrackNo) {
            return cdQueueIndex
        }

        // 2. 匹配 TrackEntity.trackNumber 与 1-based 物理音轨号（兼容曲目乱序或不连续情况）
        val exactTrackNumberMatch = tracks.indexOfFirst { it.trackNumber == physicalCdTrackNo }
        if (exactTrackNumberMatch != -1) return exactTrackNumberMatch

        // 3. 容错回退：按 0-based 列表位置直接定位
        if (cdQueueIndex in tracks.indices) return cdQueueIndex

        return 0
    }

    fun openCdTracklist(isOpen: Boolean) {
        _uiState.update { it.copy(isCdTracklistOpen = isOpen) }
    }

    fun openCdMatchAlbum(isOpen: Boolean) {
        _uiState.update { it.copy(isCdMatchAlbumOpen = isOpen) }
    }

    fun playCdTrack(trackIndex: Int) {
        userWantsPlay = true
        val tracks = _uiState.value.albumWithTracks?.tracks ?: emptyList()
        if (trackIndex in tracks.indices) {
            selectTrack(trackIndex, notifyCdPlayer = true)
        } else {
            // 未匹配唱片曲目时，直接指令 CD 机播放对应的 0-based 物理音轨索引
            val previousTrackIndex = _uiState.value.currentTrackIndex
            val delta = kotlin.math.abs(trackIndex - previousTrackIndex)
            userTrackSelectionLockoutMs = (3500L + delta * 600L).coerceAtMost(10000L)
            userTrackSelectionTimestamp = System.currentTimeMillis()
            userSelectedTrackIndex = trackIndex
            _uiState.update { it.copy(currentTrackIndex = trackIndex, isCdTracklistOpen = false) }
            shanlingBluetoothManager.playTrack(trackIndex, previousTrackIndex)
        }
        _uiState.update { it.copy(isCdTracklistOpen = false) }
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
        val target = _uiState.value.currentTrackIndex - 1
        if (target >= 0) {
            playCdTrack(target)
        }
    }

    fun nextTrack() {
        val tracks = _uiState.value.albumWithTracks?.tracks
        val totalCount = if (!tracks.isNullOrEmpty()) tracks.size else _uiState.value.cdTotalTracks
        val target = _uiState.value.currentTrackIndex + 1
        if (totalCount > 0 && target < totalCount) {
            playCdTrack(target)
        }
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
