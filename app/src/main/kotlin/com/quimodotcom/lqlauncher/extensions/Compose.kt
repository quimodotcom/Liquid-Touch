package com.quimodotcom.lqlauncher.extensions

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView

/**
 * Extension function to easily set up Compose content in a View-based Activity
 * This allows gradual migration from View to Compose
 */
fun ComponentActivity.setComposeContent(content: @Composable () -> Unit) {
    setContent {
        content()
    }
}

/**
 * Extension function to create a ComposeView that can be added to existing ViewGroups
 * This allows mixing Compose and View components
 */
fun ViewGroup.addComposeView(content: @Composable () -> Unit): ComposeView {
    val composeView = ComposeView(context).apply {
        setContent {
            content()
        }
    }
    addView(composeView)
    return composeView
}

/**
 * Extension function to create a ComposeView without adding it
 * Useful when you need to add it at a specific position
 */
fun ViewGroup.createComposeView(content: @Composable () -> Unit): ComposeView {
    return ComposeView(context).apply {
        setContent {
            content()
        }
    }
}
