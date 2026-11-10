package com.coursetable.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"
}

internal sealed class UpdateCheckResult {
    data object UpToDate : UpdateCheckResult()
    data class UpdateAvailable(val latestVersion: String, val releaseUrl: String) : UpdateCheckResult()
    data class Failed(val message: String) : UpdateCheckResult()
}

internal object GithubReleaseCheck {
    const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/liondoge123/CourseTable/releases/latest"
    const val RELEASES_URL = "https://github.com/liondoge123/CourseTable/releases"
    const val UNAVAILABLE = "暂时无法连接 GitHub"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun parseVersion(raw: String): SemanticVersion? {
        val match = VERSION.find(raw.trim()) ?: return null
        return SemanticVersion(
            match.groupValues[1].toInt(),
            match.groupValues[2].toInt(),
            match.groupValues[3].toInt(),
        )
    }

    fun interpret(currentVersionName: String, tagName: String, htmlUrl: String?): UpdateCheckResult {
        val current = parseVersion(currentVersionName)
            ?: return UpdateCheckResult.Failed("无法识别当前版本")
        val latest = parseVersion(tagName)
            ?: return UpdateCheckResult.Failed("无法识别最新版本")
        if (latest <= current) return UpdateCheckResult.UpToDate
        val releaseUrl = htmlUrl
            ?.takeIf { it.startsWith("https://github.com/liondoge123/CourseTable/") }
            ?: RELEASES_URL
        return UpdateCheckResult.UpdateAvailable(latest.toString(), releaseUrl)
    }

    fun parseRelease(body: String): Pair<String, String?>? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val tag = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        val url = json.optString("html_url").takeIf { it.isNotBlank() }
        return tag to url
    }

    suspend fun check(currentVersionName: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val body = try {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "CourseTable")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext UpdateCheckResult.Failed(UNAVAILABLE)
                response.body?.string().orEmpty()
            }
        } catch (_: Exception) {
            return@withContext UpdateCheckResult.Failed(UNAVAILABLE)
        }
        val release = parseRelease(body) ?: return@withContext UpdateCheckResult.Failed("没有读到最新版本")
        interpret(currentVersionName, release.first, release.second)
    }

    private val VERSION = Regex("""v?(\d+)\.(\d+)\.(\d+)""")
}
