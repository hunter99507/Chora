package com.craftworks.music.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.data.model.Screen
import com.craftworks.music.data.model.toAlbum
import com.craftworks.music.fadingEdge
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.elements.AlbumCard
import com.craftworks.music.ui.elements.dialogs.dialogFocusable
import com.craftworks.music.ui.viewmodels.ArtistsScreenViewModel
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
    val context = LocalContext.current
    val imageFadingEdge = Brush.verticalGradient(listOf(Color.Red.copy(0.75f), Color.Transparent))

    val coroutineScope = rememberCoroutineScope()

    // Loading spinner
    AnimatedVisibility(
        visible = showLoading,
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
        visible = artist?.name?.isNotBlank() == true,
        enter = fadeIn()
    ) {
        val headerHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.40f).coerceAtLeast(360.dp)

        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxSize()
                .dialogFocusable(),
            columns = GridCells.Adaptive(96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Group songs by their source (Local or Navidrome)
            val groupedAlbums =
                artistAlbums.groupBy { it.mediaMetadata.recordingYear }
                    .toSortedMap(compareByDescending { it })

            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Box(
                        modifier = Modifier
                            .height(headerHeight)
                            .fillMaxWidth()
                    ) {
                        //Image and Name
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(artist?.artistImageUrl)
                                .diskCacheKey(
                                    artist?.navidromeID
                                )
                                .crossfade(true)
                                .build(),
                            placeholder = painterResource(R.drawable.s_a_username),
                            fallback = painterResource(R.drawable.s_a_username),
                            contentScale = ContentScale.Crop,
                            contentDescription = "Artist Image",
                            modifier = Modifier
                                .fillMaxSize()
                                .fadingEdge(imageFadingEdge)
                                .blur(10.dp)
                        )
                        Button(
                            onClick = {
                                navHostController.popBackStack()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .padding(
                                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                                    start = 16.dp
                                )
                                .size(36.dp),
                            contentPadding = PaddingValues(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.onBackground
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                tint = MaterialTheme.colorScheme.primary,
                                contentDescription = "Go Back",
                                modifier = Modifier
                                    .height(28.dp)
                                    .size(28.dp)
                            )
                        }


                        // Album Name and Artist
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                        ) {
                            Text(
                                text = artist?.name.toString(),
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = MaterialTheme.typography.headlineLarge.fontSize,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                lineHeight = 32.sp
                            )
                        }
                    }
                }
            }

            // Play and shuffle buttons
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val allArtistSongsList = artistAlbums.flatMap {
                                    val albumId = it.mediaMetadata.extras?.getString("navidromeID") ?: it.mediaId
                                    val album = viewModel.getAlbum(albumId)
                                    if (album.size > 1) album.subList(1, album.size) else album
                                }
                                if (allArtistSongsList.isNotEmpty()) {
                                    SongHelper.play(allArtistSongsList, 0, mediaController)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, "Play Artist")
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.Action_Play), maxLines = 1)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val allArtistSongsList = artistAlbums.flatMap {
                                    val albumId = it.mediaMetadata.extras?.getString("navidromeID") ?: it.mediaId
                                    val album = viewModel.getAlbum(albumId)
                                    if (album.size > 1) album.subList(1, album.size) else album
                                }
                                if (allArtistSongsList.isNotEmpty()) {
                                    SongHelper.play(allArtistSongsList.shuffled(), 0, mediaController)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(ImageVector.vectorResource(R.drawable.round_shuffle_28), "Shuffle Artist")
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.Action_Shuffle), maxLines = 1)
                        }
                    }
                }
            }

            /* Discography header */
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.Screen_Discography),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            groupedAlbums.forEach { (groupName, albumsInGroup) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = groupName.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(top = 12.dp)
                    )
                }

                itemsIndexed(albumsInGroup) { index, album ->
                    AlbumCard(
                        album = album,
                        onClick = {
                            val album = album.toAlbum()
                            val encodedImage = URLEncoder.encode(album.coverArt, "UTF-8")
                            navHostController.navigate(Screen.AlbumDetails.route + "/${album.navidromeID}/$encodedImage") {
                                launchSingleTop = true
                            }
                        },
                        onPlay = {
                            coroutineScope.launch {
                                val mediaItems = viewModel.getAlbum(album.mediaMetadata.extras?.getString("navidromeID") ?: "")
                                if (mediaItems.isNotEmpty())
                                    SongHelper.play(
                                        mediaItems = mediaItems.subList(1, mediaItems.size),
                                        index = 0,
                                        mediaController = mediaController
                                    )
                            }
                        }
                    )
                }
            }
        }
    }
}