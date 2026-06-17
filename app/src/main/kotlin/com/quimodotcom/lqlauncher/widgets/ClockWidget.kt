package com.quimodotcom.lqlauncher.widgets

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.quimodotcom.lqlauncher.R
import com.quimodotcom.lqlauncher.compose.launcher.LiquidGlassSettings
import com.quimodotcom.lqlauncher.compose.launcher.LiquidGlassSettingsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClockWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = LiquidGlassSettingsRepository.loadSettings(context)

        provideContent {
            GlanceTheme {
                ClockWidgetContent(settings)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun ClockWidgetContent(settings: LiquidGlassSettings) {
        val currentTime = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.2f))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AndroidRemoteViews(
                    remoteViews = RemoteViews(
                        "com.quimodotcom.lqlauncher",
                        R.layout.widget_text_clock
                    )
                )
                Text(
                    text = dateFormat.format(Date(currentTime)),
                    style = TextStyle(
                        color = ColorProvider(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f)),
                        fontSize = 14.sp
                    )
                )
            }
        }
    }
}

class ClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockWidget()
}
