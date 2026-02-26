package com.quimodotcom.lqlauncher.compose.launcher

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

val CyberpunkCyan = Color(0xFF00E5FF)
val CyberpunkMagenta = Color(0xFFFF00FF)
val CyberpunkYellow = Color(0xFFFFEA00)
val CyberpunkDark = Color(0xFF0D0D0D)

@Composable
fun CyberpunkClock() {
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(100) // Update faster for seconds/glitch
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val secondsFormat = remember { SimpleDateFormat("ss", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) } // Short date

    // Glitch effect state
    var glitchOffsetX by remember { mutableStateOf(0f) }
    var glitchColor by remember { mutableStateOf(CyberpunkCyan) }

    LaunchedEffect(Unit) {
        while (true) {
            if (Random.nextFloat() > 0.95f) {
                glitchOffsetX = Random.nextInt(-5, 5).toFloat()
                glitchColor = if (Random.nextBoolean()) CyberpunkMagenta else CyberpunkYellow
                delay(50)
                glitchOffsetX = 0f
                glitchColor = CyberpunkCyan
            }
            delay(100)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Grid background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 20.dp.toPx()
            for (x in 0 until (size.width / step).toInt()) {
                drawLine(
                    color = CyberpunkCyan.copy(alpha = 0.1f),
                    start = Offset(x * step, 0f),
                    end = Offset(x * step, size.height),
                    strokeWidth = 1f
                )
            }
            for (y in 0 until (size.height / step).toInt()) {
                drawLine(
                    color = CyberpunkCyan.copy(alpha = 0.1f),
                    start = Offset(0f, y * step),
                    end = Offset(size.width, y * step),
                    strokeWidth = 1f
                )
            }

            // Corner accents
            val cornerLen = 10.dp.toPx()
            val stroke = 2.dp.toPx()

            // Top Left
            drawLine(CyberpunkCyan, Offset(0f, 0f), Offset(cornerLen, 0f), stroke)
            drawLine(CyberpunkCyan, Offset(0f, 0f), Offset(0f, cornerLen), stroke)

            // Bottom Right
            drawLine(CyberpunkMagenta, Offset(size.width, size.height), Offset(size.width - cornerLen, size.height), stroke)
            drawLine(CyberpunkMagenta, Offset(size.width, size.height), Offset(size.width, size.height - cornerLen), stroke)
        }

        Column(horizontalAlignment = Alignment.End) {
            // Time with glitch
            Box {
                 Text(
                    text = timeFormat.format(Date(currentTime)),
                    color = glitchColor.copy(alpha = 0.5f),
                    fontSize = 48.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(x = glitchOffsetX.dp, y = 0.dp)
                )
                Text(
                    text = timeFormat.format(Date(currentTime)),
                    color = Color.White,
                    fontSize = 48.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = dateFormat.format(Date(currentTime)).uppercase(),
                    color = CyberpunkYellow,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = secondsFormat.format(Date(currentTime)),
                    color = CyberpunkMagenta,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CyberpunkWeather(
    temp: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        // Tech circle
        val infiniteTransition = rememberInfiniteTransition()
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(10000, easing = LinearEasing)
            )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2.5f

            // Outer ring
            drawCircle(
                color = CyberpunkCyan.copy(alpha = 0.2f),
                radius = radius,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Butt, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f))
            )

            // Rotating ring
            rotate(rotation) {
                 drawCircle(
                    color = CyberpunkMagenta.copy(alpha = 0.4f),
                    radius = radius * 0.8f,
                    style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Butt, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 20f), 0f))
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CyberpunkYellow,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = temp,
                color = Color.White,
                fontSize = 24.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description.uppercase(),
                color = CyberpunkCyan,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Label
        Text(
            text = "ENV_DATA",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        )
    }
}

@Composable
fun CyberpunkBattery(
    level: Int,
    isCharging: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Power bar
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.height(40.dp)
            ) {
                val bars = 10
                for (i in 0 until bars) {
                    val active = (level / 100f * bars).toInt() > i
                    val barHeight = 10.dp + (i * 3).dp

                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(barHeight)
                            .background(
                                color = if (active) {
                                    if (level < 20) Color.Red else if (isCharging) CyberpunkYellow.copy(alpha = pulseAlpha) else CyberpunkCyan
                                } else {
                                    Color.White.copy(alpha = 0.1f)
                                }
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "PWR",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(
                    text = "$level%",
                    color = if (level < 20) Color.Red else Color.White,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isCharging) {
                Text(
                    text = "CHARGING...",
                    color = CyberpunkYellow,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
