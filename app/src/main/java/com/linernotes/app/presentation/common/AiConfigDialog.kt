package com.linernotes.app.presentation.common

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
import com.linernotes.app.core.preference.AiPreferences
import kotlinx.coroutines.launch

@Composable
fun AiConfigDialog(
    aiPreferences: AiPreferences,
    onTestConnection: (suspend (apiKey: String, baseUrl: String, modelName: String) -> Pair<Boolean, String>)? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var apiKey by remember { mutableStateOf(aiPreferences.apiKey) }
    var baseUrl by remember { mutableStateOf(aiPreferences.baseUrl) }
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
                Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("配置 AI 歌词翻译引擎", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "支持 Google Gemini、DeepSeek、OpenAI、Moonshot/Kimi、通义千问等所有兼容 OpenAI 格式的 API。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 快捷预设按钮
                Text("常用引擎预设快速填充：", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai"
                            modelName = "gemini-3.6-flash"
                        },
                        label = { Text("Gemini 3.6 (官方推荐)") }
                    )
                    SuggestionChip(
                        onClick = {
                            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai"
                            modelName = "gemini-3.8-flash"
                        },
                        label = { Text("Gemini 3.8 (最新尝鲜)") }
                    )
                    SuggestionChip(
                        onClick = {
                            baseUrl = "https://api.deepseek.com/v1"
                            modelName = "deepseek-chat"
                        },
                        label = { Text("DeepSeek (国内免翻)") }
                    )
                    SuggestionChip(
                        onClick = {
                            baseUrl = "https://api.openai.com/v1"
                            modelName = "gpt-4o-mini"
                        },
                        label = { Text("OpenAI") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // API Key 输入框
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("粘贴 API Key (如 AQ... 或 sk-...)") },
                    singleLine = true,
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Base URL
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API 接口地址 (Base URL)") },
                    placeholder = { Text("https://generativelanguage.googleapis.com/v1beta/openai") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Model Name
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("模型名称 (Model)") },
                    placeholder = { Text("gemini-3.6-flash / gemini-3.8-flash") },
                    singleLine = true,
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
                                testResult = onTestConnection?.invoke(apiKey, baseUrl, modelName)
                                    ?: Pair(false, "测试服务未就绪")
                                isTesting = false
                            }
                        },
                        enabled = !isTesting
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("正在连接测试...")
                        } else {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("测试连通性")
                        }
                    }

                    TextButton(onClick = { showLogs = !showLogs }) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showLogs) "隐藏日志" else "诊断日志 (${AiDebugLogger.logs.size})")
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
                                    "实时网络与调用诊断日志",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Row {
                                    TextButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(AiDebugLogger.exportAsText()))
                                    }) {
                                        Text("复制全部", fontSize = 11.sp)
                                    }
                                    TextButton(onClick = { AiDebugLogger.clear() }) {
                                        Text("清空", fontSize = 11.sp)
                                    }
                                }
                            }

                            if (AiDebugLogger.logs.isEmpty()) {
                                Text(
                                    "暂无日志记录，可点击上方「测试连通性」发起一次网络检测",
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
                    aiPreferences.apiKey = apiKey
                    aiPreferences.baseUrl = baseUrl
                    aiPreferences.modelName = modelName
                    onSaved()
                }
            ) {
                Text("保存配置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
