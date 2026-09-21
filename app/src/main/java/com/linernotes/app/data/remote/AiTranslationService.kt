package com.linernotes.app.data.remote

import com.linernotes.app.core.preference.AiPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class TranslationResult(
    val translatedTitle: String?,
    val translatedLyrics: String
)

@Singleton
class AiTranslationService @Inject constructor(
    private val aiPreferences: AiPreferences
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun translateTrack(
        trackTitle: String,
        originalLyrics: String
    ): TranslationResult = withContext(Dispatchers.IO) {
        if (aiPreferences.hasKey) {
            // 用户已配置自定义 API Key，直接由 AI 翻译；异常将抛出给界面展示明确错误
            translateWithAi(trackTitle, originalLyrics)
        } else {
            fallbackTranslate(trackTitle, originalLyrics)
        }
    }

    private fun resolveEndpointUrl(): String {
        var cleanBase = aiPreferences.baseUrl.trim().trimEnd('/')
        // 智能兼容 Google Gemini 官方域名输入
        if (cleanBase.contains("generativelanguage.googleapis.com") && !cleanBase.contains("/openai")) {
            cleanBase = if (cleanBase.endsWith("/v1beta")) {
                "$cleanBase/openai"
            } else {
                "$cleanBase/v1beta/openai"
            }
        }
        return if (cleanBase.endsWith("/chat/completions")) {
            cleanBase
        } else {
            "$cleanBase/chat/completions"
        }
    }

    private fun translateWithAi(trackTitle: String, originalLyrics: String): TranslationResult {
        val url = resolveEndpointUrl()

        // 1. 翻译歌词主体
        val promptSystem = "你是一位精通欧美流行音乐与诗意文学的专业歌词翻译家。请将用户提供的歌词逐行翻译为优美、符合原意、押韵自然的中文。务必保持与原歌词严格一一对应的行数和空行，每一行英文对应一行中文译文，绝对不要添加任何编号、多余解释、前后缀或代码块标签。"
        val promptUser = "歌曲标题：$trackTitle\n\n歌词全文：\n$originalLyrics"

        val requestBodyJson = JSONObject().apply {
            put("model", aiPreferences.modelName)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", promptSystem)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", promptUser)
                })
            })
            put("temperature", 0.3)
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${aiPreferences.apiKey}")
            .post(requestBodyJson.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw IllegalStateException("AI 服务无响应")

        if (!response.isSuccessful) {
            val errMsg = try {
                val errJson = JSONObject(responseBody)
                val errObj = errJson.optJSONObject("error")
                errObj?.optString("message") ?: responseBody
            } catch (e: Exception) {
                "HTTP ${response.code}: ${response.message}"
            }
            throw IllegalStateException("AI 接口报错: $errMsg")
        }

        val json = JSONObject(responseBody)
        if (json.has("error")) {
            val errMsg = json.optJSONObject("error")?.optString("message") ?: json.optString("error", "未知错误")
            throw IllegalStateException("AI 接口报错: $errMsg")
        }

        val choices = json.getJSONArray("choices")
        val content = choices.getJSONObject(0).getJSONObject("message").getString("content").trim()

        // 2. 翻译单曲名称
        val titlePrompt = "请给出这首歌曲标题的经典中文译名（例如 Complicated -> 复杂，Come Together -> 聚在一起，Let Go -> 展翅高飞/放手）。仅输出译名本身，不要附带任何多余标点或解释。"
        val titleRequestJson = JSONObject().apply {
            put("model", aiPreferences.modelName)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", titlePrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", trackTitle)
                })
            })
            put("temperature", 0.2)
        }.toString()

        val titleRequest = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${aiPreferences.apiKey}")
            .post(titleRequestJson.toRequestBody(jsonMediaType))
            .build()

        var translatedTitle: String? = null
        try {
            val titleResp = client.newCall(titleRequest).execute()
            val titleBody = titleResp.body?.string()
            if (titleBody != null && titleResp.isSuccessful) {
                val tJson = JSONObject(titleBody)
                if (!tJson.has("error")) {
                    val tChoices = tJson.optJSONArray("choices")
                    val tContent = tChoices?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.trim()
                    if (!tContent.isNullOrBlank()) {
                        translatedTitle = tContent.replace("\"", "").replace("《", "").replace("》", "").trim()
                    }
                }
            }
        } catch (e: Exception) {
            // 标题翻译失败非致命
        }

        return TranslationResult(
            translatedTitle = translatedTitle,
            translatedLyrics = content
        )
    }

    /**
     * 未配置 API Key 时的免费公共翻译降级方案
     */
    private fun fallbackTranslate(trackTitle: String, originalLyrics: String): TranslationResult {
        val lines = originalLyrics.lines()
        val translatedLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                translatedLines.add("")
            } else {
                try {
                    val encoded = URLEncoder.encode(trimmed, "UTF-8")
                    val queryUrl = "https://api.mymemory.translated.net/get?q=$encoded&langpair=en|zh"
                    val req = Request.Builder().url(queryUrl).build()
                    val resp = client.newCall(req).execute()
                    val body = resp.body?.string()
                    val trans = if (body != null) {
                        val obj = JSONObject(body)
                        obj.optJSONObject("responseData")?.optString("translatedText", trimmed) ?: trimmed
                    } else {
                        trimmed
                    }
                    translatedLines.add(trans)
                } catch (e: Exception) {
                    translatedLines.add(trimmed)
                }
            }
        }

        return TranslationResult(
            translatedTitle = null,
            translatedLyrics = translatedLines.joinToString("\n")
        )
    }
}
