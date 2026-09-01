@file:androidx.annotation.OptIn(UnstableApi::class)

package com.craftworks.music.ui.playing.tv

import android.util.Log
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import androidx.media3.ui.compose.state.rememberRepeatButtonState
import androidx.media3.ui.compose.state.rememberShuffleButtonState
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.OutlinedIconButtonDefaults
import androidx.tv.material3.Text
import com.craftworks.music.R
import com.craftworks.music.formatMilliseconds
import com.craftworks.music.providers.navidrome.downloadNavidromeSong
import com.craftworks.music.ui.elements.bounceClick
import com.craftworks.music.ui.elements.moveClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import com.craftworks.music.ui.playing.getVibrantSeekbarColor
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.shadow
import androidx.core.graphics.ColorUtils

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun PlaybackProgressSlider(
    color: Color = MaterialTheme.colorScheme.onBackground,
    iconColor: Color = MaterialTheme.colorScheme.onBackground,
    mediaController: MediaController? = null,
    metadata: MediaMetadata? = null
) {
    val isDarkBackground = remember(iconColor) {
        ColorUtils.calculateLuminance(iconColor.toArgb()) > 0.45
    }
    val vibrantColor = remember(color, isDarkBackground) {
        getVibrantSeekbarColor(color, isDarkBackground)
    }

    var currentValue by remember { mutableLongStateOf(0L) }
    val currentDuration by remember(mediaController?.duration) {
        derivedStateOf {
            mediaController?.duration?.coerceAtLeast(0L)
        }
    }

    val queueStatus by remember(mediaController?.currentMediaItemIndex, mediaController?.mediaItemCount) {
        derivedStateOf {
            val total = mediaController?.mediaItemCount ?: 0
            val current = (mediaController?.currentMediaItemIndex ?: -1) + 1
            if (total > 0 && current > 0) {
                "$current / $total"
            } else {
                ""
            }
        }
    }

    val animatedValue by animateFloatAsState(
        targetValue = currentValue.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "Smooth Slider Update"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val focused = remember { mutableStateOf(false) }
    var isInteracting by remember { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(mediaController, isPlaying) {
        if (mediaController != null && isPlaying) {
            while (isActive && !isInteracting) {
                currentValue = mediaController.currentPosition
                delay(1000L)
            }
        } else {
            if (mediaController != null) {
                currentValue = mediaController.currentPosition
            }
        }
    }

    DisposableEffect(mediaController) {
        if (mediaController == null) {
            onDispose { }
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                Log.d("TAG", "MediaController isPlaying changed: $playing")
                isPlaying = playing
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)
                if (reason != Player.DISCONTINUITY_REASON_SEEK)
                    currentValue = newPosition.positionMs
            }
        }

        mediaController?.addListener(listener)

        // Initial check in case state changed before listener was attached or for initial setup
        isPlaying = mediaController?.isPlaying ?: false
        currentValue = mediaController?.currentPosition ?: 0L

        onDispose {
            mediaController?.removeListener(listener)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Slider(
            enabled = metadata?.mediaType != MediaMetadata.MEDIA_TYPE_RADIO_STATION,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    focused.value = it.isFocused
                }
                .onKeyEvent { keyEvent ->
                    when {
                        keyEvent.key == Key.DirectionRight && keyEvent.type == KeyEventType.KeyDown -> {
                            mediaController?.seekForward()
                            true
                        }

                        keyEvent.key == Key.DirectionLeft && keyEvent.type == KeyEventType.KeyDown -> {
                            mediaController?.seekBack()
                            true
                        }

                        else -> false
                    }
                },
            value = animatedValue,
            onValueChange = {
                isInteracting = true
                currentValue = it.toLong()
            },
            onValueChangeFinished = {
                isInteracting = false
                mediaController?.seekTo(currentValue)
            },
            valueRange = 0f..(currentDuration?.toFloat() ?: 0f),
            colors = SliderDefaults.colors(
                activeTrackColor = vibrantColor,
                inactiveTrackColor = vibrantColor.copy(alpha = 0.30f),
                thumbColor = vibrantColor
            ),
            interactionSource = interactionSource,
            track = { sliderState ->
                val fraction = if (sliderState.valueRange.endInclusive > sliderState.valueRange.start) {
                    ((sliderState.value - sliderState.valueRange.start) / (sliderState.valueRange.endInclusive - sliderState.valueRange.start)).coerceIn(0f, 1f)
                } else 0f
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                ) {
                    val y = size.height / 2f
                    val trackThickness = 3.5.dp.toPx()
                    // Inactive track line
                    drawLine(
                        color = vibrantColor.copy(alpha = 0.28f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = trackThickness,
                        cap = StrokeCap.Round
                    )
                    // Active track line
                    if (fraction > 0f) {
                        drawLine(
                            color = vibrantColor,
                            start = Offset(0f, y),
                            end = Offset(size.width * fraction, y),
                            strokeWidth = trackThickness + 0.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            },
            thumb = {
                val isFocused = focused.value
                val dotSize by animateDpAsState(
                    targetValue = if (isFocused) 20.dp else 16.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "Thumb Dot Size"
                )
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .shadow(if (isFocused) 4.dp else 2.dp, CircleShape)
                        .background(
                            color = vibrantColor,
                            shape = CircleShape
                        )
                )
            }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = remember(currentValue) { formatMilliseconds(currentValue.toInt() / 1000) },
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Start,
                color = if (focused.value) vibrantColor else vibrantColor.copy(alpha = 0.90f),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFeatureSettings = "tnum"
                ),
                modifier = Modifier.align(Alignment.CenterStart),
                maxLines = 1
            )

            if (queueStatus.isNotBlank()) {
                Text(
                    text = queueStatus,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = vibrantColor.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFeatureSettings = "tnum"
                    ),
                    modifier = Modifier.align(Alignment.Center),
                    maxLines = 1
                )
            }

            Text(
                text = remember(currentDuration) {
                    formatMilliseconds(
                        currentDuration?.toInt()?.div(1000) ?: (currentValue / 1000).toInt()
                    )
                },
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                color = if (focused.value) vibrantColor else vibrantColor.copy(alpha = 0.90f),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFeatureSettings = "tnum"
                ),
                modifier = Modifier.align(Alignment.CenterEnd),
                maxLines = 1
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PreviousSongButton(player: Player, modifier: Modifier = Modifier) {
    val state = rememberPreviousButtonState(player)
    IconButton(
        onClick = state::onClick,
        modifier = modifier
            .bounceClick(state.isEnabled)
            .moveClick(false, state.isEnabled),
        enabled = state.isEnabled,
        colors = IconButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onSurface
        ),
        scale = IconButtonDefaults.scale(focusedScale = 1.15f)
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.media3_notification_seek_to_previous),
            contentDescription = "Previous song",
            modifier = Modifier.size(24.dp),
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PlayPauseButton(player: Player, modifier: Modifier = Modifier) {
    val state = rememberPlayPauseButtonState(player)
    val icon =
        if (state.showPlay) Icons.Rounded.PlayArrow else ImageVector.vectorResource(R.drawable.media3_notification_pause)
    val contentDescription =
        if (state.showPlay) "play"
        else "pause"

    IconButton(
        onClick = state::onClick,
        modifier = modifier.bounceClick(state.isEnabled),
        enabled = state.isEnabled,
        colors = IconButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        scale = IconButtonDefaults.scale(focusedScale = 1.15f)
    ) {
        Icon(
            icon,
            contentDescription,
            modifier = Modifier.size(30.dp)
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun NextSongButton(player: Player, modifier: Modifier = Modifier) {
    val state = rememberNextButtonState(player)
    IconButton(
        onClick = state::onClick,
        modifier = modifier
            .bounceClick(state.isEnabled)
            .moveClick(true, state.isEnabled),
        enabled = state.isEnabled,
        colors = IconButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onSurface
        ),
        scale = IconButtonDefaults.scale(focusedScale = 1.15f)
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.media3_notification_seek_to_next),
            contentDescription = "Next song",
            modifier = Modifier.size(24.dp),
        )
    }
}


@Composable
fun PlayQueueButton(
    size: Dp = 64.dp,
    onClick: () -> Unit = {}
) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(6.dp),
        colors = ButtonDefaults.colors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(0.25f)
        )
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.s_m_playback),
            contentDescription = "Close Lyrics",
            modifier = Modifier
                .height(size)
                .size(size)
        )
    }
}


