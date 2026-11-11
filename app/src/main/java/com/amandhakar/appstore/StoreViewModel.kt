package com.amandhakar.appstore

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    /** Where the store's own update is up to, null when nothing is happening. */
    val storeDownload: DownloadState? = null,
    /** How far "Update all" has got, null when it isn't running. */
    val updateAll: UpdateAllRun? = null,
    /** Package name of the app whose page is open, null on the list. */
    val openApp: String? = null,
    /** Result of the last direct GitHub check per package name. */
    val appChecks: Map<String, AppCheck> = emptyMap(),
    /** Result of the last "Check for update" of the store itself, null before the first. */
    val storeCheck: StoreCheck? = null,
)

/** The app page's own check of one app on GitHub. */
data class AppCheck(val checking: Boolean, val message: String)

/** The "Check for update" button's check of the store itself on GitHub. */
data class StoreCheck(val checking: Boolean, val message: String)

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
                // A refresh must not wipe the progress of a download that is still going.
                val downloads = _state.value.items.associate { it.app.packageName to it.download }
                val items = loaded.items.map { fresh ->
                    val item = fresh.copy(download = downloads[fresh.app.packageName])
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
     * Asks GitHub directly (one API call) whether there is a newer store, instead of reading the
     * release index, which can lag a few minutes. A newer release shows the usual update banner.
     */
    fun checkStoreUpdate() {
        if (_state.value.storeCheck?.checking == true) return
        _state.update { it.copy(storeCheck = StoreCheck(checking = true, message = "Checking GitHub…")) }
        viewModelScope.launch {
            val check = try {
                val latest = GitHub.latestRelease(BuildConfig.STORE_REPO)
                val newer = latest?.takeIf { compareVersions(it.version, BuildConfig.VERSION_NAME) > 0 }
                _state.update { it.copy(storeUpdate = newer) }
                val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
                StoreCheck(checking = false, message = StoreUpdateText.result(BuildConfig.VERSION_NAME, latest?.version, time))
            } catch (e: IOException) {
                StoreCheck(checking = false, message = "Couldn't check GitHub (${e.message}).")
            } catch (e: JSONException) {
                StoreCheck(checking = false, message = "Couldn't read GitHub's answer.")
            }
            _state.update { it.copy(storeCheck = check) }
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

    /** Downloads and installs an app, or tries again after a failure. Does nothing while one is already going. */
    fun install(item: AppItem) {
        val release = item.release ?: return
        val pkg = item.app.packageName
        val current = item.download
        if (current != null && current !is DownloadState.Failed) return
        launchJob(pkg) { runDownload(release) { setItemDownload(pkg, it) } }
    }

    /** Stops an app's download. If it was only waiting its turn in Update all, it is taken out of the queue. */
    fun cancelDownload(item: AppItem) {
        val pkg = item.app.packageName
        jobs[pkg]?.cancel()
        if (currentDownload(pkg) is DownloadState.Queued) setItemDownload(pkg, null)
    }

    /** Clears an app's failure message. */
    fun dismissFailure(item: AppItem) {
        if (currentDownload(item.app.packageName) is DownloadState.Failed) setItemDownload(item.app.packageName, null)
    }

    /** Updates the store itself. Android closes the store while it installs the new version. */
    fun updateStore() {
        val release = _state.value.storeUpdate ?: return
        val current = _state.value.storeDownload
        if (current != null && current !is DownloadState.Failed) return
        launchJob(STORE_KEY) { runDownload(release) { st -> _state.update { it.copy(storeDownload = st) } } }
    }

    fun cancelStoreDownload() {
        jobs[STORE_KEY]?.cancel()
    }

    fun dismissStoreFailure() {
        _state.update { if (it.storeDownload is DownloadState.Failed) it.copy(storeDownload = null) else it }
    }

    /**
     * Updates every app that has an update, one after another: each is downloaded, then Android
     * asks for its confirmation, and only when that is answered does the next one start. The
     * store's own update is not part of this, because installing it restarts the store.
     */
    fun updateAll() {
        if (_state.value.updateAll != null) return
        val todo = UpdateAll.pending(_state.value.items)
        if (todo.isEmpty()) return
        todo.forEach { setItemDownload(it.app.packageName, DownloadState.Queued) }
        launchJob(UPDATE_ALL_KEY) {
            try {
                for ((index, item) in todo.withIndex()) {
                    val pkg = item.app.packageName
                    // Skipped when the user took this one out of the queue while it waited.
                    if (currentDownload(pkg) !is DownloadState.Queued) continue
                    _state.update { it.copy(updateAll = UpdateAllRun(done = index, total = todo.size)) }
                    val release = item.release
                    if (release == null) {
                        setItemDownload(pkg, null)
                        continue
                    }
                    launchJob(pkg) { runDownload(release) { setItemDownload(pkg, it) } }.join()
                }
            } finally {
                // Cancelled part-way: the one running stops and the ones still waiting are dropped.
                todo.forEach { item ->
                    val pkg = item.app.packageName
                    jobs[pkg]?.cancel()
                    if (currentDownload(pkg) is DownloadState.Queued) setItemDownload(pkg, null)
                }
                _state.update { it.copy(updateAll = null) }
            }
        }
    }

    fun cancelUpdateAll() {
        jobs[UPDATE_ALL_KEY]?.cancel()
    }

    // Running downloads, by package name (or the keys below), so they can be cancelled.
    private val jobs = mutableMapOf<String, Job>()

    private fun launchJob(key: String, block: suspend () -> Unit): Job {
        jobs[key]?.cancel()
        val job = viewModelScope.launch { block() }
        jobs[key] = job
        job.invokeOnCompletion { if (jobs[key] === job) jobs.remove(key) }
        return job
    }

    /**
     * Downloads, checks and installs one release, reporting each step through [set]. Returns true
     * when Android installed it. A failure becomes a [DownloadState.Failed] to show (with Retry);
     * cancelling clears the state.
     */
    private suspend fun runDownload(release: Release, set: (DownloadState?) -> Unit): Boolean {
        val context = getApplication<Application>()
        set(DownloadState.Downloading(0, 0))
        return try {
            val apk = Installer.download(context, release.apkUrl, release.sha256) { done, total ->
                set(DownloadState.Downloading(done, total))
            }
            set(DownloadState.Installing)
            val installed = Installer.install(context, apk)
            // The result comes when the person answers Android's confirmation screen, so allow for that.
            val ok = withTimeoutOrNull(INSTALL_WAIT_MS) { installed.await() } == true
            set(null)
            ok
        } catch (e: CancellationException) {
            set(null)
            throw e
        } catch (e: Exception) {
            set(DownloadState.Failed(e.message ?: "Download failed"))
            false
        }
    }

    private fun setItemDownload(pkg: String, state: DownloadState?) {
        _state.update { s ->
            s.copy(items = s.items.map { if (it.app.packageName == pkg) it.copy(download = state) else it })
        }
    }

    private fun currentDownload(pkg: String): DownloadState? =
        _state.value.items.firstOrNull { it.app.packageName == pkg }?.download

    private companion object {
        /** How long a direct check of one app stays fresh. */
        const val RECHECK_MS = 5 * 60 * 1000L

        /** How long to wait for the person to answer Android's install screen. */
        const val INSTALL_WAIT_MS = 10 * 60 * 1000L

        const val STORE_KEY = ":store"
        const val UPDATE_ALL_KEY = ":update-all"
    }
}
