package com.linernotes.app.presentation.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linernotes.app.core.preference.AiPreferences

@Composable
fun AiConfigDialog(
    aiPreferences: AiPreferences,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var apiKey by remember { mutableStateOf(aiPreferences.apiKey) }
    var baseUrl by remember { mutableStateOf(aiPreferences.baseUrl) }
    var modelName by remember { mutableStateOf(aiPreferences.modelName) }
    var isKeyVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
                    text = "支持所有兼容 OpenAI 格式的 API（如 DeepSeek、OpenAI、Moonshot/Kimi、通义千问、硅基流动等）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 快捷预设按钮
                Text("常用预设快速填充：", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SuggestionChip(
                        onClick = {
                            baseUrl = "https://api.deepseek.com/v1"
                            modelName = "deepseek-chat"
                        },
                        label = { Text("DeepSeek (推荐)") }
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
                    label = { Text("API Key (sk-...)") },
                    placeholder = { Text("粘贴你的 API Key") },
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
                    placeholder = { Text("https://api.deepseek.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Model Name
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("模型名称 (Model)") },
                    placeholder = { Text("deepseek-chat / gpt-4o-mini") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
