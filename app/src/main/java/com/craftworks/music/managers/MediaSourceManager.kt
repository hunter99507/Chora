package com.craftworks.music.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.content.edit
import com.craftworks.music.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MediaSource(val id: String, val displayName: String, @DrawableRes val iconRes: Int) {
    ALL("all", "All Sources", R.drawable.round_music_note_24),
    LOCAL("local", "Local", R.drawable.s_m_local_filled),
    NAVIDROME("navidrome", "Navidrome", R.drawable.s_m_navidrome),
    EMBY("emby", "Emby / Jellyfin", R.drawable.s_m_emby);

    companion object {
        fun fromId(id: String?): MediaSource {
            return entries.find { it.id == id } ?: ALL
        }
    }
}

object MediaSourceManager {
    private const val PREF_NAME = "MediaSourcePrefs"
    private const val PREF_DEFAULT_SOURCE = "default_media_source"

    private lateinit var sharedPreferences: SharedPreferences

    private val _selectedSource = MutableStateFlow(MediaSource.ALL)
    val selectedSource: StateFlow<MediaSource> = _selectedSource.asStateFlow()

    private val _defaultSource = MutableStateFlow(MediaSource.ALL)
    val defaultSource: StateFlow<MediaSource> = _defaultSource.asStateFlow()

    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedDefaultId = sharedPreferences.getString(PREF_DEFAULT_SOURCE, MediaSource.ALL.id)
        val loadedDefault = MediaSource.fromId(savedDefaultId)
        _defaultSource.value = loadedDefault

        // On startup, the default source is always selected and shown
        _selectedSource.value = loadedDefault

        // If built with a dedicated flavor source (Sonora, Lyra, Aria), lock to it
        when (com.craftworks.music.BuildConfig.DEDICATED_SOURCE) {
            "LOCAL" -> {
                _defaultSource.value = MediaSource.LOCAL
                _selectedSource.value = MediaSource.LOCAL
            }
            "NAVIDROME" -> {
                _defaultSource.value = MediaSource.NAVIDROME
                _selectedSource.value = MediaSource.NAVIDROME
            }
            "EMBY" -> {
                _defaultSource.value = MediaSource.EMBY
                _selectedSource.value = MediaSource.EMBY
            }
            else -> {
                // "ALL" (Chora) retains full switchability exactly as today
            }
        }

        Log.d("MEDIA_SOURCE", "MediaSourceManager initialized. Default/Selected: ${_selectedSource.value.displayName}")
    }

    fun getAvailableSources(): List<MediaSource> {
        val list = mutableListOf<MediaSource>()

        val hasLocal = LocalProviderManager.getAllFolders().isNotEmpty()
        val hasNavidrome = NavidromeManager.getAllServers().isNotEmpty()
        val hasEmby = EmbyJellyfinManager.getAllServers().isNotEmpty()

        if (hasLocal) list.add(MediaSource.LOCAL)
        if (hasNavidrome) list.add(MediaSource.NAVIDROME)
        if (hasEmby) list.add(MediaSource.EMBY)

        // If multiple sources exist, also offer "All Sources"
        if (list.size > 1) {
            list.add(0, MediaSource.ALL)
        } else if (list.isEmpty()) {
            list.add(MediaSource.LOCAL)
        }

        return list
    }

    fun setSelectedSource(source: MediaSource) {
        if (_selectedSource.value != source) {
            _selectedSource.value = source
            Log.d("MEDIA_SOURCE", "Selected source changed to: ${source.displayName}")
            DataRefreshManager.notifyDataSourcesChanged()
        }
    }

    fun setDefaultSource(source: MediaSource) {
        _defaultSource.value = source
        if (::sharedPreferences.isInitialized) {
            sharedPreferences.edit {
                putString(PREF_DEFAULT_SOURCE, source.id)
            }
        }
        Log.d("MEDIA_SOURCE", "Default source saved as: ${source.displayName}")
    }

    fun restoreDefaultSource(sourceId: String?) {
        val source = MediaSource.fromId(sourceId)
        setDefaultSource(source)
        setSelectedSource(source)
    }

    fun isSourceActive(source: MediaSource): Boolean {
        val current = _selectedSource.value
        return current == MediaSource.ALL || current == source
    }
}
