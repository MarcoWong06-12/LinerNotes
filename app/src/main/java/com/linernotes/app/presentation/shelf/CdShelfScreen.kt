package com.linernotes.app.presentation.shelf

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.isSearchActive) {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChange(it) },
                            placeholder = { Text("搜索唱片名、译名、艺术家...") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "LinerNotes 唱片架",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                actions = {
                    if (state.isSearchActive) {
                        IconButton(onClick = { viewModel.setSearchActive(false) }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭搜索")
                        }
                    } else {
                        IconButton(onClick = { viewModel.setSearchActive(true) }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索唱片")
                        }
                    }

                    IconButton(onClick = { viewModel.setAddSheetOpen(true) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "入库新唱片",
                            tint = MaterialTheme.colorScheme.primary
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
}
