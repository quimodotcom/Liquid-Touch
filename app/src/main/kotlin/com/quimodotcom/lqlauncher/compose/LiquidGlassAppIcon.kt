package com.quimodotcom.lqlauncher.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.lens
import com.kyant.shapes.RoundedRectangle

/**
 * An app icon with liquid glass effect
 *
 * @param modifier Modifier to be applied to the app icon
 * @param appName Name of the app
 * @param icon Icon painter for the app
 * @param onClick Callback when the app icon is clicked
 * @param backdrop The backdrop instance for the liquid glass effect
 * @param tileSize Size of the app tile in dp
 * @param tint Optional tint color for the glass effect
 */
@Composable
fun LiquidGlassAppIcon(
    modifier: Modifier = Modifier,
    appName: String,
    icon: Painter?,
    onClick: () -> Unit,
    backdrop: Backdrop,
    tileSize: Dp = 64.dp,
    glassSettings: com.quimodotcom.lqlauncher.compose.launcher.LiquidGlassSettings = com.quimodotcom.lqlauncher.compose.launcher.LiquidGlassSettings(),
    tint: Color = Color.Unspecified,
    showLabel: Boolean = true
) {
    val iconSize = tileSize * 0.7f
    val padding = tileSize * 0.1f
    val corner = glassSettings.iconCornerRadius.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(tileSize)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(corner) },
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
                        drawRect(Color.White.copy(alpha = glassSettings.iconBackgroundAlpha))
                        if (tint != Color.Unspecified) {
                            drawRect(tint.copy(alpha = 0.3f))
                        }
                    }
                )
                .padding(padding)
                .graphicsLayer { transformOrigin = TransformOrigin.Center }
        ) {


            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = appName,
                    modifier = Modifier.size(iconSize),
                    tint = Color.Unspecified
                )
            }
        }

        if (showLabel && glassSettings.showAppLabels) {
            Text(
                text = appName,
                fontSize = 11.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
