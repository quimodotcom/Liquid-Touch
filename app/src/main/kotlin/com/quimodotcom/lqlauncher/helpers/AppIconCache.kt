package com.quimodotcom.lqlauncher.helpers

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

object AppIconCache {
    private const val DISK_CACHE_DIR = "app_icon_cache"
    private const val TAG = "AppIconCache"

    private lateinit var memoryCache: LruCache<String, Bitmap>
    private var initialized = false

    // Request object for prewarming
    data class IconRequest(
        val component: ComponentName,
        val customIconUri: String? = null,
        val iconPackPackage: String? = null
    )

    private fun ensureInit() {
        if (initialized) return
        val maxMem = (Runtime.getRuntime().maxMemory() / 1024).toInt() // KB
        // Use half of the process heap for cache but don't be smaller than 8MB
        val desiredKb = (maxMem * 0.5).toInt().coerceAtLeast(8 * 1024)
        memoryCache = object : LruCache<String, Bitmap>(desiredKb) {
            override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024)
        }
        initialized = true
    }

    private fun keyFor(component: ComponentName, sizePx: Int, customIconUri: String?, iconPack: String?): String {
        // Include custom URI and icon pack in key so if they change, we cache a new icon
        val uriHash = customIconUri?.hashCode() ?: 0
        val packHash = iconPack?.hashCode() ?: 0
        return "${component.packageName}/${component.className}_${sizePx}_${uriHash}_${packHash}"
    }

    /**
     * Synchronously retrieve icon from memory cache if available.
     * Returns null if not in memory.
     */
    fun getIconFromMemory(
        context: Context,
        component: ComponentName,
        sizePx: Int,
        customIconUri: String? = null,
        iconPackPackage: String? = null
    ): Bitmap? {
        ensureInit()
        val key = keyFor(component, sizePx, customIconUri, iconPackPackage)
        return memoryCache.get(key)
    }

    /**
     * Load icon with full logic: Memory -> Disk -> Custom URI -> Icon Pack -> Default System Icon.
     * Caches the result in Memory and Disk.
     */
    suspend fun loadIcon(
        context: Context,
        component: ComponentName,
        sizePx: Int,
        customIconUri: String? = null,
        iconPackPackage: String? = null
    ): Bitmap? {
        ensureInit()
        val key = keyFor(component, sizePx, customIconUri, iconPackPackage)

        // 1. Check Memory
        memoryCache.get(key)?.let { return it }

        // 2. Check Disk
        try {
            val disk = File(context.filesDir, DISK_CACHE_DIR)
            if (disk.exists()) {
                val f = File(disk, sanitizeFilename(key))
                if (f.exists()) {
                    val b = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(f.absolutePath) }
                    if (b != null) {
                        memoryCache.put(key, b)
                        return b
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from disk cache", e)
        }

        // 3. Generate/Load Icon (Heavy operation)
        val bitmap = withContext(Dispatchers.IO) {
            // A. Check Custom Icon URI
            if (customIconUri != null) {
                try {
                    val uri = Uri.parse(customIconUri)
                    val stream = context.contentResolver.openInputStream(uri)
                    val d = Drawable.createFromStream(stream, null)
                    if (d != null) return@withContext drawableToScaledBitmap(d, sizePx)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load custom icon uri: $customIconUri", e)
                }
            }

            // B. Check Icon Pack
            if (!iconPackPackage.isNullOrEmpty()) {
                try {
                    val d = IconPackHelper.getIconDrawable(context, iconPackPackage, component)
                    if (d != null) return@withContext drawableToScaledBitmap(d, sizePx)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load from icon pack: $iconPackPackage", e)
                }
            }

            // C. Default System Icon
            try {
                val pm = context.packageManager
                val d = try { pm.getActivityIcon(component) } catch (_: Exception) { null }
                    ?: try { pm.getApplicationIcon(component.packageName) } catch (_: Exception) { null }

                if (d != null) drawableToScaledBitmap(d, sizePx) else null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load default icon for $component", e)
                null
            }
        } ?: return null

        // 4. Store to Memory and Disk
        memoryCache.put(key, bitmap)
        try {
            withContext(Dispatchers.IO) {
                val disk = File(context.filesDir, DISK_CACHE_DIR)
                if (!disk.exists()) disk.mkdirs()
                val f = File(disk, sanitizeFilename(key))
                FileOutputStream(f).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 95, out) }
            }
        } catch (e: Exception) {
             Log.e(TAG, "Failed to write to disk cache", e)
        }

        return bitmap
    }

    /**
     * Pre-warm memory cache for a list of requests.
     * This loads icons into the LRU cache so subsequent getIconFromMemory calls succeed.
     */
    suspend fun prewarmMemoryCache(
        context: Context,
        requests: List<IconRequest>,
        sizePx: Int
    ) {
        ensureInit()
        // Process in parallel chunks or just sequential?
        // Sequential is safer for memory, but slow. Parallel is faster.
        // Let's do batches.

        val batchSize = 10
        val chunks = requests.chunked(batchSize)

        withContext(Dispatchers.IO) {
             chunks.forEach { batch ->
                 batch.forEach { req ->
                     // Fire and forget - loadIcon handles caching
                     try {
                         loadIcon(context, req.component, sizePx, req.customIconUri, req.iconPackPackage)
                     } catch (_: Exception) {}
                 }
                 // Small yield/delay to allow UI to breathe if needed, though we are on IO
             }
        }
    }

    private fun sanitizeFilename(key: String): String {
        return key.hashCode().toString() + ".png"
    }

    private fun drawableToScaledBitmap(drawable: Drawable, targetPx: Int): Bitmap? {
        val intrinsicW = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) drawable.intrinsicWidth else drawable.intrinsicWidth
        val intrinsicH = drawable.intrinsicHeight
        val bmp = if (drawable is BitmapDrawable && drawable.bitmap != null) drawable.bitmap else {
            val w = if (intrinsicW > 0) intrinsicW else targetPx
            val h = if (intrinsicH > 0) intrinsicH else targetPx
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }

        if (bmp.width == targetPx && bmp.height == targetPx) return bmp
        return Bitmap.createScaledBitmap(bmp, targetPx, targetPx, true)
    }

    fun clearCaches(context: Context) {
        ensureInit()
        memoryCache.evictAll()
        try { val dir = File(context.filesDir, DISK_CACHE_DIR); if (dir.exists()) dir.deleteRecursively() } catch (_: Exception) {}
    }
}
