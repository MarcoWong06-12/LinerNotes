package com.linernotes.app.presentation.booklet.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.linernotes.app.data.local.entity.TrackEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLyricSheet(
    track: TrackEntity,
    onDismiss: () -> Unit,
    onSave: (translatedTitle: String?, originalLyrics: String?, translatedLyrics: String?) -> Unit
) {
    var zhTitle by remember { mutableStateOf(track.translatedTitle ?: "") }
    var origLyrics by remember { mutableStateOf(track.originalLyrics ?: "") }
    var transLyrics by remember { mutableStateOf(track.translatedLyrics ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "校对曲目内页数据",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = zhTitle,
                onValueChange = { zhTitle = it },
                label = { Text("中文曲名译名") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = origLyrics,
                onValueChange = { origLyrics = it },
                label = { Text("原语种歌词 (逐行)") },
                modifier = Modifier.fillMaxWidth().height(160.dp),
                maxLines = 10
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = transLyrics,
                onValueChange = { transLyrics = it },
                label = { Text("中文翻译歌词 (逐行对应)") },
                modifier = Modifier.fillMaxWidth().height(160.dp),
                maxLines = 10
            )
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(zhTitle.ifBlank { null }, origLyrics, transLyrics)
                    }
                ) {
                    Text("保存本地修改")
                }
            }
        }
    }
}
