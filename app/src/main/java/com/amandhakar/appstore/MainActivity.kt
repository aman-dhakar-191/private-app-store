package com.amandhakar.appstore

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
        setContent { StoreTheme { StoreScreen() } }
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

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshInstalled() }
    LaunchedEffect(Unit) {
        Installer.results.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            vm.refreshInstalled()
        }
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.items.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null && state.items.isEmpty() ->
                    Text(
                        state.error!!,
                        Modifier.align(Alignment.Center).padding(24.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.error?.let { error ->
                        item { Text(error, color = MaterialTheme.colorScheme.error) }
                    }
                    items(state.items, key = { it.app.packageName }) { item ->
                        AppCard(item, onInstall = {
                            if (canInstall(context)) vm.install(item) else askInstallPermission(context)
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun AppCard(item: AppItem, onInstall: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
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

            if (item.progress != null) {
                LinearProgressIndicator(progress = { item.progress ?: 0f }, modifier = Modifier.fillMaxWidth())
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val installed = item.installedVersion != null
                if (item.release != null && (!installed || item.updateAvailable)) {
                    Button(onClick = onInstall) { Text(if (installed) "Update" else "Install") }
                }
                if (installed && item.app.packageName != context.packageName) {
                    val launch = context.packageManager.getLaunchIntentForPackage(item.app.packageName)
                    if (launch != null) {
                        OutlinedButton(onClick = { context.startActivity(launch) }) { Text("Open") }
                    }
                }
            }
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
    Toast.makeText(context, "Allow App Store to install apps, then tap Install again", Toast.LENGTH_LONG).show()
    context.startActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
    )
}
