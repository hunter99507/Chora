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
class SongRepository @Inject constructor(
    private val localDataSource: LocalDataSource,
    private val navidromeDataSource: NavidromeDataSource,
    private val embyJellyfinDataSource: EmbyJellyfinDataSource,
    private val localMusicStatsManager: com.craftworks.music.managers.LocalMusicStatsManager
) {

    suspend fun getSongs(
        query: String? = "",
        songCount: Int = 100, 
        songOffset: Int = 0,
        ignoreCachedResponse: Boolean = false,
        favoritesOnly: Boolean = false,
    ): List<MediaItem> = coroutineScope {
        val deferredSongs = mutableListOf<Deferred<List<MediaItem>>>()

        if (MediaSourceManager.isSourceActive(MediaSource.LOCAL) && LocalProviderManager.checkActiveFolders())
            if (query.isNullOrEmpty() && songOffset == 0)
                deferredSongs.add(async { localDataSource.getLocalSongs(ignoreCachedResponse) })

        if (MediaSourceManager.isSourceActive(MediaSource.NAVIDROME) && NavidromeManager.checkActiveServers())
            deferredSongs.add(async {
                navidromeDataSource.getNavidromeSongs(query, songCount, songOffset, ignoreCachedResponse, favoritesOnly = favoritesOnly)
            })

        if (MediaSourceManager.isSourceActive(MediaSource.EMBY) && EmbyJellyfinManager.checkActiveServers())
            deferredSongs.add(async {
                embyJellyfinDataSource.getSongs(query, songCount, songOffset, ignoreCachedResponse, favoritesOnly = favoritesOnly)
            })

        deferredSongs.awaitAll().flatten()
    }

    suspend fun setSongRating(
        songId: String, rating: Int = 0
    ) {
        when {
            songId.startsWith("Local") || songId.startsWith("content://") || songId.startsWith("file://") -> localMusicStatsManager.setRating(songId, rating)
            songId.startsWith("emby_") -> embyJellyfinDataSource.starItem(songId, rating > 0)
            else -> navidromeDataSource.setNavidromeRating(songId, rating)
        }
    }

    suspend fun getSong(songId: String, ignoreCachedResponse: Boolean = false): MediaItem? = coroutineScope {
        when {
            songId.startsWith("Local") || songId.startsWith("content://") || songId.startsWith("file://") -> localDataSource.getLocalSong(songId)
            songId.startsWith("emby_") -> embyJellyfinDataSource.getSong(songId, ignoreCachedResponse)
            else -> navidromeDataSource.getNavidromeSong(songId, ignoreCachedResponse)
        }
    }

    suspend fun getSimilarSongs(songId: String, count: Int) : List<MediaItem> = coroutineScope {
        when {
            songId.startsWith("Local") || songId.startsWith("content://") || songId.startsWith("file://") -> emptyList()
            songId.startsWith("emby_") -> embyJellyfinDataSource.getSimilarSongs(songId, count)
            else -> navidromeDataSource.getNavidromeSimilarSong(songId, count)
        }
    }

    suspend fun searchSongs(query: String, ignoreCachedResponse: Boolean = false): List<MediaItem> = coroutineScope {
        val deferredSongs = mutableListOf<Deferred<List<MediaItem>>>()

        if (MediaSourceManager.isSourceActive(MediaSource.LOCAL) && LocalProviderManager.checkActiveFolders())
            deferredSongs.add(async { localDataSource.searchLocalSongs(query) })

        if (MediaSourceManager.isSourceActive(MediaSource.NAVIDROME) && NavidromeManager.checkActiveServers())
            deferredSongs.add(async { navidromeDataSource.getNavidromeSongs(query, ignoreCachedResponse = ignoreCachedResponse) })

        if (MediaSourceManager.isSourceActive(MediaSource.EMBY) && EmbyJellyfinManager.checkActiveServers())
            deferredSongs.add(async { embyJellyfinDataSource.getSongs(query, ignoreCachedResponse = ignoreCachedResponse) })

        deferredSongs.awaitAll().flatten()
    }

    suspend fun scrobbleSong(songId: String, submission: Boolean) {
        when {
            songId.startsWith("Local") || songId.startsWith("content://") || songId.startsWith("file://") -> {
                if (submission) {
                    localMusicStatsManager.incrementPlayCount(songId)
                }
            }
            songId.startsWith("emby_") -> embyJellyfinDataSource.scrobbleSong(songId, submission)
            else -> navidromeDataSource.scrobbleSong(songId, submission)
        }
    }
}
