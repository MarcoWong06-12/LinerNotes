package com.linernotes.app.data.remote

import com.linernotes.app.core.network.LinerNotesHttpClient
import com.linernotes.app.core.util.ChineseConverter
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
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val IMG_REGEX = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    private fun buildHeaders(customToken: String?): Map<String, String> {
        val headers = mutableMapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json, text/plain, */*",
            "Accept-Language" to "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7",
            "Referer" to "https://genius.com/",
            "Sec-Ch-Ua" to "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"",
            "Sec-Ch-Ua-Mobile" to "?0",
            "Sec-Ch-Ua-Platform" to "\"Windows\"",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin"
        )
        if (!customToken.isNullOrBlank()) {
            headers["Authorization"] = "Bearer ${customToken.trim()}"
        }
        return headers
    }

    /**
     * 清理曲目名称中的干扰前缀与后缀（如音轨编号 "01. ", "1 - ", feat., Live, Remastered 等）以便提高 Genius 检索召回率
     */
    fun sanitizeTitle(title: String): String {
        return title
            .replace(Regex("""^\d+[\.\s\-_、]+\s*"""), "") // 剥离前导音轨编号如 "01. ", "1 - "
            .replace(Regex("""\s*[\(\[\{](?:feat|ft|radio\s*mix|club\s*mix|extended\s*mix|original\s*mix|mix|remix|edit|radio\s*edit|single\s*version|album\s*version|acoustic|live|remaster(?:ed)?|version|deluxe|bonus|mono|stereo|anniversary|ost|soundtrack|explicit|clean).*?[\)\]\}]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*(?:feat|radio\s*mix|club\s*mix|mix|remix|edit|radio\s*edit|single\s*version|live|remaster(?:ed)?|version|deluxe|bonus|explicit|clean).*$""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    fun sanitizeArtist(artist: String): String {
        return artist
            .replace(Regex("""\s*[\(\[\{（【].*?[\)\]\}）】]"""), "")
            .replace(Regex("""\s*feat\..*$""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    /**
     * 智能提取艺人名称多重候选（包括括号中的外文原名/中文译名、合作艺人分割、以及中英混合字段）
     * 例如输入 "艾薇儿 (Avril Lavigne)" -> ["Avril Lavigne", "艾薇儿"]
     * 例如输入 "周杰伦 (Jay Chou)" -> ["Jay Chou", "周杰伦"]
     */
    fun extractArtistCandidates(artist: String): List<String> {
        if (artist.isBlank()) return emptyList()
        val results = mutableListOf<String>()

        // 1. 提取括号中包含的名称 (如 "艾薇儿 (Avril Lavigne)" 提取出 "Avril Lavigne")
        val parenMatches = Regex("""[\(\[\{（【](.*?)[\)\]\}）】]""").findAll(artist)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()

        // 2. 剥离括号后的基础名称
        val baseArtist = sanitizeArtist(artist)

        // 3. 针对多艺术家合作进行分割 (如 "/", ",", "&", "feat.")
        val splitParts = artist.split(Regex("""[/,、&]|\bfeat\.\b|\bft\.\b""", RegexOption.IGNORE_CASE))
            .map { sanitizeArtist(it) }
            .filter { it.isNotBlank() }

        // 4. 提取纯拉丁/英文字段 (针对如 "艾薇儿·拉维尼 Avril Lavigne")
        val latinOnly = Regex("""[A-Za-z0-9\s'\.\-]+""").findAll(artist)
            .map { it.value.trim() }
            .filter { it.length >= 2 }
            .toList()

        val all = mutableListOf<String>()
        // Genius 上的西洋音乐条目绝大多数以英文/拉丁名建立，如果包含英文候选名，优先置顶放入
        val latinCandidates = (parenMatches + splitParts + latinOnly).filter { it.matches(Regex(""".*[A-Za-z].*""")) }
        all.addAll(latinCandidates)

        if (baseArtist.isNotBlank()) all.add(baseArtist)
        all.addAll(parenMatches)
        all.addAll(splitParts)

        for (item in all) {
            val clean = item.trim()
            if (clean.isNotBlank() && clean !in results) {
                results.add(clean)
            }
        }
        return results
    }

    /**
     * 检索 Genius 歌曲条目获取 song_id（支持多重候选回退检索）
     */
    suspend fun searchSong(
        title: String,
        artist: String,
        customToken: String? = null
    ): GeniusSongSearchResult? = withContext(Dispatchers.IO) {
        val cleanTitle = sanitizeTitle(title)
        val artistCandidates = extractArtistCandidates(artist)

        val candidates = mutableListOf<String>()
        // 1. 优先组合每个艺术家候选与清理后歌名
        for (a in artistCandidates) {
            if (cleanTitle.isNotBlank()) {
                candidates.add("$cleanTitle $a")
            }
        }

        // 2. 繁简体互转候选，提升华语流行乐在 Genius 上的命中率
        val tradTitle = ChineseConverter.toTraditional(cleanTitle)
        for (a in artistCandidates) {
            val tradArtist = ChineseConverter.toTraditional(a)
            val tradCand = "$tradTitle $tradArtist".trim()
            if (tradCand.isNotBlank() && tradCand !in candidates) {
                candidates.add(tradCand)
            }
        }

        // 3. 纯歌名候选 (针对知名经典歌曲，单凭歌名即可在 Genius 首屏直接命中)
        if (cleanTitle.isNotBlank() && cleanTitle !in candidates) {
            candidates.add(cleanTitle)
        }

        // 4. 原始未清洗字符串候选
        val rawTitle = title.trim()
        val rawArtist = artist.trim()
        if (rawTitle.isNotBlank() && rawArtist.isNotBlank()) {
            val rawCandidate = "$rawTitle $rawArtist"
            if (rawCandidate !in candidates) candidates.add(rawCandidate)
        }

        for (query in candidates) {
            val result = executeSearch(query, customToken)
            if (result != null) return@withContext result
        }
        null
    }

    private fun executeSearch(query: String, customToken: String?): GeniusSongSearchResult? {
        val hasCustomToken = !customToken.isNullOrBlank()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        val urls = if (hasCustomToken) {
            listOf("$GENIUS_PROD_API/search?q=$encodedQuery")
        } else {
            listOf(
                "$GENIUS_WEB_API/search/multi?q=$encodedQuery",
                "$GENIUS_WEB_API/search/song?q=$encodedQuery"
            )
        }

        for (url in urls) {
            try {
                val jsonStr = LinerNotesHttpClient.get(url, buildHeaders(customToken)) ?: continue
                val root = JSONObject(jsonStr)
                val responseObj = root.optJSONObject("response") ?: continue

                if (hasCustomToken) {
                    val hits = responseObj.optJSONArray("hits") ?: continue
                    for (i in 0 until hits.length()) {
                        val hit = hits.optJSONObject(i) ?: continue
                        val result = hit.optJSONObject("result") ?: continue
                        val song = parseSongResult(result)
                        if (song != null) return song
                    }
                } else {
                    val sections = responseObj.optJSONArray("sections") ?: continue

                    // 1. 优先从 type == "song" 专用段中查找精准曲目
                    for (i in 0 until sections.length()) {
                        val sec = sections.optJSONObject(i) ?: continue
                        if (sec.optString("type").equals("song", ignoreCase = true)) {
                            val hits = sec.optJSONArray("hits") ?: continue
                            for (j in 0 until hits.length()) {
                                val hit = hits.optJSONObject(j) ?: continue
                                val result = hit.optJSONObject("result") ?: continue
                                val song = parseSongResult(result)
                                if (song != null) return song
                            }
                        }
                    }

                    // 2. 其次从 top_hit 段中查找类型为 song 的条目
                    for (i in 0 until sections.length()) {
                        val sec = sections.optJSONObject(i) ?: continue
                        if (sec.optString("type").equals("top_hit", ignoreCase = true)) {
                            val hits = sec.optJSONArray("hits") ?: continue
                            for (j in 0 until hits.length()) {
                                val hit = hits.optJSONObject(j) ?: continue
                                val hitType = hit.optString("type")
                                if (hitType.isNotBlank() && !hitType.equals("song", ignoreCase = true)) continue
                                val result = hit.optJSONObject("result") ?: continue
                                val song = parseSongResult(result)
                                if (song != null) return song
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // 尝试下一个候选 URL
            }
        }
        return null
    }

    private fun parseSongResult(result: JSONObject): GeniusSongSearchResult? {
        val type = result.optString("_type")
        if (type.isNotBlank() && !type.equals("song", ignoreCase = true)) {
            return null
        }
        val id = result.optLong("id", 0L)
        if (id <= 0L) return null
        val title = result.optString("title").trim()
        if (title.isBlank()) return null
        val fullTitle = result.optString("full_title").ifBlank { title }
        val primaryArtist = result.optJSONObject("primary_artist")
        val artistName = primaryArtist?.optString("name")?.takeIf { it.isNotBlank() }
            ?: result.optString("artist_names")
        val thumbUrl = result.optString("song_art_image_thumbnail_url").takeIf { it.isNotBlank() }
            ?: result.optString("header_image_thumbnail_url").takeIf { it.isNotBlank() }
        val coverUrl = result.optString("song_art_image_url").takeIf { it.isNotBlank() }
            ?: result.optString("header_image_url").takeIf { it.isNotBlank() }
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

    fun isValidStoryDescription(desc: String?): Boolean {
        if (desc.isNullOrBlank()) return false
        val clean = desc.trim()
        if (clean.length < 10) return false
        val lower = clean.lowercase()
        if (lower == "?" || lower == "[?]" || lower == "tba" || lower == "tbd" || lower == "n/a" || lower == "none" || lower == "nil") {
            return false
        }
        if (lower.contains("lyrics for this song have not yet been released") ||
            lower.contains("this song is an instrumental") ||
            lower.contains("this song is instrumental")
        ) {
            return false
        }
        return true
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
            val rawDesc = song.optJSONObject("description")?.optString("plain")?.trim() ?: ""
            val validDesc = if (isValidStoryDescription(rawDesc)) rawDesc else ""
            val releaseDate = song.optString("release_date_for_display").takeIf { it.isNotBlank() }
            val headerImage = song.optString("header_image_url").takeIf { it.isNotBlank() }
            val songArtImage = song.optString("song_art_image_url").takeIf { it.isNotBlank() }
            val songUrl = song.optString("url").takeIf { it.isNotBlank() }

            if (validDesc.isBlank() && headerImage.isNullOrBlank() && songArtImage.isNullOrBlank()) {
                return@withContext null
            }

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
                descriptionPlain = validDesc,
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
