package com.quimodotcom.lqlauncher.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object AutoUpdater {
    private const val TAG = "AutoUpdater"
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdates(context: Context, url: String, token: String) {
        if (url.isBlank()) return

        withContext(Dispatchers.IO) {
            try {
                // 1. Parse URL to get owner/repo
                // Expected format: https://github.com/owner/repo/actions or just https://github.com/owner/repo
                val regex = Regex("github\\.com/([^/]+)/([^/]+)")
                val match = regex.find(url) ?: return@withContext
                val (owner, repo) = match.destructured

                Log.d(TAG, "Checking updates for $owner/$repo")

                // 2. Fetch latest successful workflow run
                val runsUrl = "https://api.github.com/repos/$owner/$repo/actions/runs?status=success&per_page=1"

                val runsRequest = Request.Builder()
                    .url(runsUrl)
                    .apply {
                        if (token.isNotBlank()) header("Authorization", "token $token")
                    }
                    .build()

                val runsResponse = client.newCall(runsRequest).execute()
                if (!runsResponse.isSuccessful) {
                    Log.e(TAG, "Failed to fetch runs: ${runsResponse.code} ${runsResponse.message}")
                    return@withContext
                }

                val runsBody = runsResponse.body?.string() ?: return@withContext
                val runsJson = json.parseToJsonElement(runsBody).jsonObject
                val workflowRuns = runsJson["workflow_runs"]?.jsonArray
                val latestRun = workflowRuns?.firstOrNull()?.jsonObject

                if (latestRun == null) {
                    Log.d(TAG, "No successful runs found")
                    return@withContext
                }

                // Check version
                val runNumber = latestRun["run_number"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                val currentBuildNumber = currentVersion?.substringAfterLast(".")?.toIntOrNull() ?: 0

                if (runNumber <= currentBuildNumber) {
                    Log.d(TAG, "App is up to date (Remote: $runNumber, Local: $currentBuildNumber)")
                    return@withContext
                }

                val artifactsUrl = latestRun["artifacts_url"]?.jsonPrimitive?.contentOrNull ?: return@withContext

                // 3. Fetch artifacts
                val artifactsRequest = Request.Builder()
                    .url(artifactsUrl)
                    .apply {
                        if (token.isNotBlank()) header("Authorization", "token $token")
                    }
                    .build()

                val artifactsResponse = client.newCall(artifactsRequest).execute()
                 if (!artifactsResponse.isSuccessful) {
                    Log.e(TAG, "Failed to fetch artifacts: ${artifactsResponse.code}")
                    return@withContext
                }

                val artifactsBody = artifactsResponse.body?.string() ?: return@withContext
                val artifactsJson = json.parseToJsonElement(artifactsBody).jsonObject
                val artifacts = artifactsJson["artifacts"]?.jsonArray
                val firstArtifact = artifacts?.firstOrNull()?.jsonObject

                if (firstArtifact == null) {
                     Log.d(TAG, "No artifacts found")
                     return@withContext
                }

                val downloadUrl = firstArtifact["archive_download_url"]?.jsonPrimitive?.contentOrNull ?: return@withContext
                val artifactName = firstArtifact["name"]?.jsonPrimitive?.contentOrNull ?: "update"

                Log.d(TAG, "Downloading artifact: $artifactName from $downloadUrl")

                // 4. Download Artifact (ZIP)
                val downloadRequest = Request.Builder()
                    .url(downloadUrl)
                     .apply {
                        if (token.isNotBlank()) header("Authorization", "token $token")
                    }
                    .build()

                val downloadResponse = client.newCall(downloadRequest).execute()
                if (!downloadResponse.isSuccessful) {
                     Log.e(TAG, "Failed to download artifact: ${downloadResponse.code}")
                    return@withContext
                }

                val zipFile = File(context.cacheDir, "update.zip")
                val sink = java.io.BufferedOutputStream(FileOutputStream(zipFile))
                downloadResponse.body?.byteStream()?.use { input ->
                    input.copyTo(sink)
                }
                sink.close()

                // 5. Unzip to find APK
                var apkFile: File? = null
                ZipInputStream(zipFile.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".apk")) {
                            apkFile = File(context.cacheDir, "update.apk") // overwrite previous
                            val fos = FileOutputStream(apkFile)
                            zis.copyTo(fos)
                            fos.close()
                            break
                        }
                        entry = zis.nextEntry
                    }
                }

                zipFile.delete()

                if (apkFile != null) {
                    Log.d(TAG, "APK extracted, triggering install")
                    // 6. Trigger Install
                    installApk(context, apkFile)
                } else {
                    Log.e(TAG, "No APK found in zip")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update failed", e)
            }
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
        }
    }
}
