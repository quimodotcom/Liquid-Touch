package com.quimodotcom.lqlauncher

import android.app.Application
import android.content.IntentFilter
import android.os.Build
import com.quimodotcom.lqlauncher.helpers.BreezyWeatherReceiver

class LqApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val breezyWeatherReceiver = BreezyWeatherReceiver()
        val filter = IntentFilter("nodomain.freeyourgadget.gadgetbridge.ACTION_GENERIC_WEATHER")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(breezyWeatherReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(breezyWeatherReceiver, filter)
        }
    }
}
