package com.skyd.anivu.ui.mpv.service

import coil3.Bitmap
import com.skyd.anivu.ui.mpv.MPVPlayer


data class PlayerState(
    val mediaLoaded: Boolean = false,
    val audioTrackId: Int = 0,
    val subtitleTrackId: Int = 0,
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val speed: Float = 1f,
    val audioTracks: List<MPVPlayer.Track> = listOf(),
    val subtitleTracks: List<MPVPlayer.Track> = listOf(),
    val buffer: Int = 0,
    val artist: String? = null,
    val album: String? = null,
    val position: Long = 0L,
    val duration: Long = 0L,
    val rotate: Float = 0f,
    val playlistPosition: Int = -1,
    val playlistCount: Int = 0,
    val paused: Boolean = true,
    val pausedForCache: Boolean = false,
    val shuffle: Boolean = false,
    val idling: Boolean = true,
    val title: String? = null,
    val thumbnail: Bitmap? = null,
)