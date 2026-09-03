@file:OptIn(UnstableApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.craftworks.music.ui.playing

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.data.repository.LyricsState
import com.craftworks.music.fadingEdge
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
    accentColor: Color = Color.White,
    lyricsOpen: Boolean = false,
    sleepTimerMinutes: Int = 10,
    onToggleLyrics: () -> Unit = {},
    onToggleQueue: () -> Unit = {},
    onToggleDetails: () -> Unit = {},
    onOpenSleepTimer: () -> Unit = {},
    onRefreshLyrics: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    val animatedAccentColor by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(durationMillis = 600),
        label = "Animated Accent Color"
    )

    val imageFadingEdge = remember {
        Brush.verticalGradient(
            0.0f to Color.Red,
            0.70f to Color.Red.copy(alpha = 0.95f),
            1.0f to Color.Transparent
        )
    }

    val context = LocalContext.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val lyrics by LyricsState.lyrics.collectAsStateWithLifecycle()
    val loadingLyrics by LyricsState.loading.collectAsStateWithLifecycle()

    val isLyricsActive = lyricsOpen && (lyrics.isNotEmpty() || loadingLyrics)
    val isRadio = metadata?.mediaType == MediaMetadata.MEDIA_TYPE_RADIO_STATION

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        //region Top Section: Full Bleed Artwork / Lyrics
        AnimatedContent(
            targetState = isLyricsActive,
            modifier = Modifier.fillMaxWidth(),
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
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = (metadata?.title ?: metadata?.displayTitle).toString(),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        fontWeight = FontWeight.Bold,
                        color = iconColor,
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
                        color = iconColor.copy(alpha = 0.75f),
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .marqueeHorizontalFadingEdges(marqueeProvider = { Modifier.basicMarquee() })
                    )
                    LyricsView(iconColor, false, mediaController, onRefreshLyrics = onRefreshLyrics)
                }
            } else {
                val artworkHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.585f).coerceAtLeast(435.dp)
                val coroutineScope = rememberCoroutineScope()

                // Resolve the active Player instance (ExoPlayer from service or MediaController)
                val activePlayer = remember(mediaController) {
                    ChoraMediaLibraryService.getInstance()?.player ?: mediaController
                }

                // Track adjacent and current items respecting shuffle and repeat
                var currentItem by remember { mutableStateOf(activePlayer?.currentMediaItem) }
                var prevItem by remember {
                    mutableStateOf(
                        if (activePlayer?.hasPreviousMediaItem() == true) {
                            val idx = activePlayer.previousMediaItemIndex
                            if (idx in 0 until activePlayer.mediaItemCount) activePlayer.getMediaItemAt(idx) else null
                        } else null
                    )
                }
                var nextItem by remember {
                    mutableStateOf(
                        if (activePlayer?.hasNextMediaItem() == true) {
                            val idx = activePlayer.nextMediaItemIndex
                            if (idx in 0 until activePlayer.mediaItemCount) activePlayer.getMediaItemAt(idx) else null
                        } else null
                    )
                }

                val pagerState = rememberPagerState(
                    initialPage = 1,
                    pageCount = { 3 }
                )

                fun updateAdjacentItems() {
                    val p = ChoraMediaLibraryService.getInstance()?.player ?: mediaController
                    currentItem = p?.currentMediaItem
                    prevItem = if (p?.hasPreviousMediaItem() == true) {
                        val idx = p.previousMediaItemIndex
                        if (idx in 0 until p.mediaItemCount) p.getMediaItemAt(idx) else null
                    } else null
                    nextItem = if (p?.hasNextMediaItem() == true) {
                        val idx = p.nextMediaItemIndex
                        if (idx in 0 until p.mediaItemCount) p.getMediaItemAt(idx) else null
                    } else null
                }

                val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
                var hasTriggeredForTarget by remember { mutableStateOf(false) }

                DisposableEffect(mediaController) {
                    val p = ChoraMediaLibraryService.getInstance()?.player ?: mediaController
                    if (p == null) {
                        onDispose { }
                    } else {
                        val listener = object : Player.Listener {
                            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                                updateAdjacentItems()
                                hasTriggeredForTarget = false
                                coroutineScope.launch {
                                    pagerState.scrollToPage(1)
                                }
                            }
                            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                                updateAdjacentItems()
                            }
                            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                                updateAdjacentItems()
                            }
                            override fun onRepeatModeChanged(repeatMode: Int) {
                                updateAdjacentItems()
                            }
                        }
                        p.addListener(listener)
                        updateAdjacentItems()
                        onDispose { p.removeListener(listener) }
                    }
                }

                // Trigger song seek immediately upon finger release towards targetPage or on settle, eliminating delay
                LaunchedEffect(pagerState, isDragged) {
                    snapshotFlow {
                        Triple(isDragged, pagerState.targetPage, pagerState.settledPage)
                    }.collect { (dragged, target, settled) ->
                        if (target == 1 && settled == 1) {
                            hasTriggeredForTarget = false
                            return@collect
                        }

                        // As soon as the user lets go (or on quick flick) and target is 0 or 2, trigger skip IMMEDIATELY
                        if (!dragged && (target != 1 || settled != 1) && !hasTriggeredForTarget) {
                            val destination = if (target != 1) target else settled
                            val p = ChoraMediaLibraryService.getInstance()?.player ?: mediaController ?: return@collect
                            hasTriggeredForTarget = true
                            if (destination == 2) {
                                if (p.hasNextMediaItem()) {
                                    p.seekToNextMediaItem()
                                } else {
                                    p.seekToNext()
                                }
                                p.play()
                            } else if (destination == 0) {
                                if (p.hasPreviousMediaItem()) {
                                    p.seekToPreviousMediaItem()
                                } else {
                                    p.seekToPrevious()
                                }
                                p.play()
                            }

                            // Safety reset if onMediaItemTransition didn't trigger
                            kotlinx.coroutines.delay(200)
                            updateAdjacentItems()
                            if (pagerState.currentPage != 1) {
                                pagerState.scrollToPage(1)
                            }
                            hasTriggeredForTarget = false
                        }
                    }
                }

                fun artworkUriForItem(item: MediaItem?): String {
                    val uri = item?.mediaMetadata?.artworkUri?.toString() ?: ""
                    return if (uri.isNotEmpty()) uri.replace(Regex("size=\\d+"), "size=600") else ""
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(artworkHeight)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        pageSpacing = 0.dp,
                        key = { it }
                    ) { page ->
                        val pageArtworkUri = when (page) {
                            0 -> artworkUriForItem(prevItem)
                            1 -> artworkUriForItem(currentItem).ifEmpty {
                                (metadata?.artworkUri?.toString() ?: "").replace(Regex("size=\\d+"), "size=600")
                            }
                            2 -> artworkUriForItem(nextItem)
                            else -> ""
                        }

                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(pageArtworkUri.ifEmpty { null })
                                .placeholderMemoryCacheKey(metadata?.artworkUri?.toString())
                                .diskCachePolicy(CachePolicy.DISABLED)
                                .build(),
                            contentDescription = "Album Cover Art",
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .fadingEdge(imageFadingEdge)
                        )
                    }

                    // Left edge back gesture guard: consumes horizontal drags in the system back zone so pager doesn't move
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(24.dp)
                            .fillMaxHeight()
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { _, _ -> }
                            }
                    )

                    // Right edge back gesture guard
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(24.dp)
                            .fillMaxHeight()
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { _, _ -> }
                            }
                    )
                }
            }
        }
        //endregion

        //region Middle Section: Title & Seekbar Area (Shifted Upwards)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AnimatedContent(
                    targetState = metadata?.title.toString(),
                    transitionSpec = {
                        (slideInHorizontally(
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                            initialOffsetX = { it / 3 }
                        ) + fadeIn(animationSpec = tween(300)))
                            .togetherWith(
                                slideOutHorizontally(
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                                    targetOffsetX = { -it / 3 }
                                ) + fadeOut(animationSpec = tween(200))
                            )
                    },
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

                AnimatedContent(
                    targetState = metadata?.artist.toString(),
                    transitionSpec = {
                        (slideInHorizontally(
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                            initialOffsetX = { it / 3 }
                        ) + fadeIn(animationSpec = tween(300)))
                            .togetherWith(
                                slideOutHorizontally(
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                                    targetOffsetX = { -it / 3 }
                                ) + fadeOut(animationSpec = tween(200))
                            )
                    },
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
                        PlaybackProgressSlider(animatedAccentColor, iconColor, mediaController, metadata)
                    }
                }
            }
        }
        //endregion

        //region Bottom Section: Playback Controls (Shifted down)
        val isDarkControlsBackground = remember(iconColor) {
            ColorUtils.calculateLuminance(iconColor.toArgb()) > 0.45
        }
        val vibrantControlsColor = remember(animatedAccentColor, isDarkControlsBackground) {
            getVibrantSeekbarColor(animatedAccentColor, isDarkControlsBackground)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChoraMediaLibraryService.getInstance()?.player?.let { player ->
                RepeatButton(player, vibrantControlsColor, Modifier.size(28.dp))
                PreviousSongButton(player, vibrantControlsColor, Modifier.size(34.dp))
                PlayPauseButton(player, vibrantControlsColor, Modifier.size(76.dp))
                NextSongButton(player, vibrantControlsColor, Modifier.size(34.dp))
                ShuffleButton(player, vibrantControlsColor, Modifier.size(28.dp))
            }
        }
        //endregion
        }

        // Back Button on top left
        IconButton(
            onClick = {
                onClose()
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 8.dp, start = 8.dp)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                tint = iconColor,
                contentDescription = "Back",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}