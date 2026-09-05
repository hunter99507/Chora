package com.craftworks.music.ui.elements

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.craftworks.music.managers.ArtistOfTheDayData
import kotlinx.coroutines.delay

@Composable
fun SongOfTheDayCard(
    artistOfTheDay: ArtistOfTheDayData? = null,
    fallbackSong: MediaItem? = null,
    onPlaySong: (selectedSong: MediaItem, allSongs: List<MediaItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    val songs = artistOfTheDay?.slideshowSongs ?: listOfNotNull(fallbackSong)
    if (songs.isEmpty()) return

    val artistKey = artistOfTheDay?.artistName ?: fallbackSong?.mediaId ?: ""
    var currentIndex by remember(artistKey) { mutableIntStateOf(0) }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(artistKey, songs.size) {
        if (songs.size > 1) {
            android.util.Log.d("SongOfTheDayCard", "Starting 5s slideshow timer for $artistKey with ${songs.size} songs")
            while (true) {
                delay(5000L)
                if (!isPressed) {
                    currentIndex = (currentIndex + 1) % songs.size
                    android.util.Log.d("SongOfTheDayCard", "Advanced to slide $currentIndex/${songs.size}: ${songs.getOrNull(currentIndex)?.mediaMetadata?.title}")
                }
            }
        }
    }

    val currentSong = songs.getOrElse(currentIndex) { songs.first() }
    val title = currentSong.mediaMetadata.title?.toString().takeUnless { it.isNullOrBlank() } ?: "Unknown Title"
    val artist = currentSong.mediaMetadata.artist?.toString().takeUnless { it.isNullOrBlank() } ?: (artistOfTheDay?.artistName ?: "Unknown Artist")
    val artworkUri = currentSong.mediaMetadata.artworkUri
    val context = LocalContext.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                val allSongs = artistOfTheDay?.allArtistSongs ?: songs
                onPlaySong(currentSong, allSongs)
            },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 6.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Crossfade(
                targetState = artworkUri,
                animationSpec = tween(durationMillis = 800),
                modifier = Modifier.fillMaxSize(),
                label = "CoverArtCrossfade"
            ) { uri ->
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Cover artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.35f to Color.Black.copy(alpha = 0.20f),
                                1.0f to Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        AnimatedContent(
                            targetState = title,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(400))
                            },
                            label = "TitleAnimation"
                        ) { targetTitle ->
                            Text(
                                text = targetTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    FilledIconButton(
                        onClick = {
                            val allSongs = artistOfTheDay?.allArtistSongs ?: songs
                            onPlaySong(currentSong, allSongs)
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play song",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SongOfTheDayCard(
    song: MediaItem,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    SongOfTheDayCard(
        artistOfTheDay = null,
        fallbackSong = song,
        onPlaySong = { _, _ -> onPlay() },
        modifier = modifier
    )
}
