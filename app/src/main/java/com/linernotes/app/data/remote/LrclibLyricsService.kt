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

object LrclibLyricsService {

    suspend fun fetchLyrics(trackTitle: String, artistName: String): OnlineLyricsResult? = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = NetEaseLyricsService.cleanTrackTitle(trackTitle)
            val encTrack = URLEncoder.encode(cleanTitle, "UTF-8")
            val encArtist = URLEncoder.encode(artistName, "UTF-8")
            val url = "https://lrclib.net/api/get?artist_name=$encArtist&track_name=$encTrack"

            val jsonStr = httpGet(url) ?: return@withContext null
            val root = JSONObject(jsonStr)

            val synced = root.optString("syncedLyrics", "")
            val plain = root.optString("plainLyrics", "")

            val lyrics = when {
                synced.isNotBlank() -> LyricAligner.alignLrcTimestamps(synced, null).first
                plain.isNotBlank() -> plain.trim()
                else -> return@withContext null
            }

            val trackName = root.optString("trackName", trackTitle)
            val artist = root.optString("artistName", artistName)

            OnlineLyricsResult(
                songId = root.optLong("id", 0L),
                title = trackName,
                artist = artist,
                originalLyrics = lyrics,
                translatedLyrics = null,
                isBilingual = false
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
                setRequestProperty("User-Agent", "LinerNotes/1.0 (Android; contact@example.com)")
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
