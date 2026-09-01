package com.craftworks.music.player

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.craftworks.music.managers.settings.PlaybackSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class SongHelper {
    companion object{
        private val _expandNowPlayingSheet = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val expandNowPlayingSheet = _expandNowPlayingSheet.asSharedFlow()

        @OptIn(UnstableApi::class)
        suspend fun play(mediaItems: List<MediaItem>, index: Int, mediaController: MediaController?) {
            val playableItems = mediaItems.filter {
                it.mediaMetadata.isPlayable != false && !it.mediaId.startsWith("folder_album_") && it.mediaId.isNotBlank()
            }
            if (playableItems.isEmpty())
                return

            val safeIndex = index.coerceIn(0, playableItems.size - 1)

            val service = ChoraMediaLibraryService.getInstance()
            val playbackSettings = service?.playbackSettingsManager ?: service?.applicationContext?.let { PlaybackSettingsManager(it) }
            val defaultShuffle = playbackSettings?.defaultShuffleFlow?.first() ?: true
            val defaultRepeat = playbackSettings?.defaultRepeatFlow?.first() ?: Player.REPEAT_MODE_ALL

            withContext(Dispatchers.Main) {
                mediaController?.repeatMode = defaultRepeat
                mediaController?.shuffleModeEnabled = defaultShuffle
                mediaController?.setMediaItems(playableItems, safeIndex, 0)
                mediaController?.prepare()
                mediaController?.play()
                _expandNowPlayingSheet.tryEmit(Unit)
            }
        }
    }
}