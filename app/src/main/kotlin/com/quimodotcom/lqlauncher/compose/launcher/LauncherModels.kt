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
        val tintColor: Long = 0xFF6366F1, // Default indigo
        val backgroundAlpha: Float = 0.12f,
        val panelType: PanelType = PanelType.EMPTY
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
        val tintColor: Long = 0xFF6366F1
    ) : LauncherItem()
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
    val wallpaperSubjectUri: String? = null,
    val subjectMatchWallpaper: Boolean = true,
    val subjectScale: Float = 1f,
    val subjectOffsetX: Float = 0f,
    val subjectOffsetY: Float = 0f,
    val useSystemWallpaper: Boolean = true,
    val showStatusBar: Boolean = true,
    // Whether the first-open wallpaper permission prompt has been shown
    val permissionPromptShown: Boolean = false
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
