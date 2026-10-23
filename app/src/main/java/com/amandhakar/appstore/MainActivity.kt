package com.amandhakar.appstore

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

@Composable
fun StoreTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
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

    // An app's page replaces the list while it is open; Back returns to the list.
    val open = state.openApp?.let { pkg -> state.items.firstOrNull { it.app.packageName == pkg } }
    if (open != null) {
        BackHandler(onBack = vm::closeApp)
        AppPage(open, state.appChecks[open.app.packageName], onBack = vm::closeApp, onInstall = { onInstall(open) })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Store") },
                actions = {
                    TextButton(onClick = vm::refresh, enabled = !state.loading) { Text("Refresh") }
                },
            )
        },
    ) { padding ->
        // The banners always render, whatever state the app list is in.
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!installAllowed) {
                item(key = "install-permission") {
                    InstallPermissionCard(onAllow = { askInstallPermission(context) })
                }
            }
            state.storeUpdate?.let { release ->
                item(key = "store-update") {
                    StoreUpdateBanner(release, state.storeProgress, onUpdate = {
                        if (canInstall(context)) vm.updateStore() else askInstallPermission(context)
                    })
                }
            }
            if (state.loading && state.items.isEmpty()) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            state.error?.let { error ->
                item(key = "error") { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            if (state.items.isEmpty() && !state.loading && state.error == null) {
                item(key = "empty") { Text("No apps in the store yet.") }
            }
            val (prerelease, stable) = state.items.partition { it.app.prerelease }
            items(stable, key = { "stable:" + it.app.packageName }) { item ->
                AppCard(item, onOpen = { vm.openApp(item) }, onInstall = { onInstall(item) })
            }
            if (prerelease.isNotEmpty()) {
                item(key = "prerelease-header") {
                    Column(Modifier.padding(top = 12.dp)) {
                        Text("Pre-release", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Test builds. They may be unfinished or break.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                items(prerelease, key = { "prerelease:" + it.app.packageName }) { item ->
                    AppCard(item, onOpen = { vm.openApp(item) }, onInstall = { onInstall(item) })
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

@Composable
fun StoreUpdateBanner(release: Release, progress: Float?, onUpdate: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Store update available", style = MaterialTheme.typography.titleMedium)
            Text(
                "Version ${release.version} (you have ${BuildConfig.VERSION_NAME})",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (release.notes.isNotBlank()) {
                Text(
                    release.notes,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            } else {
                Button(onClick = onUpdate) { Text("Update store") }
            }
        }
    }
}

/** One app in the list. Tapping it opens the app's page. */
@Composable
fun AppCard(item: AppItem, onOpen: () -> Unit, onInstall: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(item.app.name, style = MaterialTheme.typography.titleMedium)
            if (item.app.description.isNotBlank()) {
                Text(item.app.description, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(versionLine(item), style = MaterialTheme.typography.bodySmall)
            item.release?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text(
                    notes,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            AppActions(item, onInstall)
        }
    }
}

/** Install / Update / Open, or the download progress while installing. */
@Composable
fun AppActions(item: AppItem, onInstall: () -> Unit) {
    val context = LocalContext.current
    if (item.progress != null) {
        LinearProgressIndicator(progress = { item.progress ?: 0f }, modifier = Modifier.fillMaxWidth())
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val installed = item.installedVersion != null
        if (item.release != null && (!installed || item.updateAvailable)) {
            if (item.compatible) {
                Button(onClick = onInstall) { Text(if (installed) "Update" else "Install") }
            } else {
                Button(onClick = {}, enabled = false) {
                    Text("Needs Android ${Store.androidVersionName(item.app.minSdk)}+")
                }
            }
        }
        if (installed && item.app.packageName != context.packageName) {
            val launch = context.packageManager.getLaunchIntentForPackage(item.app.packageName)
            if (launch != null) {
                OutlinedButton(onClick = { context.startActivity(launch) }) { Text("Open") }
            }
        }
    }
}

/**
 * An app's page: everything about its latest release, and a direct GitHub check of just this
 * app (see StoreViewModel.openApp), so it can show a release the store list hasn't caught up with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPage(item: AppItem, check: AppCheck?, onBack: () -> Unit, onInstall: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.app.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (item.app.description.isNotBlank()) {
                Text(item.app.description, style = MaterialTheme.typography.bodyLarge)
            }
            Text(versionLine(item), style = MaterialTheme.typography.bodyMedium)
            check?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (it.checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(it.message, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (item.app.prerelease) {
                Text("Pre-release: a test build. It may be unfinished or break.", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            AppActions(item, onInstall)
            item.release?.let { release ->
                Spacer(Modifier.height(12.dp))
                Text("What's new in ${release.version}", style = MaterialTheme.typography.titleMedium)
                SelectionContainer {
                    Text(release.notes.ifBlank { "No release notes." }, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Package ${item.app.packageName}", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${item.app.repo}/releases")))
            }) { Text("All releases on GitHub") }
        }
    }
}

private fun versionLine(item: AppItem): String {
    val latest = item.release?.let { r ->
        val mb = if (r.apkSize > 0) " · %.1f MB".format(r.apkSize / 1_000_000.0) else ""
        "Latest ${r.version}$mb"
    } ?: "No APK release yet"
    val installed = item.installedVersion?.let { "Installed ${it.ifBlank { "?" }}" } ?: "Not installed"
    return "$latest · $installed"
}

private fun canInstall(context: android.content.Context): Boolean =
    context.packageManager.canRequestPackageInstalls()

private fun askInstallPermission(context: android.content.Context) {
    Toast.makeText(context, "Turn on \"Allow from this source\", then come back", Toast.LENGTH_LONG).show()
    context.startActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
    )
}
