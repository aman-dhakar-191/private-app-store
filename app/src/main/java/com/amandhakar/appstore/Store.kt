package com.amandhakar.appstore

import android.content.Context
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONException
import java.io.IOException
import java.util.Collections

data class AppItem(
    val app: CatalogApp,
    val release: Release?,
    val installedVersion: String?,
    /** Download progress 0..1 while downloading, null otherwise. */
    val progress: Float? = null,
) {
    val updateAvailable: Boolean
        get() = release != null && installedVersion != null &&
            compareVersions(release.version, installedVersion) > 0

    /** False when this phone's Android version is older than the app needs. */
    val compatible: Boolean
        get() = Build.VERSION.SDK_INT >= app.minSdk
}

/** The app list, plus a note for the screen when some of it is old or missing. */
data class LoadedApps(val items: List<AppItem>, val notice: String?)

/** Loading logic shared by the screen and the background update check. */
object Store {

    /**
     * The catalog apps with their latest releases. Releases come from index.json (one request for
     * all apps); only apps the index doesn't list yet are asked of the GitHub API. A failed request
     * falls back to the copy saved last time; this throws only when nothing was ever saved.
     */
    suspend fun loadApps(context: Context): LoadedApps = coroutineScope {
        val saved = savedResponses(context)
        val catalog = GitHub.fetchSaved(saved, BuildConfig.CATALOG_URL)
        val index = loadIndex(saved)
        // The store is updated through its own banner, so it is not listed with the other apps.
        val apps = GitHub.parseCatalog(catalog.body).filter { it.packageName != BuildConfig.APPLICATION_ID }
        // The background check runs these on several threads at once.
        val unchecked = Collections.synchronizedList(mutableListOf<Pair<String, String>>())
        val items = apps.map { app ->
            async {
                val listed = index?.value?.takeIf { it.has(app) }
                val release = if (listed != null) {
                    listed.release(app)
                } else {
                    try {
                        GitHub.releaseFor(app)
                    } catch (e: IOException) {
                        unchecked += app.name to (e.message ?: e.toString())
                        null
                    }
                }
                AppItem(app, release, installedVersion(context, app.packageName))
            }
        }.awaitAll()
        val notice = when {
            catalog.error != null -> "Showing the list saved earlier. ${catalog.error}"
            unchecked.isNotEmpty() ->
                "Couldn't check ${unchecked.joinToString { it.first }}. ${unchecked.first().second}"
            else -> index?.error?.let { "Showing versions saved earlier. $it" }
        }
        LoadedApps(items, notice)
    }

    /**
     * A newer release of the store itself, or null. Checked separately from [loadApps], so a broken
     * catalog or app repo never hides a store update (which may be the fix).
     */
    suspend fun storeUpdate(context: Context): Release? {
        val index = loadIndex(savedResponses(context))
        val latest = if (index != null) index.value.store else GitHub.latestRelease(BuildConfig.STORE_REPO)
        return latest?.takeIf { compareVersions(it.version, BuildConfig.VERSION_NAME) > 0 }
    }

    private data class LoadedIndex(val value: ReleaseIndex, val error: String?)

    /** index.json, or null when it can't be had at all (then the GitHub API is used instead). */
    private suspend fun loadIndex(saved: SharedPreferences): LoadedIndex? = try {
        val fetched = GitHub.fetchSaved(saved, BuildConfig.INDEX_URL)
        LoadedIndex(GitHub.parseIndex(fetched.body), fetched.error)
    } catch (e: IOException) {
        null
    } catch (e: JSONException) {
        null
    }

    private fun savedResponses(context: Context): SharedPreferences =
        context.getSharedPreferences("saved-responses", Context.MODE_PRIVATE)

    fun installedVersion(context: Context, packageName: String): String? = try {
        context.packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** "14" for API 34, and so on; "API n" for levels this table doesn't know yet. */
    fun androidVersionName(sdk: Int): String = when (sdk) {
        26 -> "8.0"
        27 -> "8.1"
        28 -> "9"
        29 -> "10"
        30 -> "11"
        31 -> "12"
        32 -> "12L"
        33 -> "13"
        34 -> "14"
        35 -> "15"
        36 -> "16"
        37 -> "17"
        else -> "API $sdk"
    }
}
