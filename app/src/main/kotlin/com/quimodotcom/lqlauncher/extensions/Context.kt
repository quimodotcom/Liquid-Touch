package com.quimodotcom.lqlauncher.extensions

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import com.quimodotcom.lqlauncher.helpers.Config
import kotlin.math.ceil
import kotlin.math.max

val Context.config: Config get() = Config.newInstance(applicationContext)

fun Context.getDrawableForPackageName(packageName: String): Drawable? {
    var drawable: Drawable? = null
    try {
        // try getting the properly colored launcher icons
        val launcher = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val activityList = launcher.getActivityList(packageName, Process.myUserHandle())[0]
        drawable = activityList.getBadgedIcon(0)
    } catch (e: Exception) {
    } catch (e: Error) {
    }

    if (drawable == null) {
        drawable = try {
            packageManager.getApplicationIcon(packageName)
        } catch (ignored: Exception) {
            null
        }
    }

    return drawable
}

fun Context.getCellCount(size: Int): Int {
    val tiles = ceil(((size / resources.displayMetrics.density) - 30) / 70.0).toInt()
    return max(tiles, 1)
}
