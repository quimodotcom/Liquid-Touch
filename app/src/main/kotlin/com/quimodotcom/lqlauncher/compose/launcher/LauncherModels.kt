package com.quimodotcom.lqlauncher.compose.launcher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.content.ComponentName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents an item on the launcher grid
 */
@Serializable
sealed class LauncherItem {
    abstract val id: String
    abstract val gridX: Int
    abstract val gridY: Int
    abstract val spanX: Int
    abstract val spanY: Int
    
    /**
     * An app shortcut on the home screen
     */
    @Serializable
    data class AppShortcut(
        override val id: String = UUID.randomUUID().toString(),
        override val gridX: Int,
        override val gridY: Int,
        override val spanX: Int = 1,
        override val spanY: Int = 1,
        val packageName: String,
        val label: String,
        val customIconUri: String? = null // Custom icon if user changed it
    ) : LauncherItem()
    
    /**
     * A liquid glass panel that can contain widgets or custom content
     */
    @Serializable
    data class GlassPanel(
        override val id: String = UUID.randomUUID().toString(),
        override val gridX: Int,
        override val gridY: Int,
        override val spanX: Int = 2,
        override val spanY: Int = 2,
        val title: String = "",
        val blurRadius: Float = 20f,
        val tintColor: Long = 0xFF6366F1L, // Default indigo
        val backgroundAlpha: Float = 0.12f,
        val panelType: PanelType = PanelType.EMPTY,
        val customImageUri: String? = null,
        val apps: List<String> = emptyList() // Package names for APPS type
    ) : LauncherItem()
    
    /**
     * A folder containing multiple app shortcuts
     */
    @Serializable
    data class Folder(
        override val id: String = UUID.randomUUID().toString(),
        override val gridX: Int,
        override val gridY: Int,
        override val spanX: Int = 1,
        override val spanY: Int = 1,
        val name: String,
        val apps: List<String> = emptyList(), // Package names
        val tintColor: Long = 0xFF6366F1L
    ) : LauncherItem()

    /**
     * An invisible button that triggers actions
     */
    @Serializable
    data class InvisibleButton(
        override val id: String = UUID.randomUUID().toString(),
        override val gridX: Int,
        override val gridY: Int,
        override val spanX: Int = 1,
        override val spanY: Int = 1,
        val action: LauncherAction = LauncherAction.NONE,
        val targetPackageName: String? = null
    ) : LauncherItem()
}

/**
 * Actions that can be triggered by an invisible button
 */
@Serializable
enum class LauncherAction {
    NONE,
    TOGGLE_SECRET_WALLPAPER,
    OPEN_APP,
    OPEN_APP_DRAWER,
    OPEN_SETTINGS
}

/**
 * Types of panels available
 */
@Serializable
enum class PanelType {
    EMPTY,      // Empty panel for decoration
    CLOCK,      // Shows time/date
    WEATHER,    // Weather widget (placeholder)
    QUICK_SETTINGS, // Quick toggles
    BATTERY,    // Battery level and percentage
    SEARCH,     // One-row browser search
    MEDIA_CONTROL, // Interactive media controls
    PLAY_INTEGRITY, // Device Integrity Testing
    APPS,       // Mini-grid of apps
    CUSTOM      // User-defined content
}

/**
 * Configuration for the entire launcher
 */
@Serializable
data class LauncherConfig(
    val gridColumns: Int = 4,
    val gridRows: Int = 6,
    val items: List<LauncherItem> = emptyList(),
    val wallpaperUri: String? = null,
    val wallpaperGifUri: String? = null,
    val wallpaperVideoUri: String? = null,
    val wallpaperNightUri: String? = null,
    val wallpaperSecretUri: String? = null,
    val wallpaperSubjectUri: String? = null,
    val wallpaperSubjectNightUri: String? = null,
    val subjectMatchWallpaper: Boolean = true,
    val subjectScale: Float = 1f,
    val subjectOffsetX: Float = 0f,
    val subjectOffsetY: Float = 0f,
    val subjectNightMatchWallpaper: Boolean = true,
    val subjectNightScale: Float = 1f,
    val subjectNightOffsetX: Float = 0f,
    val subjectNightOffsetY: Float = 0f,
    val useSystemWallpaper: Boolean = true,
    val showStatusBar: Boolean = true,
    // Whether the first-open wallpaper permission prompt has been shown
    val permissionPromptShown: Boolean = false
)

/**
 * Represents a complete launcher setup that can be exported/imported
 */
