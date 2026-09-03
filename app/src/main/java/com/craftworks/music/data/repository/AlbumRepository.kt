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
class AlbumRepository @Inject constructor(
    private val localDataSource: LocalDataSource,
    private val navidromeDataSource: NavidromeDataSource,
    private val embyJellyfinDataSource: EmbyJellyfinDataSource
) {
    suspend fun getAlbums(
        sort: String? = "alphabeticalByName",
        size: Int? = 100,
        offset: Int? = 0,
        ignoreCachedResponse: Boolean = false,
        favoritesOnly: Boolean = false,
    ): List<MediaItem> = coroutineScope {
        val deferredAlbums = mutableListOf<Deferred<List<MediaItem>>>()

        if (MediaSourceManager.isSourceActive(MediaSource.NAVIDROME) && NavidromeManager.checkActiveServers())
            deferredAlbums.add(async { navidromeDataSource.getNavidromeAlbums(sort, size, offset, ignoreCachedResponse, favoritesOnly=favoritesOnly) })

        if (MediaSourceManager.isSourceActive(MediaSource.EMBY) && EmbyJellyfinManager.checkActiveServers())
            deferredAlbums.add(async { embyJellyfinDataSource.getAlbums(sort, size, offset, ignoreCachedResponse, favoritesOnly=favoritesOnly) })

        if (MediaSourceManager.isSourceActive(MediaSource.LOCAL) && LocalProviderManager.checkActiveFolders())
            if (offset == 0)
                deferredAlbums.add(async { localDataSource.getLocalAlbums(sort) })

        deferredAlbums.awaitAll().flatten()
    }

    suspend fun getAlbum(albumId: String, ignoreCachedResponse: Boolean = false): List<MediaItem>? = coroutineScope {
        when {
            albumId.startsWith("Local_") -> localDataSource.getLocalAlbum(albumId)
            albumId.startsWith("emby_") || albumId.startsWith("folder_album_emby_") -> embyJellyfinDataSource.getAlbum(albumId, ignoreCachedResponse)
            else -> navidromeDataSource.getNavidromeAlbum(albumId, ignoreCachedResponse)
        }
    }

    suspend fun searchAlbum(query: String): List<MediaItem> = coroutineScope {
        val deferredAlbums = mutableListOf<Deferred<List<MediaItem>>>()

        if (MediaSourceManager.isSourceActive(MediaSource.LOCAL) && LocalProviderManager.checkActiveFolders())
            deferredAlbums.add(async { localDataSource.searchLocalAlbums(query) })

        if (MediaSourceManager.isSourceActive(MediaSource.NAVIDROME) && NavidromeManager.checkActiveServers())
            deferredAlbums.add(async { navidromeDataSource.searchNavidromeAlbums(query) })

        if (MediaSourceManager.isSourceActive(MediaSource.EMBY) && EmbyJellyfinManager.checkActiveServers())
            deferredAlbums.add(async { embyJellyfinDataSource.getAlbums(sort = "alphabeticalByName", size = 100, offset = 0, query = query) })

        deferredAlbums.awaitAll().flatten()
    }
}