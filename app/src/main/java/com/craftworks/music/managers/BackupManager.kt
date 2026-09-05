package com.craftworks.music.managers

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.craftworks.music.data.ChoraBackup
import com.craftworks.music.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {
    private const val TAG = "BackupManager"
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun createBackupJson(context: Context): String = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())

        // Read all preferences from DataStore
        val prefs = context.dataStore.data.first()
        val prefsMap = mutableMapOf<String, String>()
        for ((key, value) in prefs.asMap()) {
            prefsMap[key.name] = value.toString()
        }

        val backup = ChoraBackup(
            version = 1,
            exportedAt = timestamp,
            navidromeServers = NavidromeManager.getAllServers(),
            currentNavidromeServerId = NavidromeManager.currentServerId.value,
            embyJellyfinServers = EmbyJellyfinManager.getAllServers(),
            currentEmbyServerId = EmbyJellyfinManager.currentServerId.value,
            localFolders = LocalProviderManager.getAllFolders(),
            disabledLocalFolders = LocalProviderManager.disabledFolders.value.toList(),
            defaultMediaSource = MediaSourceManager.defaultSource.value.id,
            localRadiosJson = prefs[stringPreferencesKey("radios_list")],
            localPlaylistsJson = prefs[stringPreferencesKey("playlists_list")],
            playbackSettings = prefsMap.filterKeys { it.startsWith("default_") || it.startsWith("smart_") || it.contains("transcoding") || it.startsWith("scrobble") || it.startsWith("auto_play") },
            appearanceSettings = prefsMap.filterKeys { it.startsWith("theme") || it.startsWith("background") || it.startsWith("now_playing") || it.startsWith("show_") || it.startsWith("navbar") }
        )

        json.encodeToString(ChoraBackup.serializer(), backup)
    }

    suspend fun restoreFromJson(context: Context, jsonString: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val backup = json.decodeFromString(ChoraBackup.serializer(), jsonString)

            var restoredNavidrome = 0
            var restoredEmby = 0
            var restoredLocal = 0

            // 1. Restore Navidrome Servers
            if (backup.navidromeServers.isNotEmpty()) {
                NavidromeManager.restoreServers(backup.navidromeServers, backup.currentNavidromeServerId)
                restoredNavidrome = backup.navidromeServers.size
            }

            // 2. Restore Emby / Jellyfin Servers
            if (backup.embyJellyfinServers.isNotEmpty()) {
                EmbyJellyfinManager.restoreServers(backup.embyJellyfinServers, backup.currentEmbyServerId)
                restoredEmby = backup.embyJellyfinServers.size
            }

            // 3. Restore Local Folders
            if (backup.localFolders.isNotEmpty()) {
                LocalProviderManager.restoreFolders(
                    backup.localFolders,
                    backup.disabledLocalFolders.toSet()
                )
                restoredLocal = backup.localFolders.size
            }

            // 4. Restore Default Media Source
            backup.defaultMediaSource?.let {
                MediaSourceManager.restoreDefaultSource(it)
            }

            // 5. Restore Settings & Local playlists/radios to DataStore
            context.dataStore.edit { preferences ->
                backup.localRadiosJson?.let {
                    preferences[stringPreferencesKey("radios_list")] = it
                }
                backup.localPlaylistsJson?.let {
                    preferences[stringPreferencesKey("playlists_list")] = it
                }

                val allSettings = mutableMapOf<String, String>()
                backup.playbackSettings?.let { allSettings.putAll(it) }
                backup.appearanceSettings?.let { allSettings.putAll(it) }

                for ((k, v) in allSettings) {
                    when {
                        v.equals("true", ignoreCase = true) || v.equals("false", ignoreCase = true) -> {
                            preferences[booleanPreferencesKey(k)] = v.toBoolean()
                        }
                        v.toIntOrNull() != null -> {
                            preferences[intPreferencesKey(k)] = v.toInt()
                        }
                        v.toLongOrNull() != null -> {
                            preferences[longPreferencesKey(k)] = v.toLong()
                        }
                        else -> {
                            preferences[stringPreferencesKey(k)] = v
                        }
                    }
                }
            }

            // Refresh media sources
            DataRefreshManager.notifyDataSourcesChanged()

            val summary = "Restored $restoredNavidrome Navidrome server(s), $restoredEmby Emby server(s), $restoredLocal local folder(s)"
            Log.d(TAG, summary)
            Result.success(summary)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore backup", e)
            Result.failure(e)
        }
    }
}
