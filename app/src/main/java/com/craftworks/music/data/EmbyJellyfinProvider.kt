package com.craftworks.music.data

import kotlinx.serialization.Serializable

@Serializable
data class EmbyJellyfinProvider(
    val id: String = "0",
    var url: String,
    var username: String,
    val password: String,
    var token: String? = null,
    var userId: String? = null,
    var serverId: String? = null,
    var enabled: Boolean = true,
    var allowSelfSignedCert: Boolean? = false,
    var libraryIds: List<Pair<EmbyJellyfinLibrary, Boolean>> = listOf(Pair(EmbyJellyfinLibrary("0", "Music"), true))
)

@Serializable
data class EmbyJellyfinLibrary(
    val id: String = "0",
    var name: String,
)
