package com.quimodotcom.lqlauncher.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryCalculationTest {

    @Test
    fun testWattageCalculation() {
        // Example from user: 4.13V, 3.454A -> ~14.27W
        val voltageMv = 4130
        val currentUa = 3_454_000L

        val wattage = LauncherUtils.calculateWattage(currentUa, voltageMv)

        // 4.13 * 3.454 = 14.26502
        assertEquals(14.26502f, wattage, 0.01f)
    }

    @Test
    fun testWattageCalculationDischarging() {
        // Discharging at 500mA, 3.8V
        val voltageMv = 3800
        val currentUa = -500_000L

        val wattage = LauncherUtils.calculateWattage(currentUa, voltageMv)

        // -0.5 * 3.8 = -1.9
        assertEquals(-1.9f, wattage, 0.01f)
    }

    @Test
    fun testWattageZero() {
        assertEquals(0f, LauncherUtils.calculateWattage(0, 4000), 0.001f)
        assertEquals(0f, LauncherUtils.calculateWattage(1000000, 0), 0.001f)
    }
}
