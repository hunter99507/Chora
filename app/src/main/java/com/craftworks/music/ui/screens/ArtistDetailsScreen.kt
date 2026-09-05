package com.craftworks.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import com.craftworks.music.ui.elements.LocalBottomPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.StarRating
import androidx.media3.session.MediaController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.fadingEdge
import com.craftworks.music.data.model.Screen
import com.craftworks.music.data.model.toAlbum
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.elements.HorizontalSongCard
import com.craftworks.music.ui.elements.dialogs.RatingDialog
import com.craftworks.music.ui.elements.dialogs.dialogFocusable
import com.craftworks.music.ui.viewmodels.ArtistsScreenViewModel
import com.craftworks.music.util.AmbientGradientBackground
import com.craftworks.music.util.PaletteHelper
import kotlinx.coroutines.launch
import java.net.URLEncoder

@OptIn(ExperimentalComposeUiApi::class)
@ExperimentalFoundationApi
@Composable
@Preview
fun ArtistDetails(
    navHostController: NavHostController = rememberNavController(),
    mediaController: MediaController? = null,
    viewModel: ArtistsScreenViewModel = hiltViewModel()
) {
    val showLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val artist = viewModel.selectedArtist.collectAsStateWithLifecycle().value
    val artistAlbums = viewModel.artistAlbums.collectAsStateWithLifecycle().value
    val artistSongs = viewModel.artistSongs.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var songToRate by remember { mutableStateOf<MediaItem?>(null) }

    var paletteColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    val artworkUri = remember(artist, artistAlbums) {
        artist?.artistImageUrl.takeIf { !it.isNullOrBlank() }
            ?: artistAlbums.firstOrNull()?.mediaMetadata?.artworkUri?.toString()
    }

    LaunchedEffect(artworkUri) {
        if (!artworkUri.isNullOrBlank()) {
            paletteColors = PaletteHelper.extractColorsFromUri(artworkUri, context)
        }
    }

    // Loading spinner
    AnimatedVisibility(
        visible = showLoading,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 6.dp
            )
            Text(
                text = "Loading",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }

    BackHandler {
        if (!navHostController.popBackStack()) {
            navHostController.navigate(Screen.Artists.route) { launchSingleTop = true }
        }
    }

    // Main Content
    AnimatedVisibility(
        visible = artist?.name?.isNotBlank() == true,
        enter = fadeIn()
    ) {
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val headerHeight = (screenHeight * 0.46f).coerceIn(340.dp, 460.dp)

        val totalSeconds = artistSongs.sumOf {
            val dur = it.mediaMetadata.extras?.getInt("duration") ?: 0
            if (dur > 0) dur else ((it.mediaMetadata.durationMs ?: 0L) / 1000).toInt()
        }
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val durationFormatted = if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else if (minutes > 0) {
            String.format("%d:%02d", minutes, seconds)
        } else ""

        val metadataLine = buildString {
            append("${artistAlbums.size} ${if (artistAlbums.size == 1) "album" else "albums"}")
            if (artistSongs.isNotEmpty()) {
                append(" • ${artistSongs.size} ${if (artistSongs.size == 1) "track" else "tracks"}")
                if (durationFormatted.isNotBlank()) {
                    append(" • $durationFormatted")
                }
            }
        }

        AmbientGradientBackground(colors = paletteColors) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .dialogFocusable(),
                contentPadding = PaddingValues(
                    bottom = 64.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + LocalBottomPadding.current
                )
            ) {
            // 1. Header with 2x2 collage or artist artwork
            item {
                Box(
                    modifier = Modifier
                        .height(headerHeight)
                        .fillMaxWidth()
                ) {
                    ArtistHeaderCollage(
                        artistImageUrl = artist?.artistImageUrl,
                        albums = artistAlbums,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Top-left Back button
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                                start = 16.dp
                            )
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable {
                                if (!navHostController.popBackStack()) {
                                    navHostController.navigate(Screen.Artists.route) { launchSingleTop = true }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            tint = Color.White,
                            contentDescription = "Go Back",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // 2. Artist Name
            item {
                Text(
                    text = artist?.name.toString(),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // 3. Metadata Subtitle (e.g. 14 albums • 139 tracks • 8:57:17)
            item {
                Text(
                    text = metadataLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            // 4. Play and Shuffle Buttons
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val songsToPlay = if (artistSongs.isNotEmpty()) {
                                    artistSongs
                                } else {
                                    artistAlbums.flatMap {
                                        val albumId = it.mediaMetadata.extras?.getString("navidromeID") ?: it.mediaId
                                        val album = viewModel.getAlbum(albumId)
                                        if (album.size > 1 && album[0].mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_ALBUM) {
                                            album.subList(1, album.size)
                                        } else album
                                    }
                                }
                                if (songsToPlay.isNotEmpty()) {
                                    SongHelper.play(songsToPlay, 0, mediaController)
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, "Play Artist")
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.Action_Play), maxLines = 1, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val songsToPlay = if (artistSongs.isNotEmpty()) {
                                    artistSongs
                                } else {
                                    artistAlbums.flatMap {
                                        val albumId = it.mediaMetadata.extras?.getString("navidromeID") ?: it.mediaId
                                        val album = viewModel.getAlbum(albumId)
                                        if (album.size > 1 && album[0].mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_ALBUM) {
                                            album.subList(1, album.size)
                                        } else album
                                    }
                                }
                                if (songsToPlay.isNotEmpty()) {
                                    SongHelper.play(songsToPlay, 0, mediaController, shuffle = true)
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(ImageVector.vectorResource(R.drawable.round_shuffle_28), "Shuffle Artist")
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.Action_Shuffle), maxLines = 1, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 5. Albums Section
            if (artistAlbums.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.Albums),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    )
                }

                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(artistAlbums, key = { it.mediaId }) { albumItem ->
                            Column(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        val album = albumItem.toAlbum()
                                        val encodedImage = URLEncoder.encode(album.coverArt, "UTF-8")
                                        navHostController.navigate(Screen.AlbumDetails.route + "/${album.navidromeID}/$encodedImage") {
                                            launchSingleTop = true
                                        }
                                    }
                            ) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(albumItem.mediaMetadata.artworkUri)
                                        .diskCachePolicy(CachePolicy.DISABLED)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Album Image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = albumItem.mediaMetadata.albumTitle?.toString() ?: "",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val year = albumItem.mediaMetadata.recordingYear
                                if (year != null && year > 0) {
                                    Text(
                                        text = year.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Top Tracks Section
            if (artistSongs.isNotEmpty()) {
                item {
                    Text(
                        text = "Top tracks",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                    )
                }

                itemsIndexed(artistSongs) { index, song ->
                    HorizontalSongCard(
                        song = song,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        onClick = {
                            coroutineScope.launch {
                                SongHelper.play(artistSongs, index, mediaController)
                            }
                        },
                        onAddToQueue = {
                            mediaController?.addMediaItem(song)
                        },
                        onSetRating = {
                            songToRate = song
                        }
                    )
                }
            }
        }
    }
}

    songToRate?.let { song ->
        RatingDialog(
            currentRating = (song.mediaMetadata.userRating as? StarRating)?.starRating?.toInt() ?: 0,
            onDismiss = { songToRate = null },
            onSetRating = { rating ->
                val navId = song.mediaMetadata.extras?.getString("navidromeID") ?: song.mediaId
                viewModel.setSongRating(navId, rating)
                songToRate = null
            }
        )
    }
}

@Composable
fun ArtistHeaderCollage(
    artistImageUrl: String?,
    albums: List<MediaItem>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fadeBrush = remember {
        Brush.verticalGradient(
            0.0f to Color.Black,
            0.40f to Color.Black,
            0.70f to Color.Black.copy(alpha = 0.6f),
            0.90f to Color.Black.copy(alpha = 0.2f),
            1.0f to Color.Transparent
        )
    }

    Box(modifier = modifier.fadingEdge(fadeBrush)) {
        if (albums.size >= 4) {
            val top4 = albums.take(4)
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(top4[0].mediaMetadata.artworkUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(top4[1].mediaMetadata.artworkUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(top4[2].mediaMetadata.artworkUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(top4[3].mediaMetadata.artworkUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        } else if (!artistImageUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artistImageUrl)
                    .placeholder(R.drawable.s_a_username)
                    .error(R.drawable.s_a_username)
                    .crossfade(true)
                    .build(),
                contentDescription = "Artist Image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (albums.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(albums.first().mediaMetadata.artworkUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}