package com.quimodotcom.lqlauncher.compose.launcher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Handles persistence of launcher configuration using JSON files
 */
object LauncherConfigRepository {
    
    private const val CONFIG_FILE_NAME = "launcher_config.json"
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Save launcher configuration to file
     */
    suspend fun saveConfig(context: Context, config: LauncherConfig) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, CONFIG_FILE_NAME)
                val jsonString = json.encodeToString(config)
                file.writeText(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Load launcher configuration from file
     */
    suspend fun loadConfig(context: Context): LauncherConfig? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, CONFIG_FILE_NAME)
                if (file.exists()) {
                    val jsonString = file.readText()
                    json.decodeFromString<LauncherConfig>(jsonString)
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Check if a saved configuration exists
     */
    fun hasConfig(context: Context): Boolean {
        val file = File(context.filesDir, CONFIG_FILE_NAME)
        return file.exists()
    }
    
    /**
     * Delete the saved configuration
     */
    suspend fun deleteConfig(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, CONFIG_FILE_NAME)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
