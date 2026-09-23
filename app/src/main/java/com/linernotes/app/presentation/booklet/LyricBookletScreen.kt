package com.linernotes.app.presentation.booklet

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.linernotes.app.core.bluetooth.CdConnectionState
import com.linernotes.app.domain.model.BilingualLyricLine
import com.linernotes.app.domain.model.LyricDisplayMode
import com.linernotes.app.presentation.booklet.components.CdSyncSheet
import com.linernotes.app.presentation.booklet.components.EditLyricSheet
import com.linernotes.app.presentation.common.AiConfigDialog

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
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.connectCdPlayer()
        }
    }

    val requestCdConnect: (android.bluetooth.BluetoothDevice?) -> Unit = { targetDevice ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                viewModel.connectCdPlayer(targetDevice)
            } else {
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            viewModel.connectCdPlayer(targetDevice)
        }
    }

    val currentTrack = viewModel.getCurrentTrack()
    val totalTracks = state.albumWithTracks?.tracks?.size ?: 0

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    // 当伴侣播放器推进歌词时间轴时，平滑自动滚动居中聚焦当前活动行
    LaunchedEffect(state.activeLineIndex) {
        if (state.activeLineIndex in state.alignedLyrics.indices) {
            listState.animateScrollToItem(
                index = state.activeLineIndex + 1, // 0 号 item 为 BookletHeaderSection
                scrollOffset = -260
            )
        }
    }

    val basePaperColor = Color(0xFF121215)
    val backgroundGradient = remember(state.ambientCoverColor) {
        Brush.verticalGradient(
            colors = listOf(
                state.ambientCoverColor.copy(alpha = 0.45f),
                basePaperColor.copy(alpha = 0.88f),
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
                    FilledIconButton(
                        onClick = onNavigateBack,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(start = 8.dp).size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                actions = {
                    // Gemini 风格胶囊分段模式切换器
                    SingleChoiceSegmentedControl(
                        currentMode = state.displayMode,
                        onModeSelected = { viewModel.setDisplayMode(it) }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box {
                        FilledIconButton(
                            onClick = { viewModel.onAiTranslateClicked() },
                            enabled = !isTranslatingOverall,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.size(40.dp)
                        ) {
                            if (isTranslatingOverall) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = strings.aiTranslateAction,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = state.isTranslateMenuOpen,
                            onDismissRequest = { viewModel.setTranslateMenuOpen(false) }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text(
                                            strings.fetchOfficialLyrics,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            strings.fetchOfficialLyricsDesc,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.fetchOfficialLyricsCurrentTrack()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text(
                                            strings.batchFetchOfficialAlbum,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            strings.batchFetchOfficialAlbumDesc,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.batchFetchOfficialLyricsAlbum()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Album,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
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
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = {
                                    Text(strings.convertCurrentTrackToTraditional, style = MaterialTheme.typography.bodyMedium)
                                },
                                onClick = {
                                    viewModel.convertCurrentTrackTranslation(toTraditional = true)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(strings.convertCurrentTrackToSimplified, style = MaterialTheme.typography.bodyMedium)
                                },
                                onClick = {
                                    viewModel.convertCurrentTrackTranslation(toTraditional = false)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(strings.convertAlbumToTraditional, style = MaterialTheme.typography.bodyMedium)
                                },
                                onClick = {
                                    viewModel.convertAlbumTranslation(toTraditional = true)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Album,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(strings.convertAlbumToSimplified, style = MaterialTheme.typography.bodyMedium)
                                },
                                onClick = {
                                    viewModel.convertAlbumTranslation(toTraditional = false)
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
                    Spacer(modifier = Modifier.width(4.dp))
                    FilledIconButton(
                        onClick = { viewModel.openCdSheet(true) },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (state.cdConnectionState == CdConnectionState.CONNECTED)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = if (state.cdConnectionState == CdConnectionState.CONNECTED)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(40.dp)
                    ) {
                        if (state.cdConnectionState == CdConnectionState.CONNECTING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = if (state.cdConnectionState == CdConnectionState.CONNECTED)
                                    Icons.Default.BluetoothConnected
                                else Icons.Default.Bluetooth,
                                contentDescription = strings.cdSyncTitle,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    FilledIconButton(
                        onClick = { viewModel.openAiConfig(true) },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = strings.settingsTitle,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    FilledIconButton(
                        onClick = { viewModel.openEditSheet(true) },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(end = 8.dp).size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = strings.editLyricsAction,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = basePaperColor
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
                .padding(paddingValues)
        ) {
            val strings = com.linernotes.app.core.i18n.LocalStrings.current

            Column(modifier = Modifier.fillMaxSize()) {
                // 正在后台翻译时的顶部常驻进度条
                if (isBatchTranslatingThisAlbum) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(20.dp),
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
                                    .clip(CircleShape),
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
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 148.dp),
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
                                    Text(
                                        text = strings.noLyrics,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(top = 64.dp)
                                    )
                                }
                            } else {
                                itemsIndexed(state.alignedLyrics, key = { index, line -> "${line.lineNumber}_$index" }) { index, line ->
                                    val isActive = index == state.activeLineIndex
                                    LyricLineItem(
                                        line = line,
                                        mode = state.displayMode,
                                        isActive = isActive,
                                        isCompanionPlaying = state.isCompanionPlaying,
                                        onClick = { viewModel.onLyricLineClicked(line) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Gemini 风格悬浮胶囊伴侣控制岛 (Floating Pill Island)
            FloatingCompanionCapsule(
                trackNumber = currentTrack?.trackNumber ?: (state.currentTrackIndex + 1),
                totalTracks = totalTracks,
                trackTitle = currentTrack?.title ?: "",
                translatedTitle = currentTrack?.translatedTitle,
                currentPosMs = state.currentPositionMs,
                durationMs = state.trackDurationMs,
                isPlaying = state.isCompanionPlaying,
                hasPrevious = state.currentTrackIndex > 0,
                hasNext = state.currentTrackIndex < totalTracks - 1,
                showCalibration = state.showCalibrationBar,
                cdConnectionState = state.cdConnectionState,
                cdDeviceName = state.cdDeviceName,
                onPrevious = { viewModel.previousTrack() },
                onNext = { viewModel.nextTrack() },
                onTogglePlay = { viewModel.toggleCompanionPlay() },
                onSeek = { viewModel.seekCompanion(it) },
                onAdjustOffset = { viewModel.adjustCompanionOffset(it) },
                onToggleCalibration = { viewModel.toggleCalibrationBar() },
                onOpenCdSheet = { viewModel.openCdSheet(true) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
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
            }
        )
    }

    if (state.isCdSheetOpen) {
        val pairedDevices = remember { viewModel.getPairedBluetoothDevices() }
        CdSyncSheet(
            connectionState = state.cdConnectionState,
            connectedDeviceName = state.cdDeviceName,
            pairedDevices = pairedDevices,
            onConnect = { device ->
                requestCdConnect(device)
            },
            onDisconnect = { viewModel.disconnectCdPlayer() },
            onDismiss = { viewModel.openCdSheet(false) }
        )
    }
}

/**
 * Gemini 风格悬浮胶囊控制岛 (Floating Pill Island)
 */
@Composable
private fun FloatingCompanionCapsule(
    trackNumber: Int,
    totalTracks: Int,
    trackTitle: String,
    translatedTitle: String?,
    currentPosMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    showCalibration: Boolean,
    cdConnectionState: CdConnectionState = CdConnectionState.DISCONNECTED,
    cdDeviceName: String? = null,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onToggleCalibration: () -> Unit,
    onOpenCdSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = com.linernotes.app.core.i18n.LocalStrings.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // CD 蓝牙同步状态浮动胶囊 (Gemini Pill)
        Surface(
            shape = CircleShape,
            color = when (cdConnectionState) {
                CdConnectionState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
                CdConnectionState.CONNECTING -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.90f)
                CdConnectionState.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                CdConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            },
            border = BorderStroke(
                1.dp,
                if (cdConnectionState == CdConnectionState.CONNECTED) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else Color.White.copy(alpha = 0.10f)
            ),
            shadowElevation = 6.dp,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .clickable { onOpenCdSheet() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                if (cdConnectionState == CdConnectionState.CONNECTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                when (cdConnectionState) {
                                    CdConnectionState.CONNECTED -> Color(0xFF4CAF50)
                                    CdConnectionState.FAILED -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                }
                            )
                    )
                }
                Text(
                    text = when (cdConnectionState) {
                        CdConnectionState.CONNECTED -> "已同步 CD: ${cdDeviceName ?: "山灵 EC Mini"}"
                        CdConnectionState.CONNECTING -> strings.cdSyncConnecting
                        CdConnectionState.FAILED -> "CD 连接失败 · 点击重试"
                        CdConnectionState.DISCONNECTED -> "未连接 CD 机 · 点击连接"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (cdConnectionState == CdConnectionState.CONNECTED) FontWeight.Bold else FontWeight.Medium,
                    color = when (cdConnectionState) {
                        CdConnectionState.CONNECTED -> MaterialTheme.colorScheme.onPrimaryContainer
                        CdConnectionState.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        // 微调对齐胶囊托盘
        AnimatedVisibility(
            visible = showCalibration,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                    slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                    slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .shadow(8.dp, CircleShape)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onAdjustOffset(-1000L) },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(strings.calibrateSlower, style = MaterialTheme.typography.labelSmall)
                    }

                    Text(
                        text = strings.calibrationTip,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FilledTonalButton(
                        onClick = { onAdjustOffset(1000L) },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(strings.calibrateFaster, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // 主胶囊药丸容器 (Gemini Pill)
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = Color(0xFF1B1B20).copy(alpha = 0.95f),
            tonalElevation = 10.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(36.dp),
                    spotColor = Color.Black.copy(alpha = 0.65f)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // 顶部平滑时间进度指示
                val progress = if (durationMs > 0L) {
                    (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = formatTime(currentPosMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                )
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatTime(durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 控制按钮行
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 上一曲（圆形）
                    FilledIconButton(
                        onClick = onPrevious,
                        enabled = hasPrevious,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = strings.prevTrack,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 核心高亮播放/暂停大圆纽 (Gemini 标志性大圆形按钮)
                    val heroScale by animateFloatAsState(
                        targetValue = if (isPlaying) 1.05f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "heroScale"
                    )

                    Surface(
                        onClick = onTogglePlay,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(52.dp)
                            .scale(heroScale)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AnimatedContent(
                                targetState = isPlaying,
                                transitionSpec = {
                                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                            scaleIn(initialScale = 0.8f))
                                        .togetherWith(
                                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                                    scaleOut(targetScale = 0.8f)
                                        )
                                },
                                label = "playPause"
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playing) strings.cdCompanionPause else strings.cdCompanionPlay,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    // 下一曲（圆形）
                    FilledIconButton(
                        onClick = onNext,
                        enabled = hasNext,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = strings.nextTrack,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 曲名与音轨胶囊
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (cdConnectionState == CdConnectionState.CONNECTED) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = "CD",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "$trackNumber. $trackTitle",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (!translatedTitle.isNullOrBlank()) {
                            Text(
                                text = translatedTitle,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // 校准微调芯片（圆形）
                    FilledIconButton(
                        onClick = onToggleCalibration,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (showCalibration) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = if (showCalibration) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Tune",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(java.util.Locale.US, "%02d:%02d", min, sec)
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
                .size(112.dp)
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
        )

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = CircleShape
        ) {
            Text(
                text = "TRACK ${currentTrackNum.toString().padStart(2, '0')} / ${totalTracks.toString().padStart(2, '0')}",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.8.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
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

/**
 * 具有毫秒级时间轴聚焦动效的歌词行 (Tap-to-seek, smooth animated scale/alpha)
 */
@Composable
private fun LyricLineItem(
    line: BilingualLyricLine,
    mode: LyricDisplayMode,
    isActive: Boolean,
    isCompanionPlaying: Boolean,
    onClick: () -> Unit
) {
    if (line.isStanzaBreak) {
        Spacer(modifier = Modifier.height(20.dp))
        return
    }

    val alphaAnim by animateFloatAsState(
        targetValue = if (isActive) 1.0f else if (isCompanionPlaying) 0.38f else 0.85f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "lyricAlpha"
    )

    val scaleAnim by animateFloatAsState(
        targetValue = if (isActive) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "lyricScale"
    )

    val activeTextColor = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface
    val activeTransColor = if (isActive) Color(0xFFD0BCFF) else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = if (mode == LyricDisplayMode.BILINGUAL) 6.dp else 4.dp)
            .graphicsLayer {
                alpha = alphaAnim
                scaleX = scaleAnim
                scaleY = scaleAnim
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (mode) {
            LyricDisplayMode.BILINGUAL -> {
                if (line.original.isNotBlank()) {
                    Text(
                        text = line.original,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            lineHeight = 26.sp,
                            letterSpacing = 0.2.sp
                        ),
                        color = activeTextColor,
                        textAlign = TextAlign.Center
                    )
                }
                if (line.translation.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = line.translation,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = activeTransColor.copy(alpha = if (isActive) 1f else 0.75f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            LyricDisplayMode.ORIGINAL_ONLY -> {
                if (line.original.isNotBlank()) {
                    Text(
                        text = line.original,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            lineHeight = 30.sp,
                            letterSpacing = 0.4.sp
                        ),
                        color = activeTextColor,
                        textAlign = TextAlign.Center
                    )
                }
            }

            LyricDisplayMode.TRANSLATED_ONLY -> {
                if (line.translation.isNotBlank()) {
                    Text(
                        text = line.translation,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            lineHeight = 30.sp
                        ),
                        color = activeTextColor,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Gemini 风格胶囊分段模式切换器 (Pill Segmented Control)
 */
@Composable
private fun SingleChoiceSegmentedControl(
    currentMode: LyricDisplayMode,
    onModeSelected: (LyricDisplayMode) -> Unit
) {
    val strings = com.linernotes.app.core.i18n.LocalStrings.current
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.height(36.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(3.dp)
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
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
