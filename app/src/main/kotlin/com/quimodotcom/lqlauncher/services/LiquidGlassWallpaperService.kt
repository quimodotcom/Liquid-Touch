package com.quimodotcom.lqlauncher.services

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.util.DisplayMetrics
import android.view.SurfaceHolder
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.quimodotcom.lqlauncher.helpers.AppleMusicIntegration
import com.quimodotcom.lqlauncher.helpers.StackBlur
import com.quimodotcom.lqlauncher.helpers.DebugLogger
import com.quimodotcom.lqlauncher.compose.launcher.LiquidGlassSettings
import com.quimodotcom.lqlauncher.compose.launcher.LiquidGlassSettingsRepository
import com.quimodotcom.lqlauncher.compose.launcher.LauncherConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.max

class LiquidGlassWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return LiquidGlassEngine()
    }

    inner class LiquidGlassEngine : Engine() {

        private val engineScope = CoroutineScope(Dispatchers.Main + Job())
        private var wallpaperBitmap: Bitmap? = null
        private var subjectBitmap: Bitmap? = null
        private var mediaArtBitmap: Bitmap? = null

        // Scaled bitmaps for optimized drawing
        private var scaledWallpaper: Bitmap? = null
        private var scaledSubject: Bitmap? = null
        private var scaledMediaArt: Bitmap? = null
        private var blurredMediaArt: Bitmap? = null
        private var animatedMediaArt: Bitmap? = null
        private var animatedMediaFile: java.io.File? = null
        private var currentVideoPath: String? = null

        private var mediaTitle: String = ""
        private var mediaArtist: String = ""

        private var settings: LiquidGlassSettings = LiquidGlassSettings()

        // Video Renderer
        private var videoRenderer: VideoWallpaperRenderer? = null
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
        private val drawRunnable = Runnable { draw() }

        // Lock screen state
        private val keyguardManager by lazy { getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
        private var isVisible = false
        private var isLocked = false // Cached lock state
        private var isInAmbientMode = false // AOD state
        private var isPowerSaveMode = false // Power Save Mode state

        // Drawing objects
        private val BOTTOM_MARGIN_DP = 150f
        private val TEXT_GAP_DP = 16f
        private val CLOCK_TOP_MARGIN_DP = 80f
        private val DATE_GAP_DP = 12f
        private val DEBUG_LOG_X_OFFSET_DP = 20f
        private val DEBUG_LOG_Y_OFFSET_DP = 50f
        private val DEBUG_LOG_LINE_SPACING = 4f
        private val DEBUG_LOG_LINE_MULTIPLIER = 1.0f
        private val DEBUG_LOG_MARGIN_MULTIPLIER = 2
        private val DEBUG_LOG_BOTTOM_MARGIN_DP = 100f

        // Burn-in protection (pixel shifting)
        private var burnInOffsetX = 0f
        private var burnInOffsetY = 0f
        private val maxBurnInOffset = 10f // Max pixels to shift

        private var clockColor = Color.WHITE
        private val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        private val ambientTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-expanded", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(10f, 0f, 0f, Color.BLACK)
        }
        private val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            setShadowLayer(8f, 0f, 0f, Color.BLACK)
        }
        private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        private val debugPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.LEFT
            setShadowLayer(3f, 0f, 0f, Color.BLACK)
        }
        private val gradientPaint = Paint()
        private val fallbackPaint = Paint()

        // Liquid Glass styling paints
        private val glassCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val glassBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 100 // ~40% opacity, more visible border
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        // Cached drawing rects
        private val srcRect = Rect()
        private val dstRect = Rect()
        private val cardRect = RectF()

        // Broadcast Receiver for settings updates
        private val configReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.quimodotcom.lqlauncher.ACTION_CONFIG_CHANGED") {
                    reloadSettings()
                }
            }
        }

        // Screen state receiver to detect lock/unlock
        private val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        updateLockState()
                        draw()
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        updateLockState()
                        draw()
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        updateLockState()
                        draw()
                    }
                }
            }
        }

        private val powerSaveReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                    val isPowerSave = pm.isPowerSaveMode
                    handlePowerSaveMode(isPowerSave)
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            DebugLogger.log("WallpaperService", "onCreate")

            // Initialize GL Renderer
            videoRenderer = VideoWallpaperRenderer(applicationContext)
            // videoRenderer?.onSurfaceCreated(surfaceHolder!!) // Removed to fix crash: EGL surface creation must happen in onSurfaceCreated

            // Register receivers
            val filter = IntentFilter("com.quimodotcom.lqlauncher.ACTION_CONFIG_CHANGED")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(configReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(configReceiver, filter)
            }

            val screenFilter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenReceiver, screenFilter)

            // Register Power Save Receiver
            val powerSaveFilter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            registerReceiver(powerSaveReceiver, powerSaveFilter)

            // Initialize paints based on density
            val density = resources.displayMetrics.density
            titlePaint.textSize = 96f * (density / 2.5f) // Bigger font
            artistPaint.textSize = 48f * (density / 2.5f)
            clockPaint.textSize = 120f * (density / 2.5f)
            datePaint.textSize = 40f * (density / 2.5f)
            debugPaint.textSize = 12f * density
            glassBorderPaint.strokeWidth = 2f * density

            // Initial load
            reloadSettings()
            updateLockState()

            // Check initial Power Save Mode
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            handlePowerSaveMode(pm.isPowerSaveMode)

            // Enable Ambient Mode events if supported
            setOffsetNotificationsEnabled(false) // Often required to receive ambient events on some devices

            // Observe media state
            engineScope.launch {
                MediaStateRepository.mediaState.collectLatest { state ->
                    if (state != null) {
                        DebugLogger.log("WallpaperService", "Media: ${state.title} - ${state.artist}")
                        mediaTitle = state.title
                        mediaArtist = state.artist
                        mediaArtBitmap = state.art

                        // Reset previous animated state immediately
                        synchronized(this@LiquidGlassEngine) {
                            animatedMediaArt?.recycle()
                            animatedMediaArt = null
                            animatedMediaFile = null
                        }

                        // Force redraw to show static cover immediately
                        withContext(Dispatchers.Main) {
                            // Immediately reset video player to free resources for new track
                            // This ensures the old video stops BEFORE we start downloading/processing new art
                            DebugLogger.log("WallpaperService", "Resetting video for new track")
                            videoRenderer?.reset()
                            currentVideoPath = null
                            draw()
                        }

                        // Handle Animated Art (load high-res frame from video URL)
                        if (state.animatedArtUrl != null) {
                            DebugLogger.log("WallpaperService", "Loading Animated Art: ${state.animatedArtUrl}")
                            try {
                                val dm = resources.displayMetrics
                                // Ensure the file is cached, requesting resolution match
                                val file = withContext(Dispatchers.IO) {
                                    AppleMusicIntegration.getAnimatedArtworkFile(
                                        applicationContext,
                                        state.animatedArtUrl,
                                        state.title,
                                        state.artist,
                                            state.album,
                                        dm.widthPixels,
                                        dm.heightPixels
                                    )
                                }

                                // Check if this job is still active before applying changes
                                if (file != null && isActive) {
                                    // Still extract a bitmap for the "Blur" effect and fallback
                                    val bitmap = withContext(Dispatchers.IO) {
                                        val retriever = android.media.MediaMetadataRetriever()
                                        try {
                                            retriever.setDataSource(file.absolutePath)
                                            retriever.getFrameAtTime()
                                        } catch (e: Exception) {
                                            null
                                        } finally {
                                            try {
                                                retriever.release()
                                            } catch (e: Exception) {
                                                // Ignore release errors
                                            }
                                        }
                                    }

                                    if (bitmap != null) {
                                        synchronized(this@LiquidGlassEngine) {
                                            animatedMediaArt?.recycle()
                                            animatedMediaArt = bitmap
                                            animatedMediaFile = file
                                        }
                                        // Update primary art to use the high-res one
                                        mediaArtBitmap = bitmap
                                        updateClockColor(mediaArtBitmap)

                                        // Trigger video player update
                                        withContext(Dispatchers.Main) {
                                            if (isVisible && isLocked && settings.enableLockScreenMediaArt && !isPowerSaveMode) {
                                                if (file.absolutePath != currentVideoPath) {
                                                    DebugLogger.log("WallpaperService", "Swapping video: ${file.name}, ${file.length()}b")
                                                    videoRenderer?.setVideoSource(file)
                                                    currentVideoPath = file.absolutePath
                                                } else {
                                                    DebugLogger.log("WallpaperService", "Continuing playback: ${file.name}")
                                                }
                                            } else {
                                                DebugLogger.log("WallpaperService", "Not showing video: v=$isVisible, l=$isLocked, s=${settings.enableLockScreenMediaArt}")
                                            }
                                            draw()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                Log.e("LiquidGlassWallpaper", "Error loading animated art", e)
                            }
                        }

                        // Generate blur in background
                        launch(Dispatchers.Default) {
                            val art = animatedMediaArt ?: state.art
                            if (art != null && !art.isRecycled) {
                                try {
                                    // Create a smaller version for blurring (performance)
                                    val smallW = max(1, art.width / 4)
                                    val smallH = max(1, art.height / 4)
                                    val small = Bitmap.createScaledBitmap(art, smallW, smallH, true)
                                    val blurred = StackBlur.blur(small, 20) // Reduced radius from 60 to 20

                                    synchronized(this@LiquidGlassEngine) {
                                        blurredMediaArt?.recycle()
                                        blurredMediaArt = blurred
                                    }
                                    if (small != art && small != blurred) {
                                        small.recycle()
                                    }
                                } catch (e: Exception) {
                                    Log.e("LiquidGlassWallpaper", "Error generating blur", e)
                                }
                            } else {
                                synchronized(this@LiquidGlassEngine) {
                                    blurredMediaArt?.recycle()
                                    blurredMediaArt = null
                                }
                            }
                            draw()
                        }

                        draw()
                    } else {
                        mediaArtBitmap = null
                        updateClockColor(wallpaperBitmap) // Fallback to wallpaper
                        synchronized(this@LiquidGlassEngine) {
                            blurredMediaArt?.recycle()
                            blurredMediaArt = null
                            animatedMediaArt?.recycle()
                            animatedMediaArt = null
                        }
                        withContext(Dispatchers.Main) {
                            videoRenderer?.reset()
                            draw()
                        }
                    }
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            DebugLogger.log("WallpaperService", "onDestroy")
            try {
                unregisterReceiver(configReceiver)
                unregisterReceiver(screenReceiver)
                unregisterReceiver(powerSaveReceiver)
            } catch (e: IllegalArgumentException) {
                // Ignore if not registered
            }
            engineScope.cancel()
            recycleScaledBitmaps()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder?) {
            super.onSurfaceCreated(holder)
            if (holder != null) {
                videoRenderer?.onSurfaceCreated(holder)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            videoRenderer?.onSurfaceDestroyed()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisible = visible
            updateLockState()
            DebugLogger.log("WallpaperService", "Visibility: $visible")

            if (visible) {
                reloadSettings()
                // Resume video if file exists
                if (isLocked && animatedMediaFile != null && settings.enableLockScreenMediaArt && !isInAmbientMode && !isPowerSaveMode) {
                    videoRenderer?.setVideoSource(animatedMediaFile!!)
                }
                draw()
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        // Explicit onAmbientModeChanged override removed due to missing method in SDK 36 Environment

        // Fallback: Use onCommand to detect AOD if possible
        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: android.os.Bundle?, resultRequested: Boolean): android.os.Bundle? {
            if ("android.wallpaper.ambient_mode" == action) {
                val inAmbientMode = extras?.getBoolean("ambient_mode", false) ?: false
                if (isInAmbientMode != inAmbientMode) {
                    isInAmbientMode = inAmbientMode
                    handleAmbientMode(inAmbientMode)
                }
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        private fun handlePowerSaveMode(enabled: Boolean) {
            isPowerSaveMode = enabled
            DebugLogger.log("WallpaperService", "PowerSave: $enabled")
            if (enabled) {
                // Stop video to save power
                videoRenderer?.stop()
            } else {
                // Resume video if needed
                if (isVisible && isLocked && animatedMediaFile != null && settings.enableLockScreenMediaArt && !isInAmbientMode) {
                    videoRenderer?.setVideoSource(animatedMediaFile!!)
                }
            }
            draw()
        }

        private fun handleAmbientMode(inAmbientMode: Boolean) {
            if (inAmbientMode) {
                // Stop video to save power
                videoRenderer?.stop()

                // Switch to thinner font for AOD to save pixels/power
                clockPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                clockPaint.alpha = 200 // Dim slightly
                datePaint.alpha = 180
            } else {
                // Restore font
                clockPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                clockPaint.alpha = 255
                datePaint.alpha = 255

                // Resume video if needed
                if (isVisible && isLocked && animatedMediaFile != null && settings.enableLockScreenMediaArt && !isPowerSaveMode) {
                    videoRenderer?.setVideoSource(animatedMediaFile!!)
                }
                // Reset burn-in offset
                burnInOffsetX = 0f
                burnInOffsetY = 0f
            }
            draw()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            videoRenderer?.onSurfaceChanged(width, height)
            updateGradient(width.toFloat(), height.toFloat())
            updateFallbackGradient(width.toFloat(), height.toFloat())

            // Re-scale bitmaps for new dimensions
            engineScope.launch {
                updateScaledBitmaps(width, height)
                draw()
            }
        }

        private fun updateLockState() {
            try {
                isLocked = keyguardManager.isKeyguardLocked
            } catch (e: Exception) {
                isLocked = false
            }
        }

        private fun updateGradient(width: Float, height: Float) {
             val gradientHeight = height * 0.4f
             gradientPaint.shader = android.graphics.LinearGradient(
                    0f, height - gradientHeight,
                    0f, height,
                    intArrayOf(Color.TRANSPARENT, Color.BLACK),
                    null,
                    android.graphics.Shader.TileMode.CLAMP
                )
        }

        private fun updateFallbackGradient(width: Float, height: Float) {
             fallbackPaint.shader = android.graphics.LinearGradient(
                     0f, 0f, width, height,
                     intArrayOf(Color.parseColor("#0F0C29"), Color.parseColor("#302B63"), Color.parseColor("#24243E")),
                     null,
                     android.graphics.Shader.TileMode.CLAMP
                 )
        }

        private fun reloadSettings() {
            engineScope.launch {
                settings = LiquidGlassSettingsRepository.loadSettings(this@LiquidGlassWallpaperService)
                updateLockState()
                loadWallpapers()
                draw()
            }
        }

        private suspend fun loadWallpapers() = withContext(Dispatchers.IO) {
            try {
                val dm = resources.displayMetrics
                val targetW = dm.widthPixels
                val targetH = dm.heightPixels

                // Use LauncherConfig for wallpaper URI
                val config = LauncherConfigRepository.loadConfig(this@LiquidGlassWallpaperService)

                val uri = config?.wallpaperUri
                val subjectUri = config?.wallpaperSubjectUri

                if (uri != null && !config.useSystemWallpaper) {
                    val parsedUri = Uri.parse(uri)
                    wallpaperBitmap = loadBitmap(parsedUri, targetW, targetH)
                } else {
                    wallpaperBitmap = null
                }

                // Initial clock color update if no media playing
                if (mediaArtBitmap == null) {
                    updateClockColor(wallpaperBitmap)
                }

                if (subjectUri != null) {
                    subjectBitmap = loadBitmap(Uri.parse(subjectUri), targetW, targetH)
                } else {
                    subjectBitmap = null
                }

                // Update scaled versions immediately after loading
                val holder = surfaceHolder
                if (holder != null && holder.surfaceFrame.width() > 0) {
                    updateScaledBitmaps(holder.surfaceFrame.width(), holder.surfaceFrame.height())
                }

            } catch (e: Exception) {
                Log.e("LiquidGlassWallpaper", "Error loading wallpapers", e)
            }
        }

        private suspend fun updateScaledBitmaps(width: Int, height: Int) = withContext(Dispatchers.Default) {
            if (width <= 0 || height <= 0) return@withContext

            // Scale Wallpaper
            wallpaperBitmap?.let { src ->
                if (!src.isRecycled) {
                    val scaled = createCenterCropBitmap(src, width, height)
                    synchronized(this) {
                        scaledWallpaper?.recycle()
                        scaledWallpaper = scaled
                    }
                }
            }

            // Scale Subject
            subjectBitmap?.let { src ->
                if (!src.isRecycled) {
                    val scaled = createCenterCropBitmap(src, width, height)
                    synchronized(this) {
                        scaledSubject?.recycle()
                        scaledSubject = scaled
                    }
                }
            }
        }

        private fun createCenterCropBitmap(src: Bitmap, reqW: Int, reqH: Int): Bitmap {
            val scale = max(reqW.toFloat() / src.width, reqH.toFloat() / src.height)
            val w = (src.width * scale).toInt()
            val h = (src.height * scale).toInt()

            val scaled = Bitmap.createScaledBitmap(src, w, h, true)

            // If strictly matching size is required, we can crop.
            // But for wallpaper, overflow is fine or we create exact match.
            // Let's create exact match to optimize drawing.
            val final = Bitmap.createBitmap(reqW, reqH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(final)
            val x = (reqW - w) / 2f
            val y = (reqH - h) / 2f
            canvas.drawBitmap(scaled, x, y, bitmapPaint)
            if (scaled != src) scaled.recycle() // Recycle intermediate if not source
            return final
        }

        private fun recycleScaledBitmaps() {
            scaledWallpaper?.recycle()
            scaledSubject?.recycle()
            scaledMediaArt?.recycle()
            blurredMediaArt?.recycle()
            animatedMediaArt?.recycle()
            scaledWallpaper = null
            scaledSubject = null
            scaledMediaArt = null
            blurredMediaArt = null
            animatedMediaArt = null
        }

        private fun loadBitmap(uri: Uri, reqW: Int, reqH: Int): Bitmap? {
            return try {
                // Decode bounds
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }

                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    return null
                }

                options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, reqW, reqH)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.ARGB_8888

                contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun calculateInSampleSize(origW: Int, origH: Int, reqW: Int, reqH: Int): Int {
            if (origW <= 0 || origH <= 0 || reqW <= 0 || reqH <= 0) return 1

            var inSampleSize = 1
            if (origH > reqH || origW > reqW) {
                val halfHeight = origH / 2
                val halfWidth = origW / 2
                while ((halfHeight / inSampleSize) >= reqH && (halfWidth / inSampleSize) >= reqW) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }

        private var cachedUiBitmap: Bitmap? = null
        private var lastMediaState: String = ""

        private fun draw() {
            // Ambient Mode Handling (Black screen + Simple Clock)
            if (isInAmbientMode) {
                // Use Canvas drawing for Ambient Mode to avoid GL overhead if possible,
                // or just clear GL to black and draw UI.
                // Let's use the existing GL renderer but set background to black (null)
                videoRenderer?.setBackground(null)

                val calendar = Calendar.getInstance()
                // Update burn-in protection offsets randomly every minute
                if (calendar.get(Calendar.SECOND) == 0) {
                     burnInOffsetX = (Math.random() * maxBurnInOffset * 2 - maxBurnInOffset).toFloat()
                     burnInOffsetY = (Math.random() * maxBurnInOffset * 2 - maxBurnInOffset).toFloat()
                }

                val currentTime = ambientTimeFormat.format(calendar.time) // No seconds
                val currentDate = dateFormat.format(calendar.time)

                // Force UI update for ambient mode (pixel shift or time change)
                val currentState = "AMBIENT|$currentTime|$currentDate|$burnInOffsetX|$burnInOffsetY"
                if (currentState != lastMediaState) {
                    cachedUiBitmap?.recycle()
                    cachedUiBitmap = createUiBitmap(currentTime, currentDate) // Uses burnInOffset internally
                    lastMediaState = currentState
                    if (cachedUiBitmap != null) {
                        videoRenderer?.updateUI(cachedUiBitmap!!)
                    }
                }

                videoRenderer?.draw()

                // Request next frame (slower update rate for AOD, e.g., every minute)
                if (isVisible) {
                    handler.removeCallbacks(drawRunnable)
                    // Sync to top of minute
                    val delay = (60 - calendar.get(Calendar.SECOND)) * 1000L
                    handler.postDelayed(drawRunnable, delay.coerceAtLeast(1000L))
                }
                return
            }

            // Unify everything to use GL Renderer if possible
            if (isLocked) {
                // Pass static art to renderer (if not already set)
                // If media art is null, fall back to the scaled wallpaper bitmap
                videoRenderer?.setBackground(mediaArtBitmap ?: scaledWallpaper)

                // Render UI to Bitmap, then pass to GL
                // Check if UI needs update
                val calendar = Calendar.getInstance()
                val currentTime = timeFormat.format(calendar.time)
                val currentDate = dateFormat.format(calendar.time)

                // Include time in state check to redraw every second
                val currentState = "$mediaTitle|$mediaArtist|${surfaceHolder?.surfaceFrame?.width()}|$currentTime|$currentDate"

                if (cachedUiBitmap == null || currentState != lastMediaState) {
                    cachedUiBitmap?.recycle()
                    cachedUiBitmap = createUiBitmap(currentTime, currentDate)
                    lastMediaState = currentState
                    if (cachedUiBitmap != null) {
                        videoRenderer?.updateUI(cachedUiBitmap!!)
                    }
                }

                videoRenderer?.draw()

                // Request next frame
                if (isVisible) {
                    handler.removeCallbacks(drawRunnable)
                    if (isPowerSaveMode) {
                        handler.postDelayed(drawRunnable, 1000)
                    } else {
                        handler.postDelayed(drawRunnable, 33) // ~30fps
                    }
                }
            } else {
                handler.removeCallbacks(drawRunnable)
                // Standard Canvas Drawing for Home Screen (if not using GL for everything)
                // For now, keep Home Screen on Canvas if it doesn't need video
                val holder = surfaceHolder
                if (holder == null || holder.surface == null || !holder.surface.isValid) return

                var canvas: Canvas? = null
                try {
                    canvas = holder.lockCanvas()
                    if (canvas != null) {
                        drawFrame(canvas)
                    }
                } catch (e: Exception) {
                    Log.e("LiquidGlassWallpaper", "Error locking canvas", e)
                } finally {
                    if (canvas != null) {
                        try {
                            holder.unlockCanvasAndPost(canvas)
                        } catch (e: Exception) {
                            Log.e("LiquidGlassWallpaper", "Error unlocking canvas", e)
                        }
                    }
                }
            }
        }

        private fun createUiBitmap(time: String, date: String): Bitmap? {
            val w = surfaceHolder?.surfaceFrame?.width() ?: 0
            val h = surfaceHolder?.surfaceFrame?.height() ?: 0
            if (w <= 0 || h <= 0) return null

            // Re-use or create bitmap (TODO: Optimize reuse to avoid GC)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cvs = Canvas(bmp)

            // Draw Gradient & Text Overlay (No background image, transparency needed)
            // Gradient
            val gradientHeight = h * 0.4f
            cvs.drawRect(0f, h - gradientHeight, w.toFloat(), h.toFloat(), gradientPaint)

            drawLockScreenUI(cvs, w.toFloat(), h.toFloat(), time, date)

            return bmp
        }

        private fun drawFrame(canvas: Canvas) {
            try {
                val width = canvas.width.toFloat()
                val height = canvas.height.toFloat()

                if (isLocked && settings.enableLockScreenMediaArt && mediaArtBitmap != null && !mediaArtBitmap!!.isRecycled) {
                    drawLockScreen(canvas, width, height)
                } else if (isLocked) {
                    drawStaticWallpaper(canvas, width, height)
                } else {
                    drawWallpaper(canvas, width, height)
                }
            } catch (e: Exception) {
                Log.e("LiquidGlassWallpaper", "Error in drawFrame", e)
                try {
                    canvas.drawColor(Color.BLACK)
                } catch (e2: Exception) { }
            }
        }

        private fun drawLockScreen(canvas: Canvas, width: Float, height: Float) {
            // This is used for fallback (static image) rendering
            // Prioritize animated/high-res art
            val art = animatedMediaArt ?: mediaArtBitmap ?: return
            if (art.isRecycled) return

            // Draw Media Art (Center Crop)
            val scale = max(width / art.width, height / art.height)
            val w = art.width * scale
            val h = art.height * scale
            val x = (width - w) / 2
            val y = (height - h) / 2

            srcRect.set(0, 0, art.width, art.height)
            dstRect.set(x.toInt(), y.toInt(), (x + w).toInt(), (y + h).toInt())
            canvas.drawBitmap(art, srcRect, dstRect, bitmapPaint)

            // Draw gradient
            val gradientHeight = height * 0.4f
            canvas.drawRect(0f, height - gradientHeight, width, height, gradientPaint)

            val calendar = Calendar.getInstance()
            val time = timeFormat.format(calendar.time)
            val date = dateFormat.format(calendar.time)
            drawLockScreenUI(canvas, width, height, time, date)
        }

        private fun drawLockScreenUI(canvas: Canvas, width: Float, height: Float, time: String, date: String) {
            val centerX = (width / 2f) + burnInOffsetX
            val density = resources.displayMetrics.density

            // --- Draw Clock ---
            val clockY = (CLOCK_TOP_MARGIN_DP * density + clockPaint.textSize) + burnInOffsetY

            // In Ambient Mode, always use White/Gray (Color.BLACK is invisible on OLED black background)
            val textColor = if (isInAmbientMode) Color.LTGRAY else clockColor

            clockPaint.color = textColor
            datePaint.color = textColor

            canvas.drawText(time, centerX, clockY, clockPaint)

            val dateY = clockY + datePaint.textSize + DATE_GAP_DP * density
            canvas.drawText(date, centerX, dateY, datePaint)

            // Hide media info in Ambient Mode to reduce clutter/burn-in
            if (isInAmbientMode) return

            // --- Draw Media Info ---
            if (mediaTitle.isBlank() && mediaArtist.isBlank()) return

            // Position text near the bottom
            val bottomMargin = BOTTOM_MARGIN_DP * density
            var currentY = height - bottomMargin
            val maxWidth = (width - (40f * density)).toInt() // 20dp padding each side

            if (maxWidth > 0) {
                // Let's re-do properly stacking upwards
                var yPos = height - bottomMargin

                // 1. Artist
                if (mediaArtist.isNotBlank()) {
                    val artistTextPaint = android.text.TextPaint(artistPaint).apply {
                        textAlign = Paint.Align.LEFT
                    }
                    val artistLayout = android.text.StaticLayout.Builder.obtain(
                        mediaArtist, 0, mediaArtist.length, artistTextPaint, maxWidth
                    )
                        .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(0f, 1.0f)
                        .setIncludePad(false)
                        .build()

                    yPos -= artistLayout.height
                    canvas.save()
                    canvas.translate(centerX - (maxWidth / 2), yPos)
                    artistLayout.draw(canvas)
                    canvas.restore()
                }

                // 2. Gap
                yPos -= (TEXT_GAP_DP * density)

                // 3. Title
                if (mediaTitle.isNotBlank()) {
                    val titleTextPaint = android.text.TextPaint(titlePaint).apply {
                        textAlign = Paint.Align.LEFT
                    }
                    val titleLayout = android.text.StaticLayout.Builder.obtain(
                        mediaTitle, 0, mediaTitle.length, titleTextPaint, maxWidth
                    )
                        .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(0f, 1.0f)
                        .setIncludePad(false)
                        .build()

                    yPos -= titleLayout.height
                    canvas.save()
                    canvas.translate(centerX - (maxWidth / 2), yPos)
                    titleLayout.draw(canvas)
                    canvas.restore()
                }
            }

            // --- Draw Debug Logs ---
            if (settings.showDebugLogs) {
                val logX = DEBUG_LOG_X_OFFSET_DP * density
                val logs = DebugLogger.getLogs()
                val maxWidth = (width - (DEBUG_LOG_X_OFFSET_DP * DEBUG_LOG_MARGIN_MULTIPLIER * density)).toInt()

                if (maxWidth > 0) {
                    val textPaint = android.text.TextPaint(debugPaint)
                    val layouts = ArrayList<android.text.StaticLayout>()
                    var totalHeight = 0f

                    // 1. Calculate layouts and total height
                    for (log in logs) {
                        val builder = android.text.StaticLayout.Builder.obtain(
                            log, 0, log.length, textPaint, maxWidth
                        )
                            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                            .setLineSpacing(DEBUG_LOG_LINE_SPACING, DEBUG_LOG_LINE_MULTIPLIER)
                            .setIncludePad(false)
                        val layout = builder.build()
                        layouts.add(layout)
                        totalHeight += layout.height + DEBUG_LOG_LINE_SPACING
                    }

                    // 2. Determine start Y position (scrolling logic)
                    val startY = DEBUG_LOG_Y_OFFSET_DP * density
                    val availableHeight = height - startY - (DEBUG_LOG_BOTTOM_MARGIN_DP * density)

                    var currentY = if (totalHeight > availableHeight) {
                        // Align bottom of logs to bottom of available space
                        // currentY should start at (startY + availableHeight) - totalHeight
                        // This pushes top logs off-screen
                        startY + availableHeight - totalHeight
                    } else {
                        startY
                    }

                    // 3. Draw visible logs
                    for (layout in layouts) {
                        // Optimization: Only draw if potentially visible on screen (allowing partial visibility)
                        // Ideally checking against the "window" of (startY) to (startY + availableHeight)
                        if (currentY + layout.height > startY && currentY < height) {
                            canvas.save()
                            canvas.translate(logX, currentY)
                            layout.draw(canvas)
                            canvas.restore()
                        }
                        currentY += layout.height + DEBUG_LOG_LINE_SPACING
                    }
                }
            }
        }

        private fun updateClockColor(bitmap: Bitmap?) {
            if (bitmap == null || bitmap.isRecycled) {
                clockColor = Color.WHITE // Default
                return
            }

            try {
                // Sample top 20% of the image to determine brightness
                val sampleHeight = (bitmap.height * 0.2f).toInt().coerceAtLeast(1)

                // Scale down for performance (e.g., 50px width)
                val scaledW = 50
                val scaledH = (sampleHeight.toFloat() / bitmap.width * scaledW).toInt().coerceAtLeast(1)

                val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)

                var rSum = 0L
                var gSum = 0L
                var bSum = 0L
                val pixels = IntArray(scaledW * scaledH)
                scaled.getPixels(pixels, 0, scaledW, 0, 0, scaledW, scaledH)

                for (pixel in pixels) {
                    rSum += Color.red(pixel)
                    gSum += Color.green(pixel)
                    bSum += Color.blue(pixel)
                }

                val pixelCount = pixels.size
                val avgR = rSum / pixelCount
                val avgG = gSum / pixelCount
                val avgB = bSum / pixelCount

                // Luminance formula
                val luminance = (0.299 * avgR + 0.587 * avgG + 0.114 * avgB) / 255.0

                // If bright (> 0.5), use Black text. Else White.
                clockColor = if (luminance > 0.5) Color.BLACK else Color.WHITE

                if (scaled != bitmap) scaled.recycle()
            } catch (e: Exception) {
                Log.e("LiquidGlassWallpaper", "Error calculating clock color", e)
                clockColor = Color.WHITE
            }
        }

        // Static wallpaper for lock screen
        private fun drawStaticWallpaper(canvas: Canvas, width: Float, height: Float) {
            canvas.drawColor(Color.BLACK)

            val bmp = scaledWallpaper
            if (bmp != null && !bmp.isRecycled) {
                canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
            } else {
                // Fallback to scaling raw if scaled not ready
                val raw = wallpaperBitmap
                if (raw != null && !raw.isRecycled) {
                    val scale = max(width / raw.width, height / raw.height)
                    val w = raw.width * scale
                    val h = raw.height * scale
                    val x = (width - w) / 2
                    val y = (height - h) / 2
                    dstRect.set(x.toInt(), y.toInt(), (x + w).toInt(), (y + h).toInt())
                    canvas.drawBitmap(raw, null, dstRect, bitmapPaint)
                } else {
                    canvas.drawRect(0f, 0f, width, height, fallbackPaint)
                }
            }
        }

        private fun drawWallpaper(canvas: Canvas, width: Float, height: Float) {
            canvas.drawColor(Color.BLACK)

            // Draw Wallpaper (Scaled)
            val bmp = scaledWallpaper
            if (bmp != null && !bmp.isRecycled) {
                canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
            } else {
                // Fallback
                val raw = wallpaperBitmap
                if (raw != null && !raw.isRecycled) {
                    val scale = max(width / raw.width, height / raw.height)
                    val w = raw.width * scale
                    val h = raw.height * scale
                    val x = (width - w) / 2
                    val y = (height - h) / 2
                    dstRect.set(x.toInt(), y.toInt(), (x + w).toInt(), (y + h).toInt())
                    canvas.drawBitmap(raw, null, dstRect, bitmapPaint)
                } else {
                    canvas.drawRect(0f, 0f, width, height, fallbackPaint)
                }
            }

            // Draw Subject (Scaled)
            val sub = scaledSubject
            if (sub != null && !sub.isRecycled) {
                canvas.drawBitmap(sub, 0f, 0f, bitmapPaint)
            } else {
                val rawSub = subjectBitmap
                if (rawSub != null && !rawSub.isRecycled) {
                    val scale = max(width / rawSub.width, height / rawSub.height)
                    val w = rawSub.width * scale
                    val h = rawSub.height * scale
                    val x = (width - w) / 2
                    val y = (height - h) / 2
                    dstRect.set(x.toInt(), y.toInt(), (x + w).toInt(), (y + h).toInt())
                    canvas.drawBitmap(rawSub, null, dstRect, bitmapPaint)
                }
            }
        }
    }
}
