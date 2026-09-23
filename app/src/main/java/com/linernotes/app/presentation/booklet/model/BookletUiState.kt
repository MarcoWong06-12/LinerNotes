package com.linernotes.app.presentation.booklet.model

import androidx.compose.ui.graphics.Color
import com.linernotes.app.data.local.relation.AlbumWithTracks
import com.linernotes.app.domain.model.BilingualLyricLine
import com.linernotes.app.domain.model.LyricDisplayMode

data class BookletUiState(
    val isLoading: Boolean = true,
    val albumWithTracks: AlbumWithTracks? = null,
    val currentTrackIndex: Int = 0,
    val displayMode: LyricDisplayMode = LyricDisplayMode.BILINGUAL,
    val alignedLyrics: List<BilingualLyricLine> = emptyList(),
    val ambientCoverColor: Color = Color(0xFF1E1E24),
    val isEditingSheetOpen: Boolean = false,
    val isAiConfigOpen: Boolean = false,
    val isTranslating: Boolean = false,
    val isTranslateMenuOpen: Boolean = false,
    val userMessage: String? = null,
    val isCompanionPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val activeLineIndex: Int = -1,
    val trackDurationMs: Long = 0L,
    val showCalibrationBar: Boolean = false,
    // 实体 CD 蓝牙同步状态
    val cdConnectionState: com.linernotes.app.core.bluetooth.CdConnectionState = com.linernotes.app.core.bluetooth.CdConnectionState.DISCONNECTED,
    val cdDeviceName: String? = null,
    val isCdSheetOpen: Boolean = false
)
