package com.linernotes.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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

                    // Top-level back handler: when in an album, pressing/swiping back returns to CD Shelf
                    BackHandler(enabled = selectedAlbumId != null) {
                        selectedAlbumId = null
                    }

                    AnimatedContent(
                        targetState = selectedAlbumId,
                        transitionSpec = {
                            if (targetState != null) {
                                // Push forward into Lyric Booklet: slides in from right
                                (slideInHorizontally(
                                    initialOffsetX = { fullWidth -> fullWidth },
                                    animationSpec = tween(300)
                                ) + fadeIn(animationSpec = tween(220))).togetherWith(
                                    slideOutHorizontally(
                                        targetOffsetX = { fullWidth -> -fullWidth / 4 },
                                        animationSpec = tween(300)
                                    ) + fadeOut(animationSpec = tween(180))
                                )
                            } else {
                                // Pop backward into CD Shelf: slides out to right
                                (slideInHorizontally(
                                    initialOffsetX = { fullWidth -> -fullWidth / 4 },
                                    animationSpec = tween(300)
                                ) + fadeIn(animationSpec = tween(220))).togetherWith(
                                    slideOutHorizontally(
                                        targetOffsetX = { fullWidth -> fullWidth },
                                        animationSpec = tween(300)
                                    ) + fadeOut(animationSpec = tween(180))
                                )
                            }
                        },
                        label = "screenNavigationAnimation"
                    ) { currentAlbumId ->
                        if (currentAlbumId != null) {
                            LyricBookletScreen(
                                albumId = currentAlbumId,
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
}
