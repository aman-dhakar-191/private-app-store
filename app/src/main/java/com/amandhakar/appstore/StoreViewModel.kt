package com.amandhakar.appstore

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONException
import java.io.IOException
import java.text.DateFormat
import java.util.Date

data class StoreState(
    val loading: Boolean = false,
    val items: List<AppItem> = emptyList(),
    val error: String? = null,
    /** A newer release of the store itself, if there is one. */
    val storeUpdate: Release? = null,
    /** Download progress of the store update, null when not downloading. */
    val storeProgress: Float? = null,
    /** Package name of the app whose page is open, null on the list. */
    val openApp: String? = null,
    /** Result of the last direct GitHub check per package name. */
    val appChecks: Map<String, AppCheck> = emptyMap(),
)

/** The app page's own check of one app on GitHub. */
data class AppCheck(val checking: Boolean, val message: String)

class StoreViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(StoreState())
    val state: StateFlow<StoreState> = _state

    /**
     * Releases found by an app page's direct check. The release index can lag a few minutes
     * behind GitHub, so a refresh keeps these when they are newer than what the index says.
     */
    private val checked = mutableMapOf<String, Pair<Long, Release?>>()

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
                val items = loaded.items.map { item ->
                    val direct = checked[item.app.packageName]?.second
                    val listed = item.release
                    if (direct != null && (listed == null || compareVersions(direct.version, listed.version) > 0)) {
                        item.copy(release = direct)
                    } else {
                        item
                    }
                }
                _state.update { it.copy(loading = false, items = items, error = loaded.notice) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    /**
     * Opens an app's page and checks that one app on GitHub directly: one API call, skipped when
     * it was checked in the last few minutes. The list itself comes from the release index.
     */
    fun openApp(item: AppItem) {
        val pkg = item.app.packageName
        _state.update { it.copy(openApp = pkg) }
        refreshInstalled()
        val last = checked[pkg]?.first
        if (last != null && SystemClock.elapsedRealtime() - last < RECHECK_MS) return
        if (_state.value.appChecks[pkg]?.checking == true) return
        setCheck(pkg, AppCheck(checking = true, message = "Checking GitHub for the latest version…"))
        viewModelScope.launch {
            val check = try {
                val release = GitHub.releaseFor(item.app)
                checked[pkg] = SystemClock.elapsedRealtime() to release
                _state.update { s ->
                    s.copy(items = s.items.map { if (it.app.packageName == pkg) it.copy(release = release) else it })
                }
                val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
                AppCheck(checking = false, message = "Checked on GitHub at $time.")
            } catch (e: IOException) {
                AppCheck(checking = false, message = "Couldn't check GitHub (${e.message}). Showing the version from the store list.")
            } catch (e: JSONException) {
                AppCheck(checking = false, message = "Couldn't read GitHub's answer. Showing the version from the store list.")
            }
            setCheck(pkg, check)
        }
    }

    fun closeApp() {
        _state.update { it.copy(openApp = null) }
    }

    private fun setCheck(pkg: String, check: AppCheck) {
        _state.update { it.copy(appChecks = it.appChecks + (pkg to check)) }
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

    private companion object {
        /** How long a direct check of one app stays fresh. */
        const val RECHECK_MS = 5 * 60 * 1000L
    }
}
