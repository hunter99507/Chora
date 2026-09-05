package com.craftworks.music.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.ui.util.fastFilter
import androidx.core.math.MathUtils.clamp
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Rating
import androidx.media3.common.StarRating
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.craftworks.music.MainActivity
import com.craftworks.music.R
import com.craftworks.music.data.model.toMediaItem
import com.craftworks.music.data.repository.AlbumRepository
import com.craftworks.music.data.repository.ArtistRepository
import com.craftworks.music.data.repository.LyricsRepository
import com.craftworks.music.data.repository.PlaylistRepository
import com.craftworks.music.data.repository.RadioRepository
import com.craftworks.music.data.repository.SongRepository
import com.craftworks.music.managers.NavidromeManager
import com.craftworks.music.managers.TranscodeManager
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.managers.settings.LocalDataSettingsManager
import com.craftworks.music.managers.settings.PlaybackSettingsManager
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.math.pow

/*
    Thanks to Yurowitz on StackOverflow for this! Used it as a template.
    https://stackoverflow.com/questions/76838126/can-i-define-a-medialibraryservice-without-an-app
*/

@UnstableApi
@AndroidEntryPoint
class ChoraMediaLibraryService : MediaLibraryService() {
    //region Vars
    lateinit var player: ExoPlayer
    var session: MediaLibrarySession? = null

    private var scrobbleJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var _sleepTimerRemainingTime = MutableStateFlow(0)
    val sleepTimerRemainingTime: StateFlow<Int> = _sleepTimerRemainingTime.asStateFlow()

    // Preloads the next 3 queue items into the media cache (phone / Android Auto / TV).
    private var preloadJob: Job? = null

    @Inject lateinit var appearanceSettingsManager: AppearanceSettingsManager
    @Inject lateinit var playbackSettingsManager: PlaybackSettingsManager
    @Inject lateinit var transcodeManager: TranscodeManager

    @Inject lateinit var songRepository: SongRepository
    @Inject lateinit var albumRepository: AlbumRepository
    @Inject lateinit var artistRepository: ArtistRepository
    @Inject lateinit var radioRepository: RadioRepository
    @Inject lateinit var playlistRepository: PlaylistRepository
    @Inject lateinit var lyricsRepository: LyricsRepository
    @Inject lateinit var localMusicStatsManager: com.craftworks.music.managers.LocalMusicStatsManager

