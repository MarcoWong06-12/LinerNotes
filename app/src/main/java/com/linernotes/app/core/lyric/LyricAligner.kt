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

    /**
     * 将存储的纯文本或 LRC 歌词对齐解析为逐行双语模型。
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

        val maxLines = maxOf(origLines.size, transLines.size)
        val result = ArrayList<BilingualLyricLine>(maxLines)

        for (i in 0 until maxLines) {
            val orig = origLines.getOrElse(i) { "" }
            val trans = transLines.getOrElse(i) { "" }
            val isBlank = orig.isBlank() && trans.isBlank()

            result.add(
                BilingualLyricLine(
                    lineNumber = i + 1,
                    original = orig,
                    translation = trans,
                    isStanzaBreak = isBlank
                )
            )
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
        return line.replace(LRC_TIMESTAMP_REGEX, "").trim()
    }
}
