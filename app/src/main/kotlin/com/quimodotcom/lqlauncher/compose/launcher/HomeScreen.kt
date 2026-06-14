package com.quimodotcom.lqlauncher.compose.launcher

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.shapes.RoundedRectangle
import com.quimodotcom.lqlauncher.helpers.rememberTiltState
import com.quimodotcom.lqlauncher.viewmodels.LauncherViewModel
import kotlinx.coroutines.delay
import android.view.HapticFeedbackConstants
import android.content.ComponentName
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.quimodotcom.lqlauncher.services.MediaStateRepository
import com.quimodotcom.lqlauncher.helpers.LauncherUtils

@Composable
fun HomeScreen(
    viewModel: LauncherViewModel,
    hasWallpaperPermission: Boolean,
    onWallpaperPermissionGranted: () -> Unit,
    onExportSchematic: () -> Unit,
    onImportSchematic: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val launcherConfig = viewModel.launcherConfig
    val glassSettings = viewModel.glassSettings
    val editModeState = viewModel.editModeState

    val cellWidth by remember(viewModel.gridSize, launcherConfig.gridColumns) {
        derivedStateOf {
            if (viewModel.gridSize.width > 0) {
                viewModel.gridSize.width.toFloat() / launcherConfig.gridColumns
            } else 0f
        }
    }
    val cellHeight by remember(viewModel.gridSize, launcherConfig.gridRows) {
        derivedStateOf {
            if (viewModel.gridSize.height > 0) {
                viewModel.gridSize.height.toFloat() / launcherConfig.gridRows
            } else 0f
        }
    }

    var timeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                timeTick++
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(r, filter)
        }
        onDispose { context.unregisterReceiver(r) }
    }

    val isCurrentlyNight = remember(glassSettings, timeTick) {
        glassSettings.isCurrentlyNight(context)
    }
    val tiltState = rememberTiltState(glassSettings.enableParallax)

    val mediaState by MediaStateRepository.mediaState.collectAsState()
    val homeMediaArtEnabled = glassSettings.enableHomeMediaArt && mediaState?.art != null

    val wallpaperPainter = rememberWallpaperPainter(
        customUri = remember(
            isCurrentlyNight,
            glassSettings.secretWallpaperVisible,
            launcherConfig.wallpaperSecretUri,
            launcherConfig.wallpaperUri,
            launcherConfig.wallpaperNightUri
        ) {
            if (glassSettings.secretWallpaperVisible && launcherConfig.wallpaperSecretUri != null) {
                launcherConfig.wallpaperSecretUri
            } else if (isCurrentlyNight) {
                launcherConfig.wallpaperNightUri ?: launcherConfig.wallpaperUri
            } else {
                launcherConfig.wallpaperUri
            }
        },
        useSystem = launcherConfig.useSystemWallpaper && (!glassSettings.secretWallpaperVisible || launcherConfig.wallpaperSecretUri == null),
        permissionGranted = hasWallpaperPermission,
        mediaArt = if (homeMediaArtEnabled) mediaState?.art else null
    )

    val backdrop = rememberLayerBackdrop()
    var showWallpaperPrompt by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val l = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
                        .contains(context.packageName)
                ) {
                    val wm = android.app.WallpaperManager.getInstance(context)
                    if (wm.wallpaperInfo == null || wm.wallpaperInfo.packageName != context.packageName) {
                        showWallpaperPrompt = true
                    }
                }
            }
        }
        val lc = (context as ComponentActivity).lifecycle
        lc.addObserver(l)
        onDispose { lc.removeObserver(l) }
    }

    if (showWallpaperPrompt) {
        AlertDialog(
            onDismissRequest = { showWallpaperPrompt = false },
            title = { Text("Enable Live Wallpaper") },
            text = { Text("To display lock screen media art, Liquid Glass must be set as your live wallpaper.") },
            confirmButton = {
                TextButton(onClick = {
                    showWallpaperPrompt = false
                    try {
                        val intent = Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                            putExtra(
                                android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                ComponentName(
                                    context,
                                    "com.quimodotcom.lqlauncher.services.LiquidGlassWallpaperService"
                                )
                            )
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open wallpaper settings", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Set Wallpaper") }
            },
            dismissButton = {
                TextButton(onClick = { showWallpaperPrompt = false }) { Text("Cancel") }
            }
        )
    }

    val activeNotifications by com.quimodotcom.lqlauncher.services.MediaListenerService.activeNotificationPackages.collectAsState()
    var metadataVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        AppMetadataRepository.metadataUpdates.collect { metadataVersion++ }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(viewModel.showAppDrawer) {
                if (!viewModel.showAppDrawer) {
                    detectVerticalDragGestures { change, dragAmount ->
                        if (dragAmount < -10f) {
                            viewModel.toggleAppDrawer(true)
                            change.consume()
                        }
                    }
                }
            }
            .background(Color.Black)
    ) {
        // Wallpaper Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            Image(
                painter = wallpaperPainter,
                contentDescription = null,
                contentScale = if (launcherConfig.backgroundScaleMode == "Fit") ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (glassSettings.windowBlurEnabled) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedRectangle(0.dp) },
                                effects = { blur(glassSettings.windowBlurRadius.dp.toPx()) }
                            )
                        } else Modifier
                    )
                    .graphicsLayer {
                        val baseScale = launcherConfig.backgroundZoom
                        if (glassSettings.enableParallax) {
                            val tilt = tiltState.value
                            val maxShift = 5f * 20f * glassSettings.parallaxIntensity
                            val safeScale = maxOf(
                                1.05f,
                                if (size.width > 0) 1f + (2 * maxShift / size.width) else 1.05f,
                                if (size.height > 0) 1f + (2 * maxShift / size.height) else 1.05f
                            )
                            scaleX = safeScale * baseScale
                            scaleY = safeScale * baseScale
                            translationX = tilt.x.coerceIn(-5f, 5f) * 20f * glassSettings.parallaxIntensity
                            translationY = tilt.y.coerceIn(-5f, 5f) * 20f * glassSettings.parallaxIntensity
                        } else {
                            scaleX = baseScale
                            scaleY = baseScale
                            translationX = 0f
                            translationY = 0f
                        }
                    }
            )
        }

        val springSpec = spring<androidx.compose.ui.unit.Dp>(
            dampingRatio = glassSettings.dragSpringDamping,
            stiffness = glassSettings.dragSpringStiffness
        )

        val gridBottomPadding by animateDpAsState(
            targetValue = if (editModeState.isEnabled && !editModeState.isToolbarAtTop) 220.dp else 0.dp,
            animationSpec = springSpec,
            label = "gridBottomPadding"
        )
        val gridTopPadding by animateDpAsState(
            targetValue = if (editModeState.isEnabled && editModeState.isToolbarAtTop) 180.dp else 0.dp,
            animationSpec = springSpec,
            label = "gridTopPadding"
        )

        // Grid Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(8.dp)
                .onSizeChanged { viewModel.updateGridSize(it) }
                .pointerInput(editModeState.isEnabled) {
                    if (!editModeState.isEnabled) {
                        detectTapGestures(onLongPress = { viewModel.setEditMode(true) })
                    }
                }
        ) {
            // Panels Layer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = gridTopPadding.coerceAtLeast(0.dp),
                        bottom = gridBottomPadding.coerceAtLeast(0.dp)
                    )
            ) {
                launcherConfig.items.filterIsInstance<LauncherItem.GlassPanel>().forEach { item ->
                    val offsetX = with(density) { (item.gridX * cellWidth).toDp() }
                    val offsetY = with(density) { (item.gridY * cellHeight).toDp() }
                    val width = with(density) { (item.spanX * cellWidth).toDp() }
                    val height = with(density) { (item.spanY * cellHeight).toDp() }

                    val isSelected = editModeState.selectedItemId == item.id
                    val dragTranslation = if (isSelected && editModeState.isDragging) {
                        editModeState.dragOffset
                    } else Offset.Zero

                    Box(
                        modifier = Modifier
                            .offset(x = offsetX, y = offsetY)
                            .graphicsLayer {
                                translationX = dragTranslation.x
                                translationY = dragTranslation.y
                                alpha = if (viewModel.isSubjectPositioning) 0f else 1f
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
            }

            // Subject Layer
            val effectiveSubjectNight = if (editModeState.showWallpaperPicker) {
                viewModel.selectedSubjectAdjustmentMode == 1
            } else isCurrentlyNight

            val currentSubjectUri = remember(
                effectiveSubjectNight,
                launcherConfig.wallpaperSubjectUri,
                launcherConfig.wallpaperSubjectNightUri
            ) {
                if (launcherConfig.wallpaperSubjectNightUri != null) {
                    if (effectiveSubjectNight) {
                        launcherConfig.wallpaperSubjectNightUri
                    } else {
                        launcherConfig.wallpaperSubjectUri
                    }
                } else null
            }

            if (currentSubjectUri != null) {
                val isNightSubject = effectiveSubjectNight && launcherConfig.wallpaperSubjectNightUri != null
                val match = if (isNightSubject) {
                    launcherConfig.subjectNightMatchWallpaper
                } else {
                    launcherConfig.subjectMatchWallpaper
                }
                val scale = if (isNightSubject) launcherConfig.subjectNightScale else launcherConfig.subjectScale
                val offX = if (isNightSubject) launcherConfig.subjectNightOffsetX else launcherConfig.subjectOffsetX
                val offY = if (isNightSubject) launcherConfig.subjectNightOffsetY else launcherConfig.subjectOffsetY

                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(currentSubjectUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = if (match) {
                        if (launcherConfig.backgroundScaleMode == "Fit") ContentScale.Fit else ContentScale.Crop
                    } else ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (match) {
                                if (glassSettings.enableParallax) {
                                    val tilt = tiltState.value
                                    translationX = tilt.x * 35f * glassSettings.parallaxIntensity
                                    translationY = tilt.y * 35f * glassSettings.parallaxIntensity
                                    scaleX = 1.05f
                                    scaleY = 1.05f
                                }
                            } else {
                                val tilt = tiltState.value
                                scaleX = scale
                                scaleY = scale
                                translationX = (offX * density.density) + (tilt.x * 35f * glassSettings.parallaxIntensity)
                                translationY = (offY * density.density) + (tilt.y * 35f * glassSettings.parallaxIntensity)
                            }
                        }
                )
            }

            // Foreground Items Layer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = gridTopPadding.coerceAtLeast(0.dp),
                        bottom = gridBottomPadding.coerceAtLeast(0.dp)
                    )
            ) {
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
                            viewModel.pendingGridPosition = x to y
                            viewModel.editModeState = editModeState.copy(
                                showAppPicker = false,
                                showPanelPicker = false
                            )
                        }
                    )
                }

                launcherConfig.items.forEach { item ->
                    val isSelected = editModeState.selectedItemId == item.id
                    val offsetX = with(density) { (item.gridX * cellWidth).toDp() }
                    val offsetY = with(density) { (item.gridY * cellHeight).toDp() }
                    val width = with(density) { (item.spanX * cellWidth).toDp() }
                    val height = with(density) {
                        if (item is LauncherItem.AppShortcut || item is LauncherItem.InvisibleButton) {
                            (item.spanY * cellWidth).toDp()
                        } else {
                            (item.spanY * cellHeight).toDp()
                        }
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
                        onDragOffsetChange = { viewModel.editModeState = viewModel.editModeState.copy(dragOffset = it) },
                        onDragStart = { viewModel.editModeState = viewModel.editModeState.copy(isDragging = true) },
                        onDragEnd = { viewModel.editModeState = viewModel.editModeState.copy(isDragging = false) },
                        onSelect = { viewModel.editModeState = viewModel.editModeState.copy(selectedItemId = item.id) },
                        onMove = { nx, ny ->
                            val targetItem = viewModel.launcherConfig.items.find {
                                it.gridX == nx && it.gridY == ny && it.id != item.id
                            }

                            if (item is LauncherItem.AppShortcut && targetItem is LauncherItem.AppShortcut) {
                                val newFolder = LauncherItem.Folder(
                                    gridX = nx, gridY = ny,
                                    name = "New Folder",
                                    apps = listOf(targetItem.packageName, item.packageName)
                                )
                                viewModel.launcherConfig = viewModel.launcherConfig.copy(
                                    items = viewModel.launcherConfig.items.filter {
                                        it.id != item.id && it.id != targetItem.id
                                    } + newFolder
                                )
                            } else if (item is LauncherItem.AppShortcut && targetItem is LauncherItem.Folder) {
                                viewModel.launcherConfig = viewModel.launcherConfig.copy(
                                    items = viewModel.launcherConfig.items.map {
                                        if (it.id == targetItem.id && it is LauncherItem.Folder) {
                                            it.copy(apps = it.apps + item.packageName)
                                        } else it
                                    }.filter { it.id != item.id }
                                )
                            } else {
                                viewModel.launcherConfig = viewModel.launcherConfig.copy(
                                    items = viewModel.launcherConfig.items.map {
                                        if (it.id == item.id) {
                                            when (it) {
                                                is LauncherItem.AppShortcut -> it.copy(gridX = nx, gridY = ny)
                                                is LauncherItem.GlassPanel -> it.copy(gridX = nx, gridY = ny)
                                                is LauncherItem.Folder -> it.copy(gridX = nx, gridY = ny)
                                                is LauncherItem.InvisibleButton -> it.copy(gridX = nx, gridY = ny)
                                            }
                                        } else it
                                    }
                                )
                            }
                        },
                        onResize = { nx, ny ->
                            viewModel.launcherConfig = viewModel.launcherConfig.copy(
                                items = viewModel.launcherConfig.items.map {
                                    if (it.id == item.id && it is LauncherItem.GlassPanel) {
                                        it.copy(spanX = nx, spanY = ny)
                                    } else it
                                }
                            )
                        },
                        onDelete = {
                            viewModel.launcherConfig = viewModel.launcherConfig.copy(
                                items = viewModel.launcherConfig.items.filter { it.id != item.id }
                            )
                            viewModel.editModeState = viewModel.editModeState.copy(selectedItemId = null)
                        },
                        modifier = Modifier
                            .offset(x = offsetX, y = offsetY)
                            .size(width, height)
                            .padding(4.dp)
                            .graphicsLayer {
                                alpha = if (viewModel.isSubjectPositioning) 0f else 1f
                            }
                    ) {
                        when (item) {
                            is LauncherItem.AppShortcut -> {
                                AppShortcutView(
                                    item = item,
                                    backdrop = backdrop,
                                    glassSettings = glassSettings,
                                    metadataVersion = metadataVersion,
                                    context = context,
                                    isEditMode = editModeState.isEnabled,
                                    hasNotification = activeNotifications.contains(item.packageName),
                                    onLaunch = { LauncherUtils.launchApp(context, item.packageName) },
                                    showLabel = glassSettings.showAppLabels,
                                    cellWidth = cellWidth
                                )
                            }
                            is LauncherItem.GlassPanel -> {
                                GlassPanelContent(item, glassSettings, editModeState.isEnabled)
                            }
                            is LauncherItem.Folder -> {
                                FolderView(
                                    item = item,
                                    backdrop = backdrop,
                                    glassSettings = glassSettings,
                                    context = context,
                                    isEditMode = editModeState.isEnabled,
                                    hasNotification = item.apps.any { activeNotifications.contains(it) },
                                    onOpenFolder = { viewModel.openedFolder = item },
                                    cellWidth = cellWidth
                                )
                            }
                            is LauncherItem.InvisibleButton -> {
                                val view = LocalView.current
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (editModeState.isEnabled) {
                                                Modifier
                                                    .background(Color.White.copy(0.1f), RoundedCornerShape(12.dp))
                                                    .border(
                                                        1.dp,
                                                        Color.White.copy(0.2f),
                                                        RoundedCornerShape(12.dp)
                                                    )
                                            } else Modifier
                                        )
                                        .pointerInput(editModeState.isEnabled) {
                                            if (!editModeState.isEnabled) {
                                                detectTapGestures(onTap = {
                                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                                    when (item.action) {
                                                        LauncherAction.TOGGLE_SECRET_WALLPAPER -> {
                                                            viewModel.glassSettings = viewModel.glassSettings.copy(
                                                                secretWallpaperVisible = !viewModel.glassSettings.secretWallpaperVisible
                                                            )
                                                        }
                                                        LauncherAction.OPEN_APP_DRAWER -> viewModel.toggleAppDrawer(true)
                                                        LauncherAction.OPEN_SETTINGS -> viewModel.showSettings = true
                                                        LauncherAction.OPEN_APP -> {
                                                            item.targetPackageName?.let { LauncherUtils.launchApp(context, it) }
                                                        }
                                                        else -> {}
                                                    }
                                                })
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (editModeState.isEnabled) {
                                        Icon(
                                            imageVector = when (item.action) {
                                                LauncherAction.TOGGLE_SECRET_WALLPAPER -> Icons.Rounded.Visibility
                                                LauncherAction.OPEN_APP_DRAWER -> Icons.Rounded.Menu
                                                LauncherAction.OPEN_SETTINGS -> Icons.Rounded.Settings
                                                else -> Icons.Rounded.RadioButtonUnchecked
                                            },
                                            contentDescription = null,
                                            tint = Color.White.copy(0.5f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !editModeState.isEnabled && launcherConfig.items.isEmpty(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                EditModeHint(isVisible = true)
            }
        }

        // App Drawer Layer
        if (viewModel.showAppDrawer) {
            key(viewModel.drawerTrigger) {
                AppDrawer(
                    apps = viewModel.availableApps,
                    backdrop = backdrop,
                    glassSettings = glassSettings,
                    onAppClick = { pkg ->
                        LauncherUtils.launchApp(context, pkg)
                        viewModel.toggleAppDrawer(false)
                    },
                    onClose = { viewModel.toggleAppDrawer(false) },
                    onRefreshApps = { viewModel.reloadApps() }
                )
            }
        }

        BackHandler(enabled = viewModel.showAppDrawer || editModeState.isEnabled) {
            if (viewModel.showAppDrawer) {
                viewModel.toggleAppDrawer(false)
            } else if (editModeState.isEnabled) {
                viewModel.setEditMode(false)
            }
        }

        // Toolbars
        val toolbarContent: @Composable (Boolean) -> Unit = { isAtTop ->
            EditModeToolbar(
                backdrop = backdrop,
                isEditMode = editModeState.isEnabled,
                isAtTop = isAtTop,
                onHide = { viewModel.editModeState = viewModel.editModeState.copy(isUiHidden = true) },
                onAddApp = { viewModel.editModeState = viewModel.editModeState.copy(showAppPicker = true) },
                onAddPanel = { viewModel.editModeState = viewModel.editModeState.copy(showPanelPicker = true) },
                onAddFolder = { viewModel.showFolderNameDialog = true },
                onAddInvisibleButton = {
                    viewModel.showInvisibleButtonActionPicker = LauncherUtils.findEmptyCell(launcherConfig)
                },
                onChangeWallpaper = {
                    viewModel.editModeState = viewModel.editModeState.copy(showWallpaperPicker = true)
                },
                onOpenSettings = { viewModel.showSettings = true },
                onExitEditMode = { viewModel.setEditMode(false) },
                glassSettings = glassSettings
            )
        }

        AnimatedVisibility(
            visible = editModeState.isEnabled && !editModeState.isToolbarAtTop && !editModeState.isUiHidden && !viewModel.isSubjectPositioning,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) { toolbarContent(false) }

        AnimatedVisibility(
            visible = editModeState.isEnabled && editModeState.isToolbarAtTop && !editModeState.isUiHidden && !viewModel.isSubjectPositioning,
            modifier = Modifier.align(Alignment.TopCenter)
        ) { toolbarContent(true) }

        AnimatedVisibility(
            visible = editModeState.isEnabled && editModeState.isUiHidden && !viewModel.isSubjectPositioning,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .systemBarsPadding()
        ) {
            FloatingActionButton(
                onClick = { viewModel.editModeState = viewModel.editModeState.copy(isUiHidden = false) },
                containerColor = Color(0xFF6366F1),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.Visibility, "Show UI")
            }
        }
    }

    // Dialogs
    if (viewModel.pendingGridPosition != null) {
        AddItemMenu(
            gridX = viewModel.pendingGridPosition!!.first,
            gridY = viewModel.pendingGridPosition!!.second,
            onAddApp = { viewModel.editModeState = viewModel.editModeState.copy(showAppPicker = true) },
            onAddPanel = {
                val (x, y) = viewModel.pendingGridPosition!!
                viewModel.launcherConfig = viewModel.launcherConfig.copy(
                    items = viewModel.launcherConfig.items + LauncherItem.GlassPanel(
                        gridX = x,
                        gridY = y,
                        panelType = PanelType.CLOCK
                    )
                )
                viewModel.pendingGridPosition = null
            },
            onAddFolder = { viewModel.showFolderNameDialog = true },
            onAddInvisibleButton = {
                viewModel.showInvisibleButtonActionPicker = viewModel.pendingGridPosition
                viewModel.pendingGridPosition = null
            },
            onDismiss = { viewModel.pendingGridPosition = null }
        )
    }

    if (editModeState.showAppPicker) {
        AppPickerDialog(
            availableApps = viewModel.availableApps,
            onAppSelected = { app ->
                val pos = viewModel.pendingGridPosition ?: LauncherUtils.findEmptyCell(launcherConfig)
                viewModel.launcherConfig = viewModel.launcherConfig.copy(
                    items = viewModel.launcherConfig.items + LauncherItem.AppShortcut(
                        gridX = pos.first,
                        gridY = pos.second,
                        packageName = app.packageName,
                        label = app.label
                    )
                )
                viewModel.editModeState = viewModel.editModeState.copy(showAppPicker = false)
                viewModel.pendingGridPosition = null
            },
            onDismiss = {
                viewModel.editModeState = viewModel.editModeState.copy(showAppPicker = false)
                viewModel.pendingGridPosition = null
            }
        )
    }

    if (viewModel.showInvisibleButtonActionPicker != null) {
        val (x, y) = viewModel.showInvisibleButtonActionPicker!!
        InvisibleButtonActionPickerDialog(
            onActionSelected = { action ->
                viewModel.launcherConfig = viewModel.launcherConfig.copy(
                    items = viewModel.launcherConfig.items + LauncherItem.InvisibleButton(
                        gridX = x,
                        gridY = y,
                        action = action
                    )
                )
                viewModel.showInvisibleButtonActionPicker = null
            },
            onDismiss = { viewModel.showInvisibleButtonActionPicker = null }
        )
    }

    if (editModeState.showPanelPicker) {
        PanelPickerDialog(
            onPanelTypeSelected = { panelType ->
                val pos = viewModel.pendingGridPosition ?: LauncherUtils.findEmptyCell(launcherConfig)
                viewModel.launcherConfig = viewModel.launcherConfig.copy(
                    items = viewModel.launcherConfig.items + LauncherItem.GlassPanel(
                        gridX = pos.first,
                        gridY = pos.second,
                        panelType = panelType
                    )
                )
                viewModel.editModeState = viewModel.editModeState.copy(showPanelPicker = false)
                viewModel.pendingGridPosition = null
            },
            onDismiss = { viewModel.editModeState = viewModel.editModeState.copy(showPanelPicker = false) }
        )
    }

    if (viewModel.showFolderNameDialog) {
        FolderNameDialog(
            onConfirm = { name ->
                val pos = viewModel.pendingGridPosition ?: LauncherUtils.findEmptyCell(launcherConfig)
                viewModel.launcherConfig = viewModel.launcherConfig.copy(
                    items = viewModel.launcherConfig.items + LauncherItem.Folder(
                        gridX = pos.first,
                        gridY = pos.second,
                        name = name
                    )
                )
                viewModel.showFolderNameDialog = false
                viewModel.pendingGridPosition = null
            },
            onDismiss = {
                viewModel.showFolderNameDialog = false
                viewModel.pendingGridPosition = null
            }
        )
    }

    if (editModeState.showWallpaperPicker) {
        WallpaperPickerDialog(
            settings = glassSettings,
            onSettingsChanged = { viewModel.glassSettings = it },
            currentWallpaperUri = launcherConfig.wallpaperUri,
            currentWallpaperNightUri = launcherConfig.wallpaperNightUri,
            useSystemWallpaper = launcherConfig.useSystemWallpaper,
            currentSubjectUri = launcherConfig.wallpaperSubjectUri,
            currentSubjectNightUri = launcherConfig.wallpaperSubjectNightUri,
            subjectMatchWallpaper = launcherConfig.subjectMatchWallpaper,
            subjectScale = launcherConfig.subjectScale,
            subjectOffsetX = launcherConfig.subjectOffsetX,
            subjectOffsetY = launcherConfig.subjectOffsetY,
            subjectNightMatchWallpaper = launcherConfig.subjectNightMatchWallpaper,
            subjectNightScale = launcherConfig.subjectNightScale,
            subjectNightOffsetX = launcherConfig.subjectNightOffsetX,
            subjectNightOffsetY = launcherConfig.subjectNightOffsetY,
            selectedSubjectMode = viewModel.selectedSubjectAdjustmentMode,
            onSubjectModeChanged = { viewModel.selectedSubjectAdjustmentMode = it },
            backgroundScaleMode = launcherConfig.backgroundScaleMode,
            onBackgroundScaleModeChanged = {
                viewModel.launcherConfig = viewModel.launcherConfig.copy(backgroundScaleMode = it)
            },
            backgroundZoom = launcherConfig.backgroundZoom,
            onBackgroundZoomChanged = {
                viewModel.launcherConfig = viewModel.launcherConfig.copy(backgroundZoom = it)
            },
            onWallpaperPermissionGranted = onWallpaperPermissionGranted,
            onWallpaperSelected = { uri ->
                if (uri == null) {
                    viewModel.launcherConfig = viewModel.launcherConfig.copy(
                        useSystemWallpaper = true,
                        wallpaperUri = null
                    )
                } else {
                    val wm = android.app.WallpaperManager.getInstance(context)
                    if (wm.wallpaperInfo?.packageName != context.packageName) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
                                    wm.setStream(it)
                                }
                            } catch (e: Exception) {
                            }
                        }
                    }
                    viewModel.launcherConfig = viewModel.launcherConfig.copy(
                        useSystemWallpaper = false,
                        wallpaperUri = uri
                    )
                    context.sendBroadcast(Intent("com.quimodotcom.lqlauncher.ACTION_CONFIG_CHANGED"))
                }
            },
            onWallpaperNightSelected = {
                viewModel.launcherConfig = viewModel.launcherConfig.copy(wallpaperNightUri = it)
            },
            onSubjectSelected = {
                viewModel.launcherConfig = viewModel.launcherConfig.copy(wallpaperSubjectUri = it)
            },
            onSubjectNightSelected = {
                viewModel.launcherConfig = viewModel.launcherConfig.copy(wallpaperSubjectNightUri = it)
            },
            onSubjectConfigChanged = { isN, m, s, ox, oy ->
                viewModel.launcherConfig = if (isN) {
                    viewModel.launcherConfig.copy(
                        subjectNightMatchWallpaper = m,
                        subjectNightScale = s,
                        subjectNightOffsetX = ox,
                        subjectNightOffsetY = oy
                    )
                } else {
                    viewModel.launcherConfig.copy(
                        subjectMatchWallpaper = m,
                        subjectScale = s,
                        subjectOffsetX = ox,
                        subjectOffsetY = oy
                    )
                }
            },
            onInteractionStart = { viewModel.isSubjectPositioning = true },
            onInteractionEnd = { viewModel.isSubjectPositioning = false },
            onDismiss = {
                viewModel.editModeState = viewModel.editModeState.copy(showWallpaperPicker = false)
            }
        )
    }

    if (viewModel.showSettings) {
        LiquidGlassSettingsScreen(
            settings = glassSettings,
            onSettingsChanged = {
                viewModel.glassSettings = it
                viewModel.launcherConfig = viewModel.launcherConfig.copy(
                    gridColumns = it.gridColumns,
                    gridRows = it.gridRows
                )
            },
            launcherConfig = launcherConfig,
            onConfigChanged = { viewModel.launcherConfig = it },
            onOpenWallpaperPicker = {
                viewModel.editModeState = viewModel.editModeState.copy(showWallpaperPicker = true)
            },
            onExportSchematic = onExportSchematic,
            onImportSchematic = onImportSchematic,
            onDismiss = { viewModel.showSettings = false }
        )
    }

    viewModel.openedFolder?.let { folder ->
        OpenedFolderDialog(
            folder = folder,
            backdrop = backdrop,
            glassSettings = glassSettings,
            context = context,
            onLaunchApp = {
                LauncherUtils.launchApp(context, it)
                viewModel.openedFolder = null
            },
            onRemoveApp = { pkg ->
                viewModel.launcherConfig = viewModel.launcherConfig.copy(
                    items = viewModel.launcherConfig.items.map {
                        if (it.id == folder.id && it is LauncherItem.Folder) {
                            val nf = it.copy(apps = it.apps - pkg)
                            viewModel.openedFolder = nf
                            nf
                        } else it
                    }
                )
            },
            onSortApps = {
                viewModel.launcherConfig = viewModel.launcherConfig.copy(
                    items = viewModel.launcherConfig.items.map {
                        if (it.id == folder.id && it is LauncherItem.Folder) {
                            val sf = it.copy(apps = it.apps.sorted())
                            viewModel.openedFolder = sf
                            sf
                        } else it
                    }
                )
            },
            onDismiss = { viewModel.openedFolder = null }
        )
    }
}

