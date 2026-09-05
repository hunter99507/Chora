package com.craftworks.music.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.craftworks.music.ui.elements.LocalBottomPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.craftworks.music.R
import com.craftworks.music.data.model.Screen
import com.craftworks.music.managers.EmbyJellyfinManager
import com.craftworks.music.managers.LocalProviderManager
import com.craftworks.music.managers.NavidromeManager
import com.craftworks.music.providers.navidrome.navidromeStatus
import com.craftworks.music.ui.elements.EmbyJellyfinProviderCard
import com.craftworks.music.ui.elements.LRCLIBProviderCard
import com.craftworks.music.ui.elements.LocalProviderCard
import com.craftworks.music.ui.elements.NavidromeProviderCard
import com.craftworks.music.ui.elements.NetEaseProviderCard
import com.craftworks.music.ui.elements.dialogs.CreateMediaProviderDialog
import com.craftworks.music.ui.elements.dialogs.dialogFocusable

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
@Preview(showSystemUi = false, showBackground = true)
fun S_ProviderScreen(navHostController: NavHostController = rememberNavController()) {
    BackHandler {
        if (!navHostController.popBackStack()) {
            navHostController.navigate(Screen.Setting.route) {
                launchSingleTop = true
            }
        }
    }

    val context = LocalContext.current.applicationContext

    var showNavidromeServerDialog by remember { mutableStateOf(false) }
    var showBackupRestoreDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.Settings_Header_Media)) },
                actions = {
                    IconButton(
                        onClick = { showBackupRestoreDialog = true },
                        modifier = Modifier.size(56.dp, 70.dp),
                    ) {
                        Icon(
                            imageVector = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.s_m_backup_restore),
                            tint = MaterialTheme.colorScheme.onBackground,
                            contentDescription = stringResource(R.string.Settings_Header_Backup_Restore),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (!navHostController.popBackStack()) {
                                navHostController.navigate(Screen.Setting.route) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        modifier = Modifier.size(56.dp, 70.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            tint = MaterialTheme.colorScheme.onBackground,
                            contentDescription = "Previous Song",
                            modifier = Modifier
                                .size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
    ) { innerPadding ->
        Box (
            modifier = Modifier
                .padding(
                    top = innerPadding.calculateTopPadding()
                )
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .dialogFocusable()
        ) {

            Column(
                Modifier
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (com.craftworks.music.BuildConfig.DEDICATED_SOURCE == "ALL") {
                    com.craftworks.music.ui.elements.DefaultSourceSelectorCard(context)
                }

                val localProviders by LocalProviderManager.allFolders.collectAsStateWithLifecycle()
                val navidromeServers by NavidromeManager.allServers.collectAsStateWithLifecycle()
                val embyServers by EmbyJellyfinManager.allServers.collectAsStateWithLifecycle()

                // Local Providers
                if (com.craftworks.music.BuildConfig.DEDICATED_SOURCE == "ALL" || com.craftworks.music.BuildConfig.DEDICATED_SOURCE == "LOCAL") {
                    for (local in localProviders) {
                        LocalProviderCard(local, context)
                    }
                }

                // Navidrome Providers
                if (com.craftworks.music.BuildConfig.DEDICATED_SOURCE == "ALL" || com.craftworks.music.BuildConfig.DEDICATED_SOURCE == "NAVIDROME") {
                    for (server in navidromeServers) {
                        NavidromeProviderCard(server)
                    }
                }

                // Emby / Jellyfin Providers
                if (com.craftworks.music.BuildConfig.DEDICATED_SOURCE == "ALL" || com.craftworks.music.BuildConfig.DEDICATED_SOURCE == "EMBY") {
                    for (server in embyServers) {
                        EmbyJellyfinProviderCard(server)
                    }
                }

                // Lyrics Providers at bottom
                LRCLIBProviderCard(context)

                NetEaseProviderCard(context)

                Spacer(modifier = Modifier.height(LocalBottomPadding.current + 80.dp))
            }

            FloatingActionButton(
                onClick = {
                    showNavidromeServerDialog = true
                    navidromeStatus.value = ""
                },
                modifier = Modifier
                    .padding(end = 16.dp, bottom = 16.dp + LocalBottomPadding.current)
                    .align(Alignment.BottomEnd),
                shape = FloatingActionButtonDefaults.extendedFabShape,
                containerColor = FloatingActionButtonDefaults.containerColor,
                contentColor = contentColorFor(FloatingActionButtonDefaults.containerColor),
                elevation = FloatingActionButtonDefaults.elevation(),
            ) {
                Icon(Icons.Rounded.Add, "Add Media Provider.")
            }
        }
    }

    if(showNavidromeServerDialog)
        CreateMediaProviderDialog(setShowDialog = { showNavidromeServerDialog = it })

    if (showBackupRestoreDialog)
        com.craftworks.music.ui.elements.dialogs.BackupRestoreDialog(setShowDialog = { showBackupRestoreDialog = it })
}