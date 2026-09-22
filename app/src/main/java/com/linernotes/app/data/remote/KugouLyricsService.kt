package com.linernotes.app.data.remote

import android.util.Base64
import com.linernotes.app.core.lyric.LyricAligner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object KugouLyricsService {

    private const val SEARCH_API = "https://songsearch.kugou.com/song_search_v2"
    private const val CANDIDATE_API = "http://krcs.kugou.com/search"
    private const val DOWNLOAD_API = "http://krcs.kugou.com/download"

    suspend fun fetchLyrics(trackTitle: String, artistName: String): OnlineLyricsResult? = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = NetEaseLyricsService.cleanTrackTitle(trackTitle)
            val query = if (artistName.isNotBlank() && !artistName.equals("Unknown Artist", ignoreCase = true)) {
                "$cleanTitle $artistName"
            } else {
                cleanTitle
            }

            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val searchUrl = "$SEARCH_API?keyword=$encodedQuery&page=1&pagesize=5&platform=WebFilter"
            val searchHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            val searchJson = httpGet(searchUrl, searchHeaders) ?: return@withContext null
            val searchRoot = JSONObject(searchJson)
            val dataObj = searchRoot.optJSONObject("data") ?: return@withContext null
            val list = dataObj.optJSONArray("lists") ?: return@withContext null
            if (list.length() == 0) return@withContext null

            // 寻找候选歌曲
            var targetSong: JSONObject? = null
            val cleanArtist = artistName.trim().lowercase()

            for (i in 0 until list.length()) {
                val s = list.getJSONObject(i)
                val singer = s.optString("SingerName", "").lowercase()
                if (singer.contains(cleanArtist) || cleanArtist.contains(singer)) {
                    targetSong = s
                    break
                }
            }

            if (targetSong == null) {
                targetSong = list.getJSONObject(0)
            }

            val hash = targetSong.optString("FileHash", "")
            val duration = targetSong.optLong("Duration", 0L) * 1000L // 转换为毫秒
            val matchedTitle = targetSong.optString("SongName", trackTitle)
            val matchedArtist = targetSong.optString("SingerName", artistName)

            if (hash.isBlank()) return@withContext null

            // 检索歌词候选集
            val encTitle = URLEncoder.encode(cleanTitle, "UTF-8")
            val candUrl = "$CANDIDATE_API?ver=1&man=yes&client=mobi&keyword=$encTitle&duration=$duration&hash=$hash"
            val candJson = httpGet(candUrl, searchHeaders) ?: return@withContext null
            val candRoot = JSONObject(candJson)
            val candidates = candRoot.optJSONArray("candidates") ?: return@withContext null
            if (candidates.length() == 0) return@withContext null

            val cand = candidates.getJSONObject(0)
            val id = cand.optString("id", "")
            val accesskey = cand.optString("accesskey", "")
            if (id.isBlank() || accesskey.isBlank()) return@withContext null

            // 下载歌词
            val downUrl = "$DOWNLOAD_API?ver=1&client=mobi&id=$id&accesskey=$accesskey&fmt=lrc&charset=utf8"
            val downJson = httpGet(downUrl, searchHeaders) ?: return@withContext null
            val downRoot = JSONObject(downJson)
            val b64Content = downRoot.optString("content", "")
            if (b64Content.isBlank()) return@withContext null

            val rawLrc = try {
                String(Base64.decode(b64Content, Base64.DEFAULT), Charsets.UTF_8)
            } catch (e: Exception) {
                return@withContext null
            }

            if (rawLrc.isBlank()) return@withContext null

            val alignedPair = LyricAligner.alignLrcTimestamps(rawLrc, null)

            OnlineLyricsResult(
                songId = 0L,
                title = matchedTitle,
                artist = matchedArtist,
                originalLyrics = alignedPair.first,
                translatedLyrics = null,
                isBilingual = false
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun httpGet(urlStr: String, headers: Map<String, String> = emptyMap()): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                instanceFollowRedirects = true
            }

            if (connection.responseCode in 200..299) {
                BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { reader ->
                    reader.readText()
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