@Composable
fun AppShortcutView(
    item: LauncherItem.AppShortcut,
    backdrop: LayerBackdrop,
    glassSettings: LiquidGlassSettings,
    metadataVersion: Int,
    context: Context,
    isEditMode: Boolean,
    hasNotification: Boolean,
    onLaunch: (String) -> Unit,
    showLabel: Boolean,
    cellWidth: Float
) {
    val cornerRadius = glassSettings.iconCornerRadius.dp
    val density = LocalDensity.current
    val scaledSize = with(density) { (cellWidth * glassSettings.appTileScale).toDp() }

    val iconDrawableState = produceState<android.graphics.drawable.Drawable?>(
        initialValue = null,
        item.packageName,
        glassSettings.iconPackPackageName,
        metadataVersion
    ) {
        withContext(Dispatchers.IO) {
            try {
                val m = AppMetadataRepository.getMetadata(context, item.packageName)
                if (m?.customIconUri != null) {
                    val d = LauncherUtils.loadDrawableFromUri(context, m.customIconUri)
                    if (d != null) {
                        value = d
                        return@withContext
                    }
                }
                val pm = context.packageManager
                val i = pm.getLaunchIntentForPackage(item.packageName)
                val c = i?.component
                if (glassSettings.iconPackPackageName.isNotEmpty() && c != null) {
                    val d = com.quimodotcom.lqlauncher.helpers.IconPackHelper.getIconDrawable(
                        context,
                        glassSettings.iconPackPackageName,
                        c
                    )
                    if (d != null) {
                        value = d
                        return@withContext
                    }
                }
                value = c?.let { pm.getActivityIcon(it) } ?: pm.getApplicationIcon(item.packageName)
            } catch (e: Exception) {
                value = null
            }
        }
    }

    val iconDrawable = iconDrawableState.value
    val view = LocalView.current

    Box(
        modifier = Modifier
            .size(scaledSize)
            .clip(RoundedCornerShape(cornerRadius))
            .then(
                if (glassSettings.liquidGlassEnabled) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(cornerRadius) },
                        effects = {
                            if (glassSettings.vibrancyEnabled) vibrancy()
                            if (glassSettings.blurEnabled) blur(glassSettings.blurRadius.dp.toPx())
                            if (glassSettings.lensEnabled) lens(
                                glassSettings.refractionHeight.dp.toPx(),
                                glassSettings.refractionAmount.dp.toPx(),
                                glassSettings.chromaticAberration
                            )
                        },
                        onDrawSurface = {
                            drawRect(
                                Color(glassSettings.panelTintColor.toInt())
                                    .copy(alpha = glassSettings.iconBackgroundAlpha)
                            )
                        }
                    )
                } else {
                    Modifier.background(
                        Color.Black.copy(glassSettings.iconBackgroundAlpha),
                        RoundedCornerShape(cornerRadius)
                    )
                }
            )
            .pointerInput(isEditMode) {
                if (!isEditMode) {
                    detectTapGestures(onTap = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onLaunch(item.packageName)
                    })
                }
            }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(if (showLabel) 0.6f else 0.8f)
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
                    null,
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize(0.65f)
                )
            }
        }

        if (showLabel) {
            Text(
                text = item.label,
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 1,
                lineHeight = 10.sp,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
            )
        }
    }

    if (hasNotification && glassSettings.showNotificationDots) {
        val dotSize = 12.dp
        Box(
            modifier = Modifier
                .size(scaledSize)
                .padding(4.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            if (glassSettings.liquidGlassNotificationDots) {
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedRectangle(with(density) { (dotSize / 2).toPx() }) },
                            effects = {
                                vibrancy()
                                blur(with(density) { 4.dp.toPx() })
                            },
                            onDrawSurface = { drawRect(Color.White.copy(alpha = 0.4f)) }
                        )
                        .border(0.5.dp, Color.White.copy(0.4f), CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .background(Color(glassSettings.notificationDotColor.toInt()), CircleShape)
                        .border(1.dp, Color.White.copy(0.2f), CircleShape)
                )
            }
        }
    }
}

