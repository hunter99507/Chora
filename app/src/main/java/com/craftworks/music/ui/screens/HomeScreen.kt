package com.craftworks.music.ui.screens

import com.craftworks.music.managers.DataRefreshManager
import com.craftworks.music.ui.elements.LocalBottomPadding

import android.content.res.Configuration
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.craftworks.music.R
import com.craftworks.music.data.model.Screen
import com.craftworks.music.managers.EmbyJellyfinManager
import com.craftworks.music.managers.NavidromeManager
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.elements.AlbumRow
import com.craftworks.music.ui.elements.RippleEffect
import com.craftworks.music.ui.elements.SongOfTheDayCard
import com.craftworks.music.ui.playing.dpToPx
import com.craftworks.music.ui.viewmodels.HomeScreenViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.craftworks.music.ui.elements.PlaylistCard
import com.craftworks.music.ui.viewmodels.PlaylistScreenViewModel
import java.net.URLEncoder
import kotlin.math.roundToInt

@Stable
@Serializable
data class HomeItem(
    var key: String,
    var enabled: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    navHostController: NavHostController = rememberNavController(),
    mediaController: MediaController? = null,
    viewModel: HomeScreenViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlistViewModel: PlaylistScreenViewModel = hiltViewModel(
        remember(navHostController.currentBackStackEntry) {
            navHostController.getBackStackEntry("main_graph")
        }
    )

    val recentlyPlayedAlbums by viewModel.recentlyPlayedAlbums.collectAsStateWithLifecycle()
    val recentAlbums by viewModel.recentAlbums.collectAsStateWithLifecycle()
    val mostPlayedAlbums by viewModel.mostPlayedAlbums.collectAsStateWithLifecycle()
    val shuffledAlbums by viewModel.shuffledAlbums.collectAsStateWithLifecycle()
    val songOfTheDay by viewModel.songOfTheDay.collectAsStateWithLifecycle()
    val artistOfTheDay by viewModel.artistOfTheDay.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    val state = rememberPullToRefreshState()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var showRipple by remember { mutableIntStateOf(0) }
    val rippleXOffset = LocalWindowInfo.current.containerSize.width / 2

    val rippleYOffset = dpToPx(WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value.roundToInt())

    val onRefresh: () -> Unit = {
        viewModel.loadHomeScreenData()
        showRipple++
    }

    val navidromeLibraries by NavidromeManager.libraries.collectAsStateWithLifecycle()
    val embyLibraries by EmbyJellyfinManager.libraries.collectAsStateWithLifecycle()
    val isEmbyActive = EmbyJellyfinManager.checkActiveServers()

    val navItems by AppearanceSettingsManager(context).bottomNavItemsFlow.collectAsStateWithLifecycle(emptyList())
    val activeTabs = remember(navItems) {
        navItems.filter { it.enabled && it.screenRoute != "radio_screen" }.map { it.screenRoute }
    }

    PullToRefreshBox(
        modifier = Modifier,
        state = state,
        isRefreshing = isLoading,
        onRefresh = onRefresh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 8.dp,
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                        bottom = 4.dp
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val choraGradient = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(200f, 0f)
                    )
                    @OptIn(ExperimentalTextApi::class)
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineLarge.merge(
                            TextStyle(
                                brush = choraGradient,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            )
                        )
                    )
                    if (com.craftworks.music.BuildConfig.DEDICATED_SOURCE == "ALL") {
                        com.craftworks.music.ui.elements.SourceSelectorPill()
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            viewModel.loadHomeScreenData(forceRefresh = true)
                            DataRefreshManager.notifyDataSourcesChanged()
                            showRipple++
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            navHostController.navigate(Screen.Setting.route) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.rounded_settings_24),
                            contentDescription = "Settings",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val orderedHomeItems = AppearanceSettingsManager(context).homeItemsItemsFlow.collectAsState(
                initial = listOf(
                    HomeItem(
                        "song_of_the_day",
                        true
                    ),
                    HomeItem(
                        "playlists",
                        true
                    ),
                    HomeItem(
                        "recently_played",
                        true
                    ),
                    HomeItem(
                        "recently_added",
                        true
                    ),
                    HomeItem(
                        "most_played",
                        true
                    ),
                    HomeItem(
                        "random_songs",
                        true
                    )
                )
            ).value

            orderedHomeItems.forEach { item ->
                if (item.enabled) {
                    if (item.key == "song_of_the_day") {
                        val currentArtist = artistOfTheDay
                        val currentSong = songOfTheDay
                        if (currentArtist != null || currentSong != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)
                            ) {
                                SongOfTheDayCard(
                                    artistOfTheDay = currentArtist,
                                    fallbackSong = currentSong,
                                    onPlaySong = { selectedSong, allSongs ->
                                        coroutineScope.launch {
                                            val otherSongs = allSongs.filter { it.mediaId != selectedSong.mediaId }.shuffled()
                                            val queue = listOf(selectedSong) + otherSongs
                                            SongHelper.play(
                                                mediaItems = queue,
                                                index = 0,
                                                mediaController = mediaController,
                                                expandSheet = true,
                                                shuffle = false
                                            )
                                            mediaController?.repeatMode = Player.REPEAT_MODE_ALL
                                        }
                                    }
                                )
                            }
                        }
                    } else if (item.key == "playlists") {
                        if (playlists.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.playlists),
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = MaterialTheme.typography.headlineSmall.fontSize
                                    )

                                    Spacer(modifier = Modifier.weight(1f))

                                    IconButton(
                                        onClick = {
                                            val activeTabIdx = activeTabs.indexOf("playlist_screen")
                                            if (activeTabIdx != -1) {
                                                com.craftworks.music.ui.elements.TabStateHolder.scrollToTab(activeTabIdx)
                                            } else {
                                                navHostController.navigate(Screen.Playlists.route) {
                                                    launchSingleTop = true
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                            contentDescription = stringResource(R.string.playlists),
                                            tint = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier
                                                .size(MaterialTheme.typography.headlineSmall.fontSize.value.dp * 1.2f)
                                        )
                                    }
                                }

                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 172.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(
                                        items = playlists,
                                        key = { it.mediaId }
                                    ) { playlist ->
                                        PlaylistCard(
                                            playlist = playlist,
                                            onClick = {
                                                playlistViewModel.setCurrentPlaylist(playlist)
                                                navHostController.navigate(Screen.PlaylistDetails.route) {
                                                    launchSingleTop = true
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val albums = when (item.key) {
                            "recently_played" -> recentlyPlayedAlbums
                            "recently_added" -> recentAlbums
                            "most_played" -> mostPlayedAlbums
                            "random_songs" -> shuffledAlbums
                            else -> emptyList()
                        }

                        val titleMap = remember {
                            mapOf(
                                "recently_played" to R.string.recently_played,
                                "recently_added" to R.string.recently_added,
                                "most_played" to R.string.most_played,
                                "random_songs" to R.string.random_songs
                            )
                        }

                        AlbumRow(
                            item.key,
                            titleMap[item.key],
                            albums,
                            mediaController,
                            navHostController,
                            viewModel
                        )
                    }
                }
            }
            val bottomPadding = LocalBottomPadding.current
            if (bottomPadding > 0.dp) {
                Spacer(modifier = Modifier.height(bottomPadding))
            }
        }
    }

    RippleEffect(
        center = Offset(rippleXOffset.toFloat(), rippleYOffset.toFloat()),
        color = MaterialTheme.colorScheme.surfaceVariant,
        key = showRipple
    )
}

