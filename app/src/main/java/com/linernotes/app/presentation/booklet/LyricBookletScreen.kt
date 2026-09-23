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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.linernotes.app.core.bluetooth.CdConnectionState
import com.linernotes.app.domain.model.BilingualLyricLine
import com.linernotes.app.domain.model.LyricDisplayMode
import com.linernotes.app.presentation.booklet.components.CdSyncSheet
import com.linernotes.app.presentation.booklet.components.CdTracklistSheet
import com.linernotes.app.presentation.booklet.components.EditLyricSheet
import com.linernotes.app.presentation.common.*

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
    val shelfAlbums by viewModel.allShelfAlbums.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var isCoverViewerOpen by remember { mutableStateOf(false) }

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
    val bookletTracksCount = state.albumWithTracks?.tracks?.size ?: 0
    val totalTracks = if (bookletTracksCount > 0) bookletTracksCount else state.cdTotalTracks

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
                index = state.activeLineIndex,
                scrollOffset = -220
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
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 2.dp)
                    ) {
                        val coverUrl = state.albumWithTracks?.album?.coverUrl
                        if (!coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(coverUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .bouncyClickable(pressedScale = 0.92f) { isCoverViewerOpen = true }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = currentTrack?.title ?: (state.albumWithTracks?.album?.title ?: ""),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = state.albumWithTracks?.album?.artist ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.68f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    BouncyIconButton(
                        onClick = onNavigateBack,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.padding(start = 8.dp).size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                actions = {
                    // CD 蓝牙同步状态触钮
                    BouncyIconButton(
                        onClick = { viewModel.openCdSheet(true) },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (state.cdConnectionState == CdConnectionState.CONNECTED)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                            else Color.White.copy(alpha = 0.12f),
                            contentColor = if (state.cdConnectionState == CdConnectionState.CONNECTED)
                                MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.85f)
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        if (state.cdConnectionState == CdConnectionState.CONNECTING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = if (state.cdConnectionState == CdConnectionState.CONNECTED)
                                    Icons.Default.BluetoothConnected
                                else Icons.Default.Bluetooth,
                                contentDescription = strings.cdSyncTitle,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Apple Music 风格三点更多按钮（集成模式切换、翻译、校对、设置）
                    Box {
                        BouncyIconButton(
                            onClick = { viewModel.setTranslateMenuOpen(!state.isTranslateMenuOpen) },
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.15f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.padding(end = 8.dp).size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = "More",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = state.isTranslateMenuOpen,
                            onDismissRequest = { viewModel.setTranslateMenuOpen(false) }
                        ) {
                            // 歌词显示模式切换
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            strings.modeBilingual,
                                            fontWeight = if (state.displayMode == LyricDisplayMode.BILINGUAL) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (state.displayMode == LyricDisplayMode.BILINGUAL) {
                                            Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.setDisplayMode(LyricDisplayMode.BILINGUAL)
                                    viewModel.setTranslateMenuOpen(false)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            strings.modeOriginal,
                                            fontWeight = if (state.displayMode == LyricDisplayMode.ORIGINAL_ONLY) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (state.displayMode == LyricDisplayMode.ORIGINAL_ONLY) {
                                            Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.setDisplayMode(LyricDisplayMode.ORIGINAL_ONLY)
                                    viewModel.setTranslateMenuOpen(false)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            strings.modeTranslated,
                                            fontWeight = if (state.displayMode == LyricDisplayMode.TRANSLATED_ONLY) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (state.displayMode == LyricDisplayMode.TRANSLATED_ONLY) {
                                            Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.setDisplayMode(LyricDisplayMode.TRANSLATED_ONLY)
                                    viewModel.setTranslateMenuOpen(false)
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text(strings.fetchOfficialLyrics, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(strings.fetchOfficialLyricsDesc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    viewModel.fetchOfficialLyricsCurrentTrack()
                                },
                                leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text(strings.batchFetchOfficialAlbum, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(strings.batchFetchOfficialAlbumDesc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    viewModel.batchFetchOfficialLyricsAlbum()
                                },
                                leadingIcon = { Icon(Icons.Default.Album, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = { Text(strings.translateCurrentTrack, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    viewModel.retranslateCurrentTrack()
                                },
                                leadingIcon = { Icon(Icons.Default.FlashOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text(strings.batchTranslateAlbum, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(strings.batchTranslateAlbumDesc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    viewModel.startBatchAlbumTranslation()
                                },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = { Text(strings.convertCurrentTrackToTraditional, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    viewModel.convertCurrentTrackTranslation(toTraditional = true)
                                },
                                leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.convertCurrentTrackToSimplified, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    viewModel.convertCurrentTrackTranslation(toTraditional = false)
                                },
                                leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = { Text(strings.editLyricsAction, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    viewModel.openEditSheet(true)
                                },
                                leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.settingsTitle, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    viewModel.openSettings(true)
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color(0xFF0F0F12)
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            val coverUrl = state.albumWithTracks?.album?.coverUrl

            // 沉浸式唱片封面高斯模糊底图 (Apple Music 风格动态流光背景)
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 0.38f
                            scaleX = 1.4f
                            scaleY = 1.4f
                        }
                        .then(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Modifier.blur(80.dp)
                            } else Modifier
                        )
                )
            }

            // 纵深暗阶遮罩 (Apple Music 暗黑沉浸氛围)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF1E1E22).copy(alpha = 0.70f),
                                Color(0xFF141417).copy(alpha = 0.88f),
                                Color(0xFF0F0F12)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 180.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            if (state.alignedLyrics.isEmpty()) {
                                item {
                                    Text(
                                        text = strings.noLyrics,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.5f),
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
                                item {
                                    Spacer(modifier = Modifier.height(32.dp))
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
                trackTitle = currentTrack?.title?.takeIf { it.isNotBlank() } ?: if (state.cdConnectionState == CdConnectionState.CONNECTED && totalTracks > 0) "${strings.cdTrackFallback} ${String.format(java.util.Locale.getDefault(), "%02d", state.currentTrackIndex + 1)}" else "",
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
                onOpenCdTracklist = { viewModel.openCdTracklist(true) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
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

    if (state.isSettingsOpen) {
        SettingsDialog(
            aiPreferences = viewModel.aiPreferences,
            onDismiss = { viewModel.openSettings(false) },
            onSaved = {
                viewModel.openSettings(false)
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
            onDismiss = { viewModel.openCdSheet(false) },
            onOpenTracklist = {
                viewModel.openCdSheet(false)
                viewModel.openCdTracklist(true)
            }
        )
    }

    if (state.isCdTracklistOpen) {
        CdTracklistSheet(
            deviceName = state.cdDeviceName,
            connectionState = state.cdConnectionState,
            totalTracks = state.cdTotalTracks,
            currentTrackIndex = state.currentTrackIndex,
            cdPlaying = state.isCompanionPlaying,
            tracks = state.albumWithTracks?.tracks ?: emptyList(),
            album = state.albumWithTracks?.album,
            shelfAlbums = shelfAlbums,
            onSelectTrack = { trackIndex ->
                viewModel.playCdTrack(trackIndex)
            },
            onSwitchAlbum = { targetAlbumId ->
                viewModel.switchAlbum(targetAlbumId)
            },
            onSaveMatchedAlbum = { matchedAlbum, matchedTracks ->
                viewModel.saveAndBindMatchedAlbum(matchedAlbum, matchedTracks)
            },
            onDismiss = { viewModel.openCdTracklist(false) }
        )
    }

    if (isCoverViewerOpen && !state.albumWithTracks?.album?.coverUrl.isNullOrBlank()) {
        val cover = state.albumWithTracks?.album?.coverUrl!!
        Dialog(onDismissRequest = { isCoverViewerOpen = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1B1B20),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .shadow(elevation = 24.dp, shape = RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(cover)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.albumWithTracks?.album?.title ?: "",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.albumWithTracks?.album?.artist ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.70f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
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
    onOpenCdTracklist: () -> Unit = {},
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
                .bouncyClickable(pressedScale = 0.94f) { onOpenCdSheet() }
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
                    BouncyTonalButton(
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

                    BouncyTonalButton(
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
                // 顶部平滑时间进度调节 (Draggable Slider & Timestamps)
                var isDragging by remember { mutableStateOf(false) }
                var dragPositionMs by remember { mutableStateOf(0L) }

                val displayPosMs = if (isDragging) dragPositionMs else currentPosMs
                val maxDuration = durationMs.coerceAtLeast(1L)
                val sliderPos = (displayPosMs.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = formatTime(displayPosMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(38.dp)
                    )
                    Slider(
                        value = sliderPos,
                        onValueChange = { frac ->
                            isDragging = true
                            dragPositionMs = (frac * maxDuration).toLong()
                        },
                        onValueChangeFinished = {
                            onSeek(dragPositionMs)
                            isDragging = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .padding(horizontal = 4.dp)
                    )
                    Text(
                        text = formatTime(durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.width(38.dp),
                        textAlign = TextAlign.End
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 控制按钮行
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 上一曲（圆形）
                    BouncyIconButton(
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

                    // 核心高亮播放/暂停大圆纽 (Gemini 标志性大圆形按钮，带弹簧物理深潜与弹性回弹)
                    val playInteractionSource = remember { MutableInteractionSource() }
                    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
                    val heroScale by animateFloatAsState(
                        targetValue = if (isPlayPressed) 0.86f else if (isPlaying) 1.05f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "heroScale"
                    )
                    val heroAlpha by animateFloatAsState(
                        targetValue = if (isPlayPressed) 0.82f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "heroAlpha"
                    )

                    Surface(
                        onClick = onTogglePlay,
                        interactionSource = playInteractionSource,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(50.dp)
                            .scale(heroScale)
                            .graphicsLayer { alpha = heroAlpha }
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
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }

                    // 下一曲（圆形）
                    BouncyIconButton(
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

                    // 曲名与音轨胶囊（可点击快速展开曲目列表）
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .bouncyClickable(pressedScale = 0.96f) { onOpenCdTracklist() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (cdConnectionState == CdConnectionState.CONNECTED) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(end = 4.dp)
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
                                text = if (trackTitle.isNotBlank()) "$trackNumber. $trackTitle" else "Track $trackNumber",
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

                    // CD 实时曲目清单快捷按钮 (QueueMusic)
                    BouncyIconButton(
                        onClick = onOpenCdTracklist,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = strings.cdTracklistTitle,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 校准微调芯片（圆形）
                    BouncyIconButton(
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
        Spacer(modifier = Modifier.height(28.dp))
        return
    }

    val lineInteractionSource = remember { MutableInteractionSource() }
    val isLinePressed by lineInteractionSource.collectIsPressedAsState()

    val alphaAnim by animateFloatAsState(
        targetValue = if (isLinePressed) 0.65f else if (isActive) 1.0f else if (isCompanionPlaying) 0.38f else 0.80f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "lyricAlpha"
    )

    val scaleAnim by animateFloatAsState(
        targetValue = if (isLinePressed) 0.97f else if (isActive) 1.03f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "lyricScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = lineInteractionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = if (mode == LyricDisplayMode.BILINGUAL) 12.dp else 8.dp)
            .graphicsLayer {
                alpha = alphaAnim
                scaleX = scaleAnim
                scaleY = scaleAnim
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
        horizontalAlignment = Alignment.Start
    ) {
        when (mode) {
            LyricDisplayMode.BILINGUAL -> {
                if (line.original.isNotBlank()) {
                    Text(
                        text = line.original,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 36.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = if (isActive) Color.White else Color.White.copy(alpha = if (isCompanionPlaying) 0.40f else 0.65f),
                        textAlign = TextAlign.Start
                    )
                }
                if (line.translation.isNotBlank()) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = line.translation,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 24.sp,
                            letterSpacing = 0.2.sp
                        ),
                        color = if (isActive) Color(0xFFEDE8E3).copy(alpha = 0.88f) else Color.White.copy(alpha = if (isCompanionPlaying) 0.28f else 0.45f),
                        textAlign = TextAlign.Start
                    )
                }
            }

            LyricDisplayMode.ORIGINAL_ONLY -> {
                if (line.original.isNotBlank()) {
                    Text(
                        text = line.original,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 38.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = if (isActive) Color.White else Color.White.copy(alpha = if (isCompanionPlaying) 0.40f else 0.65f),
                        textAlign = TextAlign.Start
                    )
                }
            }

            LyricDisplayMode.TRANSLATED_ONLY -> {
                if (line.translation.isNotBlank()) {
                    Text(
                        text = line.translation,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 32.sp
                        ),
                        color = if (isActive) Color.White else Color.White.copy(alpha = if (isCompanionPlaying) 0.40f else 0.65f),
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}


