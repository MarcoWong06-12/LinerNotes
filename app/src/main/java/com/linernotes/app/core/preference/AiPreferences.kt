package com.linernotes.app.core.preference

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    private val _appLanguageFlow = kotlinx.coroutines.flow.MutableStateFlow(
        prefs.getString("app_language", com.linernotes.app.core.i18n.AppLanguage.SYSTEM.code) ?: com.linernotes.app.core.i18n.AppLanguage.SYSTEM.code
    )
    val appLanguageFlow: kotlinx.coroutines.flow.StateFlow<String> = _appLanguageFlow

    var appLanguage: String
        get() = _appLanguageFlow.value
        set(value) {
            prefs.edit().putString("app_language", value).apply()
            _appLanguageFlow.value = value
        }

    var targetLanguage: String
        get() = prefs.getString("target_language", com.linernotes.app.core.i18n.TranslationTargetLanguage.ZH_CN.code) ?: com.linernotes.app.core.i18n.TranslationTargetLanguage.ZH_CN.code
        set(value) = prefs.edit().putString("target_language", value).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value.trim()).apply()

    var baseUrl: String
        get() {
            val raw = prefs.getString("base_url", "https://www.kuaiaiapi.com/v1") ?: "https://www.kuaiaiapi.com/v1"
            return sanitizeBaseUrl(raw)
        }
        set(value) = prefs.edit().putString("base_url", sanitizeBaseUrl(value)).apply()

    var modelName: String
        get() = prefs.getString("model_name", "gpt-5.6-terra") ?: "gpt-5.6-terra"
        set(value) = prefs.edit().putString("model_name", value.trim()).apply()

    var lyricsSource: String
        get() = prefs.getString("lyrics_source", LyricsSourcePreference.AUTO_FIRST.code) ?: LyricsSourcePreference.AUTO_FIRST.code
        set(value) = prefs.edit().putString("lyrics_source", value).apply()

    val hasKey: Boolean
        get() = apiKey.isNotBlank()

    enum class LyricsSourcePreference(val code: String, val displayNameZh: String, val displayNameEn: String) {
        AUTO_FIRST("auto_first", "智能多源聚合 (推荐：网易云+QQ+酷狗+LRCLIB)", "Smart Multi-Source (NetEase + QQ + Kugou + LRCLIB)"),
        NETEASE_ONLY("netease_only", "网易云音乐 (Netease Cloud Music)", "Netease Cloud Music"),
        QQ_ONLY("qq_only", "QQ 音乐 (QQ Music)", "QQ Music"),
        KUGOU_ONLY("kugou_only", "酷狗音乐 (Kugou Music)", "Kugou Music"),
        LRCLIB_ONLY("lrclib_only", "LRCLIB (全球开源歌词库)", "LRCLIB"),
        AI_ONLY("ai_only", "仅使用 AI 智能翻译", "AI Translation Only");

        companion object {
            fun fromCode(code: String): LyricsSourcePreference =
                entries.find { it.code.equals(code, ignoreCase = true) } ?: AUTO_FIRST
        }
    }

    companion object {
        fun sanitizeBaseUrl(raw: String): String {
            val clean = raw
                .replace("POST ", "")
                .replace("post ", "")
                .replace("GET ", "")
                .replace("get ", "")
                .replace("\n", "")
                .replace("\r", "")
                .trim()

            if (clean.isBlank()) return "https://www.kuaiaiapi.com/v1"

            return try {
                val withScheme = if (clean.startsWith("http://") || clean.startsWith("https://")) clean else "https://$clean"
                val uri = java.net.URI(withScheme)
                val scheme = uri.scheme ?: "https"
                val host = uri.host ?: return clean
                val port = if (uri.port != -1) ":${uri.port}" else ""
                val rawPath = uri.path ?: ""

                val segments = rawPath.split('/')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it != "chat" && it != "completions" }

                val cleanSegments = mutableListOf<String>()
                for (seg in segments) {
                    if (seg == "v1" && cleanSegments.contains("v1")) continue
                    cleanSegments.add(seg)
                }

                val finalPath = if (cleanSegments.isNotEmpty()) cleanSegments.joinToString("/") else "v1"
                "$scheme://$host$port/$finalPath"
            } catch (e: Exception) {
                clean.removeSuffix("/chat/completions").removeSuffix("/")
            }
        }
    }
}
