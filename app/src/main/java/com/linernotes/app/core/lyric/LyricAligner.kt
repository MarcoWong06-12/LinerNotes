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

        val origLines = (originalRaw ?: "").lines().map { cleanLine(it) }
        val transLines = (translatedRaw ?: "").lines().map { cleanLine(it) }

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

    private fun cleanLine(line: String): String {
        return line.replace(LRC_TIMESTAMP_REGEX, "").trim()
    }
}
