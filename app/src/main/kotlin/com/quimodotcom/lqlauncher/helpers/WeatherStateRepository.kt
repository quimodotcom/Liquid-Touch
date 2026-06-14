package com.quimodotcom.lqlauncher.helpers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object WeatherStateRepository {
    private val _weatherData = MutableStateFlow<WeatherRepository.WeatherData?>(null)
    val weatherData: StateFlow<WeatherRepository.WeatherData?> = _weatherData.asStateFlow()

    fun update(data: WeatherRepository.WeatherData?) {
        _weatherData.value = data
    }
}
