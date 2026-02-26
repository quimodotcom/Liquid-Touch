package com.quimodotcom.lqlauncher.compose.launcher

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import coil.compose.AsyncImage
import coil.request.ImageRequest
private enum class InteractionType {
    None, Scale, OffsetX, OffsetY
}

/**
 * Wallpaper picker dialog with support for Background and Foreground (Subject) layers
 */
@Composable
fun WallpaperPickerDialog(
    currentWallpaperUri: String?,
    useSystemWallpaper: Boolean,
    onWallpaperSelected: (String?) -> Unit, // null = use system wallpaper
    onWallpaperPermissionGranted: () -> Unit,
    currentSubjectUri: String? = null,
    subjectMatchWallpaper: Boolean = true,
    subjectScale: Float = 1f,
    subjectOffsetX: Float = 0f,
    subjectOffsetY: Float = 0f,
    onSubjectSelected: ((String?) -> Unit)? = null,
    onSubjectConfigChanged: ((Boolean, Float, Float, Float) -> Unit)? = null,
    onInteractionStart: () -> Unit = {},
    onInteractionEnd: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    val wallpaperPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // Permission launcher for system wallpaper access
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            onWallpaperPermissionGranted()
            onWallpaperSelected(null)
        } else {
            android.widget.Toast.makeText(context, "Permission denied", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Image picker launcher for Background
    val backgroundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val persistedUri = persistWallpaperUri(context, it)
            onWallpaperSelected(persistedUri)
        }
    }

    // Image picker launcher for Subject
    val subjectPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val persistedUri = persistWallpaperUri(context, it)
            onSubjectSelected?.invoke(persistedUri)
        }
    }
    
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Background, 1 = Subject
    var activeInteraction by remember { mutableStateOf(InteractionType.None) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val containerColor by androidx.compose.animation.animateColorAsState(
            targetValue = if (activeInteraction != InteractionType.None) Color.Transparent else Color(0xFF1E1E2E)
        )

        val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (activeInteraction != InteractionType.None) 0f else 1f
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = containerColor,
            shadowElevation = if (activeInteraction != InteractionType.None) 0.dp else 6.dp
        ) {
            // Scrollable column to ensure Done button is always visible on small screens
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                // Header Group - hide when interacting
                if (activeInteraction == InteractionType.None) {
                    Text(
                        "Background & Layers",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )

                    Spacer(Modifier.height(16.dp))

                    // Tabs
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF6366F1),
                        indicator = { tabPositions ->
                            TabRowDefaults.Indicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = Color(0xFF6366F1)
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                selectedTab = 0
                            },
                            text = { Text("Background") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                selectedTab = 1
                            },
                            text = { Text("Subject Layer") }
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                }
                
                if (selectedTab == 0 && activeInteraction == InteractionType.None) {
                    // === BACKGROUND TAB ===

                    // System wallpaper option
                    WallpaperOption(
                        icon = Icons.Rounded.Smartphone,
                        title = "System Wallpaper",
                        description = "Use your device wallpaper",
                        isSelected = useSystemWallpaper,
                        onClick = {
                            if (useSystemWallpaper) {
                                onWallpaperSelected(null)
                            } else {
                                permissionLauncher.launch(wallpaperPermission)
                            }
                        }
                    )

                    Spacer(Modifier.height(12.dp))

                    // Pick from gallery option
                    WallpaperOption(
                        icon = Icons.Rounded.PhotoLibrary,
                        title = "Choose from Gallery",
                        description = "Select an image from your photos",
                        isSelected = !useSystemWallpaper && currentWallpaperUri != null,
                        onClick = { backgroundPickerLauncher.launch("image/*") }
                    )

                    Spacer(Modifier.height(24.dp))

                    // Preview section
                    Text("Current Background", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF2E2E3E)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (useSystemWallpaper) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.Smartphone, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Using system wallpaper", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                            }
                        } else if (currentWallpaperUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(currentWallpaperUri).crossfade(true).build(),
                                contentDescription = "Current wallpaper",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                } else {
                    // === SUBJECT TAB ===

                    if (activeInteraction == InteractionType.None) {
                        Text(
                            "The subject layer sits between panels and app icons, creating a depth effect.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )

                        Spacer(Modifier.height(12.dp))

                        WallpaperOption(
                            icon = Icons.Rounded.PhotoLibrary,
                            title = "Select Subject Image",
                            description = "Pick a transparent PNG",
                            isSelected = currentSubjectUri != null,
                            onClick = { subjectPickerLauncher.launch("image/*") }
                        )

                        if (currentSubjectUri != null) {
                            Spacer(Modifier.height(12.dp))
                            WallpaperOption(
                                icon = Icons.Rounded.Close,
                                title = "Clear Subject",
                                description = "Remove the foreground layer",
                                isSelected = false,
                                onClick = { onSubjectSelected?.invoke(null) }
                            )

                            Spacer(Modifier.height(24.dp))

                            // Alignment Options
                            Text("Alignment", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                            Spacer(Modifier.height(8.dp))

                            // Match Switch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF2E2E3E))
                                    .clickable {
                                        onSubjectConfigChanged?.invoke(!subjectMatchWallpaper, subjectScale, subjectOffsetX, subjectOffsetY)
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Match Wallpaper Alignment", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Best for cutouts from the original wallpaper",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Switch(
                                    checked = subjectMatchWallpaper,
                                    onCheckedChange = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        onSubjectConfigChanged?.invoke(it, subjectScale, subjectOffsetX, subjectOffsetY)
                                    }
                                )
                            }
                        }
                    }

                    if (currentSubjectUri != null && !subjectMatchWallpaper) {
                        if (activeInteraction == InteractionType.None) Spacer(Modifier.height(16.dp))

                        // Scale Slider
                        if (activeInteraction == InteractionType.None || activeInteraction == InteractionType.Scale) {
                            Text("Scale: ${(subjectScale * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.bodyMedium)

                            val scaleInteraction = remember { MutableInteractionSource() }
                            LaunchedEffect(scaleInteraction) {
                                scaleInteraction.interactions.collect { interaction ->
                                    when (interaction) {
                                        is PressInteraction.Press, is DragInteraction.Start -> {
                                            activeInteraction = InteractionType.Scale
                                            onInteractionStart()
                                        }
                                        is PressInteraction.Release, is PressInteraction.Cancel, is DragInteraction.Stop, is DragInteraction.Cancel -> {
                                            activeInteraction = InteractionType.None
                                            onInteractionEnd()
                                        }
                                    }
                                }
                            }

                            Slider(
                                value = subjectScale,
                                onValueChange = {
                                    // Tick logic for scale (e.g. every 0.1)
                                    val oldQuantized = (subjectScale / 0.1f).toInt()
                                    val newQuantized = (it / 0.1f).toInt()
                                    if (oldQuantized != newQuantized) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                    onSubjectConfigChanged?.invoke(false, it, subjectOffsetX, subjectOffsetY)
                                },
                                valueRange = 0.1f..3f,
                                interactionSource = scaleInteraction
                            )
                        }

                        // Offset X
                        if (activeInteraction == InteractionType.None || activeInteraction == InteractionType.OffsetX) {
                            Text("Offset X: ${subjectOffsetX.toInt()}", color = Color.White, style = MaterialTheme.typography.bodyMedium)

                            val offsetXInteraction = remember { MutableInteractionSource() }
                            LaunchedEffect(offsetXInteraction) {
                                offsetXInteraction.interactions.collect { interaction ->
                                    when (interaction) {
                                        is PressInteraction.Press, is DragInteraction.Start -> {
                                            activeInteraction = InteractionType.OffsetX
                                            onInteractionStart()
                                        }
                                        is PressInteraction.Release, is PressInteraction.Cancel, is DragInteraction.Stop, is DragInteraction.Cancel -> {
                                            activeInteraction = InteractionType.None
                                            onInteractionEnd()
                                        }
                                    }
                                }
                            }

                            Slider(
                                value = subjectOffsetX,
                                onValueChange = {
                                    // Tick logic for offset (e.g. every 10 pixels)
                                    val oldQuantized = (subjectOffsetX / 10f).toInt()
                                    val newQuantized = (it / 10f).toInt()
                                    if (oldQuantized != newQuantized) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                    onSubjectConfigChanged?.invoke(false, subjectScale, it, subjectOffsetY)
                                },
                                valueRange = -500f..500f,
                                interactionSource = offsetXInteraction
                            )
                        }

                        // Offset Y
                        if (activeInteraction == InteractionType.None || activeInteraction == InteractionType.OffsetY) {
                            Text("Offset Y: ${subjectOffsetY.toInt()}", color = Color.White, style = MaterialTheme.typography.bodyMedium)

                            val offsetYInteraction = remember { MutableInteractionSource() }
                            LaunchedEffect(offsetYInteraction) {
                                offsetYInteraction.interactions.collect { interaction ->
                                    when (interaction) {
                                        is PressInteraction.Press, is DragInteraction.Start -> {
                                            activeInteraction = InteractionType.OffsetY
                                            onInteractionStart()
                                        }
                                        is PressInteraction.Release, is PressInteraction.Cancel, is DragInteraction.Stop, is DragInteraction.Cancel -> {
                                            activeInteraction = InteractionType.None
                                            onInteractionEnd()
                                        }
                                    }
                                }
                            }

                            Slider(
                                value = subjectOffsetY,
                                onValueChange = {
                                    // Tick logic for offset (e.g. every 10 pixels)
                                    val oldQuantized = (subjectOffsetY / 10f).toInt()
                                    val newQuantized = (it / 10f).toInt()
                                    if (oldQuantized != newQuantized) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                    onSubjectConfigChanged?.invoke(false, subjectScale, subjectOffsetX, it)
                                },
                                valueRange = -500f..500f,
                                interactionSource = offsetYInteraction
                            )
                        }
                    }

                    if (activeInteraction == InteractionType.None) {
                        Spacer(Modifier.height(24.dp))

                        Text("Current Subject", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF2E2E3E)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentSubjectUri != null) {
                                // In preview, we always fit so they can see what it is
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(currentSubjectUri).crossfade(true).build(),
                                    contentDescription = "Current subject",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text("No subject selected", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                
                if (activeInteraction == InteractionType.None) {
                    Spacer(Modifier.height(24.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onDismiss()
                        }) {
                            Text("Done", color = Color(0xFF6366F1))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF6366F1) else Color.Transparent
    val view = LocalView.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .background(
                if (isSelected) Color(0xFF6366F1).copy(alpha = 0.1f)
                else Color(0xFF2E2E3E)
            )
            .clickable(onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            })
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (isSelected) Color(0xFF6366F1) else Color(0xFF3E3E4E),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(Modifier.width(16.dp))
        
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
        
        if (isSelected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = Color(0xFF6366F1)
            )
        }
    }
}

/**
 * Persist the wallpaper URI by copying to app storage
 */
private fun persistWallpaperUri(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        uri.toString()
    } catch (e: Exception) {
        // If we can't persist permissions, copy the file
        val inputStream = context.contentResolver.openInputStream(uri)
        val file = java.io.File(context.filesDir, "wallpaper_${System.currentTimeMillis()}.jpg")
        inputStream?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        file.absolutePath
    }
}

/**
 * Color picker for panel tints
 */
@Composable
fun ColorPickerDialog(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val presetColors = listOf(
        Color(0xFF6366F1), // Indigo
        Color(0xFF8B5CF6), // Violet
        Color(0xFFEC4899), // Pink
        Color(0xFFEF4444), // Red
        Color(0xFFF97316), // Orange
        Color(0xFFFBBF24), // Amber
        Color(0xFF22C55E), // Green
        Color(0xFF14B8A6), // Teal
        Color(0xFF06B6D4), // Cyan
        Color(0xFF3B82F6), // Blue
        Color(0xFF64748B), // Slate
        Color(0xFFFFFFFF), // White
    )
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E1E2E)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    "Panel Color",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                
                Spacer(Modifier.height(24.dp))
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(presetColors) { color ->
                        val isSelected = color == currentColor
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
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
                                    tint = if (color == Color.White) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                TextButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onDismiss()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Done", color = Color(0xFF6366F1))
                }
            }
        }
    }
}
