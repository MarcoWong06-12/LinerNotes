package com.linernotes.app.core.lyric

import com.linernotes.app.core.util.ChineseConverter
import com.linernotes.app.core.util.HtmlUtils
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.domain.model.BilingualLyricLine

object LyricFragmentMatcher {

    private val PUNCTUATION_REGEX = Regex("""[,\.\?!\-\'\"“”‘’\(\)\[\]{}，。？！、“”‘’…—~：；:;·]+""")
    private val SECTION_HEADER_REGEX = Regex("""^\[(?:verse|chorus|hook|bridge|intro|outro|pre-chorus|break|interlude).*?\]$""", RegexOption.IGNORE_CASE)
    private val WHITESPACE_REGEX = Regex("""\s+""")

    /**
     * 将典故注释列表精确或模糊锚定至当前歌词行列表。
     * 返回以歌词行索引 (lineIndex) 为键、关联注释实体为值的映射表。
     */
    fun matchAnnotationsToLines(
        lines: List<BilingualLyricLine>,
        annotations: List<LyricAnnotationEntity>
    ): Map<Int, LyricAnnotationEntity> {
        if (lines.isEmpty() || annotations.isEmpty()) return emptyMap()

        val result = mutableMapOf<Int, LyricAnnotationEntity>()
        val normalizedLines = lines.map { line ->
            normalizeText(line.original)
        }

        for (annotation in annotations) {
            val frag = annotation.lyricFragment.trim()
            if (frag.isBlank()) continue

            // 检查是否为多行歌词片段
            val fragLines = frag.lines()
                .map { normalizeText(it) }
                .filter { it.isNotBlank() && !SECTION_HEADER_REGEX.matches(it) }

            if (fragLines.isEmpty()) {
                // 如果 fragment 本身是纯节标题（如 [Verse 3: Big Boi]），尝试直接对齐原词
                val normSingle = normalizeText(frag)
                matchSingleSegment(normSingle, normalizedLines, annotation, result)
            } else {
                for (normFragLine in fragLines) {
                    matchSingleSegment(normFragLine, normalizedLines, annotation, result)
                }
            }

            // 支持多行片段跨行滑动窗口比对 (2行连续歌词对齐)
            val fullNormFrag = normalizeText(frag)
            if (fragLines.size > 1 && fullNormFrag.length >= 10 && normalizedLines.size >= 2) {
                for (i in 0 until normalizedLines.size - 1) {
                    val line1 = normalizedLines[i]
                    val line2 = normalizedLines[i + 1]
                    if (line1.isBlank() || line2.isBlank()) continue
                    val combined = "$line1 $line2"
                    if (fullNormFrag.contains(combined) || combined.contains(fullNormFrag)) {
                        if (result[i] == null || result[i]!!.lyricFragment.length < annotation.lyricFragment.length) {
                            result[i] = annotation
                        }
                    }
                }
            }
        }

        return result
    }

    private fun matchSingleSegment(
        normFrag: String,
        normalizedLines: List<String>,
        annotation: LyricAnnotationEntity,
        result: MutableMap<Int, LyricAnnotationEntity>
    ) {
        if (normFrag.length < 3) return

        for (i in normalizedLines.indices) {
            val normLine = normalizedLines[i]
            if (normLine.isBlank()) continue

            // 1. 完全或相互包含匹配
            if (normLine == normFrag ||
                (normLine.length >= 4 && normFrag.contains(normLine)) ||
                (normFrag.length >= 4 && normLine.contains(normFrag))
            ) {
                // 仅当当前行未被更长的注释占用时填充
                val existing = result[i]
                if (existing == null || existing.lyricFragment.length < annotation.lyricFragment.length) {
                    result[i] = annotation
                }
                continue
            }

            // 2. 单词词元交集匹配 (Word Token Overlap)
            val lineWords = normLine.split(WHITESPACE_REGEX).filter { it.length >= 2 }
            val fragWords = normFrag.split(WHITESPACE_REGEX).filter { it.length >= 2 }

            if (lineWords.size >= 3 && fragWords.size >= 3) {
                val matchedWords = lineWords.count { fragWords.contains(it) }
                val ratio = matchedWords.toFloat() / lineWords.size.toFloat()
                if (ratio >= 0.60f) {
                    val existing = result[i]
                    if (existing == null || existing.lyricFragment.length < annotation.lyricFragment.length) {
                        result[i] = annotation
                    }
                }
            }
        }
    }

    fun unescapeHtml(text: String): String = HtmlUtils.unescapeHtml(text)

    fun normalizeText(text: String): String {
        val unescaped = unescapeHtml(text)
        val simplified = ChineseConverter.toSimplified(unescaped)
        return simplified
            .replace(Regex("""\[.*?\]"""), " ") // 移除 [Verse 1]
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('“', '"')
            .replace('”', '"')
            .replace(PUNCTUATION_REGEX, " ")
            .lowercase()
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
