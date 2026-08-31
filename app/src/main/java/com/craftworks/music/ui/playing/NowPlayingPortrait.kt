@file:OptIn(UnstableApi::class)

package com.craftworks.music.ui.playing

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.data.repository.LyricsState
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.player.ChoraMediaLibraryService
import com.gigamole.composefadingedges.marqueeHorizontalFadingEdges

@kotlin.OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview(
    showSystemUi = true, device = "id:pixel_9a",
    wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE, showBackground = true
)
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview(
    showSystemUi = true, device = "id:pixel",
    wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE, showBackground = true
)
@Composable
fun NowPlayingPortrait(
    mediaController: MediaController? = null,
    metadata: MediaMetadata? = null,
    iconColor: Color = Color.White,
    accentColor: Color = Color(0xFFC89B66),
    lyricsOpen: Boolean = false,
    sleepTimerMinutes: Int = 10,
    onToggleLyrics: () -> Unit = {},
    onToggleQueue: () -> Unit = {},
    onToggleDetails: () -> Unit = {},
    onOpenSleepTimer: () -> Unit = {},
    onRefreshLyrics: () -> Unit = {}
) {
    val animatedAccentColor by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(durationMillis = 600),
        label = "Animated Accent Color"
    )

    val context = LocalContext.current
    val lyrics by LyricsState.lyrics.collectAsStateWithLifecycle()
    val loadingLyrics by LyricsState.loading.collectAsStateWithLifecycle()

    val isLyricsActive = lyricsOpen && (lyrics.isNotEmpty() || loadingLyrics)
    val isRadio = metadata?.mediaType == MediaMetadata.MEDIA_TYPE_RADIO_STATION

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        //region Top Section: Large Elongated Artwork / Lyrics
        AnimatedContent(
            targetState = isLyricsActive,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            label = "Crossfade between artwork and lyrics view",
            transitionSpec = {
                (fadeIn(tween(300)) + slideInVertically { it / 4 }) togetherWith
                        (fadeOut(tween(200)) + slideOutVertically { -it / 4 }) using
                        SizeTransform(clip = false)
            }
        ) { showLyrics ->
            if (showLyrics) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = (metadata?.title ?: metadata?.displayTitle).toString(),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Visible,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .marqueeHorizontalFadingEdges(marqueeProvider = { Modifier.basicMarquee() })
                    )
                    Text(
                        text = metadata?.artist.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .marqueeHorizontalFadingEdges(marqueeProvider = { Modifier.basicMarquee() })
                    )
                    LyricsView(Color.White, false, mediaController, onRefreshLyrics = onRefreshLyrics)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = metadata?.artworkUri.toString().replace("size=128", "size=600"),
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        label = "Crossfade between albums"
                    ) { artworkUri ->
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(artworkUri)
                                .placeholderMemoryCacheKey(metadata?.artworkUri.toString())
                                .diskCachePolicy(CachePolicy.DISABLED)
                                .build(),
                            contentDescription = "Album Cover Art",
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.76f)
                                .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    }
                }
            }
        }
        //endregion

        //region Middle Section: Metadata & Seekbar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Crossfade(
                targetState = metadata?.title.toString(),
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                label = "Animated Song Title",
                modifier = Modifier.fillMaxWidth()
            ) { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMediumEmphasized.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = iconColor,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .marqueeHorizontalFadingEdges(marqueeProvider = { Modifier.basicMarquee() })
                )
            }

            Crossfade(
                targetState = metadata?.artist.toString(),
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                label = "Animated Artist",
                modifier = Modifier.fillMaxWidth()
            ) { artistInfo ->
                Text(
                    text = artistInfo,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = iconColor.copy(alpha = 0.85f),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .marqueeHorizontalFadingEdges(marqueeProvider = { Modifier.basicMarquee() })
                )
            }

            if (metadata?.albumTitle != null && metadata.albumTitle.toString().isNotBlank()) {
                Crossfade(
                    targetState = metadata.albumTitle.toString(),
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    label = "Animated Album",
                    modifier = Modifier.fillMaxWidth()
                ) { albumInfo ->
                    Text(
                        text = albumInfo,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = iconColor.copy(alpha = 0.65f),
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .marqueeHorizontalFadingEdges(marqueeProvider = { Modifier.basicMarquee() })
                    )
                }
            }

            if (!isRadio) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PlaybackProgressSlider(iconColor, mediaController, metadata)
                }
            }
        }
        //endregion

        //region Bottom Section: Playback Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChoraMediaLibraryService.getInstance()?.player?.let { player ->
                RepeatButton(player, iconColor, Modifier.size(28.dp))
                PreviousSongButton(player, iconColor, Modifier.size(34.dp))
                PlayPauseButton(player, iconColor, Modifier.size(76.dp))
                NextSongButton(player, iconColor, Modifier.size(34.dp))
                ShuffleButton(player, iconColor, Modifier.size(28.dp))
            }
        }
        //endregion
    }
}