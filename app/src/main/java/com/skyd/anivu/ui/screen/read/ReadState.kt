package com.skyd.anivu.ui.screen.read

import com.skyd.anivu.base.mvi.MviViewState
import com.skyd.anivu.model.bean.article.ArticleWithEnclosureBean

data class ReadState(
    val articleState: ArticleState,
    val loadingDialog: Boolean,
) : MviViewState {
    companion object {
        fun initial() = ReadState(
            articleState = ArticleState.Init,
            loadingDialog = true,
        )
    }
}

sealed interface ArticleState {
    data class Success(val article: ArticleWithEnclosureBean) : ArticleState
    data object Init : ArticleState
    data object Loading : ArticleState
    data class Failed(val msg: String) : ArticleState
}