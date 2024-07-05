package com.skyd.anivu.ui.fragment.filepicker

import com.skyd.anivu.base.mvi.MviSingleEvent

sealed interface FilePickerEvent : MviSingleEvent {
    sealed interface FileListResultEvent : FilePickerEvent {
        data class Failed(val msg: String) : FileListResultEvent
    }
}