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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.height
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

    AnimatedVisibility(
        visible = currentAlbum.isNotEmpty(),
        enter = fadeIn()
    ) {
        val coroutineScope = rememberCoroutineScope()
        val playRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) { playRequester.requestFocus() }

        val songs = if (currentAlbum.size > 1) currentAlbum.subList(1, currentAlbum.size) else currentAlbum
        val totalDuration = (songs.sumOf { it.mediaMetadata.durationMs ?: 0L } / 1000).toInt()
        val year = currentAlbum[0].mediaMetadata.recordingYear
        val artistName = currentAlbum[0].mediaMetadata.artist?.toString() ?: ""

        val otherMetadata = buildString {
            if (year != null && year > 0) {
                append("$year • ")
            }
            append("${songs.size} ${if (songs.size == 1) "track" else "tracks"}")
            if (totalDuration > 0) {
                append(" • ${formatDurationSummary(totalDuration)}")
            }
        }

        val fadeBrush = remember {
            Brush.verticalGradient(
                0.0f to Color.Black,
                0.40f to Color.Black,
                0.70f to Color.Black.copy(alpha = 0.6f),
                0.90f to Color.Black.copy(alpha = 0.2f),
                1.0f to Color.Transparent
            )
        }

        AmbientGradientBackground(
            colors = paletteColors,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .focusGroup(),
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Hero Cover Art Melting into Ambient Gradient Background
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(330.dp)
                            .fadingEdge(fadeBrush)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(selectedAlbumImage.toString().replace(Regex("size=\\d+"), "size=800"))
                                .diskCachePolicy(CachePolicy.DISABLED)
                                .placeholderMemoryCacheKey(selectedAlbumImage.toString())
                                .crossfade(true)
                                .build(),
                            fallback = painterResource(R.drawable.placeholder),
                            contentScale = ContentScale.Crop,
                            contentDescription = currentAlbum[0].mediaMetadata.title?.toString(),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 2. Title, Clickable Artist, Metadata, Genre Pills, and Play / Shuffle Buttons
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp)
                            .focusGroup(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Title
                        Text(
                            text = currentAlbum[0].mediaMetadata.title?.toString() ?: "",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Artist Link + Metadata
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (artistName.isNotBlank()) {
                                var isFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .onFocusChanged { isFocused = it.isFocused }
                                        .border(
                                            width = if (isFocused) 2.dp else 1.dp,
                                            color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.border.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                        )
                                        .clickable {
                                            val fallbackId = currentAlbum[0].mediaMetadata.extras?.getString("artistId") ?: ""
                                            artistsViewModel.selectArtistByName(artistName, fallbackId)
                                            navHostController.navigate(Screen.ArtistDetails.route) {
                                                launchSingleTop = true
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(R.drawable.rounded_artist_24),
                                            contentDescription = null,
                                            tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = artistName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(
                                text = otherMetadata,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }

                        // Genre Pills
                        if (!currentAlbum[0].mediaMetadata.genre.isNullOrEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                currentAlbum[0].mediaMetadata.genre?.split(",")?.forEach {
                                    GenrePill(it.trim())
                                }
                            }
                        }

                        // Play & Shuffle Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        if (songs.isNotEmpty()) {
                                            SongHelper.play(
                                                songs,
                                                0,
                                                mediaController
                                            )
                                            navHostController.navigate(Screen.NowPlayingLandscape.route) {
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.focusRequester(playRequester),
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
                                            SongHelper.play(
                                                songs,
                                                0,
                                                mediaController,
                                                shuffle = true
                                            )
                                            navHostController.navigate(Screen.NowPlayingLandscape.route) {
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                },
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
                }

                // 3. Songs List
                val groupedSongs = songs.groupBy { it.mediaMetadata.discNumber }
                if (groupedSongs.size > 1) {
                    groupedSongs.forEach { (discNumber, disc) ->
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 48.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.Album_Disc_Number) + discNumber.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.2f),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        items(disc) { song ->
                            val songWithArt = remember(song, selectedAlbumImage) {
                                if (song.mediaMetadata.artworkUri != null && song.mediaMetadata.artworkUri != Uri.EMPTY) song
                                else song.buildUpon().setMediaMetadata(song.mediaMetadata.buildUpon().setArtworkUri(selectedAlbumImage).build()).build()
                            }
                            Box(modifier = Modifier.padding(horizontal = 48.dp)) {
                                TvHorizontalSongCard(
                                    song = songWithArt,
                                    showTrackNumber = showTrackNumbers,
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
                } else {
                    items(songs) { song ->
                        val songWithArt = remember(song, selectedAlbumImage) {
                            if (song.mediaMetadata.artworkUri != null && song.mediaMetadata.artworkUri != Uri.EMPTY) song
                            else song.buildUpon().setMediaMetadata(song.mediaMetadata.buildUpon().setArtworkUri(selectedAlbumImage).build()).build()
                        }
                        Box(modifier = Modifier.padding(horizontal = 48.dp)) {
                            TvHorizontalSongCard(
                                song = songWithArt,
                                showTrackNumber = showTrackNumbers,
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