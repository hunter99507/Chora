package com.craftworks.music.ui.elements.dialogs

import android.content.Context
import android.util.Patterns
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Checkbox
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import com.craftworks.music.R
import com.craftworks.music.data.EmbyJellyfinLibrary
import com.craftworks.music.data.EmbyJellyfinProvider
import com.craftworks.music.data.NavidromeProvider
import com.craftworks.music.data.datasource.emby.EmbyJellyfinDataSource
import com.craftworks.music.data.model.Screen
import com.craftworks.music.managers.EmbyJellyfinManager
import com.craftworks.music.managers.LocalProviderManager
import com.craftworks.music.managers.NavidromeManager
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.managers.settings.MediaProviderSettingsManager
import com.craftworks.music.providers.navidrome.getNavidromeStatus
import com.craftworks.music.providers.navidrome.navidromeStatus
import com.craftworks.music.ui.elements.bounceClick
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

//region PREVIEWS
@Preview(showBackground = true, device = "id:tv_1080p")
@Preview(showBackground = true)
@Composable
fun PreviewProviderDialog() {
    CreateMediaProviderDialog(setShowDialog = { })
}

@Preview(showBackground = true)
@Composable
fun PreviewLrcLibDialog() {
    EditLrcLibUrlDialog(setShowDialog = { })
}

