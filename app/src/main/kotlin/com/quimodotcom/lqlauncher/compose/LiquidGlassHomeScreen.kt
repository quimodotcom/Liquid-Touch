package com.quimodotcom.lqlauncher.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * Data class representing an app
 */
data class AppData(
    val name: String,
    val packageName: String,
    val icon: Painter?
)

/**
 * Data class representing a folder
 */
data class FolderData(
    val name: String,
    val apps: List<FolderApp>
)

/**
 * Main home screen with liquid glass components
 * Features a wallpaper background with liquid glass panels, folders, and app icons
 * 
 * @param modifier Modifier to be applied to the home screen
 * @param wallpaperPainter The wallpaper to display as background
 * @param apps List of apps to display
 * @param folders List of folders to display
 * @param onAppClick Callback when an app is clicked
 * @param liquidGlassPanels Optional list of custom liquid glass panels to add
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiquidGlassHomeScreen(
    modifier: Modifier = Modifier,
    wallpaperPainter: Painter,
    apps: List<AppData>,
    folders: List<FolderData>,
    onAppClick: (AppData) -> Unit,
    liquidGlassPanels: List<LiquidGlassPanelConfig> = emptyList(),
    appTileScale: Float = 1.0f
) {
    // Create the backdrop that will provide the liquid glass effect
    val backdrop: LayerBackdrop = rememberLayerBackdrop()
    val context = LocalContext.current

    // Load LiquidGlass settings
    var glassSettings by remember { mutableStateOf(com.quimodotcom.lqlauncher.compose.launcher.LiquidGlassSettings()) }
    LaunchedEffect(Unit) {
        glassSettings = com.quimodotcom.lqlauncher.compose.launcher.LiquidGlassSettingsRepository.loadSettings(context)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                detectDragGestures(
                    onDragStart = { total = 0f },
                    onDrag = { change, dragAmount ->
                        total += dragAmount.y
                        // negative => upwards
                        if (total < -300f) {
                            // App Drawer removed: upward-swipe no longer opens any activity.
                            // Previously this launched AppDrawerActivity; kept as no-op per user request.
                            total = 0f
                            change.consume()
                        }
                    },
                    onDragEnd = { total = 0f },
                    onDragCancel = { total = 0f }
                )
            }
    ) {
        // Background wallpaper with backdrop layer
        Image(
            painter = wallpaperPainter,
            contentDescription = null,
            modifier = Modifier
                .layerBackdrop(backdrop)
                .fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Content overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Custom liquid glass panels that users can add
            liquidGlassPanels.forEach { panelConfig ->
                LiquidGlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(panelConfig.modifier),
                    backdrop = backdrop,
                    blurRadius = panelConfig.blurRadius,
                    backgroundColor = panelConfig.backgroundColor,
                    tint = panelConfig.tint
                ) {
                    panelConfig.content()
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Folders section with liquid glass effect
            if (folders.isNotEmpty()) {
                LiquidGlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    backdrop = backdrop,
                    blurRadius = 22.dp,
                    backgroundColor = Color.White.copy(alpha = 0.12f)
                ) {
                    Column {
                        Text(
                            text = "Folders",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(items = folders, key = { it.name }) { folder ->
                                Box(
                                    modifier = Modifier
                                        .animateContentSize()
                                        .fillMaxWidth()
                                        .aspectRatio(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LiquidGlassFolder(
                                        folderName = folder.name,
                                        apps = folder.apps,
                                        onAppClick = { app ->
                                            onAppClick(AppData(app.name, app.packageName, app.icon))
                                        },
                                        backdrop = backdrop
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Apps grid with liquid glass effect
            LiquidGlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                backdrop = backdrop,
                blurRadius = 20.dp,
                backgroundColor = Color.White.copy(alpha = 0.1f)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = apps, key = { it.packageName }) { app ->
                        Box(
                            modifier = Modifier
                                .animateContentSize()
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            LiquidGlassAppIcon(
                                appName = app.name,
                                icon = app.icon,
                                onClick = { onAppClick(app) },
                                backdrop = backdrop,
                                tileSize = 64.dp * appTileScale
                            )
                        }
                    }
                }
            }
        }


    } // end Box
}


/**
 * Configuration for a custom liquid glass panel that users can add to the home screen
 */
data class LiquidGlassPanelConfig(
    val modifier: Modifier = Modifier,
    val blurRadius: androidx.compose.ui.unit.Dp = 20.dp,
    val backgroundColor: Color = Color.White.copy(alpha = 0.15f),
    val tint: Color = Color.Unspecified,
    val content: @Composable () -> Unit
)

/**
 * Provides the backdrop for use by child composables
 * This allows components outside the home screen to access the backdrop
 */
@Composable
fun LiquidGlassScaffold(
    modifier: Modifier = Modifier,
    wallpaperPainter: Painter,
    content: @Composable (Backdrop) -> Unit
) {
    val backdrop: LayerBackdrop = rememberLayerBackdrop()
    
    Box(modifier = modifier.fillMaxSize()) {
        // Background wallpaper with backdrop layer
        Image(
            painter = wallpaperPainter,
            contentDescription = null,
            modifier = Modifier
                .layerBackdrop(backdrop)
                .fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Provide backdrop to content
        content(backdrop)
    }
}
