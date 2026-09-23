package com.linernotes.app.core.lyric

import java.util.regex.Pattern

/**
 * 歌词与曲目标题反审查/反和谐还原引擎 (Lyric Sanitizer / Decensoring Engine)
 *
 * 针对国内流媒体平台审查机制中对脏话和敏感词的掩码替换（如 B***h, *****, ****ing, n****s），
 * 在完全保留高品质双语翻译与时间戳的同时，毫秒级还原原版英文词汇（如 Bitch, fucking, niggas）。
 */
object LyricSanitizer {

    private val PROFANITY_LIST = listOf(
        "fuck", "fucking", "fucked", "fucker", "fuckers", "fucks",
        "motherfucker", "motherfuckers", "motherfucking",
        "bitch", "bitches", "bitching", "bitchy",
        "shit", "shits", "shitty", "bullshit", "horseshit", "dipshit",
        "nigga", "niggas", "niggaz", "nigger", "niggers",
        "ass", "asses", "asshole", "assholes", "badass", "jackass", "dumbass",
        "dick", "dicks", "dickhead",
        "cock", "cocks", "cocksucker",
        "pussy", "pussies",
        "cunt", "cunts",
        "bastard", "bastards",
        "slut", "sluts", "whore", "whores",
        "damn", "damned", "goddamn"
    )

    private val CENSOR_CHECK_REGEX = Regex("""[a-zA-Z]*\*+[a-zA-Z]*|\*{2,}""")
    private val WORD_TOKEN_REGEX = Regex("""[\w'’*]+""")
    private val TIMESTAMP_REGEX = Regex("""\[\d{2}:\d{2}(?:\.\d{1,3})?\]""")

    /**
     * 判断文本中是否包含审查掩码星号
     */
    fun hasCensorship(text: String?): Boolean {
        if (text.isNullOrBlank() || !text.contains('*')) return false
        return CENSOR_CHECK_REGEX.containsMatchIn(text)
    }

    private val PRIORITY_PROFANITIES = listOf(
        "fuck", "fucking", "fucked", "shit", "bitch", "nigga", "niggas", "ass", "dick", "pussy", "cunt", "damn"
    )

    /**
     * 单个词的本地字典反和谐匹配 (支持 B***h, f***ing, ****ing, s***, n**** 等)
     */
    fun matchFromDictionary(token: String): String? {
        if (!token.contains('*')) return null
        val letters = token.filter { it.isLetter() }
        // 纯星号（如 *****）必须交由参考歌词对齐，词典不武断猜测
        if (letters.isEmpty()) return null

        val clean = token.lowercase()
        val regexStr = "^" + clean.replace("*", ".") + "$"
        val pattern = Pattern.compile(regexStr)

        val candidates = PROFANITY_LIST.filter { pattern.matcher(it).matches() }
        val matched = if (candidates.size == 1) {
            candidates.first()
        } else if (candidates.size > 1) {
            candidates.firstOrNull { it in PRIORITY_PROFANITIES } ?: candidates.first()
        } else {
            return null
        }

        return when {
            letters.all { it.isUpperCase() } -> matched.uppercase()
            letters.first().isUpperCase() -> matched.replaceFirstChar { it.uppercase() }
            else -> matched
        }
    }

