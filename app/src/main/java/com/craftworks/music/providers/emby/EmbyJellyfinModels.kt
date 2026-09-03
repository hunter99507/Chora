package com.craftworks.music.providers.emby

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmbyAuthRequest(
    @SerialName("Username")
    val username: String,
    @SerialName("Pw")
    val pw: String
)

@Serializable
data class EmbyAuthResponse(
    @SerialName("AccessToken")
    val accessToken: String? = null,
    @SerialName("ServerId")
    val serverId: String? = null,
    @SerialName("User")
    val user: EmbyUser? = null
)

@Serializable
data class EmbyUser(
    @SerialName("Id")
    val id: String,
    @SerialName("Name")
    val name: String? = null
)

@Serializable
data class EmbyItemsResponse(
    @SerialName("Items")
    val items: List<EmbyItem> = emptyList(),
    @SerialName("TotalRecordCount")
    val totalRecordCount: Int = 0
)

@Serializable
data class EmbyItem(
    @SerialName("Id")
    val id: String,
    @SerialName("Name")
    val name: String = "",
    @SerialName("Type")
    val type: String? = null,
    @SerialName("RunTimeTicks")
    val runTimeTicks: Long? = null,
    @SerialName("IndexNumber")
    val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber")
    val parentIndexNumber: Int? = null,
    @SerialName("ProductionYear")
    val productionYear: Int? = null,
    @SerialName("Artists")
    val artists: List<String>? = null,
    @SerialName("ArtistItems")
    val artistItems: List<EmbyNameIdItem>? = null,
    @SerialName("Album")
    val album: String? = null,
    @SerialName("AlbumId")
    val albumId: String? = null,
    @SerialName("AlbumArtist")
    val albumArtist: String? = null,
    @SerialName("AlbumArtists")
    val albumArtists: List<EmbyNameIdItem>? = null,
    @SerialName("Genres")
    val genres: List<String>? = null,
    @SerialName("Overview")
    val overview: String? = null,
    @SerialName("UserData")
    val userData: EmbyUserData? = null,
    @SerialName("ImageTags")
    val imageTags: Map<String, String>? = null,
    @SerialName("PrimaryImageItemId")
    val primaryImageItemId: String? = null,
    @SerialName("MediaSources")
    val mediaSources: List<EmbyMediaSource>? = null,
    @SerialName("Container")
    val container: String? = null,
    @SerialName("Bitrate")
    val bitrate: Int? = null,
    @SerialName("Size")
    val size: Long? = null,
    @SerialName("Path")
    val path: String? = null,
    @SerialName("ChildCount")
    val childCount: Int? = null,
    @SerialName("CumulativeRunTimeTicks")
    val cumulativeRunTimeTicks: Long? = null,
    @SerialName("CollectionType")
    val collectionType: String? = null
)

@Serializable
data class EmbyNameIdItem(
    @SerialName("Name")
    val name: String = "",
    @SerialName("Id")
    val id: String = ""
)

@Serializable
data class EmbyUserData(
    @SerialName("IsFavorite")
    val isFavorite: Boolean = false,
    @SerialName("PlayCount")
    val playCount: Int = 0,
    @SerialName("Played")
    val played: Boolean = false,
    @SerialName("Rating")
    val rating: Float? = null
)

@Serializable
data class EmbyMediaSource(
    @SerialName("Id")
    val id: String? = null,
    @SerialName("Path")
    val path: String? = null,
    @SerialName("Container")
    val container: String? = null,
    @SerialName("Size")
    val size: Long? = null,
    @SerialName("Bitrate")
    val bitrate: Int? = null
)
