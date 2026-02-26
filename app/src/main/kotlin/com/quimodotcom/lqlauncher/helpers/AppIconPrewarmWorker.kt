package com.quimodotcom.lqlauncher.helpers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ComponentName
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AppIconPrewarmWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        try {
            val pm = applicationContext.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val resolve = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            val components = resolve.mapNotNull { it.activityInfo?.let { ai -> ComponentName(ai.packageName, ai.name) } }
            // Target a reasonable default size (match app tile size used in UI)
            val density = applicationContext.resources.displayMetrics.density
            val sizePx = Math.round(density * 64f)
            val requests = components.map { AppIconCache.IconRequest(it) }
            AppIconCache.prewarmMemoryCache(applicationContext, requests, sizePx)
        } catch (e: Exception) {
            return Result.failure()
        }
        return Result.success()
    }
}
