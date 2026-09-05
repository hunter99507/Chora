package com.craftworks.music.ui.screens.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.craftworks.music.data.model.Screen
import com.craftworks.music.ui.elements.tv.TvArtistCard
import com.craftworks.music.ui.viewmodels.ArtistsScreenViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvArtistScreen(
    navHostController: NavHostController = rememberNavController(),
    viewModel: ArtistsScreenViewModel = hiltViewModel(),
) {
    val allArtistList by viewModel.allArtists.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.setShowFavoritesOnly(false)
        focusRequester.requestFocus()
    }

    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .focusGroup()
            .focusRequester(focusRequester)
            .focusRestorer(focusRequester),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(allArtistList) { artist ->
            TvArtistCard(
                artist = artist,
                modifier = Modifier.onFocusChanged {
                    focusRequester.saveFocusedChild()
                },
                onClick = {
                    focusRequester.saveFocusedChild()
                    viewModel.setSelectedArtist(artist)
                    navHostController.navigate(Screen.ArtistDetails.route) {
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}