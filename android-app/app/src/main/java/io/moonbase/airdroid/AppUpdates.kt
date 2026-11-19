package io.moonbase.airdroid

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** A release published at github.com/RenzVillegas24/CrossPair/releases. */
data class AppRelease(
    val version: String,
    val tag: String,
    val name: String,
    val notes: String,
    val releaseUrl: String,
    val apkUrl: String?,
    val apkName: String?,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(
        val latestVersion: String,
        val release: AppRelease,
    ) : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Minimal GitHub Releases client used by the Settings screen. It deliberately
 * uses the public releases API instead of a bundled version feed, so the app
 * always checks the same place users download releases from.
 */
object AppUpdates {
    private const val API_URL =
        "https://api.github.com/repos/RenzVillegas24/CrossPair/releases/latest"
    private const val USER_AGENT = "CrossPair-Android"

    suspend fun check(currentVersion: String): UpdateState = withContext(Dispatchers.IO) {
        try {
            val payload = fetch(API_URL)
            val json = JSONObject(payload)
            val tag = json.optString("tag_name").trim()
            if (tag.isBlank()) {
                return@withContext UpdateState.Failed("GitHub returned no release tag.")
            }
            val version = normalizeTag(tag)
            val releaseUrl = json.optString("html_url").trim()
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            var apkName: String? = null
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val name = asset.optString("name").trim()
                    if (!name.endsWith(".apk", ignoreCase = true)) continue
                    val url = asset.optString("browser_download_url").trim()
                    if (url.isBlank()) continue
                    apkUrl = url
                    apkName = name
                    break
                }
            }
            val release = AppRelease(
                version = version,
                tag = tag,
                name = json.optString("name").trim().ifBlank { tag },
                notes = json.optString("body"),
                releaseUrl = releaseUrl,
                apkUrl = apkUrl,
                apkName = apkName,
            )
            if (isNewer(version, currentVersion)) {
                UpdateState.Available(release)
            } else {
                UpdateState.UpToDate(version, release)
            }
        } catch (e: Exception) {
            UpdateState.Failed(e.message?.takeIf { it.isNotBlank() } ?: "Could not check for updates.")
        }
    }

    /**
     * Downloads the release APK to app cache. Progress is delivered as an
     * integer percentage, or null when GitHub does not provide a size.
     */
    suspend fun download(
        context: Context,
        release: AppRelease,
        onProgress: suspend (Int?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val remote = release.apkUrl
            ?: throw IllegalStateException("This release does not contain an APK.")
        val safeName = (release.apkName ?: "CrossPair-${release.version}.apk")
            .substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "CrossPair-${release.version}.apk" }
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, safeName)
        val partial = File(directory, "$safeName.part")
        target.delete()
        partial.delete()

        val connection = (URL(remote).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("GitHub download failed (HTTP $code).")
            }
            val total = connection.contentLength.toLong().takeIf { it > 0 }
            var copied = 0L
            var lastPercent = Int.MIN_VALUE
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        val percent = total?.let { ((copied * 100) / it).toInt() } ?: -1
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent.takeIf { it >= 0 })
                        }
                    }
                    output.fd.sync()
                }
            }
            if (total != null && copied != total) {
                throw IllegalStateException("Download ended early ($copied of $total bytes).")
            }
            if (copied <= 0L) {
                throw IllegalStateException("Downloaded update is empty.")
            }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            target
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Starts the Android package installer for a downloaded APK.
     *
     * Returns a message when the installer could not be opened. A null return
     * means the installer activity was launched successfully.
     */
    fun install(context: Context, apk: File): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(settingsIntent)
                return "Allow CrossPair to install unknown apps, then tap Install update again."
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            null
        } catch (e: Exception) {
            "Could not open the Android installer: ${e.message}"
        }
    }

    /** Compares numeric release versions, ignoring a leading v and suffixes. */
    fun isNewer(latest: String, current: String): Boolean {
        val latestParts = versionParts(latest)
        val currentParts = versionParts(current)
        val count = maxOf(latestParts.size, currentParts.size)
        for (index in 0 until count) {
            val latestPart = latestParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return false
    }

    private fun normalizeTag(tag: String): String =
        tag.removePrefix("v").removePrefix("V").trim()

    private fun versionParts(version: String): List<Int> {
        val core = normalizeTag(version)
            .substringBefore('-')
            .substringBefore('+')
        return core.split('.')
            .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    }

    private fun fetch(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException(
                    "GitHub returned HTTP $code${text.takeIf { it.isNotBlank() }?.let { ": ${it.take(160)}" }.orEmpty()}",
                )
            }
            text
        } finally {
            connection.disconnect()
        }
    }
}
