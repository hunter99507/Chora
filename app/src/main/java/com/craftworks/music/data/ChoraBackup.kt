package com.craftworks.music.data

import kotlinx.serialization.Serializable

@Serializable
data class ChoraBackup(
    val version: Int = 1,
    val exportedAt: String = "",
    val navidromeServers: List<NavidromeProvider> = emptyList(),
    val currentNavidromeServerId: String? = null,
    val embyJellyfinServers: List<EmbyJellyfinProvider> = emptyList(),
    val currentEmbyServerId: String? = null,
    val localFolders: List<String> = emptyList(),
    val disabledLocalFolders: List<String> = emptyList(),
    val defaultMediaSource: String? = null,
    val localRadiosJson: String? = null,
    val localPlaylistsJson: String? = null,
    val appearanceSettings: Map<String, String>? = null,
    val playbackSettings: Map<String, String>? = null
)
