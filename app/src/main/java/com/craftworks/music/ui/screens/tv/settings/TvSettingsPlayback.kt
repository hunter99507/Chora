package com.craftworks.music.ui.screens.tv.settings

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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.craftworks.music.R
import com.craftworks.music.managers.settings.PlaybackSettingsManager
import com.craftworks.music.ui.elements.dialogs.tv.DefaultRepeatModeDialog
import com.craftworks.music.ui.elements.dialogs.tv.TranscodingBitrateDialog
import com.craftworks.music.ui.elements.dialogs.tv.TranscodingFormatDialog
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
@Preview(device = "id:tv_1080p", showSystemUi = true, showBackground = true)
fun TvS_PlaybackScreen() {
    var showWifiTranscodingDialog by remember { mutableStateOf(false) }
    var showDataTranscodingDialog by remember { mutableStateOf(false) }
    var showTranscodingFormatDialog by remember { mutableStateOf(false) }
    var showDefaultRepeatDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val smartShuffle by PlaybackSettingsManager(context).smartShuffleFlow.collectAsState(true)
    val defaultRepeat by PlaybackSettingsManager(context).defaultRepeatFlow.collectAsState(androidx.media3.common.Player.REPEAT_MODE_ALL)
    val defaultRepeatLabel = when (defaultRepeat) {
        androidx.media3.common.Player.REPEAT_MODE_ALL -> "Repeat All"
        androidx.media3.common.Player.REPEAT_MODE_ONE -> "Repeat One"
        else -> "Off"
    }

    val transcodingBitrateWifi by PlaybackSettingsManager(context).wifiTranscodingBitrateFlow.collectAsState("")
    val transcodingBitrateData by PlaybackSettingsManager(context).mobileDataTranscodingBitrateFlow.collectAsState("")
    val transcodingFormat by PlaybackSettingsManager(context).transcodingFormatFlow.collectAsState("opus")
    val transcodingFormatEnabled by remember {
        derivedStateOf {
            transcodingBitrateData != "No Transcoding" || transcodingBitrateWifi != "No Transcoding"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp)
    ) {

        item {
            SettingsSwitchItem(
                title = stringResource(R.string.Setting_Smart_Shuffle),
                icon = ImageVector.vectorResource(R.drawable.round_shuffle_28),
                checked = smartShuffle,
                onCheckedChange = {
                    coroutineScope.launch {
                        PlaybackSettingsManager(context).setSmartShuffle(it)
                    }
                }
            )
        }

        item {
            SettingsButtonItem(
                title = "Default Repeat Mode",
                subtitle = defaultRepeatLabel,
                icon = ImageVector.vectorResource(R.drawable.rounded_repeat_24),
                onClick = { showDefaultRepeatDialog = true }
            )
        }

        item {
            SettingsButtonItem(
                title = stringResource(R.string.Setting_Transcoding_Wifi),
                subtitle = if (transcodingBitrateWifi != "No Transcoding") "$transcodingBitrateWifi Kbps" else transcodingBitrateWifi,
                icon = ImageVector.vectorResource(R.drawable.s_p_transcoding),
                onClick = { showWifiTranscodingDialog = true }
            )
        }

        item {
            SettingsButtonItem(
                title = stringResource(R.string.Setting_Transcoding_Wifi),
                subtitle = if (transcodingBitrateData != "No Transcoding") "$transcodingBitrateData Kbps" else transcodingBitrateData,
                icon = ImageVector.vectorResource(R.drawable.s_p_transcoding),
                onClick = { showDataTranscodingDialog = true }
            )
        }

        item {
            SettingsButtonItem(
                title = stringResource(R.string.Setting_Transcoding_Format),
                subtitle = transcodingFormat,
                icon = ImageVector.vectorResource(R.drawable.s_p_transcoding),
                enabled = transcodingFormatEnabled,
                onClick = { showTranscodingFormatDialog = true }
            )
        }

        item {
            val sliderValue by PlaybackSettingsManager(context).scrobblePercentFlow.collectAsState(7)

            Surface(
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .padding(vertical = 8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.Setting_Scrobble_Percent),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Slider(
                        value = sliderValue.toFloat(),
                        onValueChange = {
                            coroutineScope.launch {
                                PlaybackSettingsManager(context).setScrobblePercent(it.roundToInt())
                            }
                        },
                        valueRange = 0f..10f,
                        steps = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onKeyEvent { keyEvent ->
                                when (keyEvent.key) {
                                    Key.DirectionRight -> {
                                        coroutineScope.launch {
                                            PlaybackSettingsManager(context).setScrobblePercent(sliderValue + 1)
                                        }
                                        true
                                    }

                                    Key.DirectionLeft -> {
                                        coroutineScope.launch {
                                            PlaybackSettingsManager(context).setScrobblePercent(sliderValue - 1)
                                        }
                                        true
                                    }

                                    else -> false
                                }
                            }
                    )
                }
            }
        }
    }

    if (showWifiTranscodingDialog) TranscodingBitrateDialog(setShowDialog = {
        showWifiTranscodingDialog = it
    }, true)
    if (showDataTranscodingDialog) TranscodingBitrateDialog(setShowDialog = {
        showDataTranscodingDialog = it
    }, false)
    if (showTranscodingFormatDialog) TranscodingFormatDialog(setShowDialog = {
        showTranscodingFormatDialog = it
    })
    if (showDefaultRepeatDialog) DefaultRepeatModeDialog(setShowDialog = {
        showDefaultRepeatDialog = it
    })
}