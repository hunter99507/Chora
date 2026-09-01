package com.craftworks.music.ui.elements

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.craftworks.music.R
import com.craftworks.music.data.BottomNavItem
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import kotlin.math.abs

fun Modifier.swipeToNavigateTabs(
    navController: NavHostController,
    currentRoute: String,
    activeTabs: List<String>
): Modifier = this.pointerInput(currentRoute, activeTabs) {
    val currentIndex = activeTabs.indexOf(currentRoute)
    if (currentIndex == -1) return@pointerInput

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var totalDragX = 0f
        var totalDragY = 0f
        var isHorizontalDrag: Boolean? = null

        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                // Gesture ended (finger lifted)
                if (isHorizontalDrag == true) {
                    if (totalDragX < -60f && currentIndex < activeTabs.size - 1) {
                        // Swiped left -> Go to next active tab
                        val targetRoute = activeTabs[currentIndex + 1]
                        navController.navigate(targetRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    } else if (totalDragX > 60f && currentIndex > 0) {
                        // Swiped right -> Go to previous active tab
                        val targetRoute = activeTabs[currentIndex - 1]
                        navController.navigate(targetRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
                break
            }

            val dragX = change.position.x - change.previousPosition.x
            val dragY = change.position.y - change.previousPosition.y
            totalDragX += dragX
            totalDragY += dragY

            if (isHorizontalDrag == null) {
                if (abs(totalDragX) > 20f && abs(totalDragX) > abs(totalDragY) * 1.2f) {
                    isHorizontalDrag = true
                } else if (abs(totalDragY) > 20f) {
                    isHorizontalDrag = false
                }
            }
        }
    }
}

@Composable
fun SwipeableTabContent(
    navController: NavHostController,
    route: String,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val navItems by AppearanceSettingsManager(context).bottomNavItemsFlow.collectAsState(
        initial = listOf(
            BottomNavItem("Home", R.drawable.rounded_home_24, "home_screen"),
            BottomNavItem("Albums", R.drawable.rounded_library_music_24, "album_screen"),
            BottomNavItem("Songs", R.drawable.round_music_note_24, "songs_screen", false),
            BottomNavItem("Artists", R.drawable.rounded_artist_24, "artists_screen"),
            BottomNavItem("Playlists", R.drawable.rounded_queue_music_24, "playlist_screen")
        )
    )

    val activeTabs = remember(navItems) {
        navItems.filter { it.enabled }.map { it.screenRoute }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .swipeToNavigateTabs(navController, route, activeTabs)
    ) {
        content()
    }
}