    /**
     * 单行歌词或标题反和谐
     * @param censoredLine 包含掩码的行 (如 "Life's a B***h" 或 "when you're gonna go Life's a ***** and then you die;")
     * @param uncensoredRefLine 可选的无审查参考行 (如来自 LRCLIB)
     */
    fun decensorLine(censoredLine: String, uncensoredRefLine: String? = null): String {
        if (!hasCensorship(censoredLine)) return censoredLine

        // 阶段 1：使用本地词典对可推断词（如 B***h, ****ing, s***, n****）进行精准替换
        val stage1 = censoredLine.replace(CENSOR_CHECK_REGEX) { matchResult ->
            val tok = matchResult.value
            matchFromDictionary(tok) ?: tok
        }

        if (!stage1.contains('*')) return stage1
        if (uncensoredRefLine.isNullOrBlank()) return stage1

        // 阶段 2：针对纯星号（如 *****）结合无审查参考行进行词级别对应替换
        val cleanRefLine = uncensoredRefLine.replace(TIMESTAMP_REGEX, "").trim()
        val refWords = WORD_TOKEN_REGEX.findAll(cleanRefLine).map { it.value }.toList()
        if (refWords.isEmpty()) return stage1

        // 提取原行的起始时间戳标签（若有）
        val tsMatch = TIMESTAMP_REGEX.find(stage1)
        val tsPrefix = tsMatch?.value
        val stage1Text = if (tsPrefix != null) stage1.replace(TIMESTAMP_REGEX, "").trim() else stage1

        val sb = StringBuilder()
        var lastEnd = 0
        for ((wordIdx, wordMatch) in WORD_TOKEN_REGEX.findAll(stage1Text).withIndex()) {
            sb.append(stage1Text.substring(lastEnd, wordMatch.range.first))
            val word = wordMatch.value
            if (word.contains('*')) {
                val rep = if (wordIdx < refWords.size) {
                    refWords[wordIdx]
                } else {
                    refWords.firstOrNull { Math.abs(it.length - word.length) <= 1 } ?: word
                }
                sb.append(rep)
            } else {
                sb.append(word)
            }
            lastEnd = wordMatch.range.last + 1
        }
        if (lastEnd < stage1Text.length) {
            sb.append(stage1Text.substring(lastEnd))
        }

        val textResult = sb.toString()
        return if (tsPrefix != null) "$tsPrefix $textResult" else textResult
    }

    /**
     * 歌曲标题反和谐 (如 "Life's a B***h" -> "Life's a Bitch")
     */
    fun decensorTitle(title: String, refTitle: String? = null): String {
        return decensorLine(title, refTitle)
    }

    /**
     * 整首歌曲歌词反和谐
     * @param censoredLyrics 包含审查掩码的歌词文本 (支持 LRC 时间轴格式)
     * @param uncensoredRefLyrics 可选的无审查全球参考歌词 (如 LRCLIB 返回的英文纯净歌词)
     */
    fun decensorLyrics(censoredLyrics: String, uncensoredRefLyrics: String? = null): String {
        if (!hasCensorship(censoredLyrics)) return censoredLyrics

        val cLines = censoredLyrics.lines()
        if (uncensoredRefLyrics.isNullOrBlank()) {
            return cLines.joinToString("\n") { decensorLine(it, null) }
        }

        val rLines = uncensoredRefLyrics.lines()

        // 构建参考行提取器（按时间戳优先匹配，否则按顺序匹配）
        val refMapByTimestamp = mutableMapOf<String, String>()
        val pureRefLines = mutableListOf<String>()

        for (rl in rLines) {
            val tsMatch = TIMESTAMP_REGEX.find(rl)
            if (tsMatch != null) {
                refMapByTimestamp[tsMatch.value] = rl
            }
            val cleaned = rl.replace(TIMESTAMP_REGEX, "").trim()
            if (cleaned.isNotBlank()) {
                pureRefLines.add(cleaned)
            }
        }

        var pureRefIdx = 0
        val sanitizedLines = cLines.map { cl ->
            val tsMatch = TIMESTAMP_REGEX.find(cl)
            val refLine = if (tsMatch != null && refMapByTimestamp.containsKey(tsMatch.value)) {
                refMapByTimestamp[tsMatch.value]
            } else if (cl.replace(TIMESTAMP_REGEX, "").trim().isNotBlank() && pureRefIdx < pureRefLines.size) {
                pureRefLines[pureRefIdx++]
            } else {
                null
            }

            decensorLine(cl, refLine)
        }

        return sanitizedLines.joinToString("\n")
    }
}
