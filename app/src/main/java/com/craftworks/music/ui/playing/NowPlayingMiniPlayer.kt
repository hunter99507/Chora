package com.craftworks.music.ui.playing

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.craftworks.music.player.ChoraMediaLibraryService

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Stable
@Composable
fun NowPlayingMiniPlayer(
    scaffoldState: BottomSheetScaffoldState = rememberBottomSheetScaffoldState(),
    metadata: MediaMetadata? = null,
    viewModel: NowPlayingViewModel = viewModel(),
    isFloating: Boolean = false,
    onClick: () -> Unit = { }
) {
    if (metadata?.title == null || metadata.title.toString().isBlank()) {
        return
    }

    val expanded by remember { derivedStateOf { scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded } }

    val isDark = isSystemInDarkTheme()
    LaunchedEffect(metadata?.artworkUri) {
        viewModel.updatePaletteFromUri(metadata?.artworkUri, NowPlayingBackground.STATIC_BLUR, isDark)
    }

    val paletteColors by viewModel.paletteColors.collectAsStateWithLifecycle()
    val accentColor = paletteColors.firstOrNull() ?: MaterialTheme.colorScheme.primary
    val barColor = if (paletteColors.size >= 2) {
        paletteColors[1]
    } else if (paletteColors.isNotEmpty()) {
        paletteColors[0].copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val animatedBarColor by animateColorAsState(
        targetValue = barColor,
        animationSpec = tween(durationMillis = 500),
        label = "Mini Player Background"
    )
    val animatedAccentColor by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(durationMillis = 500),
        label = "Mini Player Accent"
    )

    val yTrans by animateIntAsState(
        targetValue = if (expanded) dpToPx(72) else 0,
        label = "Fullscreen Translation"
    )

    val shape = if (isFloating) RoundedCornerShape(20.dp) else RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .offset { IntOffset(x = 0, y = -yTrans) }
            .zIndex(1f)
            .then(
                if (isFloating) {
                    Modifier
                        .padding(horizontal = 16.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = shape,
                            spotColor = if (isDark) Color.Black.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.2f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                            shape = shape
                        )
                } else Modifier
            )
            .clip(shape)
            .background(animatedBarColor)
            .height(if (isFloating) 64.dp else 72.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable {
                onClick.invoke()
            }
    ) {
        // Album Image
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(metadata?.artworkUri)
                .diskCacheKey(
                    metadata?.extras?.getString("navidromeID")
                )
                .crossfade(true)
                .build(),
            contentDescription = "Album Cover",
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        // Title + Artist
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .weight(1f)
        ) {
            Text(
                text = metadata?.title.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.width(IntrinsicSize.Max)) {
                Text(
                    text = metadata?.artist.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Light,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f)
                )
                if (metadata?.recordingYear != 0 && metadata?.mediaType != MediaMetadata.MEDIA_TYPE_RADIO_STATION) {
                    Text(
                        text = " • " + metadata?.recordingYear.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Light,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        modifier = Modifier.wrapContentWidth()
                    )
                }
            }
        }

        ChoraMediaLibraryService.getInstance()?.player?.let {
            PlayPauseButton(
                it,
                animatedAccentColor,
                Modifier.size(44.dp)
            )
        }
    }
}