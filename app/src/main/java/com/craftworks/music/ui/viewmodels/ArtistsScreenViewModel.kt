package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.craftworks.music.data.model.MediaData
import com.craftworks.music.data.repository.AlbumRepository
import com.craftworks.music.data.repository.ArtistRepository
import com.craftworks.music.data.repository.SongRepository
import com.craftworks.music.managers.DataRefreshManager
import com.craftworks.music.managers.settings.LocalDataSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistsScreenViewModel @Inject constructor(
    private val artistRepository: ArtistRepository,
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val localDataSettingsManager: LocalDataSettingsManager
) : ViewModel() {
    private val _allArtists = MutableStateFlow<List<MediaData.Artist>>(emptyList())
    val allArtists: StateFlow<List<MediaData.Artist>> = _allArtists.asStateFlow()

    private val _selectedArtist = MutableStateFlow<MediaData.Artist?>(null)
    val selectedArtist: StateFlow<MediaData.Artist?> = _selectedArtist

    private val _artistAlbums = MutableStateFlow<List<MediaItem>>(emptyList())
    val artistAlbums: StateFlow<List<MediaItem>> = _artistAlbums.asStateFlow()

    private val _artistSongs = MutableStateFlow<List<MediaItem>>(emptyList())
    val artistSongs: StateFlow<List<MediaItem>> = _artistSongs.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly: StateFlow<Boolean> = _showFavoritesOnly.asStateFlow()

    init {
        getArtists()
        viewModelScope.launch {
            localDataSettingsManager.showFavoriteOnly.collect { showFavorites ->
                _showFavoritesOnly.value = showFavorites
                getArtists()
            }
        }
        viewModelScope.launch {
            DataRefreshManager.dataSourceChangedEvent.collect {
                getArtists()
            }
        }
        viewModelScope.launch {
            com.craftworks.music.managers.MediaSourceManager.selectedSource.collect {
                getArtists()
            }
        }
    }

    fun getArtists() {
        viewModelScope.launch {
            _isLoading.value = true
            _allArtists.value = artistRepository.getArtists(ignoreCachedResponse = true, favoritesOnly = _showFavoritesOnly.value)
            _isLoading.value = false
        }
    }

    suspend fun getAlbum(id: String): List<MediaItem> {
        return albumRepository.getAlbum(id) ?: emptyList()
    }

    suspend fun getArtistAlbums(artistId: String): List<MediaItem> {
        return artistRepository.getArtistAlbums(artistId)
    }

//    suspend fun search(query: String) {
//        _allArtists.value = artistRepository.searchArtists(query)
//    }
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    @OptIn(FlowPreview::class)
    val searchResults: StateFlow<List<MediaData.Artist>> = searchQuery
        .debounce(300L) // Adds a small delay to avoid searching on every keystroke.
        .combine(allArtists) { query, artists ->
            if (query.isBlank()) {
                emptyList()
            } else {
                artists.filter { artist ->
                    artist.name.contains(query, ignoreCase = true)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )


    fun setSelectedArtist(artist: MediaData.Artist) {
        _selectedArtist.value = artist
        _artistSongs.value = emptyList()
        viewModelScope.launch {
            val loadingJob = launch {
                delay(1000)
                if (_artistAlbums.value.isEmpty()) {
                    _isLoading.value = true
                }
            }
            loadingJob.start()
            coroutineScope {
                val artistAlbumsAsync = async { artistRepository.getArtistAlbums(artist.navidromeID) }
                val albums = artistAlbumsAsync.await()
                _artistAlbums.value = albums

                // Concurrently fetch songs for all albums
                val songsDeferred = albums.map { albumItem ->
                    async {
                        val albumId = albumItem.mediaMetadata.extras?.getString("navidromeID") ?: albumItem.mediaId
                        val albumTracks = albumRepository.getAlbum(albumId) ?: emptyList()
                        if (albumTracks.size > 1 && albumTracks[0].mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_ALBUM) {
                            albumTracks.subList(1, albumTracks.size)
                        } else {
                            albumTracks
                        }
                    }
                }
                _artistSongs.value = songsDeferred.awaitAll().flatten()

                val artistDetails = async { artistRepository.getArtistInfo(artist.navidromeID) }.await()
                _selectedArtist.value = _selectedArtist.value?.copy(
                    description = artistDetails?.biography ?: "",
                    musicBrainzId = artistDetails?.musicBrainzId,
                    similarArtist = artistDetails?.similarArtist
                )
            }

            loadingJob.cancel()
            _isLoading.value = false
        }
    }

    fun selectArtistByName(artistName: String, fallbackId: String = "") {
        viewModelScope.launch {
            val existing = _allArtists.value.firstOrNull {
                (fallbackId.isNotBlank() && it.navidromeID == fallbackId) ||
                it.name.equals(artistName, ignoreCase = true)
            }
            if (existing != null) {
                setSelectedArtist(existing)
                return@launch
            }
            val fetchedArtists = artistRepository.getArtists(ignoreCachedResponse = false)
            val found = fetchedArtists.firstOrNull {
                (fallbackId.isNotBlank() && it.navidromeID == fallbackId) ||
                it.name.equals(artistName, ignoreCase = true)
            }
            if (found != null) {
                setSelectedArtist(found)
            } else {
                setSelectedArtist(
                    MediaData.Artist(
                        navidromeID = fallbackId,
                        name = artistName
                    )
                )
            }
        }
    }

    fun setShowFavoritesOnly(showFavorites: Boolean) {
        viewModelScope.launch {
            localDataSettingsManager.saveShowFavoriteOnly(showFavorites)
        }
    }

    fun setSongRating(navidromeId: String, rating: Int) {
        viewModelScope.launch {
            songRepository.setSongRating(navidromeId, rating)
        }
    }
}