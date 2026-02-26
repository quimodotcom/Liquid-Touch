package com.quimodotcom.lqlauncher.helpers

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle

/**
 * Helper class to integrate liquid glass effects into the existing launcher UI
 * This allows gradual migration from View-based to Compose-based UI
 */
object LiquidGlassHelper {
    
    /**
     * Creates a ComposeView with liquid glass scaffold that can be added to ViewGroups
     * 
     * @param context The context to create the ComposeView
     * @param wallpaperPainter The wallpaper painter for the background
     * @param content The composable content with backdrop access
     * @return A ComposeView ready to be added to a ViewGroup
     */
    fun createLiquidGlassView(
        context: Context,
        wallpaperPainter: @Composable () -> Painter,
        content: @Composable (Backdrop) -> Unit
    ): ComposeView {
        return ComposeView(context).apply {
            setContent {
                val backdrop: LayerBackdrop = rememberLayerBackdrop()
                
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = wallpaperPainter(),
                        contentDescription = null,
                        modifier = Modifier
                            .layerBackdrop(backdrop)
                            .fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    
                    content(backdrop)
                }
            }
        }
    }
    
    /**
     * Wraps an existing View with a liquid glass effect
     * 
     * @param context The context to create the ComposeView
     * @param wallpaperPainter The wallpaper painter for the backdrop
     * @param viewFactory Factory function that creates the view to wrap
     * @param blurRadius The blur radius for the glass effect
     * @param backgroundColor The background color with transparency
     * @return A ComposeView that wraps the view with liquid glass effect
     */
    fun wrapWithLiquidGlass(
        context: Context,
        wallpaperPainter: @Composable () -> Painter,
        viewFactory: (Context) -> android.view.View,
        blurRadius: Dp = 20.dp,
        backgroundColor: Color = Color.White.copy(alpha = 0.12f)
    ): ComposeView {
        return ComposeView(context).apply {
            setContent {
                val backdrop: LayerBackdrop = rememberLayerBackdrop()
                
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = wallpaperPainter(),
                        contentDescription = null,
                        modifier = Modifier
                            .layerBackdrop(backdrop)
                            .fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedRectangle(16.dp) },
                                effects = {
                                    vibrancy()
                                    blur(blurRadius.toPx())
                                },
                                onDrawSurface = {
                                    drawRect(backgroundColor)
                                }
                            )
                    ) {
                        AndroidView(
                            factory = viewFactory,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Composable that provides liquid glass effect to content
 * This is the main building block for adding glass effects
 */
@Composable
fun LiquidGlassContainer(
    modifier: Modifier = Modifier,
    wallpaperPainter: Painter,
    blurRadius: Dp = 20.dp,
    backgroundColor: Color = Color.White.copy(alpha = 0.12f),
    tint: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop: LayerBackdrop = rememberLayerBackdrop()
    
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = wallpaperPainter,
            contentDescription = null,
            modifier = Modifier
                .layerBackdrop(backdrop)
                .fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(16.dp) },
                    effects = {
                        vibrancy()
                        blur(blurRadius.toPx())
                    },
                    onDrawSurface = {
                        drawRect(backgroundColor)
                        if (tint != Color.Unspecified) {
                            drawRect(tint.copy(alpha = 0.2f))
                        }
                    }
                ),
            content = content
        )
    }
}
