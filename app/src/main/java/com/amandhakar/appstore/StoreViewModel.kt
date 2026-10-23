package com.amandhakar.appstore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
        val context = getApplication<Application>()
        viewModelScope.launch {
            // Store update first and on its own: it must show even when the app list fails.
            try {
                val update = Store.storeUpdate(context)
                _state.update { it.copy(storeUpdate = update) }
            } catch (e: Exception) {
                // Keep the last known result; the app list below reports network problems.
            }
            try {
                val loaded = Store.loadApps(context)
                _state.update { it.copy(loading = false, items = loaded.items, error = loaded.notice) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    /** Re-reads installed versions only; cheap, so it runs whenever the screen resumes. */
    fun refreshInstalled() {
        val context = getApplication<Application>()
        _state.update { s ->
            s.copy(items = s.items.map { it.copy(installedVersion = Store.installedVersion(context, it.app.packageName)) })
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
}
