package com.linernotes.app.presentation.booklet.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import com.linernotes.app.core.util.ChineseConverter

enum class BilingualDisplayTab {
    PARALLEL, // 中英对照
    CHINESE,  // 纯中文
    ORIGINAL  // 纯英文
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
 * 支持 [ 中英对照 | 纯中文 | 纯英文 ] 自由平滑切换。
 * 排版与歌词翻译一致，英文段落下方紧跟中文译文，去除冗余卡片框与 Emoji，简洁高雅。
 * 完整支持一键繁体中文切换。
 */
@Composable
fun BilingualContentView(
    originalText: String,
    translatedText: String?,
    isTranslating: Boolean = false,
    isTraditional: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (originalText.isBlank()) return

    val isAlreadyChinese = remember(originalText) {
        AiAnnotationCurator.isAlreadyChinese(originalText)
    }

    val effectiveTranslation = remember(translatedText, isTraditional) {
        if (translatedText.isNullOrBlank()) null
        else if (isTraditional) ChineseConverter.toTraditional(translatedText)
        else translatedText
    }

    val hasTranslation = !effectiveTranslation.isNullOrBlank()

    // 默认展示中英对照模式（若原文已是中文则直接展示）
    var selectedTab by remember(hasTranslation) {
        mutableStateOf(if (hasTranslation) BilingualDisplayTab.PARALLEL else BilingualDisplayTab.ORIGINAL)
    }

    val paragraphs = remember(originalText, effectiveTranslation) {
        ParagraphAligner.align(originalText, effectiveTranslation)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 顶部切换控制器 (仅当原文非中文且有翻译时显示，简洁无 Emoji)
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
                    title = if (isTraditional) "中英對照" else "中英对照",
                    isSelected = selectedTab == BilingualDisplayTab.PARALLEL,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = BilingualDisplayTab.PARALLEL }
                )
                TabItem(
                    title = if (isTraditional) "純中文" else "纯中文",
                    isSelected = selectedTab == BilingualDisplayTab.CHINESE,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = BilingualDisplayTab.CHINESE }
                )
                TabItem(
                    title = if (isTraditional) "純英文" else "纯英文",
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
                        text = if (isTraditional) "正在為您生成精準中文對照釋義..." else "正在为您生成精准中文对照释义...",
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
                    val text = if (isTraditional) ChineseConverter.toTraditional(p.original) else p.original
                    Text(
                        text = text,
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
                // 1. 中英段落级精准并排对照：英文段落下方紧跟中文译文，去除了冗余卡片框，像歌词翻译一样自然优美
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    paragraphs.forEach { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            // 英文原文段落
                            Text(
                                text = item.original,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    lineHeight = 23.sp,
                                    color = Color.White.copy(alpha = 0.90f)
                                )
                            )

                            // 中文译文段落：英文下面直接跟着中文
                            if (!item.translation.isNullOrBlank()) {
                                Text(
                                    text = item.translation,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.5.sp,
                                        lineHeight = 23.sp,
                                        color = Color(0xFFFFE082).copy(alpha = 0.90f)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            BilingualDisplayTab.CHINESE -> {
                // 2. 纯中文流畅阅读视图
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
