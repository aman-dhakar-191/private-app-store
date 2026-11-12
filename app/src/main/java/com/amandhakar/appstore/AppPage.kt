package com.amandhakar.appstore

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * An app's page: everything about its latest release, and a direct GitHub check of just this
 * app (see StoreViewModel.openApp), so it can show a release the store list hasn't caught up with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPage(
    item: AppItem,
    check: AppCheck?,
    history: HistoryState?,
    onLoadHistory: () -> Unit,
    onBack: () -> Unit,
    actions: DownloadActions,
) {
    val context = LocalContext.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(StoreIcons.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Hero(item)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PageAction(item, actions)
                check?.let {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (it.checking) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (item.app.prerelease) PreReleaseBanner(Modifier.padding(horizontal = 16.dp))
            FactsStrip(item, Modifier.padding(horizontal = 16.dp))
            if (item.app.description.isNotBlank()) {
                Text(
                    item.app.description,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            if (item.app.screenshots.isNotEmpty()) Screenshots(item.app)
            item.release?.let { release ->
                PageCard(Modifier.padding(horizontal = 16.dp)) {
                    Text("What's new in ${release.version}", style = MaterialTheme.typography.titleMedium)
                    SelectionContainer {
                        Text(
                            ReleaseNotes.clean(release.notes).ifBlank { "No release notes." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            VersionHistory(history, onLoadHistory, Modifier.padding(horizontal = 16.dp))
            TextButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${item.app.repo}/releases")))
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Icon(StoreIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("All releases on GitHub")
            }
        }
    }
}

/** A rounded surface with a padded column, the box behind each block of the page. */
@Composable
private fun PageCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/** The big centred icon, the name, the package, and a tag saying where the app stands. */
@Composable
private fun Hero(item: AppItem) {
    val kind = StatusText.kind(
        installed = item.installedVersion != null,
        hasRelease = item.release != null,
        updateAvailable = item.updateAvailable,
        compatible = item.compatible,
    )
    val detail = PageStatus.detail(item.installedVersion, item.release != null)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppIcon(item, 96.dp, placeholder = true)
        Spacer(Modifier.height(6.dp))
        Text(
            item.app.name,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
        )
        Text(
            item.app.packageName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (PageStatus.tag(kind)) {
                PageTag.UpdateAvailable -> Tag("Update available", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                PageTag.UpToDate -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(StoreIcons.CheckCircle, contentDescription = null, tint = storeColors.good, modifier = Modifier.size(16.dp))
                    Text("Up to date", style = MaterialTheme.typography.labelLarge, color = storeColors.good)
                }
                PageTag.NeedsNewerAndroid -> Tag(
                    "Needs Android ${Store.androidVersionName(item.app.minSdk)}+",
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                )
                PageTag.None -> Unit
            }
            if (item.app.prerelease) Tag("Pre-release", storeColors.warnBackground, storeColors.warnText)
        }
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Tag(text: String, background: Color, content: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = background) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** The one thing to do: Install, Update, Open or why not; while a download runs, where it is up to. */
@Composable
private fun PageAction(item: AppItem, actions: DownloadActions) {
    val context = LocalContext.current
    item.download?.let { download ->
        PageCard { DownloadStatus(download, actions) }
        return
    }
    val installed = item.installedVersion != null
    val release = item.release
    val big = Modifier.fillMaxWidth().height(52.dp)
    val launch = if (installed && item.app.packageName != context.packageName) {
        context.packageManager.getLaunchIntentForPackage(item.app.packageName)
    } else {
        null
    }
    when {
        release != null && installed && item.updateAvailable && item.compatible ->
            Button(onClick = actions.onStart, modifier = big) { Text("Update") }
        release != null && !installed && item.compatible ->
            Button(onClick = actions.onStart, modifier = big) { Text("Install") }
        release != null && !item.compatible && (!installed || item.updateAvailable) ->
            Button(onClick = {}, enabled = false, modifier = big) {
                Text("Needs Android ${Store.androidVersionName(item.app.minSdk)}+")
            }
        launch != null -> Button(onClick = { context.startActivity(launch) }, modifier = big) { Text("Open") }
    }
    // When an update is the main button, Open stays available as the quieter one under it.
    val updating = release != null && installed && item.updateAvailable && item.compatible
    if (updating && launch != null) {
        OutlinedButton(onClick = { context.startActivity(launch) }, modifier = big) { Text("Open") }
    }
}

/** Version, download size and the lowest Android version, side by side in one box. */
@Composable
private fun FactsStrip(item: AppItem, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.height(IntrinsicSize.Min).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Fact(Modifier.weight(1f), AppFacts.version(item.release?.version, item.installedVersion), "Version")
            VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
            Fact(Modifier.weight(1f), AppFacts.size(item.release?.apkSize), "Size")
            VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
            Fact(Modifier.weight(1f), AppFacts.android(item.app.minSdk), "Android")
        }
    }
}

@Composable
private fun Fact(modifier: Modifier, value: String, label: String) {
    Column(modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The amber note on a test build. */
@Composable
private fun PreReleaseBanner(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = storeColors.warnBackground) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(StoreIcons.Warning, contentDescription = null, tint = storeColors.warnText, modifier = Modifier.size(20.dp))
            Text(
                "Pre-release: a test build. It may be unfinished or break.",
                style = MaterialTheme.typography.bodyMedium,
                color = storeColors.warnText,
            )
        }
    }
}

/** The app's screenshots, side by side. One that can't be loaded is simply left out. */
@Composable
private fun Screenshots(app: CatalogApp) {
    val context = LocalContext.current.applicationContext
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
        items(app.screenshots) { path ->
            val bitmap by produceState<Bitmap?>(null, path) {
                val url = IconUrls.screenshot(BuildConfig.RAW_BASE_URL, path)
                value = if (url == null) null else RemoteImages.load(context, url, maxBytes = 5_000_000, maxSide = 1280)
            }
            bitmap?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = "Screenshot of ${app.name}",
                    modifier = Modifier.height(360.dp).clip(RoundedCornerShape(16.dp)),
                )
            }
        }
    }
}

/** "Version history" as a timeline: nothing is asked of GitHub until the person taps to show it. */
@Composable
private fun VersionHistory(history: HistoryState?, onLoad: () -> Unit, modifier: Modifier = Modifier) {
    when (history) {
        null -> OutlinedButton(onClick = onLoad, modifier = modifier.fillMaxWidth().height(48.dp)) {
            Icon(StoreIcons.History, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Show version history")
        }
        HistoryState.Loading -> Row(
            modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("Loading version history", style = MaterialTheme.typography.bodyMedium)
        }
        is HistoryState.Failed -> Column(modifier) {
            Text(history.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onLoad) { Text("Try again") }
        }
        is HistoryState.Loaded -> Column(modifier.padding(horizontal = 8.dp)) {
            Text("Version history", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            if (history.entries.isEmpty()) Text("No earlier versions.", style = MaterialTheme.typography.bodySmall)
            history.entries.forEachIndexed { index, entry ->
                TimelineEntry(entry, first = index == 0, last = index == history.entries.lastIndex)
            }
        }
    }
}

@Composable
private fun TimelineEntry(entry: HistoryEntry, first: Boolean, last: Boolean) {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.width(12.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.padding(top = 5.dp).size(12.dp).clip(CircleShape)
                    .background(if (first) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
            )
            if (!last) {
                Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
            }
        }
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.version, style = MaterialTheme.typography.titleSmall)
                if (entry.prerelease) PreReleaseTag()
                Spacer(Modifier.weight(1f))
                if (entry.date.isNotBlank()) {
                    Text(entry.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                entry.notes.ifBlank { "No release notes." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
