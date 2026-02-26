package com.quimodotcom.lqlauncher.helpers

import com.quimodotcom.lqlauncher.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** Simple OpenWeatherMap client using HttpURLConnection and kotlinx.serialization. */
object WeatherRepository {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class OwmWeather(val id: Int, val main: String, val description: String, val icon: String)

    @Serializable
    private data class MainData(val temp: Double)

    // Current Weather Response
    @Serializable
    private data class CurrentWeatherResponse(
        @SerialName("dt") val dt: Long,
        val main: MainData,
        val weather: List<OwmWeather>
    )

    // Forecast Response
    @Serializable
    private data class ForecastList(
        @SerialName("dt") val dt: Long,
        val main: MainData,
        val weather: List<OwmWeather>
    )

    @Serializable
    private data class ForecastResponse(
        val list: List<ForecastList>
    )

    data class ForecastItem(val label: String, val temp: String, val iconCode: String)
    data class WeatherData(val currentTemp: String, val currentIcon: String, val hourly: List<ForecastItem>)

    /**
     * Fetches weather for the provided coordinates using standard Current + Forecast APIs.
     * This ensures compatibility with free API keys which do not support One Call.
     * Returns null if no API key is configured or on error.
     */
    fun fetchForecast(lat: Double, lon: Double, units: String = "imperial", apiKey: String? = null): WeatherData? {
        val key = apiKey ?: BuildConfig.OPENWEATHER_API_KEY
        if (key.isBlank()) return null

        val unitSymbol = if (units == "metric") "°C" else "°F"

        var connCurrent: HttpURLConnection? = null
        var connForecast: HttpURLConnection? = null

        return try {
            // 1. Fetch Current Weather
            val urlCurrent = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&units=$units&appid=$key"
            val uCurrent = URL(urlCurrent)
            connCurrent = (uCurrent.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }

            if (connCurrent.responseCode != 200) {
                 if (BuildConfig.DEBUG) android.util.Log.e("WeatherRepository", "Current weather fetch failed: ${connCurrent.responseCode}")
                 return null
            }

            val bodyCurrent = BufferedReader(InputStreamReader(connCurrent.inputStream)).use { it.readText() }
            val currentData = json.decodeFromString(CurrentWeatherResponse.serializer(), bodyCurrent)

            val currentTemp = String.format("%d%s", currentData.main.temp.toInt(), unitSymbol)
            val currentIcon = currentData.weather.firstOrNull()?.icon ?: ""

            // 2. Fetch Forecast (5 day / 3 hour)
            val urlForecast = "https://api.openweathermap.org/data/2.5/forecast?lat=$lat&lon=$lon&units=$units&appid=$key"
            val uForecast = URL(urlForecast)
            connForecast = (uForecast.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }

            val hourlyList = if (connForecast.responseCode == 200) {
                val bodyForecast = BufferedReader(InputStreamReader(connForecast.inputStream)).use { it.readText() }
                val forecastData = json.decodeFromString(ForecastResponse.serializer(), bodyForecast)

                // Take next 5 entries (which are 3-hour intervals)
                forecastData.list.take(5).mapIndexed { idx, f ->
                    val hours = (idx + 1) * 3
                    ForecastItem(
                        "+${hours}h",
                        String.format("%d%s", f.main.temp.toInt(), unitSymbol),
                        f.weather.firstOrNull()?.icon ?: ""
                    )
                }
            } else {
                emptyList()
            }

            // Combine current as the first "Now" item if needed, or just return struct
            // The UI expects a list of future forecasts, but let's stick to the requested structure.
            // We can prepend "Now" if we want, but the UI displays currentTemp separately.
            // The previous implementation had "Now" as the first item in hourly list.
            val finalHourly = mutableListOf<ForecastItem>()
            finalHourly.add(ForecastItem("Now", currentTemp, currentIcon))
            finalHourly.addAll(hourlyList)

            WeatherData(currentTemp, currentIcon, finalHourly)

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.e("WeatherRepository", "Failed to fetch weather: ${e.message}")
            e.printStackTrace()
            null
        } finally {
            connCurrent?.disconnect()
            connForecast?.disconnect()
        }
    }
}