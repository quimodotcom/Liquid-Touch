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
    val panelTintColor: Long = 0xFF6366F1,
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
    val enableLockScreenMediaArt: Boolean = false,

    // Debug & Updates
    val showDebugSettings: Boolean = false,
    val showDebugLogs: Boolean = false,
    val githubUpdateUrl: String = "",
    val githubToken: String = ""
)

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
