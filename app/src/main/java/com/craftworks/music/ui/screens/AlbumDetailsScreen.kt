package com.craftworks.music.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import com.craftworks.music.data.model.Screen
import com.craftworks.music.util.AmbientGradientBackground
import com.craftworks.music.util.PaletteHelper
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import com.craftworks.music.ui.elements.LocalBottomPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.StarRating
import androidx.media3.session.MediaController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.fadingEdge
import com.craftworks.music.formatDurationSummary
import com.craftworks.music.formatMilliseconds
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.player.SongHelper
import com.craftworks.music.providers.navidrome.downloadNavidromeAlbum
import com.craftworks.music.ui.elements.GenrePill
import com.craftworks.music.ui.elements.HorizontalSongCard
import com.craftworks.music.ui.elements.dialogs.AddSongToPlaylist
import com.craftworks.music.ui.elements.dialogs.RatingDialog
import com.craftworks.music.ui.elements.dialogs.dialogFocusable
import com.craftworks.music.ui.elements.dialogs.showAddSongToPlaylistDialog
import com.craftworks.music.ui.viewmodels.AlbumDetailsViewModel
import com.craftworks.music.ui.viewmodels.ArtistsScreenViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalComposeUiApi::class)
@ExperimentalFoundationApi
@Composable
fun AlbumDetails(
    selectedAlbumId: String = "",
    selectedAlbumImage: Uri = Uri.EMPTY,
    navHostController: NavHostController = rememberNavController(),
    mediaController: MediaController? = null,
    viewModel: AlbumDetailsViewModel = hiltViewModel()
) {
    val imageFadingEdge = Brush.verticalGradient(listOf(Color.Red.copy(0.75f), Color.Transparent))

    val parentEntry = remember(navHostController.currentBackStackEntry) {
        navHostController.getBackStackEntry("main_graph")
    }
    val artistsViewModel: ArtistsScreenViewModel = hiltViewModel(parentEntry)

    var showLoading by remember { mutableStateOf(false) }
    val currentAlbum = viewModel.songsInAlbum.collectAsStateWithLifecycle().value
    val showTrackNumbers by AppearanceSettingsManager(LocalContext.current).showTrackNumbersFlow.collectAsStateWithLifecycle(false)

    var songToRate by remember { mutableStateOf<MediaItem?>(null) }

    val context = LocalContext.current

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

    // Loading spinner
    AnimatedVisibility(
        visible = currentAlbum.isEmpty() && showLoading,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
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

    // Main Content
    AnimatedVisibility(
        visible = currentAlbum.isNotEmpty(),
        enter = fadeIn()
    ) {
        var isStarred by remember { mutableStateOf(currentAlbum[0].mediaMetadata.extras?.getString("starred")?.isNotEmpty() ?: false) }
        val requester = remember { FocusRequester() }

        val coroutineScope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            requester.requestFocus()
        }

        BackHandler {
            if (!navHostController.popBackStack()) {
                navHostController.navigate(Screen.Albums.route) { launchSingleTop = true }
            }
        }

        val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
        val headerHeight = (screenHeight * 0.46f).coerceIn(340.dp, 460.dp)

        AmbientGradientBackground(colors = paletteColors) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .dialogFocusable(),
                contentPadding = PaddingValues(
                    bottom = 64.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + LocalBottomPadding.current
                ),
            ) {
                // 1. Header (Crisp artwork with gradient fade to ambient background)
                item {
                    Box(
                        modifier = Modifier
                            .height(headerHeight)
                            .fillMaxWidth()
                    ) {
                        val fadeBrush = remember {
                            Brush.verticalGradient(
                                0.0f to Color.Black,
                                0.40f to Color.Black,
                                0.70f to Color.Black.copy(alpha = 0.6f),
                                0.90f to Color.Black.copy(alpha = 0.2f),
                                1.0f to Color.Transparent
                            )
                        }

                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(selectedAlbumImage)
                                .diskCacheKey(selectedAlbumId)
                                .crossfade(true)
                                .build(),
                            placeholder = painterResource(R.drawable.placeholder),
                            fallback = painterResource(R.drawable.placeholder),
                            contentScale = ContentScale.Crop,
                            contentDescription = "Album Image",
                            modifier = Modifier
                                .fillMaxSize()
                                .fadingEdge(fadeBrush)
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
                                        navHostController.navigate(Screen.Albums.route) { launchSingleTop = true }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                tint = Color.White,
                                contentDescription = "Back",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Top-right actions: Download and Favorite buttons
                        if (currentAlbum[0].mediaMetadata.extras?.getString("navidromeID")?.startsWith("Local_") == false) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(
                                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                                        end = 16.dp
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .clickable {
                                            coroutineScope.launch {
                                                downloadNavidromeAlbum(
                                                    context,
                                                    currentAlbum[0].mediaMetadata.title.toString(),
                                                    currentAlbum.subList(1, currentAlbum.size)
                                                )
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.rounded_download_24),
                                        contentDescription = "Download Album",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .clickable {
                                            coroutineScope.launch {
                                                if (isStarred)
                                                    viewModel.unstarAlbum(
                                                        currentAlbum[0].mediaMetadata.extras?.getString("navidromeID").toString()
                                                    )
                                                else
                                                    viewModel.starAlbum(
                                                        currentAlbum[0].mediaMetadata.extras?.getString("navidromeID").toString()
                                                    )
                                                viewModel.loadAlbumDetails(selectedAlbumId)
                                                isStarred = !isStarred
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Crossfade(targetState = isStarred) { starred ->
                                        if (starred) Icon(
                                            imageVector = ImageVector.vectorResource(R.drawable.round_favorite_24),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        else Icon(
                                            imageVector = ImageVector.vectorResource(R.drawable.round_favorite_border_24),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Album Title (Left-aligned, bold headline)
                item {
                    Text(
                        text = currentAlbum[0].mediaMetadata.title.toString(),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                    )
                }

                // 3. Subtitle Line & Genre Pills
                item {
                    val songsOnly = if (currentAlbum.size > 1) currentAlbum.subList(1, currentAlbum.size) else currentAlbum
                    val totalDuration = (songsOnly.sumOf { it.mediaMetadata.durationMs ?: 0L } / 1000).toInt()
                    val year = currentAlbum[0].mediaMetadata.recordingYear

                    val artistName = currentAlbum[0].mediaMetadata.artist?.toString() ?: ""

                    val otherMetadata = buildString {
                        if (year != null && year > 0) {
                            append("$year • ")
                        }
                        append("${songsOnly.size} ${if (songsOnly.size == 1) "track" else "tracks"}")
                        if (totalDuration > 0) {
                            append(" • ${formatDurationSummary(totalDuration)}")
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (artistName.isNotBlank()) {
                                Surface(
                                    onClick = {
                                        val fallbackId = currentAlbum[0].mediaMetadata.extras?.getString("artistId") ?: ""
                                        artistsViewModel.selectArtistByName(artistName, fallbackId)
                                        navHostController.navigate(Screen.ArtistDetails.route) {
                                            launchSingleTop = true
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = ImageVector.vectorResource(R.drawable.rounded_artist_24),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = artistName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            Text(
                                text = otherMetadata,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }

                        if (!currentAlbum[0].mediaMetadata.genre.isNullOrEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                currentAlbum[0].mediaMetadata.genre?.split(",")?.forEach {
                                    GenrePill(it.trim())
                                }
                            }
                        }
                    }
                }

                // 4. Action Buttons (Play & Shuffle)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val songs = if (currentAlbum.size > 1) currentAlbum.subList(1, currentAlbum.size) else currentAlbum
                                    if (songs.isNotEmpty()) {
                                        SongHelper.play(
                                            songs,
                                            0,
                                            mediaController
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(requester)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.height(24.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, "Play Album")
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.Action_Play), maxLines = 1, fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val songs = if (currentAlbum.size > 1) currentAlbum.subList(1, currentAlbum.size) else currentAlbum
                                    if (songs.isNotEmpty()) {
                                        SongHelper.play(
                                            songs,
                                            0,
                                            mediaController,
                                            shuffle = true
                                        )
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
                                Icon(ImageVector.vectorResource(R.drawable.round_shuffle_28), "Shuffle Album")
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.Action_Shuffle), maxLines = 1, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

            // Album Songs
            val groupedAlbums = currentAlbum.subList(1, currentAlbum.size).groupBy { song ->
                song.mediaMetadata.discNumber
            }

            if (groupedAlbums.size > 1) {
                groupedAlbums.forEach { (discNumber, albumsInGroup) ->
                    item() {
                        Column {
                            Text(
                                text = stringResource(R.string.Album_Disc_Number) + discNumber.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            )
                            HorizontalDivider(
                                modifier = Modifier
                                    .height(1.dp)
                                    .fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                        }
                    }
                    items(albumsInGroup) { song ->
                        HorizontalSongCard(
                            song = song,
                            modifier = Modifier.animateItem(),
                            showTrackNumber = showTrackNumbers,
                            onClick = {
                                coroutineScope.launch {
                                    SongHelper.play(
                                        currentAlbum.subList(1, currentAlbum.size),
                                        currentAlbum.subList(1, currentAlbum.size).indexOf(song),
                                        mediaController
                                    )
                                }
                            },
                            onAddToQueue = {
                                mediaController?.addMediaItem(song)
                            },
                            onSetRating = { songToRate = song }
                        )
                    }
                }
            }
            else {
                items(currentAlbum.subList(1, currentAlbum.size)) { song ->
                    HorizontalSongCard(
                        song = song,
                        modifier = Modifier.animateItem(),
                        showTrackNumber = showTrackNumbers,
                        onClick = {
                            coroutineScope.launch {
                                SongHelper.play(
                                    currentAlbum.subList(1, currentAlbum.size),
                                    currentAlbum.subList(1, currentAlbum.size).indexOf(song),
                                    mediaController
                                )
                            }
                        },
                        onAddToQueue = {
                            mediaController?.addMediaItem(song)
                        },
                        onSetRating = { songToRate = song }
                    )
                }
            }
        }
    }
}

    if(showAddSongToPlaylistDialog.value)
        AddSongToPlaylist(setShowDialog =  { showAddSongToPlaylistDialog.value = it } )

    songToRate?.let { song ->
        RatingDialog(
            currentRating = (song.mediaMetadata.userRating as? StarRating)?.starRating?.toInt() ?: 0,
            onDismiss = { songToRate = null },
            onSetRating = { rating ->
                viewModel.setSongRating(song.mediaMetadata.extras?.getString("navidromeID") ?: "", rating)
                songToRate = null
            }
        )
    }
}