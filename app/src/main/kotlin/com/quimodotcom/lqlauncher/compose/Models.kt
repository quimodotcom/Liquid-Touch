package com.quimodotcom.lqlauncher.compose

import androidx.compose.ui.graphics.painter.Painter

/**
 * Data class representing an app in a folder
 */
data class FolderApp(
    val name: String,
    val packageName: String,
    val icon: Painter?
)