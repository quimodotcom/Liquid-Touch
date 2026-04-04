package com.quimodotcom.lqlauncher.compose.launcher

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.io.InputStream

/**
 * Handles exporting and importing of launcher schematics
 */
object SchematicRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Exports a schematic to the given URI (typically from a SAF picker)
     */
    suspend fun exportSchematic(context: Context, uri: Uri, schematic: LauncherSchematic): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val jsonString = json.encodeToString(schematic)
                    outputStream.write(jsonString.toByteArray())
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Imports a schematic from the given URI
     */
    suspend fun importSchematic(context: Context, uri: Uri): LauncherSchematic? {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    json.decodeFromString<LauncherSchematic>(jsonString)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