@Serializable
data class LauncherTheme(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Unnamed Theme",
    val description: String = "",
    val liquidGlassEnabled: Boolean = true,
    val blurRadius: Float = 20f,
    val refractionHeight: Float = 12f,
    val refractionAmount: Float = 16f,
    val chromaticAberration: Boolean = true,
    val vibrancyEnabled: Boolean = true,
    val panelTintColor: Long = 0xFF6366F1L,
    val panelBackgroundAlpha: Float = 0.12f,
    val iconBackgroundAlpha: Float = 0.1f,
    val panelCornerRadius: Float = 20f,
    val iconCornerRadius: Float = 16f,
    val clockStyle: String = "Classic",
    val weatherStyle: String = "Classic",
    val batteryStyle: String = "Classic",
    val cyberpunkTheme: Boolean = false,
    val windowBlurEnabled: Boolean = false,
    val windowBlurRadius: Float = 20f,
    val panelBlurEnabled: Boolean = false,
    val drawerBlurEnabled: Boolean = true,
    val drawerBlurRadius: Float = 25f
) {
    companion object {
        val Default = LauncherTheme(
            id = "default",
            name = "Liquid Glass",
            description = "The classic frosted glass experience",
            liquidGlassEnabled = true,
            blurRadius = 20f,
            panelBackgroundAlpha = 0.12f,
            iconBackgroundAlpha = 0.1f,
            panelCornerRadius = 20f,
            iconCornerRadius = 16f,
            clockStyle = "Classic",
            cyberpunkTheme = false,
            drawerBlurRadius = 25f
        )

        val Cyberpunk = LauncherTheme(
            id = "cyberpunk",
            name = "Cyberpunk",
            description = "Neon aesthetics and sharp edges",
            liquidGlassEnabled = true,
            blurRadius = 15f,
            panelTintColor = 0xFFEC4899L, // Pink
            panelBackgroundAlpha = 0.15f,
            iconBackgroundAlpha = 0.15f,
            panelCornerRadius = 4f,
            iconCornerRadius = 4f,
            clockStyle = "Cyberpunk",
            weatherStyle = "Cyberpunk",
            batteryStyle = "Cyberpunk",
            cyberpunkTheme = true,
            drawerBlurRadius = 15f
        )

        val Frosted = LauncherTheme(
            id = "frosted",
            name = "Deep Frost",
            description = "High opacity and heavy blur",
            liquidGlassEnabled = true,
            blurRadius = 45f,
            panelBackgroundAlpha = 0.35f,
            iconBackgroundAlpha = 0.25f,
            panelCornerRadius = 24f,
            iconCornerRadius = 20f,
            clockStyle = "Minimal",
            drawerBlurRadius = 50f
        )

        val Minimal = LauncherTheme(
            id = "minimal",
            name = "Minimal",
            description = "Clean, sharp, and transparent",
            liquidGlassEnabled = false,
            blurRadius = 0f,
            panelBackgroundAlpha = 0.05f,
            iconBackgroundAlpha = 0.05f,
            panelCornerRadius = 12f,
            iconCornerRadius = 12f,
            clockStyle = "Minimal",
            windowBlurEnabled = false,
            panelBlurEnabled = false,
            drawerBlurEnabled = false
        )
    }
}

@Serializable
data class LauncherSchematic(
    val config: LauncherConfig,
    val settings: LiquidGlassSettings,
    val metadata: Map<String, AppMetadata> = emptyMap()
)

/**
 * Edit mode state
 */
data class EditModeState(
    val isEnabled: Boolean = false,
    val selectedItemId: String? = null,
    val isDragging: Boolean = false,
    val isResizing: Boolean = false,
    val showAppPicker: Boolean = false,
    val showPanelPicker: Boolean = false,
    val showWallpaperPicker: Boolean = false,
    val isToolbarAtTop: Boolean = false,
    val isUiHidden: Boolean = false,
    val dragOffset: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero
)

/**
 * Actions that can be performed in edit mode
 */
sealed class EditAction {
    object EnterEditMode : EditAction()
    object ExitEditMode : EditAction()
    data class SelectItem(val itemId: String) : EditAction()
    object DeselectItem : EditAction()
    data class MoveItem(val itemId: String, val newX: Int, val newY: Int) : EditAction()
    data class ResizeItem(val itemId: String, val newSpanX: Int, val newSpanY: Int) : EditAction()
    data class DeleteItem(val itemId: String) : EditAction()
    data class AddAppShortcut(val gridX: Int, val gridY: Int, val packageName: String, val label: String) : EditAction()
    data class AddPanel(val gridX: Int, val gridY: Int, val panelType: PanelType) : EditAction()
    data class AddFolder(val gridX: Int, val gridY: Int, val name: String) : EditAction()
    data class UpdateItem(val item: LauncherItem) : EditAction()
    data class SetWallpaper(val uri: String?) : EditAction()
    object ShowAppPicker : EditAction()
    object ShowPanelPicker : EditAction()
    object ShowWallpaperPicker : EditAction()
    object HideAllPickers : EditAction()
}

/**
 * Represents available apps for the picker
 */
data class AvailableApp(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable?,
    val componentName: ComponentName,
    val customIconUri: String? = null
)
