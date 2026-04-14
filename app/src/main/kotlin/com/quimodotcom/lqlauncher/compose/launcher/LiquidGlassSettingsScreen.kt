package com.quimodotcom.lqlauncher.compose.launcher

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import android.view.HapticFeedbackConstants
import kotlinx.coroutines.launch
import android.widget.Toast
import android.provider.Settings
import android.content.Intent
import android.content.ComponentName

/**
 * Fully organized and categorized settings for Liquid Glass Launcher
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassSettingsScreen(
    settings: LiquidGlassSettings,
    onSettingsChanged: (LiquidGlassSettings) -> Unit,
    launcherConfig: LauncherConfig,
    onConfigChanged: (LauncherConfig) -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    onExportSchematic: () -> Unit,
    onImportSchematic: () -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0D0D12)
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Settings", color = Color.White) },
                        navigationIcon = {
                            IconButton(onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onDismiss()
                            }) {
                                Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White)
                            }
                        },
                        actions = {
                            TextButton(onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onSettingsChanged(LiquidGlassSettings())
                            }) {
                                Text("Reset All", color = Color(0xFF6366F1))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF0D0D12)
                        )
                    )
                },
                containerColor = Color(0xFF0D0D12)
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // === 1. WALLPAPER & LAYERS ===
                    item {
                        SettingsSection(title = "Wallpaper & Layers", icon = Icons.Rounded.Wallpaper)
                    }

                    item {
                        SettingItem(
                            title = "Background Layers",
                            description = "Configure Day/Night cycles and Subject layer",
                            onClick = onOpenWallpaperPicker
                        )
                    }

                    item {
                        var showSecretPicker by remember { mutableStateOf(false) }
                        SettingItem(
                            title = "Secret Wallpaper",
                            description = "Shown on home screen after unlocking",
                            onClick = { showSecretPicker = true }
                        )

                        if (showSecretPicker) {
                            SecretWallpaperPickerDialog(
                                currentConfig = launcherConfig,
                                onConfigChanged = onConfigChanged,
                                onDismiss = { showSecretPicker = false }
                            )
                        }
                    }



                    // === 2. VISUAL EFFECTS ===
                    item {
                        Spacer(Modifier.height(16.dp))
                        SettingsSection(title = "Visual Effects", icon = Icons.Rounded.AutoAwesome)
                    }

                    item {
                        SwitchSetting(
                            title = "Liquid Glass Effects",
                            subtitle = "Master switch for all glass visual effects",
                            checked = settings.liquidGlassEnabled,
                            onCheckedChange = { onSettingsChanged(settings.copy(liquidGlassEnabled = it)) }
                        )
                    }
                    
                    item {
                        SwitchSetting(
                            title = "Enable Blur",
                            subtitle = "Frosted glass transparency effect",
                            checked = settings.blurEnabled,
                            onCheckedChange = { onSettingsChanged(settings.copy(blurEnabled = it)) }
                        )
                    }
                    
                    item {
                        SliderSetting(
                            title = "Blur Radius",
                            value = settings.blurRadius,
                            valueRange = 1f..50f,
                            enabled = settings.blurEnabled,
                            valueLabel = "${settings.blurRadius.toInt()}dp",
                            onValueChange = { onSettingsChanged(settings.copy(blurRadius = it)) }
                        )
                    }

                    item {
                        SwitchSetting(
                            title = "Glass Refraction",
                            subtitle = "Realistic lens distortion effect",
                            checked = settings.lensEnabled,
                            onCheckedChange = { onSettingsChanged(settings.copy(lensEnabled = it)) }
                        )
                    }

                    item {
                        SliderSetting(
                            title = "Refraction Height",
                            value = settings.refractionHeight,
                            valueRange = 4f..32f,
                            enabled = settings.lensEnabled,
                            valueLabel = "${settings.refractionHeight.toInt()}dp",
                            onValueChange = { onSettingsChanged(settings.copy(refractionHeight = it)) }
                        )
                    }

                    item {
                        SliderSetting(
                            title = "Refraction Intensity",
                            value = settings.refractionAmount,
                            valueRange = 4f..40f,
                            enabled = settings.lensEnabled,
                            valueLabel = "${settings.refractionAmount.toInt()}",
                            onValueChange = { onSettingsChanged(settings.copy(refractionAmount = it)) }
                        )
                    }

                    item {
                        SwitchSetting(
                            title = "Chromatic Aberration",
                            subtitle = "Rainbow fringing on glass edges",
                            checked = settings.chromaticAberration,
                            onCheckedChange = { onSettingsChanged(settings.copy(chromaticAberration = it)) }
                        )
                    }

                    item {
                        SwitchSetting(
                            title = "Vibrancy",
                            subtitle = "Enhanced background saturation",
                            checked = settings.vibrancyEnabled,
                            onCheckedChange = { onSettingsChanged(settings.copy(vibrancyEnabled = it)) }
                        )
                    }

                    item {
                        ColorPickerSetting(
                            title = "Panel Tint Color",
                            currentColor = Color(settings.panelTintColor.toInt()),
                            onColorSelected = { 
                                onSettingsChanged(settings.copy(panelTintColor = it.toArgb().toLong()))
                            }
                        )
                    }

                    item {
                        SwitchSetting(
                            title = "Interactive Lock Controls",
                            subtitle = "Show playback buttons over the lock screen",
                            checked = settings.enableLockScreenControls,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val componentName = ComponentName(context.packageName, "com.quimodotcom.lqlauncher.services.MediaListenerService")
                                    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                                    val isEnabled = flat != null && flat.contains(componentName.flattenToString())

                                    if (!isEnabled) {
                                        Toast.makeText(context, "Grant Notification Access to enable", Toast.LENGTH_LONG).show()
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                        } catch (e: Exception) {}
                                        return@SwitchSetting
                                    }
                                }
                                onSettingsChanged(settings.copy(enableLockScreenControls = enabled))
                            }
                        )
                    }

                    item {
                        SliderSetting(
                            title = "Panel Transparency",
                            value = settings.panelBackgroundAlpha,
                            valueRange = 0.05f..0.4f,
                            valueLabel = "${(settings.panelBackgroundAlpha * 100).toInt()}%",
                            onValueChange = { onSettingsChanged(settings.copy(panelBackgroundAlpha = it)) }
                        )
                    }

                    item {
                        SliderSetting(
                            title = "Panel Corner Radius",
                            value = settings.panelCornerRadius,
                            valueRange = 8f..32f,
                            valueLabel = "${settings.panelCornerRadius.toInt()}dp",
                            onValueChange = { onSettingsChanged(settings.copy(panelCornerRadius = it)) }
                        )
                    }
                    
                    // === 3. GRID & LAYOUT ===
                    item {
                        Spacer(Modifier.height(16.dp))
                        SettingsSection(title = "Grid & Layout", icon = Icons.Rounded.GridView)
                    }
                    
                    item {
                        SliderSetting(
                            title = "Grid Columns",
                            value = settings.gridColumns.toFloat(),
                            valueRange = 3f..6f,
                            steps = 2,
                            valueLabel = "${settings.gridColumns}",
                            onValueChange = { onSettingsChanged(settings.copy(gridColumns = it.toInt())) }
                        )
                    }
                    
                    item {
                        SliderSetting(
                            title = "Grid Rows",
                            value = settings.gridRows.toFloat(),
                            valueRange = 4f..8f,
                            steps = 3,
                            valueLabel = "${settings.gridRows}",
                            onValueChange = { onSettingsChanged(settings.copy(gridRows = it.toInt())) }
                        )
                    }
                    
                    item {
                        SliderSetting(
                            title = "App Icon Size",
                            value = settings.appTileScale,
                            valueRange = 0.5f..1.5f,
                            valueLabel = "${(settings.appTileScale * 100).toInt()}%",
                            onValueChange = { onSettingsChanged(settings.copy(appTileScale = it)) }
                        )
                    }

                    item {
                        SliderSetting(
                            title = "Icon Corner Radius",
                            value = settings.iconCornerRadius,
                            valueRange = 4f..24f,
                            valueLabel = "${settings.iconCornerRadius.toInt()}dp",
                            onValueChange = { onSettingsChanged(settings.copy(iconCornerRadius = it)) }
                        )
                    }

                    item {
                        SliderSetting(
                            title = "Icon Transparency",
                            value = settings.iconBackgroundAlpha,
                            valueRange = 0.05f..0.3f,
                            valueLabel = "${(settings.iconBackgroundAlpha * 100).toInt()}%",
                            onValueChange = { onSettingsChanged(settings.copy(iconBackgroundAlpha = it)) }
                        )
                    }

                    item {
                        SwitchSetting(
                            title = "Show App Labels",
                            subtitle = "Display names below icons",
                            checked = settings.showAppLabels,
                            onCheckedChange = { onSettingsChanged(settings.copy(showAppLabels = it)) }
                        )
                    }

                    // === 4. THEMES & ICONS ===
                    item {
                        Spacer(Modifier.height(16.dp))
                        SettingsSection(title = "Themes & Icons", icon = Icons.Rounded.Palette)
                    }

                    item {
                        SwitchSetting(
                            title = "Cyberpunk Theme",
                            subtitle = "Dark mode with high-contrast neon accents",
                            checked = settings.cyberpunkTheme,
                            onCheckedChange = { onSettingsChanged(settings.copy(cyberpunkTheme = it)) }
                        )
                    }

                    item {
                        // Icon Pack Picker
                        var showIconPackPicker by remember { mutableStateOf(false) }
                        val currentPackName = settings.iconPackPackageName.ifEmpty { "Default" }

                        SettingItem(
                            title = "Icon Pack",
                            description = if (settings.iconPackPackageName.isEmpty()) "Default" else "Active: $currentPackName",
                            onClick = { showIconPackPicker = true }
                        )

                        if (showIconPackPicker) {
                            IconPackPickerDialog(
                                currentPack = settings.iconPackPackageName,
                                onPackSelected = { pkg ->
                                    onSettingsChanged(settings.copy(iconPackPackageName = pkg))
                                    showIconPackPicker = false
                                },
                                onDismiss = { showIconPackPicker = false }
                            )
                        }
                    }

                    item {
                        SwitchSetting(
                            title = "Icon Pack in Drawer",
                            subtitle = "Apply custom icons to app drawer",
                            checked = settings.useIconPackInAppDrawer,
                            onCheckedChange = { onSettingsChanged(settings.copy(useIconPackInAppDrawer = it)) }
                        )
                    }

                    // === 5. WIDGET STYLES ===
                    item {
                        Spacer(Modifier.height(16.dp))
                        SettingsSection(title = "Widget Styles", icon = Icons.Rounded.Style)
                    }

                    item {
                        val styles = listOf("Classic", "Cyberpunk")
                        StyleSelector(
                            title = "Clock Style",
                            currentStyle = settings.clockStyle,
                            options = styles,
                            onStyleSelected = { onSettingsChanged(settings.copy(clockStyle = it)) }
                        )
                    }

                    item {
                        val styles = listOf("Classic", "Cyberpunk")
                        StyleSelector(
                            title = "Weather Style",
                            currentStyle = settings.weatherStyle,
                            options = styles,
                            onStyleSelected = { onSettingsChanged(settings.copy(weatherStyle = it)) }
                        )
                    }

                    item {
                        val styles = listOf("Classic", "Cyberpunk")
                        StyleSelector(
                            title = "Battery Style",
                            currentStyle = settings.batteryStyle,
                            options = styles,
                            onStyleSelected = { onSettingsChanged(settings.copy(batteryStyle = it)) }
                        )
                    }
                    
                    // === 6. INTERACTION ===
                    item {
                        Spacer(Modifier.height(16.dp))
                        SettingsSection(title = "Interaction", icon = Icons.Rounded.Explore)
                    }
                    
                    item {
                        SliderSetting(
                            title = "Drag Elasticity",
                            value = settings.dragSpringDamping,
                            valueRange = 0.3f..1.0f,
                            valueLabel = String.format("%.1f", settings.dragSpringDamping),
                            onValueChange = { onSettingsChanged(settings.copy(dragSpringDamping = it)) }
                        )
                    }
                    
                    item {
                        SliderSetting(
                            title = "Drag Speed",
                            value = settings.dragSpringStiffness,
                            valueRange = 50f..500f,
                            valueLabel = "${settings.dragSpringStiffness.toInt()}",
                            onValueChange = { onSettingsChanged(settings.copy(dragSpringStiffness = it)) }
                        )
                    }

                    item {
                        SwitchSetting(
                            title = "Parallax Effect",
                            subtitle = "Background moves with device tilt",
                            checked = settings.enableParallax,
                            onCheckedChange = { onSettingsChanged(settings.copy(enableParallax = it)) }
                        )
                    }

                    item {
                        SliderSetting(
                            title = "Parallax Intensity",
                            value = settings.parallaxIntensity,
                            valueRange = 0f..2f,
                            enabled = settings.enableParallax,
                            valueLabel = String.format("%.2f", settings.parallaxIntensity),
                            onValueChange = { onSettingsChanged(settings.copy(parallaxIntensity = it)) }
                        )
                    }

                    // === 7. INTEGRATIONS ===
                    item {
                        Spacer(Modifier.height(16.dp))
                        SettingsSection(title = "Integrations", icon = Icons.Rounded.Cloud)
                    }

                    item {
                        SwitchSetting(
                            title = "Notification Dots",
                            subtitle = "Show badge on apps with notifications",
                            checked = settings.showNotificationDots,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val componentName = ComponentName(context.packageName, "com.quimodotcom.lqlauncher.services.MediaListenerService")
                                    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                                    val isEnabled = flat != null && flat.contains(componentName.flattenToString())

                                    if (!isEnabled) {
                                        Toast.makeText(context, "Grant Notification Access to enable", Toast.LENGTH_LONG).show()
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                        } catch (e: Exception) {}
                                        return@SwitchSetting
                                    }
                                }
                                onSettingsChanged(settings.copy(showNotificationDots = enabled))
                            }
                        )
                    }

                    item {
                        ColorPickerSetting(
                            title = "Notification Dot Color",
                            currentColor = Color(settings.notificationDotColor.toInt()),
                            onColorSelected = {
                                onSettingsChanged(settings.copy(notificationDotColor = it.toArgb().toLong()))
                            },
                            enabled = !settings.liquidGlassNotificationDots
                        )
                    }

                    item {
                        SwitchSetting(
                            title = "Liquid Glass Dots",
                            subtitle = "Apply glass effect to notification badges",
                            checked = settings.liquidGlassNotificationDots,
                            onCheckedChange = { onSettingsChanged(settings.copy(liquidGlassNotificationDots = it)) }
                        )
                    }

                    item {
                        SwitchSetting(
                            title = "Lock Screen Media Art",
                            subtitle = "Show full screen album art on lock screen",
                            checked = settings.enableLockScreenMediaArt,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val componentName = ComponentName(context.packageName, "com.quimodotcom.lqlauncher.services.MediaListenerService")
                                    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                                    val isEnabled = flat != null && flat.contains(componentName.flattenToString())

                                    if (!isEnabled) {
                                        Toast.makeText(context, "Grant Notification Access to enable", Toast.LENGTH_LONG).show()
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                        } catch (e: Exception) {}
                                        return@SwitchSetting
                                    }
                                }
                                onSettingsChanged(settings.copy(enableLockScreenMediaArt = enabled))
                            }
                        )
                    }

                    item {
                        SwitchSetting(
                            title = "Home Screen Media Art",
                            subtitle = "Show full screen album art on home screen",
                            checked = settings.enableHomeMediaArt,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val componentName = ComponentName(context.packageName, "com.quimodotcom.lqlauncher.services.MediaListenerService")
                                    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                                    val isEnabled = flat != null && flat.contains(componentName.flattenToString())

                                    if (!isEnabled) {
                                        Toast.makeText(context, "Grant Notification Access to enable", Toast.LENGTH_LONG).show()
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                        } catch (e: Exception) {}
                                        return@SwitchSetting
                                    }
                                }
                                onSettingsChanged(settings.copy(enableHomeMediaArt = enabled))
                            }
                        )
                    }

                    item {
                        // OpenWeather API Key
                        var editingKey by remember { mutableStateOf(false) }
                        SettingItem(
                            title = "OpenWeather API Key",
                            description = if (settings.openWeatherApiKey.isBlank()) "Not set" else "********",
                            onClick = { editingKey = true }
                        )

                        if (editingKey) {
                            Dialog(onDismissRequest = { editingKey = false }) {
                                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1A1A24)) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("OpenWeather API Key", color = Color.White)
                                        Spacer(Modifier.height(8.dp))
                                        var keyText by remember { mutableStateOf(settings.openWeatherApiKey) }
                                        OutlinedTextField(
                                            value = keyText,
                                            onValueChange = { keyText = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                            TextButton(onClick = { editingKey = false }) { Text("Cancel") }
                                            TextButton(onClick = { onSettingsChanged(settings.copy(openWeatherApiKey = keyText)); editingKey = false }) { Text("Save") }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        val units = listOf("F", "C")
                        StyleSelector(
                            title = "Temperature Unit",
                            currentStyle = settings.weatherUnit,
                            options = units,
                            onStyleSelected = { onSettingsChanged(settings.copy(weatherUnit = it)) }
                        )
                    }

                    item {
                        SwitchSetting(
                            title = "Search Widget opens Browser",
                            subtitle = "Tapping search opens browser immediately",
                            checked = settings.searchWidgetOpensBrowserOnTap,
                            onCheckedChange = { onSettingsChanged(settings.copy(searchWidgetOpensBrowserOnTap = it)) }
                        )
                    }

                    // === 8. MAINTENANCE ===
                    item {
                        Spacer(Modifier.height(16.dp))
                        SettingsSection(title = "Maintenance", icon = Icons.Rounded.Build)
                    }

                    item {
                        SettingItem(
                            title = "Export Schematic",
                            description = "Save layout and settings to file",
                            onClick = onExportSchematic
                        )
                    }

                    item {
                        SettingItem(
                            title = "Import Schematic",
                            description = "Restore layout and settings from file",
                            onClick = onImportSchematic
                        )
                    }

                    item {
                        var showConfirmation by remember { mutableStateOf(false) }

                        if (showConfirmation) {
                            AlertDialog(
                                onDismissRequest = { showConfirmation = false },
                                title = { Text("Enable Developer Mode?") },
                                text = { Text("Only enable if you know what you are doing. Instability may occur.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        onSettingsChanged(settings.copy(showDebugSettings = true))
                                        showConfirmation = false
                                    }) {
                                        Text("Enable", color = Color(0xFFEF4444))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showConfirmation = false }) {
                                        Text("Cancel")
                                    }
                                },
                                containerColor = Color(0xFF1A1A24),
                                titleContentColor = Color.White,
                                textContentColor = Color.White.copy(alpha = 0.8f)
                            )
                        }

                        SwitchSetting(
                            title = "Developer Mode",
                            subtitle = "Access advanced configuration options",
                            checked = settings.showDebugSettings,
                            onCheckedChange = { enabled ->
                                if (enabled) showConfirmation = true
                                else onSettingsChanged(settings.copy(showDebugSettings = false))
                            }
                        )
                    }

                    if (settings.showDebugSettings) {
                        item {
                            SwitchSetting(
                                title = "Show Debug Logs",
                                subtitle = "Overlay internal logs on lock screen",
                                checked = settings.showDebugLogs,
                                onCheckedChange = { onSettingsChanged(settings.copy(showDebugLogs = it)) }
                            )
                        }

                        item {
                            var editingUrl by remember { mutableStateOf(false) }
                            SettingItem(
                                title = "GitHub Update URL",
                                description = if (settings.githubUpdateUrl.isBlank()) "Not set" else settings.githubUpdateUrl,
                                onClick = { editingUrl = true }
                            )

                            if (editingUrl) {
                                Dialog(onDismissRequest = { editingUrl = false }) {
                                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1A1A24)) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("GitHub Update URL", color = Color.White)
                                            Spacer(Modifier.height(8.dp))
                                            var text by remember { mutableStateOf(settings.githubUpdateUrl) }
                                            OutlinedTextField(
                                                value = text,
                                                onValueChange = { text = it },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                placeholder = { Text("https://github.com/user/repo/actions") }
                                            )
                                            Spacer(Modifier.height(12.dp))
                                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                                TextButton(onClick = { editingUrl = false }) { Text("Cancel") }
                                                TextButton(onClick = { onSettingsChanged(settings.copy(githubUpdateUrl = text)); editingUrl = false }) { Text("Save") }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            var editingToken by remember { mutableStateOf(false) }
                            SettingItem(
                                title = "GitHub Token (Optional)",
                                description = if (settings.githubToken.isBlank()) "Not set" else "********",
                                onClick = { editingToken = true }
                            )

                            if (editingToken) {
                                Dialog(onDismissRequest = { editingToken = false }) {
                                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1A1A24)) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("GitHub Token", color = Color.White)
                                            Spacer(Modifier.height(8.dp))
                                            var text by remember { mutableStateOf(settings.githubToken) }
                                            OutlinedTextField(
                                                value = text,
                                                onValueChange = { text = it },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                                            )
                                            Spacer(Modifier.height(12.dp))
                                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                                TextButton(onClick = { editingToken = false }) { Text("Cancel") }
                                                TextButton(onClick = { onSettingsChanged(settings.copy(githubToken = text)); editingToken = false }) { Text("Save") }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            SwitchSetting(
                                title = "Automatic Updates",
                                subtitle = "Check and install updates from GitHub",
                                checked = settings.autoUpdateEnabled,
                                onCheckedChange = { onSettingsChanged(settings.copy(autoUpdateEnabled = it)) }
                            )
                        }

                        item {
                            SettingItem(
                                title = "Check for Updates",
                                description = "Manually check for updates from GitHub",
                                onClick = {
                                    if (settings.githubUpdateUrl.isBlank()) {
                                        Toast.makeText(context, "Please set GitHub Update URL", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Checking for updates...", Toast.LENGTH_SHORT).show()
                                        scope.launch {
                                            com.quimodotcom.lqlauncher.helpers.AutoUpdater.checkForUpdates(
                                                context = context,
                                                url = settings.githubUpdateUrl,
                                                token = settings.githubToken,
                                                isManual = true
                                            )
                                        }
                                    }
                                }
                            )
                        }

                        item {
                            var showDebugger by remember { mutableStateOf(false) }
                            SettingItem(
                                title = "Art Debugger",
                                description = "Test animated cover fetching",
                                onClick = { showDebugger = true }
                            )

                            if (showDebugger) {
                                AppleMusicDebugDialog(onDismiss = { showDebugger = false })
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF6366F1),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = Color(0xFF6366F1),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val view = LocalView.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1A1A24),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 16.sp)
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onCheckedChange(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF6366F1),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color(0xFF2A2A3A)
                )
            )
        }
    }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true,
    steps: Int = 0
) {
    val view = LocalView.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1A1A24),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    title, 
                    color = if (enabled) Color.White else Color.Gray, 
                    fontSize = 16.sp
                )
                Text(
                    valueLabel, 
                    color = if (enabled) Color(0xFF6366F1) else Color.Gray, 
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = value,
                onValueChange = {
                    val isSmallRange = (valueRange.endInclusive - valueRange.start) < 5f
                    val threshold = if (isSmallRange) 0.1f else 1.0f

                    val oldQuantized = (value / threshold).toInt()
                    val newQuantized = (it / threshold).toInt()

                    if (oldQuantized != newQuantized) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    onValueChange(it)
                },
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF6366F1),
                    activeTrackColor = Color(0xFF6366F1),
                    inactiveTrackColor = Color(0xFF2A2A3A),
                    disabledThumbColor = Color.Gray,
                    disabledActiveTrackColor = Color.Gray,
                    disabledInactiveTrackColor = Color(0xFF2A2A3A)
                )
            )
        }
    }
}

@Composable
private fun ColorPickerSetting(
    title: String,
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    enabled: Boolean = true
) {
    var showPicker by remember { mutableStateOf(false) }
    val view = LocalView.current
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                showPicker = true
            } else Modifier),
        color = if (enabled) Color(0xFF1A1A24) else Color(0xFF1A1A24).copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = if (enabled) Color.White else Color.Gray, fontSize = 16.sp)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (enabled) currentColor else Color.Gray)
                    .border(2.dp, Color.White.copy(alpha = if (enabled) 0.3f else 0.1f), CircleShape)
            )
        }
    }
    
    if (showPicker) {
        GlassSettingsColorPicker(
            currentColor = currentColor,
            onColorSelected = {
                onColorSelected(it)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun GlassSettingsColorPicker(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val presetColors = listOf(
        Color(0xFF6366F1), // Indigo
        Color(0xFF8B5CF6), // Violet
        Color(0xFFA855F7), // Purple
        Color(0xFFEC4899), // Pink
        Color(0xFFEF4444), // Red
        Color(0xFFF97316), // Orange
        Color(0xFFFBBF24), // Amber
        Color(0xFF84CC16), // Lime
        Color(0xFF22C55E), // Green
        Color(0xFF14B8A6), // Teal
        Color(0xFF06B6D4), // Cyan
        Color(0xFF3B82F6), // Blue
        Color(0xFF64748B), // Slate
        Color(0xFF78716C), // Stone
        Color(0xFFFFFFFF), // White
        Color(0xFF000000), // Black
    )
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1A1A24)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Choose Color",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(Modifier.height(16.dp))
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(200.dp)
                ) {
                    items(presetColors) { color ->
                        val isSelected = color == currentColor
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onColorSelected(color)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = if (color == Color.White || color == Color(0xFFFBBF24)) 
                                        Color.Black else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun StyleSelector(
    title: String,
    currentStyle: String,
    options: List<String>,
    onStyleSelected: (String) -> Unit
) {
    val view = LocalView.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1A1A24),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { style ->
                    val isSelected = style == currentStyle
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onStyleSelected(style)
                        },
                        label = { Text(style) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF6366F1),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF2A2A3A),
                            labelColor = Color.Gray
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color.Transparent,
                            selectedBorderColor = Color.Transparent,
                            enabled = true,
                            selected = isSelected
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun IconPackPickerDialog(
    currentPack: String,
    onPackSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = LocalView.current
    var iconPacks by remember { mutableStateOf<List<com.quimodotcom.lqlauncher.helpers.IconPackInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        iconPacks = com.quimodotcom.lqlauncher.helpers.IconPackHelper.getIconPacks(context)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A1A24),
            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Select Icon Pack",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    onPackSelected("")
                                }
                                .background(if (currentPack.isEmpty()) Color(0xFF6366F1).copy(alpha = 0.2f) else Color.Transparent)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Default", color = Color.White)
                            if (currentPack.isEmpty()) {
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Rounded.Check, null, tint = Color(0xFF6366F1))
                            }
                        }
                    }

                    items(iconPacks) { pack ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    onPackSelected(pack.packageName)
                                }
                                .background(if (currentPack == pack.packageName) Color(0xFF6366F1).copy(alpha = 0.2f) else Color.Transparent)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(pack.label, color = Color.White)
                            if (currentPack == pack.packageName) {
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Rounded.Check, null, tint = Color(0xFF6366F1))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun SecretWallpaperPickerDialog(
    currentConfig: LauncherConfig,
    onConfigChanged: (LauncherConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {}

            onConfigChanged(currentConfig.copy(wallpaperSecretUri = uri.toString()))
            Toast.makeText(context, "Secret wallpaper set!", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Secret Wallpaper") },
        text = { Text("Choose an image that will only be shown on your home screen after unlocking.") },
        confirmButton = {
            TextButton(onClick = {
                pickerLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageAndVideo
                    )
                )
            }) {
                Text("Pick Image/Video")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onConfigChanged(currentConfig.copy(wallpaperSecretUri = null))
                onDismiss()
            }) {
                Text("Clear", color = Color.Red)
            }
        },
        containerColor = Color(0xFF1A1A24),
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.8f)
    )
}


@Composable
private fun SettingItem(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            })
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
        
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color.Gray
        )
    }
}
