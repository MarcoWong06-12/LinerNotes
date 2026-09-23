package com.linernotes.app.presentation.shelf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linernotes.app.presentation.common.AiConfigDialog
import com.linernotes.app.presentation.common.BouncyIconButton
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
                    if (state.isSearchActive) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            TextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.onSearchQueryChange(it) },
                                placeholder = { Text(strings.searchPlaceholder, style = MaterialTheme.typography.bodyMedium) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Text(
                            text = strings.shelfTitle,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                actions = {
                    if (state.isSearchActive) {
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

                    Spacer(modifier = Modifier.width(6.dp))

                    BouncyIconButton(
                        onClick = { viewModel.setAiConfigOpen(true) },
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

    if (state.isAiConfigOpen) {
        AiConfigDialog(
            aiPreferences = viewModel.aiPreferences,
            onTestConnection = { k, b, m -> viewModel.testAiConnection(k, b, m) },
            onDismiss = { viewModel.setAiConfigOpen(false) },
            onSaved = {
                viewModel.setAiConfigOpen(false)
            }
        )
    }
}
