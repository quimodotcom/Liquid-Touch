package com.quimodotcom.lqlauncher.helpers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.quimodotcom.lqlauncher.compose.launcher.LiquidGlassSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

@Serializable
private data class GbWeatherSpec(
    val timestamp: Long? = null,
    val location: String? = null,
    val currentTemp: Int? = null,
    val todayMinTemp: Int? = null,
    val todayMaxTemp: Int? = null,
    val currentCondition: String? = null,
    val currentConditionCode: Int? = null,
    val forecasts: List<GbForecast>? = null
)

@Serializable
private data class GbForecast(
    val conditionCode: Int? = null,
    val maxTemp: Int? = null,
    val minTemp: Int? = null
)

class BreezyWeatherReceiver : BroadcastReceiver() {
    private val json = Json { ignoreUnknownKeys = true }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "nodomain.freeyourgadget.gadgetbridge.ACTION_GENERIC_WEATHER") {
            val weatherJson = intent.getStringExtra("WeatherJson")
            val weatherGz = intent.getByteArrayExtra("WeatherGz")

            try {
                if (weatherGz != null) {
                    val decompressed = decompressGzip(weatherGz)
                    val specs = json.decodeFromString<List<GbWeatherSpec>>(decompressed)
                    specs.firstOrNull()?.let { updateWeather(context, it) }
                } else if (weatherJson != null) {
                    val spec = json.decodeFromString<GbWeatherSpec>(weatherJson)
                    updateWeather(context, spec)
                }
            } catch (e: Exception) {
                Log.e("BreezyWeatherReceiver", "Error parsing weather data", e)
            }
        }
    }

    private fun decompressGzip(compressed: ByteArray): String {
        return GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader().use { it.readText() }
    }

    private fun updateWeather(context: Context, spec: GbWeatherSpec) {
        CoroutineScope(Dispatchers.IO).launch {
            val settings = LiquidGlassSettingsRepository.loadSettings(context)
            if (settings.weatherSource != "BreezyWeather") return@launch

            val unit = settings.weatherUnit
            val currentTempRaw = spec.currentTemp?.toDouble() ?: return@launch
            val currentTemp = formatTemp(currentTempRaw, unit)
            val iconCode = mapConditionCodeToIcon(spec.currentConditionCode ?: 800)

            val hourly = spec.forecasts?.take(5)?.mapIndexed { index, forecast ->
                val tempRaw = forecast.maxTemp?.toDouble() ?: 0.0
                WeatherRepository.ForecastItem(
                    label = "+${(index + 1) * 3}h", // Approximation
                    temp = formatTemp(tempRaw, unit),
                    iconCode = mapConditionCodeToIcon(forecast.conditionCode ?: 800)
                )
            } ?: emptyList()

            val finalHourly = mutableListOf<WeatherRepository.ForecastItem>()
            finalHourly.add(WeatherRepository.ForecastItem("Now", currentTemp, iconCode))
            finalHourly.addAll(hourly)

            val weatherData = WeatherRepository.WeatherData(
                currentTemp = currentTemp,
                currentIcon = iconCode,
                hourly = finalHourly
            )

            WeatherStateRepository.update(weatherData)
        }
    }

    private fun formatTemp(rawTemp: Double, unit: String): String {
        // Robust check: GadgetBridge docs use Kelvin (~270-310), but some apps might send Celsius (<100).
        val celsius = if (rawTemp > 150) {
            rawTemp - 273.15
        } else {
            rawTemp
        }

        val temp = if (unit == "C") {
            celsius
        } else {
            celsius * 9/5 + 32
        }
        return "${temp.toInt()}°${unit}"
    }

    private fun mapConditionCodeToIcon(code: Int): String {
        return when (code) {
            in 200..299 -> "11d" // Thunderstorm
            in 300..399 -> "09d" // Drizzle
            in 500..599 -> "10d" // Rain
            in 600..699 -> "13d" // Snow
            in 700..799 -> "50d" // Atmosphere
            800 -> "01d" // Clear
            801 -> "02d" // Few clouds
            802 -> "03d" // Scattered clouds
            803, 804 -> "04d" // Broken clouds
            else -> "01d"
        }
    }
}
