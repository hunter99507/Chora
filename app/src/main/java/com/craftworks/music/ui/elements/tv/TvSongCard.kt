package com.craftworks.music.ui.elements.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media.utils.MediaConstants.METADATA_KEY_IS_EXPLICIT
import androidx.media3.common.MediaItem
import androidx.media3.common.StarRating
import androidx.tv.material3.Border
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.formatMilliseconds

@OptIn(ExperimentalTvMaterial3Api::class)
@Preview(showBackground = true, device = "id:tv_1080p")
@Composable
fun TvHorizontalSongCard(
    song: MediaItem = MediaItem.EMPTY,
    modifier: Modifier = Modifier,
    showTrackNumber: Boolean = false,
    onClick: () -> Unit = { },
    onLongClick: () -> Unit = { }
) {
    val context = LocalContext.current
    val artworkData = song.mediaMetadata.artworkUri
        ?: song.mediaMetadata.extras?.getString("artworkUri")
        ?: song.mediaMetadata.extras?.getString("coverArt")

    val durationSec = ((song.mediaMetadata.durationMs ?: 0L) / 1000).toInt()
    val formattedDuration = if (durationSec > 0) formatMilliseconds(durationSec) else ""

    ListItem(
        selected = false,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = ListItemDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = ListItemDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(12.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            )
        ),
        scale = ListItemDefaults.scale(focusedScale = 1.015f),
        leadingContent = {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkData)
                    .error(R.drawable.placeholder)
                    .fallback(R.drawable.placeholder)
                    .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val trackNum = song.mediaMetadata.trackNumber ?: 0
                val titlePrefix = if (showTrackNumber && trackNum > 0) "$trackNum. " else ""
                Text(
                    text = "$titlePrefix${song.mediaMetadata.title ?: ""}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (song.mediaMetadata.extras?.getBoolean(METADATA_KEY_IS_EXPLICIT) == true) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.rounded_explicit_24),
                        contentDescription = "Explicit",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        supportingContent = {
            val artist = song.mediaMetadata.artist?.toString() ?: ""
            val album = song.mediaMetadata.albumTitle?.toString() ?: ""
            val subtitle = when {
                !showTrackNumber && artist.isNotBlank() && album.isNotBlank() -> "$artist  •  $album"
                artist.isNotBlank() -> artist
                album.isNotBlank() -> album
                else -> ""
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (song.mediaMetadata.userRating != null) {
                    repeat((song.mediaMetadata.userRating as StarRating).starRating.toInt()) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (formattedDuration.isNotBlank()) {
                    Text(
                        text = formattedDuration,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    )
}