@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun DownloadButton(size: Dp, metadata: MediaMetadata?, enabled: Boolean) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Button(
        onClick = {
            coroutineScope.launch {
                metadata?.let {
                    downloadNavidromeSong(context, it)
                }
            }
        },
        enabled = enabled,
        modifier = if (enabled) // Disable bounce click if song is local
            Modifier
                .bounceClick()
                .height(size + 6.dp)
        else
            Modifier.height(size + 6.dp),
        contentPadding = PaddingValues(6.dp),
        colors = ButtonDefaults.colors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
        )
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.rounded_download_24),
            contentDescription = "Download Song",
            modifier = Modifier
                .height(size)
                .size(size)
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ShuffleButton(player: Player, modifier: Modifier = Modifier) {
    val state = rememberShuffleButtonState(player)
    val isActive = state.shuffleOn
    IconButton(
        onClick = state::onClick,
        modifier = modifier,
        enabled = state.isEnabled,
        colors = IconButtonDefaults.colors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent,
            contentColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            focusedContainerColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        scale = IconButtonDefaults.scale(focusedScale = 1.15f),
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.round_shuffle_28),
            contentDescription = if (isActive) "Shuffle On" else "Shuffle Off",
            modifier = Modifier.size(20.dp),
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun RepeatButton(player: Player, modifier: Modifier = Modifier) {
    val state = rememberRepeatButtonState(player)
    val isActive = state.repeatModeState != Player.REPEAT_MODE_OFF
    val icon = repeatModeIcon(state.repeatModeState)
    IconButton(
        onClick = state::onClick,
        modifier = modifier,
        enabled = state.isEnabled,
        colors = IconButtonDefaults.colors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent,
            contentColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            focusedContainerColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        scale = IconButtonDefaults.scale(focusedScale = 1.15f),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = when (state.repeatModeState) {
                Player.REPEAT_MODE_ONE -> "Repeat One"
                Player.REPEAT_MODE_ALL -> "Repeat All"
                else -> "Repeat Off"
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun repeatModeIcon(repeatMode: @Player.RepeatMode Int): ImageVector {
    return when (repeatMode) {
        Player.REPEAT_MODE_OFF -> ImageVector.vectorResource(R.drawable.rounded_repeat_24)
        Player.REPEAT_MODE_ONE -> ImageVector.vectorResource(R.drawable.rounded_repeat1_24)
        else -> ImageVector.vectorResource(R.drawable.rounded_repeat_24)
    }
}