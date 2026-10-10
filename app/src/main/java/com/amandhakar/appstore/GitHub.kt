package com.amandhakar.appstore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One entry in catalog.json. */
data class CatalogApp(
    val name: String,
    val packageName: String,
    val repo: String,
    val description: String,
)

/** The latest GitHub release of an app that has an APK attached. */
data class Release(
    val tag: String,
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
)

class GitHubException(message: String) : IOException(message)

object GitHub {

    suspend fun fetchCatalog(url: String): List<CatalogApp> = withContext(Dispatchers.IO) {
        val apps = JSONObject(get(url)).getJSONArray("apps")
        (0 until apps.length()).map { i ->
            val app = apps.getJSONObject(i)
            CatalogApp(
                name = app.getString("name"),
                packageName = app.getString("package"),
                repo = app.getString("repo"),
                description = app.optString("description"),
            )
        }
    }

    /** Returns null when the repo has no release, or its latest release has no .apk asset. */
    suspend fun latestRelease(repo: String): Release? = withContext(Dispatchers.IO) {
        val body = try {
            get("https://api.github.com/repos/$repo/releases/latest")
        } catch (e: GitHubException) {
            if (e.message?.startsWith("404") == true) return@withContext null
            throw e
        }
        val json = JSONObject(body)
        val apk = json.getJSONArray("assets").objects()
            .firstOrNull { it.getString("name").endsWith(".apk", ignoreCase = true) }
            ?: return@withContext null
        val tag = json.getString("tag_name")
        Release(
            tag = tag,
            version = tag.removePrefix("v"),
            notes = json.optString("body"),
            apkUrl = apk.getString("browser_download_url"),
            apkSize = apk.optLong("size"),
        )
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            val code = conn.responseCode
            if (code == 403 || code == 429) {
                throw GitHubException("$code: GitHub rate limit reached. Try again in a while.")
            }
            if (code !in 200..299) throw GitHubException("$code: request to $url failed")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}

/**
 * Compares dotted versions numerically ("1.10.0" > "1.9.2"). Non-numeric parts are
 * compared as text, so "1.0.0-beta" and "1.0.0" are still told apart.
 */
fun compareVersions(a: String, b: String): Int {
    val pa = a.split('.', '-', '+')
    val pb = b.split('.', '-', '+')
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { "0" }
        val y = pb.getOrElse(i) { "0" }
        val nx = x.toIntOrNull()
        val ny = y.toIntOrNull()
        val c = if (nx != null && ny != null) nx.compareTo(ny) else x.compareTo(y)
        if (c != 0) return c
    }
    return 0
}
