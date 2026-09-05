package com.craftworks.music.ui.screens.tv

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.craftworks.music.R
import com.craftworks.music.data.model.Screen
import com.craftworks.music.data.model.toAlbum
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.elements.tv.TvAlbumCard
import com.craftworks.music.ui.screens.ArtistHeaderCollage
import com.craftworks.music.ui.viewmodels.ArtistsScreenViewModel
import com.craftworks.music.util.AmbientGradientBackground
import com.craftworks.music.util.PaletteHelper
import kotlinx.coroutines.launch
import java.net.URLEncoder

@Composable
@Preview
fun TvArtistDetailsScreen(
    navHostController: NavHostController = rememberNavController(),
    mediaController: MediaController? = null,
    viewModel: ArtistsScreenViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val showLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val artist = viewModel.selectedArtist.collectAsStateWithLifecycle().value
    val artistAlbums = viewModel.artistAlbums.collectAsStateWithLifecycle().value

    var paletteColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    val firstCover = remember(artistAlbums, artist?.artistImageUrl) {
        val albumArt = artistAlbums.firstOrNull()?.mediaMetadata?.artworkUri?.toString()
        if (!albumArt.isNullOrBlank()) albumArt else artist?.artistImageUrl
    }
    LaunchedEffect(firstCover) {
        if (!firstCover.isNullOrBlank()) {
            val colors = PaletteHelper.extractColorsFromUri(firstCover, context)
            if (colors.isNotEmpty()) {
                paletteColors = colors
            }
        }
    }

    AnimatedVisibility(
        visible = showLoading || artist == null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                strokeWidth = 6.dp
            )
        }
    }

    AnimatedVisibility(
        visible = !showLoading && artist != null,
        enter = fadeIn()
    ) {
        val coroutineScope = rememberCoroutineScope()
        val playRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            playRequester.requestFocus()
        }
        AmbientGradientBackground(
            colors = paletteColors,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp),
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Hero Artwork Collage with Melting Bottom Edge
                item(span = { GridItemSpan(5) }) {
                    ArtistHeaderCollage(
                        artistImageUrl = artist?.artistImageUrl,
                        albums = artistAlbums,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(330.dp)
                    )
                }

                // 2. Artist Title, Metadata, and Action Buttons
                item(span = { GridItemSpan(5) }) {
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusGroup()
                    ) {
                        Text(
                            text = artist?.name ?: "",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "${artistAlbums.size} ${if (artistAlbums.size == 1) "album" else "albums"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val allArtistSongsList = artistAlbums.map {
                                            it.mediaMetadata.extras?.getString("navidromeID").let {
                                                val album = viewModel.getAlbum(it ?: "")
                                                if (album.isNotEmpty())
                                                    album.subList(1, album.size)
                                                else
                                                    emptyList()
                                            }
                                        }

                                        SongHelper.play(
                                            allArtistSongsList.flatten(),
                                            0,
                                            mediaController
                                        )
                                        navHostController.navigate(Screen.NowPlayingLandscape.route) {
                                            launchSingleTop = true
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
                                        val allArtistSongsList = artistAlbums.flatMap {
                                            val navId = it.mediaMetadata.extras?.getString("navidromeID") ?: it.mediaId
                                            val album = viewModel.getAlbum(navId)
                                            if (album.isNotEmpty())
                                                album.subList(1, album.size)
                                            else
                                                emptyList()
                                        }

                                        if (allArtistSongsList.isNotEmpty()) {
                                            SongHelper.play(
                                                allArtistSongsList,
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

                items(artistAlbums) { album ->
                    TvAlbumCard(
                        album = album,
                        onClick = {
                            val album = album.toAlbum()
                            val encodedImage = URLEncoder.encode(album.coverArt, "UTF-8")
                            navHostController.navigate(Screen.AlbumDetails.route + "/${album.navidromeID}/$encodedImage") {
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }
        }
    }
}