package com.quimodotcom.lqlauncher.viewmodels

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quimodotcom.lqlauncher.compose.launcher.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    var launcherConfig by mutableStateOf(LauncherConfig())
    var editModeState by mutableStateOf(EditModeState())
    var availableApps by mutableStateOf<List<AvailableApp>>(emptyList())
    var gridSize by mutableStateOf(IntSize.Zero)
    var pendingGridPosition by mutableStateOf<Pair<Int, Int>?>(null)
    var showFolderNameDialog by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var isConfigLoaded by mutableStateOf(false)
    var openedFolder by mutableStateOf<LauncherItem.Folder?>(null)
    var showAppDrawer by mutableStateOf(false)
    var drawerTrigger by mutableIntStateOf(0)
    var isSubjectPositioning by mutableStateOf(false)
    var selectedSubjectAdjustmentMode by mutableIntStateOf(0)
    var showInvisibleButtonActionPicker by mutableStateOf<Pair<Int, Int>?>(null)

    var glassSettings by mutableStateOf(LiquidGlassSettings())
    var isSettingsLoaded by mutableStateOf(false)

    init {
        loadData()
        setupAutoSave()
        checkUpdatesIfEnabled()
        registerPackageReceiver()
    }

    private fun registerPackageReceiver() {
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                reloadApps()
            }
        }
        context.registerReceiver(receiver, filter)
    }

    private fun setupAutoSave() {
        viewModelScope.launch {
            snapshotFlow { launcherConfig }
                .debounce(500L)
                .collectLatest {
                    if (isConfigLoaded) {
                        saveConfig()
                    }
                }
        }
        viewModelScope.launch {
            snapshotFlow { glassSettings }
                .debounce(300L)
                .collectLatest {
                    if (isSettingsLoaded) {
                        saveSettings()
                    }
                }
        }
    }

    private fun checkUpdatesIfEnabled() {
        viewModelScope.launch {
            // Wait for settings to load
            snapshotFlow { isSettingsLoaded }
                .collectLatest { loaded ->
                    if (loaded && glassSettings.autoUpdateEnabled) {
                        com.quimodotcom.lqlauncher.helpers.AutoUpdater.checkForUpdates(
                            context = context,
                            url = glassSettings.githubUpdateUrl,
                            token = glassSettings.githubToken,
                            isManual = false
                        )
                    }
                }
        }
    }

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val savedSettings = LiquidGlassSettingsRepository.loadSettings(context)
                withContext(Dispatchers.Main) {
                    glassSettings = savedSettings
                    isSettingsLoaded = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isSettingsLoaded = true }
            }

            val apps = loadAvailableAppsInternal(context)
            withContext(Dispatchers.Main) {
                availableApps = apps
            }

            try {
                val savedConfig = LauncherConfigRepository.loadConfig(context)
                withContext(Dispatchers.Main) {
                    if (savedConfig != null) {
                        launcherConfig = savedConfig
                    } else if (launcherConfig.items.isEmpty()) {
                        launcherConfig = launcherConfig.copy(
                            items = createDefaultItems()
                        )
                    }
                    isConfigLoaded = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isConfigLoaded = true }
            }
        }
    }

    fun reloadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = loadAvailableAppsInternal(context)
            withContext(Dispatchers.Main) {
                availableApps = apps
            }
        }
    }

    fun saveConfig() {
        if (isConfigLoaded) {
            viewModelScope.launch(Dispatchers.IO) {
                LauncherConfigRepository.saveConfig(context, launcherConfig)
            }
        }
    }

    fun saveSettings() {
        if (isSettingsLoaded) {
            viewModelScope.launch(Dispatchers.IO) {
                LiquidGlassSettingsRepository.saveSettings(context, glassSettings)
            }
        }
    }

    fun updateGridSize(size: IntSize) {
        gridSize = size
    }

    fun setEditMode(enabled: Boolean) {
        editModeState = editModeState.copy(isEnabled = enabled, isUiHidden = false)
    }

    fun toggleAppDrawer(show: Boolean) {
        if (show) drawerTrigger++
        showAppDrawer = show
    }

    /**
     * Schematic Export
     */
    fun exportSchematic(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val metadata = AppMetadataRepository.loadAll(context)
            val schematic = LauncherSchematic(
                config = launcherConfig,
                settings = glassSettings,
                metadata = metadata
            )
            val success = SchematicRepository.exportSchematic(context, uri, schematic)
            withContext(Dispatchers.Main) {
                onResult(success)
            }
        }
    }

    /**
     * Schematic Import
     */
    fun importSchematic(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val schematic = SchematicRepository.importSchematic(context, uri)
            if (schematic != null) {
                var finalSettings = schematic.settings
                if (finalSettings.iconPackPackageName.isNotEmpty()) {
                    try {
                        context.packageManager.getPackageInfo(finalSettings.iconPackPackageName, 0)
                    } catch (e: PackageManager.NameNotFoundException) {
                        finalSettings = finalSettings.copy(iconPackPackageName = "")
                    }
                }

                withContext(Dispatchers.Main) {
                    launcherConfig = schematic.config
                    glassSettings = finalSettings
                    AppMetadataRepository.saveAllMetadata(context, schematic.metadata)

                    // Force save and broadcast
                    saveConfig()
                    saveSettings()

                    onResult(true)
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            }
        }
    }

    private suspend fun loadAvailableAppsInternal(context: Context): List<AvailableApp> {
        return withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val overrides = AppMetadataRepository.loadAll(context)
            pm.queryIntentActivities(intent, 0)
                .map { resolveInfo ->
                    val pkg = resolveInfo.activityInfo.packageName
                    val override = overrides[pkg]
                    val label = override?.label ?: resolveInfo.loadLabel(pm).toString()
                    val icon = try {
                        resolveInfo.loadIcon(pm)
                    } catch (e: Exception) {
                        null
                    }
                    AvailableApp(
                        packageName = pkg,
                        label = label,
                        icon = icon,
                        componentName = ComponentName(pkg, resolveInfo.activityInfo.name),
                        customIconUri = override?.customIconUri
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    private fun createDefaultItems(): List<LauncherItem> {
        return listOf(
            LauncherItem.GlassPanel(
                gridX = 1, gridY = 0, spanX = 2, spanY = 2,
                panelType = PanelType.CLOCK, tintColor = 0xFF6366F1L
            )
        )
    }
}
