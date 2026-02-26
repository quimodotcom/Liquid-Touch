package com.quimodotcom.lqlauncher.compose.launcher

import kotlin.test.Test
import kotlin.test.assertEquals

class GridSnapTest {
    @Test
    fun `snaps based on item origin and rounds to nearest cell`() {
        val (x, y) = GridSnap.computeSnappedGridPosition(
            startGridX = 1,
            startGridY = 2,
            dragOffsetX = 0.6f * 100f,  // move a bit more than half a cell
            dragOffsetY = 0.4f * 100f,  // move less than half a cell
            cellWidth = 100f,
            cellHeight = 100f,
            gridColumns = 4,
            gridRows = 6,
            spanX = 1,
            spanY = 1,
        )

        assertEquals(2, x)
        assertEquals(2, y)
    }

    @Test
    fun `clamps to grid bounds considering item span`() {
        val (x, y) = GridSnap.computeSnappedGridPosition(
            startGridX = 3,
            startGridY = 5,
            dragOffsetX = 10_000f,
            dragOffsetY = 10_000f,
            cellWidth = 100f,
            cellHeight = 100f,
            gridColumns = 4,
            gridRows = 6,
            spanX = 2,
            spanY = 2,
        )

        // maxX = columns - spanX = 2, maxY = rows - spanY = 4
        assertEquals(2, x)
        assertEquals(4, y)
    }

    @Test
    fun `returns original position when cell size is not available`() {
        val (x, y) = GridSnap.computeSnappedGridPosition(
            startGridX = 1,
            startGridY = 1,
            dragOffsetX = 999f,
            dragOffsetY = 999f,
            cellWidth = 0f,
            cellHeight = 0f,
            gridColumns = 4,
            gridRows = 6,
            spanX = 1,
            spanY = 1,
        )

        assertEquals(1, x)
        assertEquals(1, y)
    }
}
