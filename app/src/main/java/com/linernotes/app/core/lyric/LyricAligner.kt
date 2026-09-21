package com.linernotes.app.core.lyric

import com.linernotes.app.domain.model.BilingualLyricLine

object LyricAligner {

    private val LRC_TIMESTAMP_REGEX = Regex("""\[\d{2}:\d{2}(?:\.\d{2,3})?]""")

    /**
     * 将存储的纯文本或 LRC 歌词对齐解析为逐行双语模型。
     */
    fun align(originalRaw: String?, translatedRaw: String?): List<BilingualLyricLine> {
        if (originalRaw.isNullOrBlank() && translatedRaw.isNullOrBlank()) {
            return emptyList()
        }

        val cleanOriginal = sanitizeText(originalRaw)
        val cleanTranslated = sanitizeText(translatedRaw)

        val origLines = cleanOriginal.lines().map { cleanLine(it) }
        val transLines = cleanTranslated.lines().map { cleanLine(it) }

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
