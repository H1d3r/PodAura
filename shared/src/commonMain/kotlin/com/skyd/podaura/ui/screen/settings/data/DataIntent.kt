package com.skyd.podaura.ui.screen.settings.data

import com.skyd.podaura.ui.mvi.MviIntent

sealed interface DataIntent : MviIntent {
    data object Init : DataIntent
    data object ClearCache : DataIntent
    data object DeletePlayHistory : DataIntent
    data class DeleteArticleBefore(val timestamp: Long) : DataIntent
}