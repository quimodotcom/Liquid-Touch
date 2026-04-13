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
            val queryTerm = "$title $artist"
            val query = java.net.URLEncoder.encode(queryTerm, "UTF-8")
            val searchUrl = "https://itunes.apple.com/search?term=$query&entity=song&limit=5"
            val searchReq = Request.Builder().url(searchUrl).build()

            val searchResp = runInterruptible { client.newCall(searchReq).execute() }
            val searchBody = searchResp.body?.string() ?: return@withContext null

            val searchObj = JSONObject(searchBody)
            val results = searchObj.optJSONArray("results")
            if (results == null || results.length() == 0) {
                DebugLogger.log(TAG, "No iTunes results for $queryTerm")
                return@withContext null
            }

            // Find best result matching title/artist
            var collectionId = ""
            var trackId = ""
            for (i in 0 until results.length()) {
                val res = results.getJSONObject(i)
                val trackName = res.optString("trackName")

                if (trackName.contains(title, ignoreCase = true) || title.contains(trackName, ignoreCase = true)) {
                    collectionId = res.optString("collectionId")
                    trackId = res.optString("trackId")
                    break
                }
            }

            if (collectionId.isEmpty()) {
                collectionId = results.getJSONObject(0).optString("collectionId")
                trackId = results.getJSONObject(0).optString("trackId")
            }

            if (collectionId.isEmpty()) return@withContext null

            // 3. Fetch Details from AMP API (Try Song first, then Album)
            val country = "us"
            var videoUrl: String? = null

            // A. Try Song level editorial video
            if (trackId.isNotEmpty()) {
                // Using both snake_case and camelCase extensions to be safe
                val songUrl = "https://amp-api.music.apple.com/v1/catalog/$country/songs/$trackId?extend=editorialVideo,editorial-video&views=editorial-video,editorialVideo"
                val songReq = Request.Builder().url(songUrl).build()
                val songResp = runInterruptible { client.newCall(songReq).execute() }
                if (songResp.isSuccessful) {
                    videoUrl = parseAmpResponse(songResp.body?.string())
                }
            }

            // B. Try Album level if song level failed
            if (videoUrl.isNullOrBlank()) {
                val albumUrl = "https://amp-api.music.apple.com/v1/catalog/$country/albums/$collectionId?extend=editorialVideo,editorial-video&views=editorial-video,editorialVideo"
                val albumReq = Request.Builder().url(albumUrl).build()
                val albumResp = runInterruptible { client.newCall(albumReq).execute() }

                if (albumResp.code == 401) {
                    if (refreshCredentials()) {
                        val retryReq = albumReq.newBuilder().build()
                        val retryResp = runInterruptible { client.newCall(retryReq).execute() }
                        if (retryResp.isSuccessful) {
                            videoUrl = parseAmpResponse(retryResp.body?.string())
                        }
                    }
                } else if (albumResp.isSuccessful) {
                    videoUrl = parseAmpResponse(albumResp.body?.string())
                }
            }

            if (videoUrl.isNullOrBlank()) {
                DebugLogger.log(TAG, "No video URL found for $title")
                return@withContext null
            }

            return@withContext videoUrl // Return raw URL, resolveMasterPlaylist will be called in getAnimatedArtworkFile

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Apple Music cover", e)
            null
        }
    }

    private fun parseAmpResponse(json: String?): String? {
        if (json == null) return null
        try {
            val ampObj = JSONObject(json)
            val data = ampObj.optJSONArray("data")
            if (data == null || data.length() == 0) return null

            val albumObj = data.getJSONObject(0)
            val attributes = albumObj.optJSONObject("attributes") ?: return null

            // 1. Check in attributes.editorialVideo (standard)
            val editorialVideo = attributes.optJSONObject("editorialVideo")
            if (editorialVideo != null) {
                val video = findVideoInEditorialObject(editorialVideo)
                if (video != null) return video
            }

            // 2. Check in views.editorial-video (newer API format)
            val views = albumObj.optJSONObject("views")
            val evView = views?.optJSONObject("editorial-video") ?: views?.optJSONObject("editorialVideo")
            val evData = evView?.optJSONArray("data")
            if (evData != null && evData.length() > 0) {
                val viewAttr = evData.getJSONObject(0).optJSONObject("attributes")
                if (viewAttr != null) {
                    val video = findVideoInEditorialObject(viewAttr)
                    if (video != null) return video
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error", e)
        }
        return null
    }

    private fun findVideoInEditorialObject(obj: JSONObject): String? {
        // Prioritize Motion Video formats
        val motion = obj.optJSONObject("motionDetailTall")
            ?: obj.optJSONObject("motionDetailSquare")
            ?: obj.optJSONObject("motionSquareVideo1x1")
            ?: obj.optJSONObject("motionDetailSquare1x1")

        val video = motion?.optString("video")
        if (!video.isNullOrBlank()) return video

        // Fallback to static editorial video if present
        return obj.optString("video")
    }

    /**
     * If the URL is an m3u8 master playlist, find the stream closest to the target resolution.
     * Otherwise return the original URL.
     */
    private suspend fun resolveMasterPlaylist(url: String?, targetW: Int, targetH: Int): String? {
        if (url == null) return null
        if (!url.contains(".m3u8")) return url // Use contains for URLs with params

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
            val albumUrl = "https://music.apple.com/us/album/positions-deluxe-edition/1553944254"
            val pageReq = Request.Builder()
                .url(albumUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val pageResp = client.newCall(pageReq).execute()
            val html = pageResp.body?.string() ?: return false

            // Step B: Find index.js path
            // Apple changes this frequently, using multiple patterns
            val jsPatterns = listOf(
                Pattern.compile("crossorigin src=\"(/assets/index.+?\\.js)\""),
                Pattern.compile("src=\"(/assets/index.+?\\.js)\""),
                Pattern.compile("src=\"(https://music.apple.com/assets/index.+?\\.js)\""),
                Pattern.compile("(/assets/index.+?\\.js)")
            )

            var jsPath = ""
            for (pattern in jsPatterns) {
                val matcher = pattern.matcher(html)
                if (matcher.find()) {
                    jsPath = matcher.group(1) ?: ""
                    break
                }
            }

            if (jsPath.isEmpty()) {
                Log.e(TAG, "Could not find index.js path in HTML")
                return false
            }

            val jsUrl = if (jsPath.startsWith("http")) jsPath else "https://music.apple.com$jsPath"

            // Step C: Fetch JS (cookies included automatically by CookieJar)
            val jsReq = Request.Builder()
                .url(jsUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val jsResp = client.newCall(jsReq).execute()
            val jsContent = jsResp.body?.string() ?: return false

            // Step D: Extract Token
            // Pattern: eyJhbGc... followed by more chars and ending in a quote
            val tokenPatterns = listOf(
                Pattern.compile("(eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9\\.[a-zA-Z0-9._-]+)"),
                Pattern.compile("token\":\"(eyJhbG.+?)\""),
                Pattern.compile("\"(eyJhbG.+?)\"")
            )

            for (pattern in tokenPatterns) {
                val matcher = pattern.matcher(jsContent)
                if (matcher.find()) {
                    val token = matcher.group(1)?.replace("\"", "")
                    if (token != null && token.length > 50) {
                        cachedToken = token
                        DebugLogger.log(TAG, "Refreshed Token (${token.take(10)}...)")
                        return true
                    }
                }
            }
            Log.e(TAG, "Token not found in JS content")

        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing credentials", e)
        }
        return false
    }
}
