package com.amandhakar.appstore

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UpdateWorker.schedule(this)
        askNotificationPermission()
        setContent { StoreTheme { StoreScreen() } }
    }

    /** Needed on Android 13+ for the background update notifications. Android asks only once. */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(vm: StoreViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-checked on every resume, so the card disappears after coming back from Settings.
    var installAllowed by remember { mutableStateOf(canInstall(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.refreshInstalled()
        installAllowed = canInstall(context)
    }
    LaunchedEffect(Unit) {
        Installer.results.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            vm.refreshInstalled()
        }
    }

    val onInstall = { item: AppItem ->
        if (canInstall(context)) vm.install(item) else askInstallPermission(context)
    }

    // What the Install / Update, Cancel, Retry and Dismiss buttons of an app, and of the store, do.
    val actionsFor = { item: AppItem ->
        DownloadActions(
            onStart = { onInstall(item) },
            onCancel = { vm.cancelDownload(item) },
            onDismiss = { vm.dismissFailure(item) },
        )
    }
    val storeActions = DownloadActions(
        onStart = { if (canInstall(context)) vm.updateStore() else askInstallPermission(context) },
        onCancel = vm::cancelStoreDownload,
        onDismiss = vm::dismissStoreFailure,
    )

    // The About page replaces the list while it is open; Back returns to the list.
    var showAbout by rememberSaveable { mutableStateOf(false) }
    if (showAbout) {
        BackHandler { showAbout = false }
        AboutPage(
            check = state.storeCheck,
            update = state.storeUpdate,
            updateDownload = state.storeDownload,
            updateActions = storeActions,
            onCheck = vm::checkStoreUpdate,
            onBack = { showAbout = false },
        )
        return
    }

    // An app's page replaces the list while it is open; Back returns to the list.
    val open = state.openApp?.let { pkg -> state.items.firstOrNull { it.app.packageName == pkg } }
    if (open != null) {
        BackHandler(onBack = vm::closeApp)
        AppPage(
            open,
            state.appChecks[open.app.packageName],
            history = state.histories[open.app.packageName],
            onLoadHistory = { vm.loadHistory(open) },
            onBack = vm::closeApp,
            actions = actionsFor(open),
        )
        return
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        // Keeps "Checked 4 min ago" fresh while the screen is open.
        val now by produceState(System.currentTimeMillis()) {
            while (true) {
                delay(30_000)
                value = System.currentTimeMillis()
            }
        }
        val sections = HomeGrouping.group(state.items, state.storeUpdate)
        val pending = UpdateAll.pending(state.items)
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item(key = "header") {
                    HomeHeader(checkedText = TimeText.checked(now, state.checkedAtMillis), onAbout = { showAbout = true })
                }
                if (!installAllowed) {
                    item(key = "install-permission") {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            InstallPermissionCard(onAllow = { askInstallPermission(context) })
                        }
                    }
                }
                state.error?.let { error ->
                    item(key = "error") {
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                }
                if (state.items.isEmpty() && !state.loading && state.error == null) {
                    item(key = "empty") {
                        Text("No apps in the store yet.", modifier = Modifier.padding(24.dp))
                    }
                }
                if (sections.updates.isNotEmpty()) {
                    item(key = "updates-header") {
                        SectionHeader("Updates", sections.updates.size) {
                            if (state.updateAll != null || pending.size >= 2) {
                                UpdateAllControl(
                                    count = pending.size,
                                    run = state.updateAll,
                                    onUpdateAll = { if (canInstall(context)) vm.updateAll() else askInstallPermission(context) },
                                    onCancel = vm::cancelUpdateAll,
                                )
                            }
                        }
                    }
                    item(key = "updates") {
                        GroupCard {
                            sections.updates.forEachIndexed { index, row ->
                                if (index > 0) RowDivider()
                                when (row) {
                                    is HomeRow.App -> AppRow(row.item, onOpen = { vm.openApp(row.item) }, actions = actionsFor(row.item))
                                    is HomeRow.Store -> StoreRow(row.release, state.storeDownload, storeActions, onOpen = { showAbout = true })
                                }
                            }
                        }
                    }
                }
                if (sections.installed.isNotEmpty()) {
                    item(key = "installed-header") { SectionHeader("Installed", sections.installed.size) }
                    item(key = "installed") {
                        GroupCard {
                            sections.installed.forEachIndexed { index, item ->
                                if (index > 0) RowDivider()
                                AppRow(item, onOpen = { vm.openApp(item) }, actions = actionsFor(item))
                            }
                        }
                    }
                }
                if (sections.notInstalled.isNotEmpty()) {
                    item(key = "not-installed-header") { SectionHeader("Not installed", sections.notInstalled.size) }
                    item(key = "not-installed") {
                        GroupCard {
                            sections.notInstalled.forEachIndexed { index, item ->
                                if (index > 0) RowDivider()
                                AppRow(item, onOpen = { vm.openApp(item) }, actions = actionsFor(item))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InstallPermissionCard(onAllow: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Allow installing apps", style = MaterialTheme.typography.titleMedium)
            Text(
                "Android asks you once to let App Store install apps. Turn on " +
                    "\"Allow from this source\", then come back.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAllow) { Text("Allow") }
        }
    }
}

/**
 * The app's icon, rounded. An installed app shows its own icon; otherwise the icon is downloaded
 * from the store repo. With neither, nothing is drawn, which is how the list looked before icons.
 */
@Composable
fun AppIcon(item: AppItem, size: Dp, modifier: Modifier = Modifier, placeholder: Boolean = false) {
    val context = LocalContext.current
    val packageName = item.app.packageName
    val installed = remember(packageName, item.installedVersion) {
        if (item.installedVersion != null) AppIcons.installed(context, packageName, item.installedVersion) else null
    }
    val downloaded by produceState<Bitmap?>(initialValue = null, packageName, item.app.icon, installed == null) {
        if (installed == null) value = AppIcons.remote(context.applicationContext, item.app)
    }
    val bitmap = installed ?: downloaded
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.22f)),
        )
    } else if (placeholder) {
        // No icon yet (or none in the store repo): the app's first letter on a soft tile.
        Box(
            modifier.size(size).clip(RoundedCornerShape(size * 0.22f)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                item.app.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/**
 * Where a download and install are up to: the step in words, the amount downloaded, a bar, and the
 * button that fits (Cancel while it can still be stopped, Retry and Dismiss after a failure).
 */
@Composable
fun DownloadStatus(state: DownloadState, actions: DownloadActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (state is DownloadState.Failed) {
            Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions.onStart) { IconLabel(StoreIcons.Refresh, "Retry") }
                TextButton(onClick = actions.onDismiss) { Text("Dismiss") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(DownloadText.line(state), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                if (state is DownloadState.Downloading || state is DownloadState.Queued) {
                    TextButton(onClick = actions.onCancel) { Text("Cancel") }
                }
            }
            if (state !is DownloadState.Queued) {
                val bar = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                val fraction = DownloadText.fraction(state)
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = bar)
                } else {
                    LinearProgressIndicator(modifier = bar) // size unknown, or waiting on Android: keeps moving
                }
            }
        }
    }
}

/** What the buttons under a download do: start (or retry), cancel, and clear a failure message. */
class DownloadActions(val onStart: () -> Unit, val onCancel: () -> Unit, val onDismiss: () -> Unit)

/** A small icon followed by text, for buttons and headings. */
@Composable
private fun IconLabel(icon: ImageVector, text: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(text)
}

/** The latest release's notes as plain text, or null when there are none worth showing. */
internal fun whatsNew(item: AppItem): String? =
    item.release?.notes?.let { ReleaseNotes.clean(it) }?.takeIf { it.isNotBlank() }

private fun canInstall(context: android.content.Context): Boolean =
    context.packageManager.canRequestPackageInstalls()

private fun askInstallPermission(context: android.content.Context) {
    Toast.makeText(context, "Turn on \"Allow from this source\", then come back", Toast.LENGTH_LONG).show()
    context.startActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
    )
}
