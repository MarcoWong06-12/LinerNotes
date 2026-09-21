package com.linernotes.app.core.debug

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AiLogEntry(
    val id: Long = System.currentTimeMillis(),
    val time: String,
    val isSuccess: Boolean,
    val tag: String,
    val message: String,
    val details: String? = null
)

object AiDebugLogger {
    val logs = mutableStateListOf<AiLogEntry>()

    fun log(isSuccess: Boolean, tag: String, message: String, details: String? = null) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logs.add(0, AiLogEntry(time = time, isSuccess = isSuccess, tag = tag, message = message, details = details))
        while (logs.size > 30) {
            logs.removeLast()
        }
    }

    fun clear() {
        logs.clear()
    }

    fun exportAsText(): String {
        return logs.joinToString("\n---\n") { entry ->
            val icon = if (entry.isSuccess) "✅" else "❌"
            "[$icon ${entry.time}] [${entry.tag}] ${entry.message}\n${entry.details ?: ""}".trim()
        }
    }
}
