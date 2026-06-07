package com.quimodotcom.lqlauncher.compose.launcher

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Settings for liquid glass visual effects
 */
@Serializable
data class LiquidGlassSettings(
    // Master switch
    val liquidGlassEnabled: Boolean = false,

    // Blur settings
    val blurRadius: Float = 20f,
    val blurEnabled: Boolean = true,
    
    // Lens/refraction effect (glass slider style)
    val refractionHeight: Float = 12f,
    val refractionAmount: Float = 16f,
    val chromaticAberration: Boolean = true,
    val lensEnabled: Boolean = true,
    
    // Vibrancy
    val vibrancyEnabled: Boolean = true,
    
    // Colors and transparency
    val panelTintColor: Long = 0xFF6366F1L,
    val panelBackgroundAlpha: Float = 0.12f,
    val iconBackgroundAlpha: Float = 0.1f,
    
    // Corner radius
    val panelCornerRadius: Float = 20f,
    val iconCornerRadius: Float = 16f,
    
    // Grid settings
    val gridColumns: Int = 4,
    val gridRows: Int = 6,
    val appTileScale: Float = 1.0f,  // 0.5 to 1.5, controls app icon size within cell
    
    // Animation settings
    val dragSpringDamping: Float = 0.6f,
    val dragSpringStiffness: Float = 200f,
    
    // Theme
    val useDarkTheme: Boolean = true,
    val useSystemWallpaper: Boolean = true,
    
    // Icon pack
    val iconPackPackageName: String = "",  // Empty string = default icons
    // Toggle whether the app drawer should use icon packs (may fallback to app icons if pack icons are missing)
    val useIconPackInAppDrawer: Boolean = true,

    // App labels
    val showAppLabels: Boolean = true,  // Show app names below icons

    // Integrations
    val openWeatherApiKey: String = "",
    val searchWidgetOpensBrowserOnTap: Boolean = true,
    val weatherUnit: String = "F", // "F" or "C"

    // Device Integrity
    val playIntegrityCloudProjectNumber: String = "",
    val playIntegrityEnabled: Boolean = false,

    // Widget Styles
    val clockStyle: String = "Classic",
    val weatherStyle: String = "Classic",
    val batteryStyle: String = "Classic",

    // Global Theme
    val cyberpunkTheme: Boolean = false,

    // Motion
    val enableParallax: Boolean = false,
    val parallaxIntensity: Float = 0.2f,

    // Lock Screen
    val enableLockScreenMediaArt: Boolean = true,
    val enableHomeMediaArt: Boolean = false,
    val enableLockScreenControls: Boolean = false,
    val mediaArtGlowEnabled: Boolean = false,
    val ledMatrixEnabled: Boolean = false,
    val scrollingTextEnabled: Boolean = false,

    // Debug & Updates
    val showDebugSettings: Boolean = false,
    val showDebugLogs: Boolean = false,
    val githubUpdateUrl: String = "",
    val githubToken: String = "",
    val autoUpdateEnabled: Boolean = false,

    // Notifications
    val showNotificationDots: Boolean = false,
    val notificationDotColor: Long = 0xFFEF4444L, // Default Red-500
    val liquidGlassNotificationDots: Boolean = false,

    // Wallpaper Schedule
    val dayStartHour: Int = 7,
    val dayStartMinute: Int = 0,
    val nightStartHour: Int = 20,
    val nightStartMinute: Int = 0,

    // Theme System
    val currentThemeId: String = "default",
    val customThemes: List<LauncherTheme> = emptyList(),

    // Runtime state (persisted for convenience)
    val secretWallpaperVisible: Boolean = true
) {
    /**
     * Determines if it is currently "night" based on the scheduled times,
     * the system theme, or manual user override.
     *
     * Detailed logic:
     * 1. **Default behavior:** If `nightStart` and `dayStart` are set to the same time (default),
     *    the launcher defers to the system-wide Dark Theme setting.
     * 2. **Midnight Spanning:** If `nightStart` is greater than `dayStart` (e.g., 22:00 to 07:00),
     *    the time is considered "night" if it is later than `nightStart` OR earlier than `dayStart`.
     * 3. **Single Day Range:** If `nightStart` is less than `dayStart` (e.g., 00:00 to 08:00),
     *    the time is considered "night" if it is between both values.
     *
     * @param context Used to access the system theme configuration.
     * @return True if the current theme should be "night" (dark).
     */
    fun isCurrentlyNight(context: android.content.Context): Boolean {
        // Check system theme as a fallback or baseline
        val uiMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isSystemDark = uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES

        val calendar = java.util.Calendar.getInstance()
        val currentMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
        val nightStart = nightStartHour * 60 + nightStartMinute
        val dayStart = dayStartHour * 60 + dayStartMinute

        return if (nightStart == dayStart) {
            // If times are identical, defer to system theme
            isSystemDark
        } else if (nightStart > dayStart) {
            // Night spans across midnight (e.g., 20:00 to 07:00)
            currentMinutes >= nightStart || currentMinutes < dayStart
        } else {
            // Night is within the same day (e.g., 00:00 to 07:00)
            currentMinutes >= nightStart && currentMinutes < dayStart
        }
    }
}

/**
 * Repository for saving/loading liquid glass settings
 */
object LiquidGlassSettingsRepository {
    private const val SETTINGS_FILE = "liquid_glass_settings.json"
    const val ACTION_CONFIG_CHANGED = "com.quimodotcom.lqlauncher.ACTION_CONFIG_CHANGED"
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    suspend fun saveSettings(context: Context, settings: LiquidGlassSettings) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, SETTINGS_FILE)
                file.writeText(json.encodeToString(settings))

                // Notify service (WallpaperService) to reload settings
                context.sendBroadcast(Intent(ACTION_CONFIG_CHANGED))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    suspend fun loadSettings(context: Context): LiquidGlassSettings {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, SETTINGS_FILE)
                if (file.exists()) {
                    json.decodeFromString<LiquidGlassSettings>(file.readText())
                } else {
                    LiquidGlassSettings()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                LiquidGlassSettings()
            }
        }
    }
}
