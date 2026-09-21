package com.linernotes.app.presentation.booklet

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.linernotes.app.presentation.common.AiConfigDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.linernotes.app.domain.model.BilingualLyricLine
import com.linernotes.app.domain.model.LyricDisplayMode
import com.linernotes.app.presentation.booklet.components.EditLyricSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricBookletScreen(
    albumId: String,
    onNavigateBack: () -> Unit,
    viewModel: LyricBookletViewModel = hiltViewModel()
) {
    LaunchedEffect(albumId) {
        viewModel.setAlbumId(albumId)
    }
    val state by viewModel.uiState.collectAsState()
    val batchState by viewModel.batchTranslationState.collectAsState()
    val isBatchTranslatingThisAlbum = batchState.isTranslating && batchState.albumId == state.albumWithTracks?.album?.id
    val isTranslatingOverall = state.isTranslating || isBatchTranslatingThisAlbum

    val snackbarHostState = remember { SnackbarHostState() }

    val currentTrack = viewModel.getCurrentTrack()
    val totalTracks = state.albumWithTracks?.tracks?.size ?: 0

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    val basePaperColor = Color(0xFF131316)
    val backgroundGradient = remember(state.ambientCoverColor) {
        Brush.verticalGradient(
            colors = listOf(
                state.ambientCoverColor.copy(alpha = 0.45f),
                basePaperColor.copy(alpha = 0.85f),
                basePaperColor
            ),
            startY = 0f,
            endY = 1200f
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val strings = com.linernotes.app.core.i18n.LocalStrings.current
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    SingleChoiceSegmentedControl(
                        currentMode = state.displayMode,
                        onModeSelected = { viewModel.setDisplayMode(it) }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box {
                        IconButton(
                            onClick = { viewModel.onAiTranslateClicked() },
                            enabled = !isTranslatingOverall
                        ) {
                            if (isTranslatingOverall) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = strings.aiTranslateAction,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = state.isTranslateMenuOpen,
                            onDismissRequest = { viewModel.setTranslateMenuOpen(false) }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(strings.translateCurrentTrack, style = MaterialTheme.typography.bodyMedium)
                                },
                                onClick = {
                                    viewModel.retranslateCurrentTrack()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.FlashOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text(
                                            strings.batchTranslateAlbum,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            strings.batchTranslateAlbumDesc,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.startBatchAlbumTranslation()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Album,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.openAiConfig(true) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = strings.settingsTitle,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.openEditSheet(true) }) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = strings.editLyricsAction,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            TrackNavigationBar(
                trackNumber = currentTrack?.trackNumber ?: (state.currentTrackIndex + 1),
                totalTracks = totalTracks,
                trackTitle = currentTrack?.title ?: "",
                translatedTitle = currentTrack?.translatedTitle,
                hasPrevious = state.currentTrackIndex > 0,
                hasNext = state.currentTrackIndex < totalTracks - 1,
                onPrevious = { viewModel.previousTrack() },
                onNext = { viewModel.nextTrack() }
            )
        },
        containerColor = basePaperColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
                .padding(paddingValues)
        ) {
            val strings = com.linernotes.app.core.i18n.LocalStrings.current

            // 正在整张专辑后台批量翻译时的顶部常驻进度条与取消按钮
            if (isBatchTranslatingThisAlbum) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${strings.batchTranslatingBanner} [${batchState.currentTrackIndex}/${batchState.totalTracks}] ${batchState.currentTrackTitle ?: ""}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = { viewModel.cancelBatchAlbumTranslation() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    strings.cancel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val progress = if (batchState.totalTracks > 0) {
                            batchState.currentTrackIndex.toFloat() / batchState.totalTracks.toFloat()
                        } else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(state.currentTrackIndex, totalTracks) {
                        var totalDragX = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (totalDragX > 150f) viewModel.previousTrack()
                                else if (totalDragX < -150f) viewModel.nextTrack()
                                totalDragX = 0f
                            },
                            onDragCancel = { totalDragX = 0f },
                            onHorizontalDrag = { _, dragAmount -> totalDragX += dragAmount }
                        )
                    }
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        BookletHeaderSection(
                            coverUrl = state.albumWithTracks?.album?.coverUrl ?: "",
                            albumTitle = state.albumWithTracks?.album?.title ?: "",
                            translatedTitle = state.albumWithTracks?.album?.translatedTitle,
                            artist = state.albumWithTracks?.album?.artist ?: "",
                            releaseYear = state.albumWithTracks?.album?.releaseYear ?: "",
                            currentTrackNum = currentTrack?.trackNumber ?: 1,
                            totalTracks = totalTracks
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                    }

                    if (state.alignedLyrics.isEmpty()) {
                        item {
                            val strings = com.linernotes.app.core.i18n.LocalStrings.current
                            Text(
                                text = strings.noLyrics,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(top = 64.dp)
                            )
                        }
                    } else {
                        items(state.alignedLyrics, key = { it.lineNumber }) { line ->
                            LyricLineItem(
                                line = line,
                                mode = state.displayMode
                            )
                        }
                    }
                }
            }
        }
    }
}

    if (state.isEditingSheetOpen && currentTrack != null) {
        EditLyricSheet(
            track = currentTrack,
            onDismiss = { viewModel.openEditSheet(false) },
            onSave = { zhTitle, origLyrics, transLyrics ->
                viewModel.saveManualEdits(currentTrack.id, zhTitle, origLyrics, transLyrics)
            }
        )
    }

    if (state.isAiConfigOpen) {
        AiConfigDialog(
            aiPreferences = viewModel.aiPreferences,
            onTestConnection = { k, b, m -> viewModel.testAiConnection(k, b, m) },
            onDismiss = { viewModel.openAiConfig(false) },
            onSaved = {
                viewModel.openAiConfig(false)
                viewModel.retranslateCurrentTrack()
            }
        )
    }
}

