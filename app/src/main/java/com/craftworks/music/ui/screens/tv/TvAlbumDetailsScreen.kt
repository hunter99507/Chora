package com.craftworks.music.ui.screens.tv

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.height
import androidx.media.utils.MediaConstants.METADATA_KEY_IS_EXPLICIT
import androidx.media3.common.Player
import androidx.tv.material3.Border
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import com.gigamole.composefadingedges.marqueeHorizontalFadingEdges
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.craftworks.music.R
import com.craftworks.music.data.model.Screen
import com.craftworks.music.formatDurationSummary
import com.craftworks.music.formatMilliseconds
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.elements.GenrePill
import com.craftworks.music.ui.elements.dialogs.tv.SongDialog
import com.craftworks.music.fadingEdge
import com.craftworks.music.ui.elements.tv.TvHorizontalSongCard
import com.craftworks.music.ui.viewmodels.AlbumDetailsViewModel
import com.craftworks.music.ui.viewmodels.ArtistsScreenViewModel
import com.craftworks.music.util.AmbientGradientBackground
import com.craftworks.music.util.PaletteHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAlbumDetails(
    selectedAlbumId: String = "",
    selectedAlbumImage: Uri = Uri.EMPTY,
    mediaController: MediaController? = null,
    navHostController: NavHostController = rememberNavController(),
    viewModel: AlbumDetailsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val currentAlbum = viewModel.songsInAlbum.collectAsStateWithLifecycle().value
    val showTrackNumbers by AppearanceSettingsManager(context)
        .showTrackNumbersFlow.collectAsStateWithLifecycle(false)

    val parentEntry = remember(navHostController.currentBackStackEntry) {
        navHostController.getBackStackEntry("main_graph")
    }
    val artistsViewModel: ArtistsScreenViewModel = hiltViewModel(parentEntry)

    var selectedSong by remember { mutableStateOf(MediaItem.EMPTY) }
    var showSongDialog by remember { mutableStateOf(false) }

    var showLoading by remember { mutableStateOf(false) }
    var paletteColors by remember { mutableStateOf<List<Color>>(emptyList()) }

    LaunchedEffect(selectedAlbumImage) {
        if (selectedAlbumImage != Uri.EMPTY) {
            paletteColors = PaletteHelper.extractColorsFromUri(selectedAlbumImage.toString(), context)
        }
    }

    LaunchedEffect(selectedAlbumId) {
        showLoading = false

        viewModel.loadAlbumDetails(selectedAlbumId)

        delay(500.milliseconds)
        showLoading = true
    }

    AnimatedVisibility(
        visible = currentAlbum.isEmpty() && showLoading,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 6.dp
            )
        }
    }

    var currentPlayingMediaItem by remember { mutableStateOf(mediaController?.currentMediaItem) }
    DisposableEffect(mediaController) {
        if (mediaController == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentPlayingMediaItem = mediaItem
            }
        }
        mediaController.addListener(listener)
        onDispose { mediaController.removeListener(listener) }
    }

    AnimatedVisibility(
        visible = currentAlbum.isNotEmpty(),
        enter = fadeIn()
    ) {
        val coroutineScope = rememberCoroutineScope()
        val playRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) { playRequester.requestFocus() }

        val songs = if (currentAlbum.size > 1) currentAlbum.subList(1, currentAlbum.size) else currentAlbum
        val totalDurationSec = remember(songs) {
            songs.sumOf { (it.mediaMetadata.durationMs ?: 0L) / 1000 }.toInt()
        }
        val formattedTotalDuration = remember(totalDurationSec) {
            if (totalDurationSec > 0) formatDurationSummary(totalDurationSec) else ""
        }
        val year = currentAlbum[0].mediaMetadata.recordingYear
        val artistName = currentAlbum[0].mediaMetadata.artist?.toString() ?: ""
        val albumTitle = currentAlbum[0].mediaMetadata.title?.toString() ?: ""

        val metadataLine = buildString {
            if (year != null && year > 0) {
                append("$year • ")
            }
            append("${songs.size} ${if (songs.size == 1) "track" else "tracks"}")
            if (formattedTotalDuration.isNotBlank()) {
                append(" • $formattedTotalDuration")
            }
        }

        AmbientGradientBackground(
            colors = paletteColors,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 36.dp, end = 36.dp, top = 20.dp, bottom = 20.dp)
                    .focusGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // LEFT PANE: Album Showcase matching Now Playing layout
                Column(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight()
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Album Cover Art
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .shadow(16.dp, RoundedCornerShape(20.dp), clip = true)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                RoundedCornerShape(20.dp)
                            )
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(selectedAlbumImage.toString().replace(Regex("size=\\d+"), "size=800"))
                                .placeholderMemoryCacheKey(selectedAlbumImage.toString())
                                .crossfade(true)
                                .build(),
                            fallback = painterResource(R.drawable.placeholder),
                            error = painterResource(R.drawable.placeholder),
                            contentScale = ContentScale.Crop,
                            contentDescription = albumTitle,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Album Title
                    Text(
                        text = albumTitle,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .marqueeHorizontalFadingEdges(marqueeProvider = { Modifier.basicMarquee() })
                    )

                    Spacer(Modifier.height(4.dp))

                    // Clickable Artist Name
                    if (artistName.isNotBlank()) {
                        Text(
                            text = artistName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .marqueeHorizontalFadingEdges(marqueeProvider = { Modifier.basicMarquee() })
                                .clickable {
                                    val fallbackId = currentAlbum[0].mediaMetadata.extras?.getString("artistId") ?: ""
                                    artistsViewModel.selectArtistByName(artistName, fallbackId)
                                    navHostController.navigate(Screen.ArtistDetails.route) {
                                        launchSingleTop = true
                                    }
                                }
                        )
                    }

                    if (metadataLine.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = metadataLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .marqueeHorizontalFadingEdges(marqueeProvider = { Modifier.basicMarquee() })
                        )
                    }

                    // Genre Pills (if any)
                    if (!currentAlbum[0].mediaMetadata.genre.isNullOrEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            currentAlbum[0].mediaMetadata.genre?.split(",")?.take(2)?.forEach {
                                GenrePill(it.trim())
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Play & Shuffle Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    if (songs.isNotEmpty()) {
                                        SongHelper.play(songs, 0, mediaController)
                                        navHostController.navigate(Screen.NowPlayingLandscape.route) {
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(playRequester),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.Action_Play))
                        }

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (songs.isNotEmpty()) {
                                        SongHelper.play(songs, 0, mediaController, shuffle = true)
                                        navHostController.navigate(Screen.NowPlayingLandscape.route) {
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                        ) {
                            Icon(
                                ImageVector.vectorResource(R.drawable.round_shuffle_28),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.Action_Shuffle))
                        }
                    }
                }

                // RIGHT PANE: 2-Column Tracklist Card matching Now Playing Up Next Card
                Column(
                    modifier = Modifier
                        .weight(1.35f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .focusGroup()
                        .focusRestorer(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.rounded_queue_music_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Tracklist",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${songs.size} ${if (songs.size == 1) "Track" else "Tracks"}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 4.dp)
                    )

                    val groupedSongs = remember(songs) { songs.groupBy { it.mediaMetadata.discNumber ?: 1 } }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxSize()
                            .focusGroup()
                            .focusRestorer(),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (groupedSongs.size > 1) {
                            groupedSongs.forEach { (discNumber, disc) ->
                                item(span = { GridItemSpan(2) }) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp, bottom = 2.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.Album_Disc_Number) + " " + discNumber.toString() + " (${disc.size})",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                                .fillMaxWidth()
                                        )
                                    }
                                }
                                items(disc) { song ->
                                    val isSongPlaying = currentPlayingMediaItem?.mediaId == song.mediaId ||
                                        (currentPlayingMediaItem?.mediaMetadata?.title == song.mediaMetadata.title &&
                                         currentPlayingMediaItem?.mediaMetadata?.artist == song.mediaMetadata.artist)
                                    TvTrackGridItem(
                                        song = song,
                                        isCurrent = isSongPlaying,
                                        onClick = {
                                            coroutineScope.launch {
                                                SongHelper.play(songs, songs.indexOf(song), mediaController)
                                                navHostController.navigate(Screen.NowPlayingLandscape.route) {
                                                    launchSingleTop = true
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            selectedSong = song
                                            showSongDialog = true
                                        }
                                    )
                                }
                            }
                        } else {
                            items(songs) { song ->
                                val isSongPlaying = currentPlayingMediaItem?.mediaId == song.mediaId ||
                                    (currentPlayingMediaItem?.mediaMetadata?.title == song.mediaMetadata.title &&
                                     currentPlayingMediaItem?.mediaMetadata?.artist == song.mediaMetadata.artist)
                                TvTrackGridItem(
                                    song = song,
                                    isCurrent = isSongPlaying,
                                    onClick = {
                                        coroutineScope.launch {
                                            SongHelper.play(songs, songs.indexOf(song), mediaController)
                                            navHostController.navigate(Screen.NowPlayingLandscape.route) {
                                                launchSingleTop = true
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        selectedSong = song
                                        showSongDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSongDialog)
        SongDialog(
            song = selectedSong,
            onSetRating = { rating ->
                viewModel.setSongRating(
                    songId = selectedSong.mediaMetadata.extras?.getString("navidromeID") ?: "",
                    rating = rating
                )
            },
            setShowDialog = { showSongDialog = it }
        )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvTrackGridItem(
    song: MediaItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val durationSec = ((song.mediaMetadata.durationMs ?: 0L) / 1000).toInt()
    val formattedDuration = if (durationSec > 0) formatMilliseconds(durationSec) else ""
    val trackNum = song.mediaMetadata.trackNumber ?: 0

    ListItem(
        selected = isCurrent,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ListItemDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ListItemDefaults.colors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent,
            focusedContainerColor = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.40f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
            contentColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            focusedContentColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        ),
        border = ListItemDefaults.border(
            border = Border(
                border = BorderStroke(
                    if (isCurrent) 2.dp else 1.dp,
                    if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                ),
                shape = RoundedCornerShape(10.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp)
            )
        ),
        scale = ListItemDefaults.scale(focusedScale = 1.02f),
        leadingContent = {
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrent) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Playing",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = if (trackNum > 0) trackNum.toString() else "-",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            }
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.mediaMetadata.title?.toString() ?: "",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (song.mediaMetadata.extras?.getBoolean(METADATA_KEY_IS_EXPLICIT) == true) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.rounded_explicit_24),
                        contentDescription = "Explicit",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        },
        trailingContent = {
            if (formattedDuration.isNotBlank()) {
                Text(
                    text = formattedDuration,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    )
}