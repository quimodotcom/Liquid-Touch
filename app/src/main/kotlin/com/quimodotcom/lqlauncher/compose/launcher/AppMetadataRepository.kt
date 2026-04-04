package com.quimodotcom.lqlauncher.compose.launcher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class AppMetadata(
    val label: String? = null,
    val customIconUri: String? = null
)

object AppMetadataRepository {
    private const val FILE_NAME = "app_metadata.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // In-memory cache
    private val metadataCache = ConcurrentHashMap<String, AppMetadata>()
    private var isLoaded = false

    private val _metadataUpdates = MutableSharedFlow<Unit>(replay = 0)
    val metadataUpdates = _metadataUpdates.asSharedFlow()

    suspend fun getMetadata(context: Context, packageName: String): AppMetadata? {
        if (!isLoaded) loadAll(context)
        return metadataCache[packageName]
    }

    suspend fun loadAll(context: Context): Map<String, AppMetadata> {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, FILE_NAME)
                if (file.exists()) {
                    val data = json.decodeFromString<Map<String, AppMetadata>>(file.readText())
                    metadataCache.clear()
                    metadataCache.putAll(data)
                }
                isLoaded = true
                metadataCache
            } catch (e: Exception) {
                e.printStackTrace()
                emptyMap()
            }
        }
    }

    suspend fun saveMetadata(context: Context, packageName: String, metadata: AppMetadata) {
        metadataCache[packageName] = metadata
        persist(context)
        _metadataUpdates.emit(Unit)
    }

    suspend fun removeMetadata(context: Context, packageName: String) {
        metadataCache.remove(packageName)
        persist(context)
        _metadataUpdates.emit(Unit)
    }

    private suspend fun persist(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, FILE_NAME)
                val data = metadataCache.toMap()
                file.writeText(json.encodeToString(data))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
