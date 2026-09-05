package com.craftworks.music.ui.screens.tv.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Done
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.craftworks.music.R
import com.craftworks.music.managers.EmbyJellyfinManager
import com.craftworks.music.managers.LocalProviderManager
import com.craftworks.music.managers.NavidromeManager
import com.craftworks.music.managers.settings.MediaProviderSettingsManager
import com.craftworks.music.ui.elements.dialogs.tv.CreateEmbyJellyfinProviderDialog
import com.craftworks.music.ui.elements.dialogs.tv.CreateLocalProviderDialog
import com.craftworks.music.ui.elements.dialogs.tv.CreateNavidromeProviderDialog
import com.craftworks.music.ui.elements.dialogs.tv.ModifyLrcLibProviderDialog
import com.craftworks.music.ui.elements.tv.EmbyJellyfinProviderCard
import com.craftworks.music.ui.elements.tv.LocalProviderCard
import com.craftworks.music.ui.elements.tv.LrcLibProviderCard
import com.craftworks.music.ui.elements.tv.NavidromeProviderCard
import com.craftworks.music.ui.elements.tv.NetEaseProviderCard

@Composable
fun TvS_ProviderScreen() {
    val context = LocalContext.current.applicationContext

    val localProviders by LocalProviderManager.allFolders.collectAsStateWithLifecycle()
    val navidromeServers by NavidromeManager.allServers.collectAsStateWithLifecycle()
    val embyServers by EmbyJellyfinManager.allServers.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = when (com.craftworks.music.BuildConfig.DEDICATED_SOURCE) {
        "LOCAL" -> listOf("Folders", "Lyrics")
        "NAVIDROME" -> listOf("Navidrome", "Lyrics")
        "EMBY" -> listOf("Emby / Jellyfin", "Lyrics")
        else -> listOf("Default Library", "Navidrome", "Emby / Jellyfin", "Folders", "Lyrics")
    }

    var showNavidromeServerDialog by remember { mutableStateOf(false) }
    var showEmbyServerDialog by remember { mutableStateOf(false) }
    var showLocalFolderDialog by remember { mutableStateOf(false) }
    var showLrcLibEditDialog by remember { mutableStateOf(false) }

    val lrclibUrl by MediaProviderSettingsManager(context).lrcLibEndpointFlow.collectAsStateWithLifecycle("")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        TabRow(
            modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer(),
            selectedTabIndex = selectedTab
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onFocus = { selectedTab = index },
                    onClick = { selectedTab = index },
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }

        when (tabs.getOrNull(selectedTab)) {
            "Default Library" -> {
                val defaultSource by com.craftworks.music.managers.MediaSourceManager.defaultSource.collectAsStateWithLifecycle()
                val availableSources = remember { com.craftworks.music.managers.MediaSourceManager.getAvailableSources() }
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableSources, key = { it.id }) { source ->
                        val isSelected = source == defaultSource
                        ListItem(
                            selected = isSelected,
                            onClick = {
                                com.craftworks.music.managers.MediaSourceManager.setDefaultSource(source)
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(id = source.iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            headlineContent = {
                                Text(source.displayName)
                            },
                            supportingContent = {
                                Text(if (isSelected) "Default on startup (Active)" else "Click to set as default")
                            },
                            trailingContent = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Done,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else null
                        )
                    }
                }
            }

            "Navidrome" -> LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(navidromeServers, key = { it.id }) { server ->
                    NavidromeProviderCard(server)
                }
                item {
                    ListItem(
                        selected = false,
                        onClick = {
                            showNavidromeServerDialog = true
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                        },
                        headlineContent = {
                            Text(stringResource(R.string.Action_Add))
                        }
                    )
                }
            }

            "Emby / Jellyfin" -> LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(embyServers, key = { it.id }) { server ->
                    EmbyJellyfinProviderCard(server)
                }
                item {
                    ListItem(
                        selected = false,
                        onClick = {
                            showEmbyServerDialog = true
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                        },
                        headlineContent = {
                            Text(stringResource(R.string.Action_Add))
                        }
                    )
                }
            }

            "Folders" -> LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(localProviders, key = { it }) { local ->
                    LocalProviderCard(local)
                }
                item {
                    ListItem(
                        selected = false,
                        onClick = {
                            showLocalFolderDialog = true
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                        },
                        headlineContent = {
                            Text(stringResource(R.string.Action_Add))
                        }
                    )
                }
            }

            "Lyrics" -> Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LrcLibProviderCard(lrclibUrl) {
                    showLrcLibEditDialog = true
                }
                NetEaseProviderCard()
            }
        }
    }

    if(showNavidromeServerDialog)
        CreateNavidromeProviderDialog(setShowDialog = { showNavidromeServerDialog = it })

    if(showEmbyServerDialog)
        CreateEmbyJellyfinProviderDialog(setShowDialog = { showEmbyServerDialog = it })

    if(showLocalFolderDialog)
        CreateLocalProviderDialog(setShowDialog = { showLocalFolderDialog = it })

    if(showLrcLibEditDialog)
        ModifyLrcLibProviderDialog(
            initialUrl = lrclibUrl,
            setShowDialog = { showLrcLibEditDialog = it }
        )
}