package com.linernotes.app.data.remote

import com.linernotes.app.core.debug.AiDebugLogger
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
        .callTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun isGeminiService(): Boolean {
        val base = aiPreferences.baseUrl.lowercase()
        val model = aiPreferences.modelName.lowercase()
        val key = aiPreferences.apiKey.trim()
        return base.contains("generativelanguage.googleapis.com") ||
               key.startsWith("AQ.") ||
               key.startsWith("AIza") ||
               model.contains("gemini")
    }

    suspend fun testConnection(
        apiKey: String,
        baseUrl: String,
        modelName: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val trimmedBase = baseUrl.trim().trimEnd('/')
        val trimmedModel = modelName.trim()

        if (trimmedKey.isBlank()) {
            val msg = "API Key 为空，请输入后再测试"
            AiDebugLogger.log(false, "测试连接", msg)
            return@withContext Pair(false, msg)
        }

        val isGemini = trimmedBase.contains("generativelanguage.googleapis.com") ||
                       trimmedKey.startsWith("AQ.") ||
                       trimmedKey.startsWith("AIza") ||
                       trimmedModel.contains("gemini", ignoreCase = true)

        val startTime = System.currentTimeMillis()
        try {
            if (isGemini) {
                val model = trimmedModel.ifBlank { "gemini-3.8-flash" }
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$trimmedKey"
                AiDebugLogger.log(true, "测试开始", "发起 Gemini 测试: $model")

                val testJson = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", "Hi, reply 1 word.") })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("maxOutputTokens", 10)
                    })
                }.toString()

                val req = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", trimmedKey)
                    .post(testJson.toRequestBody(jsonMediaType))
                    .build()

                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                val duration = System.currentTimeMillis() - startTime

                if (!resp.isSuccessful) {
                    val errMsg = try {
                        val obj = JSONObject(body)
                        obj.optJSONObject("error")?.optString("message") ?: body
                    } catch (e: Exception) {
                        "HTTP ${resp.code}: ${resp.message}"
                    }
                    val fullMsg = "HTTP ${resp.code} 报错: $errMsg"
                    AiDebugLogger.log(false, "Gemini 测试失败", fullMsg, "详情: $body")
                    return@withContext Pair(false, fullMsg)
                }

                AiDebugLogger.log(true, "Gemini 测试成功", "耗时 ${duration}ms，状态码 200 OK")
                return@withContext Pair(true, "连接成功！(耗时 ${duration}ms，Gemini 响应正常)")
            } else {
                val url = if (trimmedBase.endsWith("/chat/completions")) trimmedBase else "$trimmedBase/chat/completions"
                val model = trimmedModel.ifBlank { "deepseek-chat" }
                AiDebugLogger.log(true, "测试开始", "发起 OpenAI 格式测试: $url ($model)")

                val testJson = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", "Hi")
                        })
                    })
                    put("max_tokens", 10)
                }.toString()

                val req = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $trimmedKey")
                    .post(testJson.toRequestBody(jsonMediaType))
                    .build()

                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                val duration = System.currentTimeMillis() - startTime

                if (!resp.isSuccessful) {
                    val errMsg = try {
                        val obj = JSONObject(body)
                        obj.optJSONObject("error")?.optString("message") ?: body
                    } catch (e: Exception) {
                        "HTTP ${resp.code}: ${resp.message}"
                    }
                    val fullMsg = "HTTP ${resp.code} 报错: $errMsg"
                    AiDebugLogger.log(false, "OpenAI 测试失败", fullMsg, "详情: $body")
                    return@withContext Pair(false, fullMsg)
                }

                AiDebugLogger.log(true, "OpenAI 测试成功", "耗时 ${duration}ms，状态码 200 OK")
                return@withContext Pair(true, "连接成功！(耗时 ${duration}ms，接口响应正常)")
            }
        } catch (e: java.net.SocketTimeoutException) {
            val msg = "连接超时 (10s)。若使用 Gemini，国内手机请开启代理/科学上网；或改用免代理的 DeepSeek。"
            AiDebugLogger.log(false, "测试超时", msg, e.message)
            Pair(false, msg)
        } catch (e: java.net.UnknownHostException) {
            val msg = "无法解析服务器域名。请检查手机网络或代理设置是否允许应用联网。"
            AiDebugLogger.log(false, "解析域名失败", msg, e.message)
            Pair(false, msg)
        } catch (e: Exception) {
            val msg = "连接异常: ${e.javaClass.simpleName} (${e.message})"
            AiDebugLogger.log(false, "测试异常", msg, e.stackTraceToString().take(500))
            Pair(false, msg)
        }
    }

    suspend fun translateTrack(
        trackTitle: String,
        originalLyrics: String
    ): TranslationResult = withContext(Dispatchers.IO) {
        if (aiPreferences.hasKey) {
            try {
                if (isGeminiService()) {
                    translateWithGeminiNative(trackTitle, originalLyrics)
                } else {
                    translateWithOpenAi(trackTitle, originalLyrics)
                }
            } catch (e: java.net.SocketTimeoutException) {
                val err = "连接 AI 超时。若使用 Gemini，国内网络请开启手机代理/科学上网；或改用免代理的 DeepSeek。"
                AiDebugLogger.log(false, "翻译超时", err)
                throw IllegalStateException(err)
            } catch (e: java.net.UnknownHostException) {
                val err = "无法解析域名。请检查手机网络或代理设置是否允许应用联网。"
                AiDebugLogger.log(false, "网络错误", err)
                throw IllegalStateException(err)
            } catch (e: java.net.ConnectException) {
                val err = "网络连接失败。若使用 Gemini，请确认手机代理正常生效。"
                AiDebugLogger.log(false, "连接拒绝", err)
                throw IllegalStateException(err)
            } catch (e: Exception) {
                AiDebugLogger.log(false, "翻译失败", e.message ?: "未知异常")
                throw e
            }
        } else {
            fallbackTranslate(trackTitle, originalLyrics)
        }
    }

    /**
     * Google Gemini 原生官方 API 调用 (支持最新的 AQ. 格式及 AIza 密钥)
     */
    private fun translateWithGeminiNative(trackTitle: String, originalLyrics: String): TranslationResult {
        val model = aiPreferences.modelName.trim().ifBlank { "gemini-3.8-flash" }
        val key = aiPreferences.apiKey.trim()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
        AiDebugLogger.log(true, "Gemini 翻译", "开始翻译《$trackTitle》，模型: $model")

        val promptLyrics = "你是一位精通欧美流行音乐与诗意文学的专业歌词翻译家。请将用户提供的歌词逐行翻译为优美、符合原意、押韵自然的中文。务必保持与原歌词严格一一对应的行数和空行，每一行英文对应一行中文译文，绝对不要添加任何编号、多余解释、前后缀或代码块标签。\n\n歌曲标题：$trackTitle\n\n歌词全文：\n$originalLyrics"

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", promptLyrics)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
            })
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", key)
            .post(requestJson.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw IllegalStateException("Gemini 服务无响应")

        if (!response.isSuccessful) {
            val errMsg = try {
                val errJson = JSONObject(responseBody)
                val errObj = errJson.optJSONObject("error")
                errObj?.optString("message") ?: responseBody
            } catch (e: Exception) {
                "HTTP ${response.code}: ${response.message}"
            }
            val fullErr = "HTTP ${response.code} 报错: $errMsg"
            AiDebugLogger.log(false, "Gemini 失败", fullErr)
            throw IllegalStateException(fullErr)
        }

        val json = JSONObject(responseBody)
        val candidates = json.optJSONArray("candidates")
        val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
        val lyricsContent = parts?.optJSONObject(0)?.optString("text")?.trim() ?: ""
        AiDebugLogger.log(true, "Gemini 成功", "歌词主体翻译完成")

        // 单曲中文译名
        var translatedTitle: String? = null
        try {
            val titlePrompt = "请给出这首歌曲标题的经典中文译名（例如 Complicated -> 复杂，Come Together -> 聚在一起，Let Go -> 展翅高飞/放手）。仅输出译名本身，不要附带任何多余标点或解释。\n\n歌曲标题：$trackTitle"
            val titleRequestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", titlePrompt)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                })
            }.toString()

            val titleReq = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", aiPreferences.apiKey.trim())
                .post(titleRequestJson.toRequestBody(jsonMediaType))
                .build()

            val titleResp = client.newCall(titleReq).execute()
            val titleBody = titleResp.body?.string()
            if (titleBody != null && titleResp.isSuccessful) {
                val tJson = JSONObject(titleBody)
                val tCandidates = tJson.optJSONArray("candidates")
                val tParts = tCandidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                val tContent = tParts?.optJSONObject(0)?.optString("text")?.trim()
                if (!tContent.isNullOrBlank()) {
                    translatedTitle = tContent.replace("\"", "").replace("《", "").replace("》", "").trim()
                }
            }
        } catch (e: Exception) {
            // 忽略非致命单曲名翻译异常
        }

        return TranslationResult(
            translatedTitle = translatedTitle,
            translatedLyrics = lyricsContent
        )
    }

    /**
     * 通用 OpenAI 兼容格式调用 (DeepSeek, OpenAI, Moonshot, 通义千问等)
     */
    private fun translateWithOpenAi(trackTitle: String, originalLyrics: String): TranslationResult {
        var cleanBase = aiPreferences.baseUrl.trim().trimEnd('/')
        val url = if (cleanBase.endsWith("/chat/completions")) cleanBase else "$cleanBase/chat/completions"
        AiDebugLogger.log(true, "OpenAI 翻译", "开始翻译《$trackTitle》，模型: ${aiPreferences.modelName}")

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
            .addHeader("Authorization", "Bearer ${aiPreferences.apiKey.trim()}")
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
            val fullErr = "HTTP ${response.code} 报错: $errMsg"
            AiDebugLogger.log(false, "OpenAI 失败", fullErr)
            throw IllegalStateException(fullErr)
        }

        val json = JSONObject(responseBody)
        if (json.has("error")) {
            val errMsg = json.optJSONObject("error")?.optString("message") ?: json.optString("error", "未知错误")
            AiDebugLogger.log(false, "OpenAI 报错", errMsg)
            throw IllegalStateException("AI 接口报错: $errMsg")
        }

        val choices = json.getJSONArray("choices")
        val content = choices.getJSONObject(0).getJSONObject("message").getString("content").trim()
        AiDebugLogger.log(true, "OpenAI 成功", "歌词主体翻译完成")

        var translatedTitle: String? = null
        try {
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
                .addHeader("Authorization", "Bearer ${aiPreferences.apiKey.trim()}")
                .post(titleRequestJson.toRequestBody(jsonMediaType))
                .build()

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
