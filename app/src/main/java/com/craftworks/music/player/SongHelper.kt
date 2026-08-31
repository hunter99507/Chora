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
            if (mediaItems.isEmpty())
                return

            withContext(Dispatchers.Main) {
                mediaController?.setMediaItems(mediaItems, index, 0)
                mediaController?.prepare()
                mediaController?.play()
                _expandNowPlayingSheet.tryEmit(Unit)
            }
        }
    }
}