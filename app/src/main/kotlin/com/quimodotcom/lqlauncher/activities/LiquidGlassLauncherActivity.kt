package com.quimodotcom.lqlauncher.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.quimodotcom.lqlauncher.BuildConfig
import com.quimodotcom.lqlauncher.compose.launcher.HomeScreen
import com.quimodotcom.lqlauncher.viewmodels.LauncherViewModel
import org.woheller69.freeDroidWarn.FreeDroidWarn

class LiquidGlassLauncherActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        FreeDroidWarn.showWarningOnUpgrade(this, BuildConfig.VERSION_CODE)

        viewModel.viewModelScope.launch {
            snapshotFlow { viewModel.launcherConfig.showStatusBar }
                .collect { show ->
                    val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    if (show) {
                        insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                    } else {
                        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                    }
                }
        }

        val wallpaperPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

        setContent {
            val hasWallpaperPermissionState = remember {
                mutableStateOf(
                    androidx.core.content.ContextCompat.checkSelfPermission(this@LiquidGlassLauncherActivity, wallpaperPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
                )
            }

            val exportSchematicLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                if (uri != null) {
                    viewModel.exportSchematic(uri) { success ->
                        val msg = if (success) "Schematic exported" else "Export failed"
                        Toast.makeText(this@LiquidGlassLauncherActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val importSchematicLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    viewModel.importSchematic(uri) { success ->
                        val msg = if (success) "Schematic imported" else "Import failed"
                        Toast.makeText(this@LiquidGlassLauncherActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeScreen(
                    viewModel = viewModel,
                    hasWallpaperPermission = hasWallpaperPermissionState.value,
                    onWallpaperPermissionGranted = { hasWallpaperPermissionState.value = true },
                    onExportSchematic = { exportSchematicLauncher.launch("liquid_glass_layout.json") },
                    onImportSchematic = { importSchematicLauncher.launch(arrayOf("application/json", "application/octet-stream")) }
                )
            }
        }
    }
}
