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
import kotlinx.coroutines.delay
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
        private var gifBitmap: Bitmap? = null
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
        private val frameCallback = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (isVisible && !isInAmbientMode && !isPowerSaveMode) {
                    draw()
                    android.view.Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }

        // Lock screen state
        private val keyguardManager by lazy { getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
        private var isVisible = false
        private var isLocked = false // Cached lock state
        private var isInAmbientMode = false // AOD state
        private var isPowerSaveMode = false // Power Save Mode state

        private var gifJob: Job? = null
        private var gifFrameBitmap: Bitmap? = null
        private var imageLoader: ImageLoader? = null

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
                        // When screen goes off, it's usually locked
                        reloadSettings()
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        // Fully unlocked
                        reloadSettings()
                    }
                    Intent.ACTION_CONFIGURATION_CHANGED -> {
                        reloadSettings()
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
                addAction(Intent.ACTION_CONFIGURATION_CHANGED)
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

            // Initialize Coil for GIFs
            imageLoader = ImageLoader.Builder(applicationContext)
                .components {
                    if (Build.VERSION.SDK_INT >= 28) {
                        add(coil.decode.ImageDecoderDecoder.Factory())
                    } else {
                        add(coil.decode.GifDecoder.Factory())
                    }
                }
                .build()

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
                        if (settings.enableLockScreenMediaArt && state.animatedArtUrl != null) {
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
            gifJob?.cancel()
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

                // Launch interactive controls if enabled and locked
                // Check if media is actually playing/active to avoid blank overlay
                val mediaActive = MediaStateRepository.mediaState.value != null
                if (isLocked && settings.enableLockScreenControls && !isInAmbientMode && mediaActive) {
                    try {
                        val intent = Intent(this@LiquidGlassWallpaperService, com.quimodotcom.lqlauncher.activities.LockScreenOverlayActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("LiquidGlassWallpaper", "Failed to launch overlay", e)
                    }
                }

                // Resume video/gif if file exists
                if (isLocked && animatedMediaFile != null && settings.enableLockScreenMediaArt && !isInAmbientMode && !isPowerSaveMode) {
                    videoRenderer?.setVideoSource(animatedMediaFile!!)
                }
                if (!isInAmbientMode && !isPowerSaveMode) {
                    startGifJobIfNeeded()
                    android.view.Choreographer.getInstance().removeFrameCallback(frameCallback)
                    android.view.Choreographer.getInstance().postFrameCallback(frameCallback)
                }
                draw()
            } else {
                android.view.Choreographer.getInstance().removeFrameCallback(frameCallback)
                gifJob?.cancel()
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
                // Stop video/gif to save power
                videoRenderer?.stop()
                gifJob?.cancel()
                android.view.Choreographer.getInstance().removeFrameCallback(frameCallback)
            } else {
                // Resume video/gif if needed
                if (isVisible && isLocked && animatedMediaFile != null && settings.enableLockScreenMediaArt && !isInAmbientMode) {
                    videoRenderer?.setVideoSource(animatedMediaFile!!)
                }
                if (isVisible && !isInAmbientMode) {
                    startGifJobIfNeeded()
                    android.view.Choreographer.getInstance().removeFrameCallback(frameCallback)
                    android.view.Choreographer.getInstance().postFrameCallback(frameCallback)
                }
            }
            draw()
        }

        private fun handleAmbientMode(inAmbientMode: Boolean) {
            if (inAmbientMode) {
                // Stop video/gif to save power
                videoRenderer?.stop()
                gifJob?.cancel()
                android.view.Choreographer.getInstance().removeFrameCallback(frameCallback)

                // Switch to thinner font for AOD to save pixels/power
                clockPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                clockPaint.alpha = 200 // Dim slightly
                datePaint.alpha = 180
            } else {
                // Restore font
                clockPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                clockPaint.alpha = 255
                datePaint.alpha = 255

                // Resume video/gif if needed
                if (isVisible && isLocked && animatedMediaFile != null && settings.enableLockScreenMediaArt && !isPowerSaveMode) {
                    videoRenderer?.setVideoSource(animatedMediaFile!!)
                }
                if (isVisible && !isPowerSaveMode) {
                    startGifJobIfNeeded()
                    android.view.Choreographer.getInstance().removeFrameCallback(frameCallback)
                    android.view.Choreographer.getInstance().postFrameCallback(frameCallback)
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

        private var currentGifUri: String? = null
        private var currentVideoWallpaperPath: String? = null

        private fun getMimeType(uri: String): String? {
            return try {
                contentResolver.getType(Uri.parse(uri)) ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri)
                )
            } catch (e: Exception) {
                null
            }
        }

        private suspend fun loadWallpapers() = withContext(Dispatchers.IO) {
            try {
                val dm = resources.displayMetrics
                val targetW = dm.widthPixels
                val targetH = dm.heightPixels

                // Use LauncherConfig for wallpaper URI
                val config = LauncherConfigRepository.loadConfig(this@LiquidGlassWallpaperService)

                // Determine if it's currently "night" based on custom settings or system theme
                val calendar = Calendar.getInstance()
                val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

                val nightStartMinutes = settings.nightStartHour * 60 + settings.nightStartMinute
                val dayStartMinutes = settings.dayStartHour * 60 + settings.dayStartMinute

                val isCustomNight = if (nightStartMinutes > dayStartMinutes) {
                    currentMinutes >= nightStartMinutes || currentMinutes < dayStartMinutes
                } else {
                    currentMinutes >= nightStartMinutes && currentMinutes < dayStartMinutes
                }

                val isSystemNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                val isDark = isCustomNight || isSystemNight

                val mainUri = if (isLocked) {
                    if (isDark) (config?.wallpaperNightUri ?: config?.wallpaperUri) else config?.wallpaperUri
                } else {
                    if (settings.secretWallpaperVisible) {
                        config?.wallpaperSecretUri ?: (if (isDark) (config?.wallpaperNightUri ?: config?.wallpaperUri) else config?.wallpaperUri)
                    } else {
                        if (isDark) (config?.wallpaperNightUri ?: config?.wallpaperUri) else config?.wallpaperUri
                    }
                }

                // Prioritize specialized URIs (GIF/Video) if set
                val gifUri = config?.wallpaperGifUri
                val videoUri = config?.wallpaperVideoUri

                // Determine effective wallpaper mode
                // If isLocked, media art takes priority.
                // Otherwise, check if user has a global GIF or Video.

                var resolvedGif: String? = null
                var resolvedVideo: String? = null
                var resolvedImage: String? = mainUri

                if (!isLocked) {
                    if (videoUri != null) resolvedVideo = videoUri
                    else if (gifUri != null) resolvedGif = gifUri
                }

                // Process resolveImage for Bitmaps
                if (resolvedImage != null && config != null && (!config.useSystemWallpaper || (resolvedImage == config.wallpaperSecretUri))) {
                    val type = getMimeType(resolvedImage)
                    if (type?.startsWith("video/") == true) {
                        resolvedVideo = resolvedImage
                        wallpaperBitmap = null
                    } else if (type?.contains("gif") == true) {
                        resolvedGif = resolvedImage
                        wallpaperBitmap = null
                    } else {
                        wallpaperBitmap = loadBitmap(Uri.parse(resolvedImage), targetW, targetH)
                    }
                } else if (config == null || config.useSystemWallpaper) {
                    wallpaperBitmap = loadSystemWallpaper(this@LiquidGlassWallpaperService, targetW, targetH)
                } else {
                    wallpaperBitmap = null
                }

                // Initial clock color update if no media playing
                if (mediaArtBitmap == null) {
                    updateClockColor(wallpaperBitmap)
                }

                val subjectUri = config?.wallpaperSubjectUri
                if (subjectUri != null) {
                    subjectBitmap = loadBitmap(Uri.parse(subjectUri), targetW, targetH)
                } else {
                    subjectBitmap = null
                }

                // Handle Video background (not media art)
                if (resolvedVideo != currentVideoWallpaperPath) {
                    currentVideoWallpaperPath = resolvedVideo
                    withContext(Dispatchers.Main) {
                        updateVideoBackground()
                    }
                }

                // Handle GIF background
                if (resolvedGif != currentGifUri) {
                    currentGifUri = resolvedGif
                    withContext(Dispatchers.Main) {
                        startGifJobIfNeeded()
                    }
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
            DebugLogger.log("WallpaperService", "Scaling bitmaps to ${width}x${height}")

            // Scale Wallpaper
            val srcWp = wallpaperBitmap
            if (srcWp != null && !srcWp.isRecycled) {
                val scaled = createCenterCropBitmap(srcWp, width, height)
                synchronized(this@LiquidGlassEngine) {
                    scaledWallpaper?.recycle()
                    scaledWallpaper = scaled
                }
                DebugLogger.log("WallpaperService", "Wallpaper scaled successfully")
            } else {
                synchronized(this@LiquidGlassEngine) {
                    scaledWallpaper?.recycle()
                    scaledWallpaper = null
                }
                DebugLogger.log("WallpaperService", "Wallpaper bitmap null or recycled, scaled version cleared")
            }

            // Scale Subject
            val srcSub = subjectBitmap
            if (srcSub != null && !srcSub.isRecycled) {
                val scaled = createCenterCropBitmap(srcSub, width, height)
                synchronized(this@LiquidGlassEngine) {
                    scaledSubject?.recycle()
                    scaledSubject = scaled
                }
            } else {
                synchronized(this@LiquidGlassEngine) {
                    scaledSubject?.recycle()
                    scaledSubject = null
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

        private fun loadSystemWallpaper(context: Context, reqW: Int, reqH: Int): Bitmap? {
            return try {
                val wm = android.app.WallpaperManager.getInstance(context)
                // Use peekDrawable first as it's often more reliable for background services
                val drawable = wm.peekDrawable() ?: wm.drawable
                if (drawable != null) {
                    val bitmap = android.graphics.Bitmap.createBitmap(reqW, reqH, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    drawable.setBounds(0, 0, reqW, reqH)
                    drawable.draw(canvas)
                    bitmap
                } else {
                    DebugLogger.log("WallpaperService", "System wallpaper drawable is null")
                    null
                }
            } catch (e: Exception) {
                Log.e("LiquidGlassWallpaper", "Error loading system wallpaper", e)
                null
            }
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

        private fun updateVideoBackground() {
            val path = currentVideoWallpaperPath
            if (isLocked) {
                // If locked, we don't show main video (Media Art has priority if playing)
                // If path is null, it'll clear.
                return
            }

            if (path != null) {
                try {
                    val uri = Uri.parse(path)
                    // If it's a file path
                    val file = if (path.startsWith("/")) java.io.File(path)
                    else {
                        // For content URIs, we might need to cache them?
                        // VideoRenderer expects a File.
                        // Let's assume for now user picked a local file or we enhance VideoRenderer later.
                        null
                    }
                    if (file != null && file.exists()) {
                         videoRenderer?.setVideoSource(file)
                    }
                } catch (e: Exception) {
                    Log.e("LiquidGlassWallpaper", "Video bg error", e)
                }
            } else {
                // videoRenderer?.reset() // Be careful not to stop media art video
            }
        }

        private fun startGifJobIfNeeded() {
            gifJob?.cancel()
            val uri = currentGifUri ?: return
            if (isInAmbientMode || isPowerSaveMode || !isVisible) return

            gifJob = engineScope.launch {
                try {
                    val request = ImageRequest.Builder(applicationContext)
                        .data(Uri.parse(uri))
                        .build()

                    val result = imageLoader?.execute(request)
                    if (result is coil.request.SuccessResult) {
                        val drawable = result.drawable
                        if (drawable is android.graphics.drawable.Animatable) {
                            drawable.start()

                            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 512
                            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 512

                            // Re-use frame bitmap to prevent memory leak
                            if (gifFrameBitmap == null || gifFrameBitmap!!.width != width || gifFrameBitmap!!.height != height) {
                                gifFrameBitmap?.recycle()
                                gifFrameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            }

                            val canvas = Canvas(gifFrameBitmap!!)

                            while (isActive) {
                                // Clear previous frame
                                gifFrameBitmap!!.eraseColor(Color.TRANSPARENT)
                                drawable.setBounds(0, 0, width, height)
                                drawable.draw(canvas)

                                videoRenderer?.updateGifFrame(gifFrameBitmap!!)

                                delay(16) // ~60fps
                                draw()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LiquidGlassWallpaper", "GIF error", e)
                }
            }
        }

        private var cachedUiBitmap: Bitmap? = null
        private var lastMediaState: String = ""
        private var lastBgBitmap: Bitmap? = null
        private var lastSubBitmap: Bitmap? = null

        private fun draw() {
            // Ambient Mode Handling (Black screen + Simple Clock)
            if (isInAmbientMode) {
                // Use Canvas drawing for Ambient Mode to avoid GL overhead if possible,
                // or just clear GL to black and draw UI.
                // Let's use the existing GL renderer but set background to black (null)
                videoRenderer?.setBackground(null)
                videoRenderer?.setSubject(null)

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

            // Unify everything to use GL Renderer (eliminates flickering between Canvas and GL)
            // Pass background and subject layers with dirty checking to avoid redundant GPU uploads

            // Re-verify lock state immediately to prevent secret bleed
            updateLockState()

            val isShowingMediaArt = (isLocked && settings.enableLockScreenMediaArt) || settings.enableHomeMediaArt
            val artToDisplay = if (isShowingMediaArt) mediaArtBitmap else null

            // Media Art is now ONLY static background on lockscreen to avoid overlaps with animated overlay
            val currentBg = artToDisplay ?: scaledWallpaper

            if (isLocked) {
                DebugLogger.log("WallpaperService", "Locked status check: showingMediaArt=$isShowingMediaArt, currentBg=${currentBg != null}")
            }

            // Identity check to avoid redundant texture uploads
            if (currentBg !== lastBgBitmap) {
                videoRenderer?.setBackground(currentBg)
                lastBgBitmap = currentBg
            }

            val currentSub = if (!isLocked) scaledSubject else null
            if (currentSub != lastSubBitmap) {
                videoRenderer?.setSubject(currentSub)
                lastSubBitmap = currentSub
            }

            // Update Video Renderer state for Animated Art
            if (isShowingMediaArt && animatedMediaFile != null && !isPowerSaveMode) {
                // Ensure video renderer is playing our animated cover
                if (currentVideoPath != animatedMediaFile?.absolutePath) {
                    videoRenderer?.setVideoSource(animatedMediaFile!!)
                    currentVideoPath = animatedMediaFile?.absolutePath
                }
            } else if (!isLocked && currentVideoWallpaperPath != null && !isPowerSaveMode) {
                 // Home Screen Video Background
                 if (currentVideoPath != currentVideoWallpaperPath) {
                     val file = java.io.File(currentVideoWallpaperPath!!)
                     if (file.exists()) {
                         videoRenderer?.setVideoSource(file)
                         currentVideoPath = currentVideoWallpaperPath
                     }
                 }
            } else {
                 // No video should be playing if we are not showing media art on lock screen
                 // or if no home screen video is set.
                 if (currentVideoPath != null) {
                     videoRenderer?.reset()
                     currentVideoPath = null
                 }
            }

            // Render UI to Bitmap, then pass to GL
            val calendar = Calendar.getInstance()
            val currentTime = if (isLocked) timeFormat.format(calendar.time) else ""
            val currentDate = if (isLocked) dateFormat.format(calendar.time) else ""

            // Include time/lock state in check
            val currentState = "$mediaTitle|$mediaArtist|${surfaceHolder?.surfaceFrame?.width()}|$currentTime|$currentDate|$isLocked"

            if (currentState != lastMediaState) {
                if (isLocked) {
                    cachedUiBitmap?.recycle()
                    cachedUiBitmap = createUiBitmap(currentTime, currentDate)
                    if (cachedUiBitmap != null) {
                        videoRenderer?.updateUI(cachedUiBitmap!!)
                    }
                } else if (lastMediaState.isEmpty() || lastMediaState.endsWith("true")) {
                    // Just transitioned to unlocked, clear UI overlay once
                    cachedUiBitmap?.recycle()
                    cachedUiBitmap = null
                    val empty = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                    videoRenderer?.updateUI(empty)
                }
                lastMediaState = currentState
            }

            videoRenderer?.draw()

            // Request next frame
            if (isVisible) {
                handler.removeCallbacks(drawRunnable)
                if (isPowerSaveMode) {
                    handler.postDelayed(drawRunnable, 1000)
                } else if (!isInAmbientMode) {
                    // Choreographer loop is managed independently in frameCallback.doFrame
                    // No need to post here or it will grow exponentially.
                }
            }
        }

        private fun createUiBitmap(time: String, date: String): Bitmap? {
            val w = surfaceHolder?.surfaceFrame?.width() ?: 0
            val h = surfaceHolder?.surfaceFrame?.height() ?: 0
            if (w <= 0 || h <= 0) return null

            // Re-use or create bitmap
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cvs = Canvas(bmp)

            // No Background Rendering here - just overlay content

            val centerX = (w / 2f) + burnInOffsetX
            val density = resources.displayMetrics.density

            // --- Draw Clock & UI (Only if NOT showing interactive overlay) ---
            if (isInAmbientMode) {
                 // Clock
                 val clockY = (CLOCK_TOP_MARGIN_DP * density + clockPaint.textSize) + burnInOffsetY
                 val textColor = Color.LTGRAY // Dim for OLED AOD

                 clockPaint.color = textColor
                 datePaint.color = textColor

                 cvs.drawText(time, centerX, clockY, clockPaint)
                 val dateY = clockY + datePaint.textSize + DATE_GAP_DP * density
                 cvs.drawText(date, centerX, dateY, datePaint)
                 return bmp
            }

            // Standard Lockscreen UI (Redundant with overlay - keep empty for now)
            // Or only draw if overlay launch failed?
            // Let's keep it clean as requested.
            // However, we MUST render a subtle shadow/gradient if media info is NOT playing to prevent blank looks
            if (!isLocked) {
                 // Unlocked: drawing nothing here is fine, the launcher handles UI
            } else {
                 // Locked: overlay is active, keep this bitmap nearly empty (only debug logs)
            }

            // --- Draw Debug Logs ---
            if (settings.showDebugLogs) {
                val logX = DEBUG_LOG_X_OFFSET_DP * density
                val logs = DebugLogger.getLogs()
                val maxWidth = (w - (DEBUG_LOG_X_OFFSET_DP * DEBUG_LOG_MARGIN_MULTIPLIER * density)).toInt()

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
                    val availableHeight = h - startY - (DEBUG_LOG_BOTTOM_MARGIN_DP * density)

                    var curY = if (totalHeight > availableHeight) {
                        startY + availableHeight - totalHeight
                    } else {
                        startY
                    }

                    // 3. Draw visible logs
                    for (layout in layouts) {
                        if (curY + layout.height > startY && curY < h) {
                            cvs.save()
                            cvs.translate(logX, curY)
                            layout.draw(cvs)
                            cvs.restore()
                        }
                        curY += layout.height + DEBUG_LOG_LINE_SPACING
                    }
                }
            }

            return bmp
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

    }
}
