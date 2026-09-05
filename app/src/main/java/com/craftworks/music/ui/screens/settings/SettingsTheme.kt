package com.craftworks.music.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.craftworks.music.ui.elements.LocalBottomPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.craftworks.music.R
import com.craftworks.music.data.model.Screen
import com.craftworks.music.managers.settings.AppTheme
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.managers.settings.OLEDProtectionMode
import com.craftworks.music.ui.elements.dialogs.BackgroundDialog
import com.craftworks.music.ui.elements.dialogs.OledProtectionModeDialog
import com.craftworks.music.ui.elements.dialogs.ThemeDialog
import com.craftworks.music.ui.elements.dialogs.dialogFocusable
import com.craftworks.music.ui.playing.NowPlayingBackground
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun S_ThemeScreen(navHostController: NavHostController = rememberNavController()) {
    BackHandler {
        if (!navHostController.popBackStack()) {
            navHostController.navigate(Screen.Setting.route) {
                launchSingleTop = true
            }
        }
    }

    var showThemesDialog by remember { mutableStateOf(false) }
    var showBackgroundDialog by remember { mutableStateOf(false) }
    var showOledDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val appearanceManager = remember { AppearanceSettingsManager(context) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.Settings_Header_Theme)) },
                actions = {
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
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .dialogFocusable()
        ) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                /* HEADER */
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.s_a_palette),
                        contentDescription = "Theme Icon",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.Settings_Header_Theme),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.headlineLarge.fontSize,
                        modifier = Modifier.weight(1f)
                    )
                }

                Column(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // App Theme (Dark / Light / System)
                    val selectedTheme by appearanceManager.appTheme.collectAsState(AppTheme.SYSTEM.name)
                    val themeTitle = when (selectedTheme) {
                        AppTheme.DARK.name -> stringResource(R.string.Theme_Dark)
                        AppTheme.LIGHT.name -> stringResource(R.string.Theme_Light)
                        AppTheme.MODERN_EDITORIAL.name -> stringResource(R.string.Theme_Modern_Editorial)
                        AppTheme.NORDIC_SLATE.name -> stringResource(R.string.Theme_Nordic_Slate)
                        AppTheme.APPLE_MUSIC.name -> stringResource(R.string.Theme_Apple_Music)
                        AppTheme.APPLE_CLASSICAL.name -> stringResource(R.string.Theme_Apple_Classical)
                        AppTheme.MIDNIGHT_LAVENDER.name -> stringResource(R.string.Theme_Midnight_Lavender)
                        else -> stringResource(R.string.Theme_System)
                    }

                    SettingsDialogButton(
                        stringResource(R.string.Dialog_Theme),
                        themeTitle,
                        ImageVector.vectorResource(R.drawable.s_a_palette),
                        toggleEvent = {
                            showThemesDialog = true
                        }
                    )

                    // Now Playing Background Style
                    val backgroundType by appearanceManager.npBackgroundFlow.collectAsState(
                        NowPlayingBackground.STATIC_BLUR
                    )
                    val backgroundTypeLabels = mapOf(
                        NowPlayingBackground.PLAIN to R.string.Background_Plain,
                        NowPlayingBackground.STATIC_BLUR to R.string.Background_Blur,
                        NowPlayingBackground.ANIMATED_BLUR to R.string.Background_Anim,
                    )
                    SettingsDialogButton(
                        stringResource(R.string.Setting_Background),
                        stringResource(
                            backgroundTypeLabels[backgroundType]
                                ?: androidx.media3.session.R.string.error_message_invalid_state
                        ),
                        ImageVector.vectorResource(R.drawable.s_a_background),
                        toggleEvent = {
                            showBackgroundDialog = true
                        }
                    )

                    // OLED Protection Mode
                    val oledProtection by appearanceManager.oledProtectionMode.collectAsState(
                        OLEDProtectionMode.OFF
                    )
                    val oledLabels = mapOf(
                        OLEDProtectionMode.OFF to R.string.Oled_Off,
                        OLEDProtectionMode.LYRICS_ONLY to R.string.Oled_Lyrics_Only,
                        OLEDProtectionMode.MINIMAL to R.string.Oled_Minimal,
                    )
                    SettingsDialogButton(
                        stringResource(R.string.Setting_Oled_Mode),
                        stringResource(
                            oledLabels[oledProtection] ?: R.string.Oled_Off
                        ),
                        ImageVector.vectorResource(R.drawable.rounded_tv_24),
                        toggleEvent = {
                            showOledDialog = true
                        }
                    )

                    // Floating Navigation Bar Toggle
                    val isFloatingNavbar by appearanceManager.floatingNavbarFlow.collectAsState(false)
                    SettingsSwitch(
                        selected = isFloatingNavbar,
                        settingsName = stringResource(R.string.Setting_Floating_Navbar),
                        settingsIcon = ImageVector.vectorResource(R.drawable.s_a_navbar_items),
                        toggleEvent = {
                            coroutineScope.launch {
                                appearanceManager.setFloatingNavbar(!isFloatingNavbar)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(LocalBottomPadding.current + 48.dp))
            }
        }

        if (showThemesDialog)
            ThemeDialog(setShowDialog = { showThemesDialog = it })

        if (showBackgroundDialog)
            BackgroundDialog(setShowDialog = { showBackgroundDialog = it })

        if (showOledDialog)
            OledProtectionModeDialog(setShowDialog = { showOledDialog = it })
    }
}
