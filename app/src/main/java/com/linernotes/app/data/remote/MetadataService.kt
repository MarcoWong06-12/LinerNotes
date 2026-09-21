package com.linernotes.app.data.remote

import com.linernotes.app.data.local.entity.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OnlineAlbumInfo(
    val collectionId: Long,
    val title: String,
    val artist: String,
    val releaseYear: String,
    val coverUrl: String,
    val trackCount: Int
)

object MetadataService {

    suspend fun searchAlbum(query: String): OnlineAlbumInfo? = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val urlString = "https://itunes.apple.com/search?term=$encoded&entity=album&limit=1"
            val jsonStr = httpGet(urlString) ?: return@withContext null
            val root = JSONObject(jsonStr)
            val results = root.optJSONArray("results") ?: return@withContext null
            if (results.length() == 0) return@withContext null

            val item = results.getJSONObject(0)
            val collectionId = item.optLong("collectionId", 0L)
            val title = item.optString("collectionName", query)
            val artist = item.optString("artistName", "Unknown Artist")
            val releaseDate = item.optString("releaseDate", "")
            val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else "未知年份"
            val rawCover = item.optString("artworkUrl100", "")
            // 将 100x100 替换为 600x600 高清大图
            val coverUrl = rawCover.replace("100x100bb.jpg", "600x600bb.jpg")
                .replace("100x100", "600x600")
            val trackCount = item.optInt("trackCount", 0)

            OnlineAlbumInfo(
                collectionId = collectionId,
                title = title,
                artist = artist,
                releaseYear = year,
                coverUrl = coverUrl,
                trackCount = trackCount
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun fetchTracksWithLyrics(
        collectionId: Long,
        albumId: String,
        artistName: String,
        albumTitle: String
    ): List<TrackEntity> = withContext(Dispatchers.IO) {
        val tracksList = mutableListOf<TrackEntity>()
        try {
            val lookupUrl = "https://itunes.apple.com/lookup?id=$collectionId&entity=song"
            val jsonStr = httpGet(lookupUrl) ?: return@withContext emptyList()
            val root = JSONObject(jsonStr)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()

            val songs = mutableListOf<JSONObject>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                if (item.optString("wrapperType") == "track") {
                    songs.add(item)
                }
            }

            songs.sortBy { it.optInt("trackNumber", 1) }

            for ((index, song) in songs.withIndex()) {
                val trackNum = song.optInt("trackNumber", index + 1)
                val rawTrackName = song.optString("trackName", "Track $trackNum")
                // 清理 "(2019 Mix)" 或 "(Remastered)" 等尾缀
                val cleanTrackName = rawTrackName
                    .replace(Regex("""\s*\([0-9]{4}\s*Mix\)"""), "")
                    .replace(Regex("""\s*\(Remastered\s*[0-9]{0,4}\)"""), "")
                    .replace(Regex("""\s*-\s*Remastered\s*[0-9]{0,4}"""), "")
                    .trim()

                val duration = song.optLong("trackTimeMillis", 0L)

                // 从 LRCLIB 获取真实完整歌词
                val lyrics = fetchLyricsFromLrcLib(artistName, cleanTrackName, albumTitle)

                tracksList.add(
                    TrackEntity(
                        albumId = albumId,
                        trackNumber = trackNum,
                        title = cleanTrackName,
                        translatedTitle = null,
                        originalLyrics = lyrics,
                        translatedLyrics = null,
                        durationMs = if (duration > 0) duration else null
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        tracksList
    }

    suspend fun fetchLyricsFromLrcLib(
        artist: String,
        trackName: String,
        album: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val encArtist = URLEncoder.encode(artist, "UTF-8")
            val encTrack = URLEncoder.encode(trackName, "UTF-8")
            val encAlbum = URLEncoder.encode(album, "UTF-8")
            val urlString = "https://lrclib.net/api/get?artist_name=$encArtist&track_name=$encTrack&album_name=$encAlbum"

            val jsonStr = httpGet(urlString) ?: return@withContext null
            val root = JSONObject(jsonStr)

            val plain = root.optString("plainLyrics", "")
            if (plain.isNotBlank()) return@withContext plain

            val synced = root.optString("syncedLyrics", "")
            if (synced.isNotBlank()) return@withContext synced

            null
        } catch (e: Exception) {
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
