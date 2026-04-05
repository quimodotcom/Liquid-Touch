package com.quimodotcom.lqlauncher.compose.launcher

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SchematicSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testDeserializationWithLargeColorValues() {
        // This simulates an "old" schematic where colors were stored as Long
        // 0xFF6366F1 as Long is 4284704497L
        // 0xFFEF4444 as Long is 4293854276L
        val jsonString = """
        {
            "config": {
                "gridColumns": 4,
                "gridRows": 6,
                "items": [
                    {
                        "type": "com.quimodotcom.lqlauncher.compose.launcher.LauncherItem.GlassPanel",
                        "id": "test-id",
                        "gridX": 0,
                        "gridY": 0,
                        "spanX": 2,
                        "spanY": 2,
                        "title": "Test Panel",
                        "blurRadius": 20.0,
                        "tintColor": 4284704497,
                        "backgroundAlpha": 0.12,
                        "panelType": "CLOCK"
                    }
                ],
                "useSystemWallpaper": true,
                "showStatusBar": true
            },
            "settings": {
                "blurRadius": 20.0,
                "blurEnabled": true,
                "refractionHeight": 12.0,
                "refractionAmount": 16.0,
                "chromaticAberration": true,
                "lensEnabled": true,
                "vibrancyEnabled": true,
                "panelTintColor": 4284704497,
                "panelBackgroundAlpha": 0.12,
                "iconBackgroundAlpha": 0.1,
                "panelCornerRadius": 20.0,
                "iconCornerRadius": 16.0,
                "gridColumns": 4,
                "gridRows": 6,
                "appTileScale": 1.0,
                "dragSpringDamping": 0.6,
                "dragSpringStiffness": 200.0,
                "useDarkTheme": true,
                "useSystemWallpaper": true,
                "iconPackPackageName": "",
                "useIconPackInAppDrawer": true,
                "showAppLabels": true,
                "openWeatherApiKey": "",
                "searchWidgetOpensBrowserOnTap": true,
                "weatherUnit": "F",
                "clockStyle": "Classic",
                "weatherStyle": "Classic",
                "batteryStyle": "Classic",
                "cyberpunkTheme": false,
                "enableParallax": false,
                "parallaxIntensity": 0.2,
                "enableLockScreenMediaArt": false,
                "showDebugSettings": false,
                "showDebugLogs": false,
                "githubUpdateUrl": "",
                "githubToken": "",
                "showNotificationDots": false,
                "notificationDotColor": 4293870660,
                "liquidGlassNotificationDots": false
            },
            "metadata": {}
        }
        """.trimIndent()

        val schematic = json.decodeFromString<LauncherSchematic>(jsonString)

        assertEquals(4284704497L, (schematic.config.items[0] as LauncherItem.GlassPanel).tintColor)
        assertEquals(4284704497L, schematic.settings.panelTintColor)
        assertEquals(4293870660L, schematic.settings.notificationDotColor)

        // Verify Int conversion (ARGB)
        assertEquals(0xFF6366F1.toInt(), schematic.settings.panelTintColor.toInt())
        assertEquals(0xFFEF4444.toInt(), schematic.settings.notificationDotColor.toInt())
    }
}
