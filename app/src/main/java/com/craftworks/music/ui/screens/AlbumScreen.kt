package com.craftworks.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.craftworks.music.R
import com.craftworks.music.data.model.Screen
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.ui.elements.AlbumGrid
import com.craftworks.music.ui.elements.RippleEffect
import com.craftworks.music.ui.elements.TopBarWithSearch
import com.craftworks.music.ui.playing.dpToPx
import androidx.compose.runtime.rememberCoroutineScope
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.viewmodels.AlbumScreenViewModel
import kotlinx.coroutines.launch
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalFoundationApi
@Composable
fun AlbumScreen(
    navHostController: NavHostController = rememberNavController(),
    mediaController: MediaController? = null,
    viewModel: AlbumScreenViewModel = hiltViewModel(),
) {
    val albums by viewModel.allAlbums.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()

    val state = rememberPullToRefreshState()
    val isRefreshing by viewModel.isLoading.collectAsStateWithLifecycle()

    var showRipple by remember { mutableIntStateOf(0) }
    val rippleXOffset = LocalWindowInfo.current.containerSize.width / 2
    val rippleYOffset = dpToPx(12)

    val onRefresh: () -> Unit = {
        viewModel.getAlbums()
        showRipple++
    }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    var showSortMenu by remember { mutableStateOf(false) }

    val showFavoritesOnly by viewModel.showFavoritesOnly.collectAsStateWithLifecycle()

    PullToRefreshBox(
        state = state,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                Column {
                    TopBarWithSearch(
                        headerText = stringResource(R.string.Albums),
                        scrollBehavior = scrollBehavior,
                        onSearch = { query -> viewModel.search(query) },
                        searchResults = {
                            AlbumGrid(
                                searchResults,
                                mediaController,
                                onAlbumSelected = { album ->
                                    val encodedImage = URLEncoder.encode(album.coverArt, "UTF-8")
                                    navHostController.navigate(Screen.AlbumDetails.route + "/${album.navidromeID}/$encodedImage") {
                                        launchSingleTop = true
                                    }
                                },
                                true,
                                viewModel
                            )
                        },
                        extraAction = {
                            Row {
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            if (albums.isNotEmpty()) {
                                                val randomAlbum = albums.random()
                                                val albumId = randomAlbum.mediaMetadata.extras?.getString("navidromeID") ?: randomAlbum.mediaId
                                                val albumItems = viewModel.getAlbum(albumId)
                                                val songs = if (albumItems.size > 1) albumItems.subList(1, albumItems.size) else albumItems
                                                if (songs.isNotEmpty()) {
                                                    SongHelper.play(songs, 0, mediaController, shuffle = true)
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.round_shuffle_28),
                                        contentDescription = "Shuffle random album songs",
                                    )
                                }


                                Box {
                                    IconButton(
                                        onClick = { showSortMenu = true }
                                    ) {
                                        Icon(
                                            imageVector = ImageVector.vectorResource(R.drawable.rounded_sort_24),
                                            contentDescription = stringResource(R.string.Label_Sorting),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.Label_Sort_Alphabetical)) },
                                            onClick = {
                                                viewModel.setSorting(SortOrder.ALPHABETICAL)
                                                showSortMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.recently_added)) },
                                            onClick = {
                                                viewModel.setSorting(SortOrder.NEWEST)
                                                showSortMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.recently_played)) },
                                            onClick = {
                                                viewModel.setSorting(SortOrder.RECENT)
                                                showSortMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.most_played)) },
                                            onClick = {
                                                viewModel.setSorting(SortOrder.FREQUENT)
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                AlbumGrid(
                    albums,
                    mediaController,
                    onAlbumSelected = { album ->
                        val encodedImage = URLEncoder.encode(album.coverArt, "UTF-8")
                        navHostController.navigate(Screen.AlbumDetails.route + "/${album.navidromeID}/$encodedImage") {
                            launchSingleTop = true
                        }
                    },
                    false,
                    viewModel
                )
            }
        }
    }

    RippleEffect(
        center = Offset(rippleXOffset.toFloat(), rippleYOffset.toFloat()),
        color = MaterialTheme.colorScheme.surfaceVariant,
        key = showRipple
    )
}