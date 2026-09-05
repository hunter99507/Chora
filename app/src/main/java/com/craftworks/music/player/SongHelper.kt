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
        private val _expandNowPlayingSheet = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        val expandNowPlayingSheet = _expandNowPlayingSheet.asSharedFlow()

        @OptIn(UnstableApi::class)
        suspend fun play(
            mediaItems: List<MediaItem>,
            index: Int,
            mediaController: MediaController?,
            expandSheet: Boolean = true,
            shuffle: Boolean = false
        ) {
            val playableItems = mediaItems.filter {
                it.mediaMetadata.isPlayable != false &&
                !it.mediaId.startsWith("folder_album_") &&
                it.mediaId.isNotBlank() &&
                it.mediaMetadata.mediaType != androidx.media3.common.MediaMetadata.MEDIA_TYPE_ALBUM &&
                it.mediaMetadata.mediaType != androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS &&
                it.mediaMetadata.mediaType != androidx.media3.common.MediaMetadata.MEDIA_TYPE_ARTIST &&
                it.mediaMetadata.mediaType != androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS &&
                it.mediaMetadata.mediaType != androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS &&
                !it.mediaId.startsWith("action_")
            }
            if (playableItems.isEmpty())
                return

            val safeIndex = index.coerceIn(0, playableItems.size - 1)

            val service = ChoraMediaLibraryService.getInstance()
            val playbackSettings = service?.playbackSettingsManager ?: service?.applicationContext?.let { PlaybackSettingsManager(it) }
            val defaultRepeat = playbackSettings?.defaultRepeatFlow?.first() ?: Player.REPEAT_MODE_ALL
            val isSmartShuffle = playbackSettings?.smartShuffleFlow?.first() ?: true
            android.util.Log.d("SmartShuffle", "SongHelper.play: shuffle=$shuffle, isSmartShuffle=$isSmartShuffle, totalPlayable=${playableItems.size}")

            val preparedItems = if (shuffle) {
                withContext(Dispatchers.Default) {
                    if (isSmartShuffle) SmartShuffleHelper.smartShuffle(playableItems) else playableItems.shuffled()
                }
            } else {
                playableItems
            }

            withContext(Dispatchers.Main) {
                mediaController?.repeatMode = defaultRepeat
                service?.isQueuePreShuffled = shuffle
                if (shuffle) {
                    mediaController?.shuffleModeEnabled = true
                    mediaController?.setMediaItems(preparedItems, 0, 0)
                } else {
                    val defaultShuffle = playbackSettings?.defaultShuffleFlow?.first() ?: false
                    mediaController?.shuffleModeEnabled = defaultShuffle
                    mediaController?.setMediaItems(preparedItems, safeIndex, 0)
                }
                mediaController?.prepare()
                mediaController?.play()
                if (expandSheet) {
                    _expandNowPlayingSheet.tryEmit(Unit)
                }
            }
        }

        @OptIn(UnstableApi::class)
        fun calculateQueuePosition(player: Player?): Pair<Int, Int> {
            if (player == null) return Pair(0, 0)
            val total = player.mediaItemCount
            val currentIndex = player.currentMediaItemIndex
            if (total <= 0 || currentIndex !in 0 until total) return Pair(0, 0)

            val timeline = player.currentTimeline
            if (timeline.isEmpty || !player.shuffleModeEnabled) {
                return Pair(currentIndex + 1, total)
            }

            // Backward traversal in shuffle order to count how many songs precede the current item
            var position = 1
            var idx = currentIndex
            var foundStart = false
            while (position <= total) {
                val prev = timeline.getPreviousWindowIndex(idx, Player.REPEAT_MODE_OFF, true)
                if (prev == androidx.media3.common.C.INDEX_UNSET || prev < 0) {
                    foundStart = true
                    break
                }
                if (prev == idx) break
                position++
                idx = prev
            }

            if (foundStart) {
                return Pair(position.coerceIn(1, total), total)
            }

            // Fallback: forward traversal from getFirstWindowIndex
            var forwardPos = 1
            var curr = timeline.getFirstWindowIndex(true)
            while (forwardPos <= total && curr != androidx.media3.common.C.INDEX_UNSET && curr >= 0) {
                if (curr == currentIndex) {
                    return Pair(forwardPos, total)
                }
                curr = timeline.getNextWindowIndex(curr, Player.REPEAT_MODE_OFF, true)
                forwardPos++
            }

            return Pair(currentIndex + 1, total)
        }
    }
}