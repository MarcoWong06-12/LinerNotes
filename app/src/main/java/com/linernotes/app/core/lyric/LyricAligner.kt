package com.linernotes.app.core.lyric

import com.linernotes.app.domain.model.BilingualLyricLine

object LyricAligner {

    private val LRC_TIMESTAMP_REGEX = Regex("""\[\d{2}:\d{2}(?:\.\d{2,3})?]""")

    fun isRefusalText(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val refusalMarkers = listOf(
            "无法逐行翻译", "無法逐行翻譯",
            "受版权保护", "受版權保護",
            "版权原因", "版權原因",
            "侵犯版权", "侵犯版權",
            "版权所有", "版權所有",
            "90 个字符", "90 個字元", "90个字符", "90個字元",
            "主题摘要", "主題摘要", "意象与情绪", "意象與情緒",
            "copyrighted", "copyright protection", "copyright infringement",
            "cannot translate", "unable to translate", "cannot reproduce"
        )
        val lower = text.lowercase()
        if (refusalMarkers.any { lower.contains(it) }) {
            return true
        }
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size in 1..3) {
            val apologyPrefixes = listOf(
                "抱歉", "對不起", "对不起", "很抱歉", "非常抱歉",
                "sorry", "i apologize", "as an ai", "i cannot"
            )
            if (apologyPrefixes.any { lines.first().startsWith(it, ignoreCase = true) }) {
                return true
            }
        }
        return false
    }

    private val LRC_METADATA_REGEX = Regex("""^\[(ti|ar|al|by|offset|length|re|ve|encoding):.*?]""", RegexOption.IGNORE_CASE)

    /**
     * 将存储的纯文本或 LRC 歌词对齐解析为逐行双语模型。
     * 具备智能段落容错对齐：彻底杜绝由于模型遗漏空行导致后续全部错位（“不齐”）的问题。
     */
    fun align(originalRaw: String?, translatedRaw: String?): List<BilingualLyricLine> {
        if (originalRaw.isNullOrBlank() && translatedRaw.isNullOrBlank()) {
            return emptyList()
        }

        val cleanOriginal = sanitizeText(originalRaw)
        val cleanTranslated = sanitizeText(translatedRaw)

        // 若本地存储的内容实际上是 AI 触发版权限制后的拒识文本，自动视为空，避免污染歌词界面
        val safeTranslated = if (isRefusalText(cleanTranslated)) "" else cleanTranslated

        val origLines = cleanOriginal.lines().map { cleanLine(it) }
        val transLines = safeTranslated.lines().map { cleanLine(it) }

        if (transLines.isEmpty() || transLines.all { it.isBlank() }) {
            // 没有翻译时，纯净展示原文
            return origLines.mapIndexed { index, line ->
                BilingualLyricLine(
                    lineNumber = index + 1,
                    original = line,
                    translation = "",
                    isStanzaBreak = line.isBlank()
                )
            }
        }

        val isExactStructureMatch = origLines.size == transLines.size &&
            origLines.indices.all { i -> origLines[i].isBlank() == transLines[i].isBlank() }

        val result = ArrayList<BilingualLyricLine>()

        if (isExactStructureMatch) {
            // 结构与空行完全对齐时，按行一一对应
            for (i in origLines.indices) {
                val orig = origLines[i]
                val trans = transLines[i]
                result.add(
                    BilingualLyricLine(
                        lineNumber = i + 1,
                        original = orig,
                        translation = trans,
                        isStanzaBreak = orig.isBlank() && trans.isBlank()
                    )
                )
            }
        } else {
            // 智能段落容错对齐：
            // 大模型经常会漏掉原歌词中的空行（导致原歌词第 10 行空行与译文第 10 行文字错位，引发后面全部移位“不齐”）。
            // 策略：保留原歌词空行作为段落标记，原歌词的每一句非空歌词严格匹配译文的每一句非空歌词！
            val nonBlankTrans = transLines.filter { it.isNotBlank() }
            var transIdx = 0
            var lineNum = 1

            for (orig in origLines) {
                if (orig.isBlank()) {
                    result.add(
                        BilingualLyricLine(
                            lineNumber = lineNum++,
                            original = "",
                            translation = "",
                            isStanzaBreak = true
                        )
                    )
                } else {
                    val trans = if (transIdx < nonBlankTrans.size) nonBlankTrans[transIdx++] else ""
                    result.add(
                        BilingualLyricLine(
                            lineNumber = lineNum++,
                            original = orig,
                            translation = trans,
                            isStanzaBreak = false
                        )
                    )
                }
            }

            while (transIdx < nonBlankTrans.size) {
                result.add(
                    BilingualLyricLine(
                        lineNumber = lineNum++,
                        original = "",
                        translation = nonBlankTrans[transIdx++],
                        isStanzaBreak = false
                    )
                )
            }
        }

        return result
    }

    private fun sanitizeText(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val lines = text.lines().toMutableList()
        // 移除开头与结尾可能存在的 markdown 代码块标签（如 ``` 或 ```markdown）
        while (lines.isNotEmpty() && lines.first().trim().startsWith("```")) {
            lines.removeAt(0)
        }
        while (lines.isNotEmpty() && lines.last().trim().startsWith("```")) {
            lines.removeAt(lines.size - 1)
        }
        return lines.joinToString("\n")
    }

    private fun cleanLine(line: String): String {
        val withoutTime = line.replace(LRC_TIMESTAMP_REGEX, "").trim()
        if (withoutTime.matches(LRC_METADATA_REGEX)) {
            return ""
        }
        return withoutTime
    }
}
