package com.example.data.remote

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.example.BuildConfig
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
     * Avvia il download dell'APK della nuova release tramite DownloadManager di sistema
     * e apre il link della release per l'aggiornamento immediato.
     */
    fun triggerInAppUpdate(context: Context, release: GitHubReleaseInfo): Long? {
        val targetUrl = release.apkDownloadUrl ?: release.htmlUrl
        val fileName = release.apkFileName ?: "VixFlix-${release.tagName}.apk"

        val downloadId = runCatching {
            if (release.apkDownloadUrl != null) {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val request = DownloadManager.Request(Uri.parse(release.apkDownloadUrl))
                    .setTitle("Aggiornamento VixFlix ${release.tagName}")
                    .setDescription("Download di $fileName da GitHub Releases…")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setMimeType("application/vnd.android.package-archive")
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                dm.enqueue(request)
            } else null
        }.getOrNull()

        runCatching {
            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
        }

        return downloadId
    }
}
