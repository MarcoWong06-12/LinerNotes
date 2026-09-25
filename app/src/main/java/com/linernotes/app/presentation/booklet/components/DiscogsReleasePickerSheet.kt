package com.linernotes.app.presentation.booklet.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DiscFull
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.linernotes.app.data.remote.DiscogsReleaseSummary
import com.linernotes.app.presentation.common.BouncyButton
import com.linernotes.app.presentation.common.BouncyIconButton
import com.linernotes.app.presentation.common.BouncyTonalButton
import com.linernotes.app.presentation.common.bouncyClickable
import com.linernotes.app.presentation.common.bouncyPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.linernotes.app.presentation.theme.VaultBlack
import com.linernotes.app.presentation.theme.VaultSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscogsReleasePickerSheet(
    initialQuery: String,
    results: List<DiscogsReleaseSummary>,
    isLoading: Boolean,
    currentAlbumCover: String? = null,
    discogsToken: String = "",
    onSearch: (String) -> Unit,
    onSelectRelease: (Long) -> Unit,
    onViewDetail: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf(initialQuery) }
    var selectedFilter by remember { mutableStateOf("全部") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val filters = listOf("全部", "🇯🇵 日本版", "🇺🇸 美版", "🇪🇺 欧版", "发烧规格 (SHM/SACD)", "纸套复刻 (Mini-LP)")

    val filteredResults = remember(results, selectedFilter) {
        when (selectedFilter) {
            "🇯🇵 日本版" -> results.filter { it.country.equals("Japan", ignoreCase = true) || it.primaryFormat == "SHM-CD" || it.primaryFormat == "BSCD2" }
            "🇺🇸 美版" -> results.filter { it.country.equals("US", ignoreCase = true) || it.country.equals("United States", ignoreCase = true) }
            "🇪🇺 欧版" -> results.filter { it.country.equals("Europe", ignoreCase = true) || it.country.equals("UK", ignoreCase = true) || it.country.equals("Germany", ignoreCase = true) }
            "发烧规格 (SHM/SACD)" -> results.filter { it.primaryFormat in listOf("SHM-CD", "SACD", "XRCD", "BSCD2", "HDCD") }
            "纸套复刻 (Mini-LP)" -> results.filter { it.primaryFormat.contains("纸套") || it.primaryFormat.contains("Mini-LP") || it.formats.any { f -> f.contains("Paper Sleeve", ignoreCase = true) } }
            else -> results
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VaultBlack,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.25f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(horizontal = 20.dp)
        ) {
            // 顶栏：标题与关闭
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.DiscFull,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "实体 CD 版本库 · Discogs",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "查找不同国家压盘、日版特典、厂牌及唱片编号",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                BouncyIconButton(
                    onClick = onDismiss,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }

            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                placeholder = { Text("搜索唱片名、艺术家或条形码/CatNo...", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboardController?.hide()
                    onSearch(searchQuery)
                }),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                    focusedContainerColor = VaultSurface,
                    unfocusedContainerColor = VaultSurface
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 筛选标签栏 (Filter Chips)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filters.forEach { filter ->
                    val chipInteractionSource = remember { MutableInteractionSource() }
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, fontSize = 12.sp) },
                        interactionSource = chipInteractionSource,
                        modifier = Modifier.bouncyPress(interactionSource = chipInteractionSource, pressedScale = 0.94f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 列表内容区
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "正在从 Discogs 检索全球实体 CD 版本...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            } else if (filteredResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Icon(
                            Icons.Default.Album,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (results.isEmpty()) "未找到相关实体 CD 版本" else "在当前筛选条件下无版本",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "尝试修改搜索词或清空筛选条件重新检索",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        BouncyTonalButton(onClick = { onSearch(searchQuery) }) {
                            Text("重新检索")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredResults, key = { it.id }) { release ->
                        DiscogsReleaseCard(
                            release = release,
                            currentAlbumCover = currentAlbumCover,
                            discogsToken = discogsToken,
                            onSelect = { onSelectRelease(release.id) },
                            onViewDetail = { onViewDetail(release.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscogsReleaseCard(
    release: DiscogsReleaseSummary,
    currentAlbumCover: String?,
    discogsToken: String,
    onSelect: () -> Unit,
    onViewDetail: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = VaultSurface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable { onViewDetail() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面缩略图 (优先 Release 自身封面，回退至当前专辑封面)
            val imgUrl = release.thumbUrl?.takeIf { it.isNotBlank() }
                ?: release.coverImageUrl?.takeIf { it.isNotBlank() }
                ?: currentAlbumCover?.takeIf { it.isNotBlank() }

            if (!imgUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imgUrl)
                        .addHeader("User-Agent", "LinerNotes/1.0 (Android; +https://github.com/MarcoWong06-12/LinerNotes)")
                        .apply {
                            if (discogsToken.isNotBlank()) addHeader("Authorization", "Discogs token=${discogsToken.trim()}")
                        }
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Album,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 版本规格详细信息
            Column(modifier = Modifier.weight(1f)) {
                // 格式标签 + 国家地区 + 年份
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (release.primaryFormat) {
                            "SHM-CD", "SACD", "XRCD", "BSCD2" -> MaterialTheme.colorScheme.primaryContainer
                            "Digipak CD", "Mini-LP 纸套 CD" -> MaterialTheme.colorScheme.secondaryContainer
                            else -> Color.White.copy(alpha = 0.12f)
                        }
                    ) {
                        Text(
                            text = release.primaryFormat,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = when (release.primaryFormat) {
                                "SHM-CD", "SACD", "XRCD", "BSCD2" -> MaterialTheme.colorScheme.primary
                                "Digipak CD", "Mini-LP 纸套 CD" -> MaterialTheme.colorScheme.secondary
                                else -> Color.White.copy(alpha = 0.85f)
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    val countryFlag = when (release.country?.lowercase()) {
                        "japan" -> "🇯🇵 日本"
                        "us", "united states" -> "🇺🇸 美国"
                        "europe" -> "🇪🇺 欧洲"
                        "uk", "united kingdom" -> "🇬🇧 英国"
                        "germany" -> "🇩🇪 德国"
                        "france" -> "🇫🇷 法国"
                        "taiwan" -> "🇹🇼 台湾"
                        "hong kong" -> "🇭🇰 香港"
                        else -> release.country ?: ""
                    }
                    if (countryFlag.isNotBlank()) {
                        Text(
                            text = countryFlag,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    if (!release.year.isNullOrBlank()) {
                        Text(
                            text = "· ${release.year}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 专辑与艺术家
                Text(
                    text = release.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!release.artist.isNullOrBlank()) {
                    Text(
                        text = release.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 厂牌与唱片编号 (CatNo)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!release.catno.isNullOrBlank()) {
                        Text(
                            text = release.catno,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (!release.label.isNullOrBlank()) {
                        Text(
                            text = release.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 详情与选用操作按钮组
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BouncyTonalButton(
                    onClick = onViewDetail,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("详情", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                BouncyButton(
                    onClick = onSelect,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("选用", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
