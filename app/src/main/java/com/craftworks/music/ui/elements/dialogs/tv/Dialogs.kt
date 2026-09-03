package com.craftworks.music.ui.elements.dialogs.tv

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Checkbox
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craftworks.music.managers.MediaSource
import com.craftworks.music.managers.MediaSourceManager
import com.craftworks.music.R
import com.craftworks.music.ui.playing.NowPlayingBackground

@Composable
fun <T> GenericListDialog(
    setShowDialog: (Boolean) -> Unit,
    titleRes: Int,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    label: @Composable (T) -> String,
    helperText: (T) -> String = { "" },
) {
    AlertDialog(
        onDismissRequest = { setShowDialog(false) },
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(titleRes),
                color = MaterialTheme.colorScheme.onSurface,
                style =  MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    val isSelected = option == selectedOption

                    ListItem(
                        selected = isSelected,
                        enabled = !(option == NowPlayingBackground.ANIMATED_BLUR && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU),
                        headlineContent = {
                            Text(label(option) + helperText(option))
                        },
                        trailingContent = {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onOptionSelected(option)
                                    setShowDialog(false)
                                }
                            )
                        },
                        onClick = {
                            onOptionSelected(option)
                            setShowDialog(false)
                        },
                    )
                }
            }
        },
        confirmButton = { }
    )
}

@Composable
fun <T> GenericCheckDialog(
    setShowDialog: (Boolean) -> Unit,
    titleRes: Int,
    items: List<T>,
    label: @Composable (T) -> String,
    isEnabled: (T) -> Boolean,
    onCheckedChange: (index: Int, checked: Boolean) -> Unit,
    onReset: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { setShowDialog(false) },
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(titleRes),
                color = MaterialTheme.colorScheme.onSurface,
                style =  MaterialTheme.typography.titleLarge
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                items(items) { item ->
                    val index = items.indexOf(item)

                    ListItem(
                        selected = false,
                        onClick = { onCheckedChange(index, !isEnabled(item)) },
                        onLongClick = {

                        },
                        headlineContent = {
                            Text(
                                text = label(item),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        leadingContent = {
                            Checkbox(
                                checked = isEnabled(item),
                                onCheckedChange = null
                            )
                        },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { setShowDialog(false) }
            ) {
                Text(stringResource(R.string.Action_Done))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = {
                onReset()
                setShowDialog(false)
            }) {
                Text(stringResource(R.string.Action_Reset))
            }
        }
    )
}

@Composable
fun TvSourceSelectorDialog(
    setShowDialog: (Boolean) -> Unit
) {
    val selectedSource by MediaSourceManager.selectedSource.collectAsStateWithLifecycle()
    val availableSources = remember { MediaSourceManager.getAvailableSources() }
    val navidromeServers by com.craftworks.music.managers.NavidromeManager.allServers.collectAsStateWithLifecycle()
    val currentNavidromeId by com.craftworks.music.managers.NavidromeManager.currentServerId.collectAsStateWithLifecycle()
    val embyServers by com.craftworks.music.managers.EmbyJellyfinManager.allServers.collectAsStateWithLifecycle()
    val currentEmbyId by com.craftworks.music.managers.EmbyJellyfinManager.currentServerId.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = { setShowDialog(false) },
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Select Music Library",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableSources.forEach { source ->
                    if (source == MediaSource.NAVIDROME && navidromeServers.size > 1) {
                        navidromeServers.forEach { server ->
                            val isSelected = (source == selectedSource) && (server.id == currentNavidromeId)
                            item {
                                ListItem(
                                    selected = isSelected,
                                    onClick = {
                                        com.craftworks.music.managers.NavidromeManager.setCurrentServer(server.id)
                                        com.craftworks.music.managers.NavidromeManager.setServerEnabled(server.id, true)
                                        MediaSourceManager.setSelectedSource(source)
                                        setShowDialog(false)
                                    },
                                    headlineContent = { Text("Navidrome (${server.username})") },
                                    supportingContent = { Text(server.url) },
                                    trailingContent = {
                                        RadioButton(selected = isSelected, onClick = null)
                                    }
                                )
                            }
                        }
                    } else if (source == MediaSource.EMBY && embyServers.size > 1) {
                        embyServers.forEach { server ->
                            val isSelected = (source == selectedSource) && (server.id == currentEmbyId)
                            item {
                                ListItem(
                                    selected = isSelected,
                                    onClick = {
                                        com.craftworks.music.managers.EmbyJellyfinManager.setCurrentServer(server.id)
                                        com.craftworks.music.managers.EmbyJellyfinManager.setServerEnabled(server.id, true)
                                        MediaSourceManager.setSelectedSource(source)
                                        setShowDialog(false)
                                    },
                                    headlineContent = { Text("Emby (${server.username})") },
                                    supportingContent = { Text(server.url) },
                                    trailingContent = {
                                        RadioButton(selected = isSelected, onClick = null)
                                    }
                                )
                            }
                        }
                    } else {
                        val isSelected = source == selectedSource
                        item {
                            ListItem(
                                selected = isSelected,
                                onClick = {
                                    MediaSourceManager.setSelectedSource(source)
                                    setShowDialog(false)
                                },
                                headlineContent = { Text(source.displayName) },
                                trailingContent = {
                                    RadioButton(selected = isSelected, onClick = null)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = { setShowDialog(false) }) {
                Text("Close")
            }
        }
    )
}