@Preview(showBackground = true)
@Composable
fun PreviewNoMediaProvidersDialog() {
    NoMediaProvidersDialog(setShowDialog = { }, NavHostController(LocalContext.current))
}
//endregion

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun EditLrcLibUrlDialog(
    setShowDialog: (Boolean) -> Unit,
    context: Context = LocalContext.current
) {
    val settingsManager = remember { MediaProviderSettingsManager(context) }

    var url by remember { mutableStateOf("https://lrclib.net") }

    // Launch a coroutine to collect the flow once and update url
    LaunchedEffect(settingsManager) {
        settingsManager.lrcLibEndpointFlow.collect { value ->
            url = value
        }
    }

    var isValidUrl: Boolean by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = { setShowDialog(false) }) {
        Column(
            modifier = Modifier
                .shadow(12.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp)
                .dialogFocusable()
                .selectableGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.Dialog_LRCLIB_Url),
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    isValidUrl = Patterns.WEB_URL.matcher(url).matches()
                },
                label = { Text(stringResource(R.string.Dialog_LRCLIB_Url)) },
                singleLine = true,
                isError = !isValidUrl
            )

            Button(
                onClick = {
                    if (isValidUrl)
                        runBlocking {
                            MediaProviderSettingsManager(context).setLrcLibEndpoint(url)
                        }

                    setShowDialog(false)
                },
                modifier = Modifier
                    .bounceClick(),
                enabled = isValidUrl
            ) {
                Text(stringResource(R.string.Action_Done))
            }
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun CreateMediaProviderDialog(
    setShowDialog: (Boolean) -> Unit,
    context: Context = LocalContext.current
) {
    var url: String by remember { mutableStateOf("") }
    var username: String by remember { mutableStateOf("") }
    var password: String by remember { mutableStateOf("") }
    var allowCerts: Boolean by remember { mutableStateOf(false) }

    var dir: String by remember { mutableStateOf("/Music/") }
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val maxDialogHeight = (configuration.screenHeightDp * 0.70f).dp.coerceAtMost(520.dp)

    Dialog(
        onDismissRequest = { setShowDialog(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 360.dp)
                .heightIn(max = maxDialogHeight)
                .shadow(12.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
                .dialogFocusable()
                .selectableGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.Settings_Header_Media),
                style = MaterialTheme.typography.titleLarge
            )

            var expanded by remember { mutableStateOf(false) }

            var embyStatus by remember { mutableStateOf("") }
            var embyAuthToken by remember { mutableStateOf<String?>(null) }
            var embyUserId by remember { mutableStateOf<String?>(null) }
            var embyServerId by remember { mutableStateOf<String?>(null) }
            var embyLibrariesSelection by remember { mutableStateOf<List<Pair<EmbyJellyfinLibrary, Boolean>>>(emptyList()) }

            val options = listOf(
                stringResource(R.string.Source_Local),
                stringResource(R.string.Source_Navidrome),
                stringResource(R.string.Source_Emby)
            )
            var selectedOptionText by remember { mutableStateOf(options[1]) }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
            ) {
                TextField(
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    readOnly = true,
                    value = selectedOptionText,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.Dialog_Media_Source)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    options.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                selectedOptionText = selectionOption
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            //region Local Folder
            if (selectedOptionText == stringResource(R.string.Source_Local))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    /* Directory */
                    OutlinedTextField(
                        value = dir,
                        onValueChange = { dir = it },
                        label = { Text(stringResource(R.string.Label_Local_Directory)) },
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    LocalProviderManager.addFolder(dir)
                                    setShowDialog(false)
                                } catch (_: Exception) {
                                    // DO NOTHING
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .bounceClick(),
                    ) {
                        Text(
                            stringResource(R.string.Action_Add)
                        )
                    }
                }
            //endregion

            //region Navidrome
            else if (selectedOptionText == stringResource(R.string.Source_Navidrome))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    /* SERVER URL */
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.Label_Navidrome_URL)) },
                        placeholder = { Text("http://domain.tld:<port>") },
                        singleLine = true,
                        isError = navidromeStatus.value == "Invalid URL"
                    )
                    /* USERNAME */
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.Label_Navidrome_Username)) },
                        singleLine = true,
                        isError = navidromeStatus.value == "Wrong username or password"
                    )
                    /* PASSWORD */
                    var passwordVisible by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.Label_Navidrome_Password)) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passwordVisible)
                                R.drawable.round_visibility_24
                            else
                                R.drawable.round_visibility_off_24

                            val description =
                                if (passwordVisible) "Hide password" else "Show password"

                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(id = image),
                                    description
                                )
                            }
                        },
                        isError = navidromeStatus.value == "Wrong username or password"
                    )

                    /* Allow Self Signed Certs */
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                    ) {
                        Text(
                            text = stringResource(R.string.Label_Allow_Self_Signed_Certs),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start
                        )
                        Switch(checked = allowCerts, onCheckedChange = { allowCerts = it })
                    }

                    if (navidromeStatus.value != "") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Status: ${navidromeStatus.value}",
                                fontWeight = FontWeight.Medium,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }

                    Crossfade (
                        navidromeStatus.value == "ok"
                    ) {
                        if (it) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val server = NavidromeProvider(
                                            java.util.UUID.randomUUID().toString(),
                                            url,
                                            username,
                                            password,
                                            true,
                                            allowCerts
                                        )
                                        NavidromeManager.addServer(server)
                                        AppearanceSettingsManager(context).setUsername(username)

                                        navidromeStatus.value = ""

                                        setShowDialog(false)
                                    }
                                },
                                modifier = Modifier
                                    .height(50.dp)
                                    .fillMaxWidth()
                                    .bounceClick(),
                                enabled = navidromeStatus.value == "ok"
                            ) {
                                Text(
                                    stringResource(R.string.Action_Add)
                                )
                            }
                        }
                        else {
                            OutlinedButton(
                                onClick = {
                                    val server = NavidromeProvider(
                                        url,
                                        url,
                                        username,
                                        password,
                                        true,
                                        allowCerts
                                    )
                                    coroutineScope.launch {
                                        getNavidromeStatus(server)
                                    }
                                },
                                modifier = Modifier
                                    .height(50.dp)
                                    .fillMaxWidth()
                                    .bounceClick()
                            ) {
                                Text(
                                    stringResource(R.string.Action_Login)
                                )
                            }
                        }
                    }
                }
            //endregion

            //region Emby / Jellyfin
            else if (selectedOptionText == stringResource(R.string.Source_Emby))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    /* SERVER URL */
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.Label_Emby_URL)) },
                        placeholder = { Text("http://192.168.0.30:8096") },
                        singleLine = true,
                        isError = embyStatus == "Invalid URL"
                    )
                    /* USERNAME */
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.Label_Navidrome_Username)) },
                        singleLine = true,
                        isError = embyStatus == "Authentication Failed"
                    )
                    /* PASSWORD */
                    var passwordVisible by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.Label_Navidrome_Password)) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passwordVisible)
                                R.drawable.round_visibility_24
                            else
                                R.drawable.round_visibility_off_24

                            val description =
                                if (passwordVisible) "Hide password" else "Show password"

                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(id = image),
                                    description
                                )
                            }
                        },
                        isError = embyStatus == "Authentication Failed"
                    )

                    /* Allow Self Signed Certs */
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                    ) {
                        Text(
                            text = stringResource(R.string.Label_Allow_Self_Signed_Certs),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start
                        )
                        Switch(checked = allowCerts, onCheckedChange = { allowCerts = it })
                    }

                    if (embyStatus.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Status: $embyStatus",
                                fontWeight = FontWeight.Medium,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }

                    Crossfade (
                        embyStatus == "ok"
                    ) { isOk ->
                        if (isOk) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (embyLibrariesSelection.isNotEmpty()) {
                                    Text(
                                        text = "Select Folders to Include:",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        embyLibrariesSelection.forEachIndexed { index, (library, isSelected) ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceBright)
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = { checked ->
                                                        val updated = embyLibrariesSelection.toMutableList()
                                                        updated[index] = Pair(library, checked)
                                                        embyLibrariesSelection = updated
                                                    }
                                                )
                                                Text(
                                                    text = library.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    modifier = Modifier.padding(start = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val selectedLibs = if (embyLibrariesSelection.isNotEmpty()) {
                                                embyLibrariesSelection
                                            } else {
                                                listOf(Pair(EmbyJellyfinLibrary("0", "Music"), true))
                                            }
                                            val server = EmbyJellyfinProvider(
                                                id = url,
                                                url = url,
                                                username = username,
                                                password = password,
                                                token = embyAuthToken,
                                                userId = embyUserId,
                                                serverId = embyServerId,
                                                enabled = true,
                                                allowSelfSignedCert = allowCerts,
                                                libraryIds = selectedLibs
                                            )
                                            EmbyJellyfinManager.addServer(server, selectedLibraries = selectedLibs)
                                            AppearanceSettingsManager(context).setUsername(username)
                                            embyStatus = ""
                                            embyLibrariesSelection = emptyList()
                                            setShowDialog(false)
                                        }
                                    },
                                    modifier = Modifier
                                        .height(50.dp)
                                        .fillMaxWidth()
                                        .bounceClick(),
                                    enabled = embyStatus == "ok"
                                ) {
                                    Text(
                                        stringResource(R.string.Action_Add)
                                    )
                                }
                            }
                        }
                        else {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val auth = EmbyJellyfinDataSource().authenticate(
                                            serverUrl = url,
                                            username = username,
                                            password = password,
                                            allowSelfSignedCert = allowCerts
                                        )
                                        if (auth?.accessToken != null && auth.user != null) {
                                            embyAuthToken = auth.accessToken
                                            embyUserId = auth.user.id
                                            embyServerId = auth.serverId

                                            val tempServer = EmbyJellyfinProvider(
                                                id = url,
                                                url = url,
                                                username = username,
                                                password = password,
                                                token = auth.accessToken,
                                                userId = auth.user.id,
                                                serverId = auth.serverId,
                                                enabled = true,
                                                allowSelfSignedCert = allowCerts
                                            )
                                            val views = EmbyJellyfinDataSource().getViews(tempServer)
                                            val librariesWithSelection = if (views.isNotEmpty()) {
                                                views.map { item ->
                                                    val isMusic = item.collectionType?.lowercase() == "music"
                                                    Pair(EmbyJellyfinLibrary(item.id, item.name), isMusic)
                                                }.let { list ->
                                                    // If no folder had collectionType == "music", check all by default
                                                    if (list.none { it.second }) list.map { it.copy(second = true) } else list
                                                }
                                            } else {
                                                EmbyJellyfinDataSource().getLibraries(tempServer).map { Pair(it, true) }
                                            }
                                            embyLibrariesSelection = librariesWithSelection
                                            embyStatus = "ok"
                                        } else {
                                            embyStatus = "Authentication Failed"
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .height(50.dp)
                                    .fillMaxWidth()
                                    .bounceClick()
                            ) {
                                Text(
                                    stringResource(R.string.Action_Login)
                                )
                            }
                        }
                    }
                }
            //endregion
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun NoMediaProvidersDialog(setShowDialog: (Boolean) -> Unit, navController: NavHostController) {
    Dialog(onDismissRequest = { setShowDialog(false) }) {
        Column(
            modifier = Modifier
                .shadow(12.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp)
                .dialogFocusable(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.Settings_Header_Media),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = stringResource(R.string.No_Providers_Splash),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = {
                    navController.navigate(Screen.S_Providers.route) {
                        launchSingleTop = true
                    }; setShowDialog(false)
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .bounceClick()
            ) {
                Text(
                    stringResource(R.string.Action_Go)
                )
            }
        }
    }
}