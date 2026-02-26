package com.quimodotcom.lqlauncher.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle

/**
 * A folder with liquid glass effect that can contain multiple apps
 * Features an expandable view with animated transitions
 * 
 * @param modifier Modifier to be applied to the folder
 * @param folderName Name of the folder
 * @param apps List of apps in the folder
 * @param onAppClick Callback when an app is clicked
 * @param backdrop The backdrop instance for the liquid glass effect
 * @param tint Optional tint color for the folder
 */
@Composable
fun LiquidGlassFolder(
    modifier: Modifier = Modifier,
    folderName: String,
    apps: List<FolderApp>,
    onAppClick: (FolderApp) -> Unit,
    backdrop: Backdrop,
    tint: Color = Color.Unspecified
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Collapsed folder icon
        Box(
            modifier = Modifier
                .size(56.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(14.dp) },
                    effects = {
                        vibrancy()
                        blur(15f.dp.toPx())
                        lens(6f.dp.toPx(), 12f.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 0.15f))
                        if (tint != Color.Unspecified) {
                            drawRect(tint.copy(alpha = 0.25f))
                        }
                    }
                )
                .clickable { isExpanded = !isExpanded }
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            // Show first 4 app icons in a mini grid preview
            if (apps.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    apps.take(4).chunked(2).forEach { row ->
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            row.forEach { app ->
                                app.icon?.let { icon ->
                                    Icon(
                                        painter = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.Unspecified
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Text(
            text = folderName,
            fontSize = 11.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        
        // Expanded folder content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .widthIn(max = 280.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(20.dp) },
                        effects = {
                            vibrancy()
                            blur(25f.dp.toPx())
                        },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = 0.2f))
                            if (tint != Color.Unspecified) {
                                drawRect(tint.copy(alpha = 0.15f))
                            }
                        }
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = folderName,
                        fontSize = 16.sp,
                        color = Color.White,
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(apps) { app ->
                            FolderAppItem(
                                app = app,
                                onClick = {
                                    onAppClick(app)
                                    isExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderAppItem(
    app: FolderApp,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        app.icon?.let { icon ->
            Icon(
                painter = icon,
                contentDescription = app.name,
                modifier = Modifier.size(40.dp),
                tint = Color.Unspecified
            )
        }
        Text(
            text = app.name,
            fontSize = 10.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
