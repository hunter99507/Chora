package com.craftworks.music.ui.screens.tv.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.craftworks.music.R
import com.craftworks.music.managers.settings.AppTheme
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.managers.settings.OLEDProtectionMode
import com.craftworks.music.ui.elements.dialogs.tv.BackgroundDialog
import com.craftworks.music.ui.elements.dialogs.tv.HomeItemsDialog
import com.craftworks.music.ui.elements.dialogs.tv.NameDialog
import com.craftworks.music.ui.elements.dialogs.tv.NavbarItemsDialog
import com.craftworks.music.ui.elements.dialogs.tv.OledProtectionModeDialog
import com.craftworks.music.ui.elements.dialogs.tv.ThemeDialog
import com.craftworks.music.ui.playing.NowPlayingBackground
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
@Preview(device = "id:tv_1080p", showSystemUi = true, showBackground = true)
fun TvS_AppearanceScreen() {
    var showNameDialog by remember { mutableStateOf(false) }
    var showBackgroundDialog by remember { mutableStateOf(false) }
    var showOledDialog by remember { mutableStateOf(false) }
    var showThemesDialog by remember { mutableStateOf(false) }
    var showNavbarItemsDialog by remember { mutableStateOf(false) }
    var showHomeItemsDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp)
    ) {
        // Username, Theme, Background, Navbar Items, Home Items, Title Alignment
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                val username by AppearanceSettingsManager(context).usernameFlow.collectAsState("Username")

                SettingsButtonItem(
                    title = stringResource(R.string.Setting_Username),
                    subtitle = username,
                    icon = ImageVector.vectorResource(R.drawable.s_a_username),
                    onClick = { showNameDialog = true }
                )

                // Theme
                val selectedTheme by AppearanceSettingsManager(context).appTheme.collectAsState(
                    AppTheme.SYSTEM.name
                )
                val themes = listOf(
                    AppTheme.DARK.name,
                    AppTheme.LIGHT.name,
                    AppTheme.SYSTEM.name
                )
                val themeStrings = mapOf(
                    AppTheme.DARK.name to R.string.Theme_Dark,
                    AppTheme.LIGHT.name to R.string.Theme_Light,
                    AppTheme.SYSTEM.name to R.string.Theme_System
                )

                SettingsButtonItem(
                    title = stringResource(R.string.Dialog_Theme),
                    subtitle = stringResource(themeStrings[selectedTheme] ?: R.string.Theme_System),
                    icon = ImageVector.vectorResource(R.drawable.s_a_palette),
                    onClick = { showThemesDialog = true }
                )

                // Background Style
                val backgroundType by AppearanceSettingsManager(context).npBackgroundFlow.collectAsState(
                    NowPlayingBackground.STATIC_BLUR
                )
                val backgroundLabels = mapOf(
                    NowPlayingBackground.PLAIN to R.string.Background_Plain,
                    NowPlayingBackground.STATIC_BLUR to R.string.Background_Blur,
                    NowPlayingBackground.ANIMATED_BLUR to R.string.Background_Anim,
                )

                SettingsButtonItem(
                    title = stringResource(R.string.Setting_Background),
                    subtitle = stringResource(
                        backgroundLabels[backgroundType] ?: R.string.Background_Plain
                    ),
                    icon = ImageVector.vectorResource(R.drawable.s_a_background),
                    onClick = { showBackgroundDialog = true }
                )

                // OLED Protection Mode
                val oledProtection by AppearanceSettingsManager(context).oledProtectionMode.collectAsState(
                    OLEDProtectionMode.OFF
                )
                val oledLabels = mapOf(
                    OLEDProtectionMode.OFF to R.string.Oled_Off,
                    OLEDProtectionMode.LYRICS_ONLY to R.string.Oled_Lyrics_Only,
                    OLEDProtectionMode.MINIMAL to R.string.Oled_Minimal,
                )

                SettingsButtonItem(
                    title = stringResource(R.string.Setting_Oled_Mode),
                    subtitle = stringResource(
                        oledLabels[oledProtection] ?: R.string.Oled_Off
                    ),
                    icon = ImageVector.vectorResource(R.drawable.rounded_tv_24),
                    onClick = { showOledDialog = true }
                )

                // Screen standby
                val screenStandby by AppearanceSettingsManager(context).disableScreenStandby.collectAsState(
                    true
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.Setting_Screen_Standby),
                    icon = ImageVector.vectorResource(R.drawable.rounded_tv_24),
                    checked = screenStandby,
                    onCheckedChange = {
                        coroutineScope.launch {
                            AppearanceSettingsManager(context).setDisableScreenStandby(it)
                        }
                    }
                )

                // Nav Items
                val enabledNavbarItems by AppearanceSettingsManager(context).bottomNavItemsFlow.collectAsState(
                    emptyList()
                )

                SettingsButtonItem(
                    title = stringResource(R.string.Setting_Navbar_Items),
                    subtitle = enabledNavbarItems.filter { it.enabled }
                        .joinToString(", ") { it.title },
                    icon = ImageVector.vectorResource(R.drawable.s_a_navbar_items),
                    onClick = { showNavbarItemsDialog = true }
                )

                // Home Items
                val titleMap = mapOf(
                    "song_of_the_day" to R.string.song_of_the_day,
                    "playlists" to R.string.playlists,
                    "recently_played" to R.string.recently_played,
                    "recently_added" to R.string.recently_added,
                    "most_played" to R.string.most_played,
                    "random_songs" to R.string.random_songs
                )
                val enabledHomeItems by AppearanceSettingsManager(context).homeItemsItemsFlow.collectAsState(
                    emptyList()
                )

                SettingsButtonItem(
                    title = stringResource(R.string.Setting_Home_Items),
                    subtitle = enabledHomeItems.filter { it.enabled }
                        .map { stringResource(titleMap[it.key] ?: R.string.recently_played) }
                        .joinToString(","),
                    icon = ImageVector.vectorResource(R.drawable.s_a_home_items),
                    onClick = { showHomeItemsDialog = true }
                )

            }
        }

        // Switches
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // More Song Info
                val showMoreInfo by AppearanceSettingsManager(context).showMoreInfoFlow.collectAsState(
                    true
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.Setting_MoreInfo),
                    icon = ImageVector.vectorResource(R.drawable.s_a_moreinfo),
                    checked = showMoreInfo,
                    onCheckedChange = {
                        coroutineScope.launch {
                            AppearanceSettingsManager(context).setShowMoreInfo(it)
                        }
                    }
                )

                // Show Provider Dividers
                val showProviderDividers by AppearanceSettingsManager(context).showProviderDividersFlow.collectAsState(
                    true
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.Setting_ProviderDividers),
                    icon = ImageVector.vectorResource(R.drawable.s_a_moreinfo),
                    checked = showProviderDividers,
                    onCheckedChange = {
                        coroutineScope.launch {
                            AppearanceSettingsManager(context).setShowProviderDividers(it)
                        }
                    }
                )

                // Track numbers in album view
                val showTrackNumbers by AppearanceSettingsManager(context).showTrackNumbersFlow.collectAsState(
                    true
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.Setting_TrackNumbersAlbum),
                    icon = ImageVector.vectorResource(R.drawable.rounded_format_list_numbered_24),
                    checked = showTrackNumbers,
                    onCheckedChange = {
                        coroutineScope.launch {
                            AppearanceSettingsManager(context).setShowTrackNumbers(it)
                        }
                    }
                )
            }
        }

    }

    // Dialogs (still need TV adaptation, but keep original for now)
    if (showNameDialog) NameDialog(setShowDialog = { showNameDialog = it })
    if (showBackgroundDialog) BackgroundDialog(setShowDialog = { showBackgroundDialog = it })
    if (showOledDialog) OledProtectionModeDialog(setShowDialog = { showOledDialog = it })
    if (showThemesDialog) ThemeDialog(setShowDialog = { showThemesDialog = it })
    if (showNavbarItemsDialog) NavbarItemsDialog(setShowDialog = { showNavbarItemsDialog = it })
    if (showHomeItemsDialog) HomeItemsDialog(setShowDialog = { showHomeItemsDialog = it })
}