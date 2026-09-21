package com.linernotes.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.core.i18n.resolveAppStrings
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.presentation.booklet.LyricBookletScreen
import com.linernotes.app.presentation.shelf.CdShelfScreen
import com.linernotes.app.presentation.theme.LinerNotesTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var aiPreferences: AiPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appLanguageCode by aiPreferences.appLanguageFlow.collectAsState()
            val strings = remember(appLanguageCode) {
                resolveAppStrings(appLanguageCode)
            }

            CompositionLocalProvider(LocalStrings provides strings) {
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
}
