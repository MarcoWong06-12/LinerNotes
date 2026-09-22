package com.linernotes.app.data.remote

import com.linernotes.app.core.preference.AiPreferences

object UnifiedLyricsService {

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
            AiPreferences.LyricsSourcePreference.LRCLIB_ONLY -> {
                LrclibLyricsService.fetchLyrics(trackTitle, artistName)
            }
            AiPreferences.LyricsSourcePreference.AI_ONLY -> {
                null
            }
            AiPreferences.LyricsSourcePreference.AUTO_FIRST -> {
                // 智能多源聚合回退 (Smart Multi-Source Aggregator Pipeline)
                // 1. 优先尝试网易云音乐 (官方人工精翻覆盖率最高，毫秒级时间戳精准)
                var bestCandidate: OnlineLyricsResult? = null

                val neteaseRes = NetEaseLyricsService.fetchLyrics(trackTitle, artistName)
                if (neteaseRes != null && neteaseRes.originalLyrics.isNotBlank()) {
                    if (neteaseRes.isBilingual) {
                        return neteaseRes
                    }
                    bestCandidate = neteaseRes
                }

                // 2. 尝试 QQ 音乐 (曲库海量，拥有官方双语及大曲库)
                val qqRes = QQMusicLyricsService.fetchLyrics(trackTitle, artistName)
                if (qqRes != null && qqRes.originalLyrics.isNotBlank()) {
                    if (qqRes.isBilingual) {
                        return qqRes
                    }
                    if (bestCandidate == null) bestCandidate = qqRes
                }

                // 3. 尝试 酷狗音乐 (原版精准时间轴歌词庞大)
                val kugouRes = KugouLyricsService.fetchLyrics(trackTitle, artistName)
                if (kugouRes != null && kugouRes.originalLyrics.isNotBlank()) {
                    if (kugouRes.isBilingual) {
                        return kugouRes
                    }
                    if (bestCandidate == null) bestCandidate = kugouRes
                }

                // 4. 尝试 LRCLIB (全球国际开源同步歌词库)
                if (bestCandidate == null) {
                    val lrclibRes = LrclibLyricsService.fetchLyrics(trackTitle, artistName)
                    if (lrclibRes != null && lrclibRes.originalLyrics.isNotBlank()) {
                        bestCandidate = lrclibRes
                    }
                }

                bestCandidate
            }
        }
    }
}
