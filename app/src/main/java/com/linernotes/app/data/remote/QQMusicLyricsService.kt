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

object QQMusicLyricsService {

    private const val SEARCH_API = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp"
    private const val LYRIC_API = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"

    suspend fun fetchLyrics(trackTitle: String, artistName: String): OnlineLyricsResult? = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = NetEaseLyricsService.cleanTrackTitle(trackTitle)
            val query = if (artistName.isNotBlank() && !artistName.equals("Unknown Artist", ignoreCase = true)) {
                "$cleanTitle $artistName"
            } else {
                cleanTitle
            }

            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val searchUrl = "$SEARCH_API?p=1&n=5&w=$encodedQuery&format=json"
            val searchHeaders = mapOf(
                "Referer" to "https://y.qq.com/",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            val searchJson = httpGet(searchUrl, searchHeaders) ?: return@withContext null
            val searchRoot = JSONObject(searchJson)
            val dataObj = searchRoot.optJSONObject("data") ?: return@withContext null
            val songObj = dataObj.optJSONObject("song") ?: return@withContext null
            val songList = songObj.optJSONArray("list") ?: return@withContext null
            if (songList.length() == 0) return@withContext null

            // 优先匹配歌手
            var targetSong: JSONObject? = null
            val cleanArtist = artistName.trim().lowercase()

            for (i in 0 until songList.length()) {
                val s = songList.getJSONObject(i)
                val singers = s.optJSONArray("singer")
                var matched = false
                if (singers != null) {
                    for (j in 0 until singers.length()) {
                        val name = singers.getJSONObject(j).optString("name", "").lowercase()
                        if (name.contains(cleanArtist) || cleanArtist.contains(name)) {
                            matched = true
                            break
                        }
                    }
                }
                if (matched) {
                    targetSong = s
                    break
                }
            }

            if (targetSong == null) {
                targetSong = songList.getJSONObject(0)
            }

            val songmid = targetSong.optString("songmid", "")
            if (songmid.isBlank()) return@withContext null

            val matchedTitle = targetSong.optString("songname", trackTitle)
            val matchedArtist = targetSong.optJSONArray("singer")?.optJSONObject(0)?.optString("name", artistName) ?: artistName

            val lyricUrl = "$LYRIC_API?songmid=$songmid&format=json&nobase64=1"
            val lyricHeaders = mapOf(
                "Referer" to "https://y.qq.com/portal/player.html",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            val lyricJson = httpGet(lyricUrl, lyricHeaders) ?: return@withContext null
            val lyricRoot = JSONObject(lyricJson)

            var rawLrc = lyricRoot.optString("lyric", "")
            var rawTrans = lyricRoot.optString("trans", "")

            if (isBase64(rawLrc)) {
                rawLrc = decodeBase64(rawLrc)
            }
            if (isBase64(rawTrans)) {
                rawTrans = decodeBase64(rawTrans)
            }

            if (rawLrc.isBlank()) return@withContext null

            val alignedPair = LyricAligner.alignLrcTimestamps(rawLrc, rawTrans)

            OnlineLyricsResult(
                songId = targetSong.optLong("songid", 0L),
                title = matchedTitle,
                artist = matchedArtist,
                originalLyrics = alignedPair.first,
                translatedLyrics = alignedPair.second.ifBlank { null },
                isBilingual = alignedPair.second.isNotBlank()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isBase64(str: String): Boolean {
        if (str.length < 20) return false
        return !str.contains("[") && !str.contains("]") && (str.endsWith("=") || str.matches(Regex("""^[A-Za-z0-9+/=\r\n]+$""")))
    }

    private fun decodeBase64(str: String): String {
        return try {
            String(Base64.decode(str, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            str
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
