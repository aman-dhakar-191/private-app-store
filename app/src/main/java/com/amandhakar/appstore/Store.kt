package com.amandhakar.appstore

import android.content.Context
import android.content.pm.PackageManager
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
}

data class StoreSnapshot(
    val items: List<AppItem>,
    /** A newer release of the store itself, if there is one. */
    val storeUpdate: Release?,
)

/** Loading logic shared by the screen and the background update check. */
object Store {

    suspend fun load(context: Context): StoreSnapshot = coroutineScope {
        // Inside coroutineScope, a failed request throws to the caller instead of crashing the app.
        val storeRelease = async { GitHub.latestRelease(BuildConfig.STORE_REPO) }
        // The store is updated through its own banner, so it is not listed with the other apps.
        val catalog = GitHub.fetchCatalog(BuildConfig.CATALOG_URL)
            .filter { it.packageName != BuildConfig.APPLICATION_ID }
        val items = catalog.map { app ->
            async { AppItem(app, GitHub.latestRelease(app.repo), installedVersion(context, app.packageName)) }
        }.awaitAll()
        val storeUpdate = storeRelease.await()
            ?.takeIf { compareVersions(it.version, BuildConfig.VERSION_NAME) > 0 }
        StoreSnapshot(items, storeUpdate)
    }

    fun installedVersion(context: Context, packageName: String): String? = try {
        context.packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
