package com.skyd.anivu.ui.screen.feed.requestheaders

import com.skyd.anivu.base.mvi.MviIntent
import com.skyd.anivu.model.bean.FeedBean

sealed interface RequestHeadersIntent : MviIntent {
    data class Init(val feedUrl: String) : RequestHeadersIntent
    data class UpdateHeaders(val feedUrl: String, val headers: FeedBean.RequestHeaders) :
        RequestHeadersIntent
}