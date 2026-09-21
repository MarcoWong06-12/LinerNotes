package com.linernotes.app.presentation.shelf.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.linernotes.app.data.local.entity.AlbumEntity
import com.linernotes.app.data.local.entity.TrackEntity
import com.linernotes.app.data.remote.MetadataService
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCdBottomSheet(
    onDismiss: () -> Unit,
    onSaveAlbum: (AlbumEntity, List<TrackEntity>) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var matchedCollectionId by remember { mutableLongStateOf(0L) }

    var title by remember { mutableStateOf("") }
    var translatedTitle by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var releaseYear by remember { mutableStateOf("") }
    var coverUrl by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // 系统相册图片选择器
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coverUrl = uri.toString()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "收纳新唱片入库",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(12.dp))

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("联网精准抓取") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("手动录入档案") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("搜索唱片 (如: Abbey Road / 周杰伦 / 1989)") },
                    placeholder = { Text("输入专辑名或艺术家搜索") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    isSearching = true
                                    searchError = null
                                    coroutineScope.launch {
                                        val result = MetadataService.searchAlbum(searchQuery)
                                        isSearching = false
                                        if (result != null) {
                                            title = result.title
                                            artist = result.artist
                                            releaseYear = result.releaseYear
                                            coverUrl = result.coverUrl
                                            matchedCollectionId = result.collectionId
                                            if (result.title.equals("Abbey Road", ignoreCase = true)) {
                                                translatedTitle = "修道院之路"
                                            }
                                        } else {
                                            searchError = "未搜索到对应专辑，请尝试更精确的名称"
                                        }
                                    }
                                }
                            },
                            enabled = searchQuery.isNotBlank() && !isSearching
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isSearching) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "正在联网抓取高清封面、曲目与年份...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (searchError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = searchError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 封面预览与相册选择
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "封面预览",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (coverUrl.isBlank()) "从相册选择封面" else "更换相册图片")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("专辑名 (原版)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = translatedTitle,
                onValueChange = { translatedTitle = it },
                label = { Text("中文译名 (可选)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("艺术家") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = releaseYear,
                    onValueChange = { releaseYear = it },
                    label = { Text("年份") },
                    modifier = Modifier.width(110.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = coverUrl,
                onValueChange = { coverUrl = it },
                label = { Text("封面图片链接 (支持网络链接或本地相册)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it },
                label = { Text("唱片条形码 / Catalog No. (可选)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("版本收藏备注 (如：日版首版、带侧封 OBI)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (title.isNotBlank() && artist.isNotBlank()) {
                        isSearching = true
                        val albumId = UUID.randomUUID().toString()
                        val album = AlbumEntity(
                            id = albumId,
                            title = title,
                            translatedTitle = translatedTitle.ifBlank { null },
                            artist = artist,
                            releaseYear = releaseYear.ifBlank { "未知年份" },
                            coverUrl = coverUrl,
                            barcode = barcode.ifBlank { null },
                            purchaseDate = System.currentTimeMillis(),
                            notes = notes.ifBlank { null }
                        )

                        coroutineScope.launch {
                            val tracks = if (matchedCollectionId > 0L) {
                                // 抓取真实完整曲目单及歌词库中的全篇歌词
                                MetadataService.fetchTracksWithLyrics(
                                    collectionId = matchedCollectionId,
                                    albumId = albumId,
                                    artistName = artist,
                                    albumTitle = title
                                )
                            } else {
                                // 手动录入时保底创建首轨
                                listOf(
                                    TrackEntity(
                                        albumId = albumId,
                                        trackNumber = 1,
                                        title = title,
                                        translatedTitle = translatedTitle.ifBlank { null },
                                        originalLyrics = "（可在歌词本页面点击右上角笔形图标编辑并录入歌词）",
                                        translatedLyrics = null
                                    )
                                )
                            }
                            isSearching = false
                            onSaveAlbum(album, tracks)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() && artist.isNotBlank() && !isSearching
            ) {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("正在同步全碟曲目与完整歌词...")
                } else {
                    Text("确认归档入架")
                }
            }
        }
    }
}
