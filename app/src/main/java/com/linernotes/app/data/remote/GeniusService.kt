package com.linernotes.app.data.remote

import com.linernotes.app.core.network.LinerNotesHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class GeniusSongSearchResult(
    val id: Long,
    val title: String,
    val fullTitle: String,
    val artist: String,
    val thumbUrl: String?,
    val coverUrl: String?,
    val url: String?
)

data class GeniusSongDetail(
    val id: Long,
    val title: String,
    val artist: String,
    val descriptionPlain: String,
    val releaseDate: String?,
    val headerImageUrl: String?,
    val songArtImageUrl: String?,
    val producerCredits: String?,
    val songUrl: String?
)

data class GeniusAnnotationItem(
    val id: Long,
    val bodyPlain: String,
    val bodyHtml: String?,
    val verified: Boolean,
    val authorName: String?,
    val authorAvatarUrl: String?,
    val votesTotal: Int,
    val url: String?,
    val imageUrls: List<String>
)

data class GeniusReferentItem(
    val id: Long,
    val fragment: String,
    val annotations: List<GeniusAnnotationItem>
)

object GeniusService {

    private const val GENIUS_WEB_API = "https://genius.com/api"
    private const val GENIUS_PROD_API = "https://api.genius.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val IMG_REGEX = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    private fun buildHeaders(customToken: String?): Map<String, String> {
        val headers = mutableMapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json, text/plain, */*",
            "Referer" to "https://genius.com"
        )
        if (!customToken.isNullOrBlank()) {
            headers["Authorization"] = "Bearer ${customToken.trim()}"
        }
        return headers
    }

    /**
     * 清理曲目名称中的干扰后缀（如 feat., Live, Remastered, Bonus Track 等）以便提高 Genius 检索召回率
     */
    private fun sanitizeSearchQuery(title: String, artist: String): String {
        val cleanTitle = title
            .replace(Regex("""\s*[\(\[\{](?:feat|ft|live|remaster|version|deluxe|bonus|mono|stereo|anniversary|ost|soundtrack).*?[\)\]\}]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*(?:feat|live|remaster|version|deluxe|bonus).*$""", RegexOption.IGNORE_CASE), "")
            .trim()

        val cleanArtist = artist
            .replace(Regex("""\s*[\(\[\{].*?[\)\]\}]"""), "")
            .replace(Regex("""\s*feat\..*$""", RegexOption.IGNORE_CASE), "")
            .trim()

        return "$cleanTitle $cleanArtist".trim()
    }

