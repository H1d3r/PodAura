package com.skyd.podaura.ui.screen.feed.mute

import com.skyd.mvi.MviSingleEvent

sealed interface MuteFeedEvent : MviSingleEvent {
    sealed interface MuteResultEvent : MuteFeedEvent {
        data class Failed(val msg: String) : MuteResultEvent
    }
}