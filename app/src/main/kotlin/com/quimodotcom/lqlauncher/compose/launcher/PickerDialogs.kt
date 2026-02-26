package com.quimodotcom.lqlauncher.compose.launcher

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap


/**
 * App picker dialog for selecting apps to add to home screen
 */
@Composable
fun AppPickerDialog(
    availableApps: List<AvailableApp>,
    onAppSelected: (AvailableApp) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredApps = remember(availableApps, searchQuery) {
        if (searchQuery.isBlank()) {
            availableApps.sortedBy { it.label.lowercase() }
        } else {
            availableApps.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }.sortedBy { it.label.lowercase() }
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E1E2E)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Text(
                    "Select App",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                
                Spacer(Modifier.height(16.dp))
                
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search apps...", color = Color.Gray) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, null, tint = Color.Gray)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Clear, null, tint = Color.Gray)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color.Gray
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(Modifier.height(16.dp))
                
                // App grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredApps) { app ->
                        AppPickerItem(
                            app = app,
                            onClick = { onAppSelected(app) }
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Cancel button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", color = Color(0xFF6366F1))
                }
            }
        }
    }
}

@Composable
private fun AppPickerItem(
    app: AvailableApp,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2E2E3E)),
            contentAlignment = Alignment.Center
        ) {
            app.icon?.let { drawable ->
                val bitmap = remember(drawable) {
                    drawable.toBitmap(96, 96).asImageBitmap()
                }
                Image(
                    painter = BitmapPainter(bitmap),
                    contentDescription = app.label,
                    modifier = Modifier.size(40.dp)
                )
            } ?: Icon(
                Icons.Rounded.Android,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(32.dp)
            )
        }
        
        Spacer(Modifier.height(4.dp))
        
        // App name
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Panel type picker dialog
 */
@Composable
fun PanelPickerDialog(
    onPanelTypeSelected: (PanelType) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E1E2E)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    "Add Panel",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                
                Spacer(Modifier.height(16.dp))
                
                PanelOption(
                    icon = Icons.Rounded.Dashboard,
                    title = "Empty Panel",
                    description = "A decorative glass panel",
                    onClick = { onPanelTypeSelected(PanelType.EMPTY) }
                )
                
                PanelOption(
                    icon = Icons.Rounded.AccessTime,
                    title = "Clock",
                    description = "Shows current time and date",
                    onClick = { onPanelTypeSelected(PanelType.CLOCK) }
                )
                
                PanelOption(
                    icon = Icons.Rounded.Cloud,
                    title = "Weather",
                    description = "Weather information",
                    onClick = { onPanelTypeSelected(PanelType.WEATHER) }
                )
                
                PanelOption(
                    icon = Icons.Rounded.Settings,
                    title = "Quick Settings",
                    description = "Quick access toggles",
                    onClick = { onPanelTypeSelected(PanelType.QUICK_SETTINGS) }
                )
                
                PanelOption(
                    icon = Icons.Rounded.BatteryFull,
                    title = "Battery",
                    description = "Battery level and status",
                    onClick = { onPanelTypeSelected(PanelType.BATTERY) }
                )

                PanelOption(
                    icon = Icons.Rounded.Search,
                    title = "Browser Search",
                    description = "One-row search bar (opens default browser)",
                    onClick = { onPanelTypeSelected(PanelType.SEARCH) }
                )
                
                Spacer(Modifier.height(16.dp))
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", color = Color(0xFF6366F1))
                }
            }
        }
    }
}

@Composable
private fun PanelOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xFF6366F1).copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF6366F1),
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

/**
 * Folder name input dialog
 */
@Composable
fun FolderNameDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E1E2E)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    "Create Folder",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                
                Spacer(Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Folder name", color = Color.Gray) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (folderName.isNotBlank()) {
                                onConfirm(folderName)
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color.Gray
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { 
                            if (folderName.isNotBlank()) {
                                onConfirm(folderName)
                            }
                        },
                        enabled = folderName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1)
                        )
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}

/**
 * Add item menu for empty cells
 */
@Composable
fun AddItemMenu(
    gridX: Int,
    gridY: Int,
    onAddApp: () -> Unit,
    onAddPanel: () -> Unit,
    onAddFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E1E2E)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Add to Home Screen",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onAddApp)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Apps, null, tint = Color(0xFF6366F1))
                    Spacer(Modifier.width(16.dp))
                    Text("App Shortcut", color = Color.White)
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onAddPanel)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Dashboard, null, tint = Color(0xFF6366F1))
                    Spacer(Modifier.width(16.dp))
                    Text("Glass Panel", color = Color.White)
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onAddFolder)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Folder, null, tint = Color(0xFF6366F1))
                    Spacer(Modifier.width(16.dp))
                    Text("Folder", color = Color.White)
                }
            }
        }
    }
}

/**
 * Icon pack picker dialog
 */
@Composable
fun IconPackPickerDialog(
    currentIconPack: String,
    onDismiss: () -> Unit,
    onIconPackSelected: (String) -> Unit
) {
    // Icon packs are removed — show a disabled dialog
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E1E2E),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    "Icon Packs Disabled",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )

                Spacer(Modifier.height(16.dp))

                Text("Icon packs have been removed from this build and are no longer supported.", color = Color(0xFFBDBDBD))

                Spacer(Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close", color = Color(0xFF6366F1))
                }
            }
        }
    }
}


