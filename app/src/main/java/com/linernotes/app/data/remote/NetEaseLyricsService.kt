package com.linernotes.app.data.remote

import com.linernotes.app.core.lyric.LyricAligner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OnlineLyricsResult(
    val songId: Long,
    val title: String,
    val artist: String,
    val originalLyrics: String,
    val translatedLyrics: String?,
    val isBilingual: Boolean
)

object NetEaseLyricsService {

    private const val SEARCH_API = "https://music.163.com/api/search/get/web"
    private const val LYRIC_API = "https://music.163.com/api/song/lyric"

    private val CLEAN_SUFFIX_REGEX = Regex(
        """\s*(\(feat\..*?\)|feat\..*|\(featuring.*?\)|featuring.*|\([0-9]{4}\s*Mix\)|\(Remastered.*?\)|-\s*Remastered.*|-\s*feat\..*|\(.*?Version\)|\(.*?Edition\))""",
        RegexOption.IGNORE_CASE
    )

    fun cleanTrackTitle(title: String): String {
        return title.replace(CLEAN_SUFFIX_REGEX, "").trim()
    }

    suspend fun fetchLyrics(
        trackTitle: String,
        artistName: String
    ): OnlineLyricsResult? = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = cleanTrackTitle(trackTitle)
            val query = if (artistName.isNotBlank() && !artistName.equals("Unknown Artist", ignoreCase = true)) {
                "$cleanTitle $artistName"
            } else {
                cleanTitle
            }

            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val searchUrl = "$SEARCH_API?s=$encodedQuery&type=1&limit=5"
            val searchJson = httpGet(searchUrl) ?: return@withContext null
            val searchRoot = JSONObject(searchJson)
            val resultObj = searchRoot.optJSONObject("result") ?: return@withContext null
            val songs = resultObj.optJSONArray("songs") ?: return@withContext null
            if (songs.length() == 0) return@withContext null

            // 优先选择歌手名称匹配的最佳条目，若无匹配则选第 1 个结果
            var bestSong: JSONObject? = null
            val cleanArtist = artistName.trim().lowercase()

            for (i in 0 until songs.length()) {
                val s = songs.getJSONObject(i)
                val artists = s.optJSONArray("artists")
                var artistMatched = false
                if (artists != null) {
                    for (j in 0 until artists.length()) {
                        val aName = artists.getJSONObject(j).optString("name", "").lowercase()
                        if (aName.contains(cleanArtist) || cleanArtist.contains(aName)) {
                            artistMatched = true
                            break
                        }
                    }
                }
                if (artistMatched) {
                    bestSong = s
                    break
                }
            }

            if (bestSong == null) {
                bestSong = songs.getJSONObject(0)
            }

            val songId = bestSong.optLong("id", 0L)
            if (songId <= 0L) return@withContext null

            val matchedTitle = bestSong.optString("name", trackTitle)
            val matchedArtist = bestSong.optJSONArray("artists")?.optJSONObject(0)?.optString("name", artistName) ?: artistName

            val lyricUrl = "$LYRIC_API?id=$songId&lv=1&kv=1&tv=1"
            val lyricJson = httpGet(lyricUrl) ?: return@withContext null
            val lyricRoot = JSONObject(lyricJson)

            val lrcObj = lyricRoot.optJSONObject("lrc")
            val origLrc = lrcObj?.optString("lyric", "") ?: ""
            if (origLrc.isBlank()) return@withContext null

            val tlyricObj = lyricRoot.optJSONObject("tlyric")
            val transLrc = tlyricObj?.optString("lyric", "") ?: ""

            // 利用时间戳精准对齐
            val alignedPair = LyricAligner.alignLrcTimestamps(origLrc, transLrc)

            OnlineLyricsResult(
                songId = songId,
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

    private fun httpGet(urlStr: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                setRequestProperty("Referer", "https://music.163.com/")
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
