package com.craftworks.music.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

object LocalProviderManager {
    private val _allFolders = MutableStateFlow<List<String>>(emptyList())
    val allFolders: StateFlow<List<String>> = _allFolders.asStateFlow()

    private val _disabledFolders = MutableStateFlow<Set<String>>(emptySet())
    val disabledFolders: StateFlow<Set<String>> = _disabledFolders.asStateFlow()

    fun addFolder(folder: String) {
        Log.d("LOCAL_PROVIDER", "Added local folder $folder")
        if (_allFolders.value.contains(folder)) return
        _allFolders.value += folder
        saveFolders()
    }

    fun removeFolder(folder: String) {
        _allFolders.value -= folder
        _disabledFolders.value -= folder
        saveFolders()
    }

    fun isFolderEnabled(folder: String): Boolean {
        return !_disabledFolders.value.contains(folder)
    }

    fun setFolderEnabled(folder: String, enabled: Boolean) {
        if (enabled) {
            _disabledFolders.value -= folder
        } else {
            _disabledFolders.value += folder
        }
        saveFolders()
    }

    fun checkActiveFolders(): Boolean {
        return getActiveFolders().isNotEmpty()
    }

    fun getAllFolders(): List<String> = _allFolders.value

    fun getActiveFolders(): List<String> {
        val disabled = _disabledFolders.value
        return _allFolders.value.filter { !disabled.contains(it) }
    }

    // Save and load local folders.
    private lateinit var sharedPreferences: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }
    private const val PREF_FOLDERS = "local_folders"
    private const val PREF_DISABLED_FOLDERS = "disabled_local_folders"

    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences("LocalProviderPrefs", Context.MODE_PRIVATE)
        loadFolders()
    }

    private fun saveFolders() {
        DataRefreshManager.notifyDataSourcesChanged()
        if (::sharedPreferences.isInitialized) {
            val serversJson = json.encodeToString(_allFolders.value)
            val disabledJson = json.encodeToString(_disabledFolders.value.toList())
            sharedPreferences.edit {
                putString(PREF_FOLDERS, serversJson)
                putString(PREF_DISABLED_FOLDERS, disabledJson)
            }
        }
    }

    private fun loadFolders() {
        val foldersJson = sharedPreferences.getString(PREF_FOLDERS, null)
        if (foldersJson != null) {
            val loadedServers: List<String> = json.decodeFromString(foldersJson)
            _allFolders.value = loadedServers.distinct()
        }

        val disabledJson = sharedPreferences.getString(PREF_DISABLED_FOLDERS, null)
        if (disabledJson != null) {
            val loadedDisabled: List<String> = json.decodeFromString(disabledJson)
            _disabledFolders.value = loadedDisabled.toSet()
        }
    }
}