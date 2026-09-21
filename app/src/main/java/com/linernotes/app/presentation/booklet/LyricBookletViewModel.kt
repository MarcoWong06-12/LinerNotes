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
    private val aiTranslationService: AiTranslationService
) : ViewModel() {

    private var currentAlbumId: String = ""

    private val _uiState = MutableStateFlow(BookletUiState())
    val uiState: StateFlow<BookletUiState> = _uiState.asStateFlow()

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

    fun onAiTranslateClicked() {
        if (!aiPreferences.hasKey) {
            // 如果尚未配置 AI API Key，直接弹出配置窗口引导配置
            _uiState.update { it.copy(isAiConfigOpen = true) }
        } else {
            retranslateCurrentTrack()
        }
    }

    fun retranslateCurrentTrack() {
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