@Composable
private fun BookletHeaderSection(
    coverUrl: String,
    albumTitle: String,
    translatedTitle: String?,
    artist: String,
    releaseYear: String,
    currentTrackNum: Int,
    totalTracks: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(coverUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(108.dp)
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
        )

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(100.dp)
        ) {
            Text(
                text = "TRACK ${currentTrackNum.toString().padStart(2, '0')} / ${totalTracks.toString().padStart(2, '0')}",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.8.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = albumTitle,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (!translatedTitle.isNullOrBlank()) {
            Text(
                text = translatedTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "$artist • $releaseYear",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LyricLineItem(
    line: BilingualLyricLine,
    mode: LyricDisplayMode
) {
    if (line.isStanzaBreak) {
        Spacer(modifier = Modifier.height(20.dp))
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (mode == LyricDisplayMode.BILINGUAL) 6.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (mode) {
            LyricDisplayMode.BILINGUAL -> {
                if (line.original.isNotBlank()) {
                    Text(
                        text = line.original,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            lineHeight = 26.sp,
                            letterSpacing = 0.2.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
                if (line.translation.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = line.translation,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            LyricDisplayMode.ORIGINAL_ONLY -> {
                if (line.original.isNotBlank()) {
                    Text(
                        text = line.original,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 30.sp,
                            letterSpacing = 0.4.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }

            LyricDisplayMode.TRANSLATED_ONLY -> {
                if (line.translation.isNotBlank()) {
                    Text(
                        text = line.translation,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleChoiceSegmentedControl(
    currentMode: LyricDisplayMode,
    onModeSelected: (LyricDisplayMode) -> Unit
) {
    val strings = com.linernotes.app.core.i18n.LocalStrings.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(2.dp)
        ) {
            SegmentItem(strings.modeBilingual, currentMode == LyricDisplayMode.BILINGUAL) {
                onModeSelected(LyricDisplayMode.BILINGUAL)
            }
            SegmentItem(strings.modeOriginal, currentMode == LyricDisplayMode.ORIGINAL_ONLY) {
                onModeSelected(LyricDisplayMode.ORIGINAL_ONLY)
            }
            SegmentItem(strings.modeTranslated, currentMode == LyricDisplayMode.TRANSLATED_ONLY) {
                onModeSelected(LyricDisplayMode.TRANSLATED_ONLY)
            }
        }
    }
}

@Composable
private fun SegmentItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TrackNavigationBar(
    trackNumber: Int,
    totalTracks: Int,
    trackTitle: String,
    translatedTitle: String?,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val strings = com.linernotes.app.core.i18n.LocalStrings.current
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrevious, enabled = hasPrevious) {
                Icon(Icons.Default.SkipPrevious, contentDescription = strings.prevTrack)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "$trackNumber. $trackTitle",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!translatedTitle.isNullOrBlank()) {
                    Text(
                        text = translatedTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onNext, enabled = hasNext) {
                Icon(Icons.Default.SkipNext, contentDescription = strings.nextTrack)
            }
        }
    }
}
