@file:OptIn(UnstableApi::class) package com.craftworks.music.player

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SongHelper {
    companion object{
        private val _expandNowPlayingSheet = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val expandNowPlayingSheet = _expandNowPlayingSheet.asSharedFlow()

        suspend fun play(mediaItems: List<MediaItem>, index: Int, mediaController: MediaController?) {
            val playableItems = mediaItems.filter {
                it.mediaMetadata.isPlayable != false && !it.mediaId.startsWith("folder_album_") && it.mediaId.isNotBlank()
            }
            if (playableItems.isEmpty())
                return

            val safeIndex = index.coerceIn(0, playableItems.size - 1)

            withContext(Dispatchers.Main) {
                mediaController?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                mediaController?.shuffleModeEnabled = false
                mediaController?.setMediaItems(playableItems, safeIndex, 0)
                mediaController?.prepare()
                mediaController?.play()
                _expandNowPlayingSheet.tryEmit(Unit)
            }
        }
    }
}