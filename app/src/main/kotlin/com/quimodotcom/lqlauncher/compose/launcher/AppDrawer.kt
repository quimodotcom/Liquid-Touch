package com.quimodotcom.lqlauncher.compose.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.SwipeableState
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import com.quimodotcom.lqlauncher.helpers.AppIconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AppDrawer(
    apps: List<AvailableApp>,
    backdrop: LayerBackdrop,
    glassSettings: LiquidGlassSettings,
    onAppClick: (String) -> Unit,
    onClose: () -> Unit,
    onRefreshApps: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // Prewarm cache when apps or settings change
    LaunchedEffect(apps, glassSettings.iconPackPackageName, glassSettings.useIconPackInAppDrawer) {
        val requests = apps.map { app ->
            AppIconCache.IconRequest(
                component = app.componentName,
                customIconUri = app.customIconUri,
                iconPackPackage = if (glassSettings.useIconPackInAppDrawer) glassSettings.iconPackPackageName else null
            )
        }
        AppIconCache.prewarmMemoryCache(context, requests, 192)
    }

    // Ensure state updates trigger recomposition of filtered list
    val currentApps = rememberUpdatedState(apps)

    // State for app options dialog
    var appForOptions by remember { mutableStateOf<AvailableApp?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val filteredApps = remember(currentApps.value, searchQuery) {
        if (searchQuery.isBlank()) currentApps.value
        else currentApps.value.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    // Physics-based swipe-to-dismiss
    val swipeableState = rememberSwipeableState(initialValue = 1) // 1 = Open, 0 = Closed
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Use full screen height as the close anchor to ensure it fully dismisses
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    val anchors = mapOf(0f to 1, screenHeightPx to 0) // Pull down to close

    // Detect if closed by swipe
    LaunchedEffect(swipeableState.currentValue) {
        if (swipeableState.currentValue == 0) {
            onClose()
        }
    }

    // Fail-safe: Detect if visually closed (offset at bottom) to ensure state sync
    LaunchedEffect(swipeableState) {
        snapshotFlow { swipeableState.offset.value }
            .collect { offset ->
                if (offset >= screenHeightPx - 5f) {
                    onClose()
                }
            }
    }

    // Determine panel style
    val panelColor = Color(glassSettings.panelTintColor)
    val panelAlpha = glassSettings.panelBackgroundAlpha
    val blurRadius = glassSettings.blurRadius.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer { translationY = swipeableState.offset.value }
                .swipeable(
                    state = swipeableState,
                    anchors = anchors,
                    thresholds = { _, _ -> FractionalThreshold(0.3f) },
                    orientation = Orientation.Vertical
                )
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(glassSettings.panelCornerRadius.dp) },
                    effects = {
                         // Optimization: Defer expensive blur effects until the drawer is nearly static/open
                         // This significantly improves frame rate during the drag/animation.
                         if (swipeableState.offset.value <= 10f) {
                             if (glassSettings.vibrancyEnabled) vibrancy()
                             if (glassSettings.blurEnabled) blur(blurRadius.toPx())
                             if (glassSettings.lensEnabled) lens(
                                refractionHeight = glassSettings.refractionHeight.dp.toPx(),
                                refractionAmount = glassSettings.refractionAmount.dp.toPx(),
                                chromaticAberration = glassSettings.chromaticAberration
                            )
                         }
                    },
                    onDrawSurface = {
                         drawRect(panelColor.copy(alpha = panelAlpha))
                    }
                )
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    // Prevent clicks passing through and clear focus
                    focusManager.clearFocus()
                }
                .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            // Drag Handle Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                 Box(
                     modifier = Modifier
                        .width(48.dp)
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.3f), CircleShape)
                 )
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text("Search apps...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF6366F1),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color(0xFF6366F1),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )

            Spacer(modifier = Modifier.height(16.dp))

            // App Grid
            val listState = rememberLazyGridState()

            // Nested scroll to detect overscroll at top
            val nestedScrollConnection = remember {
                object : NestedScrollConnection {
                    // We removed custom onPreScroll and onPostScroll logic because `swipeable`
                    // should handle drag gestures if configured correctly.
                    // The main issue was aggressive auto-closing on fling.

                    // We only want to handle FLING here.
                    // Drag is handled by the parent swipeable modifier if the child (LazyGrid)
                    // cannot scroll further up.
                    // However, LazyGrid consumes all drag events usually.

                    // To solve "scrolling up closes it": We must prevent the swipeable from seeing the drag
                    // UNLESS we are truly at the top and pulling down.

                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        // If user is scrolling DOWN (y > 0) and we are at the top of the list,
                        // we want the parent Swipeable to take over.
                        // By returning Offset.Zero, we say "we didn't consume it",
                        // but usually the parent needs to be fed the delta manually if nested scrolling isn't fully automatic.

                        // BUT, if we are scrolling UP (y < 0), we want the list to handle it.
                        return Offset.Zero
                    }

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        // If the list has reached the top and there is still downward scroll available,
                        // feed it to the swipeable state to drag the drawer down.
                        if (available.y > 0 && source == NestedScrollSource.Drag) {
                            scope.launch {
                                swipeableState.performDrag(available.y)
                            }
                            return available
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        // If the drawer is partially dragged (offset > 0), we must consume the fling
                        // to ensure it settles to an anchor (Open or Closed).
                        if (swipeableState.offset.value > 0f) {
                            swipeableState.performFling(available.y)
                            return available
                        }
                        return Velocity.Zero
                    }

                    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                        // We REMOVE the logic that auto-closes the drawer on rapid downward fling (available.y > 0).
                        // This was likely causing the "auto closes when scrolling to top" issue.
                        // When a user flings the list up to reach the top, the momentum might result in
                        // a positive 'available' velocity once it hits the top edge.
                        // By removing the animateTo(0) call here, the drawer stays open unless explicitly dragged.
                        return Velocity.Zero
                    }
                }
            }

            LazyVerticalGrid(
                state = listState,
                columns = GridCells.Adaptive(minSize = 72.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .nestedScroll(nestedScrollConnection)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppDrawerItem(
                        app = app,
                        glassSettings = glassSettings,
                        onClick = { onAppClick(app.packageName) },
                        onLongClick = { appForOptions = app }
                    )
                }
            }
        }
    }

    // Options Dialog
    if (appForOptions != null && !showEditDialog) {
        AppOptionsDialog(
            app = appForOptions!!,
            onDismiss = { appForOptions = null },
            onEdit = { showEditDialog = true },
            onKill = {
                val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                am.killBackgroundProcesses(appForOptions!!.packageName)
                android.widget.Toast.makeText(context, "Killed ${appForOptions!!.label}", android.widget.Toast.LENGTH_SHORT).show()
                appForOptions = null
            },
            onUninstall = {
                val intent = android.content.Intent(android.content.Intent.ACTION_DELETE)
                intent.data = android.net.Uri.parse("package:${appForOptions!!.packageName}")
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                appForOptions = null
            }
        )
    }

    // Edit Dialog
    if (showEditDialog && appForOptions != null) {
        EditAppInfoDialog(
            app = appForOptions!!,
            onDismiss = { showEditDialog = false; appForOptions = null },
            onSave = { newLabel, newIconUri ->
                scope.launch(Dispatchers.IO) {
                    val metadata = AppMetadata(
                        label = if (newLabel != appForOptions!!.label) newLabel else null,
                        customIconUri = newIconUri
                    )
                    AppMetadataRepository.saveMetadata(context, appForOptions!!.packageName, metadata)
                    withContext(Dispatchers.Main) {
                        onRefreshApps()
                        showEditDialog = false
                        appForOptions = null
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerItem(
    app: AvailableApp,
    glassSettings: LiquidGlassSettings,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cornerRadius = glassSettings.iconCornerRadius.dp
    val tintColor = Color(glassSettings.panelTintColor)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(
                color = tintColor.copy(alpha = glassSettings.iconBackgroundAlpha),
                shape = RoundedCornerShape(cornerRadius)
            )
            .padding(8.dp)
    ) {
        val iconPack = if (glassSettings.useIconPackInAppDrawer) glassSettings.iconPackPackageName else null

        // Fast path: Check memory cache first
        val memoryIcon = remember(app, iconPack) {
            AppIconCache.getIconFromMemory(context, app.componentName, 192, app.customIconUri, iconPack)
        }

        val iconBitmap = if (memoryIcon != null) {
             remember { androidx.compose.runtime.mutableStateOf(memoryIcon.asImageBitmap()) }
        } else {
             produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, app.componentName, app.customIconUri, iconPack) {
                 withContext(Dispatchers.IO) {
                     val loaded = AppIconCache.loadIcon(context, app.componentName, 192, app.customIconUri, iconPack)
                     value = loaded?.asImageBitmap()
                 }
             }
        }

        // Icon content
        Box(
            modifier = Modifier
                .fillMaxSize(0.7f)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (iconBitmap.value != null) {
                Image(
                    bitmap = iconBitmap.value!!,
                    contentDescription = app.label,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                 // Fallback icon (should be very rare now)
                 Box(modifier = Modifier.fillMaxSize().background(Color.Gray))
            }
        }

        if (glassSettings.showAppLabels) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
