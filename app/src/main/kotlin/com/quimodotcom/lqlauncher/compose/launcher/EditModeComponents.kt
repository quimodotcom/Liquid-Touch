package com.quimodotcom.lqlauncher.compose.launcher

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import kotlin.math.roundToInt

import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import com.kyant.backdrop.backdrops.LayerBackdrop

/**
 * Edit mode wrapper that supports precise drag, scale and centered transform origin.
 */
@Composable
fun EditModeWrapper(
    item: LauncherItem,
    isSelected: Boolean,
    isEditMode: Boolean,
    cellWidth: Float,
    cellHeight: Float,
    gridColumns: Int,
    gridRows: Int,
    onSelect: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onResize: (Int, Int) -> Unit,
    onDelete: () -> Unit,
    dragOffset: Offset = Offset.Zero,
    onDragOffsetChange: (Offset) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isDragging by remember(item.gridX, item.gridY) { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.08f
            isSelected && isEditMode -> 1.0f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "scale"
    )

    val elevation by animateFloatAsState(
        targetValue = if (isDragging) 16f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "elevation"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isDragging -> Color(0xFF818CF8)
            isSelected && isEditMode -> Color(0xFF6366F1)
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "border"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .zIndex(if (isDragging) 100f else if (isSelected && isEditMode) 50f else 0f)
            .graphicsLayer {
                // Follow finger offset
                translationX = if (isDragging) dragOffset.x else 0f
                translationY = if (isDragging) dragOffset.y else 0f
                // Center the transform so scaling anchors in the middle
                transformOrigin = TransformOrigin.Center
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation
            }
            .then(
                if (isEditMode) {
                    Modifier
                        .border(2.dp, borderColor, RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onSelect() })
                        }
                        .pointerInput(isSelected, item.gridX, item.gridY, item.spanX, item.spanY) {
                            if (isSelected && cellWidth > 0 && cellHeight > 0) {
                                var currentDrag = Offset.Zero
                                detectDragGestures(
                                    onDragStart = {
                                        isDragging = true
                                        onDragStart()
                                        currentDrag = Offset.Zero
                                        onDragOffsetChange(Offset.Zero)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        currentDrag += dragAmount
                                        onDragOffsetChange(currentDrag)
                                    },
                                    onDragEnd = {
                                        val (newX, newY) = GridSnap.computeSnappedGridPosition(
                                            startGridX = item.gridX,
                                            startGridY = item.gridY,
                                            dragOffsetX = currentDrag.x,
                                            dragOffsetY = currentDrag.y,
                                            cellWidth = cellWidth,
                                            cellHeight = cellHeight,
                                            gridColumns = gridColumns,
                                            gridRows = gridRows,
                                            spanX = item.spanX,
                                            spanY = item.spanY
                                        )

                                        if (newX != item.gridX || newY != item.gridY) {
                                            onMove(newX, newY)
                                        }

                                        isDragging = false
                                        onDragEnd()
                                        onDragOffsetChange(Offset.Zero)
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        onDragEnd()
                                        onDragOffsetChange(Offset.Zero)
                                    }
                                )
                            }
                        }
                } else Modifier
            )
    ) {
        // Centered content so when scale < 1 it remains centered
        Box(contentAlignment = Alignment.Center) {
            content()
        }

        if (isEditMode && isSelected && !isDragging) {
            // Delete button
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 12.dp, y = (-12).dp)
                    .size(36.dp)
                    .background(Color(0xFFEF4444), CircleShape)
                    .pointerInput(Unit) { detectTapGestures(onTap = { onDelete() }) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (item is LauncherItem.GlassPanel) {
                ResizeHandle(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    gridColumns = gridColumns,
                    gridRows = gridRows,
                    item = item,
                    onResize = onResize
                )
            }
        }
    }
}

