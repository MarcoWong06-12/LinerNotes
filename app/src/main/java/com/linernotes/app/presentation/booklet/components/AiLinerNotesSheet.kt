package com.linernotes.app.presentation.booklet.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linernotes.app.data.local.entity.AlbumEntity
import com.linernotes.app.data.local.entity.TrackEntity
import com.linernotes.app.presentation.common.BouncyIconButton
import com.linernotes.app.presentation.common.BouncyTonalButton
import com.linernotes.app.presentation.theme.VaultBlack
import com.linernotes.app.presentation.theme.VaultSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiLinerNotesSheet(
    album: AlbumEntity?,
    currentTrack: TrackEntity?,
    isGenerating: Boolean,
    onGenerateOrRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

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
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp)
        ) {
            // 顶栏：标题与关闭
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "AI 唱片策展人 · 深度导赏",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "《${currentTrack?.title ?: album?.title ?: ""}》 时代背景与隐喻解构",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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

            HorizontalDivider(color = Color.White.copy(alpha = 0.10f))

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 卡片 1: 时代思潮与创作转折点
                CurationCard(
                    icon = Icons.Default.HistoryEdu,
                    iconTint = MaterialTheme.colorScheme.primary,
                    sectionTag = "GENESIS & ERA",
                    title = "时代背景与创作心境",
                    content = "录制于 ${album?.releaseYear ?: "经典"} 年代。此时 ${album?.artist ?: "创作者"} 正处于个人艺术探索与风格转型的关键节点。作品巧妙融合了实体唱片黄金时代的母带模拟质感，既映射了当时社会变迁下的群体思潮，也注入了个体在情感与现实夹缝中的真挚独白。"
                )

                // 卡片 2: 歌词隐喻与文化彩蛋拆解
                CurationCard(
                    icon = Icons.Default.Psychology,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    sectionTag = "LYRICAL METAPHOR",
                    title = "歌词双关与隐秘彩蛋",
                    content = if (currentTrack?.originalLyrics.isNullOrBlank()) {
                        "当前曲目未加载歌词。在整张唱片中，反复出现的意象形成了独特的叙事闭环，用诗意隐喻代指了不可言说的追寻与失落。"
                    } else {
                        "在核心段落中，词作者运用了精妙的通感与双关语。原词以具象的日常物件起笔，实则构建了跨越时空的情感隐喻，配合韵脚的递进，将情绪推向无以言表的共鸣高潮。"
                    }
                )

                // 卡片 3: 编曲与器乐鉴赏要点 (发烧友耳朵指南)
                CurationCard(
                    icon = Icons.Default.Headphones,
                    iconTint = Color(0xFF64B5F6),
                    sectionTag = "SONIC HIGHLIGHTS",
                    title = "器乐编排与发烧鉴赏要点",
                    content = "建议佩戴高解析耳机或搭配发烧音响细听：\n• 声场空间感：开场采用极具景深感的声相定位，营造出录音室现场的物理空气感；\n• 动态层次：低频贝斯走线沉稳富有弹性，切分节奏精准咬合鼓点；\n• 人声结像：歌手声线质感极其靠前，齿音与胸腔共鸣细节纤毫毕现。"
                )
            }

            // 底部生成/重新策展按钮
            Surface(
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                BouncyTonalButton(
                    onClick = onGenerateOrRefresh,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI 策展人正在深度解析中...")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("重新深度解构当前唱片")
                    }
                }
            }
        }
    }
}

@Composable
private fun CurationCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    sectionTag: String,
    title: String,
    content: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = VaultSurface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                Text(
                    text = sectionTag,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = iconTint
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}
