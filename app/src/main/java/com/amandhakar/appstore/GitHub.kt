package com.amandhakar.appstore

import android.content.SharedPreferences
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
    /** Lowest Android API level the app runs on (from "minSdk"); 0 when not given. */
    val minSdk: Int = 0,
    /**
     * True for entries in "prereleaseApps": the app is published as GitHub pre-releases whose tags
     * start with [tagPrefix], instead of as the repo's latest release.
     */
    val prerelease: Boolean = false,
    val tagPrefix: String = "v",
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

/**
 * index.json from the `index` branch: every catalog app's latest release, written by the
 * "Release index" workflow so phones don't call the GitHub API once per app.
 */
data class ReleaseIndex(val store: Release?, val releases: Map<String, Release?>) {
    /** True when the index lists this app (its release may still be null: no release yet). */
    fun has(app: CatalogApp): Boolean = releases.containsKey(indexKey(app))
    fun release(app: CatalogApp): Release? = releases[indexKey(app)]
}

/** Must match index_key() in scripts/build-index.py. */
fun indexKey(app: CatalogApp): String = if (app.prerelease) "${app.repo}#${app.tagPrefix}" else app.repo

/** A downloaded file, or the copy saved last time with the reason the download failed. */
data class Fetched(val body: String, val error: String?)

object GitHub {

    /**
     * Downloads [url] and saves the body in [saved]. When the download fails, returns the copy saved
     * last time instead, with the error, so the store can still show its list offline or when
     * rate limited. Throws only when there is no saved copy either.
     */
    suspend fun fetchSaved(saved: SharedPreferences, url: String): Fetched = withContext(Dispatchers.IO) {
        try {
            val body = get(url)
            saved.edit().putString(url, body).apply()
            Fetched(body, null)
        } catch (e: IOException) {
            val old = saved.getString(url, null) ?: throw e
            Fetched(old, e.message ?: e.toString())
        }
    }

    /**
     * Reads both lists of catalog.json. Pre-release apps live in their own "prereleaseApps" list so
     * older store versions, which only read "apps", never see them.
     */
    fun parseCatalog(body: String): List<CatalogApp> {
        val json = JSONObject(body)
        return json.catalogList("apps", prerelease = false) + json.catalogList("prereleaseApps", prerelease = true)
    }

    fun parseIndex(body: String): ReleaseIndex {
        val json = JSONObject(body)
        val releases = json.getJSONObject("releases")
        return ReleaseIndex(
            store = json.optJSONObject("store")?.toIndexedRelease(),
            releases = releases.keys().asSequence().associateWith { releases.optJSONObject(it)?.toIndexedRelease() },
        )
    }

    private fun JSONObject.toIndexedRelease() = Release(
        tag = getString("tag"),
        version = getString("version"),
        notes = optString("notes"),
        apkUrl = getString("apkUrl"),
        apkSize = optLong("apkSize"),
    )

    private fun JSONObject.catalogList(key: String, prerelease: Boolean): List<CatalogApp> =
        (optJSONArray(key) ?: JSONArray()).objects().map { app ->
            CatalogApp(
                name = app.getString("name"),
                packageName = app.getString("package"),
                repo = app.getString("repo"),
                description = app.optString("description"),
                minSdk = app.optInt("minSdk", 0),
                prerelease = prerelease,
                tagPrefix = app.optString("tagPrefix").ifEmpty { "v" },
            )
        }

    suspend fun releaseFor(app: CatalogApp): Release? =
        if (app.prerelease) latestTaggedRelease(app.repo, app.tagPrefix) else latestRelease(app.repo)

    /** Returns null when the repo has no release, or its latest release has no .apk asset. */
    suspend fun latestRelease(repo: String): Release? = withContext(Dispatchers.IO) {
        val body = try {
            get("https://api.github.com/repos/$repo/releases/latest")
        } catch (e: GitHubException) {
            if (e.message?.startsWith("404") == true) return@withContext null
            throw e
        }
        JSONObject(body).toRelease("v")
    }

    /**
     * The highest version among the repo's recent releases (pre-releases included) whose tag starts
     * with [prefix] and that has an .apk attached.
     */
    suspend fun latestTaggedRelease(repo: String, prefix: String): Release? = withContext(Dispatchers.IO) {
        JSONArray(get("https://api.github.com/repos/$repo/releases?per_page=50")).objects()
            .filter { !it.optBoolean("draft") && it.getString("tag_name").startsWith(prefix) }
            .mapNotNull { it.toRelease(prefix) }
            .maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
    }

    private fun JSONObject.toRelease(prefix: String): Release? {
        val apk = getJSONArray("assets").objects()
            .firstOrNull { it.getString("name").endsWith(".apk", ignoreCase = true) }
            ?: return null
        val tag = getString("tag_name")
        return Release(
            tag = tag,
            version = tag.removePrefix(prefix).removePrefix("v"),
            notes = optString("body"),
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
