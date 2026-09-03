package com.craftworks.music.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.craftworks.music.data.EmbyJellyfinLibrary
import com.craftworks.music.data.EmbyJellyfinProvider
import com.craftworks.music.data.datasource.emby.EmbyJellyfinDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

object EmbyJellyfinManager {
    private val servers = mutableMapOf<String, EmbyJellyfinProvider>()

    private var _currentServerId = MutableStateFlow<String?>(null)
    val currentServerId: StateFlow<String?> = _currentServerId.asStateFlow()

    private val _allServers = MutableStateFlow<List<EmbyJellyfinProvider>>(emptyList())
    val allServers: StateFlow<List<EmbyJellyfinProvider>> = _allServers.asStateFlow()

    private var _libraries = MutableStateFlow<List<Pair<EmbyJellyfinLibrary, Boolean>>>(emptyList())
    val libraries: StateFlow<List<Pair<EmbyJellyfinLibrary, Boolean>>> =
        _libraries
            .stateIn(
                CoroutineScope(Dispatchers.Main.immediate),
                SharingStarted.Eagerly,
                emptyList()
            )

    private val _syncStatus = MutableStateFlow(false)
    val syncStatus: StateFlow<Boolean> = _syncStatus.asStateFlow()

    suspend fun addServer(
        server: EmbyJellyfinProvider,
        isPing: Boolean = false,
        selectedLibraries: List<Pair<EmbyJellyfinLibrary, Boolean>>? = null
    ) {
        Log.d("EMBY_JELLYFIN", "Added server $server")
        server.url = if (!server.url.trim().startsWith("http"))
            "http://" + server.url.trim()
        else
            server.url.trim()

        servers[server.id] = server
        _currentServerId.value = server.id

        if (isPing)
            return

        val librariesToSave = selectedLibraries ?: EmbyJellyfinDataSource().getLibraries(server).map { Pair(it, true) }

        setServerLibraries(server.id, librariesToSave)
        if (server.id == _currentServerId.value) {
            _libraries.value = librariesToSave
        }

        updateServersFlow()
        saveServers()
    }

    fun setServerLibraries(serverId: String, libraries: List<Pair<EmbyJellyfinLibrary, Boolean>>) {
        servers[serverId]?.libraryIds = libraries
        if (serverId == _currentServerId.value) {
            _libraries.value = libraries
        }
        saveServers()
    }

    fun toggleServerLibraryEnabled(serverId: String, libraryId: String, isEnabled: Boolean) {
        servers[serverId]?.let { server ->
            val updatedLibraries = server.libraryIds.map { (library, currentEnabled) ->
                if (library.id == libraryId) {
                    Pair(library, isEnabled)
                } else {
                    Pair(library, currentEnabled)
                }
            }
            server.libraryIds = updatedLibraries
            if (serverId == _currentServerId.value) {
                if (_libraries.value != updatedLibraries) {
                    _libraries.value = updatedLibraries
                }
            }
            saveServers()
        }
    }

    fun removeServer(id: String, isPing: Boolean = false) {
        servers.remove(id)
        if (_currentServerId.value == id) {
            _currentServerId.value = servers.keys.firstOrNull()
            _libraries.value = _currentServerId.value?.let { servers[it]?.libraryIds } ?: emptyList()
        }
        if (isPing)
            return

        updateServersFlow()
        saveServers()
    }

    fun checkActiveServers(): Boolean {
        return servers.values.any { it.enabled } && getCurrentServer()?.enabled == true
    }

    fun getAllServers(): List<EmbyJellyfinProvider> = servers.values.toList()
    fun getCurrentServer(): EmbyJellyfinProvider? = _currentServerId.value?.let { servers[it] }

    fun setCurrentServer(serverId: String?) {
        _currentServerId.value = serverId
        _libraries.value = serverId?.let { servers[it]?.libraryIds } ?: emptyList()
        updateServersFlow()
        saveServers()
    }

    fun setServerEnabled(serverId: String, enabled: Boolean) {
        servers[serverId]?.let { server ->
            server.enabled = enabled
            if (enabled) {
                _currentServerId.value = serverId
                _libraries.value = server.libraryIds
            } else {
                if (_currentServerId.value == serverId) {
                    val nextActiveServer = servers.values.firstOrNull { it.enabled && it.id != serverId }
                    _currentServerId.value = nextActiveServer?.id
                    _libraries.value = nextActiveServer?.libraryIds ?: emptyList()
                }
            }
            updateServersFlow()
            saveServers()
        }
    }

    fun getEnabledLibraryIdsForCurrentServer(): List<String>? {
        val currentServer = getCurrentServer() ?: return null
        return currentServer.libraryIds
            .filter { it.second }
            .map { it.first.id }
    }

    private fun updateServersFlow() {
        _allServers.value = servers.values.toList()
    }

    fun setSyncingStatus(status: Boolean) {
        _syncStatus.value = status
    }

    private lateinit var sharedPreferences: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }
    private const val PREF_SERVERS = "emby_jellyfin_servers"
    private const val PREF_CURRENT_SERVER = "current_emby_server_id"

    fun init(context: Context) {
        setSyncingStatus(true)
        sharedPreferences = context.getSharedPreferences("EmbyJellyfinPrefs", Context.MODE_PRIVATE)
        loadServers()
        setSyncingStatus(false)
    }

    private fun saveServers() {
        DataRefreshManager.notifyDataSourcesChanged()
        if (::sharedPreferences.isInitialized) {
            val serversJson = json.encodeToString(servers as Map<String, EmbyJellyfinProvider>)
            sharedPreferences.edit { putString(PREF_SERVERS, serversJson) }
            sharedPreferences.edit { putString(PREF_CURRENT_SERVER, _currentServerId.value) }
        }
    }

    private fun loadServers() {
        _currentServerId.value = sharedPreferences.getString(PREF_CURRENT_SERVER, null)
        val serversJson = sharedPreferences.getString(PREF_SERVERS, null)
        if (serversJson != null) {
            val loadedServers: Map<String, EmbyJellyfinProvider> = json.decodeFromString(serversJson)
            servers.putAll(loadedServers)
        }
        _libraries.value = _currentServerId.value?.let { servers[it]?.libraryIds } ?: emptyList()
        updateServersFlow()
    }
}
