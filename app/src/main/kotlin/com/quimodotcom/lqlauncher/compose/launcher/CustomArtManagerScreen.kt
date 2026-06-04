package com.quimodotcom.lqlauncher.compose.launcher

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.view.HapticFeedbackConstants
import kotlinx.coroutines.launch
import android.widget.Toast
import android.graphics.BitmapFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomArtManagerScreen(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    var customArts by remember { mutableStateOf<List<CustomArtEntry>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        customArts = CustomArtRepository.loadAll(context)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0D0D12)
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Custom Album Art", color = Color.White) },
                        navigationIcon = {
                            IconButton(onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onDismiss()
                            }) {
                                Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White)
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                showAddDialog = true
                            }) {
                                Icon(Icons.Rounded.Add, "Add", tint = Color(0xFF6366F1))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF0D0D12)
                        )
                    )
                },
                containerColor = Color(0xFF0D0D12)
            ) { padding ->
                if (customArts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Text("No custom arts added", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        items(customArts, key = { it.id }) { entry ->
                            CustomArtItem(
                                entry = entry,
                                onDelete = {
                                    scope.launch {
                                        CustomArtRepository.removeCustomArt(context, entry.id)
                                        customArts = CustomArtRepository.loadAll(context)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCustomArtDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, artist, uri ->
                scope.launch {
                    val success = CustomArtRepository.addCustomArt(context, title, artist, uri)
                    if (success) {
                        customArts = CustomArtRepository.loadAll(context)
                        showAddDialog = false
                    } else {
                        Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@Composable
fun CustomArtItem(
    entry: CustomArtEntry,
    onDelete: () -> Unit
) {
    val bitmap = remember(entry.localPath) {
        try {
            BitmapFactory.decodeFile(entry.localPath)
        } catch (e: Exception) {
            null
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1A1A24),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.size(60.dp).background(Color.DarkGray, RoundedCornerShape(8.dp)))
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(entry.artist, color = Color.Gray, fontSize = 12.sp)
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, "Delete", tint = Color.Red.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun AddCustomArtDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Uri) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        selectedUri = uri
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1A1A24),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Add Custom Art", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Song Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) {
                    Text(if (selectedUri == null) "Select Image" else "Image Selected")
                }

                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
                    TextButton(
                        onClick = {
                            if (title.isNotBlank() && artist.isNotBlank() && selectedUri != null) {
                                onConfirm(title, artist, selectedUri!!)
                            }
                        },
                        enabled = title.isNotBlank() && artist.isNotBlank() && selectedUri != null
                    ) {
                        Text("Save", color = Color(0xFF6366F1))
                    }
                }
            }
        }
    }
}
