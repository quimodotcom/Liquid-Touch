package com.quimodotcom.lqlauncher.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.quimodotcom.lqlauncher.compose.launcher.LiquidGlassSettingsRepository

class SearchWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = LiquidGlassSettingsRepository.loadSettings(context)
        provideContent {
            GlanceTheme {
                SearchWidgetContent()
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun SearchWidgetContent() {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f))
                    .padding(horizontal = 16.dp)
                    .clickable(actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=")))),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Search...",
                    style = TextStyle(
                        color = ColorProvider(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f)),
                        fontSize = 14.sp
                    )
                )
            }
        }
    }
}

class SearchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SearchWidget()
}
