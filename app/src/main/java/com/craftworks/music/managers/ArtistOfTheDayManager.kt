package com.craftworks.music.managers

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.craftworks.music.data.model.MediaData
import com.craftworks.music.data.repository.AlbumRepository
import com.craftworks.music.data.repository.ArtistRepository
import com.craftworks.music.data.repository.SongRepository
import com.craftworks.music.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

data class ArtistOfTheDayData(
    val artistName: String,
    val slideshowSongs: List<MediaItem>,
    val allArtistSongs: List<MediaItem>
)

@Singleton
class ArtistOfTheDayManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val artistRepository: ArtistRepository,
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository
) {
    companion object {
        private val AOTD_DATE_KEY = stringPreferencesKey("artist_of_the_day_date")
        private val AOTD_ARTIST_ID_KEY = stringPreferencesKey("artist_of_the_day_artist_id")
    }

    suspend fun getArtistOfTheDay(forceRefresh: Boolean = false): ArtistOfTheDayData? = coroutineScope {
        val today = LocalDate.now().toString()
        val (cachedDate, cachedArtistId) = context.dataStore.data.map { preferences ->
            (preferences[AOTD_DATE_KEY] ?: "") to (preferences[AOTD_ARTIST_ID_KEY] ?: "")
        }.firstOrNull() ?: ("" to "")

        val artists = artistRepository.getArtists(size = 500, ignoreCachedResponse = forceRefresh)

        var chosenArtist: MediaData.Artist? = null
        if (!forceRefresh && cachedDate == today && cachedArtistId.isNotEmpty() && artists.isNotEmpty()) {
            chosenArtist = artists.find { it.navidromeID == cachedArtistId || it.name == cachedArtistId }
        }

        if (chosenArtist == null && artists.isNotEmpty()) {
            val random = if (forceRefresh) Random(System.currentTimeMillis()) else Random(today.hashCode().toLong())
            val shuffled = artists.shuffled(random)

            for (artist in shuffled) {
                val songs = fetchSongsForArtist(artist)
                if (songs.isNotEmpty()) {
                    chosenArtist = artist
                    break
                }
            }
        }

        if (chosenArtist == null) {
            val allSongs = songRepository.getSongs(songCount = 100)
            if (allSongs.isEmpty()) return@coroutineScope null

            val songsByArtist = allSongs.groupBy { it.mediaMetadata.artist?.toString() ?: "Unknown Artist" }
            val artistNames = songsByArtist.keys.filter { it.isNotBlank() && it != "Unknown Artist" }.ifEmpty { songsByArtist.keys.toList() }
            val random = if (forceRefresh) Random(System.currentTimeMillis()) else Random(today.hashCode().toLong())
            val chosenName = artistNames.shuffled(random).firstOrNull() ?: return@coroutineScope null
            val songs = songsByArtist[chosenName] ?: emptyList()

            val slideshow = if (songs.size <= 10) songs else songs.shuffled(random).take(10)
            return@coroutineScope ArtistOfTheDayData(
                artistName = chosenName,
                slideshowSongs = slideshow,
                allArtistSongs = songs
            )
        }

        val allSongs = fetchSongsForArtist(chosenArtist)
        if (allSongs.isEmpty()) return@coroutineScope null

        val random = if (forceRefresh) Random(System.currentTimeMillis()) else Random(today.hashCode().toLong())
        val slideshow = if (allSongs.size <= 10) {
            allSongs
        } else {
            allSongs.shuffled(random).take(10)
        }

        val artistIdToCache = chosenArtist.navidromeID.ifEmpty { chosenArtist.name }
        context.dataStore.edit { preferences ->
            preferences[AOTD_DATE_KEY] = today
            preferences[AOTD_ARTIST_ID_KEY] = artistIdToCache
        }

        android.util.Log.d("ArtistOfTheDay", "Artist chosen: ${chosenArtist.name}, total songs: ${allSongs.size}, slideshow count: ${slideshow.size}")

        ArtistOfTheDayData(
            artistName = chosenArtist.name,
            slideshowSongs = slideshow,
            allArtistSongs = allSongs
        )
    }

    private suspend fun fetchSongsForArtist(artist: MediaData.Artist): List<MediaItem> = coroutineScope {
        val albums = artistRepository.getArtistAlbums(artist.navidromeID)
        android.util.Log.d("ArtistOfTheDay", "fetchSongsForArtist: artist=${artist.name}, albums count=${albums.size}")
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
        val tracks = songsDeferred.awaitAll().flatten()
        if (tracks.isNotEmpty()) {
            android.util.Log.d("ArtistOfTheDay", "fetchSongsForArtist: from albums got ${tracks.size} tracks")
            return@coroutineScope tracks
        }
        val searched = songRepository.searchSongs(artist.name)
        if (searched.isNotEmpty()) {
            val matching = searched.filter { it.mediaMetadata.artist?.toString().equals(artist.name, ignoreCase = true) }
            if (matching.isNotEmpty()) {
                android.util.Log.d("ArtistOfTheDay", "fetchSongsForArtist: from search got ${matching.size} tracks")
                return@coroutineScope matching
            }
        }
        emptyList()
    }
}
