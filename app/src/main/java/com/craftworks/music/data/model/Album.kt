package com.craftworks.music.data.model

import android.os.Bundle
import androidx.compose.runtime.mutableStateListOf
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

var albumList:MutableList<MediaData.Album> = mutableStateListOf()

fun MediaData.Album.toMediaItem(): MediaItem {
    val albumTitle = this@toMediaItem.name?.ifBlank { null }
        ?: this@toMediaItem.title?.ifBlank { null }
        ?: this@toMediaItem.album?.ifBlank { null }
        ?: "Unknown Album"
    val albumArtist = this@toMediaItem.artist.ifBlank { "Unknown Artist" }

    val mediaMetadata = MediaMetadata.Builder()
        .setTitle(albumTitle)
        .setArtist(albumArtist)
        .setAlbumTitle(albumTitle)
        .setDisplayTitle(albumTitle)
        .setAlbumArtist(albumArtist)
        .setArtworkUri(this@toMediaItem.coverArt?.ifBlank { null }?.toUri())
        .setRecordingYear(this@toMediaItem.year)
        .setDurationMs(this@toMediaItem.duration?.times(1000)?.toLong())
        .setIsBrowsable(true)
        .setIsPlayable(false)
        .setGenre(this@toMediaItem.genres?.joinToString() { it.name ?: "" })
        .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
        .setExtras(
            Bundle().apply {
                putString("navidromeID", this@toMediaItem.navidromeID)
                putString("starred", this@toMediaItem.starred)
                this@toMediaItem.artistId?.let { putString("artistId", it) }
            }
        )
        .build()

    return MediaItem.Builder()
        .setMediaId(
            if (this@toMediaItem.navidromeID.startsWith("Local_"))
                "folder_album_" + this@toMediaItem.navidromeID
            else
                this@toMediaItem.navidromeID
        )
        .setMediaMetadata(mediaMetadata)
        .build()
}

fun MediaItem.toAlbum(): MediaData.Album {
    val mediaMetadata = this.mediaMetadata
    val extras = mediaMetadata.extras
    val title = mediaMetadata.albumTitle?.toString()?.ifBlank { null }
        ?: mediaMetadata.title?.toString()?.ifBlank { null }
        ?: ""

    val cover = mediaMetadata.artworkUri?.toString() ?: ""
    return MediaData.Album(
        navidromeID = extras?.getString("navidromeID") ?: this.mediaId.removePrefix("folder_album_"),
        name = title,
        title = title,
        album = title,
        artist = mediaMetadata.artist?.toString() ?: "",
        year = mediaMetadata.recordingYear ?: mediaMetadata.releaseYear ?: 0,
        coverArt = cover,
        duration = extras?.getInt("Duration") ?: ((mediaMetadata.durationMs ?: 0L) / 1000L).toInt(),
        songs = mutableListOf(),
        songCount = 0,
        artistId = extras?.getString("artistId") ?: ""
    )
}