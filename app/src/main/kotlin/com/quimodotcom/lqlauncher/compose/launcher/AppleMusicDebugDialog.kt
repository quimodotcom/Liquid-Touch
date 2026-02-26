package com.quimodotcom.lqlauncher.compose.launcher

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import android.net.Uri
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.quimodotcom.lqlauncher.helpers.AppleMusicIntegration
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppleMusicDebugDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var songTitle by remember { mutableStateOf("Positions") }
    var artistName by remember { mutableStateOf("Ariana Grande") }
    var logs by remember { mutableStateOf(listOf<String>()) }
    var resultFile by remember { mutableStateOf<java.io.File?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        logs = logs + "[$timestamp] $message"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A1A24)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Animated Cover Debugger",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, "Close", tint = Color.Gray)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Input Fields
                OutlinedTextField(
                    value = songTitle,
                    onValueChange = { songTitle = it },
                    label = { Text("Song Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFF6366F1),
                        unfocusedLabelColor = Color.Gray
                    )
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = artistName,
                    onValueChange = { artistName = it },
                    label = { Text("Artist Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFF6366F1),
                        unfocusedLabelColor = Color.Gray
                    )
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (songTitle.isBlank() || artistName.isBlank()) return@Button

                        isLoading = true
                        resultFile = null
                        logs = emptyList() // Clear previous logs

                        log("Starting search for '$songTitle' by '$artistName'...")

                        scope.launch {
                            try {
                                log("Calling AppleMusicIntegration...")
                                val url = AppleMusicIntegration.searchAndGetAnimatedCover(songTitle, artistName)

                                if (url != null) {
                                    log("Found Master URL: $url")
                                    log("Downloading segment matching display...")
                                    val dm = context.resources.displayMetrics
                                    val file = AppleMusicIntegration.getAnimatedArtworkFile(context, url, songTitle, artistName, null, dm.widthPixels, dm.heightPixels)

                                    if (file != null) {
                                        log("Success! File cached at: ${file.name}")
                                        log("Size: ${file.length() / 1024} KB")
                                        resultFile = file
                                    } else {
                                        log("Failed to download file.")
                                    }
                                } else {
                                    log("Failed. No URL found.")
                                    log("Check Logcat for details (AppleMusicIntegration tag).")
                                }
                            } catch (e: Exception) {
                                log("Error: ${e.message}")
                                e.printStackTrace()
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6366F1),
                        disabledContainerColor = Color(0xFF6366F1).copy(alpha = 0.5f)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Searching...")
                    } else {
                        Text("Test Fetch")
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Result Preview
                if (resultFile != null) {
                    Text("Result Preview:", color = Color.Gray, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val videoView = android.widget.VideoView(ctx)
                                videoView.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                                videoView.setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    mp.start()
                                }
                                videoView
                            },
                            update = { videoView ->
                                videoView.setVideoURI(Uri.fromFile(resultFile))
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Logs Area
                Text("Logs:", color = Color.Gray, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // Fill remaining space
                        .border(1.dp, Color(0xFF2A2A3A), RoundedCornerShape(8.dp)),
                    color = Color(0xFF0D0D12),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    SelectionContainer {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(logs) { log ->
                                Text(
                                    text = log,
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
