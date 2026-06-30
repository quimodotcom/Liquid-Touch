package com.quimodotcom.lqlauncher.compose.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import com.quimodotcom.lqlauncher.helpers.LauncherUtils
import com.quimodotcom.lqlauncher.helpers.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GlassPanelBackground(
    item: LauncherItem.GlassPanel,
    backdrop: LayerBackdrop,
    glassSettings: LiquidGlassSettings,
    isEditMode: Boolean
) {
    val cornerRadius = glassSettings.panelCornerRadius.dp
    val customImageUri = item.customImageUri
    val panelTintColor = Color(item.tintColor.toInt())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius))
            .then(
                if (item.customImageUri != null) {
                    Modifier
                } else if (glassSettings.liquidGlassEnabled) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(cornerRadius) },
                        effects = {
                            if (glassSettings.vibrancyEnabled) vibrancy()
                            // Corrected: Respect panelBlurEnabled
                            if (glassSettings.blurEnabled && glassSettings.panelBlurEnabled) {
                                blur(glassSettings.blurRadius.dp.toPx())
                            }
                            if (glassSettings.lensEnabled) lens(
                                refractionHeight = glassSettings.refractionHeight.dp.toPx(),
                                refractionAmount = glassSettings.refractionAmount.dp.toPx(),
                                chromaticAberration = glassSettings.chromaticAberration
                            )
                        },
                        onDrawSurface = {
                            drawRect(panelTintColor.copy(alpha = glassSettings.panelBackgroundAlpha))
                        }
                    )
                } else {
                    Modifier.background(
                        color = Color.Black.copy(alpha = glassSettings.panelBackgroundAlpha),
                        shape = RoundedCornerShape(cornerRadius)
                    )
                }
            )
    ) {
        if (customImageUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(customImageUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.6f)
            )
        }
    }
}

@Composable
fun GlassPanelContent(
    item: LauncherItem.GlassPanel,
    glassSettings: LiquidGlassSettings,
    isEditMode: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (item.panelType == PanelType.SEARCH) 8.dp else 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (glassSettings.cyberpunkTheme) {
            when (item.panelType) {
                PanelType.CLOCK -> CyberpunkClock()
                PanelType.WEATHER -> CyberpunkWeatherPanelContent(glassSettings)
                PanelType.BATTERY -> CyberpunkBatteryPanelContent()
                else -> StandardPanelRouter(item, glassSettings, isEditMode)
            }
        } else {
            StandardPanelRouter(item, glassSettings, isEditMode)
        }
    }
}

