package com.linernotes.app.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linernotes.app.core.debug.AiDebugLogger
import com.linernotes.app.core.i18n.AppLanguage
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.core.i18n.TranslationTargetLanguage
import com.linernotes.app.core.preference.AiPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigDialog(
    aiPreferences: AiPreferences,
    onTestConnection: (suspend (apiKey: String, baseUrl: String, modelName: String) -> Pair<Boolean, String>)? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val strings = LocalStrings.current

    var appLanguage by remember { mutableStateOf(aiPreferences.appLanguage) }
    var targetLanguage by remember { mutableStateOf(aiPreferences.targetLanguage) }
    var lyricsSource by remember { mutableStateOf(aiPreferences.lyricsSource) }

    var apiKey by remember { mutableStateOf(aiPreferences.apiKey) }
    var baseUrl by remember { mutableStateOf(AiPreferences.sanitizeBaseUrl(aiPreferences.baseUrl)) }
    var modelName by remember { mutableStateOf(aiPreferences.modelName) }
    var isKeyVisible by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showLogs by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.settingsDialogTitle, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // ==========================================
                // 1. 语言与本地化配置专区
                // ==========================================
                Text(
                    text = strings.sectionLocalization,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 应用界面语言
                Text(strings.appLanguageLabel, style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = appLanguage == lang.code,
                            onClick = {
                                appLanguage = lang.code
                                aiPreferences.appLanguage = lang.code
                            },
                            label = { Text(lang.displayName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 歌词翻译目标语言
                Text(strings.targetLanguageLabel, style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TranslationTargetLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = targetLanguage == lang.code,
                            onClick = {
                                targetLanguage = lang.code
                            },
                            label = { Text(lang.displayName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // 歌词与翻译首选源
                // ==========================================
                Text(
                    text = "🎵 " + strings.lyricsSourceSetting,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val options = listOf(
                        AiPreferences.LyricsSourcePreference.AUTO_FIRST.code to strings.sourceAuto,
                        AiPreferences.LyricsSourcePreference.NETEASE_ONLY.code to strings.sourceOfficial,
                        AiPreferences.LyricsSourcePreference.QQ_ONLY.code to strings.sourceQq,
                        AiPreferences.LyricsSourcePreference.KUGOU_ONLY.code to strings.sourceKugou,
                        AiPreferences.LyricsSourcePreference.MUSIXMATCH_ONLY.code to strings.sourceMusixmatch,
                        AiPreferences.LyricsSourcePreference.LRCLIB_ONLY.code to strings.sourceLrclib,
                        AiPreferences.LyricsSourcePreference.AI_ONLY.code to strings.sourceAi
                    )

                    options.forEach { (code, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { lyricsSource = code }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = lyricsSource == code,
                                onClick = { lyricsSource = code }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // 2. AI 翻译引擎配置专区
                // ==========================================
                Text(
                    text = strings.sectionAiEngine,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strings.aiEngineDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 快捷预设按钮
                Text(strings.presetsTitle, style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            baseUrl = "https://www.kuaiaiapi.com/v1"
                            modelName = "gpt-4o-mini"
                        },
                        label = { Text(strings.presetKuaiaiFast) }
                    )
                    SuggestionChip(
                        onClick = {
                            baseUrl = "https://www.kuaiaiapi.com/v1"
                            modelName = "gpt-5.6-terra"
                        },
                        label = { Text(strings.presetKuaiai) }
                    )
                    SuggestionChip(
                        onClick = {
                            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai"
                            modelName = "gemini-2.0-flash"
                        },
                        label = { Text(strings.presetGemini36) }
                    )
                    SuggestionChip(
                        onClick = {
                            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai"
                            modelName = "gemini-1.5-flash"
                        },
                        label = { Text(strings.presetGemini38) }
                    )
                    SuggestionChip(
                        onClick = {
                            baseUrl = "https://api.deepseek.com/v1"
                            modelName = "deepseek-chat"
                        },
                        label = { Text(strings.presetDeepSeek) }
                    )
                    SuggestionChip(
                        onClick = {
                            baseUrl = "https://api.openai.com/v1"
                            modelName = "gpt-4o-mini"
                        },
                        label = { Text(strings.presetOpenAi) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // API Key 输入框
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it.replace("\n", "").replace("\r", "").trim() },
                    label = { Text(strings.apiKeyLabel) },
                    placeholder = { Text(strings.apiKeyPlaceholder) },
                    singleLine = true,
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val clip = clipboardManager.getText()?.text?.trim()
                                if (!clip.isNullOrBlank()) {
                                    apiKey = clip.replace("\n", "").replace("\r", "").trim()
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "粘贴剪贴板内容")
                            }
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Base URL
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it.replace("POST ", "")
                            .replace("post ", "")
                            .replace("\n", "")
                            .replace("\r", "")
                            .trim()
                    },
                    label = { Text(strings.baseUrlLabel) },
                    placeholder = { Text(strings.baseUrlPlaceholder) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            val clip = clipboardManager.getText()?.text?.trim()
                            if (!clip.isNullOrBlank()) {
                                baseUrl = AiPreferences.sanitizeBaseUrl(clip)
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "粘贴剪贴板内容")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Model Name
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it.replace("\n", "").replace("\r", "").trim() },
                    label = { Text(strings.modelLabel) },
                    placeholder = { Text(strings.modelPlaceholder) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            val clip = clipboardManager.getText()?.text?.trim()
                            if (!clip.isNullOrBlank()) {
                                modelName = clip.replace("\n", "").replace("\r", "").trim()
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "粘贴剪贴板内容")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 测试与诊断操作行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isTesting = true
                                testResult = null
                                val cleanBase = AiPreferences.sanitizeBaseUrl(baseUrl)
                                baseUrl = cleanBase
                                testResult = onTestConnection?.invoke(apiKey.trim(), cleanBase, modelName.trim())
                                    ?: Pair(false, "测试服务未就绪")
                                isTesting = false
                            }
                        },
                        enabled = !isTesting
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.testingStatus)
                        } else {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.testConnectionBtn)
                        }
                    }

                    TextButton(onClick = { showLogs = !showLogs }) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showLogs) strings.hideLogsBtn else "${strings.diagnosticLogsBtn} (${AiDebugLogger.logs.size})")
                    }
                }

                // 连通性测试结果提示条
                testResult?.let { result ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = if (result.first) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = (if (result.first) "✅ " else "❌ ") + result.second,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (result.first) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // 诊断日志列表面板
                if (showLogs) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    strings.logsTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Row {
                                    TextButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(AiDebugLogger.exportAsText()))
                                    }) {
                                        Text(strings.copyAll, fontSize = 11.sp)
                                    }
                                    TextButton(onClick = { AiDebugLogger.clear() }) {
                                        Text(strings.clearLogs, fontSize = 11.sp)
                                    }
                                }
                            }

                            if (AiDebugLogger.logs.isEmpty()) {
                                Text(
                                    strings.noLogsYet,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 180.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    AiDebugLogger.logs.forEach { log ->
                                        Text(
                                            text = "[${if (log.isSuccess) "✅" else "❌"} ${log.time}] [${log.tag}] ${log.message}" +
                                                    (if (!log.details.isNullOrBlank()) "\n${log.details}" else ""),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            color = if (log.isSuccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    aiPreferences.appLanguage = appLanguage
                    aiPreferences.targetLanguage = targetLanguage
                    aiPreferences.lyricsSource = lyricsSource
                    aiPreferences.apiKey = apiKey.trim()
                    aiPreferences.baseUrl = AiPreferences.sanitizeBaseUrl(baseUrl)
                    aiPreferences.modelName = modelName.trim()
                    onSaved()
                }
            ) {
                Text(strings.saveConfigBtn)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
