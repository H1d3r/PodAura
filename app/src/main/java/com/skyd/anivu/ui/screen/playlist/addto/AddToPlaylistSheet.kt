package com.skyd.anivu.ui.screen.playlist.addto

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.skyd.anivu.R
import com.skyd.anivu.base.mvi.getDispatcher
import com.skyd.anivu.ext.safeItemKey
import com.skyd.anivu.model.bean.playlist.MediaUrlWithArticleIdBean
import com.skyd.anivu.model.bean.playlist.PlaylistViewBean
import com.skyd.anivu.ui.component.CircularProgressPlaceholder
import com.skyd.anivu.ui.component.ErrorPlaceholder
import com.skyd.anivu.ui.component.PagingRefreshStateIndicator
import com.skyd.anivu.ui.screen.playlist.PlaylistItem
import com.skyd.anivu.ui.screen.playlist.PlaylistItemPlaceholder


@Composable
fun AddToPlaylistSheet(
    onDismissRequest: () -> Unit,
    currentPlaylistId: String?,
    selectedMediaList: List<MediaUrlWithArticleIdBean>,
    viewModel: AddToPlaylistViewModel = hiltViewModel(),
) {
    val dispatch = viewModel.getDispatcher(
        currentPlaylistId,
        selectedMediaList,
        startWith = AddToPlaylistIntent.Init(currentPlaylistId, selectedMediaList)
    )
    val uiState by viewModel.viewState.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Text(
            text = stringResource(R.string.add_to_playlist),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(6.dp))
        when (val playlistState = uiState.playlistState) {
            is PlaylistState.Failed -> ErrorPlaceholder(playlistState.msg)
            PlaylistState.Init -> CircularProgressPlaceholder()
            is PlaylistState.Success -> AddToPlaylistSheetContent(
                playlist = playlistState.playlistPagingDataFlow.collectAsLazyPagingItems(),
                selected = { it.playlist.playlistId in uiState.addedPlaylists },
                onSelect = {
                    dispatch(AddToPlaylistIntent.AddTo(medias = selectedMediaList, playlist = it))
                },
                onRemove = {
                    dispatch(
                        AddToPlaylistIntent.RemoveFromPlaylist(
                            medias = selectedMediaList,
                            playlist = it,
                        )
                    )
                },
            )
        }
    }
}


@Composable
fun AddToPlaylistSheetContent(
    playlist: LazyPagingItems<PlaylistViewBean>,
    selected: (PlaylistViewBean) -> Boolean,
    onSelect: (PlaylistViewBean) -> Unit,
    onRemove: (PlaylistViewBean) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    PagingRefreshStateIndicator(
        lazyPagingItems = playlist,
        placeholderPadding = contentPadding,
    ) {
        LazyColumn {
            items(
                count = playlist.itemCount,
                key = playlist.safeItemKey { it.playlist.playlistId },
            ) { index ->
                when (val item = playlist[index]) {
                    is PlaylistViewBean -> {
                        val s = selected(item)
                        PlaylistItem(
                            playlistViewBean = item,
                            selected = s,
                            onClick = { if (s) onRemove(item) else onSelect(item) },
                            onRename = {},
                            onDelete = { },
                        )
                    }

                    else -> PlaylistItemPlaceholder()
                }
            }
        }
    }
}