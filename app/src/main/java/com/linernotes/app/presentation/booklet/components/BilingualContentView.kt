package com.linernotes.app.presentation.booklet.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linernotes.app.core.lyric.AiAnnotationCurator

enum class BilingualDisplayTab {
    PARALLEL, // 📖 中英对照
    CHINESE,  // 🇨🇳 纯中文
    ORIGINAL  // 🔤 纯英文
}

data class AlignedParagraph(
    val original: String,
    val translation: String?
)

object ParagraphAligner {
    fun align(original: String, translation: String?): List<AlignedParagraph> {
        val origParagraphs = original.split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (origParagraphs.isEmpty()) {
            return if (original.isNotBlank()) listOf(AlignedParagraph(original.trim(), translation?.trim())) else emptyList()
        }

        if (translation.isNullOrBlank()) {
            return origParagraphs.map { AlignedParagraph(it, null) }
        }

        val transParagraphs = translation.split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // 若段落数一致，进行 1:1 精确段落配对
        if (origParagraphs.size == transParagraphs.size) {
            return origParagraphs.zip(transParagraphs) { o, t -> AlignedParagraph(o, t) }
        }

        // 若原文仅有 1 段而译文被拆分，合并译文
        if (origParagraphs.size == 1) {
            return listOf(AlignedParagraph(origParagraphs[0], translation.trim()))
        }

        // 若译文按单换行拆分与原文段落匹配
        val altTrans = translation.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (origParagraphs.size == altTrans.size) {
            return origParagraphs.zip(altTrans) { o, t -> AlignedParagraph(o, t) }
        }

        // 兜底：按索引对齐
        return origParagraphs.mapIndexed { idx, o ->
            AlignedParagraph(o, transParagraphs.getOrNull(idx))
        }
    }
}

/**
 * 专业段落级双语对照视图
 * 支持 [ 📖 中英对照 | 🇨🇳 纯中文 | 🔤 纯英文 ] 自由平滑切换，
 * 彻底消除“英文在上面、中文在下面”无法对照阅读的痛点。
 */
@Composable
fun BilingualContentView(
    originalText: String,
    translatedText: String?,
    isTranslating: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (originalText.isBlank()) return

    val isAlreadyChinese = remember(originalText) {
        AiAnnotationCurator.isAlreadyChinese(originalText)
    }

    val hasTranslation = !translatedText.isNullOrBlank()

    // 默认展示中英对照模式（若原文已是中文则无需对照标签）
    var selectedTab by remember(hasTranslation) {
        mutableStateOf(if (hasTranslation) BilingualDisplayTab.PARALLEL else BilingualDisplayTab.ORIGINAL)
    }

    val paragraphs = remember(originalText, translatedText) {
        ParagraphAligner.align(originalText, translatedText)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部切换控制器 (仅当原文非中文且有翻译或翻译中时显示)
        if (!isAlreadyChinese && hasTranslation) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TabItem(
                    title = "📖 中英对照",
                    isSelected = selectedTab == BilingualDisplayTab.PARALLEL,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = BilingualDisplayTab.PARALLEL }
                )
                TabItem(
                    title = "🇨🇳 纯中文",
                    isSelected = selectedTab == BilingualDisplayTab.CHINESE,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = BilingualDisplayTab.CHINESE }
                )
                TabItem(
                    title = "🔤 纯英文",
                    isSelected = selectedTab == BilingualDisplayTab.ORIGINAL,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = BilingualDisplayTab.ORIGINAL }
                )
            }
        }

        // 正在自动翻译中的轻量状态提示
        AnimatedVisibility(visible = isTranslating) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "唱片学者正在自动为您生成精准中文对照释义...",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 若原文已经是纯中文，直接以高雅内页版式渲染
        if (isAlreadyChinese) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                paragraphs.forEach { p ->
                    Text(
                        text = p.original,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            letterSpacing = 0.2.sp
                        ),
                        color = Color(0xFFEDEDED)
                    )
                }
            }
            return@Column
        }

        // 按照当前模式逐段展示
        when (selectedTab) {
            BilingualDisplayTab.PARALLEL -> {
                // 1. 中英段落级精准并排对照 (Paragraph-by-Paragraph Interleaved)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    paragraphs.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.04f),
                            border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 原文段落
                                Text(
                                    text = item.original,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.5.sp,
                                        lineHeight = 22.sp,
                                        color = Color.White.copy(alpha = 0.88f)
                                    )
                                )

                                // 中文译文段落
                                if (!item.translation.isNullOrBlank()) {
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFFFD54F).copy(alpha = 0.20f),
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Text(
                                                text = "译",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFFFD54F)
                                                ),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                        Text(
                                            text = item.translation,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 14.sp,
                                                lineHeight = 22.sp,
                                                color = Color(0xFFFFE082).copy(alpha = 0.95f)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            BilingualDisplayTab.CHINESE -> {
                // 2. 纯中文流畅阅读视图
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    paragraphs.forEach { item ->
                        val text = item.translation?.takeIf { it.isNotBlank() } ?: item.original
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                lineHeight = 24.sp
                            ),
                            color = Color(0xFFF0F0F0)
                        )
                    }
                }
            }

            BilingualDisplayTab.ORIGINAL -> {
                // 3. 纯英文原文视图
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    paragraphs.forEach { item ->
                        Text(
                            text = item.original,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                lineHeight = 23.sp
                            ),
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else Color.Transparent,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.60f)
                )
            )
        }
    }
}
