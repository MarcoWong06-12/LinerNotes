package com.linernotes.app.presentation.booklet

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.data.local.entity.TrackEntity
import com.linernotes.app.data.remote.AiTranslationService
import com.linernotes.app.domain.model.LyricDisplayMode
import com.linernotes.app.domain.repository.AlbumRepository
import com.linernotes.app.presentation.booklet.model.BookletUiState
import com.linernotes.app.core.translation.BatchTranslationManager
import com.linernotes.app.core.translation.BatchTranslationState
import com.linernotes.app.core.util.ChineseConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LyricBookletViewModel @Inject constructor(
    private val repository: AlbumRepository,
    val aiPreferences: AiPreferences,
    private val aiTranslationService: AiTranslationService,
    private val batchTranslationManager: BatchTranslationManager
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
    }

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

                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            albumWithTracks = albumWithTracks.copy(tracks = tracks),
                            currentTrackIndex = safeIndex,
                            alignedLyrics = aligned
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

    fun selectTrack(index: Int) {
        val tracks = _uiState.value.albumWithTracks?.tracks ?: return
        if (index in tracks.indices) {
            val track = tracks[index]
            val aligned = LyricAligner.align(track.originalLyrics, track.translatedLyrics)
            _uiState.update {
                it.copy(
                    currentTrackIndex = index,
                    alignedLyrics = aligned
                )
            }
        }
    }

    fun previousTrack() {
        selectTrack(_uiState.value.currentTrackIndex - 1)
    }

    fun nextTrack() {
        selectTrack(_uiState.value.currentTrackIndex + 1)
    }

    fun updateAmbientColor(color: Color) {
        _uiState.update { it.copy(ambientCoverColor = color) }
    }

    fun openEditSheet(isOpen: Boolean) {
        _uiState.update { it.copy(isEditingSheetOpen = isOpen) }
    }

    fun openAiConfig(isOpen: Boolean) {
        _uiState.update { it.copy(isAiConfigOpen = isOpen) }
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

                    // 若官方库仅检索到原版（无官方译文），且启用了智能回退与 AI 引擎，则自动推敲补全翻译
                    if (!result.isBilingual && aiPreferences.lyricsSource == AiPreferences.LyricsSourcePreference.AUTO_FIRST.code && aiPreferences.hasKey) {
                        _uiState.update { it.copy(userMessage = "已匹配官方原版歌词，正在由 AI 自动推敲翻译...") }
                        val aiResult = aiTranslationService.translateTrack(
                            trackTitle = currentTrack.title,
                            originalLyrics = result.originalLyrics
                        )
                        repository.updateTrackTranslation(
                            trackId = currentTrack.id,
                            translatedTitle = aiResult.translatedTitle ?: currentTrack.translatedTitle,
                            originalLyrics = result.originalLyrics,
                            translatedLyrics = aiResult.translatedLyrics
                        )
                        _uiState.update { it.copy(isTranslating = false, userMessage = "官方原版歌词已入库，AI 翻译已同步补全！") }
                    } else {
                        val msg = if (result.isBilingual) "官方双语歌词已匹配并同步入库！" else "已检索到官方原版歌词（暂无官方译文）"
                        _uiState.update { it.copy(isTranslating = false, userMessage = msg) }
                    }
                } else {
                    // 若官方库未搜到且启用了智能回退并且有 key，则尝试 AI 翻译
                    if (aiPreferences.lyricsSource == AiPreferences.LyricsSourcePreference.AUTO_FIRST.code && aiPreferences.hasKey && !currentTrack.originalLyrics.isNullOrBlank()) {
                        _uiState.update { it.copy(userMessage = "在线歌词库未检索到，正在自动回退由 AI 翻译...") }
                        val aiResult = aiTranslationService.translateTrack(
                            trackTitle = currentTrack.title,
                            originalLyrics = currentTrack.originalLyrics
                        )
                        repository.updateTrackTranslation(
                            trackId = currentTrack.id,
                            translatedTitle = aiResult.translatedTitle ?: currentTrack.translatedTitle,
                            originalLyrics = currentTrack.originalLyrics,
                            translatedLyrics = aiResult.translatedLyrics
                        )
                        _uiState.update { it.copy(isTranslating = false, userMessage = "AI 翻译完成并已保存！") }
                    } else {
                        _uiState.update { it.copy(isTranslating = false, userMessage = "未检索到该歌曲歌词，可尝试切换歌词源或使用 AI 翻译") }
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
        if (!aiPreferences.hasKey) {
            _uiState.update { it.copy(isAiConfigOpen = true, userMessage = "请先配置 AI 引擎 API Key") }
            return
        }
        val album = _uiState.value.albumWithTracks?.album ?: return
        batchTranslationManager.startBatchTranslation(album.id, album.title)
    }

    fun cancelBatchAlbumTranslation() {
        batchTranslationManager.cancelBatchTranslation()
    }

    fun retranslateCurrentTrack() {
        _uiState.update { it.copy(isTranslateMenuOpen = false) }
        if (!aiPreferences.hasKey) {
            _uiState.update { it.copy(isAiConfigOpen = true, userMessage = "请先配置 AI 引擎 API Key") }
            return
        }
        val currentTrack = getCurrentTrack() ?: return
        if (currentTrack.originalLyrics.isNullOrBlank()) {
            _uiState.update { it.copy(userMessage = "当前曲目无歌词，无法进行翻译") }
            return
        }

        viewModelScope.launch {
            val msg = if (aiPreferences.hasKey) "AI 正在逐行推敲翻译歌词中..." else "正在进行基础翻译中..."
            _uiState.update { it.copy(isTranslating = true, userMessage = msg) }
            try {
                val result = aiTranslationService.translateTrack(
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

    suspend fun testAiConnection(apiKey: String, baseUrl: String, modelName: String): Pair<Boolean, String> {
        return aiTranslationService.testConnection(apiKey, baseUrl, modelName)
    }
}
