package com.skyd.anivu.ui.mpv.pip

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRect
import androidx.core.util.Consumer
import com.skyd.anivu.ext.activity
import com.skyd.anivu.ui.mpv.controller.state.PlayState
import com.skyd.anivu.ui.mpv.controller.state.PlayStateCallback

@Composable
internal fun PipListenerPreAPI12(shouldEnterPipMode: Boolean) {
    val currentShouldEnterPipMode by rememberUpdatedState(newValue = shouldEnterPipMode)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    ) {
        val context = LocalContext.current
        DisposableEffect(context) {
            val activity = context.activity as ComponentActivity
            val onUserLeaveBehavior: () -> Unit = {
                if (currentShouldEnterPipMode) {
                    val builder = PictureInPictureParams.Builder()
                    activity.enterPictureInPictureMode(builder.build())
                }
            }
            activity.addOnUserLeaveHintListener(onUserLeaveBehavior)
            onDispose { activity.removeOnUserLeaveHintListener(onUserLeaveBehavior) }
        }
    } else {
        Log.i("PIP_TAG", "API does not support PiP")
    }
}

@Composable
internal fun Modifier.pipParams(
    context: Context,
    shouldEnterPipMode: Boolean,
    playState: PlayState,
): Modifier = run {
    var builder by rememberSaveable { mutableStateOf<PictureInPictureParams.Builder?>(null) }
    val currentPlayState by rememberUpdatedState(playState)
    val setActionsAndApplyBuilder: (PictureInPictureParams.Builder) -> Unit = remember {
        { builder ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setActions(
                    listOfRemoteActions(
                        playState = currentPlayState,
                        context = context,
                    ),
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(shouldEnterPipMode)
                }
                context.activity.setPictureInPictureParams(builder.build())
            }
        }
    }

    LaunchedEffect(playState.isPlaying) {
        builder?.let { setActionsAndApplyBuilder(it) }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        onGloballyPositioned { layoutCoordinates ->
            (builder ?: PictureInPictureParams.Builder()).let { b ->
                builder = b
                if (shouldEnterPipMode) {
                    b.setSourceRectHint(
                        layoutCoordinates
                            .boundsInWindow()
                            .toAndroidRectF()
                            .toRect()
                    )
                }
                setActionsAndApplyBuilder(b)
            }
        }
    } else this
}

@Composable
internal fun rememberIsInPipMode(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val activity = LocalContext.current.activity as ComponentActivity
        var pipMode by remember { mutableStateOf(activity.isInPictureInPictureMode) }
        DisposableEffect(activity) {
            val observer = Consumer<PictureInPictureModeChangedInfo> { info ->
                pipMode = info.isInPictureInPictureMode
            }
            activity.addOnPictureInPictureModeChangedListener(observer)
            onDispose { activity.removeOnPictureInPictureModeChangedListener(observer) }
        }
        return pipMode
    } else {
        return false
    }
}

@Composable
fun PipBroadcastReceiver(playStateCallback: PlayStateCallback) {
    if (rememberIsInPipMode()) {
        val context = LocalContext.current
        DisposableEffect(context) {
            val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if ((intent == null) || (intent.action != ACTION_BROADCAST_CONTROL)) {
                        return
                    }

                    when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                        EXTRA_CONTROL_PAUSE, EXTRA_CONTROL_PLAY ->
                            playStateCallback.onPlayStateChanged()
                    }
                }
            }
            ContextCompat.registerReceiver(
                context,
                broadcastReceiver,
                IntentFilter(ACTION_BROADCAST_CONTROL),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            onDispose {
                context.unregisterReceiver(broadcastReceiver)
            }
        }
    }
}

internal fun Activity.manualEnterPictureInPictureMode() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        enterPictureInPictureMode(PictureInPictureParams.Builder().build())
    }
}