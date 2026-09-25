package com.linernotes.app.presentation.shelf

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linernotes.app.presentation.common.BouncyIconButton
import com.linernotes.app.presentation.common.SettingsDialog
import com.linernotes.app.presentation.shelf.components.AddCdBottomSheet
import com.linernotes.app.presentation.shelf.components.CdCard
import com.linernotes.app.presentation.shelf.components.EmptyShelfState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CdShelfScreen(
    onNavigateToBooklet: (albumId: String) -> Unit,
    viewModel: CdShelfViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val strings = com.linernotes.app.core.i18n.LocalStrings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = state.isSearchActive,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220, delayMillis = 40)) +
                                scaleIn(initialScale = 0.94f, animationSpec = tween(220)))
                                .togetherWith(fadeOut(animationSpec = tween(160)))
                        },
                        label = "shelfTopBarAnimation"
                    ) { isSearching ->
                        if (isSearching) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        CircleShape
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                                    .padding(horizontal = 14.dp)
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                BasicTextField(
                                    value = state.searchQuery,
                                    onValueChange = { viewModel.onSearchQueryChange(it) },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { innerTextField ->
                                        Box(
                                            contentAlignment = Alignment.CenterStart,
                                            modifier = Modifier.fillMaxHeight()
                                        ) {
                                            if (state.searchQuery.isEmpty()) {
                                                Text(
                                                    strings.searchPlaceholder,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    maxLines = 1
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                                if (state.searchQuery.isNotEmpty()) {
                                    BouncyIconButton(
                                        onClick = { viewModel.onSearchQueryChange("") },
                                        shape = CircleShape,
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = Color.Transparent,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = strings.shelfTitle,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                },
                actions = {
                    AnimatedContent(
                        targetState = state.isSearchActive,
                        transitionSpec = {
                            fadeIn(tween(180)).togetherWith(fadeOut(tween(120)))
                        },
                        label = "searchActionButtonAnimation"
                    ) { isSearching ->
                        if (isSearching) {
                            BouncyIconButton(
                                onClick = { viewModel.setSearchActive(false) },
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = strings.closeSearch, modifier = Modifier.size(20.dp))
                            }
                        } else {
                            BouncyIconButton(
                                onClick = { viewModel.setSearchActive(true) },
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = strings.searchCd, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    BouncyIconButton(
                        onClick = { viewModel.setSettingsOpen(true) },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = strings.settingsTitle,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    BouncyIconButton(
                        onClick = { viewModel.setAddSheetOpen(true) },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.padding(end = 8.dp).size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = strings.addAlbumTooltip,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = Color(0xFF121214)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.albums.isEmpty() && !state.isLoading) {
                EmptyShelfState()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.albums, key = { it.id }) { album ->
                        CdCard(
                            album = album,
                            onClick = { onNavigateToBooklet(album.id) },
                            onViewBooklet = { onNavigateToBooklet(album.id) },
                            onRemove = { viewModel.removeAlbum(album.id) }
                        )
                    }
                }
            }
        }
    }

    if (state.isAddSheetOpen) {
        AddCdBottomSheet(
            onDismiss = { viewModel.setAddSheetOpen(false) },
            onSaveAlbum = { album, tracks ->
                viewModel.saveNewAlbum(album, tracks)
            }
        )
    }

    if (state.isSettingsOpen) {
        SettingsDialog(
            aiPreferences = viewModel.aiPreferences,
            onDismiss = { viewModel.setSettingsOpen(false) },
            onSaved = {
                viewModel.setSettingsOpen(false)
            }
        )
    }
}
