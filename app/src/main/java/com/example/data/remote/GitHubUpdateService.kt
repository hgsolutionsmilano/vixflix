package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class GitHubReleaseInfo(
    val tagName: String,
    val releaseTitle: String,
    val changelog: String,
    val publishedAt: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long,
    val isUpdateAvailable: Boolean,
    val currentVersion: String = BuildConfig.VERSION_NAME
)

class GitHubUpdateService(
    private val okHttpClient: OkHttpClient
) {
    private val downloadHttpClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Controlla la presenza di una nuova versione su GitHub Releases per il repository specificato (`owner/repo`).
     */
    suspend fun checkForUpdates(ownerAndRepo: String): Result<GitHubReleaseInfo> = withContext(Dispatchers.IO) {
        val cleanedRepo = ownerAndRepo.trim()
            .removePrefix("https://github.com/")
            .removeSuffix(".git")
            .trim('/')

        if (!cleanedRepo.contains("/") || cleanedRepo.split("/").size != 2) {
            return@withContext Result.failure(
                IllegalArgumentException("Formato repository non valido. Usa 'owner/repo' (es. tuo-utente/vixflix).")
            )
        }

        runCatching {
            val latestUrl = "https://api.github.com/repos/$cleanedRepo/releases/latest"
            val latestRequest = Request.Builder()
                .url(latestUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "VixFlix-Android/${BuildConfig.VERSION_NAME}")
                .build()

            var releaseJson: JSONObject? = null
            okHttpClient.newCall(latestRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        releaseJson = JSONObject(body)
                    }
                }
            }

            if (releaseJson == null) {
                val listUrl = "https://api.github.com/repos/$cleanedRepo/releases?per_page=5"
                val listRequest = Request.Builder()
                    .url(listUrl)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "VixFlix-Android/${BuildConfig.VERSION_NAME}")
                    .build()

                okHttpClient.newCall(listRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("GitHub API ha risposto con codice ${response.code} per $cleanedRepo")
                    }
                    val arr = JSONArray(response.body?.string().orEmpty())
                    if (arr.length() == 0) {
                        throw IllegalStateException("Nessuna release trovata su GitHub per $cleanedRepo. Effettua un check-in per avviare la GitHub Action!")
                    }
                    releaseJson = arr.getJSONObject(0)
                }
            }

            val json = releaseJson ?: throw IllegalStateException("Risposta GitHub Releases vuota")
            val tagName = json.optString("tag_name", "v1.0.0")
            val name = json.optString("name", tagName).ifBlank { tagName }
            val body = json.optString("body", "Nuova versione compilata automaticamente da GitHub Actions.")
            val publishedAt = json.optString("published_at", "").take(10)
            val htmlUrl = json.optString("html_url", "https://github.com/$cleanedRepo/releases")

            var apkUrl: String? = null
            var apkName: String? = null
            var apkSize = 0L

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkName = assetName
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            val isNewer = isRemoteVersionNewer(
                remoteTag = tagName,
                localVersionName = BuildConfig.VERSION_NAME
            )

            GitHubReleaseInfo(
                tagName = tagName,
                releaseTitle = name,
                changelog = body,
                publishedAt = publishedAt,
                htmlUrl = htmlUrl,
                apkDownloadUrl = apkUrl,
                apkFileName = apkName,
                apkSizeBytes = apkSize,
                isUpdateAvailable = isNewer,
                currentVersion = BuildConfig.VERSION_NAME
            )
        }
    }

    fun isRemoteVersionNewer(remoteTag: String, localVersionName: String): Boolean {
        val remoteClean = remoteTag.trim().removePrefix("v").removePrefix("V")
        val localClean = localVersionName.trim().removePrefix("v").removePrefix("V")
        if (remoteClean == localClean) return false

        val remoteParts = remoteClean.split(".", "-").mapNotNull { it.toIntOrNull() }
        val localParts = localClean.split(".", "-").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return remoteClean != localClean && remoteClean.isNotBlank()
    }

    /**
     * Scarica automaticamente in background il file APK della nuova release,
     * emettendo l'avanzamento in tempo reale e restituendo il File pronto per l'installazione.
     */
    suspend fun downloadReleaseApkInBackground(
        context: Context,
        release: GitHubReleaseInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long, progressFraction: Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val downloadUrl = release.apkDownloadUrl
            ?: return@withContext Result.failure(
                IllegalStateException("Nessun file APK allegato alla release ${release.tagName}.")
            )

        runCatching {
            val safeFileName = (release.apkFileName ?: "VixFlix-${release.tagName}.apk")
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.cacheDir, "updates").apply { mkdirs() }
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val destinationFile = File(targetDir, safeFileName)
            val tempFile = File(targetDir, "$safeFileName.part")
            if (tempFile.exists()) {
                tempFile.delete()
            }

            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "VixFlix-Android/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/octet-stream, application/vnd.android.package-archive, */*")
                .build()

            downloadHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Errore download APK da GitHub (HTTP ${response.code})")
                }
                val body = response.body ?: throw IllegalStateException("Corpo APK vuoto durante il download")
                val expectedBytes = body.contentLength().takeIf { it > 0L } ?: release.apkSizeBytes

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var lastReported = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (downloaded - lastReported >= 64 * 1024 || downloaded == expectedBytes) {
                                lastReported = downloaded
                                val fraction = if (expectedBytes > 0L) {
                                    (downloaded.toFloat() / expectedBytes.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0.5f
                                }
                                onProgress(downloaded, expectedBytes, fraction)
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }

            onProgress(destinationFile.length(), destinationFile.length(), 1f)
            destinationFile
        }
    }

    /**
     * Avvia immediatamente il processo di installazione/aggiornamento Android per l'APK scaricato.
     */
    fun launchApkInstaller(context: Context, apkFile: File): Boolean {
        return runCatching {
            if (!apkFile.exists()) return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val permissionIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(permissionIntent)
                    return true
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
            true
        }.getOrElse {
            false
        }
    }

    /**
     * Fallback o apertura manuale se il file locale non è ancora presente.
     */
    fun triggerInAppUpdate(context: Context, release: GitHubReleaseInfo, downloadedFile: File? = null) {
        if (downloadedFile != null && downloadedFile.exists()) {
            if (launchApkInstaller(context, downloadedFile)) return
        }
        val targetUrl = release.apkDownloadUrl ?: release.htmlUrl
        runCatching {
            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
        }
    }
}
