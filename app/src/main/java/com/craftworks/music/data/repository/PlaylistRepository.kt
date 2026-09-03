package com.craftworks.music.data.repository

import androidx.media3.common.MediaItem
import com.craftworks.music.data.datasource.emby.EmbyJellyfinDataSource
import com.craftworks.music.data.datasource.local.LocalDataSource
import com.craftworks.music.data.datasource.navidrome.NavidromeDataSource
import com.craftworks.music.data.model.toMediaItem
import com.craftworks.music.managers.EmbyJellyfinManager
import com.craftworks.music.managers.LocalProviderManager
import com.craftworks.music.managers.MediaSource
import com.craftworks.music.managers.MediaSourceManager
import com.craftworks.music.managers.NavidromeManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val localDataSource: LocalDataSource,
    private val navidromeDataSource: NavidromeDataSource,
    private val embyJellyfinDataSource: EmbyJellyfinDataSource
) {

    suspend fun getPlaylists(ignoreCachedResponse: Boolean = false): List<MediaItem> = coroutineScope {
        val deferredPlaylists = mutableListOf<Deferred<List<MediaItem>>>()

        if (MediaSourceManager.isSourceActive(MediaSource.NAVIDROME) && NavidromeManager.checkActiveServers())
            deferredPlaylists.add(async { navidromeDataSource.getNavidromePlaylists(ignoreCachedResponse) })

        if (MediaSourceManager.isSourceActive(MediaSource.EMBY) && EmbyJellyfinManager.checkActiveServers())
            deferredPlaylists.add(async { embyJellyfinDataSource.getPlaylists().map { it.toMediaItem() } })

        if (MediaSourceManager.isSourceActive(MediaSource.LOCAL) && LocalProviderManager.checkActiveFolders())
            deferredPlaylists.add(async { localDataSource.getLocalPlaylists() })

        val allItems = deferredPlaylists.awaitAll().flatten()
        com.craftworks.music.managers.FileLogger.log("REPO_PLAYLISTS", "Raw playlists count: ${allItems.size}, titles: ${allItems.map { "${it.mediaId}->${it.mediaMetadata.title}" }}")

        val deduplicated = allItems.distinctBy { 
            it.mediaMetadata.title?.toString()?.trim()?.lowercase() ?: it.mediaId 
        }
        com.craftworks.music.managers.FileLogger.log("REPO_PLAYLISTS", "Deduplicated count: ${deduplicated.size}, titles: ${deduplicated.map { "${it.mediaId}->${it.mediaMetadata.title}" }}")

        deduplicated
    }

    suspend fun getPlaylistSongs(playlistId: String, ignoreCachedResponse: Boolean = false): List<MediaItem> = coroutineScope {
        when {
            playlistId.startsWith("Local_") -> localDataSource.getLocalPlaylistSongs(playlistId)
            playlistId.startsWith("emby_") -> embyJellyfinDataSource.getPlaylist(playlistId)
            else -> navidromeDataSource.getNavidromePlaylist(playlistId, ignoreCachedResponse) ?: emptyList()
        }
    }

    suspend fun createPlaylist(name: String, songsToAdd: String, addToNavidrome: Boolean) {
        if (NavidromeManager.checkActiveServers() && addToNavidrome) {
            navidromeDataSource.createNavidromePlaylist(name, listOf(songsToAdd), true)
        }
        else {
            localDataSource.createLocalPlaylist(name, songsToAdd)
        }
    }

    suspend fun addSongToPlaylist(playlistId: String, songID: String) {
        if (playlistId.startsWith("Local_")){
            localDataSource.addSongToLocalPlaylist(playlistId, songID)
        } else {
            navidromeDataSource.addSongToNavidromePlaylist(playlistId, songID, true)
        }
    }
    suspend fun removeSongFromPlaylist(playlistId: String, songIndex: Int) {
        if (playlistId.startsWith("Local_")){
            localDataSource.removeSongFromLocalPlaylist(playlistId, songIndex)
        } else {
            navidromeDataSource.removeSongFromNavidromePlaylist(playlistId, songIndex, true)
        }
    }

    suspend fun deletePlaylist(playlistId: String) {
        if (playlistId.startsWith("Local_")){
            localDataSource.deleteLocalPlaylist(playlistId)
        } else {
            navidromeDataSource.deleteNavidromePlaylist(playlistId, true)
        }
    }
}
