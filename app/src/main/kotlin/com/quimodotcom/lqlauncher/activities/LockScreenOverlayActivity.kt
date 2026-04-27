package com.quimodotcom.lqlauncher.activities

import android.app.KeyguardManager
import android.content.Context
import android.util.Log
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.quimodotcom.lqlauncher.services.MediaStateRepository
import com.quimodotcom.lqlauncher.services.NotificationItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest

class LockScreenOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen
        setShowWhenLocked(true)
        setInheritShowWhenLocked(true)

        // Make it transparent and full screen
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

        setContent {
            LockScreenOverlayContent(
                onUnlock = { action ->
                    val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() {
                            super.onDismissSucceeded()
                            action?.invoke()
                            finish()
                        }
                        override fun onDismissCancelled() {
                            super.onDismissCancelled()
                            finish()
                        }
                    })
                },
                onDismiss = { finish() }
            )
        }
    }
}

@Composable
fun NotificationList(
    notifications: List<NotificationItem>,
    onNotificationClick: (NotificationItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(notifications, key = { it.key }) { item ->
            NotificationCard(
                item = item,
                onClick = { onNotificationClick(item) },
                onDismiss = {
                    val intent = android.content.Intent("com.quimodotcom.lqlauncher.CANCEL_NOTIFICATION").apply {
                        setPackage(context.packageName)
                        putExtra("key", item.key)
                    }
                    context.sendBroadcast(intent)
                }
            )
        }
    }
}

@Composable
fun NotificationCard(
    item: NotificationItem,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current

    val iconBitmapState = produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, item.packageName) {
        withContext(Dispatchers.IO) {
            try {
                val drawable = context.packageManager.getApplicationIcon(item.packageName)
                value = drawable.toBitmap(64, 64).asImageBitmap()
            } catch (e: Exception) {
                value = null
            }
        }
    }
    val iconBitmap = iconBitmapState.value

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .clickable(onClick = onClick)
            .pointerInput(item.key) {
                detectDragGestures(
                    onDragStart = { },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                        }
                    },
                    onDragEnd = {
                        val threshold = 150f * density.density
                        if (kotlin.math.abs(offsetX.value) > threshold) {
                            scope.launch {
                                // Animate off screen
                                val target = if (offsetX.value > 0) size.width.toFloat() else -size.width.toFloat()
                                offsetX.animateTo(target, tween(200))
                                onDismiss()
                            }
                        } else {
                            scope.launch {
                                offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offsetX.animateTo(0f)
                        }
                    }
                )
            }
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // App Icon
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Rounded.Notifications,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.text,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun LockScreenOverlayContent(onUnlock: (action: (() -> Unit)?) -> Unit, onDismiss: () -> Unit) {
    val mediaState by MediaStateRepository.mediaState.collectAsState()
    val notifications by MediaStateRepository.activeNotifications.collectAsState()
    val context = LocalContext.current

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while(true) {
            currentTime = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    val timeStr = remember(currentTime) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(currentTime)) }
    val dateStr = remember(currentTime) { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(currentTime)) }

    // Backup unlock method
    BackHandler {
        onUnlock(null)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.01f)) // Almost transparent but receives touch
            .pointerInput(Unit) {
                var totalDragY = 0f
                detectDragGestures(
                    onDragStart = { totalDragY = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragY += dragAmount.y
                    },
                    onDragEnd = {
                        if (totalDragY < -150f) {
                            onUnlock(null)
                        }
                        totalDragY = 0f
                    }
                )
            }
    ) {
        // Clock
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timeStr,
                color = Color.White,
                fontSize = 80.sp,
                fontWeight = FontWeight.Light
            )
            Text(
                text = dateStr,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 18.sp
            )
        }

        // Notifications List
        NotificationList(
            notifications = notifications,
            onNotificationClick = { item ->
                android.util.Log.d("LockScreen", "Notification clicked: ${item.packageName}, key: ${item.key}, hasIntent: ${item.contentIntent != null}")
                onUnlock {
                    try {
                        // 1. Launch the app's content intent using Activity context
                        item.contentIntent?.send(context, 0, null)

                        // 2. Dismiss the notification from system and UI
                        val cancelIntent = android.content.Intent("com.quimodotcom.lqlauncher.CANCEL_NOTIFICATION").apply {
                            setPackage(context.packageName)
                            putExtra("key", item.key)
                        }
                        context.sendBroadcast(cancelIntent)
                    } catch (e: Exception) {
                        Log.e("LockScreen", "Failed to process notification click", e)
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 220.dp, bottom = 260.dp)
        )

        if (mediaState != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = mediaState?.title ?: "Unknown Title",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 32.sp
                )
                Text(
                    text = mediaState?.artist ?: "Unknown Artist",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(32.dp))

                // Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    IconButton(onClick = { MediaStateRepository.skipToPrevious() }) {
                        Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }

                    Surface(
                        onClick = { MediaStateRepository.playPause() },
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (mediaState?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    IconButton(onClick = { MediaStateRepository.skipToNext() }) {
                        Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }

        // Swipe up to unlock hint or button
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = { onUnlock(null) },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(Icons.Rounded.KeyboardArrowUp, null, tint = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Swipe up or tap to unlock",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }

    // Auto-dismiss if unlocked
    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    LaunchedEffect(Unit) {
        while(true) {
            if (!keyguardManager.isKeyguardLocked) {
                onDismiss()
                break
            }
            kotlinx.coroutines.delay(1000)
        }
    }
}
