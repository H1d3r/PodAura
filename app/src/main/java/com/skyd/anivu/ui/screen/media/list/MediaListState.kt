package com.skyd.anivu.ui.screen.media.list

import com.skyd.anivu.base.mvi.MviViewState
import com.skyd.anivu.model.bean.MediaGroupBean
import com.skyd.anivu.model.bean.VideoBean

data class MediaListState(
    val listState: ListState,
    val groups: List<MediaGroupBean>,
    val loadingDialog: Boolean,
) : MviViewState {
    companion object {
        fun initial() = MediaListState(
            listState = ListState.Init(),
            groups = listOf(),
            loadingDialog = false,
        )
    }
}

sealed class ListState(open val loading: Boolean) {
    data class Success(val list: List<VideoBean>, override val loading: Boolean = false) :
        ListState(loading)

    data class Init(override val loading: Boolean = false) : ListState(loading)
    data class Failed(val msg: String, override val loading: Boolean = false) : ListState(loading)
}