package com.skyd.anivu.ext

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.TypedArray
import android.graphics.Point
import android.os.Build
import android.util.TypedValue
import androidx.core.content.ContextCompat

val Context.activity: Activity
    get() {
        return tryActivity ?: error("Can't find activity: $this")
    }

@get:JvmName("tryActivity")
val Context.tryActivity: Activity?
    get() {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                return ctx
            }
            ctx = ctx.baseContext
        }
        return null
    }

val Context.screenIsLand: Boolean
    get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

fun Context.screenHeight(includeVirtualKey: Boolean): Int {
    val display = ContextCompat.getDisplayOrDefault(this)
    val outPoint = Point()
    // 可能有虚拟按键的情况
    if (includeVirtualKey) display.getRealSize(outPoint)
    else display.getSize(outPoint)
    return outPoint.y
}

fun Context.screenWidth(includeVirtualKey: Boolean): Int {
    val display = ContextCompat.getDisplayOrDefault(this)
    val outPoint = Point()
    // 可能有虚拟按键的情况
    if (includeVirtualKey) display.getRealSize(outPoint)
    else display.getSize(outPoint)
    return outPoint.x
}

fun Context.getAttrColor(attr: Int): Int {
    val typedValue = TypedValue()
    val typedArray: TypedArray = obtainStyledAttributes(typedValue.data, intArrayOf(attr))
    val color = typedArray.getColor(0, 0)
    typedArray.recycle()
    return color
}

fun Context.getAppVersionName(): String {
    var appVersionName = ""
    try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            packageManager.getPackageInfo(packageName, 0)
        }
        appVersionName = packageInfo.versionName
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
    }
    return appVersionName
}

fun Context.getAppName(): String? {
    return try {
        val packageInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
        val labelRes: Int = packageInfo.applicationInfo.labelRes
        getString(labelRes)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}