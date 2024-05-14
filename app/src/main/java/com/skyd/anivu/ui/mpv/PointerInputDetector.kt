package com.skyd.anivu.ui.mpv

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.skyd.anivu.ext.activity
import com.skyd.anivu.ext.detectDoubleFingerTransformGestures
import com.skyd.anivu.ext.getScreenBrightness
import com.skyd.anivu.model.preference.player.PlayerDoubleTapPreference
import com.skyd.anivu.ui.local.LocalPlayerDoubleTap
import kotlin.math.abs

private val inStatusBarArea: PointerInputScope.(y: Float) -> Boolean = { y ->
    y / density <= 60
}

@Composable
internal fun Modifier.detectPressGestures(
    controllerWidth: () -> Int,
    currentPosition: () -> Int,
    onSeekTo: (Int) -> Unit,
    onPlayOrPause: () -> Unit,
    speed: () -> Float,
    onSpeedChanged: (Float) -> Unit,
    showController: () -> Boolean,
    onShowControllerChanged: (Boolean) -> Unit,
    isLongPressing: () -> Boolean,
    isLongPressingChanged: (Boolean) -> Unit,
    onShowForwardRipple: (Offset) -> Unit,
    onShowBackwardRipple: (Offset) -> Unit,
    cancelAutoHideControllerRunnable: () -> Boolean,
    restartAutoHideControllerRunnable: () -> Unit,
): Modifier {
    var beforeLongPressingSpeed by remember { mutableFloatStateOf(speed()) }

    val playerDoubleTap = LocalPlayerDoubleTap.current
    val onDoubleTapPausePlay: () -> Unit = remember { { onPlayOrPause() } }

    val onDoubleTapBackwardForward: (Offset) -> Unit = { offset ->
        if (offset.x < controllerWidth() / 2f) {
            onSeekTo(currentPosition() - 10) // -10s.
            onShowBackwardRipple(offset)
        } else {
            onSeekTo(currentPosition() + 10) // +10s.
            onShowForwardRipple(offset)
        }
    }
    val onDoubleTapBackwardPausePlayForward: (Offset) -> Unit = { offset ->
        if (offset.x <= controllerWidth() * 0.25f) {
            onSeekTo(currentPosition() - 10) // -10s.
            onShowBackwardRipple(offset)
        } else if (offset.x >= controllerWidth() * 0.75f) {
            onSeekTo(currentPosition() + 10) // +10s.
            onShowForwardRipple(offset)
        } else {
            onDoubleTapPausePlay()
        }
    }

    val onDoubleTap: (Offset) -> Unit = { offset ->
        when (playerDoubleTap) {
            PlayerDoubleTapPreference.BACKWARD_FORWARD -> onDoubleTapBackwardForward(offset)
            PlayerDoubleTapPreference.BACKWARD_PAUSE_PLAY_FORWARD ->
                onDoubleTapBackwardPausePlayForward(offset)

            else -> onDoubleTapPausePlay()
        }
    }

    return pointerInput(playerDoubleTap) {
        detectTapGestures(
            onLongPress = {
                beforeLongPressingSpeed = speed()
                isLongPressingChanged(true)
                onSpeedChanged(3f)
            },
            onDoubleTap = {
                restartAutoHideControllerRunnable()
                onDoubleTap(it)
            },
            onPress = {
                tryAwaitRelease()
                if (isLongPressing()) {
                    isLongPressingChanged(false)
                    onSpeedChanged(beforeLongPressingSpeed)
                }
            },
            onTap = {
                cancelAutoHideControllerRunnable()
                onShowControllerChanged(!showController())
            }
        )
    }
}

