package com.quimodotcom.lqlauncher.activities

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.foundation.Canvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import android.util.Log
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.quimodotcom.lqlauncher.compose.launcher.*

import com.quimodotcom.lqlauncher.helpers.WeatherRepository
import com.quimodotcom.lqlauncher.helpers.rememberTiltState
import com.quimodotcom.lqlauncher.extensions.config
import java.text.SimpleDateFormat
import java.util.*

/**
 * Full-featured Liquid Glass Launcher with Edit Mode
 *
 * Features:
 * - Customizable home screen grid
 * - Drag & drop app shortcuts, folders, and panels
 * - Resize glass panels
 * - Wallpaper picker
 * - Long-press to enter edit mode
 */
class LiquidGlassLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Mutable state that drives UI and composition
        val wallpaperPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val hasWallpaperPermissionState = mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(this, wallpaperPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                EditableLauncherScreen(
                    hasWallpaperPermission = hasWallpaperPermissionState.value,
                    onWallpaperPermissionGranted = { hasWallpaperPermissionState.value = true }
                )
            }
        }
    }
}

@Composable
private fun EditableLauncherScreen(
    hasWallpaperPermission: Boolean,
    onWallpaperPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Launcher configuration state
    var launcherConfig by remember { mutableStateOf(LauncherConfig()) }
    var editModeState by remember { mutableStateOf(EditModeState()) }
    var availableApps by remember { mutableStateOf<List<AvailableApp>>(emptyList()) }
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    var pendingGridPosition by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showFolderNameDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var isConfigLoaded by remember { mutableStateOf(false) }
    var openedFolder by remember { mutableStateOf<LauncherItem.Folder?>(null) }
    var showAppDrawer by remember { mutableStateOf(false) }
    var isSubjectPositioning by remember { mutableStateOf(false) }

    // Liquid glass settings state
    var glassSettings by remember { mutableStateOf(LiquidGlassSettings()) }
    var isSettingsLoaded by remember { mutableStateOf(false) }

    // Metadata version for icon updates
    var metadataVersion by remember { mutableIntStateOf(0) }

    // Listen for metadata updates
    LaunchedEffect(Unit) {
        AppMetadataRepository.metadataUpdates.collect {
            metadataVersion++
        }
    }

    // App reload trigger
    val reloadApps = remember {
        {
            scope.launch(Dispatchers.IO) {
                availableApps = loadAvailableApps(context)
            }
            Unit
        }
    }

    // Monitor package changes
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                reloadApps()
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // Load saved config, available apps, and glass settings
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // Load glass settings first
            val savedSettings = LiquidGlassSettingsRepository.loadSettings(context)
            if (savedSettings != null) {
                glassSettings = savedSettings
            }
            isSettingsLoaded = true

            // Icon pack system removed — delete any residual icon pack caches
            try {
                val dir = java.io.File(context.filesDir, "iconpack_cache")
                if (dir.exists()) dir.deleteRecursively()
            } catch (e: Exception) {
                if (com.quimodotcom.lqlauncher.BuildConfig.DEBUG) android.util.Log.d("Launcher", "Icon pack cache cleanup failed: ${e.message}")
            }

            // Load available apps
            availableApps = loadAvailableApps(context)

            // Try to load saved config
            val savedConfig = LauncherConfigRepository.loadConfig(context)
            if (savedConfig != null) {
                launcherConfig = savedConfig
            } else if (launcherConfig.items.isEmpty()) {
                // Create default items only if no saved config exists
                launcherConfig = launcherConfig.copy(
                    items = createDefaultItems(availableApps)
                )
            }
            isConfigLoaded = true
        }
    }

    // Save config whenever it changes (with debounce)
    LaunchedEffect(launcherConfig) {
        if (isConfigLoaded) {
            // Debounce saving to avoid excessive writes
            delay(500)
            LauncherConfigRepository.saveConfig(context, launcherConfig)
        }
    }

    // Save glass settings whenever they change (with debounce)
    LaunchedEffect(glassSettings) {
        if (isSettingsLoaded) {
            delay(300)
            LiquidGlassSettingsRepository.saveSettings(context, glassSettings)
        }
    }

    // Calculate cell size
    val cellWidth = remember(gridSize, launcherConfig.gridColumns) {
        if (gridSize.width > 0) gridSize.width.toFloat() / launcherConfig.gridColumns else 0f
    }
    val cellHeight = remember(gridSize, launcherConfig.gridRows) {
        if (gridSize.height > 0) gridSize.height.toFloat() / launcherConfig.gridRows else 0f
    }

    // Parallax state
    val tiltState = rememberTiltState(glassSettings.enableParallax)

    // Wallpaper painter (honour permission)
    val wallpaperPainter = rememberWallpaperPainter(
        customUri = launcherConfig.wallpaperUri,
        useSystem = launcherConfig.useSystemWallpaper,
        permissionGranted = hasWallpaperPermission
    )

    // Backdrop for liquid glass effect
    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current

    // Live Wallpaper Prompt State
    var showWallpaperPrompt by remember { mutableStateOf(false) }

    // Check for permissions and wallpaper status on resume
    DisposableEffect(Unit) {
        val listener = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // Check if notification permission is granted
                val notificationAccessGranted = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

                if (notificationAccessGranted) {
                    // Check if our Live Wallpaper is active
                    val wallpaperManager = android.app.WallpaperManager.getInstance(context)
                    val wallpaperInfo = wallpaperManager.wallpaperInfo
                    val isServiceActive = wallpaperInfo != null &&
                        wallpaperInfo.packageName == context.packageName &&
                        wallpaperInfo.serviceName == com.quimodotcom.lqlauncher.services.LiquidGlassWallpaperService::class.java.name

                    if (!isServiceActive) {
                        showWallpaperPrompt = true
                    }
                }
            }
        }
        val lifecycle = (context as androidx.activity.ComponentActivity).lifecycle
        lifecycle.addObserver(listener)
        onDispose {
            lifecycle.removeObserver(listener)
        }
    }

    if (showWallpaperPrompt) {
        AlertDialog(
            onDismissRequest = { showWallpaperPrompt = false },
            title = { Text("Enable Live Wallpaper") },
            text = { Text("To display lock screen media art, Liquid Glass must be set as your live wallpaper.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWallpaperPrompt = false
                        try {
                            val intent = Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                            intent.putExtra(
                                android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                ComponentName(context, com.quimodotcom.lqlauncher.services.LiquidGlassWallpaperService::class.java)
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open wallpaper settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Set Wallpaper")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWallpaperPrompt = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                detectDragGestures(
                    onDragStart = { total = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        total += dragAmount.y
                        if (total < -200f) {
                            // App Drawer: upward-swipe gesture
                            showAppDrawer = true
                            total = 0f
                        }
                    },
                    onDragEnd = { total = 0f },
                    onDragCancel = { total = 0f }
                )
            }
            .background(Color.Black)
    ) {

        // Wallpaper layer with backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            Image(
                painter = wallpaperPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (glassSettings.enableParallax) {
                            val tilt = tiltState.value
                            val intensity = glassSettings.parallaxIntensity

                            // Dynamic safe scaling to ensure image never goes out of bounds
                            val maxTilt = 5f
                            val factor = 20f
                            val maxShift = maxTilt * factor * intensity

                            val safeScaleX = if (size.width > 0) 1f + (2 * maxShift / size.width) else 1f
                            val safeScaleY = if (size.height > 0) 1f + (2 * maxShift / size.height) else 1f
                            val safeScale = maxOf(1.05f, safeScaleX, safeScaleY)

                            scaleX = safeScale
                            scaleY = safeScale

                            translationX = tilt.x.coerceIn(-maxTilt, maxTilt) * factor * intensity
                            translationY = tilt.y.coerceIn(-maxTilt, maxTilt) * factor * intensity
                        } else {
                            scaleX = 1.05f
                            scaleY = 1.05f
                            translationX = 0f
                            translationY = 0f
                        }
                    }
            )
        }

        // Main content - Grid of items
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .onSizeChanged { gridSize = it }
                // Exclude the bottom handle region from the global gesture detector so the handle can receive input
                .padding(bottom = 96.dp)
                .pointerInput(editModeState.isEnabled) {
                    if (!editModeState.isEnabled) {
                        detectTapGestures(
                            onLongPress = {
                                editModeState = editModeState.copy(isEnabled = true)
                            }
                        )
                    }
                }
        ) {
            // Render grid cells (empty indicators in edit mode)
            if (editModeState.isEnabled && cellWidth > 0 && cellHeight > 0) {
                EmptyGridCells(
                    gridColumns = launcherConfig.gridColumns,
                    gridRows = launcherConfig.gridRows,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    occupiedCells = launcherConfig.items.flatMap { item ->
                        (0 until item.spanX).flatMap { dx ->
                            (0 until item.spanY).map { dy ->
                                (item.gridX + dx) to (item.gridY + dy)
                            }
                        }
                    }.toSet(),
                    onCellClick = { x, y ->
                        pendingGridPosition = x to y
                        editModeState = editModeState.copy(showAppPicker = false, showPanelPicker = false)
                    }
                )
            }

            // Render items in layers: Panels -> Subject -> Apps/Folders

            // 1. Glass Panel Backgrounds (Bottom)
            val glassPanels = remember(launcherConfig.items) {
                launcherConfig.items.filterIsInstance<LauncherItem.GlassPanel>()
            }

            glassPanels.forEach { item ->
                // Calculate position and size manually since we are not using EditModeWrapper here
                val offsetX = with(density) { (item.gridX * cellWidth).toDp() }
                val offsetY = with(density) { (item.gridY * cellHeight).toDp() }
                val width = with(density) { (item.spanX * cellWidth).toDp() }
                val height = with(density) { (item.spanY * cellHeight).toDp() }

                val isSelected = editModeState.selectedItemId == item.id
                val dragTranslation = if (isSelected && editModeState.isDragging) editModeState.dragOffset else Offset.Zero

                // If positioning subject, hide panels
                val alpha = if (isSubjectPositioning) 0f else 1f

                Box(
                    modifier = Modifier
                        .offset(x = offsetX, y = offsetY)
                        .graphicsLayer {
                            translationX = dragTranslation.x
                            translationY = dragTranslation.y
                            this.alpha = alpha
                        }
                        .size(width, height)
                        .padding(4.dp)
                ) {
                    GlassPanelBackground(
                        item = item,
                        backdrop = backdrop,
                        glassSettings = glassSettings,
                        isEditMode = editModeState.isEnabled
                    )
                }
            }

            // 2. Subject Layer (Middle)
            if (launcherConfig.wallpaperSubjectUri != null) {
                if (launcherConfig.subjectMatchWallpaper) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(launcherConfig.wallpaperSubjectUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (glassSettings.enableParallax) {
                                    val tilt = tiltState.value
                                    val intensity = glassSettings.parallaxIntensity

                                    // Dynamic safe scaling
                                    val maxTilt = 5f
                                    val factor = 35f
                                    val maxShift = maxTilt * factor * intensity

                                    val safeScaleX = if (size.width > 0) 1f + (2 * maxShift / size.width) else 1f
                                    val safeScaleY = if (size.height > 0) 1f + (2 * maxShift / size.height) else 1f
                                    val safeScale = maxOf(1.05f, safeScaleX, safeScaleY)

                                    scaleX = safeScale
                                    scaleY = safeScale

                                    translationX = tilt.x.coerceIn(-maxTilt, maxTilt) * factor * intensity
                                    translationY = tilt.y.coerceIn(-maxTilt, maxTilt) * factor * intensity
                                } else {
                                    scaleX = 1.05f
                                    scaleY = 1.05f
                                    translationX = 0f
                                    translationY = 0f
                                }
                            }
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(launcherConfig.wallpaperSubjectUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val tilt = tiltState.value
                                val intensity = glassSettings.parallaxIntensity
                                scaleX = launcherConfig.subjectScale
                                scaleY = launcherConfig.subjectScale
                                translationX = (launcherConfig.subjectOffsetX * density.density) + (tilt.x * 35f * intensity)
                                translationY = (launcherConfig.subjectOffsetY * density.density) + (tilt.y * 35f * intensity)
                            }
                    )
                }
            }

            // 3. Glass Panel Content + Apps/Folders (Top)
            // We reuse the standard LauncherItemView logic but split content
            // NOTE: For Glass Panels, we now render CONTENT only. For Apps, we render FULL.

            launcherConfig.items.forEach { item ->
                val isSelected = editModeState.selectedItemId == item.id
                val alpha = if (isSubjectPositioning) 0f else 1f

                val offsetX = with(density) { (item.gridX * cellWidth).toDp() }
                val offsetY = with(density) { (item.gridY * cellHeight).toDp() }

                val width = with(density) {
                    if (item is LauncherItem.AppShortcut) (item.spanX * cellWidth).toDp()
                    else (item.spanX * cellWidth).toDp()
                }
                val height = with(density) {
                    if (item is LauncherItem.AppShortcut) (item.spanY * cellWidth).toDp()
                    else (item.spanY * cellHeight).toDp()
                }

                EditModeWrapper(
                    item = item,
                    isSelected = isSelected,
                    isEditMode = editModeState.isEnabled,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    gridColumns = launcherConfig.gridColumns,
                    gridRows = launcherConfig.gridRows,
                    dragOffset = if (isSelected) editModeState.dragOffset else Offset.Zero,
                    onDragOffsetChange = { newOffset ->
                        editModeState = editModeState.copy(dragOffset = newOffset)
                    },
                    onDragStart = {
                        editModeState = editModeState.copy(isDragging = true)
                    },
                    onDragEnd = {
                        editModeState = editModeState.copy(isDragging = false)
                    },
                    onSelect = {
                        editModeState = editModeState.copy(selectedItemId = item.id)
                    },
                    onMove = { newX, newY ->
                        // Folder drop logic
                        if (item is LauncherItem.AppShortcut) {
                            val targetFolder = launcherConfig.items.filterIsInstance<LauncherItem.Folder>().find { folder ->
                                newX >= folder.gridX && newX < folder.gridX + folder.spanX &&
                                newY >= folder.gridY && newY < folder.gridY + folder.spanY
                            }
                            if (targetFolder != null) {
                                launcherConfig = launcherConfig.copy(
                                    items = launcherConfig.items.mapNotNull { existingItem ->
                                        when {
                                            existingItem.id == targetFolder.id && existingItem is LauncherItem.Folder -> {
                                                existingItem.copy(apps = existingItem.apps + item.packageName)
                                            }
                                            existingItem.id == item.id -> null
                                            else -> existingItem
                                        }
                                    }
                                )
                                editModeState = editModeState.copy(selectedItemId = null)
                                return@EditModeWrapper
                            }
                        }

                        launcherConfig = launcherConfig.copy(
                            items = launcherConfig.items.map {
                                if (it.id == item.id) {
                                    when (it) {
                                        is LauncherItem.AppShortcut -> it.copy(gridX = newX, gridY = newY)
                                        is LauncherItem.GlassPanel -> it.copy(gridX = newX, gridY = newY)
                                        is LauncherItem.Folder -> it.copy(gridX = newX, gridY = newY)
                                    }
                                } else it
                            }
                        )
                    },
                    onResize = { newSpanX, newSpanY ->
                        launcherConfig = launcherConfig.copy(
                            items = launcherConfig.items.map {
                                if (it.id == item.id && it is LauncherItem.GlassPanel) {
                                    it.copy(spanX = newSpanX, spanY = newSpanY)
                                } else it
                            }
                        )
                    },
                    onDelete = {
                        launcherConfig = launcherConfig.copy(
                            items = launcherConfig.items.filter { it.id != item.id }
                        )
                        editModeState = editModeState.copy(selectedItemId = null)
                    },
                    modifier = Modifier
                        .offset(x = offsetX, y = offsetY)
                        .size(width = width, height = height)
                        .padding(4.dp)
                        .graphicsLayer { this.alpha = alpha }
                ) {
                    when (item) {
                        is LauncherItem.AppShortcut -> AppShortcutView(
                            item = item,
                            backdrop = backdrop,
                            glassSettings = glassSettings,
                            metadataVersion = metadataVersion,
                            context = context,
                            isEditMode = editModeState.isEnabled,
                            onLaunch = { launchApp(context, item.packageName) },
                            showLabel = glassSettings.showAppLabels,
                            cellWidth = cellWidth
                        )
                        is LauncherItem.GlassPanel -> GlassPanelContent(
                            item = item,
                            glassSettings = glassSettings,
                            isEditMode = editModeState.isEnabled
                        )
                        is LauncherItem.Folder -> FolderView(
                            item = item,
                            backdrop = backdrop,
                            glassSettings = glassSettings,
                            context = context,
                            isEditMode = editModeState.isEnabled,
                            onOpenFolder = { openedFolder = item },
                            cellWidth = cellWidth
                        )
                    }
                }
            }

            // Edit mode hint
            AnimatedVisibility(
                visible = !editModeState.isEnabled && launcherConfig.items.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                EditModeHint(isVisible = true)
            }
        }

        // App Drawer Overlay
        AnimatedVisibility(
            visible = showAppDrawer,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            AppDrawer(
                apps = availableApps,
                backdrop = backdrop,
                glassSettings = glassSettings,
                onAppClick = { pkg ->
                    launchApp(context, pkg)
                    showAppDrawer = false
                },
                onClose = { showAppDrawer = false },
                onRefreshApps = reloadApps
            )
        }

        BackHandler(enabled = showAppDrawer) {
            showAppDrawer = false
        }

        // Edit mode toolbar
        EditModeToolbar(
            backdrop = backdrop,
            isEditMode = editModeState.isEnabled,
            onAddApp = { editModeState = editModeState.copy(showAppPicker = true) },
            onAddPanel = { editModeState = editModeState.copy(showPanelPicker = true) },
            onAddFolder = { showFolderNameDialog = true },
            onChangeWallpaper = { editModeState = editModeState.copy(showWallpaperPicker = true) },
            onOpenSettings = { showSettings = true },
            onExitEditMode = {
                editModeState = EditModeState()
            },
            glassSettings = glassSettings,
            modifier = Modifier.align(Alignment.BottomCenter)
        )


    }

    // Dialogs


    if (pendingGridPosition != null) {
        AddItemMenu(
            gridX = pendingGridPosition!!.first,
            gridY = pendingGridPosition!!.second,
            onAddApp = {
                editModeState = editModeState.copy(showAppPicker = true)
            },
            onAddPanel = {
                val (x, y) = pendingGridPosition!!
                launcherConfig = launcherConfig.copy(
                    items = launcherConfig.items + LauncherItem.GlassPanel(
                        gridX = x,
                        gridY = y,
                        spanX = 2,
                        spanY = 2,
                        panelType = PanelType.CLOCK
                    )
                )
                pendingGridPosition = null
            },
            onAddFolder = {
                showFolderNameDialog = true
            },
            onDismiss = { pendingGridPosition = null }
        )
    }

    if (editModeState.showAppPicker) {
        AppPickerDialog(
            availableApps = availableApps,
            onAppSelected = { app ->
                val pos = pendingGridPosition ?: findEmptyCell(launcherConfig)
                launcherConfig = launcherConfig.copy(
                    items = launcherConfig.items + LauncherItem.AppShortcut(
                        gridX = pos.first,
                        gridY = pos.second,
                        packageName = app.packageName,
                        label = app.label
                    )
                )
                editModeState = editModeState.copy(showAppPicker = false)
                pendingGridPosition = null
            },
            onDismiss = {
                editModeState = editModeState.copy(showAppPicker = false)
                pendingGridPosition = null
            }
        )
    }

    if (editModeState.showPanelPicker) {
        PanelPickerDialog(
            onPanelTypeSelected = { panelType ->
                val pos = pendingGridPosition ?: findEmptyCell(launcherConfig)
                launcherConfig = launcherConfig.copy(
                    items = launcherConfig.items + LauncherItem.GlassPanel(
                        gridX = pos.first,
                        gridY = pos.second,
                        spanX = 2,
                        spanY = 2,
                        panelType = panelType
                    )
                )
                editModeState = editModeState.copy(showPanelPicker = false)
                pendingGridPosition = null
            },
            onDismiss = {
                editModeState = editModeState.copy(showPanelPicker = false)
            }
        )
    }

    if (showFolderNameDialog) {
        FolderNameDialog(
            onConfirm = { name ->
                val pos = pendingGridPosition ?: findEmptyCell(launcherConfig)
                launcherConfig = launcherConfig.copy(
                    items = launcherConfig.items + LauncherItem.Folder(
                        gridX = pos.first,
                        gridY = pos.second,
                        name = name
                    )
                )
                showFolderNameDialog = false
                pendingGridPosition = null
            },
            onDismiss = {
                showFolderNameDialog = false
                pendingGridPosition = null
            }
        )
    }

    if (editModeState.showWallpaperPicker) {
        WallpaperPickerDialog(
            currentWallpaperUri = launcherConfig.wallpaperUri,
            useSystemWallpaper = launcherConfig.useSystemWallpaper,
            currentSubjectUri = launcherConfig.wallpaperSubjectUri,
            subjectMatchWallpaper = launcherConfig.subjectMatchWallpaper,
            subjectScale = launcherConfig.subjectScale,
            subjectOffsetX = launcherConfig.subjectOffsetX,
            subjectOffsetY = launcherConfig.subjectOffsetY,
            onWallpaperPermissionGranted = onWallpaperPermissionGranted,
            onWallpaperSelected = { uri ->
                launcherConfig = if (uri == null) {
                    launcherConfig.copy(useSystemWallpaper = true, wallpaperUri = null)
                } else {
                    // Check if LiquidGlassWallpaperService is active
                    val wallpaperManager = android.app.WallpaperManager.getInstance(context)
                    val wallpaperInfo = wallpaperManager.wallpaperInfo
                    val isServiceActive = wallpaperInfo != null &&
                        wallpaperInfo.packageName == context.packageName &&
                        wallpaperInfo.serviceName == com.quimodotcom.lqlauncher.services.LiquidGlassWallpaperService::class.java.name

                    // Persist chosen wallpaper for launcher
                    val newConfig = launcherConfig.copy(useSystemWallpaper = false, wallpaperUri = uri)

                    if (!isServiceActive) {
                        // Only set system wallpaper if our service is NOT active (fallback behavior)
                        // If service IS active, updating config is enough (handled by service watching config changes)
                        try {
                            val parsedUri = android.net.Uri.parse(uri)
                            val wm = android.app.WallpaperManager.getInstance(context)
                            val dm = context.resources.displayMetrics
                            val targetW = dm.widthPixels
                            val targetH = dm.heightPixels

                            scope.launch(Dispatchers.IO) {
                                try {
                                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    context.contentResolver.openInputStream(parsedUri)?.use { input ->
                                        android.graphics.BitmapFactory.decodeStream(input, null, bounds)
                                    }

                                    val origW = bounds.outWidth.coerceAtLeast(1)
                                    val origH = bounds.outHeight.coerceAtLeast(1)

                                    val opts = android.graphics.BitmapFactory.Options().apply {
                                        inSampleSize = calculateInSampleSize(origW, origH, targetW, targetH)
                                        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                                    }

                                    val decoded = context.contentResolver.openInputStream(parsedUri)?.use { input ->
                                        android.graphics.BitmapFactory.decodeStream(input, null, opts)
                                    }

                                    if (decoded != null) {
                                        val scale = maxOf(targetW.toFloat() / decoded.width.toFloat(), targetH.toFloat() / decoded.height.toFloat())
                                        val scaledW = (decoded.width * scale).toInt()
                                        val scaledH = (decoded.height * scale).toInt()

                                        val resized = try {
                                            if (decoded.width == scaledW && decoded.height == scaledH) decoded else android.graphics.Bitmap.createScaledBitmap(decoded, scaledW, scaledH, true)
                                        } catch (e: Throwable) {
                                            android.util.Log.w("LiquidGlassLauncher", "Bitmap scaling failed, using decoded bitmap: ${e.message}")
                                            decoded
                                        }

                                        val offsetX = ((resized.width - targetW) / 2).coerceAtLeast(0)
                                        val offsetY = ((resized.height - targetH) / 2).coerceAtLeast(0)
                                        val cropW = minOf(targetW, resized.width - offsetX)
                                        val cropH = minOf(targetH, resized.height - offsetY)

                                        val finalBitmap = try {
                                            android.graphics.Bitmap.createBitmap(resized, offsetX, offsetY, cropW, cropH)
                                        } catch (e: Throwable) {
                                            android.util.Log.w("LiquidGlassLauncher", "Center-crop failed, using resized bitmap: ${e.message}")
                                            resized
                                        }

                                        try {
                                            wm.setBitmap(finalBitmap)
                                            android.util.Log.i("LiquidGlassLauncher", "Applied wallpaper bitmap ${finalBitmap.width}x${finalBitmap.height} (target ${targetW}x${targetH}) from $uri")
                                        } catch (se: SecurityException) {
                                            android.util.Log.w("LiquidGlassLauncher", "SecurityException setting wallpaper bitmap: ${se.message}")
                                            try {
                                                context.contentResolver.openInputStream(parsedUri)?.use { fs -> wm.setStream(fs) }
                                            } catch (e2: Exception) {
                                                android.util.Log.e("LiquidGlassLauncher", "Fallback setStream failed", e2)
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("LiquidGlassLauncher", "Failed to set wallpaper bitmap", e)
                                            try {
                                                context.contentResolver.openInputStream(parsedUri)?.use { fs -> wm.setStream(fs) }
                                            } catch (e2: Exception) {
                                                android.util.Log.e("LiquidGlassLauncher", "Fallback setStream failed", e2)
                                            }
                                        }
                                    } else {
                                        android.util.Log.w("LiquidGlassLauncher", "Decoded bitmap was null, falling back to setStream for $uri")
                                        try {
                                            context.contentResolver.openInputStream(parsedUri)?.use { fs -> wm.setStream(fs) }
                                        } catch (e: Exception) {
                                            android.util.Log.e("LiquidGlassLauncher", "Fallback setStream failed", e)
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("LiquidGlassLauncher", "Error applying wallpaper", e)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                         // Notify service of config change immediately
                         scope.launch(Dispatchers.IO) {
                             // Save immediately to ensure service sees it
                             LauncherConfigRepository.saveConfig(context, newConfig)
                             // Also trigger config change broadcast just in case saveConfig doesn't do it (Wait, saveConfig DOESN'T do it for LauncherConfig, only LiquidGlassSettings!)
                             // LiquidGlassSettingsRepository.saveSettings sends broadcast.
                             // We should send broadcast here too or update LauncherConfigRepository to send it.
                             // But wait, LiquidGlassWallpaperService listens to "com.quimodotcom.lqlauncher.ACTION_CONFIG_CHANGED".
                             // Let's manually send it here.
                             context.sendBroadcast(Intent("com.quimodotcom.lqlauncher.ACTION_CONFIG_CHANGED"))
                         }
                    }
                    newConfig
                }
            },
            onSubjectSelected = { uri ->
                launcherConfig = launcherConfig.copy(wallpaperSubjectUri = uri)
            },
            onSubjectConfigChanged = { match, scale, offX, offY ->
                launcherConfig = launcherConfig.copy(
                    subjectMatchWallpaper = match,
                    subjectScale = scale,
                    subjectOffsetX = offX,
                    subjectOffsetY = offY
                )
            },
            onInteractionStart = { isSubjectPositioning = true },
            onInteractionEnd = { isSubjectPositioning = false },
            onDismiss = {
                editModeState = editModeState.copy(showWallpaperPicker = false)
            }
        )
    }

    // Liquid Glass Settings dialog
    if (showSettings) {
        LiquidGlassSettingsScreen(
            settings = glassSettings,
            onSettingsChanged = { newSettings ->
                glassSettings = newSettings
                // Apply grid settings to launcher config
                launcherConfig = launcherConfig.copy(
                    gridColumns = newSettings.gridColumns,
                    gridRows = newSettings.gridRows
                )
            },
            onDismiss = { showSettings = false }
        )
    }

    // Opened folder dialog
    openedFolder?.let { folder ->
        OpenedFolderDialog(
            folder = folder,
            backdrop = backdrop,
            glassSettings = glassSettings,
            context = context,
            onLaunchApp = { packageName ->
                launchApp(context, packageName)
                openedFolder = null
            },
            onRemoveApp = { packageName ->
                launcherConfig = launcherConfig.copy(
                    items = launcherConfig.items.map { item ->
                        if (item.id == folder.id && item is LauncherItem.Folder) {
                            val newApps = item.apps - packageName
                            // Also update the currently opened folder state so the dialog refreshes
                            val newFolder = item.copy(apps = newApps)
                            openedFolder = newFolder
                            newFolder
                        } else {
                            item
                        }
                    }
                )
                Toast.makeText(context, "Removed from folder", Toast.LENGTH_SHORT).show()
            },
            onSortApps = {
                launcherConfig = launcherConfig.copy(
                    items = launcherConfig.items.map { item ->
                        if (item.id == folder.id && item is LauncherItem.Folder) {
                            val sortedApps = item.apps.sortedBy { pkg ->
                                availableApps.find { it.packageName == pkg }?.label?.lowercase() ?: pkg
                            }
                            val newFolder = item.copy(apps = sortedApps)
                            openedFolder = newFolder
                            newFolder
                        } else {
                            item
                        }
                    }
                )
            },
            onDismiss = { openedFolder = null }
        )
    }
}

@Composable
private fun LauncherItemView(
    item: LauncherItem,
    isEditMode: Boolean,
    isSelected: Boolean,
    backdrop: LayerBackdrop,
    glassSettings: LiquidGlassSettings,
    metadataVersion: Int,
    cellWidth: Float,
    cellHeight: Float,
    gridColumns: Int,
    gridRows: Int,
    allItems: List<LauncherItem>,
    context: Context,
    onSelect: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onResize: (Int, Int) -> Unit,
    onDelete: () -> Unit,
    onAddToFolder: (folderId: String, packageName: String) -> Unit,
    onOpenFolder: (LauncherItem.Folder) -> Unit,
    onLaunch: (String) -> Unit
) {
    val density = LocalDensity.current

    val offsetX = with(density) { (item.gridX * cellWidth).toDp() }
    val offsetY = with(density) { (item.gridY * cellHeight).toDp() }

    // For apps: use 1:1 square cells (use cellWidth for both dimensions)
    // For panels/folders: use flexible rectangular cells
    val width = with(density) {
        if (item is LauncherItem.AppShortcut) {
            (item.spanX * cellWidth).toDp()
        } else {
            (item.spanX * cellWidth).toDp()
        }
    }
    val height = with(density) {
        if (item is LauncherItem.AppShortcut) {
            (item.spanY * cellWidth).toDp()  // Use cellWidth for 1:1 ratio
        } else {
            (item.spanY * cellHeight).toDp()  // Use cellHeight for panels
        }
    }

    EditModeWrapper(
        item = item,
        isSelected = isSelected,
        isEditMode = isEditMode,
        cellWidth = cellWidth,
        cellHeight = cellHeight,
        gridColumns = gridColumns,
        gridRows = gridRows,
        onSelect = onSelect,
        onMove = { newX, newY ->
            // Check if dropping onto a folder
            if (item is LauncherItem.AppShortcut) {
                val targetFolder = allItems.filterIsInstance<LauncherItem.Folder>().find { folder ->
                    newX >= folder.gridX && newX < folder.gridX + folder.spanX &&
                    newY >= folder.gridY && newY < folder.gridY + folder.spanY
                }
                if (targetFolder != null) {
                    onAddToFolder(targetFolder.id, item.packageName)
                    return@EditModeWrapper
                }
            }
            onMove(newX, newY)
        },
        onResize = onResize,
        onDelete = onDelete,
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .size(width = width, height = height)
            .padding(4.dp)
    ) {
        when (item) {
            is LauncherItem.AppShortcut -> AppShortcutView(
                item = item,
                backdrop = backdrop,
                glassSettings = glassSettings,
                metadataVersion = metadataVersion,
                context = context,
                isEditMode = isEditMode,
                onLaunch = onLaunch,
                showLabel = glassSettings.showAppLabels,
                cellWidth = cellWidth
            )
            is LauncherItem.GlassPanel -> {
                Box {
                    GlassPanelBackground(
                        item = item,
                        backdrop = backdrop,
                        glassSettings = glassSettings,
                        isEditMode = isEditMode
                    )
                    GlassPanelContent(
                        item = item,
                        glassSettings = glassSettings,
                        isEditMode = isEditMode
                    )
                }
            }
            is LauncherItem.Folder -> FolderView(
                item = item,
                backdrop = backdrop,
                glassSettings = glassSettings,
                context = context,
                isEditMode = isEditMode,
                onOpenFolder = { onOpenFolder(item) },
                cellWidth = cellWidth
            )
        }
    }
}

@Composable
private fun AppShortcutView(
    item: LauncherItem.AppShortcut,
    backdrop: LayerBackdrop,
    glassSettings: LiquidGlassSettings,
    metadataVersion: Int,
    context: Context,
    isEditMode: Boolean,
    onLaunch: (String) -> Unit,
    showLabel: Boolean = true,
    cellWidth: Float = 0f
) {
    val cornerRadius = glassSettings.iconCornerRadius.dp
    val tintColor = Color(glassSettings.panelTintColor)
    val density = LocalDensity.current
    val scaledSize = with(density) { (cellWidth * glassSettings.appTileScale).toDp() }

    // Use produceState to load icon async and support updates from metadata
    val iconDrawableState = produceState<android.graphics.drawable.Drawable?>(initialValue = null, item.packageName, glassSettings.iconPackPackageName, metadataVersion) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Check Metadata Overrides
                val metadata = com.quimodotcom.lqlauncher.compose.launcher.AppMetadataRepository.getMetadata(context, item.packageName)
                if (metadata?.customIconUri != null) {
                    val d = loadDrawableFromUri(context, metadata.customIconUri)
                    if (d != null) {
                        value = d
                        return@withContext
                    }
                }

                // 2. Resolve Component
                val pm = context.packageManager
                val launchIntent = pm.getLaunchIntentForPackage(item.packageName)
                var component: ComponentName? = launchIntent?.component

                if (component == null) {
                    val queryIntent = Intent(Intent.ACTION_MAIN, null).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        setPackage(item.packageName)
                    }
                    val resolve = pm.queryIntentActivities(queryIntent, 0).firstOrNull()
                    component = resolve?.activityInfo?.let { ai -> ComponentName(ai.packageName, ai.name) }
                }

                // 3. Check Icon Pack
                if (glassSettings.iconPackPackageName.isNotEmpty() && component != null) {
                     val d = com.quimodotcom.lqlauncher.helpers.IconPackHelper.getIconDrawable(context, glassSettings.iconPackPackageName, component)
                     if (d != null) {
                         value = d
                         return@withContext
                     }
                }

                // 4. Default App Icon
                value = component?.let { comp -> pm.getActivityIcon(comp) } ?: pm.getApplicationIcon(item.packageName)
            } catch (e: Exception) {
                value = null
            }
        }
    }
    val iconDrawable = iconDrawableState.value
    val view = LocalView.current

    Column(
        modifier = Modifier
            .size(scaledSize)
            .clip(RoundedCornerShape(cornerRadius))
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(cornerRadius) },
                effects = {
                    if (glassSettings.vibrancyEnabled) vibrancy()
                    if (glassSettings.blurEnabled) blur(glassSettings.blurRadius.dp.toPx())
                    if (glassSettings.lensEnabled) lens(
                        refractionHeight = glassSettings.refractionHeight.dp.toPx(),
                        refractionAmount = glassSettings.refractionAmount.dp.toPx(),
                        chromaticAberration = glassSettings.chromaticAberration
                    )
                },
                onDrawSurface = {
                    drawRect(tintColor.copy(alpha = glassSettings.iconBackgroundAlpha))
                }
            )
            .pointerInput(isEditMode) {
                if (!isEditMode) {
                    detectTapGestures(
                        onTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onLaunch(item.packageName)
                        }
                    )
                }
            }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App icon - fills most of the tile
        Box(
            modifier = Modifier
                .fillMaxSize(0.85f)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (iconDrawable != null) {
                Image(
                    bitmap = iconDrawable.toBitmap(96, 96).asImageBitmap(),
                    contentDescription = item.label,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Rounded.Android,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize(0.65f)
                )
            }
        }

        if (showLabel) {
            Spacer(Modifier.height(4.dp))

            // App label
            Text(
                text = item.label,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GlassPanelBackground(
    item: LauncherItem.GlassPanel,
    backdrop: LayerBackdrop,
    glassSettings: LiquidGlassSettings,
    isEditMode: Boolean
) {
    val panelTintColor = Color(item.tintColor)
    val blurRadius = glassSettings.blurRadius.dp
    val cornerRadius = glassSettings.panelCornerRadius.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius))
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(cornerRadius) },
                effects = {
                    if (glassSettings.vibrancyEnabled) vibrancy()
                    if (glassSettings.blurEnabled) blur(blurRadius.toPx())
                    if (glassSettings.lensEnabled) lens(
                        refractionHeight = glassSettings.refractionHeight.dp.toPx(),
                        refractionAmount = glassSettings.refractionAmount.dp.toPx(),
                        chromaticAberration = glassSettings.chromaticAberration
                    )
                },
                onDrawSurface = {
                    // Lower alpha so grid shows through in edit mode
                    val alpha = if (isEditMode) 0.05f else glassSettings.panelBackgroundAlpha
                    drawRect(panelTintColor.copy(alpha = alpha))
                }
            )
    )
}

