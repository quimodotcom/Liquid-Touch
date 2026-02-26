package com.quimodotcom.lqlauncher.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle

/**
 * A widget container with liquid glass effect
 * Provides a frosted glass background for home screen widgets
 * 
 * @param modifier Modifier to be applied to the widget container
 * @param backdrop The backdrop instance for the liquid glass effect
 * @param blurRadius Blur radius in dp for the glass effect
 * @param backgroundColor Background color with transparency for glass effect
 * @param tint Optional tint color for the widget background
 * @param content The widget content to be displayed
 */
@Composable
fun LiquidGlassWidget(
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    blurRadius: Dp = 18.dp,
    backgroundColor: Color = Color.White.copy(alpha = 0.08f),
    tint: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(20.dp) },
                effects = {
                    vibrancy()
                    blur(blurRadius.toPx())
                },
                onDrawSurface = {
                    drawRect(backgroundColor)
                    if (tint != Color.Unspecified) {
                        drawRect(tint.copy(alpha = 0.15f))
                    }
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            content = content
        )
    }
}