@Composable
private fun StandardPanelRouter(
    item: LauncherItem.GlassPanel,
    glassSettings: LiquidGlassSettings,
    isEditMode: Boolean
) {
    when (item.panelType) {
        PanelType.CLOCK -> ClockPanelContent(glassSettings)
        PanelType.WEATHER -> WeatherPanelContent(glassSettings)
        PanelType.BATTERY -> BatteryPanelContent(glassSettings)
        PanelType.QUICK_SETTINGS -> QuickSettingsPanelContent()
        PanelType.MEDIA_CONTROL -> MediaControlPanelContent()
        PanelType.APPS -> AppGridPanelContent(item, glassSettings)
        PanelType.SEARCH -> BrowserSearchPanelContent(glassSettings.searchWidgetOpensBrowserOnTap, isEditMode)
        PanelType.PLAY_INTEGRITY -> PlayIntegrityPanelContent(glassSettings)
        else -> {
            if (item.title.isNotEmpty()) {
                Text(text = item.title, color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun CyberpunkWeatherPanelContent(glassSettings: LiquidGlassSettings) {
    var weatherData by remember { mutableStateOf<WeatherRepository.WeatherData?>(null) }
    LaunchedEffect(glassSettings.openWeatherApiKey) {
        if (glassSettings.openWeatherApiKey.isNotBlank()) {
            withContext(Dispatchers.IO) {
                weatherData = WeatherRepository.fetchForecast(
                    lat = 0.0, lon = 0.0,
                    units = if (glassSettings.weatherUnit == "C") "metric" else "imperial",
                    apiKey = glassSettings.openWeatherApiKey
                )
            }
        }
    }
    val temp = weatherData?.currentTemp ?: "22°C"
    val desc = "Sunny"
    CyberpunkWeather(temp, Icons.Rounded.WbSunny, desc)
}

@Composable
private fun CyberpunkBatteryPanelContent() {
    val context = LocalContext.current
    val batteryManager = remember { context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager }
    var level by remember { mutableIntStateOf(0) }
    var isCharging by remember { mutableStateOf(false) }
    var voltageMv by remember { mutableIntStateOf(0) }
    var currentUa by remember { mutableLongStateOf(0L) }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                voltageMv = intent?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
            }
        }
        context.registerReceiver(receiver, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            level = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val status = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_STATUS)
            isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL
            currentUa = batteryManager.getLongProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            delay(2000)
        }
    }

    CyberpunkBattery(level, isCharging, voltageMv, currentUa)
}

@Composable
fun ClockPanelContent(glassSettings: LiquidGlassSettings) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }
    val cal = Calendar.getInstance().apply { timeInMillis = currentTime }

    val hours = cal.get(Calendar.HOUR).toFloat() + cal.get(Calendar.MINUTE) / 60f
    val minutes = cal.get(Calendar.MINUTE).toFloat() + cal.get(Calendar.SECOND) / 60f
    val seconds = cal.get(Calendar.SECOND).toFloat()

    when (glassSettings.clockStyle) {
        "Headline" -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = timeFormat.format(Date(currentTime)),
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-2).sp
                )
                Text(
                    text = dateFormat.format(Date(currentTime)).uppercase(),
                    color = Color.White.copy(0.6f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
        "Vertical" -> {
            val h = SimpleDateFormat("HH", Locale.getDefault()).format(Date(currentTime))
            val m = SimpleDateFormat("mm", Locale.getDefault()).format(Date(currentTime))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = h,
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 40.sp
                )
                Text(
                    text = m,
                    color = Color(0xFF6366F1),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Light,
                    lineHeight = 40.sp
                )
            }
        }
        "Minimal" -> {
            Text(
                text = timeFormat.format(Date(currentTime)),
                color = Color.White,
                fontSize = 56.sp,
                fontWeight = FontWeight.Thin
            )
        }
        "Analog" -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(80.dp)) {
                        drawArc(
                            Color.White.copy(0.06f),
                            -90f,
                            360f,
                            false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f)
                        )
                        drawArc(
                            Color(0xFF60A5FA),
                            -90f,
                            (minutes / 60f) * 360f,
                            false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f, cap = StrokeCap.Round)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = timeFormat.format(Date(currentTime)),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        "Classic" -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(80.dp)) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val radius = size.minDimension / 2f
                        drawCircle(Color.White.copy(0.06f), radius = radius)

                        for (i in 0 until 60) {
                            val angle = i * 6f - 90f
                            val rad = Math.toRadians(angle.toDouble()).toFloat()
                            val inner = if (i % 5 == 0) radius * 0.78f else radius * 0.86f
                            val outer = radius * 0.95f
                            drawLine(
                                color = Color.White.copy(if (i % 5 == 0) 0.9f else 0.25f),
                                start = Offset(cx + inner * kotlin.math.cos(rad), cy + inner * kotlin.math.sin(rad)),
                                end = Offset(cx + outer * kotlin.math.cos(rad), cy + outer * kotlin.math.sin(rad)),
                                strokeWidth = if (i % 5 == 0) 2f else 1f
                            )
                        }

                        val hAngle = (hours / 12f) * 360f - 90f
                        val hRad = Math.toRadians(hAngle.toDouble()).toFloat()
                        drawLine(
                            Color.White,
                            Offset(cx, cy),
                            Offset(cx + radius * 0.45f * kotlin.math.cos(hRad), cy + radius * 0.45f * kotlin.math.sin(hRad)),
                            strokeWidth = 4f,
                            cap = StrokeCap.Round
                        )

                        val mAngle = (minutes / 60f) * 360f - 90f
                        val mRad = Math.toRadians(mAngle.toDouble()).toFloat()
                        drawLine(
                            Color.White,
                            Offset(cx, cy),
                            Offset(cx + radius * 0.65f * kotlin.math.cos(mRad), cy + radius * 0.65f * kotlin.math.sin(mRad)),
                            strokeWidth = 2.5f,
                            cap = StrokeCap.Round
                        )

                        val sAngle = (seconds / 60f) * 360f - 90f
                        val sRad = Math.toRadians(sAngle.toDouble()).toFloat()
                        drawLine(
                            Color(0xFFFF6B6B),
                            Offset(cx, cy),
                            Offset(cx + radius * 0.78f * kotlin.math.cos(sRad), cy + radius * 0.78f * kotlin.math.sin(sRad)),
                            strokeWidth = 1.6f,
                            cap = StrokeCap.Round
                        )
                        drawCircle(Color.White, radius = 4f)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = timeFormat.format(Date(currentTime)),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light
                )
                Text(
                    text = dateFormat.format(Date(currentTime)),
                    color = Color.White.copy(0.7f),
                    fontSize = 12.sp
                )
            }
        }
        else -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = timeFormat.format(Date(currentTime)),
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp
                )
                Text(
                    text = dateFormat.format(Date(currentTime)),
                    color = Color.White.copy(0.7f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun WeatherPanelContent(glassSettings: LiquidGlassSettings) {
    var fetched by remember { mutableStateOf<WeatherRepository.WeatherData?>(null) }
    val apiKey = glassSettings.openWeatherApiKey
    val units = if (glassSettings.weatherUnit == "C") "metric" else "imperial"

    LaunchedEffect(apiKey, units) {
        if (apiKey.isNotBlank()) {
            withContext(Dispatchers.IO) {
                fetched = WeatherRepository.fetchForecast(0.0, 0.0, units = units, apiKey = apiKey)
            }
        }
    }

    data class Forecast(val label: String, val temp: String, val icon: ImageVector)
    fun mapIconCode(code: String): ImageVector = when {
        code.startsWith("01") -> Icons.Rounded.WbSunny
        code.startsWith("02") || code.startsWith("03") || code.startsWith("04") -> Icons.Rounded.CloudQueue
        code.startsWith("09") || code.startsWith("10") -> Icons.Rounded.Grain
        code.startsWith("11") -> Icons.Rounded.FlashOn
        code.startsWith("13") -> Icons.Rounded.AcUnit
        else -> Icons.Rounded.Cloud
    }

    val forecasts = remember(fetched) {
        fetched?.hourly?.map { Forecast(it.label, it.temp, mapIconCode(it.iconCode)) } ?: listOf(
            Forecast("Now", "--°", Icons.Rounded.Cloud),
            Forecast("+3h", "--°", Icons.Rounded.Cloud)
        )
    }

    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(forecasts.size) {
        while (forecasts.size > 1) {
            delay(4000L)
            index = (index + 1) % forecasts.size
        }
    }

    val xOffset by animateFloatAsState(
        targetValue = -index * 120f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "weatherAnimation"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val current = forecasts.getOrNull(index) ?: forecasts[0]
        Icon(
            imageVector = current.icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(text = current.temp, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Light)
        Text(text = current.label, color = Color.White.copy(0.7f), fontSize = 12.sp)

        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .height(30.dp)
                .width(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(0.03f))
        ) {
            Row(
                modifier = Modifier
                    .offset(x = xOffset.dp)
                    .padding(horizontal = 4.dp)
            ) {
                forecasts.forEach { f ->
                    Row(
                        modifier = Modifier
                            .width(120.dp)
                            .fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(f.icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(f.label + ": " + f.temp, color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun BatteryPanelContent(glassSettings: LiquidGlassSettings) {
    val context = LocalContext.current
    val batteryManager = remember { context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager }
    var level by remember { mutableIntStateOf(0) }
    var isCharging by remember { mutableStateOf(false) }

    // Additional metrics
    var voltageMv by remember { mutableIntStateOf(0) }
    var currentUa by remember { mutableLongStateOf(0L) }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                voltageMv = intent?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
            }
        }
        context.registerReceiver(receiver, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            level = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val status = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_STATUS)
            isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL

            // Current in microamperes. Positive is charging, negative is discharging.
            currentUa = batteryManager.getLongProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

            delay(2000)
        }
    }

    val fillWidth by animateFloatAsState(targetValue = level / 100f, animationSpec = tween(800), label = "batteryFill")
    val idlePulse = rememberInfiniteTransition(label = "batteryPulse")
    val bob by idlePulse.animateFloat(
        0f,
        3f,
        infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "batteryBob"
    )

    val voltageV = voltageMv / 1000f
    val currentMa = currentUa / 1000f
    val wattageW = LauncherUtils.calculateWattage(currentUa, voltageMv)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .offset(y = bob.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val capW = w * 0.08f
                val bodyW = w - capW - 6f
                val bodyH = h * 0.5f
                val left = 3f
                val top = (h - bodyH) / 2f

                drawRoundRect(
                    Color.White.copy(0.12f),
                    Offset(left, top),
                    Size(bodyW, bodyH),
                    CornerRadius(10f)
                )

                val fillColor = when {
                    level > 80 -> Color(0xFF22C55E)
                    level > 20 -> Color(0xFFFBBF24)
                    else -> Color(0xFFEF4444)
                }

                drawRoundRect(
                    fillColor,
                    Offset(left, top),
                    Size(bodyW * fillWidth, bodyH),
                    CornerRadius(10f)
                )

                drawRoundRect(
                    Color.White.copy(0.12f),
                    Offset(left + bodyW + 3f, top + bodyH * 0.25f),
                    Size(capW, bodyH * 0.5f),
                    CornerRadius(4f)
                )
            }
        }
        Text(text = "$level%", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)

        if (voltageMv > 0) {
            Text(
                text = String.format("%.2fV  %.2fW", voltageV, kotlin.math.abs(wattageW)),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp
            )
            Text(
                text = String.format("%.0f mA", currentMa),
                color = if (currentMa > 0) Color(0xFF22C55E) else Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }

        if (isCharging) {
            Text("Charging", color = Color(0xFF22C55E), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun QuickSettingsPanelContent() {
    val context = LocalContext.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickSettingButton(icon = Icons.Rounded.Wifi, enabled = true) {
            try {
                context.startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
            } catch (e: Exception) {
            }
        }
        QuickSettingButton(icon = Icons.Rounded.Bluetooth, enabled = false) {
            try {
                context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
            } catch (e: Exception) {
            }
        }
        QuickSettingButton(icon = Icons.Rounded.FlashlightOn, enabled = false) {
            // Flashlight toggle logic
        }
        QuickSettingButton(icon = Icons.Rounded.Settings, enabled = false) {
            try {
                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
            }
        }
    }
}

@Composable
private fun QuickSettingButton(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (enabled) Color(0xFF6366F1) else Color.White.copy(0.1f))
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun MediaControlPanelContent() {
    val mediaState by com.quimodotcom.lqlauncher.services.MediaStateRepository.mediaState.collectAsState()
    val view = LocalView.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (mediaState == null) {
            Text("No Media", color = Color.White.copy(0.4f), fontSize = 12.sp)
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    com.quimodotcom.lqlauncher.services.MediaStateRepository.skipToPrevious()
                }) {
                    Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White)
                }
                Surface(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        com.quimodotcom.lqlauncher.services.MediaStateRepository.playPause()
                    },
                    shape = CircleShape,
                    color = Color.White.copy(0.1f)
                ) {
                    Icon(
                        if (mediaState?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(32.dp)
                    )
                }
                IconButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    com.quimodotcom.lqlauncher.services.MediaStateRepository.skipToNext()
                }) {
                    Icon(Icons.Rounded.SkipNext, null, tint = Color.White)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                mediaState?.title ?: "",
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AppGridPanelContent(item: LauncherItem.GlassPanel, glassSettings: LiquidGlassSettings) {
    val context = LocalContext.current
    val apps = item.apps
    if (apps.isEmpty()) {
        Text("Empty", color = Color.White.copy(0.4f), fontSize = 12.sp)
        return
    }

    // Auto-calculate columns based on count to keep icons reasonably sized
    val cols = when {
        apps.size <= 1 -> 1
        apps.size <= 4 -> 2
        apps.size <= 9 -> 3
        else -> 4
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp), // Internal padding to prevent edge-touching
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false
    ) {
        items(apps) { pkg ->
            val icon = remember(pkg) {
                try {
                    context.packageManager.getApplicationIcon(pkg)
                } catch (e: Exception) {
                    null
                }
            }
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { launchAppLocal(context, pkg) },
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon.toBitmap(96, 96).asImageBitmap(),
                        null,
                        modifier = Modifier.fillMaxSize(0.85f) // Better scaling within the subgrid cell
                    )
                } else {
                    Icon(Icons.Rounded.Android, null, tint = Color.White.copy(0.3f))
                }
            }
        }
    }
}

@Composable
fun BrowserSearchPanelContent(openBrowserOnTap: Boolean, isEditMode: Boolean) {
    var query by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val view = LocalView.current

    val searchAction = {
        if (query.isNotEmpty()) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query")))
            query = ""
            focusManager.clearFocus()
        } else {
            // If empty, always open browser regardless of setting if explicitly triggered by button
            context.startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER))
        }
    }

    if (openBrowserOnTap && !isEditMode) {
        // Render as a clean button that opens the browser immediately
        Surface(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                context.startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER))
            },
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.05f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Search...",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Rounded.Search,
                    null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        // Standard interactive text field
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            placeholder = { Text("Search...", color = Color.White.copy(0.4f), fontSize = 14.sp) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedBorderColor = Color.White.copy(0.3f),
                unfocusedBorderColor = Color.White.copy(0.1f),
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            ),
            trailingIcon = {
                IconButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    searchAction()
                }) {
                    Icon(Icons.Rounded.Search, null, tint = Color.White.copy(0.6f))
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                searchAction()
            }),
            enabled = !isEditMode
        )
    }
}

@Composable
private fun PlayIntegrityPanelContent(glassSettings: LiquidGlassSettings) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Rounded.Security,
            null,
            tint = if (glassSettings.playIntegrityEnabled) Color(0xFF6366F1) else Color.White.copy(0.3f)
        )
        Spacer(Modifier.height(4.dp))
        Text("Play Integrity", color = Color.White, fontSize = 12.sp)
        Text(
            if (glassSettings.playIntegrityEnabled) "Verified" else "Disabled",
            color = Color.White.copy(0.5f),
            fontSize = 10.sp
        )
    }
}

private fun launchAppLocal(context: Context, pkg: String) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) context.startActivity(intent)
    } catch (e: Exception) {
    }
}
