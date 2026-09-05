package com.craftworks.music.ui.screens.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.navigation.NavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.text.font.FontWeight
import com.craftworks.music.R
import com.craftworks.music.data.model.Screen
import com.craftworks.music.managers.NavidromeManager
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.elements.dialogs.tv.SongDialog
import com.craftworks.music.ui.elements.tv.TvHorizontalSongCard
import com.craftworks.music.ui.viewmodels.SongsScreenViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSongsScreen(
    mediaController: MediaController? = null,
    navHostController: NavController,
    viewModel: SongsScreenViewModel = hiltViewModel(),
) {
    val songs by viewModel.allSongs.collectAsStateWithLifecycle()

    var selectedSong by remember { mutableStateOf(MediaItem.EMPTY) }
    var showSongDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    if (NavidromeManager.checkActiveServers()) {
        LaunchedEffect(songs.size) {
            if (songs.size % 50 != 0) return@LaunchedEffect
            if (songs.size < 50) return@LaunchedEffect

            snapshotFlow {
                val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                    ?: return@snapshotFlow false
                val total = gridState.layoutInfo.totalItemsCount
                if (total < songs.size - 5) return@snapshotFlow false
                (total - lastVisible) <= 15
            }.filter { it }.collect {
                viewModel.getMoreSongs(50)
            }
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .focusGroup()
            .focusRequester(focusRequester)
            .focusRestorer(focusRequester),
        contentPadding = PaddingValues(start = 32.dp, end = 48.dp, top = 20.dp, bottom = 28.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${songs.size} ${if (songs.size == 1) "Song" else "Songs"}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = {
                        viewModel.shuffleAll(mediaController)
                    },
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.round_shuffle_28),
                            contentDescription = stringResource(R.string.Action_Shuffle),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.Action_Shuffle))
                    }
                }
            }
        }

        itemsIndexed(songs) { index, song ->
            TvHorizontalSongCard(
                song = song,
                modifier = Modifier.onFocusChanged {
                    focusRequester.saveFocusedChild()
                },
                onClick = {
                    coroutineScope.launch {
                        SongHelper.play(songs, index, mediaController)
                        navHostController.navigate(Screen.NowPlayingLandscape.route) {
                            launchSingleTop = true
                        }
                    }
                },
                onLongClick = {
                    selectedSong = song
                    showSongDialog = true
                }
            )
        }
    }

    if (showSongDialog)
        SongDialog(
            song = selectedSong,
            onSetRating = { rating ->
                viewModel.setSongRating(
                    songId = selectedSong.mediaMetadata.extras?.getString("navidromeID") ?: "",
                    rating = rating
                )
            },
            setShowDialog = { showSongDialog = it }
        )
}