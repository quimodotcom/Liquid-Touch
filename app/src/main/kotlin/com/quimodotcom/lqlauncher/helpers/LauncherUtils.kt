package com.quimodotcom.lqlauncher.helpers

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.app.Activity
import com.quimodotcom.lqlauncher.compose.launcher.LauncherConfig
import com.quimodotcom.lqlauncher.compose.launcher.LauncherItem

object LauncherUtils {

    fun launchApp(context: Context, packageName: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                context.startActivity(intent)
                if (context is Activity) {
                    if (android.os.Build.VERSION.SDK_INT >= 34) {
                        context.overrideActivityTransition(
                            Activity.OVERRIDE_TRANSITION_OPEN,
                            android.R.anim.fade_in,
                            android.R.anim.fade_out
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        context.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore failures
        }
    }

    fun loadDrawableFromUri(context: Context, uriString: String): Drawable? {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use {
                Drawable.createFromStream(it, uriString)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun findEmptyCell(config: LauncherConfig): Pair<Int, Int> {
        val occupied = config.items.flatMap { item ->
            (0 until item.spanX).flatMap { dx ->
                (0 until item.spanY).map { dy ->
                    (item.gridX + dx) to (item.gridY + dy)
                }
            }
        }.toSet()

        for (y in 0 until config.gridRows) {
            for (x in 0 until config.gridColumns) {
                if ((x to y) !in occupied) return x to y
            }
        }
        return 0 to 0
    }

    /**
     * Calculates battery wattage given current in microamperes and voltage in millivolts.
     * Returns value in Watts.
     */
    fun calculateWattage(currentUa: Long, voltageMv: Int): Float {
        val currentA = currentUa / 1_000_000f
        val voltageV = voltageMv / 1_000f
        return currentA * voltageV
    }
}
