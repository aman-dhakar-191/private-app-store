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
import androidx.compose.foundation.Image
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
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

    // The About page replaces the list while it is open; Back returns to the list.
    var showAbout by rememberSaveable { mutableStateOf(false) }
    if (showAbout) {
        BackHandler { showAbout = false }
        AboutPage(
            check = state.storeCheck,
            update = state.storeUpdate,
            updateProgress = state.storeProgress,
            onCheck = vm::checkStoreUpdate,
            onUpdate = { if (canInstall(context)) vm.updateStore() else askInstallPermission(context) },
            onBack = { showAbout = false },
        )
        return
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
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.semantics { contentDescription = "More options" },
                        ) { Text("\u22EE", style = MaterialTheme.typography.titleLarge) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Check for update") },
                                onClick = {
                                    menuOpen = false
                                    showAbout = true
                                    vm.checkStoreUpdate()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("About") },
                                onClick = {
                                    menuOpen = false
                                    showAbout = true
                                },
                            )
                        }
                    }
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

/**
 * About the store: its version, a button that checks GitHub directly for a newer store (and says
 * what it found), and links. A newer store also shows the update button here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPage(
    check: StoreCheck?,
    update: Release?,
    updateProgress: Float?,
    onCheck: () -> Unit,
    onUpdate: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val icon = remember { AppIcons.installed(context, context.packageName, BuildConfig.VERSION_NAME) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Image(
                        bitmap = icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)),
                    )
                }
                Column {
                    Text("App Store", style = MaterialTheme.typography.headlineSmall)
                    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                "A small app store for friends. The apps, and their updates, come from GitHub Releases.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))

            if (update != null) {
                Text("Version ${update.version} is available", style = MaterialTheme.typography.titleMedium)
                if (updateProgress != null) {
                    LinearProgressIndicator(progress = { updateProgress }, modifier = Modifier.fillMaxWidth())
                } else {
                    Button(onClick = onUpdate) { Text("Update store") }
                }
            }
            if (check?.checking == true) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(check.message, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                OutlinedButton(onClick = onCheck) { Text("Check for update") }
                check?.let {
                    Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.STORE_REPO}/releases")))
            }) { Text("Store releases on GitHub") }
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.STORE_REPO}")))
            }) { Text("Source and app list on GitHub") }
            Text("Package ${context.packageName}", style = MaterialTheme.typography.bodySmall)
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

/** One app in the list: its icon, name, a short description and status. Tapping it opens the app's page. */
@Composable
fun AppCard(item: AppItem, onOpen: () -> Unit, onInstall: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Shows nothing (and takes no room) when the app has no icon.
                AppIcon(item, 56.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.app.description.isNotBlank()) {
                        Text(
                            item.app.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(statusLine(item), style = MaterialTheme.typography.labelMedium)
                }
            }
            whatsNew(item)?.let { notes ->
                Spacer(Modifier.height(8.dp))
                Text(
                    notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            AppActions(item, onInstall)
        }
    }
}

/**
 * The app's icon, rounded. An installed app shows its own icon; otherwise the icon is downloaded
 * from the store repo. With neither, nothing is drawn, which is how the list looked before icons.
 */
@Composable
fun AppIcon(item: AppItem, size: Dp, modifier: Modifier = Modifier) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(item, 72.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.app.name, style = MaterialTheme.typography.headlineSmall)
                    Text(statusLine(item), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        item.app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.app.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(item.app.description, style = MaterialTheme.typography.bodyLarge)
            }
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
                    Text(
                        ReleaseNotes.clean(release.notes).ifBlank { "No release notes." },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${item.app.repo}/releases")))
            }) { Text("All releases on GitHub") }
        }
    }
}

private fun statusLine(item: AppItem): String = StatusText.line(
    installedVersion = item.installedVersion,
    latestVersion = item.release?.version,
    latestSizeBytes = item.release?.apkSize ?: 0,
    updateAvailable = item.updateAvailable,
)

/** The latest release's notes as plain text, or null when there are none worth showing. */
private fun whatsNew(item: AppItem): String? =
    item.release?.notes?.let { ReleaseNotes.clean(it) }?.takeIf { it.isNotBlank() }

private fun canInstall(context: android.content.Context): Boolean =
    context.packageManager.canRequestPackageInstalls()

private fun askInstallPermission(context: android.content.Context) {
    Toast.makeText(context, "Turn on \"Allow from this source\", then come back", Toast.LENGTH_LONG).show()
    context.startActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
    )
}
