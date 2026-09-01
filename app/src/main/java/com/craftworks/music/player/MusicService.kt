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
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
    lateinit var player: Player
    var session: MediaLibrarySession? = null

    private var scrobbleJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var _sleepTimerRemainingTime = MutableStateFlow(0)
    val sleepTimerRemainingTime: StateFlow<Int> = _sleepTimerRemainingTime.asStateFlow()

    @Inject lateinit var appearanceSettingsManager: AppearanceSettingsManager
    @Inject lateinit var playbackSettingsManager: PlaybackSettingsManager
    @Inject lateinit var transcodeManager: TranscodeManager

    @Inject lateinit var albumRepository: AlbumRepository
    @Inject lateinit var artistRepository: ArtistRepository
    @Inject lateinit var songRepository: SongRepository
    @Inject lateinit var radioRepository: RadioRepository
    @Inject lateinit var playlistRepository: PlaylistRepository
    @Inject lateinit var lyricsRepository: LyricsRepository

    companion object {
        const val CUSTOM_ACTION_SHUFFLE = "com.craftworks.chora.CUSTOM_ACTION_SHUFFLE"
        const val CUSTOM_ACTION_REPEAT = "com.craftworks.chora.CUSTOM_ACTION_REPEAT"

        private var instance: ChoraMediaLibraryService? = null

        fun getInstance(): ChoraMediaLibraryService? {
            return instance
        }
    }

    private val rootItem = MediaItem.Builder()
        .setMediaId("nodeROOT")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(false)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
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
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                .setTitle("Albums")
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

    private var rootHierarchy = mutableListOf(homeItem, albumsItem, artistsItem, radiosItem, playlistsItem)

    private val serviceMainScope = CoroutineScope(Dispatchers.Main)
    private val serviceIOScope = CoroutineScope(Dispatchers.IO)

    var aHomeScreenItems = mutableListOf<MediaItem>()
    var aAlbumScreenItems = mutableListOf<MediaItem>()
    var aArtistsScreenItems = mutableListOf<MediaItem>()
    var aRadioScreenItems = mutableListOf<MediaItem>()
    var aPlaylistScreenItems = mutableListOf<MediaItem>()

    var aFolderSongs = mutableListOf<MediaItem>()

    //endregion

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        instance = this

        Log.d("AA", "onCreate Android Auto")

        if (session == null)
            initializePlayer()
        else
            Log.d("AA", "MediaSession already initialized, not recreating")
    }

    @OptIn(UnstableApi::class)
    fun initializePlayer() {
        serviceIOScope.launch {
            appearanceSettingsManager.bottomNavItemsFlow.collect { items ->
                val routeToItem = mapOf(
                    "home_screen" to homeItem,
                    "album_screen" to albumsItem,
                    "artists_screen" to artistsItem,
                    "radio_screen" to radiosItem,
                    "playlist_screen" to playlistsItem
                )

                rootHierarchy = items
                    .filter { it.enabled }
                    .mapNotNull { routeToItem[it.screenRoute] }
                    .toMutableList()

                session?.notifyChildrenChanged("nodeROOT", 0, null)
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
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingDataSourceFactory))
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
                // Apply ReplayGain
                if (mediaItem?.mediaMetadata?.extras?.getFloat("replayGain") != null) {
                    player.volume = clamp(
                        (10f.pow(
                            ((mediaItem.mediaMetadata.extras?.getFloat("replayGain") ?: 0f) / 20f)
                        )), 0f, 1f
                    )
                    Log.d("REPLAY GAIN", "Setting ReplayGain to ${player.volume}")
                }

                playerScrobbled = false

                super.onMediaItemTransition(mediaItem, reason)

                serviceIOScope.launch {
                    lyricsRepository.getLyrics(mediaItem?.mediaMetadata)
                    val mediaId = mediaItem?.mediaMetadata?.extras?.getString("navidromeID") ?: return@launch
                    songRepository.scrobbleSong(mediaId, false)

                    // Preload next track in queue for instant skipping
                    try {
                        val nextIndex = player.currentMediaItemIndex + 1
                        if (nextIndex < player.mediaItemCount) {
                            val nextItem = player.getMediaItemAt(nextIndex)
                            val nextUri = nextItem.requestMetadata.mediaUri ?: nextItem.mediaId.toUri()
                            if (nextUri.toString().startsWith("http")) {
                                val url = java.net.URL(nextUri.toString())
                                val connection = url.openConnection() as java.net.HttpURLConnection
                                connection.setRequestProperty("Range", "bytes=0-1048576")
                                connection.connectTimeout = 5000
                                connection.readTimeout = 5000
                                connection.connect()
                                val buffer = ByteArray(32768)
                                val inputStream = connection.inputStream
                                var read = 0
                                var total = 0
                                while (inputStream.read(buffer).also { read = it } != -1 && total < 1048576) {
                                    total += read
                                }
                                inputStream.close()
                                connection.disconnect()
                                Log.d("PRELOAD", "Preloaded $total bytes for next track: ${nextItem.mediaMetadata.title}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("PRELOAD", "Preload exception: ${e.message}")
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

        session = MediaLibrarySession.Builder(this, player, LibrarySessionCallback())
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
                        if (NavidromeManager.checkActiveServers() &&
                            mediaItem?.mediaMetadata?.extras?.getString("navidromeID")
                                ?.startsWith("Local") == false &&
                            mediaItem.mediaMetadata.mediaType != MediaMetadata.MEDIA_TYPE_RADIO_STATION
                        ) {
                            serviceIOScope.launch {
                                songRepository.scrobbleSong(mediaItem.mediaMetadata.extras?.getString("navidromeID") ?: "", true)
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
        val isShuffle = if (::player.isInitialized) player.shuffleModeEnabled else true
        val shuffleButton = CommandButton.Builder()
            .setDisplayName(if (isShuffle) "Shuffle: On" else "Shuffle: Off")
            .setIconResId(R.drawable.round_shuffle_28)
            .setSessionCommand(SessionCommand(CUSTOM_ACTION_SHUFFLE, Bundle.EMPTY))
            .setEnabled(true)
            .build()

        val repeatMode = if (::player.isInitialized) player.repeatMode else Player.REPEAT_MODE_ALL
        val repeatIcon = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.rounded_repeat1_24
            else -> R.drawable.rounded_repeat_24
        }
        val repeatButton = CommandButton.Builder()
            .setDisplayName(if (repeatMode != Player.REPEAT_MODE_OFF) "Repeat: On" else "Repeat: Off")
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
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CUSTOM_ACTION_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(CUSTOM_ACTION_REPEAT, Bundle.EMPTY))
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
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
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
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            serviceIOScope.launch {
                println("ONPOSTCONNTECT MUSIC SERVICE!")

                if (session.isAutoCompanionController(controller))
                    getHomeScreenItems()

                this@ChoraMediaLibraryService.session?.notifyChildrenChanged(
                    "nodeHOME",
                    aHomeScreenItems.size,
                    null
                )
            }
            super.onPostConnect(session, controller)
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

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            // Android Auto uses the legacy MediaController, so we need to do some very weird hacks to make it work nicely.
            val settable = SettableFuture.create<List<MediaItem>>()
            serviceIOScope.launch {
                try {
                    // If only one item is requested, check if it belongs to a folder we've already loaded
                    if (mediaItems.size == 1) {
                        val requestedId = mediaItems[0].mediaId
                        if (requestedId == "action_shuffle_all_songs") {
                            val allSongs = songRepository.getSongs().map { it.withHighResArtwork().buildUpon().setUri(it.mediaId).build() }
                            val shuffled = allSongs.shuffled()
                            withContext(Dispatchers.Main) {
                                player.shuffleModeEnabled = true
                                player.repeatMode = Player.REPEAT_MODE_ALL
                            }
                            settable.set(shuffled)
                            return@launch
                        }

                        // Try to find the full item in the last browsed folder
                        val fullItem = aFolderSongs.find { it.mediaId == requestedId }
                        if (fullItem != null) {
                            val startIndex = aFolderSongs.indexOf(fullItem)
                            val folderQueue = aFolderSongs.subList(startIndex, aFolderSongs.size).map { item ->
                                item.buildUpon()
                                    .setUri(item.mediaId)
                                    .build()
                            }
                            settable.set(folderQueue)
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
            val newRating = (rating as StarRating).starRating.toInt()

            runBlocking {
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
                    val items = when (parentId) {
                        "nodeROOT" -> rootHierarchy
                        "nodeHOME" -> getHomeScreenItems()
                        "nodeALBUMS" -> getAlbumScreenItems()
                        "nodeARTISTS" -> getArtistScreenItems()
                        "nodeRADIOS" -> getRadioItems()
                        "nodePLAYLISTS" -> getPlaylistItems()
                        else -> {
                            val mediaItem =
                                aHomeScreenItems.find { it.mediaId == parentId }
                                    ?: aPlaylistScreenItems.find { it.mediaId == parentId }
                                    ?: aAlbumScreenItems.find { it.mediaId == parentId }
                                    ?: aArtistsScreenItems.find { it.mediaId == parentId }
                            getFolderItems(
                                parentId,
                                mediaItem?.mediaMetadata?.mediaType
                                    ?: MediaMetadata.MEDIA_TYPE_ALBUM
                            )
                        }
                    }
                    settable.set(LibraryResult.ofItemList(items, params))
                } catch (e: Exception) {
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
                    "nodeROOT" -> 2
                    "nodeHOME" -> aHomeScreenItems.size
                    "nodeALBUMS" -> aAlbumScreenItems.size
                    "nodeARTISTS" -> aArtistsScreenItems.size
                    "nodeRADIOS" -> aRadioScreenItems.size
                    "nodePLAYLISTS" -> aPlaylistScreenItems.size
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
                LocalDataSettingsManager(applicationContext).playbackResumptionPlaylistWithStartPosition.collectLatest { playbackResumptionList ->
                    settable.set(playbackResumptionList)
                    Log.d("RESUMPTION", "Got mediaitems")
                    withContext(Dispatchers.Main) {
                        player.setMediaItems(playbackResumptionList.mediaItems)
                        player.prepare()
                        player.playWhenReady = true

                        player.seekTo(playbackResumptionList.startIndex, playbackResumptionList.startPositionMs)

                        Log.d(
                            "RESUMPTION",
                            "Set playlist: ${playbackResumptionList.mediaItems.map { it.mediaMetadata.title }} at index ${playbackResumptionList.startIndex} with position ${playbackResumptionList.startPositionMs}"
                        )
                    }
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
            return Futures.immediateFuture(
                LibraryResult.ofItemList(
                    runBlocking {
                        songRepository.getSongs(query).toMutableList()
                    },
                    LibraryParams.Builder().build()
                )
            )
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            println("onSearch!!!")

            session.notifySearchResultChanged(
                browser,
                query,
                runBlocking {
                    songRepository.getSongs(query).size +
                            albumRepository.searchAlbum(query).size +
                            radioRepository.getRadios().map { it.toMediaItem() }.fastFilter {
                                it.mediaMetadata.station?.contains(
                                    query
                                ) ?: false
                            }.size +
                            playlistRepository.getPlaylists().fastFilter {
                                it.mediaMetadata.title?.contains(
                                    query
                                ) == true
                            }.size
                },
                LibraryParams.Builder().build()
            )

            return Futures.immediateFuture(LibraryResult.ofVoid())
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
        scrobbleJob?.cancel()
        sleepTimerJob?.cancel()
        instance = null
        super.onDestroy()
    }

    fun saveState() {
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

    //region getChildren
    private suspend fun getHomeScreenItems(): MutableList<MediaItem> {
        Log.d("AA", "GETTING ANDROID AUTO SCREEN ITEMS")
        if (aHomeScreenItems.isEmpty()) {
            try {
                coroutineScope {
                    val recentlyPlayedDeferred = async { albumRepository.getAlbums("recent", 6) }
                    val mostPlayedDeferred = async { albumRepository.getAlbums("frequent", 6) }

                    val recentlyPlayedAlbums = recentlyPlayedDeferred.await()
                    val mostPlayedAlbums = mostPlayedDeferred.await()

                    recentlyPlayedAlbums.forEach { album ->
                        aHomeScreenItems.add(
                            album.withHighResArtwork().apply {
                                this.mediaMetadata.extras?.putString(
                                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                                    this@ChoraMediaLibraryService.getString(R.string.recently_played)
                                )
                            }
                        )
                    }

                    mostPlayedAlbums.forEach { album ->
                        aHomeScreenItems.add(
                            album.withHighResArtwork().apply {
                                this.mediaMetadata.extras?.putString(
                                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                                    this@ChoraMediaLibraryService.getString(R.string.most_played)
                                )
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("AA", "Error loading home screen items", e)
            }
        }
        return aHomeScreenItems
    }

    private suspend fun getAlbumScreenItems(): MutableList<MediaItem> {
        Log.d("AA", "GETTING ANDROID AUTO ALBUM SCREEN ITEMS")
        if (aAlbumScreenItems.isEmpty()) {
            try {
                val albums = albumRepository.getAlbums("alphabeticalByName", 100, 0)
                aAlbumScreenItems.addAll(albums.map { it.withHighResArtwork() })
            } catch (e: Exception) {
                Log.e("AA", "Error loading album screen items", e)
            }
        }
        return aAlbumScreenItems
    }

    private suspend fun getArtistScreenItems(): MutableList<MediaItem> {
        Log.d("AA", "GETTING ANDROID AUTO ARTIST SCREEN ITEMS")
        if (aArtistsScreenItems.isEmpty()) {
            try {
                val artists = artistRepository.getArtists()
                artists.forEach {
                    val highResArt = if (it.artistImageUrl?.contains("size=") == true)
                        it.artistImageUrl.replace(Regex("size=\\d+"), "size=800")
                    else
                        it.artistImageUrl
                    aArtistsScreenItems.add(
                        MediaItem.Builder()
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(it.name)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                                    .setArtworkUri(highResArt?.toUri())
                                    .setIsBrowsable(true)
                                    .setIsPlayable(false)
                                    .build()
                            )
                            .setMediaId(it.navidromeID)
                            .setUri(it.navidromeID)
                            .build()
                    )
                }
            } catch (e: Exception) {
                Log.e("AA", "Error loading artist screen items", e)
            }
        }
        return aArtistsScreenItems
    }

    private suspend fun getRadioItems(): MutableList<MediaItem> {
        if (aRadioScreenItems.isEmpty()) {
            try {
                aRadioScreenItems.addAll(
                    radioRepository.getRadios().map { radio ->
                        radio.toMediaItem().withHighResArtwork()
                    }
                )
            } catch (e: Exception) {
                Log.e("AA", "Error loading radio screen items", e)
            }
        }
        return aRadioScreenItems
    }

    private suspend fun getPlaylistItems(): MutableList<MediaItem> {
        if (aPlaylistScreenItems.isEmpty()) {
            try {
                val shuffleAllItem = MediaItem.Builder()
                    .setMediaId("action_shuffle_all_songs")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("🔀 Shuffle All Songs")
                            .setArtist("Library")
                            .setIsBrowsable(false)
                            .setIsPlayable(true)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                            .build()
                    )
                    .build()
                aPlaylistScreenItems.add(shuffleAllItem)
                aPlaylistScreenItems.addAll(playlistRepository.getPlaylists().map { it.withHighResArtwork() })
            } catch (e: Exception) {
                Log.e("AA", "Error loading playlist screen items", e)
            }
        }
        return aPlaylistScreenItems
    }

    private suspend fun getFolderItems(parentId: String, type: Int): MutableList<MediaItem> {
        aFolderSongs.clear()
        try {
            when (type) {
                MediaMetadata.MEDIA_TYPE_ALBUM -> {
                    val albumSongs = albumRepository.getAlbum(parentId)
                    aFolderSongs.addAll(
                        albumSongs?.subList(1, albumSongs.size)?.map { it.withHighResArtwork() } ?: emptyList()
                    )
                }

                MediaMetadata.MEDIA_TYPE_PLAYLIST -> {
                    aFolderSongs.addAll(
                        playlistRepository.getPlaylistSongs(parentId).map { it.withHighResArtwork() }
                    )
                }

                MediaMetadata.MEDIA_TYPE_ARTIST -> {
                    aFolderSongs.addAll(
                        artistRepository.getArtistAlbums(parentId).map { it.withHighResArtwork() }
                    )
                }

                else -> aFolderSongs.clear()
            }
        } catch (e: Exception) {
            Log.e("AA", "Error loading folder items for $parentId", e)
        }
        return aFolderSongs
    }
    //endregion
}