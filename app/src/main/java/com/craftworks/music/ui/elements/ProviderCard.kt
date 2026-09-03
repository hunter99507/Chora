package com.craftworks.music.ui.elements

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craftworks.music.R
import com.craftworks.music.data.EmbyJellyfinProvider
import com.craftworks.music.data.NavidromeProvider
import com.craftworks.music.data.repository.LyricsState
import com.craftworks.music.managers.EmbyJellyfinManager
import com.craftworks.music.managers.LocalProviderManager
import com.craftworks.music.managers.NavidromeManager
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.managers.settings.MediaProviderSettingsManager
import com.craftworks.music.ui.elements.dialogs.EditLrcLibUrlDialog
import kotlinx.coroutines.runBlocking

@Preview
@Composable
fun LocalProviderCard(local: String = "", context: Context = LocalContext.current){
    Row(modifier = Modifier
        .padding(bottom = 12.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceBright),
        verticalAlignment = Alignment.CenterVertically) {
        // Provider Icon
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.s_m_local_filled),
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = "Folder Icon",
            modifier = Modifier
                .padding(start = 20.dp, end = 16.dp)
                .height(32.dp)
                .size(32.dp)
        )
        // Provider Name
        Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.Source_Local),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
            )
            Text(
                text = local,
                color = MaterialTheme.colorScheme.onBackground.copy(0.75f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Delete Button
        Button(
            onClick = { LocalProviderManager.removeFolder(local) },
            shape = CircleShape,
            modifier = Modifier
                .size(32.dp),
            contentPadding = PaddingValues(2.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                tint = MaterialTheme.colorScheme.onBackground,
                contentDescription = "Delete Local Provider",
                modifier = Modifier
                    .height(32.dp)
                    .size(32.dp)
            )
        }

        Spacer(Modifier.width(20.dp))
        val disabledFolders by LocalProviderManager.disabledFolders.collectAsStateWithLifecycle()
        val isEnabled = !disabledFolders.contains(local)

        // Enabled Checkbox
        Checkbox(
            checked = isEnabled,
            onCheckedChange = { isChecked ->
                LocalProviderManager.setFolderEnabled(local, isChecked)
                Log.d("LOCAL_PROVIDER", "Local folder $local enabled: $isChecked")
            }
        )
    }
}

