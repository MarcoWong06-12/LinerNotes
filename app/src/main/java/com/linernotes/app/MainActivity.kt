package com.linernotes.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.linernotes.app.presentation.booklet.LyricBookletScreen
import com.linernotes.app.presentation.shelf.CdShelfScreen
import com.linernotes.app.presentation.theme.LinerNotesTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LinerNotesTheme {
                var selectedAlbumId by remember { mutableStateOf<String?>(null) }

                if (selectedAlbumId != null) {
                    LyricBookletScreen(
                        albumId = selectedAlbumId!!,
                        onNavigateBack = { selectedAlbumId = null }
                    )
                } else {
                    CdShelfScreen(
                        onNavigateToBooklet = { albumId ->
                            selectedAlbumId = albumId
                        }
                    )
                }
            }
        }
    }
}