@Composable
private fun GlassPanelContent(
    item: LauncherItem.GlassPanel,
    glassSettings: LiquidGlassSettings,
    isEditMode: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (item.panelType == PanelType.SEARCH) 8.dp else 16.dp),
        contentAlignment = Alignment.Center
    ) {
        when (item.panelType) {
            PanelType.CLOCK -> ClockPanelContent(glassSettings)
            PanelType.WEATHER -> WeatherPanelContent(glassSettings)
            PanelType.QUICK_SETTINGS -> QuickSettingsPanelContent()
            PanelType.BATTERY -> BatteryPanelContent(glassSettings)
            PanelType.SEARCH -> BrowserSearchPanelContent(isEditMode = isEditMode)
            PanelType.EMPTY, PanelType.CUSTOM -> {
                if (item.title.isNotEmpty()) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ClockPanelContent(glassSettings: LiquidGlassSettings) {
    if (glassSettings.clockStyle == "Cyberpunk") {
        CyberpunkClock()
        return
    }

    // No specific interaction for clock yet, so no haptics needed here unless we add click-to-open-alarm

    // smooth ticking clock with small breathing animation
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }

    // breathing animation for the panel
    val infinite = rememberInfiniteTransition()
    val scale by infinite.animateFloat(
        initialValue = 0.995f,
        targetValue = 1.005f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )

    // compute analog hand rotations
    val cal = Calendar.getInstance().apply { timeInMillis = currentTime }
    val hours = cal.get(Calendar.HOUR).toFloat() + cal.get(Calendar.MINUTE) / 60f
    val minutes = cal.get(Calendar.MINUTE).toFloat() + cal.get(Calendar.SECOND) / 60f
    val seconds = cal.get(Calendar.SECOND).toFloat() + (cal.get(Calendar.MILLISECOND).toFloat() / 1000f)

    // pick face from user config
    val cfg = LocalContext.current.config
    when (cfg.clockFace) {
        1 -> {
            // Minimal: big digital time, small date below
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            ) {
                Text(
                    text = timeFormat.format(Date(currentTime)),
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Light
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = dateFormat.format(Date(currentTime)),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
        2 -> {
            // Modern: circular progress representing minutes + digital time
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(80.dp)) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val radius = size.minDimension / 2f

                        // background ring
                        drawArc(Color.White.copy(alpha = 0.06f), startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = Offset(cx - radius, cy - radius), size = Size(radius * 2f, radius * 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))

                        // minute progress
                        val minuteProgress = (minutes / 60f) * 360f
                        drawArc(Color(0xFF60A5FA), startAngle = -90f, sweepAngle = minuteProgress, useCenter = false, topLeft = Offset(cx - radius, cy - radius), size = Size(radius * 2f, radius * 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f, cap = StrokeCap.Round))
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = timeFormat.format(Date(currentTime)),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        else -> {
            // Classic (existing implementation)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // analog clock
                    Canvas(modifier = Modifier.size(80.dp)) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val radius = size.minDimension / 2f

                        // face
                        drawCircle(Color.White.copy(alpha = 0.06f), radius = radius)

                        // minute ticks
                        for (i in 0 until 60) {
                            val angle = i * 6f - 90f
                            val rad = Math.toRadians(angle.toDouble()).toFloat()
                            val inner = if (i % 5 == 0) radius * 0.78f else radius * 0.86f
                            val outer = radius * 0.95f
                            val x1 = cx + inner * kotlin.math.cos(rad)
                            val y1 = cy + inner * kotlin.math.sin(rad)
                            val x2 = cx + outer * kotlin.math.cos(rad)
                            val y2 = cy + outer * kotlin.math.sin(rad)
                            drawLine(Color.White.copy(alpha = if (i % 5 == 0) 0.9f else 0.25f), Offset(x1, y1), Offset(x2, y2), strokeWidth = if (i % 5 == 0) 2f else 1f)
                        }

                        // hands - hour
                        val hourAngle = (hours / 12f) * 360f - 90f
                        val hourRad = Math.toRadians(hourAngle.toDouble()).toFloat()
                        drawLine(Color.White, Offset(cx, cy), Offset(cx + radius * 0.45f * kotlin.math.cos(hourRad), cy + radius * 0.45f * kotlin.math.sin(hourRad)), strokeWidth = 4f, cap = StrokeCap.Round)

                        // minute
                        val minAngle = (minutes / 60f) * 360f - 90f
                        val minRad = Math.toRadians(minAngle.toDouble()).toFloat()
                        drawLine(Color.White, Offset(cx, cy), Offset(cx + radius * 0.65f * kotlin.math.cos(minRad), cy + radius * 0.65f * kotlin.math.sin(minRad)), strokeWidth = 2.5f, cap = StrokeCap.Round)

                        // seconds - smooth sweep
                        val secAngle = (seconds / 60f) * 360f - 90f
                        val secRad = Math.toRadians(secAngle.toDouble()).toFloat()
                        drawLine(Color(0xFFFF6B6B), Offset(cx, cy), Offset(cx + radius * 0.78f * kotlin.math.cos(secRad), cy + radius * 0.78f * kotlin.math.sin(secRad)), strokeWidth = 1.6f, cap = StrokeCap.Round)

                        // center dot
                        drawCircle(Color.White, radius = 4f, center = Offset(cx, cy))
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = timeFormat.format(Date(currentTime)),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light
                )
                Text(
                    text = dateFormat.format(Date(currentTime)),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun WeatherPanelContent(glassSettings: LiquidGlassSettings) {
    // Try real OpenWeatherMap integration when API key is present; fallback to sample data
    data class Forecast(val dayLabel: String, val temp: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

    // state for fetched data
    var fetched by remember { mutableStateOf<com.quimodotcom.lqlauncher.helpers.WeatherRepository.WeatherData?>(null) }
    // default coords (San Francisco) — later we can add a settings entry for location
    val defaultLat = 37.7749
    val defaultLon = -122.4194

    // Use key from LiquidGlassSettings if available, otherwise fallback to old config
    val apiKey = glassSettings.openWeatherApiKey.ifBlank { LocalContext.current.config.openWeatherApiKey }
    val units = if (glassSettings.weatherUnit == "C") "metric" else "imperial"

    LaunchedEffect(apiKey, units) {
        // Try fetching in background if API key is configured
        if (apiKey.isNotBlank()) {
            withContext(Dispatchers.IO) {
                val data = WeatherRepository.fetchForecast(defaultLat, defaultLon, units = units, apiKey = apiKey)
                if (data != null) {
                    fetched = data
                }
            }
        }
    }

    // choose source for display
    val sampleForecasts = listOf(
        Forecast("Now", "72°F", Icons.Rounded.Cloud),
        Forecast("+1h", "73°F", Icons.Rounded.CloudQueue),
        Forecast("+3h", "70°F", Icons.Rounded.WbSunny),
        Forecast("+6h", "68°F", Icons.Rounded.Cloud),
        Forecast("+12h", "65°F", Icons.Rounded.NightsStay)
    )

    fun mapIconCode(code: String): androidx.compose.ui.graphics.vector.ImageVector = when {
        code.startsWith("01") -> Icons.Rounded.WbSunny
        code.startsWith("02") || code.startsWith("03") || code.startsWith("04") -> Icons.Rounded.CloudQueue
        code.startsWith("09") || code.startsWith("10") -> Icons.Rounded.Grain
        code.startsWith("11") -> Icons.Rounded.FlashOn
        code.startsWith("13") -> Icons.Rounded.AcUnit
        code.startsWith("50") -> Icons.Rounded.Cloud
        else -> Icons.Rounded.Cloud
    }

    val forecasts = remember(fetched) {
        if (fetched != null) {
            val d = fetched!!
            val list = d.hourly.map { f ->
                Forecast(f.label, f.temp, mapIconCode(f.iconCode))
            }
            if (list.isEmpty()) sampleForecasts else list
        } else sampleForecasts
    }

    // Auto-cycle index
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000L)
            index = (index + 1) % forecasts.size
        }
    }

    if (glassSettings.weatherStyle == "Cyberpunk") {
        val currentTemp = fetched?.currentTemp ?: forecasts[index].temp
        val currentIcon = if (fetched != null) mapIconCode(fetched!!.currentIcon) else forecasts[index].icon
        val desc = if (fetched != null) "Cloudy" else "Partly Cloudy" // Placeholder description
        CyberpunkWeather(currentTemp, currentIcon, desc)
        return
    }

    // animate offset via animatable
    val xOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    val widthPx = with(LocalDensity.current) { 160.dp.toPx() }

    LaunchedEffect(index) {
        xOffset.animateTo(-index * widthPx, animationSpec = tween(600, easing = FastOutSlowInEasing))
    }

    // Ensure everything is centered in the tile
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            // Current weather main column
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 6.dp)) {
                val currentIcon = if (fetched != null) mapIconCode(fetched!!.currentIcon) else forecasts[index].icon
                val currentTemp = fetched?.currentTemp ?: forecasts[index].temp

                Icon(
                    imageVector = currentIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(text = currentTemp, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Light)
                Text(text = when (currentIcon) {
                    Icons.Rounded.WbSunny -> "Sunny"
                    Icons.Rounded.NightsStay -> "Clear"
                    else -> "Partly Cloudy"
                }, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }

            // Forecast horizontal scroller (auto-cycling)
            Box(modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))) {
                val cornerPx = with(LocalDensity.current) { 8.dp.toPx() }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    // background subtle
                    drawRoundRect(Color.White.copy(alpha = 0.03f), cornerRadius = CornerRadius(cornerPx))
                }

                Row(modifier = Modifier
                    .offset { IntOffset(xOffset.value.roundToInt(), 0) }
                    .padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    forecasts.forEach { f ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp).padding(4.dp)) {
                            Icon(imageVector = f.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.height(2.dp))
                            Text(text = f.dayLabel, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                            Text(text = f.temp, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickSettingsPanelContent() {
    var wifiEnabled by remember { mutableStateOf(true) }
    var btEnabled by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }
    val view = LocalView.current

    // Compact horizontal layout
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        CompactQuickSettingToggle(
            icon = Icons.Rounded.Wifi,
            enabled = wifiEnabled,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                wifiEnabled = !wifiEnabled
            }
        )
        CompactQuickSettingToggle(
            icon = Icons.Rounded.Bluetooth,
            enabled = btEnabled,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                btEnabled = !btEnabled
            }
        )
        CompactQuickSettingToggle(
            icon = Icons.Rounded.FlashlightOn,
            enabled = flashEnabled,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                flashEnabled = !flashEnabled
            }
        )
        val context = LocalContext.current
        CompactQuickSettingToggle(
            icon = Icons.Rounded.AirplanemodeActive,
            enabled = false,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                try {
                    context.startActivity(Intent(android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot open settings", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun BrowserSearchPanelContent(isEditMode: Boolean = false) {
    val context = LocalContext.current
    val cfg = context.config
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    val openBrowser: (String?) -> Unit = { q ->
        try {
            if (q == null) {
                // Launch the default browser app main activity without opening a new tab
                var launched = false
                try {
                    val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("http://"))
                    val resolveInfo = context.packageManager.resolveActivity(viewIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                    val pkg = resolveInfo?.activityInfo?.packageName
                    val launch = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
                    if (launch != null) {
                        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launch)
                        launched = true
                    }
                } catch (e: Exception) {
                    // fallback to opening homepages URL if launch fails
                }
                if (!launched) {
                    val fallback = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com")).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(fallback)
                }
            } else {
                val url = java.net.URLEncoder.encode(q, "UTF-8").let { "https://www.google.com/search?q=$it" }
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    val openOnTap = cfg.searchWidgetOpensBrowserOnTap

    Box(modifier = Modifier
        .fillMaxWidth()
        .then(
            if (!isEditMode) {
                Modifier.pointerInput(openOnTap) {
                    detectTapGestures(onTap = {
                        if (openOnTap && !isEditMode) {
                            // open default browser (no search)
                            openBrowser(null)
                        } else {
                            // focus for typing
                            focusRequester.requestFocus()
                        }
                    })
                }
            } else Modifier
        )) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    text = if (openOnTap) "Tap to Search" else "Search web...",
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White) },
            textStyle = androidx.compose.ui.text.TextStyle(
                color = Color.White,
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            ),
            trailingIcon = {
                // Disable actionable trailing icon when open-on-tap is enabled to avoid creating tabs
                if (!openOnTap && query.isNotBlank()) {
                    IconButton(onClick = { openBrowser(query) }) {
                        Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = Color(0xFF6366F1))
                    }
                }
            },

            singleLine = true,
            enabled = !isEditMode && !openOnTap,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = {
                    if (query.isNotBlank()) openBrowser(query)
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF6366F1),
                unfocusedBorderColor = Color.Gray
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun CompactQuickSettingToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    // Compact circle button - no labels
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (enabled) Color(0xFF6366F1).copy(alpha = 0.9f) else Color.White.copy(alpha = 0.15f)
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun BatteryPanelContent(glassSettings: LiquidGlassSettings) {
    val context = LocalContext.current
    val batteryManager = remember { context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager }

    var batteryLevel by remember { mutableStateOf(0) }
    var isCharging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            batteryManager?.let { bm ->
                val newLevel = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                batteryLevel = newLevel
                val status = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_STATUS)
                isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == android.os.BatteryManager.BATTERY_STATUS_FULL
            }
            delay(5000) // Update every 5 seconds
        }
    }

    if (glassSettings.batteryStyle == "Cyberpunk") {
        CyberpunkBattery(batteryLevel, isCharging)
        return
    }

    // animate the fill fraction when battery level changes
    val fillFraction by animateFloatAsState(targetValue = (batteryLevel.coerceIn(0,100) / 100f), animationSpec = tween(800))

    // idle animation when not charging (gentle pulse)
    val idlePulse = rememberInfiniteTransition()
    val idleAlpha by idlePulse.animateFloat(
        initialValue = 0.02f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )
    val bob by idlePulse.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )

    // precompute pixel conversions
    val padPx = with(LocalDensity.current) { 3.dp.toPx() }
    val cornerPx = with(LocalDensity.current) { 6.dp.toPx() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            // battery container drawn with canvas so we can animate fill
            Canvas(modifier = Modifier
                .size(80.dp)
                .offset { IntOffset(0, bob.roundToInt()) }) {
                val w = size.width
                val h = size.height
                val capWidth = w * 0.08f
                val bodyW = w - capWidth - padPx
                val bodyH = h * 0.6f
                val left = padPx
                val top = (h - bodyH) / 2f

                // border
                drawRoundRect(color = Color.White.copy(alpha = 0.12f), topLeft = Offset(left, top), size = Size(bodyW, bodyH), cornerRadius = CornerRadius(cornerPx))

                // fill
                val fillW = bodyW * fillFraction
                val fillColor = when {
                    batteryLevel > 80 -> Color(0xFF22C55E)
                    batteryLevel > 20 -> Color(0xFFFBBF24)
                    else -> Color(0xFFEF4444)
                }
                drawRoundRect(color = fillColor, topLeft = Offset(left, top), size = Size(fillW, bodyH), cornerRadius = CornerRadius(cornerPx))

                // cap
                drawRoundRect(color = Color.White.copy(alpha = 0.12f), topLeft = Offset(left + bodyW + padPx, top + bodyH * 0.25f), size = Size(capWidth, bodyH * 0.5f), cornerRadius = CornerRadius(2.dp.toPx()))

                // charging pulse overlay
                if (isCharging) {
                    val pulse = (System.currentTimeMillis() % 1000L) / 1000f
                    drawRoundRect(color = Color.White.copy(alpha = 0.06f + 0.12f * kotlin.math.abs(kotlin.math.sin(pulse * Math.PI).toFloat())), topLeft = Offset(left, top), size = Size(bodyW, bodyH), cornerRadius = CornerRadius(cornerPx))
                } else {
                    // idle subtle pulse overlay
                    drawRoundRect(color = Color.White.copy(alpha = idleAlpha), topLeft = Offset(left, top), size = Size(bodyW, bodyH), cornerRadius = CornerRadius(cornerPx))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(text = "$batteryLevel%",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold)

        AnimatedVisibility(visible = isCharging) {
            Text(text = "Charging", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun FolderView(
    item: LauncherItem.Folder,
    backdrop: LayerBackdrop,
    glassSettings: LiquidGlassSettings,
    context: Context,
    isEditMode: Boolean,
    onOpenFolder: () -> Unit,
    cellWidth: Float = 0f
) {
    val tintColor = Color(item.tintColor)
    val cornerRadius = glassSettings.iconCornerRadius.dp
    val density = LocalDensity.current
    val scaledSize = with(density) { (cellWidth * glassSettings.appTileScale).toDp() }
    val view = LocalView.current

    // Load app icons for preview (up to 4)
    val appIcons = remember(item.apps) {
        item.apps.take(4).mapNotNull { packageName ->
            try {
                packageName to context.packageManager.getApplicationIcon(packageName)
            } catch (e: Exception) {
                null
            }
        }
    }

    Column(
        modifier = Modifier
            .size(scaledSize)
            .clip(RoundedCornerShape(cornerRadius))
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(cornerRadius) },
                effects = {
                    if (glassSettings.vibrancyEnabled) vibrancy()
                    if (glassSettings.blurEnabled) blur(glassSettings.blurRadius.dp.toPx())
                    if (glassSettings.lensEnabled) lens(
                        refractionHeight = glassSettings.refractionHeight.dp.toPx(),
                        refractionAmount = glassSettings.refractionAmount.dp.toPx(),
                        chromaticAberration = glassSettings.chromaticAberration
                    )
                },
                onDrawSurface = {
                    drawRect(tintColor.copy(alpha = glassSettings.iconBackgroundAlpha))
                }
            )
            .pointerInput(isEditMode) {
                if (!isEditMode) {
                    detectTapGestures(
                        onTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onOpenFolder()
                        }
                    )
                }
            }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App icons grid (2x2 preview)
        if (appIcons.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                // 2x2 grid of app icons
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        appIcons.getOrNull(0)?.let { (_, icon) ->
                            Image(
                                bitmap = icon.toBitmap(64, 64).asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        } ?: Spacer(Modifier.size(20.dp))

                        appIcons.getOrNull(1)?.let { (_, icon) ->
                            Image(
                                bitmap = icon.toBitmap(64, 64).asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        } ?: Spacer(Modifier.size(20.dp))
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        appIcons.getOrNull(2)?.let { (_, icon) ->
                            Image(
                                bitmap = icon.toBitmap(64, 64).asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        } ?: Spacer(Modifier.size(20.dp))

                        appIcons.getOrNull(3)?.let { (_, icon) ->
                            Image(
                                bitmap = icon.toBitmap(64, 64).asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        } ?: Spacer(Modifier.size(20.dp))
                    }
                }
            }
        } else {
            // Empty folder placeholder
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 24.sp
                )
            }
        }

        // Folder name
        Text(
            text = item.name,
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OpenedFolderDialog(
    folder: LauncherItem.Folder,
    backdrop: LayerBackdrop,
    glassSettings: LiquidGlassSettings,
    context: Context,
    onLaunchApp: (String) -> Unit,
    onRemoveApp: (String) -> Unit,
    onSortApps: () -> Unit,
    onDismiss: () -> Unit
) {
    val tintColor = Color(folder.tintColor)
    val cornerRadius = glassSettings.panelCornerRadius.dp
    val view = LocalView.current

    // Load all app icons
    val appIcons = remember(folder.apps) {
        folder.apps.mapNotNull { packageName ->
            try {
                val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                val appName = context.packageManager.getApplicationLabel(appInfo).toString()
                Triple(packageName, context.packageManager.getApplicationIcon(packageName), appName)
            } catch (e: Exception) {
                null
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(cornerRadius))
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(cornerRadius) },
                    effects = {
                        if (glassSettings.vibrancyEnabled) vibrancy()
                        if (glassSettings.blurEnabled) blur(glassSettings.blurRadius.dp.toPx())
                        if (glassSettings.lensEnabled) lens(
                            refractionHeight = glassSettings.refractionHeight.dp.toPx(),
                            refractionAmount = glassSettings.refractionAmount.dp.toPx(),
                            chromaticAberration = glassSettings.chromaticAberration
                        )
                    },
                    onDrawSurface = {
                        drawRect(tintColor.copy(alpha = glassSettings.panelBackgroundAlpha))
                    }
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Folder title and controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = folder.name,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onSortApps()
                }) {
                    Icon(
                        imageVector = Icons.Rounded.SortByAlpha,
                        contentDescription = "Sort A-Z",
                        tint = Color.White
                    )
                }
            }

            if (appIcons.isEmpty()) {
                Text(
                    text = "Folder is empty",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            } else {
                // App grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(appIcons) { (packageName, icon, appName) ->
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        onLaunchApp(packageName)
                                    },
                                    onLongClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        onRemoveApp(packageName)
                                    }
                                )
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                bitmap = icon.toBitmap(96, 96).asImageBitmap(),
                                contentDescription = appName,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(glassSettings.iconCornerRadius.dp))
                            )
                            if (glassSettings.showAppLabels) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = appName,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(56.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyGridCells(
    gridColumns: Int,
    gridRows: Int,
    cellWidth: Float,
    cellHeight: Float,
    occupiedCells: Set<Pair<Int, Int>>,
    onCellClick: (Int, Int) -> Unit
) {
    val density = LocalDensity.current

    for (x in 0 until gridColumns) {
        for (y in 0 until gridRows) {
            if ((x to y) !in occupiedCells) {
                val offsetX = with(density) { (x * cellWidth).toDp() }
                val offsetY = with(density) { (y * cellHeight).toDp() }
                val width = with(density) { cellWidth.toDp() }
                val height = with(density) { cellHeight.toDp() }

                EmptyCellIndicator(
                    gridX = x,
                    gridY = y,
                    isEditMode = true,
                    onClick = { onCellClick(x, y) },
                    modifier = Modifier
                        .offset(x = offsetX, y = offsetY)
                        .size(width = width, height = height)
                        .padding(4.dp)
                )
            }
        }
    }
}


@Composable
private fun rememberWallpaperPainter(
    customUri: String?,
    useSystem: Boolean,
    permissionGranted: Boolean
): Painter {
    val context = LocalContext.current
    var painter by remember { mutableStateOf<Painter?>(null) }
    var retryCount by remember { mutableStateOf(0) }

    // Retry loading wallpaper after a short delay if initial load fails
    LaunchedEffect(customUri, useSystem, retryCount, permissionGranted) {
        withContext(Dispatchers.IO) {
            painter = try {
                if (!useSystem && customUri != null) {
                    // Load custom wallpaper from URI or file path
                    val bitmap = if (customUri.startsWith("/")) {
                        // It's a file path
                        val options = android.graphics.BitmapFactory.Options().apply {
                            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                        }
                        android.graphics.BitmapFactory.decodeFile(customUri, options)
                    } else {
                        // It's a content URI
                        val uri = android.net.Uri.parse(customUri)
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            android.graphics.BitmapFactory.decodeStream(inputStream)
                        }
                    }
                    bitmap?.asImageBitmap()?.let { BitmapPainter(it) }
                } else {
                    // Use system wallpaper with fallback only if permission is granted
                    if (permissionGranted) loadSystemWallpaper(context) else null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Retry once after delay
                if (retryCount == 0) {
                    delay(500)
                    retryCount++
                }
                null
            }
        }
    }

    return painter ?: GradientPainter()
}

private fun loadSystemWallpaper(context: Context): Painter? {
    return try {
        val wallpaperManager = android.app.WallpaperManager.getInstance(context)
        // Try to get the actual wallpaper drawable
        val wallpaperDrawable = wallpaperManager.drawable
        if (wallpaperDrawable != null) {
            // Get display metrics for proper sizing
            val displayMetrics = context.resources.displayMetrics
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            // Convert to bitmap with proper size
            val bitmap = wallpaperDrawable.toBitmap(width, height)
            BitmapPainter(bitmap.asImageBitmap())
        } else {
            // Fallback: try to get wallpaper via peek
            val peekDrawable = wallpaperManager.peekDrawable()
            peekDrawable?.toBitmap()?.asImageBitmap()?.let { BitmapPainter(it) }
        }
    } catch (se: SecurityException) {
        android.util.Log.w("LiquidGlassLauncher", "No permission to read wallpaper: ${se.message}")
        return null
    } catch (e: Exception) {
        e.printStackTrace()
        return null
        null
    } catch (e: SecurityException) {
        // No permission to read wallpaper
        e.printStackTrace()
        null
    }
}

// Helper to compute downsample sample size similar to Android docs
private fun calculateInSampleSize(origW: Int, origH: Int, reqW: Int, reqH: Int): Int {
    var inSampleSize = 1
    if (origH > reqH || origW > reqW) {
        var halfHeight = origH / 2
        var halfWidth = origW / 2
        while ((halfHeight / inSampleSize) >= reqH && (halfWidth / inSampleSize) >= reqW) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

private class GradientPainter : Painter() {
    override val intrinsicSize = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF0F0C29),
                    Color(0xFF302B63),
                    Color(0xFF24243E)
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )
    }
}

private suspend fun loadAvailableApps(context: Context): List<AvailableApp> {
    val pm = context.packageManager
    val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
    intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)

    val overrides = com.quimodotcom.lqlauncher.compose.launcher.AppMetadataRepository.loadAll(context)

    return pm.queryIntentActivities(intent, 0)
        .map { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            val override = overrides[pkg]

            val label = override?.label ?: resolveInfo.loadLabel(pm).toString()
            val icon = try {
                if (override?.customIconUri != null) {
                    loadDrawableFromUri(context, override.customIconUri) ?: resolveInfo.loadIcon(pm)
                } else {
                    resolveInfo.loadIcon(pm)
                }
            } catch (e: Exception) {
                null
            }

            AvailableApp(
                packageName = pkg,
                label = label,
                icon = icon,
                componentName = ComponentName(pkg, resolveInfo.activityInfo.name),
                customIconUri = override?.customIconUri
            )
        }
        .sortedBy { it.label.lowercase() }
}

private fun loadDrawableFromUri(context: Context, uriString: String): android.graphics.drawable.Drawable? {
    return try {
        val uri = android.net.Uri.parse(uriString)
        val stream = context.contentResolver.openInputStream(uri)
        android.graphics.drawable.Drawable.createFromStream(stream, uriString)
    } catch (e: Exception) {
        null
    }
}

private fun createDefaultItems(apps: List<AvailableApp>): List<LauncherItem> {
    val items = mutableListOf<LauncherItem>()

    // Add a clock panel
    items.add(
        LauncherItem.GlassPanel(
            gridX = 1,
            gridY = 0,
            spanX = 2,
            spanY = 2,
            panelType = PanelType.CLOCK,
            blurRadius = 25f,
            tintColor = 0xFF6366F1
        )
    )

    // Fresh installs: do not populate app shortcuts by default — keep a clean page
    // (only the clock panel is shown). This prevents clicks on placeholder apps causing bugs.

    return items
}

private fun findEmptyCell(config: LauncherConfig): Pair<Int, Int> {
    val occupied = config.items.flatMap { item ->
        (0 until item.spanX).flatMap { dx ->
            (0 until item.spanY).map { dy ->
                (item.gridX + dx) to (item.gridY + dy)
            }
        }
    }.toSet()

    for (y in 0 until config.gridRows) {
        for (x in 0 until config.gridColumns) {
            if ((x to y) !in occupied) {
                return x to y
            }
        }
    }
    return 0 to 0
}

private fun launchApp(context: Context, packageName: String) {
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
            if (context is android.app.Activity) {
                context.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        } else {
            Toast.makeText(context, "Cannot launch app", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Error launching app", Toast.LENGTH_SHORT).show()
    }
}