@Composable
internal fun Modifier.detectControllerGestures(
    enabled: () -> Boolean,
    controllerWidth: () -> Int,
    controllerHeight: () -> Int,
    onShowBrightness: (Boolean) -> Unit,
    onBrightnessRangeChanged: (ClosedFloatingPointRange<Float>) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onShowVolume: (Boolean) -> Unit,
    onVolumeRangeChanged: (IntRange) -> Unit,
    onVolumeChanged: (Int) -> Unit,
    currentPosition: () -> Int,
    onShowSeekTimePreview: (Boolean) -> Unit,
    onTimePreviewChanged: (Int) -> Unit,
    onSeekTo: (Int) -> Unit,
    videoRotate: () -> Float,
    onVideoRotate: (Float) -> Unit,
    videoZoom: () -> Float,
    onVideoZoom: (Float) -> Unit,
    videoOffset: () -> Offset,
    onVideoOffset: (Offset) -> Unit,
    cancelAutoHideControllerRunnable: () -> Boolean,
    restartAutoHideControllerRunnable: () -> Unit,
): Modifier {
    if (!enabled()) {
        onShowBrightness(false)
        onShowVolume(false)
        onShowSeekTimePreview(false)
        restartAutoHideControllerRunnable()
        return this
    }

    var pointerStartX by rememberSaveable { mutableFloatStateOf(0f) }
    var pointerStartY by rememberSaveable { mutableFloatStateOf(0f) }

    val context = LocalContext.current
    val activity = remember(context) { context.activity }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val minVolume = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
    } else 0

    var startBrightness by rememberSaveable { mutableFloatStateOf(0f) }
    var startVolume by rememberSaveable { mutableIntStateOf(0) }

    var seekTimePreviewStartPosition by remember { mutableIntStateOf(0) }
    var seekTimePreviewPositionDelta by remember { mutableIntStateOf(0) }

    return pointerInput(Unit) {
        detectDoubleFingerTransformGestures(
            onVerticalDragStart = onVerticalDragStart@{
                cancelAutoHideControllerRunnable()
                pointerStartX = it.x
                pointerStartY = it.y
                if (inStatusBarArea(it.y)) return@onVerticalDragStart
                when (pointerStartX) {
                    in 0f..controllerWidth() / 3f -> {
                        onBrightnessRangeChanged(0.01f..1f)
                        startBrightness = activity.window.attributes.apply {
                            if (screenBrightness <= 0.00f) {
                                val brightness = activity.getScreenBrightness()
                                if (brightness != null) {
                                    screenBrightness = brightness / 255.0f
                                    activity.window.setAttributes(this)
                                }
                            }
                        }.screenBrightness
                        onBrightnessChanged(startBrightness)
                        onShowBrightness(true)
                    }

                    in controllerWidth() * 2 / 3f..controllerWidth().toFloat() -> {
                        onVolumeRangeChanged(minVolume..maxVolume)
                        startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        onVolumeChanged(startVolume)
                        onShowVolume(true)
                    }
                }
            },
            onVerticalDragEnd = {
                restartAutoHideControllerRunnable()
                onShowBrightness(false)
                onShowVolume(false)
            },
            onVerticalDragCancel = {
                restartAutoHideControllerRunnable()
                onShowBrightness(false)
                onShowVolume(false)
            },
            onVerticalDrag = onVerticalDrag@{ change, _ ->
                val deltaY = change.position.y - pointerStartY
                if (inStatusBarArea(pointerStartY) || abs(deltaY) < 50) return@onVerticalDrag

                when (pointerStartX) {
                    in 0f..controllerWidth() / 3f -> {
                        val layoutParams = activity.window.attributes
                        layoutParams.screenBrightness =
                            (startBrightness - deltaY / controllerHeight())
                                .coerceIn(0.01f..1f)
                        activity.window.setAttributes(layoutParams)
                        onBrightnessChanged(layoutParams.screenBrightness)
                    }

                    in controllerWidth() * 2 / 3f..controllerWidth().toFloat() -> {
                        val desiredVolume = (startVolume - deltaY / controllerHeight() *
                                1.2f * (maxVolume - minVolume)).toInt()
                            .coerceIn(minVolume..maxVolume)
                        onVolumeChanged(desiredVolume)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, desiredVolume, 0)
                    }
                }
            },
            onHorizontalDragStart = onHorizontalDragStart@{
                cancelAutoHideControllerRunnable()
                pointerStartX = it.x
                pointerStartY = it.y
                if (inStatusBarArea(it.y)) return@onHorizontalDragStart
                seekTimePreviewStartPosition = currentPosition()
                seekTimePreviewPositionDelta = 0
                onShowSeekTimePreview(true)
            },
            onHorizontalDragEnd = onHorizontalDragEnd@{
                onShowSeekTimePreview(false)
                restartAutoHideControllerRunnable()
                if (inStatusBarArea(pointerStartY)) return@onHorizontalDragEnd
                onSeekTo(seekTimePreviewStartPosition + seekTimePreviewPositionDelta)
            },
            onHorizontalDragCancel = {
                onShowSeekTimePreview(false)
                restartAutoHideControllerRunnable()
            },
            onHorizontalDrag = onHorizontalDrag@{ change, _ ->
                if (inStatusBarArea(pointerStartY)) return@onHorizontalDrag
                seekTimePreviewPositionDelta =
                    ((change.position.x - pointerStartX) / density / 8).toInt()
                onTimePreviewChanged(seekTimePreviewStartPosition + seekTimePreviewPositionDelta)
            },
            onGesture = onGesture@{ _: Offset, pan: Offset, zoom: Float, rotation: Float ->
                onVideoOffset(videoOffset() + pan)
                onVideoRotate(videoRotate() + rotation)
                onVideoZoom(videoZoom() * zoom)
            }
        )
    }
}