@Composable
private fun ResizeHandle(
    modifier: Modifier = Modifier,
    cellWidth: Float,
    cellHeight: Float,
    gridColumns: Int,
    gridRows: Int,
    item: LauncherItem.GlassPanel,
    onResize: (Int, Int) -> Unit
) {
    var isResizing by remember { mutableStateOf(false) }
    var resizeOffset by remember { mutableStateOf(Offset.Zero) }

    val handleSize by animateDpAsState(
        targetValue = if (isResizing) 36.dp else 28.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "handleSize"
    )

    val handleColor by animateColorAsState(
        targetValue = if (isResizing) Color(0xFF22C55E) else Color(0xFF6366F1),
        animationSpec = tween(150),
        label = "handleColor"
    )

    Box(
        modifier = modifier
            .offset(x = 8.dp, y = 8.dp)
            .size(handleSize)
            .background(handleColor, RoundedCornerShape(8.dp))
            .pointerInput(cellWidth, cellHeight, item.spanX, item.spanY) {
                if (cellWidth > 0 && cellHeight > 0) {
                    detectDragGestures(
                        onDragStart = {
                            isResizing = true
                            resizeOffset = Offset.Zero
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            resizeOffset += dragAmount
                        },
                        onDragEnd = {
                            val cellsX = (resizeOffset.x / cellWidth).roundToInt()
                            val cellsY = (resizeOffset.y / cellHeight).roundToInt()

                            val newSpanX = (item.spanX + cellsX).coerceIn(1, minOf(4, gridColumns - item.gridX))
                            val newSpanY = (item.spanY + cellsY).coerceIn(1, minOf(6, gridRows - item.gridY))

                            if (newSpanX != item.spanX || newSpanY != item.spanY) {
                                onResize(newSpanX, newSpanY)
                            }

                            isResizing = false
                            resizeOffset = Offset.Zero
                        },
                        onDragCancel = {
                            isResizing = false
                            resizeOffset = Offset.Zero
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.OpenInFull,
            contentDescription = "Resize",
            tint = Color.White,
            modifier = Modifier.size(if (isResizing) 22.dp else 18.dp)
        )
    }
}

@Composable
fun EditModeToolbar(
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    isEditMode: Boolean,
    onAddApp: () -> Unit,
    onAddPanel: () -> Unit,
    onAddFolder: () -> Unit,
    onChangeWallpaper: () -> Unit,
    onOpenSettings: () -> Unit,
    onExitEditMode: () -> Unit,
    glassSettings: com.quimodotcom.lqlauncher.compose.launcher.LiquidGlassSettings = com.quimodotcom.lqlauncher.compose.launcher.LiquidGlassSettings(),
    modifier: Modifier = Modifier
) {
    if (!isEditMode) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(glassSettings.panelCornerRadius.dp) },
                effects = {
                    if (glassSettings.vibrancyEnabled) vibrancy()
                    if (glassSettings.blurEnabled) blur(glassSettings.blurRadius.dp.toPx())
                    if (glassSettings.lensEnabled) lens(
                        refractionHeight = glassSettings.refractionHeight.dp.toPx(),
                        refractionAmount = glassSettings.refractionAmount.dp.toPx()
                    )
                },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = glassSettings.panelBackgroundAlpha * 0.08f))
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolbarButton(Icons.Rounded.Apps, "Add App", onClick = onAddApp)
        ToolbarButton(Icons.Rounded.Widgets, "Add Panel", onClick = onAddPanel)
        ToolbarButton(Icons.Rounded.Folder, "Add Folder", onClick = onAddFolder)
        ToolbarButton(Icons.Rounded.Wallpaper, "Wallpaper", onClick = onChangeWallpaper)
        ToolbarButton(Icons.Rounded.Settings, "Settings", onClick = onOpenSettings)
        ToolbarButton(Icons.Rounded.Check, "Done", onClick = onExitEditMode, primary = true)
    }
}

@Composable
private fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    val view = LocalView.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        IconButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
            modifier = Modifier
                .size(52.dp)
                .background(if (primary) Color(0xFF6366F1) else Color.White.copy(alpha = 0.12f), CircleShape)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
fun EditModeHint(
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    Box(
        modifier = modifier
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
            .padding(horizontal = 28.dp, vertical = 18.dp)
    ) {
        androidx.compose.material3.Text(
            text = "Long press to enter edit mode",
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun EmptyCellIndicator(
    gridX: Int,
    gridY: Int,
    isEditMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isEditMode) return

    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Add,
            contentDescription = "Add item",
            tint = Color.White.copy(alpha = 0.25f),
            modifier = Modifier.size(28.dp)
        )
    }
}
