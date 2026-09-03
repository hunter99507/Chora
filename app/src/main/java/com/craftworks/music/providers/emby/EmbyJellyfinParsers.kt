package com.craftworks.music.providers.emby

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.StarRating
import com.craftworks.music.data.model.Artists
import com.craftworks.music.data.model.Genre
import com.craftworks.music.data.model.MediaData
import com.craftworks.music.data.model.toMediaItem

fun EmbyItem.toSongMediaItem(serverUrl: String, token: String): MediaItem {
    val cleanUrl = serverUrl.trimEnd('/')
    val streamUri = "$cleanUrl/Audio/$id/stream?static=true&api_key=$token&X-Emby-Token=$token".toUri()
    val artUri = "$cleanUrl/Items/${primaryImageItemId ?: id}/Images/Primary?maxWidth=500&maxHeight=500&quality=90&api_key=$token&X-Emby-Token=$token".toUri()
    val durationSeconds = ((runTimeTicks ?: 0L) / 10_000_000L).toInt()
    val durationMs = ((runTimeTicks ?: 0L) / 10_000L)
    val artistName = artists?.joinToString(", ") ?: albumArtist ?: ""

    val mediaMetadata = MediaMetadata.Builder()
        .setTitle(name)
        .setDisplayTitle(name)
        .setArtist(artistName)
        .setAlbumArtist(albumArtist ?: artistName)
        .setAlbumTitle(album ?: "")
        .setArtworkUri(artUri)
        .setRecordingYear(productionYear)
        .setDiscNumber(parentIndexNumber ?: 1)
        .setTrackNumber(indexNumber ?: 1)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .setDurationMs(durationMs)
        .setGenre(genres?.joinToString(", "))
        .apply {
            if (userData?.isFavorite == true) {
                setUserRating(StarRating(5, 5f))
            } else {
                setUserRating(StarRating(5, 0f))
            }
        }
        .setExtras(Bundle().apply {
            putString("navidromeID", "emby_$id")
            putString("embyId", id)
            putString("provider", "emby")
            putString("lyricsArtist", artistName)
            putString("albumId", albumId ?: "")
            putInt("duration", durationSeconds)
            putString("format", container ?: "audio")
            putLong("bitrate", bitrate?.toLong() ?: 0L)
            putBoolean("isRadio", false)
            putBoolean("isFavorite", userData?.isFavorite == true)
        })
        .build()

    return MediaItem.Builder()
        .setMediaId(streamUri.toString())
        .setUri(streamUri)
        .setMediaMetadata(mediaMetadata)
        .build()
}

fun EmbyItem.toAlbumMediaItem(serverUrl: String, token: String): MediaItem {
    val cleanUrl = serverUrl.trimEnd('/')
    val artUri = "$cleanUrl/Items/${primaryImageItemId ?: id}/Images/Primary?maxWidth=500&maxHeight=500&quality=90&api_key=$token&X-Emby-Token=$token".toUri()
    val artistName = albumArtist ?: artists?.joinToString(", ") ?: ""
    val durationSeconds = ((runTimeTicks ?: 0L) / 10_000_000L).toInt()

    val mediaMetadata = MediaMetadata.Builder()
        .setIsBrowsable(true)
        .setIsPlayable(false)
        .setTitle(name)
        .setDisplayTitle(name)
        .setAlbumTitle(name)
        .setArtist(artistName)
        .setAlbumArtist(artistName)
        .setArtworkUri(artUri)
        .setRecordingYear(productionYear)
        .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
        .setGenre(genres?.joinToString(", "))
        .setExtras(Bundle().apply {
            putString("navidromeID", "emby_$id")
            putString("embyId", id)
            putString("provider", "emby")
            putInt("songCount", childCount ?: 0)
            putInt("duration", durationSeconds)
            putString("artistId", artistItems?.firstOrNull()?.id ?: "")
            putString("year", productionYear?.toString() ?: "")
            putString("coverArt", artUri.toString())
            if (userData?.isFavorite == true) {
                putString("starred", "starred")
            }
        })
        .build()

    return MediaItem.Builder()
        .setMediaId("folder_album_emby_$id")
        .setMediaMetadata(mediaMetadata)
        .build()
}

fun EmbyItem.toArtistData(serverUrl: String, token: String): MediaData.Artist {
    val cleanUrl = serverUrl.trimEnd('/')
    val artUri = "$cleanUrl/Items/$id/Images/Primary?maxWidth=500&maxHeight=500&quality=90&api_key=$token&X-Emby-Token=$token"

    return MediaData.Artist(
        navidromeID = "emby_$id",
        name = name,
        artistImageUrl = artUri,
        albumCount = childCount ?: 0,
        description = overview ?: "",
        starred = if (userData?.isFavorite == true) "starred" else null
    )
}

fun EmbyItem.toPlaylistData(serverUrl: String, token: String): MediaData.Playlist {
    val cleanUrl = serverUrl.trimEnd('/')
    val artUri = "$cleanUrl/Items/$id/Images/Primary?maxWidth=500&maxHeight=500&quality=90&api_key=$token&X-Emby-Token=$token"
    val durationSeconds = ((cumulativeRunTimeTicks ?: runTimeTicks ?: 0L) / 10_000_000L).toInt()

    return MediaData.Playlist(
        navidromeID = "emby_$id",
        name = name,
        songCount = childCount ?: 0,
        duration = durationSeconds,
        created = "",
        changed = "",
        coverArt = artUri
    )
}
