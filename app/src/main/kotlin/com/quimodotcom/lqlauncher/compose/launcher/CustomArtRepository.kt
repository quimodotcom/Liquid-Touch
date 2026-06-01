package com.quimodotcom.lqlauncher.compose.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Serializable
data class CustomArtEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String,
    val localPath: String
)

object CustomArtRepository {
    private const val FILE_NAME = "custom_art.json"
    private const val DIR_NAME = "custom_art_images"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var customArtCache = mutableListOf<CustomArtEntry>()
    private var isLoaded = false

    private suspend fun ensureLoaded(context: Context) {
        if (!isLoaded) {
            loadAll(context)
        }
    }

    suspend fun loadAll(context: Context): List<CustomArtEntry> {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, FILE_NAME)
                if (file.exists()) {
                    val data = json.decodeFromString<List<CustomArtEntry>>(file.readText())
                    customArtCache = data.toMutableList()
                }
                isLoaded = true
                customArtCache
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun addCustomArt(context: Context, title: String, artist: String, imageUri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                ensureLoaded(context)

                // 1. Create directory if not exists
                val dir = File(context.filesDir, DIR_NAME)
                if (!dir.exists()) dir.mkdirs()

                // 2. Copy image to internal storage
                val fileName = "art_${UUID.randomUUID()}.webp"
                val destFile = File(dir, fileName)

                context.contentResolver.openInputStream(imageUri)?.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)
                    if (bitmap != null) {
                        FileOutputStream(destFile).use { output ->
                            bitmap.compress(Bitmap.CompressFormat.WEBP, 90, output)
                        }
                    } else {
                        return@withContext false
                    }
                } ?: return@withContext false

                // 3. Update cache and persist
                val entry = CustomArtEntry(title = title, artist = artist, localPath = destFile.absolutePath)
                customArtCache.add(entry)
                persist(context)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun removeCustomArt(context: Context, id: String) {
        withContext(Dispatchers.IO) {
            ensureLoaded(context)
            val entry = customArtCache.find { it.id == id }
            if (entry != null) {
                val file = File(entry.localPath)
                if (file.exists()) file.delete()
                customArtCache.remove(entry)
                persist(context)
            }
        }
    }

    suspend fun getCustomArt(context: Context, title: String, artist: String): Bitmap? {
        ensureLoaded(context)
        val entry = customArtCache.find {
            it.title.equals(title, ignoreCase = true) && it.artist.equals(artist, ignoreCase = true)
        } ?: return null

        return withContext(Dispatchers.IO) {
            try {
                BitmapFactory.decodeFile(entry.localPath)
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun persist(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, FILE_NAME)
                file.writeText(json.encodeToString(customArtCache))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
