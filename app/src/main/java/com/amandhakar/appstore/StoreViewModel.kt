package com.amandhakar.appstore

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

data class StoreState(
    val loading: Boolean = false,
    val items: List<AppItem> = emptyList(),
    val error: String? = null,
    /** A newer release of the store itself, if there is one. */
    val storeUpdate: Release? = null,
    /** Download progress of the store update, null when not downloading. */
    val storeProgress: Float? = null,
)

class StoreViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(StoreState())
    val state: StateFlow<StoreState> = _state

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                // coroutineScope makes a failed request throw here instead of crashing the app.
                val (items, storeUpdate) = coroutineScope { loadStore() }
                _state.update { it.copy(loading = false, items = items, storeUpdate = storeUpdate) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    private suspend fun CoroutineScope.loadStore(): Pair<List<AppItem>, Release?> {
        val storeRelease = async { GitHub.latestRelease(BuildConfig.STORE_REPO) }
        // The store is updated through its own banner, so it is not listed with the other apps.
        val catalog = GitHub.fetchCatalog(BuildConfig.CATALOG_URL)
            .filter { it.packageName != BuildConfig.APPLICATION_ID }
        val items = catalog.map { app ->
            async { AppItem(app, GitHub.latestRelease(app.repo), installedVersion(app.packageName)) }
        }.awaitAll()
        val storeUpdate = storeRelease.await()
            ?.takeIf { compareVersions(it.version, BuildConfig.VERSION_NAME) > 0 }
        return items to storeUpdate
    }

    /** Re-reads installed versions only; cheap, so it runs whenever the screen resumes. */
    fun refreshInstalled() {
        _state.update { s ->
            s.copy(items = s.items.map { it.copy(installedVersion = installedVersion(it.app.packageName)) })
        }
    }

    fun install(item: AppItem) {
        val release = item.release ?: return
        if (item.progress != null) return
        downloadAndInstall(release) { p -> setProgress(item, p) }
    }

    /** Updates the store itself. Android closes the store while it installs the new version. */
    fun updateStore() {
        val release = _state.value.storeUpdate ?: return
        if (_state.value.storeProgress != null) return
        downloadAndInstall(release) { p -> _state.update { it.copy(storeProgress = p) } }
    }

    private fun downloadAndInstall(release: Release, setProgress: (Float?) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            setProgress(0f)
            try {
                val apk = Installer.download(context, release.apkUrl) { p -> setProgress(p) }
                Installer.install(context, apk)
            } catch (e: Exception) {
                Installer.report("Download failed: ${e.message}")
            } finally {
                setProgress(null)
            }
        }
    }

    private fun setProgress(item: AppItem, progress: Float?) {
        _state.update { s ->
            s.copy(items = s.items.map { if (it.app == item.app) it.copy(progress = progress) else it })
        }
    }

    private fun installedVersion(packageName: String): String? = try {
        getApplication<Application>().packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
