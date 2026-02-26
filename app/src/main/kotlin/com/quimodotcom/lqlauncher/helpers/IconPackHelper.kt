package com.quimodotcom.lqlauncher.helpers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.ConcurrentHashMap

data class IconPackInfo(
    val packageName: String,
    val label: String
)

object IconPackHelper {
    private const val TAG = "IconPackHelper"
    // Using simple Map since we replace the whole map on load
    private val appFilterCache = ConcurrentHashMap<String, Map<ComponentName, String>>()
    private val iconPackResCache = ConcurrentHashMap<String, Resources>()

    fun getIconPacks(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val iconPacks = mutableListOf<IconPackInfo>()
        val themes = mutableListOf<ResolveInfo>()

        // Common intents for icon packs
        val intentActions = listOf(
            "com.novalauncher.THEME",
            "org.adw.launcher.THEMES",
            "com.fede.launcher.THEME_IO",
            "com.anddoes.launcher.THEME"
        )

        for (action in intentActions) {
            try {
                themes.addAll(pm.queryIntentActivities(Intent(action), 0))
            } catch (e: Exception) {
                // Ignore
            }
        }

        // Remove duplicates based on package name
        val uniquePackages = themes.map { it.activityInfo.packageName }.toSet()

        for (pkg in uniquePackages) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                iconPacks.add(IconPackInfo(pkg, pm.getApplicationLabel(appInfo).toString()))
            } catch (e: Exception) {
                // Ignore
            }
        }

        return iconPacks.sortedBy { it.label }
    }

    private fun parseAppFilter(parser: XmlPullParser): Map<ComponentName, String> {
        val componentMap = mutableMapOf<ComponentName, String>()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (component != null && drawable != null) {
                    try {
                        // Format: ComponentInfo{package/class}
                        val cleanComponent = component.substring(
                            component.indexOf("{") + 1,
                            component.indexOf("}")
                        )
                        val parts = cleanComponent.split("/")
                        if (parts.size == 2) {
                            val pkg = parts[0]
                            var cls = parts[1]
                            if (cls.startsWith(".")) cls = pkg + cls
                            componentMap[ComponentName(pkg, cls)] = drawable
                        }
                    } catch (e: Exception) {
                        // Parse error for this item, skip
                    }
                }
            }
            eventType = parser.next()
        }
        return componentMap
    }

    fun loadAppFilter(context: Context, iconPackPackageName: String): Map<ComponentName, String> {
        if (appFilterCache.containsKey(iconPackPackageName)) {
            return appFilterCache[iconPackPackageName]!!
        }

        var componentMap: Map<ComponentName, String> = emptyMap()
        try {
            val pm = context.packageManager
            val resources = pm.getResourcesForApplication(iconPackPackageName)
            iconPackResCache[iconPackPackageName] = resources

            val resId = resources.getIdentifier("appfilter", "xml", iconPackPackageName)
            if (resId != 0) {
                val parser = resources.getXml(resId)
                componentMap = parseAppFilter(parser)
            } else {
                // Fallback to assets
                try {
                    val assetManager = resources.assets
                    val inputStream = assetManager.open("appfilter.xml")
                    val factory = XmlPullParserFactory.newInstance()
                    val parser = factory.newPullParser()
                    parser.setInput(inputStream, "UTF-8")
                    componentMap = parseAppFilter(parser)
                    inputStream.close()
                } catch (e: Exception) {
                    Log.w(TAG, "appfilter.xml not found in assets either for $iconPackPackageName")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading appfilter for $iconPackPackageName", e)
        }

        appFilterCache[iconPackPackageName] = componentMap
        return componentMap
    }

    fun getIconDrawable(context: Context, iconPackPackageName: String, componentName: ComponentName): Drawable? {
        if (iconPackPackageName.isEmpty()) return null

        val filter = loadAppFilter(context, iconPackPackageName)
        val drawableName = filter[componentName] ?: return null

        try {
            var res = iconPackResCache[iconPackPackageName]
            if (res == null) {
                res = context.packageManager.getResourcesForApplication(iconPackPackageName)
                iconPackResCache[iconPackPackageName] = res
            }

            val resId = res.getIdentifier(drawableName, "drawable", iconPackPackageName)
            if (resId != 0) {
                return res.getDrawable(resId, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading icon $drawableName from $iconPackPackageName", e)
        }
        return null
    }
}
