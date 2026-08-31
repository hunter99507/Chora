package com.craftworks.music.ui.elements

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.craftworks.music.data.model.Screen

val topLevelTabs = listOf(
    Screen.Home.route,
    Screen.Albums.route,
    Screen.Song.route,
    Screen.Artists.route,
    Screen.Playlists.route
)

fun Modifier.swipeToNavigateTabs(
    navController: NavHostController,
    currentRoute: String
): Modifier = this.pointerInput(currentRoute) {
    val currentIndex = topLevelTabs.indexOf(currentRoute)
    if (currentIndex == -1) return@pointerInput

    var totalDragX = 0f
    detectHorizontalDragGestures(
        onDragStart = { totalDragX = 0f },
        onDragEnd = {
            if (totalDragX < -100f && currentIndex < topLevelTabs.size - 1) {
                // Swiped left -> Go to next tab
                val targetRoute = topLevelTabs[currentIndex + 1]
                navController.navigate(targetRoute) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            } else if (totalDragX > 100f && currentIndex > 0) {
                // Swiped right -> Go to previous tab
                val targetRoute = topLevelTabs[currentIndex - 1]
                navController.navigate(targetRoute) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
        onHorizontalDrag = { _, dragAmount ->
            totalDragX += dragAmount
        }
    )
}

@Composable
fun SwipeableTabContent(
    navController: NavHostController,
    route: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .swipeToNavigateTabs(navController, route)
    ) {
        content()
    }
}
