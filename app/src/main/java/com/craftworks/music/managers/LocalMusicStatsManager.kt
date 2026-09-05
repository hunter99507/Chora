package com.craftworks.music.managers

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMusicStatsManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val PREF_NAME = "LocalMusicStatsPrefs"
        private const val PREFIX_PLAY_COUNT = "play_count_"
        private const val PREFIX_LAST_PLAYED = "last_played_"
        private const val PREFIX_RATING = "rating_"
        private const val KEY_STARRED_IDS = "starred_song_ids"

        fun cleanId(rawId: String): String {
            val trimmed = rawId.trim()
            return when {
                trimmed.startsWith("content://") -> trimmed.substringAfterLast("/")
                trimmed.startsWith("Local_") -> trimmed.removePrefix("Local_")
                trimmed.startsWith("file://") -> trimmed.removePrefix("file://")
                else -> trimmed
            }
        }
    }

    @Synchronized
    fun incrementPlayCount(rawId: String): Int {
        val id = cleanId(rawId)
        if (id.isBlank()) return 0
        val current = prefs.getInt("$PREFIX_PLAY_COUNT$id", 0)
        val updated = current + 1
        prefs.edit {
            putInt("$PREFIX_PLAY_COUNT$id", updated)
            putLong("$PREFIX_LAST_PLAYED$id", System.currentTimeMillis())
        }
        return updated
    }

    fun getPlayCount(rawId: String): Int {
        val id = cleanId(rawId)
        if (id.isBlank()) return 0
        return prefs.getInt("$PREFIX_PLAY_COUNT$id", 0)
    }

    fun getLastPlayed(rawId: String): Long {
        val id = cleanId(rawId)
        if (id.isBlank()) return 0L
        return prefs.getLong("$PREFIX_LAST_PLAYED$id", 0L)
    }

    @Synchronized
    fun setRating(rawId: String, rating: Int) {
        val id = cleanId(rawId)
        if (id.isBlank()) return
        prefs.edit {
            if (rating > 0) {
                putInt("$PREFIX_RATING$id", rating)
            } else {
                remove("$PREFIX_RATING$id")
            }
        }
    }

    fun getRating(rawId: String): Int {
        val id = cleanId(rawId)
        if (id.isBlank()) return 0
        return prefs.getInt("$PREFIX_RATING$id", 0)
    }

    @Synchronized
    fun setStarred(rawId: String, starred: Boolean): Boolean {
        val id = cleanId(rawId)
        if (id.isBlank()) return false
        val currentSet = prefs.getStringSet(KEY_STARRED_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val changed = if (starred) {
            currentSet.add(id)
        } else {
            currentSet.remove(id)
        }
        prefs.edit {
            putStringSet(KEY_STARRED_IDS, currentSet)
        }
        return changed
    }

    fun isStarred(rawId: String): Boolean {
        val id = cleanId(rawId)
        if (id.isBlank()) return false
        val currentSet = prefs.getStringSet(KEY_STARRED_IDS, emptySet()) ?: return false
        return currentSet.contains(id)
    }

    fun getStarredIds(): Set<String> {
        return prefs.getStringSet(KEY_STARRED_IDS, emptySet()) ?: emptySet()
    }
}
