package com.craftworks.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.session.MediaController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.craftworks.music.R
import com.craftworks.music.data.BottomNavItem
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.ui.elements.TabStateHolder
import com.craftworks.music.ui.viewmodels.AlbumScreenViewModel
import com.craftworks.music.ui.viewmodels.ArtistsScreenViewModel
import com.craftworks.music.ui.viewmodels.HomeScreenViewModel
import com.craftworks.music.ui.viewmodels.PlaylistScreenViewModel
import com.craftworks.music.ui.viewmodels.SongsScreenViewModel

/**
 * Hosts all 5 main tab screens inside a [HorizontalPager] for true finger-tracking navigation.
 *
 * The pager page <-> bottom nav bar sync is managed via [TabStateHolder]:
 *  - The pager writes [TabStateHolder.currentTabIndex] on every page change.
 *  - The bottom nav bar writes [TabStateHolder.requestedTabIndex] when a tab icon is tapped.
 *  - A [LaunchedEffect] on [TabStateHolder.requestedTabIndex] animates the pager to the requested page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainTabsPagerScreen(
    parentBackStackEntry: NavBackStackEntry,
    navController: NavHostController,
    mediaController: MediaController?
) {
    val context = LocalContext.current

    val navItems by AppearanceSettingsManager(context).bottomNavItemsFlow.collectAsState(
        initial = listOf(
            BottomNavItem("Home", R.drawable.rounded_home_24, "home_screen"),
            BottomNavItem("Albums", R.drawable.rounded_library_music_24, "album_screen"),
            BottomNavItem("Songs", R.drawable.round_music_note_24, "songs_screen"),
            BottomNavItem("Artists", R.drawable.rounded_artist_24, "artists_screen"),
            BottomNavItem("Playlists", R.drawable.rounded_queue_music_24, "playlist_screen")
        )
    )

    // Only enabled, non-radio tabs participate in the pager
    val activeTabs = remember(navItems) {
        navItems.filter { it.enabled && it.screenRoute != "radio_screen" }.map { it.screenRoute }
    }

    val pagerState = rememberPagerState(
        initialPage = TabStateHolder.currentTabIndex.intValue.coerceIn(0, (activeTabs.size - 1).coerceAtLeast(0)),
        pageCount = { activeTabs.size }
    )

    // Pager -> TabStateHolder: keep currentTabIndex up to date as pages scroll
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            TabStateHolder.currentTabIndex.intValue = page
        }
    }

    // TabStateHolder.requestSequence -> Pager: bottom nav bar requests a scroll
    LaunchedEffect(TabStateHolder.requestSequence.longValue) {
        if (TabStateHolder.requestSequence.longValue == 0L) return@LaunchedEffect
        val target = TabStateHolder.requestedTabIndex.intValue.coerceIn(0, (activeTabs.size - 1).coerceAtLeast(0))
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    LaunchedEffect(Unit) {
        val target = TabStateHolder.requestedTabIndex.intValue.coerceIn(0, (activeTabs.size - 1).coerceAtLeast(0))
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    // ViewModels scoped to parent (main_graph) entry so state survives pager page changes
    val homeViewModel: HomeScreenViewModel = hiltViewModel(parentBackStackEntry)
    val playlistViewModel: PlaylistScreenViewModel = hiltViewModel(parentBackStackEntry)
    val albumViewModel: AlbumScreenViewModel = hiltViewModel(parentBackStackEntry)
    val songsViewModel: SongsScreenViewModel = hiltViewModel(parentBackStackEntry)
    val artistsViewModel: ArtistsScreenViewModel = hiltViewModel(parentBackStackEntry)

    HorizontalPager(
        state = pagerState,
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        // Pre-compose one page on each side so adjacent pages are ready during drag
        beyondViewportPageCount = 1,
        key = { activeTabs.getOrElse(it) { it.toString() } }
    ) { pageIndex ->
        when (activeTabs.getOrNull(pageIndex)) {
            "home_screen"    -> HomeScreen(navController, mediaController, homeViewModel)
            "album_screen"   -> AlbumScreen(navController, mediaController, albumViewModel)
            "songs_screen"   -> SongsScreen(mediaController, songsViewModel)
            "artists_screen" -> ArtistsScreen(navController, mediaController, artistsViewModel)
            "playlist_screen"-> PlaylistScreen(navController, playlistViewModel)
            else             -> { /* unknown tab - render nothing */ }
        }
    }
}
