package com.quimodotcom.lqlauncher.helpers

import android.content.Context
import android.util.Log
import com.quimodotcom.lqlauncher.helpers.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object AppleMusicIntegration {
    private const val TAG = "AppleMusicIntegration"
    // Relaxed min size to 10KB to avoid blocking valid small videos,
    // while still filtering out 0-byte or 404 text responses
    private const val MIN_FILE_SIZE_BYTES = 10 * 1024L
    private var cachedToken: String? = null

    // CookieJar implementation to persist cookies in memory
    private val cookieStore = HashMap<String, List<Cookie>>()
    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore[url.host] = cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor { chain ->
            val request = chain.request()
            val builder = request.newBuilder()

            // Add Authorization header if available and not already set
            val token = cachedToken
            if (token != null && request.header("Authorization") == null &&
                request.url.host.contains("apple.com")) {
                builder.header("Authorization", "Bearer $token")
            }
            // Add Origin header if not set
            if (request.header("Origin") == null && request.url.host.contains("apple.com")) {
                builder.header("Origin", "https://music.apple.com")
            }

            chain.proceed(builder.build())
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun searchAndGetAnimatedCover(title: String, artist: String, album: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            // 1. Ensure we have a token
            if (cachedToken == null) {
                if (!refreshCredentials()) {
                    return@withContext null
                }
            }

            // 2. Search iTunes to get Apple Music ID
            // Include album in query if available for better accuracy
            val queryTerm = if (!album.isNullOrBlank()) {
                 "$title $artist $album"
            } else {
                 "$title $artist"
            }
            val query = queryTerm.replace(" ", "+")
            val searchUrl = "https://itunes.apple.com/search?term=$query&entity=album&limit=1"
            val searchReq = Request.Builder().url(searchUrl).build()

            val searchResp = runInterruptible { client.newCall(searchReq).execute() }
            val searchBody = searchResp.body?.string() ?: return@withContext null

            val searchObj = JSONObject(searchBody)
            if (searchObj.optInt("resultCount") == 0) return@withContext null

            val result = searchObj.getJSONArray("results").getJSONObject(0)
            val collectionId = result.optString("collectionId")

            if (collectionId.isEmpty()) return@withContext null

            // 3. Fetch Album Details from AMP API
            // Use 'us' as default store front
            val country = "us"
            val ampUrl = "https://amp-api.music.apple.com/v1/catalog/$country/albums/$collectionId?extend=editorialVideo"

            // Authorization handled by interceptor
            val ampReq = Request.Builder()
                .url(ampUrl)
                .build()

            val ampResp = runInterruptible { client.newCall(ampReq).execute() }

            if (ampResp.code == 401) {
                // Token expired, refresh and retry once
                if (refreshCredentials()) {
                    // Interceptor will pick up the new token automatically
                    val retryReq = ampReq.newBuilder().build()
                    val retryResp = runInterruptible { client.newCall(retryReq).execute() }
                    if (retryResp.isSuccessful) {
                        val videoUrl = parseAmpResponse(retryResp.body?.string())
                        return@withContext resolveMasterPlaylist(videoUrl, 0, 0)
                    }
                }
                return@withContext null
            }

            if (!ampResp.isSuccessful) return@withContext null

            val videoUrl = parseAmpResponse(ampResp.body?.string())
            return@withContext resolveMasterPlaylist(videoUrl, 0, 0)

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Apple Music cover", e)
            null
        }
    }

    private fun parseAmpResponse(json: String?): String? {
        if (json == null) return null
        val ampObj = JSONObject(json)
        val data = ampObj.optJSONArray("data")
        if (data == null || data.length() == 0) return null

        val attributes = data.getJSONObject(0).optJSONObject("attributes") ?: return null
        val editorialVideo = attributes.optJSONObject("editorialVideo") ?: return null

        // Prioritize Tall > Square > 1x1
        val motion = editorialVideo.optJSONObject("motionDetailTall")
            ?: editorialVideo.optJSONObject("motionDetailSquare")
            ?: editorialVideo.optJSONObject("motionSquareVideo1x1")

        return motion?.optString("video")
    }

    /**
     * If the URL is an m3u8 master playlist, find the stream closest to the target resolution.
     * Otherwise return the original URL.
     */
    private suspend fun resolveMasterPlaylist(url: String?, targetW: Int, targetH: Int): String? {
        if (url == null) return null
        if (!url.endsWith(".m3u8")) return url

        return try {
            val req = Request.Builder().url(url).build()
            val resp = runInterruptible { client.newCall(req).execute() }
            if (!resp.isSuccessful) return url

            val content = resp.body?.string() ?: return url

            // Check if it's a master playlist
            if (!content.contains("#EXT-X-STREAM-INF")) return url

            var bestUrl: String? = null
            var bestDiff = Int.MAX_VALUE
            var maxBandwidth = 0L // Fallback

            val lines = content.lines()
            for (i in lines.indices) {
                val line = lines[i]
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    // Extract RESOLUTION
                    val resMatch = Pattern.compile("RESOLUTION=(\\d+)x(\\d+)").matcher(line)
                    val bandwidthMatch = Pattern.compile("BANDWIDTH=(\\d+)").matcher(line)

                    var w = 0
                    var h = 0
                    if (resMatch.find()) {
                        w = resMatch.group(1)?.toIntOrNull() ?: 0
                        h = resMatch.group(2)?.toIntOrNull() ?: 0
                    }

                    var bandwidth = 0L
                    if (bandwidthMatch.find()) {
                        bandwidth = bandwidthMatch.group(1)?.toLongOrNull() ?: 0L
                    }

                    // Score based on resolution difference
                    var isBetter = false
                    if (w > 0 && h > 0 && targetW > 0 && targetH > 0) {
                        val diff = kotlin.math.abs((w * h) - (targetW * targetH))
                        if (diff < bestDiff) {
                            bestDiff = diff
                            isBetter = true
                        } else if (diff == bestDiff && bandwidth > maxBandwidth) {
                            // Tie-breaker: higher bandwidth
                            isBetter = true
                        }
                    } else {
                        // Fallback to bandwidth if no resolution or target provided
                        if (bandwidth > maxBandwidth) {
                            maxBandwidth = bandwidth
                            isBetter = true
                        }
                    }

                    if (isBetter) {
                        // The URL is usually on the next non-empty, non-comment line
                        for (j in i + 1 until lines.size) {
                            val nextLine = lines[j].trim()
                            if (nextLine.isNotEmpty() && !nextLine.startsWith("#")) {
                                if (nextLine.startsWith("http")) {
                                    bestUrl = nextLine
                                } else {
                                    val base = url.substringBeforeLast("/")
                                    bestUrl = "$base/$nextLine"
                                }
                                if (w > 0 && h > 0) bestDiff = kotlin.math.abs((w * h) - (targetW * targetH))
                                maxBandwidth = bandwidth
                                break
                            }
                        }
                    }
                }
            }

            bestUrl ?: url
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving master playlist", e)
            url
        }
    }

    private suspend fun getFirstSegmentUrl(variantUrl: String): String? {
        return try {
            // Ensure we handle redirects explicitly if needed, though OkHttp does by default.
            // Using a standard User-Agent might help with some CDNs.
            val req = Request.Builder()
                .url(variantUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .build()

            val resp = runInterruptible { client.newCall(req).execute() }
            if (!resp.isSuccessful) {
                Log.e(TAG, "Failed to fetch playlist: ${resp.code} for $variantUrl")
                return null
            }

            val content = resp.body?.string() ?: return null

            // Find first line that doesn't start with # and is not empty
            val lines = content.lines()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    if (trimmed.startsWith("http")) {
                        return trimmed
                    } else {
                        // Handle absolute paths if they start with / but not http
                        if (trimmed.startsWith("/")) {
                             // Construct from host root
                             val uri = java.net.URI(variantUrl)
                             val scheme = uri.scheme
                             val authority = uri.authority
                             return "$scheme://$authority$trimmed"
                        } else {
                             val base = variantUrl.substringBeforeLast("/")
                             return "$base/$trimmed"
                        }
                    }
                }
            }
            Log.e(TAG, "No valid segment found in playlist content")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting first segment URL", e)
            null
        }
    }

    suspend fun getAnimatedArtworkFile(context: Context, url: String, title: String, artist: String, album: String? = null, targetW: Int = 0, targetH: Int = 0): java.io.File? = withContext(Dispatchers.IO) {
        try {
            DebugLogger.log(TAG, "Fetching art for: $title")
            // Generate Filename
            // Use only the primary artist to avoid duplicates for songs with features
            val primaryArtist = artist.split(Regex("[,&]|\\s+(?i)(feat\\.|featuring|with)\\s+")).first().trim()
            val safeArtist = primaryArtist.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val safeAlbum = album?.replace(Regex("[^a-zA-Z0-9.-]"), "_")

            // Use album name for filename if available to avoid duplicates for songs on the same album
            val filename = if (!safeAlbum.isNullOrBlank()) {
                "${safeArtist}_${safeAlbum}.mp4"
            } else {
                val safeTitle = title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                "${safeTitle}_${safeArtist}.mp4"
            }

            val cacheDir = java.io.File(context.getExternalFilesDir(null), "animated_covers")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val cacheFile = java.io.File(cacheDir, filename)

            // Return cached file if exists and is valid
            if (cacheFile.exists() && cacheFile.length() > 0) {
                // Check for minimum size (e.g. 50KB) to avoid empty/corrupt files
                if (cacheFile.length() < MIN_FILE_SIZE_BYTES) {
                    DebugLogger.log(TAG, "Cache too small: ${cacheFile.length()}")
                    cacheFile.delete()
                } else {
                    DebugLogger.log(TAG, "Cache hit: ${cacheFile.name}")
                    return@withContext cacheFile
                }
            }

            // 1. Resolve Master Playlist to Best Variant matching resolution
            val variantUrl = resolveMasterPlaylist(url, targetW, targetH) ?: return@withContext null
            Log.d(TAG, "Resolved Master Playlist to: $variantUrl")

            // 2. Get First Segment URL from Variant
            val segmentUrl = getFirstSegmentUrl(variantUrl) ?: return@withContext null
            Log.d(TAG, "Downloading Segment URL: $segmentUrl")

            // 3. Download Segment to Cache File (Atomic Write)
            DebugLogger.log(TAG, "Downloading...")
            val tempFile = java.io.File(cacheDir, "${filename}.tmp")
            val segmentReq = Request.Builder().url(segmentUrl).build()
            val segmentResp = runInterruptible { client.newCall(segmentReq).execute() }
            if (!segmentResp.isSuccessful) {
                DebugLogger.log(TAG, "Download failed: ${segmentResp.code}")
                return@withContext null
            }

            val sink = tempFile.sink()
            val source = segmentResp.body?.source()
            var success = false
            if (source != null) {
                try {
                    val buffer = sink.buffer()
                    val bytesWritten = runInterruptible { buffer.writeAll(source) }
                    buffer.close()

                    if (bytesWritten >= MIN_FILE_SIZE_BYTES) {
                        success = true
                    } else {
                        Log.e(TAG, "Download too small ($bytesWritten bytes), deleting temp file")
                        success = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing to temp file", e)
                    try { tempFile.delete() } catch (ignored: Exception) {}
                }
            } else {
                return@withContext null
            }

            if (success) {
                DebugLogger.log(TAG, "Download complete: ${tempFile.length()}")
                if (tempFile.renameTo(cacheFile)) {
                    return@withContext cacheFile
                } else {
                    DebugLogger.log(TAG, "Rename failed")
                    Log.e(TAG, "Failed to rename temp file to cache file")
                    // Fallback: return tempFile if rename fails? No, retry next time.
                    // Or maybe file locking issue. Try deleting target first.
                    if (cacheFile.exists()) cacheFile.delete()
                    if (tempFile.renameTo(cacheFile)) {
                        return@withContext cacheFile
                    }
                }
            }

            return@withContext null

        } catch (e: Exception) {
            Log.e(TAG, "Error getting animated artwork file", e)
            null
        }
    }

    suspend fun getAnimatedArtworkBitmap(context: Context, url: String, title: String, artist: String, album: String? = null): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        val file = getAnimatedArtworkFile(context, url, title, artist, album) ?: return@withContext null
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            return@withContext retriever.getFrameAtTime()
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting frame from file", e)
            null
        }
    }

    private fun refreshCredentials(): Boolean {
        try {
            // Step A: Hit a generic page to initialize cookies
            // The Python script uses a specific album, let's replicate that to be safe
            val albumUrl = "https://music.apple.com/us/album/positions-deluxe-edition/1553944254"
            val pageReq = Request.Builder()
                .url(albumUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .build()

            val pageResp = client.newCall(pageReq).execute()
            val html = pageResp.body?.string() ?: return false

            // Step B: Find index.js
            val jsPattern = Pattern.compile("crossorigin src=\"(/assets/index.+?\\.js)\"")
            val jsMatcher = jsPattern.matcher(html)
            if (!jsMatcher.find()) {
                Log.e(TAG, "Could not find index.js path")
                return false
            }
            val jsUrl = "https://music.apple.com${jsMatcher.group(1)}"

            // Step C: Fetch JS (cookies included automatically by CookieJar)
            val jsReq = Request.Builder()
                .url(jsUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .build()

            val jsResp = client.newCall(jsReq).execute()
            val jsContent = jsResp.body?.string() ?: return false

            // Step D: Extract Token
            // Pattern from user script: (eyJhbGc.+?)"
            val tokenPattern = Pattern.compile("(\"eyJhbGc.+?)\"")
            val tokenMatcher = tokenPattern.matcher(jsContent)

            if (tokenMatcher.find()) {
                val token = tokenMatcher.group(1)?.replace("\"", "")
                if (token != null) {
                    cachedToken = token
                    Log.d(TAG, "Refreshed Apple Music Token")
                    return true
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing credentials", e)
        }
        return false
    }
}
