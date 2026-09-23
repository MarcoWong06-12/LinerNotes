package com.linernotes.app.data.remote

import com.linernotes.app.core.preference.AiPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

object UnifiedLyricsService {

    private val TIMESTAMP_REGEX = Regex("""\[\d{2}:\d{2}(?:\.\d{1,3})?\]""")

    suspend fun fetchLyrics(
        trackTitle: String,
        artistName: String,
        sourcePref: String = AiPreferences.LyricsSourcePreference.AUTO_FIRST.code
    ): OnlineLyricsResult? {
        val pref = AiPreferences.LyricsSourcePreference.fromCode(sourcePref)

        return when (pref) {
            AiPreferences.LyricsSourcePreference.NETEASE_ONLY -> {
                NetEaseLyricsService.fetchLyrics(trackTitle, artistName)
            }
            AiPreferences.LyricsSourcePreference.QQ_ONLY -> {
                QQMusicLyricsService.fetchLyrics(trackTitle, artistName)
            }
            AiPreferences.LyricsSourcePreference.KUGOU_ONLY -> {
                KugouLyricsService.fetchLyrics(trackTitle, artistName)
            }
            AiPreferences.LyricsSourcePreference.MUSIXMATCH_ONLY -> {
                MusixmatchLyricsService.fetchLyrics(trackTitle, artistName)
            }
            AiPreferences.LyricsSourcePreference.LRCLIB_ONLY -> {
                LrclibLyricsService.fetchLyrics(trackTitle, artistName)
            }
            AiPreferences.LyricsSourcePreference.AUTO_FIRST -> {
                fetchAutoAggregated(trackTitle, artistName)
            }
        }
    }

    /**
     * 智能多源并发竞速检索 (High-Performance Parallel Multi-Source Engine)
     * 同步发起并发请求，通过加权评分锁定最优官方双语与高精度时间轴歌词。
     */
    private suspend fun fetchAutoAggregated(
        trackTitle: String,
        artistName: String
    ): OnlineLyricsResult? = supervisorScope {
        // 并发检索网易云与 QQ 音乐（具备官方双语翻译的最优源）
        val neteaseDeferred = async {
            withTimeoutOrNull(3500L) {
                NetEaseLyricsService.fetchLyrics(trackTitle, artistName)
            }
        }
        val qqDeferred = async {
            withTimeoutOrNull(3500L) {
                QQMusicLyricsService.fetchLyrics(trackTitle, artistName)
            }
        }
        // 并发检索酷狗与 LRCLIB（具备海量时间轴原版歌词的坚实后盾）
        val kugouDeferred = async {
            withTimeoutOrNull(3500L) {
                KugouLyricsService.fetchLyrics(trackTitle, artistName)
            }
        }
        val lrclibDeferred = async {
            withTimeoutOrNull(3500L) {
                LrclibLyricsService.fetchLyrics(trackTitle, artistName)
            }
        }

        val neteaseRes = neteaseDeferred.await()
        if (neteaseRes != null && neteaseRes.isBilingual && neteaseRes.originalLyrics.isNotBlank()) {
            return@supervisorScope neteaseRes
        }

        val qqRes = qqDeferred.await()
        if (qqRes != null && qqRes.isBilingual && qqRes.originalLyrics.isNotBlank()) {
            return@supervisorScope qqRes
        }

        val kugouRes = kugouDeferred.await()
        val lrclibRes = lrclibDeferred.await()

        // 收集所有有效候选结果
        val candidates = listOfNotNull(neteaseRes, qqRes, kugouRes, lrclibRes)
            .filter { it.originalLyrics.isNotBlank() }

        if (candidates.isEmpty()) {
            // 尝试轻量 Musixmatch 兜底
            return@supervisorScope withTimeoutOrNull(2500L) {
                MusixmatchLyricsService.fetchLyrics(trackTitle, artistName)
            }
        }

        // 质量评分系统：
        // 1. 双语官方精翻 (权重 +1000)
        // 2. 含有时间戳对齐 (权重 +500)
        // 3. 歌词行数丰满度
        candidates.maxByOrNull { res ->
            var score = 0
            if (res.isBilingual) score += 1000
            if (res.originalLyrics.contains(TIMESTAMP_REGEX)) score += 500
            score += (res.originalLyrics.lines().size).coerceAtMost(100)
            score
        }
    }
}
