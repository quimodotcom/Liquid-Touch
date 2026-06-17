package com.quimodotcom.lqlauncher.widgets

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.quimodotcom.lqlauncher.helpers.WeatherStateRepository
import com.quimodotcom.lqlauncher.compose.launcher.LiquidGlassSettings
import com.quimodotcom.lqlauncher.compose.launcher.LiquidGlassSettingsRepository

class WeatherWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = LiquidGlassSettingsRepository.loadSettings(context)
        provideContent {
            val weatherData by WeatherStateRepository.weatherData.collectAsState()
            GlanceTheme {
                WeatherWidgetContent(weatherData?.location ?: "Unknown", weatherData?.currentTemp ?: "--°")
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun WeatherWidgetContent(location: String, temp: String) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.2f))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = location,
                    style = TextStyle(
                        color = ColorProvider(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = temp,
                    style = TextStyle(
                        color = ColorProvider(androidx.compose.ui.graphics.Color.White),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}
