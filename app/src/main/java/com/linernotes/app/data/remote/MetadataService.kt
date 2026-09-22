package com.linernotes.app.data.remote

import com.linernotes.app.core.lyric.LyricSearchCleaner
import com.linernotes.app.core.network.LinerNotesHttpClient
import com.linernotes.app.data.local.entity.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONObject
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

    private val HEADERS = mapOf(
        "User-Agent" to "LinerNotes/1.0 (Android; https://github.com/MarcoWong06-12/LinerNotes)"
    )

    suspend fun searchAlbum(query: String): OnlineAlbumInfo? = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val urlString = "https://itunes.apple.com/search?term=$encoded&entity=album&limit=1"
            val jsonStr = LinerNotesHttpClient.get(urlString, HEADERS) ?: return@withContext null
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
            null
        }
    }

    suspend fun fetchTracksWithLyrics(
        collectionId: Long,
        albumId: String,
        artistName: String,
        albumTitle: String
    ): List<TrackEntity> = withContext(Dispatchers.IO) {
        try {
            val lookupUrl = "https://itunes.apple.com/lookup?id=$collectionId&entity=song"
            val jsonStr = LinerNotesHttpClient.get(lookupUrl, HEADERS) ?: return@withContext emptyList()
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

            // 并发加速检索全辑各曲目歌词
            val deferredList = songs.mapIndexed { index, song ->
                async {
                    val trackNum = song.optInt("trackNumber", index + 1)
                    val rawTrackName = song.optString("trackName", "Track $trackNum")
                    val cleanTrackName = LyricSearchCleaner.cleanTrackTitle(rawTrackName)
                    val duration = song.optLong("trackTimeMillis", 0L)

                    val lyricResult = UnifiedLyricsService.fetchLyrics(cleanTrackName, artistName)

                    TrackEntity(
                        albumId = albumId,
                        trackNumber = trackNum,
                        title = cleanTrackName,
                        translatedTitle = null,
                        originalLyrics = lyricResult?.originalLyrics,
                        translatedLyrics = lyricResult?.translatedLyrics,
                        durationMs = if (duration > 0) duration else null
                    )
                }
            }

            deferredList.awaitAll()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
