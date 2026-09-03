package com.craftworks.music.data.repository

import androidx.media3.common.MediaItem
import com.craftworks.music.data.datasource.emby.EmbyJellyfinDataSource
import com.craftworks.music.data.datasource.local.LocalDataSource
import com.craftworks.music.data.datasource.navidrome.NavidromeDataSource
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
class StarredRepository @Inject constructor(
    private val localDataSource: LocalDataSource,
    private val navidromeDataSource: NavidromeDataSource,
    private val embyJellyfinDataSource: EmbyJellyfinDataSource
) {

    suspend fun getStarredItems(ignoreCachedResponse: Boolean = false): List<MediaItem> = coroutineScope {
        val deferredStarred = mutableListOf<Deferred<List<MediaItem>>>()

        if (MediaSourceManager.isSourceActive(MediaSource.NAVIDROME) && NavidromeManager.checkActiveServers())
            deferredStarred.add(async { navidromeDataSource.getNavidromeStarred(ignoreCachedResponse) })

        if (MediaSourceManager.isSourceActive(MediaSource.EMBY) && EmbyJellyfinManager.checkActiveServers()) {
            deferredStarred.add(async {
                val starredSongs = embyJellyfinDataSource.getStarredSongs()
                val starredAlbums = embyJellyfinDataSource.getStarredAlbums()
                starredSongs + starredAlbums
            })
        }

        if (MediaSourceManager.isSourceActive(MediaSource.LOCAL) && LocalProviderManager.checkActiveFolders())
            deferredStarred.add(async { localDataSource.getLocalStarredItems() })

        deferredStarred.awaitAll().flatten()
    }

     suspend fun starItem(id: String = "", albumId: String = "", artistId: String = "", ignoreCachedResponse: Boolean) {
         val targetId = if (id.isNotBlank()) id else if (albumId.isNotBlank()) albumId else artistId
         when {
             targetId.startsWith("Local_") -> localDataSource.starLocalItem(id)
             targetId.startsWith("emby_") || targetId.startsWith("folder_album_emby_") -> embyJellyfinDataSource.starItem(targetId, true)
             else -> navidromeDataSource.starNavidromeItem(id, albumId, artistId, ignoreCachedResponse)
         }
     }

     suspend fun unStarItem(id: String = "", albumId: String = "", artistId: String = "", ignoreCachedResponse: Boolean) {
         val targetId = if (id.isNotBlank()) id else if (albumId.isNotBlank()) albumId else artistId
         when {
             targetId.startsWith("Local_") -> localDataSource.unstarLocalItem(id)
             targetId.startsWith("emby_") || targetId.startsWith("folder_album_emby_") -> embyJellyfinDataSource.starItem(targetId, false)
             else -> navidromeDataSource.unstarNavidromeItem(id, albumId, artistId, ignoreCachedResponse)
         }
     }
}
