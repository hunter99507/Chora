package com.craftworks.music.ui.elements

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf

/**
 * Shared Compose state bridging AnimatedBottomNavBar and MainTabsPagerScreen.
 *
 * currentTabIndex   – written by the pager, read by the bottom nav bar for the selected indicator.
 * requestedTabIndex – written by the bottom nav bar / selectTab, read by the pager.
 * requestSequence   – incremented on every tab navigation request to ensure LaunchedEffect always triggers.
 */
object TabStateHolder {
    /** The page currently settled/visible in the HorizontalPager. */
    var currentTabIndex = mutableIntStateOf(0)

    /** The page the bottom nav bar wants the pager to scroll to. */
    var requestedTabIndex = mutableIntStateOf(0)

    /** Sequence counter ensuring even repeat clicks to the same tab index trigger navigation. */
    var requestSequence = mutableLongStateOf(0L)

    fun scrollToTab(index: Int) {
        requestedTabIndex.intValue = index
        requestSequence.longValue++
    }

    fun selectTab(route: String) {
        val index = when (route) {
            "home_screen" -> 0
            "album_screen" -> 1
            "songs_screen" -> 2
            "artists_screen" -> 3
            "playlist_screen" -> 4
            else -> 0
        }
        scrollToTab(index)
    }
}
