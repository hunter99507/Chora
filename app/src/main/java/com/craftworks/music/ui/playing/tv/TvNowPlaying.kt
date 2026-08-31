@file:OptIn(UnstableApi::class)

package com.craftworks.music.ui.playing.tv

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.data.repository.LyricsState
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.managers.settings.OLEDProtectionMode
import com.craftworks.music.player.ChoraMediaLibraryService
import com.craftworks.music.ui.elements.tv.TvHorizontalSongCard
import com.craftworks.music.ui.playing.LyricsView
import com.craftworks.music.ui.screens.tv.requestFocusOnFirstGainingVisibility
import com.gigamole.composefadingedges.marqueeHorizontalFadingEdges

@Preview(device = "id:tv_1080p", showBackground = true, showSystemUi = true)
@Composable
fun TvNowPlaying(
    mediaController: MediaController? = null,
    iconColor: Color = Color.Black,
    metadata: MediaMetadata? = null,
    onRefreshLyrics: () -> Unit = {}
){
    val lyrics by LyricsState.lyrics.collectAsStateWithLifecycle()

    val oledProtectionMode by AppearanceSettingsManager(LocalContext.current).oledProtectionMode.collectAsStateWithLifecycle(
        OLEDProtectionMode.OFF
    )

    val iconTextColor by animateColorAsState(
        targetValue = iconColor,
        animationSpec = tween(1000, 0, FastOutSlowInEasing),
        label = "Animated text color"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 36.dp, end = 36.dp, top = 20.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (oledProtectionMode == OLEDProtectionMode.LYRICS_ONLY) {
            Text(
                text = StringBuilder()
                    .append(metadata?.title)
                    .append(" • ")
                    .append(metadata?.albumTitle)
                    .append(" • ")
                    .append(metadata?.artist)
                    .toString(),
                color = iconTextColor,
                maxLines = 1,
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .padding(bottom = 8.dp)
                    .marqueeHorizontalFadingEdges(marqueeProvider = { Modifier.basicMarquee() })
            )
        }

        // --- Main Content Area (Album Card & Synced Lyrics) ---
        Row (
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (oledProtectionMode == OLEDProtectionMode.OFF) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StandardCardContainer(
                        imageCard = {
                            Box(
                                Modifier
                                    .height(250.dp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(
                                            metadata?.artworkUri.toString()
                                                .replace("size=128", "size=500")
                                        )
                                        .diskCachePolicy(CachePolicy.DISABLED)
                                        .placeholderMemoryCacheKey(metadata?.artworkUri.toString())
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Album Cover Art",
                                    fallback = painterResource(R.drawable.placeholder),
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .shadow(8.dp, RoundedCornerShape(16.dp), clip = true)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                            }
                        },
                        title = {
                            Text(
                                text = metadata?.title.toString(),
                                color = iconTextColor,
                                maxLines = 1,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .marqueeHorizontalFadingEdges(marqueeProvider = { Modifier.basicMarquee() })
                            )
                        },
                        subtitle = {
                            if (metadata?.albumTitle != null && metadata.recordingYear != null) {
                                Text(
                                    text = metadata.albumTitle.toString() + if (metadata.recordingYear != 0) " • " + metadata.recordingYear else "",
                                    color = iconTextColor.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    modifier = Modifier
                                        .marqueeHorizontalFadingEdges(marqueeProvider = { Modifier.basicMarquee() })
                                )
                            }
                        },
                        description = {
                            Text(
                                text = metadata?.artist.toString(),
                                color = iconTextColor.copy(alpha = 0.8f),
                                maxLines = 1,
                                modifier = Modifier
                                    .marqueeHorizontalFadingEdges(marqueeProvider = { Modifier.basicMarquee() })
                            )
                        }
                    )
                }
            }

            if (oledProtectionMode == OLEDProtectionMode.MINIMAL) {
                TvHorizontalSongCard(
                    song = mediaController?.currentMediaItem ?: MediaItem.EMPTY,
                    modifier = Modifier
                        .focusable(false)
                        .graphicsLayer {
                            alpha = 0.75f
                        },
                    showTrackNumber = false,
                    onClick = { },
                    onLongClick = { }
                )
            }

            AnimatedVisibility(
                visible = metadata?.mediaType != MediaMetadata.MEDIA_TYPE_RADIO_STATION && lyrics.isNotEmpty() && oledProtectionMode != OLEDProtectionMode.MINIMAL,
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(
                        if (oledProtectionMode == OLEDProtectionMode.LYRICS_ONLY) 0.6f
                        else 1f
                    )
            ) {
                Box(
                    modifier = Modifier
                        .focusable(false)
                        .focusProperties {
                            canFocus = false
                        }
                ) {
                    LyricsView(
                        iconTextColor,
                        true,
                        mediaController,
                        PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                        onRefreshLyrics
                    )
                }
            }
        }

        // --- Static Sleek Bottom Playback Bar ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(20.dp))
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (metadata?.mediaType != MediaMetadata.MEDIA_TYPE_RADIO_STATION) {
                PlaybackProgressSlider(iconTextColor, mediaController, metadata)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChoraMediaLibraryService.getInstance()?.player?.let { player ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ShuffleButton(
                            player,
                            Modifier.size(IconButtonDefaults.SmallButtonSize)
                        )

                        PreviousSongButton(
                            player,
                            Modifier.size(IconButtonDefaults.MediumButtonSize)
                        )

                        PlayPauseButton(
                            player,
                            Modifier
                                .size(IconButtonDefaults.LargeButtonSize)
                                .requestFocusOnFirstGainingVisibility()
                        )

                        NextSongButton(
                            player,
                            Modifier.size(IconButtonDefaults.MediumButtonSize)
                        )

                        RepeatButton(
                            player,
                            Modifier.size(IconButtonDefaults.SmallButtonSize)
                        )
                    }
                }
            }
        }
    }
}