    companion object {
        const val CUSTOM_ACTION_SHUFFLE = "com.craftworks.chora.CUSTOM_ACTION_SHUFFLE"
        const val CUSTOM_ACTION_REPEAT = "com.craftworks.chora.CUSTOM_ACTION_REPEAT"
        const val CUSTOM_ACTION_CYCLE_SOURCE = "com.craftworks.chora.CUSTOM_ACTION_CYCLE_SOURCE"
        const val CUSTOM_ACTION_REFRESH = "com.craftworks.chora.CUSTOM_ACTION_REFRESH"

        private var instance: ChoraMediaLibraryService? = null

        fun getInstance(): ChoraMediaLibraryService? {
            return instance
        }

        @Volatile
        private var simpleCache: SimpleCache? = null

        @Synchronized
        fun getSimpleCache(context: android.content.Context): SimpleCache {
            if (simpleCache == null) {
                val cacheDir = File(context.cacheDir, "media_stream_cache")
                val databaseProvider = StandaloneDatabaseProvider(context)
                simpleCache = SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(250 * 1024 * 1024L), databaseProvider)
            }
            return simpleCache!!
        }
    }

    var isQueuePreShuffled: Boolean = false

    private val rootItem = MediaItem.Builder()
        .setMediaId("nodeROOT")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .setTitle("Root")
                .build()
        )
        .build()

    private val homeItem = MediaItem.Builder()
        .setMediaId("nodeHOME")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
                .setTitle("Home")
                .setExtras(Bundle().apply {
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                    )
                })
                .build()
        )
        .build()

    private val albumsItem = MediaItem.Builder()
        .setMediaId("nodeALBUMS")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
                .setTitle("Albums")
                .setExtras(Bundle().apply {
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                    )
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                    )
                })
                .build()
        )
        .build()

    private val songsItem = MediaItem.Builder()
        .setMediaId("nodeSONGS")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                .setTitle("Songs")
                .setExtras(Bundle().apply {
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                    )
                })
                .build()
        )
        .build()

    private val artistsItem = MediaItem.Builder()
        .setMediaId("nodeARTISTS")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)
                .setTitle("Artists")
                .setExtras(Bundle().apply {
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                    )
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                    )
                })
                .build()
        )
        .build()

    private val radiosItem = MediaItem.Builder()
        .setMediaId("nodeRADIOS")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                .setTitle("Radios")
                .build()
        )
        .build()

    private val playlistsItem = MediaItem.Builder()
        .setMediaId("nodePLAYLISTS")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                .setTitle("Playlists")
                .build()
        )
        .build()

    private val sourcesItem = MediaItem.Builder()
        .setMediaId("nodeSOURCES")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .setTitle("Sources")
                .build()
        )
        .build()

    private var rootHierarchy = mutableListOf(homeItem, albumsItem, songsItem, artistsItem, playlistsItem)

    private val serviceMainScope = CoroutineScope(Dispatchers.Main)
    private val serviceIOScope = CoroutineScope(Dispatchers.IO)

    @Volatile
    var aHomeScreenItems: List<MediaItem> = emptyList()
    @Volatile
    var aAlbumScreenItems: List<MediaItem> = emptyList()
    @Volatile
    var aSongScreenItems: List<MediaItem> = emptyList()
    @Volatile
    var aArtistsScreenItems: List<MediaItem> = emptyList()
    @Volatile
    var aRadioScreenItems: List<MediaItem> = emptyList()
    @Volatile
    var aPlaylistScreenItems: List<MediaItem> = emptyList()

    @Volatile
    var aFolderSongs: List<MediaItem> = emptyList()

    private val homeMutex = Mutex()
    private val albumMutex = Mutex()
    private val songMutex = Mutex()
    private val artistMutex = Mutex()
    private val radioMutex = Mutex()
    private val playlistMutex = Mutex()
    private val folderMutex = Mutex()

    //endregion

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        Log.d("AA", "onCreate Android Auto")

        if (session == null)
            initializePlayer()
        else
            Log.d("AA", "MediaSession already initialized, not recreating")

        instance = this
    }

    @OptIn(UnstableApi::class)
    fun initializePlayer() {
        serviceIOScope.launch {
            appearanceSettingsManager.androidAutoNavItemsFlow.collect { items ->
                val routeToItem = mapOf(
                    "home_screen" to homeItem,
                    "album_screen" to albumsItem,
                    "songs_screen" to songsItem,
                    "artists_screen" to artistsItem,
                    "playlist_screen" to playlistsItem,
                    "radio_screen" to radiosItem,
                    "sources_screen" to sourcesItem
                )

                rootHierarchy = items
                    .filter { it.enabled }
                    .mapNotNull { routeToItem[it.screenRoute] }
                    .toMutableList()

                if (rootHierarchy.isEmpty()) {
                    rootHierarchy = mutableListOf(homeItem, albumsItem, songsItem, artistsItem, playlistsItem)
                }

                session?.notifyChildrenChanged("nodeROOT", rootHierarchy.size, null)
            }
        }

        serviceIOScope.launch {
            appearanceSettingsManager.homeItemsItemsFlow.collect {
                aHomeScreenItems = emptyList()
                session?.notifyChildrenChanged("nodeHOME", 0, null)
            }
        }

        val resolvingDataSourceFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this),
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    val uri = dataSpec.uri

                    if (uri.path?.contains("stream") == true) {
                        val bitrate = runBlocking { transcodeManager.currentBitrateFlow.first() }
                        if (bitrate == "No Transcoding")
                            return dataSpec

                        val format = runBlocking { transcodeManager.currentFormatFlow.first() }

                        val newUri = uri.buildUpon()
                            .appendQueryParameter("format", format)
                            .appendQueryParameter("maxBitRate", bitrate)
                            .build()

                        return dataSpec.withUri(newUri)
                    }
                    return dataSpec
                }
            }
        )

        val cache = getSimpleCache(this)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(resolvingDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000,
                120_000,
                1_000,
                2_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(30_000, true)
            .build()

        player = ExoPlayer.Builder(this)
            .setSeekParameters(SeekParameters.EXACT)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setWakeMode(
                if (NavidromeManager.checkActiveServers())
                    C.WAKE_MODE_NETWORK
                else
                    C.WAKE_MODE_LOCAL
            )
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build()

        player.repeatMode = Player.REPEAT_MODE_ALL
        player.shuffleModeEnabled = true

        var playerScrobbled = false

        player.addListener(object : Player.Listener {
            override fun onRepeatModeChanged(repeatMode: Int) {
                updateCustomLayout()
                super.onRepeatModeChanged(repeatMode)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateCustomLayout()
                super.onShuffleModeEnabledChanged(shuffleModeEnabled)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Apply ReplayGain as the target volume for this track
                val replayGain = mediaItem?.mediaMetadata?.extras?.getFloat("replayGain")
                val targetVolume =
                    if (mediaItem?.mediaMetadata?.extras?.containsKey("replayGain") == true && replayGain != null) {
                        clamp(
                            (10f.pow(
                                (replayGain / 20f)
                            )), 0f, 1f
                        )
                    } else {
                        1f
                    }
                player.volume = targetVolume

                playerScrobbled = false

                super.onMediaItemTransition(mediaItem, reason)

                serviceIOScope.launch {
                    lyricsRepository.getLyrics(mediaItem?.mediaMetadata)
                    val mediaId = mediaItem?.mediaMetadata?.extras?.getString("navidromeID") ?: return@launch
                    songRepository.scrobbleSong(mediaId, false)
                }

                // Pre-cache up to 3 upcoming tracks in the queue so pressing "next" (or letting
                // a track end) never waits on the network. This runs in its OWN coroutine so it
                // starts immediately — not serialized behind the lyrics/scrobble network calls —
                // and cancels any stale preload from a previous transition.
                preloadJob?.cancel()
                preloadJob = serviceIOScope.launch {
                    try {
                        // Give the player a moment to buffer the new track first; live playback
                        // gets first claim on bandwidth (this also keeps the fade-in clean).
                        delay(2000)

                        // ExoPlayer requires all player access on the thread that created it
                        // (main) — read the queue there, then stream/cache off the main thread.
                        val pendingUris = withContext(Dispatchers.Main) {
                            if (!::player.isInitialized) return@withContext emptyList<String>()
                            val currentIndex = player.currentMediaItemIndex
                            val totalItems = player.mediaItemCount
                            (1..3).mapNotNull { offset ->
                                val targetIndex = currentIndex + offset
                                if (targetIndex >= totalItems) return@mapNotNull null
                                val nextItem = player.getMediaItemAt(targetIndex)
                                val nextUri = nextItem.requestMetadata.mediaUri ?: nextItem.mediaId.toUri()
                                if (nextUri.toString().startsWith("http")) nextUri.toString() else null
                            }
                        }

                        if (pendingUris.isEmpty()) return@launch

                        val cacheDataSource = cacheDataSourceFactory.createDataSource()

                        pendingUris.forEachIndexed { index, uri ->
                            val dataSpec = DataSpec.Builder()
                                .setUri(uri)
                                .setLength(2 * 1024 * 1024L)
                                .build()
                            val cacheWriter = CacheWriter(
                                cacheDataSource,
                                dataSpec,
                                ByteArray(32768),
                                null
                            )
                            cacheWriter.cache()
                            Log.d("PRELOAD", "Pre-cached upcoming track [${index + 1}/${pendingUris.size}]: $uri")
                        }
                    } catch (e: Exception) {
                        Log.d("PRELOAD", "Pre-cache exception: ${e.message}")
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                error.printStackTrace()
                Log.e("PLAYER", error.stackTraceToString())

                Toast.makeText(
                    this@ChoraMediaLibraryService,
                    PlaybackException.getErrorCodeName(error.errorCode),
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
            }
        })

        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sessionPlayer = object : androidx.media3.common.ForwardingPlayer(player) {
            private fun isRadioTrack(): Boolean {
                val item = currentMediaItem ?: return false
                return item.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_RADIO_STATION ||
                        item.mediaMetadata.extras?.getBoolean("isRadio") == true
            }

            override fun getAvailableCommands(): Player.Commands {
                val builder = super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                if (!isRadioTrack()) {
                    builder.add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    builder.add(Player.COMMAND_SEEK_BACK)
                    builder.add(Player.COMMAND_SEEK_FORWARD)
                    builder.add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
                }
                return builder.build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                if (command == Player.COMMAND_SEEK_TO_PREVIOUS ||
                    command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ||
                    command == Player.COMMAND_SEEK_TO_NEXT ||
                    command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
                ) {
                    return true
                }
                if (!isRadioTrack() && (
                    command == Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM ||
                    command == Player.COMMAND_SEEK_BACK ||
                    command == Player.COMMAND_SEEK_FORWARD ||
                    command == Player.COMMAND_SEEK_TO_DEFAULT_POSITION
                )) {
                    return true
                }
                return super.isCommandAvailable(command)
            }

            override fun isCurrentMediaItemSeekable(): Boolean {
                if (!isRadioTrack() && (duration > 0 || (currentMediaItem?.mediaMetadata?.durationMs ?: 0L) > 0)) {
                    return true
                }
                return super.isCurrentMediaItemSeekable
            }

            override fun isCurrentMediaItemDynamic(): Boolean {
                if (!isRadioTrack()) {
                    return false
                }
                return super.isCurrentMediaItemDynamic
            }

            override fun getDuration(): Long {
                val baseDuration = super.getDuration()
                if (baseDuration > 0 && baseDuration != C.TIME_UNSET) {
                    return baseDuration
                }
                val metaDuration = currentMediaItem?.mediaMetadata?.durationMs
                if (metaDuration != null && metaDuration > 0) {
                    return metaDuration
                }
                val extraDuration = currentMediaItem?.mediaMetadata?.extras?.getInt("duration", 0)?.times(1000L) ?: 0L
                if (extraDuration > 0) {
                    return extraDuration
                }
                return baseDuration
            }

            override fun seekToPrevious() {
                play()
                if (hasPreviousMediaItem()) {
                    super.seekToPrevious()
                } else {
                    seekTo(0)
                }
            }

            override fun seekToPreviousMediaItem() {
                play()
                if (hasPreviousMediaItem()) {
                    super.seekToPreviousMediaItem()
                } else {
                    seekTo(0)
                }
            }

            override fun seekToNext() {
                play()
                super.seekToNext()
            }

            override fun seekToNextMediaItem() {
                play()
                super.seekToNextMediaItem()
            }

            override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
                setPlayerShuffleMode(shuffleModeEnabled)
                super.setShuffleModeEnabled(shuffleModeEnabled)
            }

            override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
                super.setMediaItems(mediaItems)
                if (player.shuffleModeEnabled && isQueuePreShuffled && mediaItems.size > 0) {
                    player.setShuffleOrder(androidx.media3.exoplayer.source.ShuffleOrder.UnshuffledShuffleOrder(mediaItems.size))
                }
            }

            override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
                super.setMediaItems(mediaItems, resetPosition)
                if (player.shuffleModeEnabled && isQueuePreShuffled && mediaItems.size > 0) {
                    player.setShuffleOrder(androidx.media3.exoplayer.source.ShuffleOrder.UnshuffledShuffleOrder(mediaItems.size))
                }
            }

            override fun setMediaItems(
                mediaItems: MutableList<MediaItem>,
                startIndex: Int,
                startPositionMs: Long
            ) {
                super.setMediaItems(mediaItems, startIndex, startPositionMs)
                if (player.shuffleModeEnabled && isQueuePreShuffled && mediaItems.size > 0) {
                    player.setShuffleOrder(androidx.media3.exoplayer.source.ShuffleOrder.UnshuffledShuffleOrder(mediaItems.size))
                }
            }
        }

        session = MediaLibrarySession.Builder(this, sessionPlayer, LibrarySessionCallback())
            .setId("AutoSession")
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        scrobbleJob = serviceMainScope.launch {
            while (isActive) {
                val duration = player.duration
                val mediaItem = player.currentMediaItem

                if (duration > 0 && !playerScrobbled) {
                    val currentPosition = player.currentPosition
                    val progress = (currentPosition * 100 / duration).toInt()
                    val scrobblePercentage = playbackSettingsManager.scrobblePercentFlow.first() * 10

                    if (progress >= scrobblePercentage) {
                        playerScrobbled = true
                        val navId = mediaItem?.mediaMetadata?.extras?.getString("navidromeID") ?: ""
                        val mediaId = mediaItem?.mediaId ?: ""
                        if (navId.startsWith("Local") || mediaId.startsWith("content://") || mediaId.startsWith("file://")) {
                            serviceIOScope.launch {
                                songRepository.scrobbleSong(navId.ifEmpty { mediaId }, true)
                            }
                        } else if (NavidromeManager.checkActiveServers() &&
                            mediaItem?.mediaMetadata?.mediaType != MediaMetadata.MEDIA_TYPE_RADIO_STATION
                        ) {
                            serviceIOScope.launch {
                                songRepository.scrobbleSong(navId, true)
                            }
                        }
                    }
                }
                delay(1000)
            }
        }

        serviceMainScope.launch {
            transcodeManager.transcodingConfigChangesFlow
                .distinctUntilChanged()
                .collect {
                    if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                        if (player.currentTimeline.isEmpty.not()) {
                            updateTranscodingDuringPlayback()
                        }
                    }
                }
        }

        Log.d("AA", "Initialized MediaLibraryService.")
    }

    private fun buildCustomLayout(): ImmutableList<CommandButton> {
        val isShuffle = if (::player.isInitialized) player.shuffleModeEnabled else false
        val shuffleIcon = if (isShuffle) R.drawable.round_shuffle_on_28 else R.drawable.round_shuffle_off_28
        val shuffleButton = CommandButton.Builder()
            .setDisplayName(if (isShuffle) "Shuffle: On" else "Shuffle: Off")
            .setIconResId(shuffleIcon)
            .setSessionCommand(SessionCommand(CUSTOM_ACTION_SHUFFLE, Bundle.EMPTY))
            .setEnabled(true)
            .build()

        val repeatMode = if (::player.isInitialized) player.repeatMode else Player.REPEAT_MODE_OFF
        val repeatIcon = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.rounded_repeat1_on_24
            Player.REPEAT_MODE_ALL -> R.drawable.rounded_repeat_on_24
            else -> R.drawable.rounded_repeat_off_24
        }
        val repeatButton = CommandButton.Builder()
            .setDisplayName(
                when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repeat: One"
                    Player.REPEAT_MODE_ALL -> "Repeat: All"
                    else -> "Repeat: Off"
                }
            )
            .setIconResId(repeatIcon)
            .setSessionCommand(SessionCommand(CUSTOM_ACTION_REPEAT, Bundle.EMPTY))
            .setEnabled(true)
            .build()

        return ImmutableList.of(shuffleButton, repeatButton)
    }

    private fun updateCustomLayout() {
        session?.setCustomLayout(buildCustomLayout())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return session
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        private var androidAutoDefaultSourceApplied = false

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val superResult = super.onConnect(session, controller)
            val sessionCommands = superResult.availableSessionCommands.buildUpon()
                .add(SessionCommand(CUSTOM_ACTION_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(CUSTOM_ACTION_REPEAT, Bundle.EMPTY))
                .add(SessionCommand(CUSTOM_ACTION_CYCLE_SOURCE, Bundle.EMPTY))
                .add(SessionCommand(CUSTOM_ACTION_REFRESH, Bundle.EMPTY))
                .build()

            val playerCommands = session.player.availableCommands.buildUpon()
                .add(Player.COMMAND_SET_SHUFFLE_MODE)
                .add(Player.COMMAND_SET_REPEAT_MODE)
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_PREPARE)
                .add(Player.COMMAND_STOP)
                .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                .add(Player.COMMAND_SET_MEDIA_ITEM)
                .add(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .setCustomLayout(buildCustomLayout())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CUSTOM_ACTION_SHUFFLE -> {
                    setPlayerShuffleMode(!player.shuffleModeEnabled)
                    updateCustomLayout()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CUSTOM_ACTION_REPEAT -> {
                    player.repeatMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    updateCustomLayout()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CUSTOM_ACTION_CYCLE_SOURCE -> {
                    serviceIOScope.launch {
                        cycleToNextSource()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CUSTOM_ACTION_REFRESH -> {
                    serviceIOScope.launch {
                        refreshAllAndroidAutoScreens()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        @Suppress("DEPRECATION")
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: @Player.Command Int
        ): Int {
            if (playerCommand == Player.COMMAND_SEEK_TO_NEXT ||
                playerCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS ||
                playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
            ) {
                player.play()
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            serviceIOScope.launch {
                Log.d("AA", "onPostConnect controller: ${controller.packageName}")
                val isCar = controller.packageName.contains("gearhead") || controller.packageName.contains("auto")
                if (isCar && !androidAutoDefaultSourceApplied) {
                    androidAutoDefaultSourceApplied = true
                    val defaultSrc = com.craftworks.music.managers.MediaSourceManager.defaultSource.value
                    if (com.craftworks.music.managers.MediaSourceManager.selectedSource.value != defaultSrc) {
                        withContext(Dispatchers.Main) {
                            com.craftworks.music.managers.MediaSourceManager.setSelectedSource(defaultSrc)
                        }
                    }
                }

                aHomeScreenItems = emptyList()
                aPlaylistScreenItems = emptyList()
                aAlbumScreenItems = emptyList()
                aSongScreenItems = emptyList()
                aArtistsScreenItems = emptyList()

                getHomeScreenItems()
                getPlaylistItems()

                withContext(Dispatchers.Main) {
                    updateCustomLayout()
                }

                if (aHomeScreenItems.isNotEmpty()) {
                    this@ChoraMediaLibraryService.session?.notifyChildrenChanged(
                        "nodeHOME",
                        aHomeScreenItems.size,
                        null
                    )
                }

                if (aPlaylistScreenItems.isNotEmpty()) {
                    this@ChoraMediaLibraryService.session?.notifyChildrenChanged(
                        "nodePLAYLISTS",
                        aPlaylistScreenItems.size,
                        null
                    )
                }
            }
            super.onPostConnect(session, controller)
        }

        override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
            val isCar = controller.packageName.contains("gearhead") || controller.packageName.contains("auto")
            if (isCar) {
                androidAutoDefaultSourceApplied = false
            }
            super.onDisconnected(session, controller)
        }

        /*
        @OptIn(UnstableApi::class)
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaItemsWithStartPosition> {
            // We need to use URI from requestMetaData because of https://github.com/androidx/media/issues/282
            val updatedStartIndex =
                SongHelper.currentTracklist.indexOfFirst { it.mediaId == mediaItems[0].mediaId }

            val currentTracklist =
                if (updatedStartIndex != -1) {
                    SongHelper.currentTracklist
                } else {
                    SongHelper.currentTracklist = mediaItems.toMutableList()
                    mediaItems
                }

            val connectivityManager =
                this@ChoraMediaLibraryService.baseContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

            val networkCapabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

            val bitrate: String = runBlocking {
                when {
                    networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> {
                        Log.d("NetworkCheck", "Device is on Wi-Fi")
                        playbackSettingsManager.wifiTranscodingBitrateFlow.first()
                    }
                    networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
                        Log.d("NetworkCheck", "Device is on Mobile Data")
                        playbackSettingsManager.mobileDataTranscodingBitrateFlow.first()
                    }
                    else -> {
                        Log.d("NetworkCheck", "Device is on another network type")
                        playbackSettingsManager.wifiTranscodingBitrateFlow.first()
                    }
                }
            }

            val bitrateOptions = if (bitrate != "No Transcoding" && bitrate.isNotEmpty()) {
                runBlocking {
                    "&maxBitRate=$bitrate&format=${playbackSettingsManager.transcodingFormatFlow.first()}"
                }
            } else {
                ""
            }

            val result = MediaItemsWithStartPosition(
                currentTracklist.map { mediaItem ->
                    MediaItem.Builder()
                        .setMediaId(mediaItem.mediaId)
                        .setMediaMetadata(mediaItem.mediaMetadata)
                        .setUri(mediaItem.mediaId + if (mediaItem.mediaMetadata.extras?.getString("navidromeID")?.startsWith("Local_") == false) bitrateOptions else "")
                        .build()
                },
                if (updatedStartIndex != -1) updatedStartIndex else startIndex,
                startPositionMs
            )

            return Futures.immediateFuture(result)
        }
        */

        @OptIn(UnstableApi::class)
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            // Android Auto uses the legacy MediaController, so we need to do some very weird hacks to make it work nicely.
            val settable = SettableFuture.create<List<MediaItem>>()
            serviceIOScope.launch {
                try {
                    if (mediaItems.size == 1) {
                        val requestedId = mediaItems[0].mediaId
                        if (requestedId == "action_refresh_library") {
                            refreshAllAndroidAutoScreens()
                            settable.set(emptyList())
                            return@launch
                        }

                        if (requestedId.startsWith("action_select_source_")) {
                            switchSourceFromId(requestedId)
                            refreshAllAndroidAutoScreens()
                            settable.set(emptyList())
                            return@launch
                        }

                        if (requestedId.startsWith("action_play_playlist_")) {
                            val playlistId = requestedId.removePrefix("action_play_playlist_")
                            val playlistSongs = playlistRepository.getPlaylistSongs(playlistId)
                            val validSongs = playlistSongs.filter { isValidPlayableAudioItem(it) }
                            if (validSongs.isNotEmpty()) {
                                val queue = validSongs.map { it.withHighResArtwork().buildUpon().setUri(it.mediaId).build() }
                                withContext(Dispatchers.Main) {
                                    player.shuffleModeEnabled = false
                                    player.repeatMode = Player.REPEAT_MODE_ALL
                                }
                                settable.set(queue)
                            } else {
                                settable.set(emptyList())
                            }
                            return@launch
                        }

                        if (requestedId.startsWith("action_shuffle_playlist_")) {
                            val playlistId = requestedId.removePrefix("action_shuffle_playlist_")
                            val playlistSongs = playlistRepository.getPlaylistSongs(playlistId)
                            val validSongs = playlistSongs.filter { isValidPlayableAudioItem(it) }
                            if (validSongs.isNotEmpty()) {
                                val isSmartShuffle = playbackSettingsManager.smartShuffleFlow.first()
                                val prepared = if (isSmartShuffle) SmartShuffleHelper.smartShuffle(validSongs) else validSongs.shuffled()
                                val shuffled = prepared.map { it.withHighResArtwork().buildUpon().setUri(it.mediaId).build() }
                                withContext(Dispatchers.Main) {
                                    isQueuePreShuffled = true
                                    player.shuffleModeEnabled = true
                                    player.repeatMode = Player.REPEAT_MODE_ALL
                                }
                                settable.set(shuffled)
                            } else {
                                settable.set(emptyList())
                            }
                            return@launch
                        }

                        if (requestedId.startsWith("action_play_album_")) {
                            val albumId = requestedId.removePrefix("action_play_album_")
                            val albumSongs = albumRepository.getAlbum(albumId)
                            val validSongs = (albumSongs ?: emptyList()).filter { isValidPlayableAudioItem(it) }
                            if (validSongs.isNotEmpty()) {
                                val queue = validSongs.map { it.withHighResArtwork().buildUpon().setUri(it.mediaId).build() }
                                withContext(Dispatchers.Main) {
                                    player.shuffleModeEnabled = false
                                    player.repeatMode = Player.REPEAT_MODE_ALL
                                }
                                settable.set(queue)
                            } else {
                                settable.set(emptyList())
                            }
                            return@launch
                        }

                        if (requestedId.startsWith("action_shuffle_album_")) {
                            val albumId = requestedId.removePrefix("action_shuffle_album_")
                            val albumSongs = albumRepository.getAlbum(albumId)
                            val validSongs = (albumSongs ?: emptyList()).filter { isValidPlayableAudioItem(it) }
                            if (validSongs.isNotEmpty()) {
                                val isSmartShuffle = playbackSettingsManager.smartShuffleFlow.first()
                                val prepared = if (isSmartShuffle) SmartShuffleHelper.smartShuffle(validSongs) else validSongs.shuffled()
                                val shuffled = prepared.map { it.withHighResArtwork().buildUpon().setUri(it.mediaId).build() }
                                withContext(Dispatchers.Main) {
                                    isQueuePreShuffled = true
                                    player.shuffleModeEnabled = true
                                    player.repeatMode = Player.REPEAT_MODE_ALL
                                }
                                settable.set(shuffled)
                            } else {
                                settable.set(emptyList())
                            }
                            return@launch
                        }

                        if (requestedId.startsWith("action_play_artist_") || requestedId.startsWith("action_shuffle_artist_")) {
                            val isShuffle = requestedId.startsWith("action_shuffle_artist_")
                            val artistId = if (isShuffle) requestedId.removePrefix("action_shuffle_artist_") else requestedId.removePrefix("action_play_artist_")
                            val albums = artistRepository.getArtistAlbums(artistId)
                            val allSongs = albums.flatMap { albumItem ->
                                val albumId = albumItem.mediaMetadata.extras?.getString("navidromeID") ?: albumItem.mediaId
                                val albumTracks = albumRepository.getAlbum(albumId) ?: emptyList()
                                albumTracks.filter { isValidPlayableAudioItem(it) }
                            }
                            if (allSongs.isNotEmpty()) {
                                val isSmartShuffle = if (isShuffle) playbackSettingsManager.smartShuffleFlow.first() else false
                                val prepared = if (isShuffle) {
                                    if (isSmartShuffle) SmartShuffleHelper.smartShuffle(allSongs) else allSongs.shuffled()
                                } else allSongs
                                val queue = prepared.map { it.withHighResArtwork().buildUpon().setUri(it.mediaId).build() }
                                withContext(Dispatchers.Main) {
                                    if (isShuffle) {
                                        isQueuePreShuffled = true
                                        player.shuffleModeEnabled = true
                                    } else {
                                        isQueuePreShuffled = false
                                        player.shuffleModeEnabled = false
                                    }
                                    player.repeatMode = Player.REPEAT_MODE_ALL
                                }
                                settable.set(queue)
                            } else {
                                settable.set(emptyList())
                            }
                            return@launch
                        }

                        if (requestedId == "action_play_all_songs") {
                            val rawSongs = songRepository.getSongs(songCount = 5000)
                            val allSongs = rawSongs.filter { isValidPlayableAudioItem(it) }
                            if (allSongs.isNotEmpty()) {
                                val queue = allSongs.map { it.withHighResArtwork().buildUpon().setUri(it.mediaId).build() }
                                withContext(Dispatchers.Main) {
                                    player.shuffleModeEnabled = false
                                    player.repeatMode = Player.REPEAT_MODE_ALL
                                }
                                settable.set(queue)
                            } else {
                                settable.set(emptyList())
                            }
                            return@launch
                        }

                        if (requestedId == "action_shuffle_all_songs") {
                            val rawSongs = songRepository.getSongs(songCount = 5000)
                            val allSongs = rawSongs.filter { isValidPlayableAudioItem(it) }
                            if (allSongs.isNotEmpty()) {
                                val isSmartShuffle = playbackSettingsManager.smartShuffleFlow.first()
                                val shuffled = if (isSmartShuffle) SmartShuffleHelper.smartShuffle(allSongs) else allSongs.shuffled()
                                val queue = shuffled.map { it.withHighResArtwork().buildUpon().setUri(it.mediaId).build() }
                                withContext(Dispatchers.Main) {
                                    isQueuePreShuffled = true
                                    player.shuffleModeEnabled = true
                                    player.repeatMode = Player.REPEAT_MODE_ALL
                                }
                                settable.set(queue)
                            } else {
                                settable.set(emptyList())
                            }
                            return@launch
                        }

                        // Try to find the full item in the last browsed folder
                        val fullItem = aFolderSongs.find { it.mediaId == requestedId }
                        if (fullItem != null) {
                            val songItems = aFolderSongs.filter { !it.mediaId.startsWith("action_shuffle_") && !it.mediaId.startsWith("action_play_") }
                            val actualStartIndex = songItems.indexOfFirst { it.mediaId == requestedId }.coerceAtLeast(0)
                            val folderQueue = songItems.subList(actualStartIndex, songItems.size).map { item ->
                                item.buildUpon()
                                    .setUri(item.mediaId)
                                    .build()
                            }
                            settable.set(folderQueue)
                            return@launch
                        }

                        // Try to find the song in the songs tab
                        val songItem = aSongScreenItems.find { it.mediaId == requestedId }
                        if (songItem != null) {
                            val pureSongs = aSongScreenItems.filter { !it.mediaId.startsWith("action_shuffle_") }
                            val songIndex = pureSongs.indexOfFirst { it.mediaId == requestedId }.coerceAtLeast(0)
                            val songQueue = pureSongs.subList(songIndex, pureSongs.size).map { item ->
                                item.buildUpon()
                                    .setUri(item.mediaId)
                                    .build()
                            }
                            settable.set(songQueue)
                            return@launch
                        }

                        // Check if it's an album that needs its songs loaded
                        val albumSongs = albumRepository.getAlbum(requestedId)
                        if (!albumSongs.isNullOrEmpty()) {
                            val songsList = if (albumSongs.size > 1) albumSongs.subList(1, albumSongs.size) else albumSongs
                            val queue = songsList.map { it.buildUpon().setUri(it.mediaId).build() }
                            settable.set(queue)
                            return@launch
                        }

                        // Check if it's a playlist
                        val playlistSongs = playlistRepository.getPlaylistSongs(requestedId)
                        if (playlistSongs.isNotEmpty()) {
                            val queue = playlistSongs.map { it.buildUpon().setUri(it.mediaId).build() }
                            settable.set(queue)
                            return@launch
                        }

                        // Not found in folder, check other cached items
                        val cachedItem = aPlaylistScreenItems.find { it.mediaId == requestedId }
                            ?: aSongScreenItems.find { it.mediaId == requestedId }
                            ?: aRadioScreenItems.find { it.mediaId == requestedId }
                            ?: aAlbumScreenItems.find { it.mediaId == requestedId }
                            ?: aArtistsScreenItems.find { it.mediaId == requestedId }

                        if (cachedItem != null) {
                            val enrichedItem = cachedItem.buildUpon()
                                .setUri(cachedItem.mediaId)
                                .build()
                            settable.set(listOf(enrichedItem))
                            return@launch
                        }
                    }

                    val updatedMediaItems = mediaItems.map { item ->
                        item.buildUpon()
                            .setUri(item.mediaId)
                            .build()
                    }
                    settable.set(updatedMediaItems)
                } catch (e: Exception) {
                    Log.e("AA", "Error in onAddMediaItems", e)
                    settable.set(mediaItems)
                }
            }
            return settable
        }

        override fun onSetRating(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            rating: Rating
        ): ListenableFuture<SessionResult> {
            val currentItem = player.currentMediaItem
                ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))

            val navidromeID = currentItem.mediaMetadata.extras?.getString("navidromeID") ?: ""
            val newRating = (rating as? StarRating)?.starRating
                ?.takeIf { it >= 0f }   // -1f means "unset"
                ?.toInt() ?: return super.onSetRating(session, controller, rating)

            serviceIOScope.launch {
                songRepository.setSongRating(navidromeID, newRating)
            }

            val updatedExtras = Bundle(currentItem.mediaMetadata.extras ?: Bundle()).apply {
                putInt("rating", newRating)
            }
            val updatedItem = currentItem.buildUpon()
                .setMediaMetadata(
                    currentItem.mediaMetadata.buildUpon()
                        .setExtras(updatedExtras)
                        .setUserRating(rating)
                        .build()
                )
                .build()

            val index = player.currentMediaItemIndex
            if (player.currentMediaItem?.mediaMetadata?.extras?.getString("navidromeID") == navidromeID) {
                player.replaceMediaItem(index, updatedItem)
            }
            return super.onSetRating(session, controller, rating)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        @OptIn(UnstableApi::class)
        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val settable = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceIOScope.launch {
                try {
                    com.craftworks.music.managers.FileLogger.log("AA_SERVICE", "onGetChildren requested for parentId: $parentId, page: $page, pageSize: $pageSize, package: ${browser.packageName}")
                    val items = when (parentId) {
                        "nodeROOT" -> rootHierarchy
                        "nodeHOME" -> getHomeScreenItems()
                        "nodeALBUMS" -> getAlbumScreenItems()
                        "nodeSONGS" -> getSongItems()
                        "nodeARTISTS" -> getArtistScreenItems()
                        "nodeRADIOS" -> getRadioItems()
                        "nodePLAYLISTS" -> getPlaylistItems()
                        "nodeSOURCES" -> getSourceItems()
                        "action_refresh_library" -> {
                            refreshAllAndroidAutoScreens()
                            getSourceItems()
                        }
                        else -> {
                            if (parentId.startsWith("action_select_source_")) {
                                switchSourceFromId(parentId)
                                refreshAllAndroidAutoScreens()
                                val currentSource = com.craftworks.music.managers.MediaSourceManager.selectedSource.value
                                val sourceName = currentSource.displayName
                                val iconRes = when (currentSource) {
                                    com.craftworks.music.managers.MediaSource.NAVIDROME -> R.drawable.s_m_navidrome
                                    com.craftworks.music.managers.MediaSource.EMBY -> R.drawable.s_m_emby
                                    com.craftworks.music.managers.MediaSource.LOCAL -> R.drawable.s_m_local_filled
                                    else -> R.drawable.s_m_media_providers
                                }

                                val confirmItem = MediaItem.Builder()
                                    .setMediaId("info_source_switched")
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle("✓ Switched to $sourceName")
                                            .setSubtitle("Tap [←] at top left to return to main tabs")
                                            .setArtworkUri(
                                                android.net.Uri.parse("android.resource://${this@ChoraMediaLibraryService.packageName}/$iconRes")
                                            )
                                            .setIsBrowsable(false)
                                            .setIsPlayable(false)
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                            .build()
                                    )
                                    .build()

                                val items = mutableListOf<MediaItem>()
                                items.add(confirmItem)
                                items.addAll(getHomeScreenItems())
                                items
                            } else {
                                val mediaItem =
                                    aHomeScreenItems.find { it.mediaId == parentId }
                                        ?: aPlaylistScreenItems.find { it.mediaId == parentId }
                                        ?: aAlbumScreenItems.find { it.mediaId == parentId }
                                        ?: aSongScreenItems.find { it.mediaId == parentId }
                                        ?: aArtistsScreenItems.find { it.mediaId == parentId }
                                getFolderItems(
                                    parentId,
                                    mediaItem?.mediaMetadata?.mediaType
                                        ?: MediaMetadata.MEDIA_TYPE_ALBUM
                                )
                            }
                        }
                    }
                    val isGridParent = parentId == "nodeALBUMS" ||
                            parentId == "nodeARTISTS" ||
                            parentId == "nodeHOME"
                    val returnParams = if (isGridParent) {
                        val b = params?.extras?.let { Bundle(it) } ?: Bundle()
                        b.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
                        b.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
                        LibraryParams.Builder().setExtras(b).build()
                    } else {
                        val b = params?.extras?.let { Bundle(it) } ?: Bundle()
                        b.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
                        b.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
                        LibraryParams.Builder().setExtras(b).build()
                    }
                    com.craftworks.music.managers.FileLogger.log("AA_SERVICE", "onGetChildren returning ${items.size} items for $parentId: ${items.map { "${it.mediaId}->${it.mediaMetadata.title}" }}")
                    settable.set(LibraryResult.ofItemList(items, returnParams))
                } catch (e: Exception) {
                    com.craftworks.music.managers.FileLogger.log("AA_SERVICE", "Error in onGetChildren for $parentId: ${e.message}")
                    Log.e("AA", "Error in onGetChildren for parentId: $parentId", e)
                    settable.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                }
            }
            return settable
        }


        @OptIn(UnstableApi::class)
        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val mediaItem = aFolderSongs.find { it.mediaId == mediaId }
                ?: aSongScreenItems.find { it.mediaId == mediaId }
                ?: aPlaylistScreenItems.find { it.mediaId == mediaId }
                ?: aRadioScreenItems.find { it.mediaId == mediaId }
                ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))

            return Futures.immediateFuture(
                LibraryResult.ofItem(
                    mediaItem,
                    LibraryParams.Builder().build()
                )
            )
        }

        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            session.notifyChildrenChanged(
                parentId,
                when (parentId) {
                    "nodeROOT" -> rootHierarchy.size
                    "nodeHOME" -> aHomeScreenItems.size
                    "nodeALBUMS" -> aAlbumScreenItems.size
                    "nodeSONGS" -> aSongScreenItems.size
                    "nodeARTISTS" -> aArtistsScreenItems.size
                    "nodeRADIOS" -> aRadioScreenItems.size
                    "nodePLAYLISTS" -> aPlaylistScreenItems.size
                    "nodeSOURCES" -> getSourceItems().size
                    else -> 0
                },
                params
            )

            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isToStream: Boolean
        ): ListenableFuture<MediaItemsWithStartPosition> {
            val settable = SettableFuture.create<MediaItemsWithStartPosition>()
            serviceMainScope.launch {
                Log.d("RESUMPTION", "Getting onPlaybackResumption")
                try {
                    val resumptionData = withTimeoutOrNull(3000L) {
                        LocalDataSettingsManager(applicationContext).playbackResumptionPlaylistWithStartPosition.firstOrNull()
                    }
                    if (resumptionData != null && resumptionData.mediaItems.isNotEmpty()) {
                        Log.d("RESUMPTION", "Got mediaitems: ${resumptionData.mediaItems.size}")
                        settable.set(resumptionData)
                        withContext(Dispatchers.Main) {
                            player.setMediaItems(resumptionData.mediaItems, resumptionData.startIndex, resumptionData.startPositionMs)
                            player.prepare()
                        }
                    } else {
                        Log.d("RESUMPTION", "No resumption items available")
                        settable.setException(UnsupportedOperationException("No resumption items available"))
                    }
                } catch (e: Exception) {
                    Log.e("RESUMPTION", "Error onPlaybackResumption", e)
                    settable.setException(e)
                }
            }
            return settable
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val settable = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceIOScope.launch {
                try {
                    val searchSongs = songRepository.getSongs(query)
                    settable.set(
                        LibraryResult.ofItemList(
                            searchSongs,
                            params ?: LibraryParams.Builder().build()
                        )
                    )
                } catch (e: Exception) {
                    Log.e("AA", "Error in onGetSearchResult for query: $query", e)
                    settable.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                }
            }
            return settable
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            val settable = SettableFuture.create<LibraryResult<Void>>()
            serviceIOScope.launch {
                try {
                    Log.d("AA", "onSearch: $query")
                    val songCount = songRepository.getSongs(query).size
                    val albumCount = albumRepository.searchAlbum(query).size
                    val radioCount = radioRepository.getRadios().map { it.toMediaItem() }.fastFilter {
                        it.mediaMetadata.station?.contains(
                            query,
                            ignoreCase = true
                        ) ?: false
                    }.size
                    val playlistCount = playlistRepository.getPlaylists().fastFilter {
                        it.mediaMetadata.title?.contains(
                            query,
                            ignoreCase = true
                        ) == true
                    }.size
                    val total = songCount + albumCount + radioCount + playlistCount

                    session.notifySearchResultChanged(
                        browser,
                        query,
                        total,
                        params ?: LibraryParams.Builder().build()
                    )
                    settable.set(LibraryResult.ofVoid())
                } catch (e: Exception) {
                    Log.e("AA", "Error in onSearch for query: $query", e)
                    settable.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                }
            }
            return settable
        }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel() // Cancel any previously running timer

        if (minutes <= 0) {
            Log.d("SLEEPTIMER", "Sleep timer cancelled.")
            _sleepTimerRemainingTime.value = 0
            return
        }

        Log.d("SLEEPTIMER", "Sleep timer set for $minutes minutes.")

        sleepTimerJob = serviceMainScope.launch {
            var timeRemaining = minutes
            _sleepTimerRemainingTime.value = timeRemaining

            while (timeRemaining > 0) {
                delay(60 * 1000L)
                timeRemaining--
                _sleepTimerRemainingTime.value = timeRemaining
            }

            if (::player.isInitialized && player.isPlaying) {
                player.stop()
                Log.d("SLEEPTIMER", "Timer finished. Playback stopped.")
            }
        }
    }

    fun setPlayerShuffleMode(enabled: Boolean) {
        if (!::player.isInitialized) return
        if (enabled) {
            val total = player.mediaItemCount
            val currentIndex = player.currentMediaItemIndex
            if (total > 1) {
                val safeCurrent = currentIndex.coerceIn(0, total - 1)
                val pastAndCurrent = (0..safeCurrent).toList()
                val remaining = ((safeCurrent + 1) until total).shuffled()
                val order = (pastAndCurrent + remaining).toIntArray()
                player.setShuffleOrder(androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(order, System.currentTimeMillis()))
            }
        } else {
            player.setShuffleOrder(androidx.media3.exoplayer.source.ShuffleOrder.UnshuffledShuffleOrder(player.mediaItemCount))
        }
        player.shuffleModeEnabled = enabled
    }

    private fun updateTranscodingDuringPlayback() {
        val currentWindowIndex = player.currentMediaItemIndex
        val currentPlaybackPosition = player.currentPosition
        val wasPlaying = player.isPlaying

        val currentQueue = mutableListOf<MediaItem>()
        for (i in 0 until player.mediaItemCount) {
            currentQueue.add(player.getMediaItemAt(i))
        }

        player.setMediaItems(currentQueue, currentWindowIndex, currentPlaybackPosition)

        player.prepare()

        if (wasPlaying) {
            player.play()
        }
    }

    override fun onDestroy() {
        saveState()
        session?.release()
        if (::player.isInitialized) {
            player.release()
        }
        scrobbleJob?.cancel()
        sleepTimerJob?.cancel()
        preloadJob?.cancel()
        serviceMainScope.cancel()
        serviceIOScope.cancel()
        instance = null
        super.onDestroy()
    }

    fun saveState() {
        if (!::player.isInitialized) return
        runBlocking {
            Log.d(
                "AA",
                "Saving state! Playlist: ${List(player.mediaItemCount) { i -> player.getMediaItemAt(i) }.map { it.mediaMetadata.title }}, current index: ${player.currentMediaItemIndex}, current position: ${player.currentPosition}"
            )

            LocalDataSettingsManager(applicationContext).setPlaybackResumption(
                List(player.mediaItemCount) { i ->
                    player.getMediaItemAt(i)
                },
                player.currentMediaItemIndex,
                player.currentPosition
            )
        }
    }

    private fun MediaItem.withHighResArtwork(): MediaItem {
        val artUri = this.mediaMetadata.artworkUri?.toString() ?: return this
        val highResUri = if (artUri.contains("size=")) artUri.replace(Regex("size=\\d+"), "size=800") else artUri
        return this.buildUpon()
            .setMediaMetadata(
                this.mediaMetadata.buildUpon()
                    .setArtworkUri(highResUri.toUri())
                    .build()
            )
            .build()
    }

    private fun MediaItem.withListContentStyle(): MediaItem {
        val currentExtras = this.mediaMetadata.extras ?: Bundle()
        val newExtras = Bundle(currentExtras).apply {
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
        }
        return this.buildUpon()
            .setMediaMetadata(
                this.mediaMetadata.buildUpon()
                    .setExtras(newExtras)
                    .build()
            )
            .build()
    }

    private fun isValidPlayableAudioItem(item: MediaItem): Boolean {
        if (item.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_ALBUM ||
            item.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS ||
            item.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_ARTIST ||
            item.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS ||
            item.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
        ) {
            return false
        }
        val mediaId = item.mediaId
        return mediaId.startsWith("http://") ||
                mediaId.startsWith("https://") ||
                mediaId.startsWith("content://") ||
                mediaId.startsWith("file://")
    }

    //region getChildren
    private suspend fun getHomeScreenItems(): List<MediaItem> = homeMutex.withLock {
        Log.d("AA", "GETTING ANDROID AUTO SCREEN ITEMS")
        if (aHomeScreenItems.isEmpty()) {
            val items = mutableListOf<MediaItem>()
            try {
                coroutineScope {
                    val homeItems = appearanceSettingsManager.homeItemsItemsFlow.first()
                    for (item in homeItems) {
                        if (!item.enabled) continue
                        when (item.key) {
                            "playlists" -> {
                                val playlists = playlistRepository.getPlaylists()
                                playlists.take(6).forEach { playlist ->
                                    items.add(
                                        playlist.withHighResArtwork().apply {
                                            this.mediaMetadata.extras?.putString(
                                                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                                                this@ChoraMediaLibraryService.getString(R.string.playlists)
                                            )
                                        }
                                    )
                                }
                            }
                            "recently_played" -> {
                                val recent = albumRepository.getAlbums("recent", 6)
                                recent.forEach { album ->
                                    items.add(
                                        album.withHighResArtwork().apply {
                                            this.mediaMetadata.extras?.putString(
                                                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                                                this@ChoraMediaLibraryService.getString(R.string.recently_played)
                                            )
                                        }
                                    )
                                }
                            }
                            "recently_added" -> {
                                val newest = albumRepository.getAlbums("newest", 6)
                                newest.forEach { album ->
                                    items.add(
                                        album.withHighResArtwork().apply {
                                            this.mediaMetadata.extras?.putString(
                                                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                                                this@ChoraMediaLibraryService.getString(R.string.recently_added)
                                            )
                                        }
                                    )
                                }
                            }
                            "most_played" -> {
                                val frequent = albumRepository.getAlbums("frequent", 6)
                                frequent.forEach { album ->
                                    items.add(
                                        album.withHighResArtwork().apply {
                                            this.mediaMetadata.extras?.putString(
                                                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                                                this@ChoraMediaLibraryService.getString(R.string.most_played)
                                            )
                                        }
                                    )
                                }
                            }
                            "random_songs" -> {
                                val random = albumRepository.getAlbums("random", 6)
                                random.forEach { album ->
                                    items.add(
                                        album.withHighResArtwork().apply {
                                            this.mediaMetadata.extras?.putString(
                                                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                                                this@ChoraMediaLibraryService.getString(R.string.random_songs)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AA", "Error loading home screen items", e)
            }

            if (items.isEmpty()) {
                try {
                    val fallbackAlbums = albumRepository.getAlbums("alphabeticalByName", 12)
                    fallbackAlbums.forEach { album ->
                        items.add(album.withHighResArtwork())
                    }
                } catch (e: Exception) {
                    Log.e("AA", "Error loading fallback albums for home screen", e)
                }
            }

            if (items.isEmpty()) {
                items.add(
                    MediaItem.Builder()
                        .setMediaId("node_no_media")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle("No Media Available")
                                .setArtist("Please set up a music provider in the app")
                                .setIsBrowsable(false)
                                .setIsPlayable(false)
                                .build()
                        )
                        .build()
                )
            }
            aHomeScreenItems = items
        }
        return@withLock aHomeScreenItems
    }

    private fun getSourceItems(): List<MediaItem> {
        val current = com.craftworks.music.managers.MediaSourceManager.selectedSource.value
        val availableSources = com.craftworks.music.managers.MediaSourceManager.getAvailableSources()
        val navidromeServers = com.craftworks.music.managers.NavidromeManager.getAllServers()
        val currentNavidromeId = com.craftworks.music.managers.NavidromeManager.currentServerId.value
        val embyServers = com.craftworks.music.managers.EmbyJellyfinManager.getAllServers()
        val currentEmbyId = com.craftworks.music.managers.EmbyJellyfinManager.currentServerId.value

        val items = mutableListOf<MediaItem>()

        items.add(
            MediaItem.Builder()
                .setMediaId("action_refresh_library")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsPlayable(false)
                        .setIsBrowsable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .setTitle("🔄 Refresh Library")
                        .setSubtitle("Tap to re-sync music catalog")
                        .build()
                )
                .build()
        )

        availableSources.forEach { source ->
            if (source == com.craftworks.music.managers.MediaSource.NAVIDROME && navidromeServers.size > 1) {
                navidromeServers.forEach { server ->
                    val isSelected = (source == current) && (server.id == currentNavidromeId)
                    val prefix = if (isSelected) "✓ " else ""
                    items.add(
                        MediaItem.Builder()
                            .setMediaId("action_select_source_navidrome_${server.id}")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setIsPlayable(false)
                                    .setIsBrowsable(true)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                    .setTitle("${prefix}Navidrome (${server.username})")
                                    .setSubtitle(if (isSelected) "Active Library" else "Tap to switch")
                                    .build()
                            )
                            .build()
                    )
                }
            } else if (source == com.craftworks.music.managers.MediaSource.EMBY && embyServers.size > 1) {
                embyServers.forEach { server ->
                    val isSelected = (source == current) && (server.id == currentEmbyId)
                    val prefix = if (isSelected) "✓ " else ""
                    items.add(
                        MediaItem.Builder()
                            .setMediaId("action_select_source_emby_${server.id}")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setIsPlayable(false)
                                    .setIsBrowsable(true)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                    .setTitle("${prefix}Emby (${server.username})")
                                    .setSubtitle(if (isSelected) "Active Library" else "Tap to switch")
                                    .build()
                            )
                            .build()
                    )
                }
            } else {
                val isSelected = source == current
                val prefix = if (isSelected) "✓ " else ""
                items.add(
                    MediaItem.Builder()
                        .setMediaId("action_select_source_${source.id}")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setIsPlayable(false)
                                .setIsBrowsable(true)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                .setTitle("$prefix${source.displayName}")
                                .setSubtitle(if (isSelected) "Active Library" else "Tap to switch")
                                .build()
                        )
                        .build()
                )
            }
        }
        return items
    }

    private suspend fun getAlbumScreenItems(): List<MediaItem> = albumMutex.withLock {
        Log.d("AA", "GETTING ANDROID AUTO ALBUM SCREEN ITEMS")
        if (aAlbumScreenItems.isEmpty()) {
            try {
                val albums = albumRepository.getAlbums("alphabeticalByName", 100, 0)
                aAlbumScreenItems = albums.map { it.withHighResArtwork().withListContentStyle() }
            } catch (e: Exception) {
                Log.e("AA", "Error loading album screen items", e)
            }
        }
        return@withLock aAlbumScreenItems
    }

    private suspend fun getSongItems(): List<MediaItem> = songMutex.withLock {
        Log.d("AA", "GETTING ANDROID AUTO SONG SCREEN ITEMS")
        if (aSongScreenItems.isEmpty()) {
            val items = mutableListOf<MediaItem>()
            try {
                val songs = songRepository.getSongs(songCount = 2000)
                if (songs.isNotEmpty()) {
                    val shuffleItem = MediaItem.Builder()
                        .setMediaId("action_shuffle_all_songs")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle("Shuffle All")
                                .setArtworkUri(
                                    android.net.Uri.parse("android.resource://${this@ChoraMediaLibraryService.packageName}/${R.drawable.round_shuffle_28}")
                                )
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                .build()
                        )
                        .build()
                    items.add(shuffleItem)
                }
                items.addAll(songs.map { song ->
                    song.withHighResArtwork().buildUpon()
                        .setMediaMetadata(
                            song.mediaMetadata.buildUpon()
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .build()
                        )
                        .build()
                })
            } catch (e: Exception) {
                Log.e("AA", "Error loading song screen items", e)
            }
            aSongScreenItems = items
        }
        return@withLock aSongScreenItems
    }

    private suspend fun getArtistScreenItems(): List<MediaItem> = artistMutex.withLock {
        Log.d("AA", "GETTING ANDROID AUTO ARTIST SCREEN ITEMS")
        if (aArtistsScreenItems.isEmpty()) {
            val items = mutableListOf<MediaItem>()
            try {
                val artists = artistRepository.getArtists()
                artists.forEach {
                    val highResArt = if (it.artistImageUrl?.contains("size=") == true)
                        it.artistImageUrl.replace(Regex("size=\\d+"), "size=800")
                    else
                        it.artistImageUrl
                    items.add(
                        MediaItem.Builder()
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(it.name)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                                    .setArtworkUri(highResArt?.toUri())
                                    .setIsBrowsable(true)
                                    .setIsPlayable(false)
                                    .setExtras(Bundle().apply {
                                        putInt(
                                            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                                            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                                        )
                                        putInt(
                                            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                                            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                                        )
                                    })
                                    .build()
                            )
                            .setMediaId(it.navidromeID)
                            .setUri(it.navidromeID)
                            .build()
                    )
                }
                aArtistsScreenItems = items
            } catch (e: Exception) {
                Log.e("AA", "Error loading artist screen items", e)
            }
        }
        return@withLock aArtistsScreenItems
    }

    private suspend fun getRadioItems(): List<MediaItem> = radioMutex.withLock {
        if (aRadioScreenItems.isEmpty()) {
            try {
                aRadioScreenItems = radioRepository.getRadios().map { radio ->
                    radio.toMediaItem().withHighResArtwork()
                }
            } catch (e: Exception) {
                Log.e("AA", "Error loading radio screen items", e)
            }
        }
        return@withLock aRadioScreenItems
    }

    private suspend fun getPlaylistItems(): List<MediaItem> = playlistMutex.withLock {
        try {
            val playlists = playlistRepository.getPlaylists(true)
            val deduplicated = playlists.distinctBy { 
                it.mediaMetadata.title?.toString()?.trim()?.lowercase() ?: it.mediaId 
            }.map { it.withHighResArtwork() }
            aPlaylistScreenItems = deduplicated
            com.craftworks.music.managers.FileLogger.log("AA_PLAYLISTS", "getPlaylistItems final list size: ${aPlaylistScreenItems.size}, items: ${aPlaylistScreenItems.map { "${it.mediaId}->${it.mediaMetadata.title}" }}")
        } catch (e: Exception) {
            com.craftworks.music.managers.FileLogger.log("AA_PLAYLISTS", "Error in getPlaylistItems: ${e.message}")
            Log.e("AA", "Error loading playlist screen items", e)
        }
        return@withLock aPlaylistScreenItems
    }

    private suspend fun getFolderItems(parentId: String, type: Int): List<MediaItem> = folderMutex.withLock {
        val result = mutableListOf<MediaItem>()
        try {
            when (type) {
                MediaMetadata.MEDIA_TYPE_ALBUM -> {
                    val albumSongs = albumRepository.getAlbum(parentId)
                    val songsList = if (albumSongs != null) {
                        if (albumSongs.size > 1 && albumSongs[0].mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_ALBUM) {
                            albumSongs.subList(1, albumSongs.size)
                        } else {
                            albumSongs
                        }
                    } else emptyList()

                    if (songsList.isNotEmpty()) {
                        val playItem = MediaItem.Builder()
                            .setMediaId("action_play_album_$parentId")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle("Play Album")
                                    .setArtworkUri("android.resource://${packageName}/${R.drawable.round_play_arrow_24}".toUri())
                                    .setIsBrowsable(false)
                                    .setIsPlayable(true)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                    .build()
                            )
                            .build()
                        result.add(playItem)

                        val shuffleItem = MediaItem.Builder()
                            .setMediaId("action_shuffle_album_$parentId")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle("Shuffle Album")
                                    .setArtworkUri("android.resource://${packageName}/${R.drawable.round_shuffle_28}".toUri())
                                    .setIsBrowsable(false)
                                    .setIsPlayable(true)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                    .build()
                            )
                            .build()
                        result.add(shuffleItem)
                        result.addAll(songsList.map { it.withHighResArtwork() })
                    }
                }

                MediaMetadata.MEDIA_TYPE_PLAYLIST -> {
                    val playlistSongs = playlistRepository.getPlaylistSongs(parentId)
                    if (playlistSongs.isNotEmpty()) {
                        val playItem = MediaItem.Builder()
                            .setMediaId("action_play_playlist_$parentId")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle("Play Playlist")
                                    .setArtworkUri("android.resource://${packageName}/${R.drawable.round_play_arrow_24}".toUri())
                                    .setIsBrowsable(false)
                                    .setIsPlayable(true)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                    .build()
                            )
                            .build()
                        result.add(playItem)

                        val shuffleItem = MediaItem.Builder()
                            .setMediaId("action_shuffle_playlist_$parentId")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle("Shuffle Playlist")
                                    .setArtworkUri("android.resource://${packageName}/${R.drawable.round_shuffle_28}".toUri())
                                    .setIsBrowsable(false)
                                    .setIsPlayable(true)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                    .build()
                            )
                            .build()
                        result.add(shuffleItem)
                        result.addAll(playlistSongs.map { it.withHighResArtwork() })
                    }
                }

                MediaMetadata.MEDIA_TYPE_ARTIST -> {
                    val artistAlbums = artistRepository.getArtistAlbums(parentId)
                    if (artistAlbums.isNotEmpty()) {
                        val playItem = MediaItem.Builder()
                            .setMediaId("action_play_artist_$parentId")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle("Play All")
                                    .setArtworkUri("android.resource://${packageName}/${R.drawable.round_play_arrow_24}".toUri())
                                    .setIsBrowsable(false)
                                    .setIsPlayable(true)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                    .build()
                            )
                            .build()
                        result.add(playItem)

                        val shuffleItem = MediaItem.Builder()
                            .setMediaId("action_shuffle_artist_$parentId")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle("Shuffle Artist")
                                    .setArtworkUri("android.resource://${packageName}/${R.drawable.round_shuffle_28}".toUri())
                                    .setIsBrowsable(false)
                                    .setIsPlayable(true)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                    .build()
                            )
                            .build()
                        result.add(shuffleItem)
                    }
                    result.addAll(
                        artistAlbums.map { it.withHighResArtwork().withListContentStyle() }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("AA", "Error loading folder items", e)
        }
        aFolderSongs = result
        return@withLock aFolderSongs
    }

    private suspend fun switchSourceFromId(requestedId: String) {
        if (requestedId.startsWith("action_select_source_navidrome_")) {
            val serverId = requestedId.removePrefix("action_select_source_navidrome_")
            withContext(Dispatchers.Main) {
                com.craftworks.music.managers.NavidromeManager.setCurrentServer(serverId)
                com.craftworks.music.managers.NavidromeManager.setServerEnabled(serverId, true)
                com.craftworks.music.managers.MediaSourceManager.setSelectedSource(com.craftworks.music.managers.MediaSource.NAVIDROME)
            }
        } else if (requestedId.startsWith("action_select_source_emby_")) {
            val serverId = requestedId.removePrefix("action_select_source_emby_")
            withContext(Dispatchers.Main) {
                com.craftworks.music.managers.EmbyJellyfinManager.setCurrentServer(serverId)
                com.craftworks.music.managers.EmbyJellyfinManager.setServerEnabled(serverId, true)
                com.craftworks.music.managers.MediaSourceManager.setSelectedSource(com.craftworks.music.managers.MediaSource.EMBY)
            }
        } else {
            val sourceId = requestedId.removePrefix("action_select_source_")
            val source = com.craftworks.music.managers.MediaSource.fromId(sourceId)
            withContext(Dispatchers.Main) {
                com.craftworks.music.managers.MediaSourceManager.setSelectedSource(source)
            }
        }
    }

    private suspend fun cycleToNextSource() {
        val availableSources = com.craftworks.music.managers.MediaSourceManager.getAvailableSources()
        val currentSource = com.craftworks.music.managers.MediaSourceManager.selectedSource.value

        val navidromeServers = com.craftworks.music.managers.NavidromeManager.getAllServers()
        val currentNavidromeId = com.craftworks.music.managers.NavidromeManager.currentServerId.value

        val embyServers = com.craftworks.music.managers.EmbyJellyfinManager.getAllServers()
        val currentEmbyId = com.craftworks.music.managers.EmbyJellyfinManager.currentServerId.value

        if (currentSource == com.craftworks.music.managers.MediaSource.NAVIDROME && navidromeServers.size > 1) {
            val serverIndex = navidromeServers.indexOfFirst { it.id == currentNavidromeId }
            if (serverIndex != -1 && serverIndex + 1 < navidromeServers.size) {
                val nextServer = navidromeServers[serverIndex + 1]
                withContext(Dispatchers.Main) {
                    com.craftworks.music.managers.NavidromeManager.setCurrentServer(nextServer.id)
                    com.craftworks.music.managers.NavidromeManager.setServerEnabled(nextServer.id, true)
                }
                refreshAllAndroidAutoScreens()
                return
            }
        }

        if (currentSource == com.craftworks.music.managers.MediaSource.EMBY && embyServers.size > 1) {
            val serverIndex = embyServers.indexOfFirst { it.id == currentEmbyId }
            if (serverIndex != -1 && serverIndex + 1 < embyServers.size) {
                val nextServer = embyServers[serverIndex + 1]
                withContext(Dispatchers.Main) {
                    com.craftworks.music.managers.EmbyJellyfinManager.setCurrentServer(nextServer.id)
                    com.craftworks.music.managers.EmbyJellyfinManager.setServerEnabled(nextServer.id, true)
                }
                refreshAllAndroidAutoScreens()
                return
            }
        }

        val currentIndex = availableSources.indexOf(currentSource)
        val nextSource = if (currentIndex != -1 && currentIndex + 1 < availableSources.size) {
            availableSources[currentIndex + 1]
        } else {
            availableSources.firstOrNull() ?: com.craftworks.music.managers.MediaSource.LOCAL
        }

        withContext(Dispatchers.Main) {
            if (nextSource == com.craftworks.music.managers.MediaSource.NAVIDROME && navidromeServers.isNotEmpty()) {
                val firstServer = navidromeServers.first()
                com.craftworks.music.managers.NavidromeManager.setCurrentServer(firstServer.id)
                com.craftworks.music.managers.NavidromeManager.setServerEnabled(firstServer.id, true)
            } else if (nextSource == com.craftworks.music.managers.MediaSource.EMBY && embyServers.isNotEmpty()) {
                val firstServer = embyServers.first()
                com.craftworks.music.managers.EmbyJellyfinManager.setCurrentServer(firstServer.id)
                com.craftworks.music.managers.EmbyJellyfinManager.setServerEnabled(firstServer.id, true)
            }
            com.craftworks.music.managers.MediaSourceManager.setSelectedSource(nextSource)
        }

        refreshAllAndroidAutoScreens()
    }

    private suspend fun refreshAllAndroidAutoScreens() {
        aHomeScreenItems = emptyList()
        aAlbumScreenItems = emptyList()
        aSongScreenItems = emptyList()
        aArtistsScreenItems = emptyList()
        aPlaylistScreenItems = emptyList()
        aRadioScreenItems = emptyList()
        aFolderSongs = emptyList()

        com.craftworks.music.managers.DataRefreshManager.notifyDataSourcesChanged()

        session?.notifyChildrenChanged("nodeROOT", rootHierarchy.size, null)
        session?.notifyChildrenChanged("nodeHOME", 0, null)
        session?.notifyChildrenChanged("nodeALBUMS", 0, null)
        session?.notifyChildrenChanged("nodeSONGS", 0, null)
        session?.notifyChildrenChanged("nodeARTISTS", 0, null)
        session?.notifyChildrenChanged("nodePLAYLISTS", 0, null)
        session?.notifyChildrenChanged("nodeRADIOS", 0, null)
        session?.notifyChildrenChanged("nodeSOURCES", getSourceItems().size, null)

        withContext(Dispatchers.Main) {
            updateCustomLayout()
        }
    }
    //endregion
}