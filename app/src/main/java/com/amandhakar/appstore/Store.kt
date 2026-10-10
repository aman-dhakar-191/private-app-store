package com.amandhakar.appstore

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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

/** Loading logic shared by the screen and the background update check. */
object Store {

    /**
     * The catalog apps with their latest releases. Inside coroutineScope, a failed request throws
     * to the caller instead of crashing the app.
     */
    suspend fun loadApps(context: Context): List<AppItem> = coroutineScope {
        // The store is updated through its own banner, so it is not listed with the other apps.
        val catalog = GitHub.fetchCatalog(BuildConfig.CATALOG_URL)
            .filter { it.packageName != BuildConfig.APPLICATION_ID }
        catalog.map { app ->
            async { AppItem(app, GitHub.releaseFor(app), installedVersion(context, app.packageName)) }
        }.awaitAll()
    }

    /**
     * A newer release of the store itself, or null. Checked separately from [loadApps], so a broken
     * catalog or app repo never hides a store update (which may be the fix).
     */
    suspend fun storeUpdate(): Release? = GitHub.latestRelease(BuildConfig.STORE_REPO)
        ?.takeIf { compareVersions(it.version, BuildConfig.VERSION_NAME) > 0 }

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