@Composable
fun FolderView(
    item: LauncherItem.Folder,
    backdrop: LayerBackdrop,
    glassSettings: LiquidGlassSettings,
    context: Context,
    isEditMode: Boolean,
    hasNotification: Boolean,
    onOpenFolder: () -> Unit,
    cellWidth: Float
) {
    val cornerRadius = glassSettings.iconCornerRadius.dp
    val density = LocalDensity.current
    val scaledSize = with(density) { (cellWidth * glassSettings.appTileScale).toDp() }
    val view = LocalView.current

    val appIcons = remember(item.apps) {
        item.apps.take(4).mapNotNull { pkg ->
            try {
                pkg to context.packageManager.getApplicationIcon(pkg)
            } catch (e: Exception) {
                null
            }
        }
    }

    Column(
        modifier = Modifier
            .size(scaledSize)
            .clip(RoundedCornerShape(cornerRadius))
            .then(
                if (glassSettings.liquidGlassEnabled) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(cornerRadius) },
                        effects = {
                            if (glassSettings.vibrancyEnabled) vibrancy()
                            if (glassSettings.blurEnabled) blur(glassSettings.blurRadius.dp.toPx())
                            if (glassSettings.lensEnabled) lens(
                                glassSettings.refractionHeight.dp.toPx(),
                                glassSettings.refractionAmount.dp.toPx(),
                                glassSettings.chromaticAberration
                            )
                        },
                        onDrawSurface = {
                            drawRect(
                                Color(item.tintColor.toInt())
                                    .copy(alpha = glassSettings.iconBackgroundAlpha)
                            )
                        }
                    )
                } else {
                    Modifier.background(
                        Color.Black.copy(glassSettings.iconBackgroundAlpha),
                        RoundedCornerShape(cornerRadius)
                    )
                }
            )
            .pointerInput(isEditMode) {
                if (!isEditMode) {
                    detectTapGestures(onTap = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onOpenFolder()
                    })
                }
            }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (appIcons.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        appIcons.getOrNull(0)?.let { (_, i) ->
                            Image(
                                bitmap = i.toBitmap(64, 64).asImageBitmap(),
                                null,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        } ?: Spacer(Modifier.size(20.dp))
                        appIcons.getOrNull(1)?.let { (_, i) ->
                            Image(
                                bitmap = i.toBitmap(64, 64).asImageBitmap(),
                                null,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        } ?: Spacer(Modifier.size(20.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        appIcons.getOrNull(2)?.let { (_, i) ->
                            Image(
                                bitmap = i.toBitmap(64, 64).asImageBitmap(),
                                null,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        } ?: Spacer(Modifier.size(20.dp))
                        appIcons.getOrNull(3)?.let { (_, i) ->
                            Image(
                                bitmap = i.toBitmap(64, 64).asImageBitmap(),
                                null,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        } ?: Spacer(Modifier.size(20.dp))
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = Color.White.copy(0.5f), fontSize = 24.sp)
            }
        }
        Text(
            text = item.name,
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }

    if (hasNotification && glassSettings.showNotificationDots) {
        val dotSize = 12.dp
        Box(
            modifier = Modifier
                .size(scaledSize)
                .padding(4.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            if (glassSettings.liquidGlassNotificationDots) {
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedRectangle(with(density) { (dotSize / 2).toPx() }) },
                            effects = {
                                vibrancy()
                                blur(with(density) { 4.dp.toPx() })
                            },
                            onDrawSurface = { drawRect(Color.White.copy(alpha = 0.4f)) }
                        )
                        .border(0.5.dp, Color.White.copy(0.4f), CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .background(Color(glassSettings.notificationDotColor.toInt()), CircleShape)
                        .border(1.dp, Color.White.copy(0.2f), CircleShape)
                )
            }
        }
    }
}

@Composable
fun EmptyGridCells(
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
                EmptyCellIndicator(
                    gridX = x,
                    gridY = y,
                    isEditMode = true,
                    onClick = { onCellClick(x, y) },
                    modifier = Modifier
                        .offset(
                            x = with(density) { (x * cellWidth).toDp() },
                            y = with(density) { (y * cellHeight).toDp() }
                        )
                        .size(
                            with(density) { cellWidth.toDp() },
                            with(density) { cellHeight.toDp() }
                        )
                        .padding(4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OpenedFolderDialog(
    folder: LauncherItem.Folder,
    backdrop: LayerBackdrop,
    glassSettings: LiquidGlassSettings,
    context: Context,
    onLaunchApp: (String) -> Unit,
    onRemoveApp: (String) -> Unit,
    onSortApps: () -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val appIcons = remember(folder.apps) {
        folder.apps.mapNotNull { p ->
            try {
                val ai = context.packageManager.getApplicationInfo(p, 0)
                Triple(
                    p,
                    context.packageManager.getApplicationIcon(p),
                    context.packageManager.getApplicationLabel(ai).toString()
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(glassSettings.panelCornerRadius.dp))
                .then(
                    if (glassSettings.liquidGlassEnabled) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedRectangle(glassSettings.panelCornerRadius.dp) },
                            effects = {
                                if (glassSettings.vibrancyEnabled) vibrancy()
                                if (glassSettings.blurEnabled) blur(glassSettings.blurRadius.dp.toPx())
                                if (glassSettings.lensEnabled) lens(
                                    glassSettings.refractionHeight.dp.toPx(),
                                    glassSettings.refractionAmount.dp.toPx(),
                                    glassSettings.chromaticAberration
                                )
                            },
                            onDrawSurface = {
                                drawRect(
                                    Color(folder.tintColor.toInt())
                                        .copy(alpha = glassSettings.panelBackgroundAlpha)
                                )
                            }
                        )
                    } else {
                        Modifier.background(
                            Color.Black.copy(glassSettings.panelBackgroundAlpha),
                            RoundedCornerShape(glassSettings.panelCornerRadius.dp)
                        )
                    }
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(folder.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onSortApps()
                }) {
                    Icon(Icons.Rounded.SortByAlpha, null, tint = Color.White)
                }
            }

            if (appIcons.isEmpty()) {
                Text(
                    "Folder is empty",
                    color = Color.White.copy(0.6f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(appIcons) { (p, i, n) ->
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        onLaunchApp(p)
                                    },
                                    onLongClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        onRemoveApp(p)
                                    }
                                )
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                bitmap = i.toBitmap(96, 96).asImageBitmap(),
                                n,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(glassSettings.iconCornerRadius.dp))
                            )
                            if (glassSettings.showAppLabels) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    n,
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
fun rememberWallpaperPainter(
    customUri: String?,
    useSystem: Boolean,
    permissionGranted: Boolean,
    mediaArt: android.graphics.Bitmap? = null
): Painter {
    val context = LocalContext.current
    var painter by remember { mutableStateOf<Painter?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(customUri, useSystem, retryCount, permissionGranted, mediaArt) {
        withContext(Dispatchers.IO) {
            try {
                val newP = if (mediaArt != null) {
                    BitmapPainter(mediaArt.asImageBitmap())
                } else if (!useSystem && customUri != null) {
                    val uri = android.net.Uri.parse(customUri)
                    var rot = 0
                    try {
                        context.contentResolver.openInputStream(uri)?.use {
                            rot = when (android.media.ExifInterface(it).getAttributeInt(
                                android.media.ExifInterface.TAG_ORIENTATION,
                                android.media.ExifInterface.ORIENTATION_NORMAL
                            )) {
                                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                                else -> 0
                            }
                        }
                    } catch (e: Exception) {
                    }
                    val b = if (customUri.startsWith("/")) {
                        android.graphics.BitmapFactory.decodeFile(customUri)
                    } else {
                        context.contentResolver.openInputStream(uri)?.use {
                            android.graphics.BitmapFactory.decodeStream(it)
                        }
                    }
                    val fB = if (b != null && rot != 0) {
                        val m = android.graphics.Matrix().apply { postRotate(rot.toFloat()) }
                        val r = android.graphics.Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
                        b.recycle()
                        r
                    } else b
                    fB?.asImageBitmap()?.let { BitmapPainter(it) }
                } else if (permissionGranted) {
                    var sp: Painter? = null
                    for (i in 0..2) {
                        val wm = android.app.WallpaperManager.getInstance(context)
                        val wd = wm.drawable
                        sp = if (wd != null) {
                            BitmapPainter(
                                wd.toBitmap(
                                    context.resources.displayMetrics.widthPixels,
                                    context.resources.displayMetrics.heightPixels
                                ).asImageBitmap()
                            )
                        } else {
                            wm.peekDrawable()?.toBitmap()?.asImageBitmap()?.let { BitmapPainter(it) }
                        }
                        if (sp != null) break
                        delay(300)
                    }
                    sp
                } else null

                if (newP != null) {
                    painter = newP
                } else if (retryCount < 2) {
                    delay(1000)
                    retryCount++
                }
            } catch (e: Exception) {
                if (retryCount < 2) {
                    delay(1000)
                    retryCount++
                }
            }
        }
    }
    return painter ?: GradientPainter()
}

class GradientPainter : Painter() {
    override val intrinsicSize = androidx.compose.ui.geometry.Size.Unspecified
    override fun DrawScope.onDraw() {
        drawRect(
            Brush.linearGradient(
                0.0f to Color(0xFF0F0C29),
                0.4f to Color(0xFF302B63),
                0.7f to Color(0xFF533483),
                1.0f to Color(0xFF24243E),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )
    }
}
