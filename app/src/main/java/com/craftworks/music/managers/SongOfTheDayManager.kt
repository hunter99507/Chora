package com.craftworks.music.managers

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.MediaItem
import com.craftworks.music.data.repository.SongRepository
import com.craftworks.music.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class SongOfTheDayManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository
) {
    companion object {
        private val SOTD_DATE_KEY = stringPreferencesKey("song_of_the_day_date")
        private val SOTD_MEDIA_ID_KEY = stringPreferencesKey("song_of_the_day_media_id")
    }

    suspend fun getSongOfTheDay(forceRefresh: Boolean = false): MediaItem? {
        val today = LocalDate.now().toString()
        val (cachedDate, cachedMediaId) = context.dataStore.data.map { preferences ->
            (preferences[SOTD_DATE_KEY] ?: "") to (preferences[SOTD_MEDIA_ID_KEY] ?: "")
        }.firstOrNull() ?: ("" to "")

        val songs = songRepository.getSongs(songCount = 100)
        if (songs.isEmpty()) return null

        if (!forceRefresh && cachedDate == today && cachedMediaId.isNotEmpty()) {
            val found = songs.find { it.mediaId == cachedMediaId }
            if (found != null) {
                return found
            }
        }

        val index = if (forceRefresh) {
            Random.nextInt(songs.size)
        } else {
            val seed = today.hashCode().toLong()
            Random(seed).nextInt(songs.size)
        }
        val chosenSong = songs[index]

        context.dataStore.edit { preferences ->
            preferences[SOTD_DATE_KEY] = today
            preferences[SOTD_MEDIA_ID_KEY] = chosenSong.mediaId
        }

        return chosenSong
    }
}
