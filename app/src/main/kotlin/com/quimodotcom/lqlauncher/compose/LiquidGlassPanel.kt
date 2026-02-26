package com.quimodotcom.lqlauncher.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle

/**
 * A panel with liquid glass effect for displaying app shortcuts, folders, or widgets
 * Users can add these panels to the home screen background
 * 
 * @param modifier Modifier to be applied to the panel
 * @param backdrop The backdrop instance for the liquid glass effect
 * @param blurRadius Blur radius in dp for the glass effect
 * @param cornerRadius Corner radius for the panel shape
 * @param backgroundColor Background color with transparency for glass effect
 * @param tint Optional tint color for the glass
 * @param content The content to be displayed inside the panel
 */
@Composable
fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    blurRadius: Dp = 20.dp,
    cornerRadius: Float = 0.3f,
    backgroundColor: Color = Color.White.copy(alpha = 0.15f),
    tint: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(16.dp) },
                effects = {
                    vibrancy()
                    blur(blurRadius.toPx())
                    lens(
                        refractionHeight = 12f.dp.toPx(),
                        refractionAmount = 16f.dp.toPx(),
                        chromaticAberration = true
                    )
                },
                onDrawSurface = {
                    drawRect(backgroundColor)
                    if (tint != Color.Unspecified) {
                        drawRect(tint.copy(alpha = 0.2f))
                    }
                }
            )
            .padding(16.dp),
        content = content
    )
}

/**
 * A simpler liquid glass panel without explicit backdrop parameter
 * Useful for static glass panels that don't need dynamic effects
 */
@Composable
fun StaticLiquidGlassPanel(
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidGlassPanel(
        modifier = modifier,
        backdrop = backdrop,
        blurRadius = 25.dp,
        cornerRadius = 0.25f,
        backgroundColor = Color.White.copy(alpha = 0.1f),
        content = content
    )
}
