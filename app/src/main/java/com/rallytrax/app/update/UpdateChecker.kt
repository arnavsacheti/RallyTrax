package com.rallytrax.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val body: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val publishedAt: String,
)

@Singleton
class UpdateChecker @Inject constructor() {

    companion object {
        private const val GITHUB_OWNER = "arnavsacheti"
        private const val GITHUB_REPO = "RallyTrax"
        private const val API_URL =
            "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    }

    /**
     * Fetches the latest release from GitHub and returns [ReleaseInfo],
     * or null if the request fails.
     */
    suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            if (connection.responseCode != 200) {
                connection.disconnect()
                return@withContext null
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(responseBody)
            val tagName = json.optString("tag_name", "")
            val versionName = tagName.removePrefix("v")
            val releaseName = json.optString("name", tagName)
            val body = json.optString("body", "")
            val htmlUrl = json.optString("html_url", "")
            val publishedAt = json.optString("published_at", "")

            // Find APK asset
            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }

            ReleaseInfo(
                tagName = tagName,
                versionName = versionName,
                releaseName = releaseName,
                body = body,
                htmlUrl = htmlUrl,
                apkDownloadUrl = apkUrl,
                publishedAt = publishedAt,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns true if [remoteVersion] is newer than [currentVersion].
     * Compares semantic version components (major.minor.patch).
     */
    fun isNewer(currentVersion: String, remoteVersion: String): Boolean {
        val current = parseVersion(currentVersion)
        val remote = parseVersion(remoteVersion)

        for (i in 0 until maxOf(current.size, remote.size)) {
            val c = current.getOrElse(i) { 0 }
            val r = remote.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun parseVersion(version: String): List<Int> {
        return version.removePrefix("v")
            .split(".")
            .mapNotNull { it.toIntOrNull() }
    }
}
