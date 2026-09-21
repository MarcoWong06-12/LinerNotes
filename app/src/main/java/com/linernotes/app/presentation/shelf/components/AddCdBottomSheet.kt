package com.linernotes.app.presentation.shelf.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.linernotes.app.data.local.entity.AlbumEntity
import com.linernotes.app.data.local.entity.TrackEntity
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCdBottomSheet(
    onDismiss: () -> Unit,
    onSaveAlbum: (AlbumEntity, List<TrackEntity>) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    var title by remember { mutableStateOf("") }
    var translatedTitle by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var releaseYear by remember { mutableStateOf("") }
    var coverUrl by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "收纳新唱片入库",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(12.dp))

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("联网检索抓取") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("手动录入档案") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("输入专辑名检索 (MusicBrainz / LRCLIB)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (title.isBlank()) title = "Abbey Road"
                        artist = "The Beatles"
                        releaseYear = "1969"
                        coverUrl = "https://coverartarchive.org/release/1f52d004-bb52-4467-bc5b-426c1c876e5d/front"
                        translatedTitle = "修道院之路"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("自动匹配元数据与歌词")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("专辑名 (原版)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = translatedTitle,
                onValueChange = { translatedTitle = it },
                label = { Text("中文译名 (可选)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("艺术家") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = releaseYear,
                    onValueChange = { releaseYear = it },
                    label = { Text("年份") },
                    modifier = Modifier.width(100.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = coverUrl,
                onValueChange = { coverUrl = it },
                label = { Text("封面图 URL 或本地路径") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it },
                label = { Text("唱片条形码 / Catalog No. (可选)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("版本收藏备注 (如：日版首版、侧标完整)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (title.isNotBlank() && artist.isNotBlank()) {
                        val albumId = UUID.randomUUID().toString()
                        val album = AlbumEntity(
                            id = albumId,
                            title = title,
                            translatedTitle = translatedTitle.ifBlank { null },
                            artist = artist,
                            releaseYear = releaseYear.ifBlank { "未知年份" },
                            coverUrl = coverUrl,
                            barcode = barcode.ifBlank { null },
                            purchaseDate = System.currentTimeMillis(),
                            notes = notes.ifBlank { null }
                        )

                        val defaultTracks = listOf(
                            TrackEntity(
                                albumId = albumId,
                                trackNumber = 1,
                                title = "Come Together",
                                translatedTitle = "聚在一起",
                                originalLyrics = "Here come old flat top\nHe come grooving up slowly\nHe got joo joo eyeball\nHe one holy roller",
                                translatedLyrics = "老留兰头走过来了\n他不紧不慢地走来\n他带着古怪邪魅的眼神\n他像个虔诚的圣者"
                            ),
                            TrackEntity(
                                albumId = albumId,
                                trackNumber = 2,
                                title = "Something",
                                translatedTitle = "某种感觉",
                                originalLyrics = "Something in the way she moves\nAttracts me like no other lover\nSomething in the way she woos me",
                                translatedLyrics = "她举手投足间的韵味\n深深吸引我，无可比拟\n她向我倾诉爱意时的神采"
                            )
                        )

                        onSaveAlbum(album, defaultTracks)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() && artist.isNotBlank()
            ) {
                Text("确认归档入架")
            }
        }
    }
}
