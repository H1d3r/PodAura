package com.skyd.anivu.ext

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.provider.Settings

/**
 * 获取系统屏幕亮度
 */
fun Activity.getScreenBrightness(): Int? = try {
    Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
} catch (e: Settings.SettingNotFoundException) {
    e.printStackTrace()
    null
}

fun Activity.landOrientation() {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
}

@SuppressLint("SourceLockedOrientationActivity")
fun Activity.portOrientation() {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}