@Composable fun NavidromeLogo(){
    var rotation by remember { mutableFloatStateOf(-10f) }
    val animatedRotation by animateFloatAsState(
        targetValue = rotation,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "Navidrome Logo Rotate"
    )
    val clickAction = rememberUpdatedState {
        rotation += 180f
    }

    val isClickable =
        if (LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK != Configuration.UI_MODE_TYPE_TELEVISION)
            Modifier.clickable { clickAction.value.invoke() }
        else
            Modifier

    Image(
        painter = painterResource(R.drawable.s_m_navidrome),
        contentDescription = "Navidrome Icon",
        modifier = Modifier
            .size(76.dp)
            .offset(x = (-36).dp)
            .shadow(24.dp, CircleShape)
            .graphicsLayer {
                rotationZ = animatedRotation
            }
            .then(isClickable)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun AlbumRow(
    key: String,
    title: Int?,
    albums: List<MediaItem>,
    mediaController: MediaController?,
    navHostController: NavHostController,
    viewModel: HomeScreenViewModel
) {
    val coroutineScope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = stringResource(title ?: androidx.media3.session.R.string.error_message_fallback),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                fontSize = MaterialTheme.typography.headlineSmall.fontSize
            )

            Spacer(modifier = Modifier.weight(1f))

            if (LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(MaterialTheme.typography.headlineSmall.fontSize.value.dp * 1.2f)
                )
            } else {
                IconButton(
                    onClick = {
                        navHostController.navigate(Screen.HomeLists.route + "/$key") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .size(MaterialTheme.typography.headlineSmall.fontSize.value.dp * 1.2f)
                    )
                }
            }
        }

        AlbumRow(
            albums,
            onAlbumSelected = { album ->
                val encodedImage = URLEncoder.encode(album.coverArt, "UTF-8")
                navHostController.navigate(Screen.AlbumDetails.route + "/${album.navidromeID}/$encodedImage") {
                    launchSingleTop = true
                }
            },
            onPlay = { album ->
                coroutineScope.launch {
                    val mediaItems = viewModel.getAlbumSongs(album.mediaMetadata.extras?.getString("navidromeID") ?: "")
                    val playableSongs = if (mediaItems.isNotEmpty() && mediaItems.first().mediaMetadata.isPlayable == false) {
                        mediaItems.drop(1)
                    } else {
                        mediaItems
                    }
                    if (playableSongs.isNotEmpty()) {
                        SongHelper.play(
                            mediaItems = playableSongs,
                            index = 0,
                            mediaController = mediaController
                        )
                    }
                }
            }
        )
    }
}