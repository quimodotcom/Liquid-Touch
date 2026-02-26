package com.quimodotcom.lqlauncher.compose.launcher

import kotlin.math.roundToInt

/**
 * Utilities for snapping edit-mode drag positions to grid coordinates.
 */
object GridSnap {
    /**
     * Compute the snapped (gridX, gridY) for an item after it has been dragged by (dragOffsetX, dragOffsetY)
     * in pixels.
     *
     * Important: We snap based on the dragged item's top-left (origin) position, not on the pointer position.
     * This avoids off-by-one cell jumps depending on where inside the item the drag started.
     */
    fun computeSnappedGridPosition(
        startGridX: Int,
        startGridY: Int,
        dragOffsetX: Float,
        dragOffsetY: Float,
        cellWidth: Float,
        cellHeight: Float,
        gridColumns: Int,
        gridRows: Int,
        spanX: Int,
        spanY: Int,
    ): Pair<Int, Int> {
        if (cellWidth <= 0f || cellHeight <= 0f) {
            return startGridX to startGridY
        }

        val rawGridX = ((startGridX * cellWidth) + dragOffsetX) / cellWidth
        val rawGridY = ((startGridY * cellHeight) + dragOffsetY) / cellHeight

        // Use round-to-nearest for a "snap" feel. floor() biases toward top/left and can feel jumpy.
        val snappedX = rawGridX.roundToInt()
        val snappedY = rawGridY.roundToInt()

        val maxX = (gridColumns - spanX).coerceAtLeast(0)
        val maxY = (gridRows - spanY).coerceAtLeast(0)

        return snappedX.coerceIn(0, maxX) to snappedY.coerceIn(0, maxY)
    }
}
