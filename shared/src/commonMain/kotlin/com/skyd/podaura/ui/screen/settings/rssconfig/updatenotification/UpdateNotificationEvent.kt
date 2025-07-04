package com.skyd.podaura.ui.screen.settings.rssconfig.updatenotification

import com.skyd.mvi.MviSingleEvent

sealed interface UpdateNotificationEvent : MviSingleEvent {
    sealed interface RuleListResultEvent : UpdateNotificationEvent {
        data class Failed(val msg: String) : RuleListResultEvent
    }

    sealed interface AddResultEvent : UpdateNotificationEvent {
        data class Failed(val msg: String) : AddResultEvent
    }

    sealed interface RemoveResultEvent : UpdateNotificationEvent {
        data class Failed(val msg: String) : RemoveResultEvent
    }
}