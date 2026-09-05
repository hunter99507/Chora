@file:OptIn(UnstableApi::class)

package com.craftworks.music.ui.playing.tv

import androidx.annotation.OptIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.tv.material3.Border
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.formatMilliseconds
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.managers.settings.OLEDProtectionMode
import com.craftworks.music.player.ChoraMediaLibraryService
import com.craftworks.music.ui.elements.tv.TvHorizontalSongCard
import com.craftworks.music.ui.playing.getVibrantSeekbarColor
import com.craftworks.music.ui.screens.tv.requestFocusOnFirstGainingVisibility
import com.gigamole.composefadingedges.marqueeHorizontalFadingEdges

@OptIn(ExperimentalTvMaterial3Api::class)
@Preview(device = "id:tv_1080p", showBackground = true, showSystemUi = true)
@Composable
fun TvNowPlaying(
    mediaController: MediaController? = null,
    iconColor: Color = Color.Black,
    accentColor: Color = Color.White,
    metadata: MediaMetadata? = null,
    onRefreshLyrics: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    val oledProtectionMode by AppearanceSettingsManager(LocalContext.current)
        .oledProtectionMode.collectAsStateWithLifecycle(OLEDProtectionMode.OFF)

    val iconTextColor by animateColorAsState(
        targetValue = iconColor,
        animationSpec = tween(1000, 0, FastOutSlowInEasing),
        label = "Animated text color"
    )

    val animatedAccentColor by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(1000, 0, FastOutSlowInEasing),
        label = "Animated accent color"
    )

    val themePrimary = MaterialTheme.colorScheme.primary
    val isDarkBackground = remember(iconColor) {
        ColorUtils.calculateLuminance(iconColor.toArgb()) > 0.45
    }
    val highlightColor = remember(animatedAccentColor, themePrimary, isDarkBackground) {
        val baseColor = if (animatedAccentColor != Color.White && animatedAccentColor != Color.Black) animatedAccentColor else themePrimary
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(baseColor.toArgb(), hsl)
        if (isDarkBackground) {
            hsl[1] = hsl[1].coerceAtLeast(0.75f)
            hsl[2] = hsl[2].coerceIn(0.65f, 0.82f)
        } else {
            hsl[2] = hsl[2].coerceAtMost(0.35f)
        }
        Color(ColorUtils.HSLToColor(hsl))
    }

    // Queue tracking from MediaController
    val currentList = remember { mutableStateListOf<MediaItem>() }
    var currentIndex by remember { mutableIntStateOf(mediaController?.currentMediaItemIndex ?: 0) }

    val playPauseFocusRequester = remember { FocusRequester() }
    val queueFocusRequester = remember { FocusRequester() }
    var isQueueFocused by remember { mutableStateOf(false) }

    BackHandler {
        if (isQueueFocused) {
            isQueueFocused = false
            playPauseFocusRequester.requestFocus()
        } else {
            onClose()
        }
    }

    DisposableEffect(mediaController) {
        if (mediaController == null) return@DisposableEffect onDispose {}

        fun syncQueue() {
            currentList.clear()
            val count = mediaController.mediaItemCount
            currentList.addAll(List(count) { i -> mediaController.getMediaItemAt(i) })
            currentIndex = mediaController.currentMediaItemIndex
        }

        syncQueue()

        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                syncQueue()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncQueue()
            }
        }

        mediaController.addListener(listener)
        onDispose { mediaController.removeListener(listener) }
    }

    val queueListState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (currentIndex in currentList.indices) {
            queueListState.animateScrollToItem(maxOf(0, currentIndex - 1))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 36.dp, end = 36.dp, top = 18.dp, bottom = 12.dp)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                    if (isQueueFocused) {
                        isQueueFocused = false
                        playPauseFocusRequester.requestFocus()
                        true
                    } else {
                        onClose()
                        true
                    }
                } else {
                    false
                }
            },
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // OLED Minimal Protection View
        if (oledProtectionMode == OLEDProtectionMode.MINIMAL) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TvHorizontalSongCard(
                    song = mediaController?.currentMediaItem ?: MediaItem.EMPTY,
                    modifier = Modifier
                        .focusable(false)
                        .graphicsLayer { alpha = 0.75f },
                    showTrackNumber = false,
                    onClick = { },
                    onLongClick = { }
                )
            }
        } else {
            // --- Main Content Area: Dual-Pane (Album Showcase + Up Next Queue) ---
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // LEFT PANE: Album Showcase, Metadata & Format Badges
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = 12.dp),
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
                                iconTextColor.copy(alpha = 0.15f),
                                RoundedCornerShape(20.dp)
                            )
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(
                                    metadata?.artworkUri.toString()
                                        .replace(Regex("size=\\d+"), "size=800")
                                )
                                .placeholderMemoryCacheKey(metadata?.artworkUri.toString())
                                .crossfade(true)
                                .build(),
                            contentDescription = "Album Cover Art",
                            fallback = painterResource(R.drawable.placeholder),
                            error = painterResource(R.drawable.placeholder),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Track Title
                    Text(
                        text = metadata?.title?.toString() ?: "",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = iconTextColor,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .marqueeHorizontalFadingEdges(marqueeProvider = { Modifier.basicMarquee() })
                    )

                    Spacer(Modifier.height(4.dp))

                    // Artist Name
                    Text(
                        text = metadata?.artist?.toString() ?: "",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = highlightColor,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .marqueeHorizontalFadingEdges(marqueeProvider = { Modifier.basicMarquee() })
                    )

                    // Album & Recording Year
                    val albumYear = buildString {
                        if (!metadata?.albumTitle.isNullOrBlank()) {
                            append(metadata.albumTitle)
                        }
                        if (metadata?.recordingYear != null && metadata.recordingYear != 0) {
                            if (isNotEmpty()) append(" • ")
                            append(metadata.recordingYear)
                        }
                    }
                    if (albumYear.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = albumYear,
                            style = MaterialTheme.typography.bodyMedium,
                            color = iconTextColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .marqueeHorizontalFadingEdges(marqueeProvider = { Modifier.basicMarquee() })
                        )
                    }


                }

                // RIGHT PANE: Up Next / Live Play Queue inside a gorgeous frosted Card
                Column(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
                        .border(
                            BorderStroke(1.dp, iconTextColor.copy(alpha = 0.12f)),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .focusGroup()
                        .focusRestorer(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Queue Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.rounded_queue_music_24),
                            contentDescription = null,
                            tint = highlightColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Up Next",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = iconTextColor
                        )
                        Spacer(Modifier.weight(1f))
                        if (currentList.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(iconTextColor.copy(alpha = 0.08f))
                                    .border(1.dp, iconTextColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "${currentIndex + 1} / ${currentList.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = highlightColor
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        color = iconTextColor.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp)
                    )

                    if (currentList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Queue is empty",
                                style = MaterialTheme.typography.bodyMedium,
                                color = iconTextColor.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        LazyColumn(
                            state = queueListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(queueFocusRequester)
                                .focusGroup()
                                .focusRestorer(queueFocusRequester),
                            contentPadding = PaddingValues(vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(currentList) { idx, song ->
                                TvQueueItem(
                                    song = song,
                                    index = idx,
                                    isCurrent = idx == currentIndex,
                                    highlightColor = highlightColor,
                                    textColor = iconTextColor,
                                    modifier = Modifier
                                        .onFocusChanged { focusState ->
                                            if (focusState.isFocused) {
                                                isQueueFocused = true
                                            }
                                        }
                                        .focusProperties {
                                            left = playPauseFocusRequester
                                        },
                                    onClick = {
                                        mediaController?.seekToDefaultPosition(idx)
                                        mediaController?.play()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Sleek Floating Playback Controls ---
        Column(
            modifier = Modifier
                .widthIn(max = 960.dp)
                .fillMaxWidth(0.9f)
                .padding(top = 8.dp, bottom = 4.dp)
                .onFocusChanged {
                    if (it.hasFocus) {
                        isQueueFocused = false
                    }
                }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (metadata?.mediaType != MediaMetadata.MEDIA_TYPE_RADIO_STATION) {
                PlaybackProgressSlider(
                    color = highlightColor,
                    iconColor = iconColor,
                    mediaController = mediaController,
                    metadata = metadata,
                    modifier = Modifier.focusProperties {
                        up = queueFocusRequester
                    }
                )
            }

            val isDarkControlsBackground = remember(iconColor) {
                ColorUtils.calculateLuminance(iconColor.toArgb()) > 0.45
            }
            val vibrantControlsColor = remember(highlightColor, isDarkControlsBackground) {
                getVibrantSeekbarColor(highlightColor, isDarkControlsBackground)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    .focusProperties {
                        up = queueFocusRequester
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                (ChoraMediaLibraryService.getInstance()?.player ?: mediaController)?.let { player ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ShuffleButton(
                            player,
                            color = vibrantControlsColor,
                            modifier = Modifier.size(30.dp)
                        )

                        PreviousSongButton(
                            player,
                            color = vibrantControlsColor,
                            modifier = Modifier.size(36.dp)
                        )

                        PlayPauseButton(
                            player,
                            color = vibrantControlsColor,
                            modifier = Modifier
                                .size(48.dp)
                                .focusRequester(playPauseFocusRequester)
                                .requestFocusOnFirstGainingVisibility()
                        )

                        NextSongButton(
                            player,
                            color = vibrantControlsColor,
                            modifier = Modifier.size(36.dp)
                        )

                        RepeatButton(
                            player,
                            color = vibrantControlsColor,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvQueueItem(
    song: MediaItem,
    index: Int,
    isCurrent: Boolean,
    highlightColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val durationSec = ((song.mediaMetadata.durationMs ?: 0L) / 1000).toInt()
    val formattedDuration = if (durationSec > 0) formatMilliseconds(durationSec) else ""

    ListItem(
        selected = isCurrent,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ListItemDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ListItemDefaults.colors(
            containerColor = if (isCurrent) highlightColor.copy(alpha = 0.22f) else Color.Transparent,
            focusedContainerColor = if (isCurrent) highlightColor.copy(alpha = 0.40f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
            contentColor = if (isCurrent) Color.White else textColor,
            focusedContentColor = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurface
        ),
        border = ListItemDefaults.border(
            border = Border(
                border = BorderStroke(
                    if (isCurrent) 2.dp else 1.dp,
                    if (isCurrent) highlightColor else textColor.copy(alpha = 0.05f)
                ),
                shape = RoundedCornerShape(10.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, if (isCurrent) highlightColor else MaterialTheme.colorScheme.primary),
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
                        tint = highlightColor,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = textColor.copy(alpha = 0.45f)
                    )
                }
            }
        },
        headlineContent = {
            Text(
                text = song.mediaMetadata.title?.toString() ?: "",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isCurrent) highlightColor else textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            val artist = song.mediaMetadata.artist?.toString() ?: ""
            if (artist.isNotBlank()) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrent) highlightColor.copy(alpha = 0.85f) else textColor.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        trailingContent = {
            if (formattedDuration.isNotBlank()) {
                Text(
                    text = formattedDuration,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isCurrent) highlightColor else textColor.copy(alpha = 0.5f)
                )
            }
        }
    )
}