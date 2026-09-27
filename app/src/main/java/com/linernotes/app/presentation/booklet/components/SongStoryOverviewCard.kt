package com.linernotes.app.presentation.booklet.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.linernotes.app.core.lyric.AiAnnotationCurator
import com.linernotes.app.data.local.entity.SongStoryEntity
import com.linernotes.app.presentation.common.BouncyTonalButton

@Composable
fun SongStoryOverviewCard(
    story: SongStoryEntity?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    isTranslating: Boolean = false,
    onTranslate: (SongStoryEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (story == null || story.descriptionPlain.isBlank()) return

    val isChinese = remember(story.descriptionPlain) {
        AiAnnotationCurator.isAlreadyChinese(story.descriptionPlain)
    }

    val hasTranslation = !story.descriptionTranslation.isNullOrBlank()
    var showTranslation by remember(story.trackId, story.descriptionTranslation) {
        mutableStateOf(hasTranslation)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1E1E26).copy(alpha = 0.85f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring())
            .clickable { onToggleExpand() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 顶部横幅图片 (若有 headerImageUrl)
            if (!story.headerImageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    AsyncImage(
                        model = story.headerImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // 标题行与折叠图标
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "关于《${story.title}》· 创作心境与背景",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            if (!story.releaseDate.isNullOrBlank() || !story.producerCredits.isNullOrBlank()) {
                                val metaText = listOfNotNull(
                                    story.releaseDate?.let { "发行于 $it" },
                                    story.producerCredits?.let { "制作: $it" }
                                ).joinToString(" • ")
                                Text(
                                    text = metaText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = Color.White.copy(alpha = 0.55f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 故事文本（折叠时最多显示 3 行，展开显示全部）
                val displayText = if (!isChinese && showTranslation && hasTranslation) {
                    story.descriptionTranslation!!
                } else {
                    story.descriptionPlain
                }

                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    ),
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )

                // 展开状态下：仅当故事非中文时才提供翻译与原文切换控制
                AnimatedVisibility(visible = isExpanded && !isChinese) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        if (!hasTranslation) {
                            // 未翻译状态：提供一键翻译按钮
                            BouncyTonalButton(
                                onClick = { onTranslate(story) },
                                shape = RoundedCornerShape(10.dp),
                                enabled = !isTranslating,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                            ) {
                                if (isTranslating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("正在翻译背景故事...", fontSize = 12.sp)
                                } else {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("一键中文翻译", fontSize = 12.sp)
                                }
                            }
                        } else {
                            // 已翻译状态：提供双向自由切换段 [ 原文 | 中文释义 ]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    onClick = { showTranslation = false },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (!showTranslation) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                        Text(
                                            text = "英文原文",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (!showTranslation) FontWeight.Bold else FontWeight.Normal,
                                                color = if (!showTranslation) Color.White else Color.White.copy(alpha = 0.6f)
                                            )
                                        )
                                    }
                                }
                                Surface(
                                    onClick = { showTranslation = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (showTranslation) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                        Text(
                                            text = "中文释义",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (showTranslation) FontWeight.Bold else FontWeight.Normal,
                                                color = if (showTranslation) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
