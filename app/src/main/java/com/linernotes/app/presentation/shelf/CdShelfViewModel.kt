package com.linernotes.app.presentation.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.data.local.entity.AlbumEntity
import com.linernotes.app.data.local.entity.TrackEntity
import com.linernotes.app.domain.repository.AlbumRepository
import com.linernotes.app.presentation.shelf.model.ShelfUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CdShelfViewModel @Inject constructor(
    private val repository: AlbumRepository,
    val aiPreferences: AiPreferences
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)
    private val _isAddSheetOpen = MutableStateFlow(false)
    private val _isSettingsOpen = MutableStateFlow(false)

    val uiState: StateFlow<ShelfUiState> = combine(
        _searchQuery.flatMapLatest { query ->
            if (query.isBlank()) repository.getCollectionStream()
            else repository.searchCollectionStream(query)
        },
        _searchQuery,
        _isSearchActive,
        _isAddSheetOpen,
        _isSettingsOpen
    ) { albums, query, isSearchActive, isAddSheetOpen, isSettingsOpen ->
        ShelfUiState(
            albums = albums,
            searchQuery = query,
            isSearchActive = isSearchActive,
            isAddSheetOpen = isAddSheetOpen,
            isSettingsOpen = isSettingsOpen,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShelfUiState())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
        if (!active) _searchQuery.value = ""
    }

    fun setAddSheetOpen(open: Boolean) {
        _isAddSheetOpen.value = open
    }

    fun setSettingsOpen(open: Boolean) {
        _isSettingsOpen.value = open
    }

    fun saveNewAlbum(album: AlbumEntity, tracks: List<TrackEntity>) {
        viewModelScope.launch {
            repository.saveAlbum(album, tracks)
            _isAddSheetOpen.value = false
        }
    }

    fun removeAlbum(albumId: String) {
        viewModelScope.launch {
            repository.removeAlbumFromShelf(albumId)
        }
    }
}
