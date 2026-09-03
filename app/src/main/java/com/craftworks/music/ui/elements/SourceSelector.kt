package com.craftworks.music.ui.elements

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craftworks.music.R
import com.craftworks.music.managers.MediaSource
import com.craftworks.music.managers.MediaSourceManager

@Composable
fun SourceSelectorPill(
    modifier: Modifier = Modifier,
    onSourceChanged: ((MediaSource) -> Unit)? = null
) {
    val selectedSource by MediaSourceManager.selectedSource.collectAsStateWithLifecycle()
    val navidromeServers by com.craftworks.music.managers.NavidromeManager.allServers.collectAsStateWithLifecycle()
    val currentNavidromeId by com.craftworks.music.managers.NavidromeManager.currentServerId.collectAsStateWithLifecycle()
    val currentNavidromeServer = navidromeServers.find { it.id == currentNavidromeId }

    val embyServers by com.craftworks.music.managers.EmbyJellyfinManager.allServers.collectAsStateWithLifecycle()
    val currentEmbyId by com.craftworks.music.managers.EmbyJellyfinManager.currentServerId.collectAsStateWithLifecycle()
    val currentEmbyServer = embyServers.find { it.id == currentEmbyId }

    val availableSources = remember(navidromeServers, embyServers, selectedSource) {
        MediaSourceManager.getAvailableSources()
    }

    var expanded by remember { mutableStateOf(false) }

    val pillDisplayName = when (selectedSource) {
        MediaSource.NAVIDROME -> if (navidromeServers.size > 1 && currentNavidromeServer != null) "Navidrome (${currentNavidromeServer.username})" else selectedSource.displayName
        MediaSource.EMBY -> if (embyServers.size > 1 && currentEmbyServer != null) "Emby (${currentEmbyServer.username})" else selectedSource.displayName
        else -> selectedSource.displayName
    }

    // Static badge when only 1 source — no dropdown needed
    if (availableSources.size <= 1 && availableSources.firstOrNull() != MediaSource.ALL && navidromeServers.size <= 1 && embyServers.size <= 1) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                painter = painterResource(id = selectedSource.iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = pillDisplayName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "arrowRotation"
    )

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val gradientBrush = remember(primary, tertiary) {
        Brush.linearGradient(listOf(primary, tertiary))
    }

    val pillBg by animateColorAsState(
        targetValue = if (expanded)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        animationSpec = tween(200),
        label = "pillBg"
    )

    val textColor by animateColorAsState(
        targetValue = if (expanded)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(200),
        label = "textColor"
    )

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .border(
                    width = 1.dp,
                    brush = gradientBrush,
                    shape = RoundedCornerShape(24.dp)
                )
                .background(pillBg)
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = selectedSource.iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = pillDisplayName,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    ),
                    color = textColor,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(5.dp))
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.chevron_down),
                    contentDescription = "Expand sources",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(arrowRotation)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(240.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "SWITCH SOURCE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(4.dp))

            availableSources.forEach { source ->
                if (source == MediaSource.NAVIDROME && navidromeServers.size > 1) {
                    navidromeServers.forEach { server ->
                        val isServerActive = (selectedSource == MediaSource.NAVIDROME) && (server.id == currentNavidromeId)
                        val host = server.url.removePrefix("http://").removePrefix("https://").substringBefore("/").substringBefore(":")
                        SourceDropdownItem(
                            iconRes = R.drawable.s_m_navidrome,
                            label = "Navidrome · ${server.username}",
                            sublabel = host,
                            isActive = isServerActive,
                            onClick = {
                                expanded = false
                                com.craftworks.music.managers.NavidromeManager.setCurrentServer(server.id)
                                com.craftworks.music.managers.NavidromeManager.setServerEnabled(server.id, true)
                                MediaSourceManager.setSelectedSource(MediaSource.NAVIDROME)
                                onSourceChanged?.invoke(MediaSource.NAVIDROME)
                            }
                        )
                    }
                } else if (source == MediaSource.EMBY && embyServers.size > 1) {
                    embyServers.forEach { server ->
                        val isServerActive = (selectedSource == MediaSource.EMBY) && (server.id == currentEmbyId)
                        val host = server.url.removePrefix("http://").removePrefix("https://").substringBefore("/").substringBefore(":")
                        SourceDropdownItem(
                            iconRes = R.drawable.s_m_emby,
                            label = "Emby · ${server.username}",
                            sublabel = host,
                            isActive = isServerActive,
                            onClick = {
                                expanded = false
                                com.craftworks.music.managers.EmbyJellyfinManager.setCurrentServer(server.id)
                                com.craftworks.music.managers.EmbyJellyfinManager.setServerEnabled(server.id, true)
                                MediaSourceManager.setSelectedSource(MediaSource.EMBY)
                                onSourceChanged?.invoke(MediaSource.EMBY)
                            }
                        )
                    }
                } else {
                    val isCurrent = source == selectedSource
                    SourceDropdownItem(
                        iconRes = source.iconRes,
                        label = source.displayName,
                        sublabel = when (source) {
                            MediaSource.ALL -> "All connected sources"
                            MediaSource.LOCAL -> "Device storage"
                            MediaSource.NAVIDROME -> "Navidrome server"
                            MediaSource.EMBY -> "Emby / Jellyfin server"
                        },
                        isActive = isCurrent,
                        onClick = {
                            expanded = false
                            MediaSourceManager.setSelectedSource(source)
                            onSourceChanged?.invoke(source)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SourceDropdownItem(
    iconRes: Int,
    label: String,
    sublabel: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val gradientBrush = remember(primary) {
        Brush.horizontalGradient(listOf(primary.copy(alpha = 0.15f), Color.Transparent))
    }

    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = sublabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
                if (isActive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Active",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isActive) Modifier.background(gradientBrush) else Modifier
            )
    )
}
