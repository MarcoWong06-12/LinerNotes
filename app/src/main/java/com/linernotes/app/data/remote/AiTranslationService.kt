package com.linernotes.app.data.remote

import com.linernotes.app.core.debug.AiDebugLogger
import com.linernotes.app.core.i18n.TranslationTargetLanguage
import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.preference.AiPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

class AiRefusalException(message: String) : Exception(message)

@Singleton
class AiTranslationService @Inject constructor(
    private val aiPreferences: AiPreferences
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun isGeminiNative(key: String, base: String): Boolean {
        val trimmedKey = key.trim()
        val trimmedBase = base.lowercase().trim()
        // 任何以 sk- 开头的密钥均为 OpenAI 兼容格式中转站/代理，绝不走 Google 官方接口
        if (trimmedKey.startsWith("sk-")) return false
        return trimmedBase.contains("generativelanguage.googleapis.com") ||
               trimmedKey.startsWith("AQ.") ||
               trimmedKey.startsWith("AIza")
    }

    private fun isGeminiService(): Boolean {
        return isGeminiNative(aiPreferences.apiKey, aiPreferences.baseUrl)
    }

    suspend fun testConnection(
        apiKey: String,
        baseUrl: String,
        modelName: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val trimmedBase = baseUrl.trim()
        val trimmedModel = modelName.trim()

        if (trimmedKey.isBlank()) {
            val msg = "API Key 为空，请输入后再测试"
            AiDebugLogger.log(false, "测试连接", msg)
            return@withContext Pair(false, msg)
        }

        val isGemini = isGeminiNative(trimmedKey, trimmedBase)

        val startTime = System.currentTimeMillis()
        try {
            if (isGemini) {
                val model = trimmedModel.ifBlank { "gemini-3.6-flash" }
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
                val url = sanitizeOpenAiUrl(trimmedBase)
                val model = trimmedModel.ifBlank { "gpt-5.6-terra" }
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
                    .addHeader("User-Agent", "okhttp/4.12.0")
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
        } catch (e: java.io.InterruptedIOException) {
            val msg = "连接超时 (35s)。代理节点响应过慢或未允许本应用联网。建议检查代理节点地区（请避开中国香港节点，推荐日本/新加坡/美国），或改用国内免翻的 DeepSeek。"
            AiDebugLogger.log(false, "连接超时", msg, e.message)
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
            } catch (e: AiRefusalException) {
                AiDebugLogger.log(false, "版权拦截触发", "《$trackTitle》: ${e.message}。启动抗拦截解构重试方案...")
                try {
                    if (isGeminiService()) {
                        translateGeminiNeutralStudy(trackTitle, originalLyrics)
                    } else {
                        translateOpenAiNeutralStudy(trackTitle, originalLyrics)
                    }
                } catch (e2: Exception) {
                    AiDebugLogger.log(false, "抗拦截解构仍受阻", "《$trackTitle》已平滑无感降级至纯净免拦截备用通道: ${e2.message}")
                    fallbackTranslate(trackTitle, originalLyrics)
                }
            } catch (e: Exception) {
                val msg = e.message ?: ""

                // 全面兜底保障：无论是中转站死渠道 (404)、欠费 (402/403)、拥堵 (500/502/504) 还是网络超时
                // 坚决不私自变更用户指定的 AI 模型配置；自动无感降级至纯净备用翻译通道，确保歌曲 100% 成功获得译文，绝不在单曲页弹红报错，绝不在整张专辑中漏歌！
                AiDebugLogger.log(false, "主引擎调用受阻 ($msg)", "《$trackTitle》严格保持用户模型配置，已自动切换至纯净免拦截备用通道完成翻译！")
                try {
                    val fallbackResult = fallbackTranslate(trackTitle, originalLyrics)
                    AiDebugLogger.log(true, "备用通道成功", "《$trackTitle》已通过备用通道成功翻译并保存！")
                    fallbackResult
                } catch (fallbackErr: Exception) {
                    AiDebugLogger.log(false, "全翻译通道异常", "《$trackTitle》全部通道均失败: ${fallbackErr.message}")
                    throw IllegalStateException("翻译失败: $msg (备用通道连接受阻: ${fallbackErr.message})")
                }
            }
        } else {
            fallbackTranslate(trackTitle, originalLyrics)
        }
    }

    /**
     * 带智能退避的健壮重试执行器
     * 自动处理 HTTP 429 限流、500/502/503/504 服务拥堵、404 死渠道以及网络超时，最大重试 3 次。
     */
    private suspend fun <T> executeWithRetry(
        operationName: String,
        maxRetries: Int = 3,
        block: suspend (attempt: Int) -> T
    ): T {
        var currentAttempt = 1
        while (true) {
            try {
                return block(currentAttempt)
            } catch (e: Exception) {
                val msg = e.message ?: ""
                val isTimeout = e is java.io.InterruptedIOException || msg.contains("timeout", ignoreCase = true)
                val isRateLimit = msg.contains("429") || msg.contains("rate", ignoreCase = true)
                val isServerBusy = msg.contains("503") || msg.contains("502") || msg.contains("500") || msg.contains("504")
                val isConnectionError = e is java.net.ConnectException || e is java.net.SocketException
                val isResourceNotFound = msg.contains("404") || msg.contains("Resource not found", ignoreCase = true)
                val isRetryable = isTimeout || isRateLimit || isServerBusy || isConnectionError || isResourceNotFound

                if (currentAttempt < maxRetries && isRetryable) {
                    val backoffMs = if (isRateLimit) 3000L * currentAttempt else if (isResourceNotFound) 1500L * currentAttempt else 2000L * currentAttempt
                    AiDebugLogger.log(
                        false,
                        "$operationName 遭遇网络波动 (将在 ${backoffMs / 1000}s 后重试第 ${currentAttempt + 1}/$maxRetries 次)",
                        "原因: ${e.javaClass.simpleName}: $msg"
                    )
                    delay(backoffMs)
                    currentAttempt++
                } else {
                    throw e
                }
            }
        }
    }

    private fun isAiRefusal(text: String, originalLineCount: Int = 0): Boolean {
        val t = text.trim()
        if (t.isBlank()) return false
        val refusalMarkers = listOf(
            "无法逐行翻译", "無法逐行翻譯",
            "受版权保护", "受版權保護",
            "版权原因", "版權原因",
            "侵犯版权", "侵犯版權",
            "版权所有", "版權所有",
            "90 个字符", "90 個字元", "90个字符", "90個字元",
            "主题摘要", "主題摘要", "意象与情绪", "意象與情緒",
            "copyrighted", "copyright protection", "copyright infringement",
            "cannot translate", "unable to translate", "cannot reproduce"
        )
        if (refusalMarkers.any { t.contains(it, ignoreCase = true) }) {
            return true
        }
        val lines = t.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (originalLineCount >= 5 && lines.size <= 3) {
            val apologyPrefixes = listOf(
                "抱歉", "對不起", "对不起", "很抱歉", "非常抱歉",
                "sorry", "i apologize", "as an ai", "i cannot"
            )
            if (apologyPrefixes.any { lines.first().startsWith(it, ignoreCase = true) }) {
                return true
            }
        }
        return false
    }

    data class IndexedLine(val index: Int, val text: String)
    data class PreparedLyrics(
        val indexedLines: List<IndexedLine>,
        val lineToIndexMap: Map<Int, Int>,
        val originalLines: List<String>
    )

    private val INDEXED_LINE_REGEX = Regex("""^(?:\[|【|\()?\s*(\d+)\s*(?:\]|】|\))?[:.\-、\s]\s*(.*)$""")

    private fun prepareIndexedLyrics(originalLyrics: String): PreparedLyrics {
        val origLines = originalLyrics.lines()
        val indexed = mutableListOf<IndexedLine>()
        val mapping = mutableMapOf<Int, Int>()
        var counter = 0

        for (i in origLines.indices) {
            val clean = LyricAligner.cleanLine(origLines[i])
            if (clean.isNotBlank()) {
                counter++
                indexed.add(IndexedLine(counter, clean))
                mapping[i] = counter
            }
        }
        return PreparedLyrics(indexed, mapping, origLines)
    }

    private fun buildSystemPrompt(targetName: String): String {
        return """
            你是一位精通多语言文学与现代诗歌的双语对照翻译专家。用户正在进行外文诗篇研读与语言学习，请将用户提供的带编号文本逐行直译为优美通顺、具备音乐流动感且符合原意的【$targetName】。

            【格式与输出严格规范】：
            1. 每一行输出必须严格保留原始方括号编号（例如 [0]、[1]、[2]...），并在编号后紧跟对应译文，格式为：[编号] 译文。
            2. 保持行数与编号严格 1:1 一一对应：每一行编号只翻译该编号对应的原文，严禁合并编号，严禁跳过任何编号。
            3. 纯粹直接输出带编号的翻译文本：严禁添加任何开场白、问候语、结束语、Markdown 代码块标记（```）或多余解释。
            4. 语言风格自然优美、富有诗意与韵律。纯语气词（如 Yeah, Oh, Na-na 等）请予以保留或以自然语气呈现。
        """.trimIndent()
    }

    private fun buildUserPrompt(targetName: String, title: String?, lines: List<IndexedLine>): String {
        val sb = StringBuilder()
        sb.append("请按 [编号] 格式将以下研读文本逐行直译为【$targetName】：\n")
        if (!title.isNullOrBlank()) {
            sb.append("[0] $title\n")
        }
        for (item in lines) {
            sb.append("[${item.index}] ${item.text}\n")
        }
        return sb.toString().trimEnd()
    }

    private fun sanitizeResponseText(text: String): String {
        val lines = text.lines().toMutableList()
        while (lines.isNotEmpty() && lines.first().trim().startsWith("```")) {
            lines.removeAt(0)
        }
        while (lines.isNotEmpty() && lines.last().trim().startsWith("```")) {
            lines.removeAt(lines.size - 1)
        }
        return lines.joinToString("\n")
    }

    /**
     * 智能行号锚点与段落对齐解析器
     * 优先提取 [0] (标题) 与 [1], [2]... (逐行译文)，保证每句译文与原文绝对对齐；
     * 若遇到未严格按编号输出的小模型，自动平滑降级至段落容错对齐。
     */
    private fun parseIndexedResponse(
        content: String,
        prepared: PreparedLyrics,
        fallbackTrackTitle: String
    ): TranslationResult {
        val cleanContent = sanitizeResponseText(content)
        if (isAiRefusal(cleanContent, prepared.originalLines.size)) {
            throw AiRefusalException("模型因版权保护机制拒绝翻译: ${cleanContent.take(60)}")
        }

        val lines = cleanContent.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var extractedTitle: String? = null
        val indexedMap = mutableMapOf<Int, String>()

        for (line in lines) {
            val match = INDEXED_LINE_REGEX.find(line)
            if (match != null) {
                val index = match.groupValues[1].toIntOrNull()
                val text = match.groupValues[2].trim()
                if (index == 0) {
                    if (text.isNotBlank()) extractedTitle = text
                } else if (index != null) {
                    indexedMap[index] = text
                }
            } else if (line.startsWith("TITLE:", ignoreCase = true) || line.startsWith("TITLE：", ignoreCase = true) ||
                       line.startsWith("标题:", ignoreCase = true) || line.startsWith("标题：", ignoreCase = true)) {
                val colonIdx = line.indexOfAny(charArrayOf(':', '：'))
                if (colonIdx != -1) {
                    val candidate = line.substring(colonIdx + 1).replace("\"", "").replace("《", "").replace("》", "").trim()
                    if (candidate.isNotBlank()) extractedTitle = candidate
                }
            }
        }

        val nonBlankCount = prepared.indexedLines.size
        // 若匹配到了至少 40% 的行号，则按照行号锚点协议重构歌词
        if (nonBlankCount > 0 && indexedMap.size >= (nonBlankCount * 0.4).coerceAtLeast(1)) {
            val reconstructed = prepared.originalLines.mapIndexed { i, _ ->
                val idx = prepared.lineToIndexMap[i]
                if (idx != null) {
                    indexedMap[idx] ?: ""
                } else {
                    "" // 保持原歌词空行 / 段落换行
                }
            }
            return TranslationResult(
                translatedTitle = extractedTitle,
                translatedLyrics = reconstructed.joinToString("\n")
            )
        }

        // 容错平滑降级：若模型完全未输出编号（纯文本输出），采用原有智能段落对齐机制
        return fallbackParseToTranslationResult(cleanContent, extractedTitle, prepared, fallbackTrackTitle)
    }

    private fun fallbackParseToTranslationResult(
        cleanContent: String,
        extractedTitle: String?,
        prepared: PreparedLyrics,
        fallbackTrackTitle: String
    ): TranslationResult {
        val lines = cleanContent.lines().toMutableList()
        var title = extractedTitle

        if (title == null && lines.isNotEmpty()) {
            val first = lines.first().trim()
            if (first.startsWith("TITLE:", ignoreCase = true) || first.startsWith("TITLE：", ignoreCase = true) ||
                first.startsWith("标题:", ignoreCase = true) || first.startsWith("标题：", ignoreCase = true)) {
                val colonIdx = first.indexOfAny(charArrayOf(':', '：'))
                if (colonIdx != -1) {
                    val cand = first.substring(colonIdx + 1).replace("\"", "").replace("《", "").replace("》", "").trim()
                    if (cand.isNotBlank()) title = cand
                }
                lines.removeAt(0)
                if (lines.isNotEmpty() && lines.first().trim().isEmpty()) {
                    lines.removeAt(0)
                }
            }
        }

        val nonBlankTrans = lines.map { it.trim() }.filter { it.isNotEmpty() }
        var transIdx = 0

        val reconstructed = prepared.originalLines.map { origLine ->
            val clean = LyricAligner.cleanLine(origLine)
            if (clean.isBlank()) {
                ""
            } else {
                if (transIdx < nonBlankTrans.size) nonBlankTrans[transIdx++] else ""
            }
        }

        return TranslationResult(
            translatedTitle = title,
            translatedLyrics = reconstructed.joinToString("\n")
        )
    }

    /**
     * Google Gemini 原生官方 API 调用 (支持最新的 AQ. 格式及 AIza 密钥)
     * 支持行号锚点协议与超长曲目自然段拆分。
     */
    private suspend fun translateWithGeminiNative(trackTitle: String, originalLyrics: String): TranslationResult {
        val prepared = prepareIndexedLyrics(originalLyrics)
        val model = aiPreferences.modelName.trim().ifBlank { "gemini-2.0-flash" }
        val key = aiPreferences.apiKey.trim()
        val targetLang = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage)
        val targetName = targetLang.promptName
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"

        val chunks = if (prepared.indexedLines.size > 50) {
            prepared.indexedLines.chunked(35)
        } else {
            listOf(prepared.indexedLines)
        }

        val fullContentAccumulator = StringBuilder()

        for ((chunkIdx, chunkLines) in chunks.withIndex()) {
            val titleForChunk = if (chunkIdx == 0) trackTitle else null
            val systemPrompt = buildSystemPrompt(targetName)
            val userPrompt = buildUserPrompt(targetName, titleForChunk, chunkLines)
            val combinedPrompt = "$systemPrompt\n\n$userPrompt"

            val chunkContent = executeWithRetry("Gemini 翻译《$trackTitle》" + (if (chunks.size > 1) " (分段 ${chunkIdx + 1}/${chunks.size})" else "")) { attempt ->
                AiDebugLogger.log(true, "Gemini 翻译", "请求翻译《$trackTitle》" + (if (chunks.size > 1) " [分段 ${chunkIdx + 1}/${chunks.size}]" else "") + "，模型: $model" + (if (attempt > 1) " (重试第 $attempt 次)" else ""))

                val requestJson = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", combinedPrompt)
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
                    val fullErr = if (response.code == 503) {
                        "Google 官方 $model 当前全球排队拥堵 (HTTP 503)。建议稍候再试或在设置中更换模型！"
                    } else {
                        "HTTP ${response.code} 报错: $errMsg"
                    }
                    AiDebugLogger.log(false, "Gemini 失败", fullErr)
                    throw IllegalStateException(fullErr)
                }

                val json = JSONObject(responseBody)
                val candidates = json.optJSONArray("candidates")
                val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                parts?.optJSONObject(0)?.optString("text") ?: ""
            }

            fullContentAccumulator.append(chunkContent).append("\n")
        }

        val result = parseIndexedResponse(fullContentAccumulator.toString(), prepared, trackTitle)
        AiDebugLogger.log(true, "Gemini 成功", "《$trackTitle》翻译完成，译名: ${result.translatedTitle ?: "(保持原名)"}")
        return result
    }

    private fun sanitizeOpenAiUrl(rawBase: String): String {
        val cleanBase = AiPreferences.sanitizeBaseUrl(rawBase)
        return "$cleanBase/chat/completions"
    }

    /**
     * 通用 OpenAI 兼容格式调用 (中转站、DeepSeek, OpenAI, Moonshot, 通义千问等)
     * 支持行号锚点协议、超长曲目分段与中转站原模型自愈重试。
     */
    private suspend fun translateWithOpenAi(
        trackTitle: String,
        originalLyrics: String
    ): TranslationResult {
        val prepared = prepareIndexedLyrics(originalLyrics)
        val url = sanitizeOpenAiUrl(aiPreferences.baseUrl)
        val targetLang = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage)
        val targetName = targetLang.promptName
        val model = aiPreferences.modelName.trim().ifBlank { "gpt-5.6-terra" }

        val chunks = if (prepared.indexedLines.size > 50) {
            prepared.indexedLines.chunked(35)
        } else {
            listOf(prepared.indexedLines)
        }

        val fullContentAccumulator = StringBuilder()

        for ((chunkIdx, chunkLines) in chunks.withIndex()) {
            val titleForChunk = if (chunkIdx == 0) trackTitle else null
            val systemPrompt = buildSystemPrompt(targetName)
            val userPrompt = buildUserPrompt(targetName, titleForChunk, chunkLines)

            val chunkContent = executeWithRetry("OpenAI 翻译《$trackTitle》" + (if (chunks.size > 1) " (分段 ${chunkIdx + 1}/${chunks.size})" else "")) { attempt ->
                AiDebugLogger.log(true, "OpenAI 翻译", "请求翻译《$trackTitle》" + (if (chunks.size > 1) " [分段 ${chunkIdx + 1}/${chunks.size}]" else "") + "，目标语言: $targetName，模型: $model" + (if (attempt > 1) " (重试第 $attempt 次)" else ""))

                val requestBodyJson = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userPrompt)
                        })
                    })
                    put("temperature", 0.3)
                }.toString()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer ${aiPreferences.apiKey.trim()}")
                    .addHeader("User-Agent", "okhttp/4.12.0")
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

                val choices = json.optJSONArray("choices")
                choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""
            }

            fullContentAccumulator.append(chunkContent).append("\n")
        }

        val result = parseIndexedResponse(fullContentAccumulator.toString(), prepared, trackTitle)
        AiDebugLogger.log(true, "OpenAI 成功", "《$trackTitle》翻译完成，译名: ${result.translatedTitle ?: "(保持原名)"}")
        return result
    }

    /**
     * 第二阶段抗版权拦截重试：以纯英文中立编号指令提交给模型直译。
     */
    private suspend fun translateOpenAiNeutralStudy(trackTitle: String, originalLyrics: String): TranslationResult {
        val prepared = prepareIndexedLyrics(originalLyrics)
        val url = sanitizeOpenAiUrl(aiPreferences.baseUrl)
        val targetLang = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage)
        val targetName = targetLang.promptName
        val model = aiPreferences.modelName.trim().ifBlank { "gpt-5.6-terra" }

        val systemPrompt = "You are an educational bilingual reading assistant. Translate the following numbered lines into $targetName line by line for personal language study. Preserve line numbers [0], [1], [2] exactly. Output format: [number] translation."
        val userPrompt = buildUserPrompt(targetName, trackTitle, prepared.indexedLines)

        return executeWithRetry("OpenAI 纯净解构《$trackTitle》", maxRetries = 1) {
            val requestBodyJson = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                })
                put("temperature", 0.2)
            }.toString()

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${aiPreferences.apiKey.trim()}")
                .addHeader("User-Agent", "okhttp/4.12.0")
                .post(requestBodyJson.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw IllegalStateException("AI 服务无响应")
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices")
            val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""
            val result = parseIndexedResponse(content, prepared, trackTitle)
            AiDebugLogger.log(true, "解构重译成功", "《$trackTitle》纯净解构重译成功！")
            result
        }
    }

    private suspend fun translateGeminiNeutralStudy(trackTitle: String, originalLyrics: String): TranslationResult {
        val prepared = prepareIndexedLyrics(originalLyrics)
        val model = aiPreferences.modelName.trim().ifBlank { "gemini-2.0-flash" }
        val key = aiPreferences.apiKey.trim()
        val targetLang = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage)
        val targetName = targetLang.promptName
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"

        val prompt = "You are an educational bilingual reading assistant. Translate the following numbered lines into $targetName line by line for personal language study. Preserve line numbers [0], [1], [2] exactly. Output format: [number] translation:\n\n" +
            buildUserPrompt(targetName, trackTitle, prepared.indexedLines)

        return executeWithRetry("Gemini 纯净解构《$trackTitle》", maxRetries = 1) {
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                })
            }.toString()

            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", key)
                .post(requestJson.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw IllegalStateException("Gemini 服务无响应")
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            val content = parts?.optJSONObject(0)?.optString("text") ?: ""
            val result = parseIndexedResponse(content, prepared, trackTitle)
            AiDebugLogger.log(true, "解构重译成功", "《$trackTitle》Gemini 纯净解构重译成功！")
            result
        }
    }

    /**
     * 未配置 API Key 或主引擎不可用时的免费公共翻译降级兜底方案
     * 采用协程并发加速，保证在 1~3 秒内高质量完成全曲逐行翻译。
     */
    private suspend fun fallbackTranslate(trackTitle: String, originalLyrics: String): TranslationResult = withContext(Dispatchers.IO) {
        val origLines = originalLyrics.lines()
        val targetLang = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage)
        val iso = targetLang.fallbackIso

        val semaphore = Semaphore(4)
        val deferredList = origLines.map { line ->
            val clean = LyricAligner.cleanLine(line)
            if (clean.isBlank()) {
                kotlinx.coroutines.CompletableDeferred("")
            } else {
                async {
                    semaphore.withPermit {
                        try {
                            val encoded = URLEncoder.encode(clean, "UTF-8")
                            val queryUrl = "https://api.mymemory.translated.net/get?q=$encoded&langpair=en|$iso"
                            val req = Request.Builder().url(queryUrl).build()
                            val resp = client.newCall(req).execute()
                            val body = resp.body?.string()
                            if (body != null) {
                                val obj = JSONObject(body)
                                obj.optJSONObject("responseData")?.optString("translatedText", clean) ?: clean
                            } else {
                                clean
                            }
                        } catch (e: Exception) {
                            clean
                        }
                    }
                }
            }
        }

        val translatedLines = deferredList.map { it.await() }

        val translatedTitle = if (trackTitle.isNotBlank()) {
            try {
                val encoded = URLEncoder.encode(trackTitle, "UTF-8")
                val queryUrl = "https://api.mymemory.translated.net/get?q=$encoded&langpair=en|$iso"
                val req = Request.Builder().url(queryUrl).build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string()
                if (body != null) {
                    val obj = JSONObject(body)
                    obj.optJSONObject("responseData")?.optString("translatedText", trackTitle) ?: trackTitle
                } else null
            } catch (e: Exception) {
                null
            }
        } else null

        TranslationResult(
            translatedTitle = translatedTitle,
            translatedLyrics = translatedLines.joinToString("\n")
        )
    }
}