    /**
     * 检索 Genius 歌曲条目获取 song_id
     */
    suspend fun searchSong(
        title: String,
        artist: String,
        customToken: String? = null
    ): GeniusSongSearchResult? = withContext(Dispatchers.IO) {
        val query = sanitizeSearchQuery(title, artist)
        if (query.isBlank()) return@withContext null

        val hasCustomToken = !customToken.isNullOrBlank()
        val url = if (hasCustomToken) {
            "$GENIUS_PROD_API/search?q=${URLEncoder.encode(query, "UTF-8")}"
        } else {
            "$GENIUS_WEB_API/search/multi?q=${URLEncoder.encode(query, "UTF-8")}"
        }

        try {
            val jsonStr = LinerNotesHttpClient.get(url, buildHeaders(customToken)) ?: return@withContext null
            val root = JSONObject(jsonStr)
            val responseObj = root.optJSONObject("response") ?: return@withContext null

            if (hasCustomToken) {
                // api.genius.com /search 返回 hits 数组
                val hits = responseObj.optJSONArray("hits") ?: return@withContext null
                for (i in 0 until hits.length()) {
                    val hit = hits.optJSONObject(i) ?: continue
                    val result = hit.optJSONObject("result") ?: continue
                    val resultType = hit.optString("type")
                    if (resultType.equals("song", ignoreCase = true) || hit.has("result")) {
                        return@withContext parseSongResult(result)
                    }
                }
            } else {
                // genius.com/api/search/multi 返回 sections 数组
                val sections = responseObj.optJSONArray("sections") ?: return@withContext null
                for (i in 0 until sections.length()) {
                    val sec = sections.optJSONObject(i) ?: continue
                    val secType = sec.optString("type")
                    if (secType.equals("song", ignoreCase = true) || secType.equals("top_hit", ignoreCase = true)) {
                        val hits = sec.optJSONArray("hits") ?: continue
                        for (j in 0 until hits.length()) {
                            val hit = hits.optJSONObject(j) ?: continue
                            val result = hit.optJSONObject("result") ?: continue
                            val song = parseSongResult(result)
                            if (song != null) return@withContext song
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSongResult(result: JSONObject): GeniusSongSearchResult? {
        val id = result.optLong("id", 0L)
        if (id <= 0L) return null
        val title = result.optString("title")
        val fullTitle = result.optString("full_title")
        val primaryArtist = result.optJSONObject("primary_artist")
        val artistName = primaryArtist?.optString("name") ?: ""
        val thumbUrl = result.optString("song_art_image_thumbnail_url").takeIf { it.isNotBlank() }
        val coverUrl = result.optString("song_art_image_url").takeIf { it.isNotBlank() }
        val url = result.optString("url").takeIf { it.isNotBlank() }

        return GeniusSongSearchResult(
            id = id,
            title = title,
            fullTitle = fullTitle,
            artist = artistName,
            thumbUrl = thumbUrl,
            coverUrl = coverUrl,
            url = url
        )
    }

    /**
     * 获取歌曲背景故事总览 (About this song / Description)
     */
    suspend fun getSongDetails(
        songId: Long,
        customToken: String? = null
    ): GeniusSongDetail? = withContext(Dispatchers.IO) {
        val baseUrl = if (!customToken.isNullOrBlank()) GENIUS_PROD_API else GENIUS_WEB_API
        val url = "$baseUrl/songs/$songId?text_format=plain"

        try {
            val jsonStr = LinerNotesHttpClient.get(url, buildHeaders(customToken)) ?: return@withContext null
            val root = JSONObject(jsonStr)
            val song = root.optJSONObject("response")?.optJSONObject("song") ?: return@withContext null

            val title = song.optString("title")
            val primaryArtist = song.optJSONObject("primary_artist")?.optString("name") ?: ""
            val descPlain = song.optJSONObject("description")?.optString("plain")?.trim() ?: ""
            val releaseDate = song.optString("release_date_for_display").takeIf { it.isNotBlank() }
            val headerImage = song.optString("header_image_url").takeIf { it.isNotBlank() }
            val songArtImage = song.optString("song_art_image_url").takeIf { it.isNotBlank() }
            val songUrl = song.optString("url").takeIf { it.isNotBlank() }

            // 提取制作人 credits
            val producersArr = song.optJSONArray("producer_artists")
            val producers = if (producersArr != null && producersArr.length() > 0) {
                val list = mutableListOf<String>()
                for (i in 0 until producersArr.length()) {
                    producersArr.optJSONObject(i)?.optString("name")?.let { list.add(it) }
                }
                list.joinToString(", ")
            } else null

            GeniusSongDetail(
                id = songId,
                title = title,
                artist = primaryArtist,
                descriptionPlain = descPlain,
                releaseDate = releaseDate,
                headerImageUrl = headerImage,
                songArtImageUrl = songArtImage,
                producerCredits = producers,
                songUrl = songUrl
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取该歌曲的所有歌词典故注释列表 (Referents)
     */
    suspend fun getReferents(
        songId: Long,
        customToken: String? = null
    ): List<GeniusReferentItem> = withContext(Dispatchers.IO) {
        val baseUrl = if (!customToken.isNullOrBlank()) GENIUS_PROD_API else GENIUS_WEB_API
        val url = "$baseUrl/referents?song_id=$songId&text_format=plain,html&per_page=50"

        try {
            val jsonStr = LinerNotesHttpClient.get(url, buildHeaders(customToken)) ?: return@withContext emptyList()
            val root = JSONObject(jsonStr)
            val referentsArr = root.optJSONObject("response")?.optJSONArray("referents") ?: return@withContext emptyList()

            val resultList = mutableListOf<GeniusReferentItem>()
            for (i in 0 until referentsArr.length()) {
                val refObj = referentsArr.optJSONObject(i) ?: continue
                val refId = refObj.optLong("id", 0L)
                val fragment = refObj.optString("fragment").trim()
                if (fragment.isBlank()) continue

                val annotationsArr = refObj.optJSONArray("annotations") ?: continue
                val annotationItems = mutableListOf<GeniusAnnotationItem>()

                for (j in 0 until annotationsArr.length()) {
                    val annotObj = annotationsArr.optJSONObject(j) ?: continue
                    val annotId = annotObj.optLong("id", 0L)
                    val bodyObj = annotObj.optJSONObject("body")
                    val bodyPlain = bodyObj?.optString("plain")?.trim() ?: ""
                    val bodyHtml = bodyObj?.optString("html")
                    if (bodyPlain.isBlank() && bodyHtml.isNullOrBlank()) continue

                    val isVerified = annotObj.optBoolean("verified", false) || annotObj.optJSONObject("verified_by") != null
                    val votesTotal = annotObj.optInt("votes_total", 0)
                    val shareUrl = annotObj.optString("share_url").takeIf { it.isNotBlank() } ?: annotObj.optString("url").takeIf { it.isNotBlank() }

                    // 作者/贡献者信息
                    var authorName: String? = null
                    var authorAvatarUrl: String? = null
                    val authorsArr = annotObj.optJSONArray("authors")
                    if (authorsArr != null && authorsArr.length() > 0) {
                        val authorUser = authorsArr.optJSONObject(0)?.optJSONObject("user")
                        authorName = authorUser?.optString("name")
                        authorAvatarUrl = authorUser?.optJSONObject("avatar")?.optJSONObject("thumb")?.optString("url")
                    }
                    if (authorName.isNullOrBlank()) {
                        val createdBy = annotObj.optJSONObject("created_by")
                        authorName = createdBy?.optString("name")
                        authorAvatarUrl = createdBy?.optJSONObject("avatar")?.optJSONObject("thumb")?.optString("url")
                    }

                    // 提取图片 URLs (从 HTML 中抓取历史相片、录音室照片)
                    val imageUrls = mutableListOf<String>()
                    if (!bodyHtml.isNullOrBlank()) {
                        IMG_REGEX.findAll(bodyHtml).forEach { match ->
                            val src = match.groupValues.getOrNull(1)
                            if (!src.isNullOrBlank() && (src.startsWith("http://") || src.startsWith("https://")) && !src.contains("avatar", ignoreCase = true)) {
                                imageUrls.add(src)
                            }
                        }
                    }

                    annotationItems.add(
                        GeniusAnnotationItem(
                            id = annotId,
                            bodyPlain = bodyPlain,
                            bodyHtml = bodyHtml,
                            verified = isVerified,
                            authorName = authorName,
                            authorAvatarUrl = authorAvatarUrl,
                            votesTotal = votesTotal,
                            url = shareUrl,
                            imageUrls = imageUrls.distinct()
                        )
                    )
                }

                if (annotationItems.isNotEmpty()) {
                    resultList.add(
                        GeniusReferentItem(
                            id = refId,
                            fragment = fragment,
                            annotations = annotationItems
                        )
                    )
                }
            }
            resultList
        } catch (e: Exception) {
            emptyList()
        }
    }
}
