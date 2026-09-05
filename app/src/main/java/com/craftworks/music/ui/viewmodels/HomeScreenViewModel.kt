package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.craftworks.music.data.repository.AlbumRepository
import com.craftworks.music.managers.DataRefreshManager
import com.craftworks.music.managers.EmbyJellyfinManager
import com.craftworks.music.managers.NavidromeManager
import com.craftworks.music.data.repository.PlaylistRepository
import com.craftworks.music.managers.ArtistOfTheDayData
import com.craftworks.music.managers.ArtistOfTheDayManager
import com.craftworks.music.managers.SongOfTheDayManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeScreenViewModel @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val playlistRepository: PlaylistRepository,
    private val songOfTheDayManager: SongOfTheDayManager,
    private val artistOfTheDayManager: ArtistOfTheDayManager
) : ViewModel() {
    private val _artistOfTheDay = MutableStateFlow<ArtistOfTheDayData?>(null)
    val artistOfTheDay: StateFlow<ArtistOfTheDayData?> = _artistOfTheDay.asStateFlow()

    private val _songOfTheDay = MutableStateFlow<MediaItem?>(null)
    val songOfTheDay: StateFlow<MediaItem?> = _songOfTheDay.asStateFlow()

    private val _playlists = MutableStateFlow<List<MediaItem>>(emptyList())
    val playlists: StateFlow<List<MediaItem>> = _playlists.asStateFlow()

    private val _recentlyPlayedAlbums = MutableStateFlow<List<MediaItem>>(emptyList())
    val recentlyPlayedAlbums: StateFlow<List<MediaItem>> = _recentlyPlayedAlbums.asStateFlow()

    private val _recentAlbums = MutableStateFlow<List<MediaItem>>(emptyList())
    val recentAlbums: StateFlow<List<MediaItem>> = _recentAlbums.asStateFlow()

    private val _mostPlayedAlbums = MutableStateFlow<List<MediaItem>>(emptyList())
    val mostPlayedAlbums: StateFlow<List<MediaItem>> = _mostPlayedAlbums.asStateFlow()

    private val _shuffledAlbums = MutableStateFlow<List<MediaItem>>(emptyList())
    val shuffledAlbums: StateFlow<List<MediaItem>> = _shuffledAlbums.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadHomeScreenData()

        viewModelScope.launch {
            DataRefreshManager.dataSourceChangedEvent.collect {
                loadHomeScreenData(forceRefresh = true)
            }
        }

        viewModelScope.launch {
            combine(
                NavidromeManager.currentServerId,
                NavidromeManager.libraries
            ) { serverId, libs -> serverId to libs }
                .distinctUntilChanged()
                .collect { (serverId, libs) ->
                    if (serverId != null && libs.isNotEmpty()) {
                        loadHomeScreenData()
                    }
                }
        }

        viewModelScope.launch {
            combine(
                EmbyJellyfinManager.currentServerId,
                EmbyJellyfinManager.libraries
            ) { serverId, libs -> serverId to libs }
                .distinctUntilChanged()
                .collect { (serverId, libs) ->
                    if (serverId != null && libs.isNotEmpty()) {
                        loadHomeScreenData()
                    }
                }
        }
    }

    fun loadHomeScreenData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            supervisorScope {
                launch {
                    try {
                        val data = playlistRepository.getPlaylists(forceRefresh)
                        _playlists.value = data
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreenVM", "Failed to load playlists", e)
                    }
                }
                launch {
                    try {
                        val data = albumRepository.getAlbums("recent", 20, 0, forceRefresh)
                        _recentlyPlayedAlbums.value = data
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreenVM", "Failed to load recently played", e)
                    }
                }
                launch {
                    try {
                        val data = albumRepository.getAlbums("newest", 20, 0, forceRefresh)
                        _recentAlbums.value = data
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreenVM", "Failed to load recently added", e)
                    }
                }
                launch {
                    try {
                        val data = albumRepository.getAlbums("frequent", 20, 0, forceRefresh)
                        _mostPlayedAlbums.value = data
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreenVM", "Failed to load most played", e)
                    }
                }
                launch {
                    try {
                        val data = albumRepository.getAlbums("random", 20, 0, forceRefresh)
                        _shuffledAlbums.value = data
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreenVM", "Failed to load random albums", e)
                    }
                }
                launch {
                    try {
                        val sotd = songOfTheDayManager.getSongOfTheDay(forceRefresh)
                        _songOfTheDay.value = sotd
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreenVM", "Failed to load song of the day", e)
                    }
                }
                launch {
                    try {
                        val aotd = artistOfTheDayManager.getArtistOfTheDay(forceRefresh)
                        _artistOfTheDay.value = aotd
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreenVM", "Failed to load artist of the day", e)
                    }
                }
            }
            _isLoading.value = false
        }
    }

    suspend fun getAlbumSongs(albumId: String): List<MediaItem> {
        return albumRepository.getAlbum(albumId) ?: emptyList()
    }
}