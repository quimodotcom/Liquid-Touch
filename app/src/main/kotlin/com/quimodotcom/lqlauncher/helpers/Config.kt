package com.quimodotcom.lqlauncher.helpers

import android.content.Context
import android.content.SharedPreferences
import com.quimodotcom.lqlauncher.R

class Config(val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    var wasHomeScreenInit: Boolean
        get() = prefs.getBoolean(WAS_HOME_SCREEN_INIT, false)
        set(wasHomeScreenInit) = prefs.edit().putBoolean(WAS_HOME_SCREEN_INIT, wasHomeScreenInit).apply()

    var homeColumnCount: Int
        get() = prefs.getInt(HOME_COLUMN_COUNT, COLUMN_COUNT)
        set(homeColumnCount) = prefs.edit().putInt(HOME_COLUMN_COUNT, homeColumnCount).apply()

    var homeRowCount: Int
        get() = prefs.getInt(HOME_ROW_COUNT, ROW_COUNT)
        set(homeRowCount) = prefs.edit().putInt(HOME_ROW_COUNT, homeRowCount).apply()

    var drawerColumnCount: Int
        get() = prefs.getInt(DRAWER_COLUMN_COUNT, context.resources.getInteger(R.integer.portrait_column_count))
        set(drawerColumnCount) = prefs.edit().putInt(DRAWER_COLUMN_COUNT, drawerColumnCount).apply()

    var showSearchBar: Boolean
        get() = prefs.getBoolean(SHOW_SEARCH_BAR, true)
        set(showSearchBar) = prefs.edit().putBoolean(SHOW_SEARCH_BAR, showSearchBar).apply()

    var closeAppDrawer: Boolean
        get() = prefs.getBoolean(CLOSE_APP_DRAWER, false)
        set(closeAppDrawer) = prefs.edit().putBoolean(CLOSE_APP_DRAWER, closeAppDrawer).apply()

    var autoShowKeyboardInAppDrawer: Boolean
        get() = prefs.getBoolean(AUTO_SHOW_KEYBOARD_IN_APP_DRAWER, false)
        set(autoShowKeyboardInAppDrawer) = prefs.edit()
            .putBoolean(AUTO_SHOW_KEYBOARD_IN_APP_DRAWER, autoShowKeyboardInAppDrawer).apply()

    // Clock face selection (0 = Classic, 1 = Minimal, 2 = Modern)
    var clockFace: Int
        get() = prefs.getInt(CLOCK_FACE, 0)
        set(clockFace) = prefs.edit().putInt(CLOCK_FACE, clockFace).apply()

    // OpenWeatherMap API key (optional, can be set from Settings)
    var openWeatherApiKey: String
        get() = prefs.getString(OPEN_WEATHER_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(OPEN_WEATHER_API_KEY, value).apply()

    // Whether the search panel opens the default browser on tap (default true)
    var searchWidgetOpensBrowserOnTap: Boolean
        get() = prefs.getBoolean(SEARCH_WIDGET_OPEN_BROWSER_ON_TAP, true)
        set(value) = prefs.edit().putBoolean(SEARCH_WIDGET_OPEN_BROWSER_ON_TAP, value).apply()
}