@Preview
@Composable
fun NavidromeProviderCard(
    server: NavidromeProvider = NavidromeProvider(
        "0",
        "https://demo.navidrome.org",
        "CraftWorks",
        "demo",
        enabled = true,
        allowSelfSignedCert = true
    )
) {
    val currentServerId by NavidromeManager.currentServerId.collectAsStateWithLifecycle()
    val isSelected = server.enabled && server.id == currentServerId
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Row(modifier = Modifier
        .padding(bottom = 12.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceBright)
        .clickable {
            val newChecked = !isSelected
            NavidromeManager.setServerEnabled(server.id, newChecked)
            if (newChecked) {
                coroutineScope.launch {
                    AppearanceSettingsManager(context).setUsername(server.username)
                }
            }
            Log.d("NAVIDROME", "Navidrome Server ${server.id} enabled: $newChecked")
        }
        .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Provider Icon
        Image(
            painter = painterResource(R.drawable.s_m_navidrome),
            contentDescription = "Navidrome Icon",
            modifier = Modifier
                .padding(start = 20.dp, end = 16.dp)
                .size(32.dp)
        )
        // Provider Name
        Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                text = server.username,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
            )
            Text(
                text = server.url,
                color = MaterialTheme.colorScheme.onBackground.copy(0.75f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Enabled Checkbox
        Checkbox(
            checked = isSelected,
            onCheckedChange = { isChecked ->
                NavidromeManager.setServerEnabled(server.id, isChecked)
                if (isChecked) {
                    coroutineScope.launch {
                        AppearanceSettingsManager(context).setUsername(server.username)
                    }
                }
                Log.d("NAVIDROME", "Navidrome Server ${server.id} enabled: $isChecked")
            }
        )

        // Delete Button
        Button(
            onClick = { NavidromeManager.removeServer(server.id) },
            shape = CircleShape,
            modifier = Modifier
                .size(32.dp),
            contentPadding = PaddingValues(2.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                tint = MaterialTheme.colorScheme.onBackground,
                contentDescription = "Remove Navidrome Server",
                modifier = Modifier
                    .height(32.dp)
                    .size(32.dp)
            )
        }

        Spacer(Modifier.width(20.dp))
    }
}

@Preview
@Composable
fun EmbyJellyfinProviderCard(
    server: EmbyJellyfinProvider = EmbyJellyfinProvider(
        "0",
        "http://localhost:8096",
        "EmbyUser",
        "password",
        enabled = true,
        allowSelfSignedCert = true
    )
) {
    val currentServerId by EmbyJellyfinManager.currentServerId.collectAsStateWithLifecycle()
    val libraries by EmbyJellyfinManager.libraries.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceBright)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Provider Icon
            Image(
                painter = painterResource(R.drawable.s_m_emby),
                contentDescription = "Emby / Jellyfin Icon",
                modifier = Modifier
                    .padding(start = 20.dp, end = 16.dp)
                    .size(32.dp)
            )
            // Provider Name
            Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(
                    text = server.username,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = server.url,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.75f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            var checked by remember { mutableStateOf(false) }
            checked = server.enabled && server.id == currentServerId

            // Enabled Checkbox
            val context = LocalContext.current
            Checkbox(
                checked = checked,
                onCheckedChange = { isChecked ->
                    EmbyJellyfinManager.setServerEnabled(server.id, isChecked)
                    if (isChecked) {
                        runBlocking {
                            AppearanceSettingsManager(context).setUsername(server.username)
                        }
                    }
                    Log.d("EMBY_JELLYFIN", "Emby Server ${server.id} enabled: $isChecked")
                }
            )

            // Delete Button
            Button(
                onClick = { EmbyJellyfinManager.removeServer(server.id) },
                shape = CircleShape,
                modifier = Modifier
                    .size(32.dp),
                contentPadding = PaddingValues(2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    tint = MaterialTheme.colorScheme.onBackground,
                    contentDescription = "Remove Emby Server",
                    modifier = Modifier
                        .height(32.dp)
                        .size(32.dp)
                )
            }

            Spacer(Modifier.width(20.dp))
        }

        // Library Filter Chips
        if (server.id == currentServerId && libraries.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                libraries.forEach { (library, isSelected) ->
                    FilterChip(
                        onClick = {
                            currentServerId?.let { serverId ->
                                EmbyJellyfinManager.toggleServerLibraryEnabled(
                                    serverId,
                                    library.id,
                                    !isSelected
                                )
                            }
                        },
                        label = {
                            Text(library.name)
                        },
                        selected = isSelected,
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun LRCLIBProviderCard(
    context: Context = LocalContext.current
){
    var showEditDialog by remember { mutableStateOf(false) }
    Row(modifier = Modifier
        .padding(bottom = 12.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceBright)
        .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Provider Icon
        Image(
            painter = painterResource(R.drawable.lrclib_logo),
            contentDescription = "LRCLIB.net logo",
            modifier = Modifier
                .padding(start = 20.dp, end = 16.dp)
                .size(32.dp)
        )
        // Provider Name
        Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                text = "Lyrics",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
            )
            Text(
                text = "LRCLIB.net",
                color = MaterialTheme.colorScheme.onBackground.copy(0.75f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Enabled Checkbox
        Checkbox(
            checked = LyricsState.useLrcLib,
            onCheckedChange = {
                LyricsState.useLrcLib = it
                runBlocking {
                    MediaProviderSettingsManager(context).setUseLrcLib(it)
                }
            }
        )

        if (showEditDialog)
            EditLrcLibUrlDialog(setShowDialog = { showEditDialog = it })

        // Edit Button
        Button(
            onClick = { showEditDialog = true },
            shape = CircleShape,
            modifier = Modifier
                .size(32.dp),
            contentPadding = PaddingValues(2.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                tint = MaterialTheme.colorScheme.onBackground,
                contentDescription = "Remove Navidrome Server",
                modifier = Modifier
                    .height(32.dp)
                    .size(32.dp)
            )
        }

        Spacer(Modifier.width(20.dp))
    }
}

@Preview
@Composable
fun NetEaseProviderCard(
    context: Context = LocalContext.current
){
    var showEditDialog by remember { mutableStateOf(false) }
    Row(modifier = Modifier
        .padding(bottom = 12.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceBright)
        .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Provider Icon
        Image(
            painter = painterResource(R.drawable.netease_cloud_music),
            contentDescription = "NetEase logo",
            modifier = Modifier
                .padding(start = 20.dp, end = 16.dp)
                .size(32.dp)
        )
        // Provider Name
        Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                text = "Lyrics",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
            )
            Text(
                text = "NetEase",
                color = MaterialTheme.colorScheme.onBackground.copy(0.75f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Enabled Checkbox
        Checkbox(
            checked = LyricsState.useNetEase,
            onCheckedChange = {
                LyricsState.useNetEase = it
                runBlocking {
                    MediaProviderSettingsManager(context).setUseNetEase(it)
                }
            }
        )

        Spacer(Modifier.width(12.dp))
    }
}

@Composable
fun DefaultSourceSelectorCard(context: Context = LocalContext.current) {
    val defaultSource by com.craftworks.music.managers.MediaSourceManager.defaultSource.collectAsStateWithLifecycle()
    val availableSources = remember { com.craftworks.music.managers.MediaSourceManager.getAvailableSources() }

    Column(
        modifier = Modifier
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.round_music_note_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Default Library on Startup",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Text(
                    text = "Select which library is active when Chora opens",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableSources.forEach { source ->
                val selected = source == defaultSource
                FilterChip(
                    selected = selected,
                    onClick = {
                        com.craftworks.music.managers.MediaSourceManager.setDefaultSource(source)
                    },
                    label = {
                        Text(
                            text = source.displayName,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = source.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}