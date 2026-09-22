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
import java.util.UUID

object MusixmatchLyricsService {

    private const val BASE_URL = "https://apic.musixmatch.com/ws/1.1/"
    private const val APP_ID = "android-player-v1.0"
    private const val USER_AGENT = "Dalvik/2.1.0 (Linux; U; Android 13)"
    private const val COOKIE = "AWSELB=0; AWSELBCORS=0"

    @Volatile
    private var cachedUserToken: String? = null

    private val TIMESTAMP_LINE_REGEX = Regex("""^(\[\d{2}:\d{2}(?:\.\d{1,3})?\])(.*)$""")

    private fun generateT(): String = UUID.randomUUID().toString().replace("-", "")

    suspend fun ensureUserToken(customToken: String? = null): String? = withContext(Dispatchers.IO) {
        if (!customToken.isNullOrBlank()) {
            return@withContext customToken.trim()
        }
        cachedUserToken?.let { return@withContext it }

        synchronized(this) {
            cachedUserToken?.let { return@withContext it }
            try {
                val t = generateT()
                val url = "${BASE_URL}token.get?user_language=en&app_id=$APP_ID&t=$t"
                val jsonStr = httpGet(url) ?: return@withContext null
                val root = JSONObject(jsonStr)
                val token = root.optJSONObject("message")
                    ?.optJSONObject("body")
                    ?.optString("user_token", "")

                if (!token.isNullOrBlank() && token != "00000000000000000000000000000000000000000000000000000000") {
                    cachedUserToken = token
                    token
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    fun invalidateToken() {
        cachedUserToken = null
    }

    suspend fun fetchLyrics(
        trackTitle: String,
        artistName: String,
        customToken: String? = null,
        targetLanguage: String = "zh"
    ): OnlineLyricsResult? = withContext(Dispatchers.IO) {
        try {
            val token = ensureUserToken(customToken) ?: return@withContext null
            val cleanTitle = NetEaseLyricsService.cleanTrackTitle(trackTitle)
            val encTrack = URLEncoder.encode(cleanTitle, "UTF-8")
            val encArtist = URLEncoder.encode(artistName, "UTF-8")
            val tSearch = generateT()

            val searchUrl = "${BASE_URL}track.search?page_size=5&page=1&s_track_rating=desc&q_track=$encTrack&q_artist=$encArtist&usertoken=$token&format=json&app_id=$APP_ID&t=$tSearch"
            val searchJson = httpGet(searchUrl) ?: return@withContext null
            val searchRoot = JSONObject(searchJson)

            val statusCode = searchRoot.optJSONObject("message")
                ?.optJSONObject("header")
                ?.optInt("status_code", 0) ?: 0

            if (statusCode == 401) {
                invalidateToken()
                return@withContext null
            }

            val trackList = searchRoot.optJSONObject("message")
                ?.optJSONObject("body")
                ?.optJSONArray("track_list") ?: return@withContext null

            if (trackList.length() == 0) return@withContext null

            // 优先选择有同步字幕的条目
            var bestTrackObj: JSONObject? = null
            for (i in 0 until trackList.length()) {
                val item = trackList.optJSONObject(i)?.optJSONObject("track") ?: continue
                if (item.optInt("has_subtitles", 0) == 1) {
                    bestTrackObj = item
                    break
                }
            }
            if (bestTrackObj == null) {
                for (i in 0 until trackList.length()) {
                    val item = trackList.optJSONObject(i)?.optJSONObject("track") ?: continue
                    if (item.optInt("has_lyrics", 0) == 1) {
                        bestTrackObj = item
                        break
                    }
                }
            }
            if (bestTrackObj == null) {
                bestTrackObj = trackList.optJSONObject(0)?.optJSONObject("track") ?: return@withContext null
            }

            val trackId = bestTrackObj.optLong("track_id", 0L)
            val matchedTitle = bestTrackObj.optString("track_name", trackTitle)
            val matchedArtist = bestTrackObj.optString("artist_name", artistName)
            val hasSubtitles = bestTrackObj.optInt("has_subtitles", 0) == 1

            if (trackId == 0L) return@withContext null

            var originalLyrics: String? = null

            // 1. 获取 LRC 同步时间戳字幕
            if (hasSubtitles) {
                val tSub = generateT()
                val subUrl = "${BASE_URL}track.subtitle.get?subtitle_format=lrc&track_id=$trackId&usertoken=$token&format=json&app_id=$APP_ID&t=$tSub"
                val subJson = httpGet(subUrl)
                if (subJson != null) {
                    val subRoot = JSONObject(subJson)
                    val subBody = subRoot.optJSONObject("message")
                        ?.optJSONObject("body")
                        ?.optJSONObject("subtitle")
                        ?.optString("subtitle_body", "")
                    if (!subBody.isNullOrBlank()) {
                        originalLyrics = subBody.trim()
                    }
                }
            }

            // 2. 无同步时间戳时，回退获取纯文本歌词
            if (originalLyrics.isNullOrBlank()) {
                val tLyr = generateT()
                val lyrUrl = "${BASE_URL}track.lyrics.get?track_id=$trackId&usertoken=$token&format=json&app_id=$APP_ID&t=$tLyr"
                val lyrJson = httpGet(lyrUrl)
                if (lyrJson != null) {
                    val lyrRoot = JSONObject(lyrJson)
                    val lyrBody = lyrRoot.optJSONObject("message")
                        ?.optJSONObject("body")
                        ?.optJSONObject("lyrics")
                        ?.optString("lyrics_body", "")
                    if (!lyrBody.isNullOrBlank()) {
                        // 移除 Musixmatch 版权免责声明尾缀 (e.g. "******* This Lyrics is NOT for Commercial use *******")
                        val cleanBody = lyrBody.lines()
                            .filterNot { it.contains("This Lyrics is NOT for Commercial use", ignoreCase = true) }
                            .joinToString("\n")
                            .trim()
                        if (cleanBody.isNotBlank()) {
                            originalLyrics = cleanBody
                        }
                    }
                }
            }

            if (originalLyrics.isNullOrBlank()) return@withContext null

            // 3. 尝试获取官方众包逐行翻译 (Crowd Translations)
            var translatedLyrics: String? = null
            var isBilingual = false

            val langCode = when {
                targetLanguage.startsWith("zh", ignoreCase = true) -> "zh"
                targetLanguage.startsWith("ja", ignoreCase = true) -> "ja"
                targetLanguage.startsWith("ko", ignoreCase = true) -> "ko"
                targetLanguage.startsWith("es", ignoreCase = true) -> "es"
                targetLanguage.startsWith("fr", ignoreCase = true) -> "fr"
                targetLanguage.startsWith("de", ignoreCase = true) -> "de"
                else -> targetLanguage.lowercase().trim()
            }

            val tTrans = generateT()
            val transUrl = "${BASE_URL}crowd.track.translations.get?translation_fields_set=minimal&selected_language=$langCode&track_id=$trackId&comment_format=text&part=user&usertoken=$token&format=json&app_id=$APP_ID&t=$tTrans"
            val transJson = httpGet(transUrl)
            if (transJson != null) {
                val transRoot = JSONObject(transJson)
                val transList = transRoot.optJSONObject("message")
                    ?.optJSONObject("body")
                    ?.optJSONArray("translations_list")

                if (transList != null && transList.length() > 0) {
                    val transMap = mutableMapOf<String, String>()
                    for (j in 0 until transList.length()) {
                        val transItem = transList.optJSONObject(j)?.optJSONObject("translation") ?: continue
                        val origLine = transItem.optString("subtitle_matched_line", "").trim()
                        val transText = transItem.optString("description", "").trim()
                        if (origLine.isNotEmpty() && transText.isNotEmpty()) {
                            transMap[origLine] = transText
                        }
                    }

                    if (transMap.isNotEmpty()) {
                        // 根据原版 LRC 逐行合成对应的翻译时间轴
                        val transSb = StringBuilder()
                        var matchedCount = 0
                        for (line in originalLyrics.lines()) {
                            val match = TIMESTAMP_LINE_REGEX.find(line)
                            if (match != null) {
                                val ts = match.groupValues[1]
                                val text = match.groupValues[2].trim()
                                val translated = transMap[text]
                                if (!translated.isNullOrBlank()) {
                                    transSb.append("$ts $translated\n")
                                    matchedCount++
                                } else {
                                    transSb.append("$ts\n")
                                }
                            } else {
                                val translated = transMap[line.trim()]
                                if (!translated.isNullOrBlank()) {
                                    transSb.append("$translated\n")
                                    matchedCount++
                                }
                            }
                        }

                        if (matchedCount > 0) {
                            val aligned = LyricAligner.alignLrcTimestamps(originalLyrics, transSb.toString().trim())
                            originalLyrics = aligned.first
                            translatedLyrics = aligned.second
                            isBilingual = !translatedLyrics.isNullOrBlank()
                        }
                    }
                }
            }

            if (!isBilingual && originalLyrics.contains("[")) {
                originalLyrics = LyricAligner.alignLrcTimestamps(originalLyrics, null).first
            }

            OnlineLyricsResult(
                songId = trackId,
                title = matchedTitle,
                artist = matchedArtist,
                originalLyrics = originalLyrics,
                translatedLyrics = translatedLyrics,
                isBilingual = isBilingual
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
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Cookie", COOKIE)
                setRequestProperty("Accept", "application/json")
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
