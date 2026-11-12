package com.amandhakar.appstore

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

/** The top of the home screen: the title, when the list was last checked, and the button to About. */
@Composable
fun HomeHeader(checkedText: String, onAbout: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text("App Store", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold))
            Text(checkedText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            IconButton(onClick = onAbout) { Icon(StoreIcons.Info, contentDescription = "About and settings") }
        }
    }
}

/** A group's title and count, and what sits at its right (Update all, or where it has got to). */
@Composable
fun SectionHeader(title: String, count: Int, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 4.dp).height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
        Spacer(Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** The rounded white (or dark) box that holds a group's rows. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column { content() }
    }
}

@Composable
fun RowDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

/** One app in the list. Tapping it opens the app's page; the right side holds the one thing to do next. */
@Composable
fun AppRow(item: AppItem, onOpen: () -> Unit, actions: DownloadActions) {
    val download = item.download
    val line = RowText.line(
        installedVersion = item.installedVersion,
        latestVersion = item.release?.version,
        latestSizeBytes = item.release?.apkSize ?: 0,
        updateAvailable = item.updateAvailable,
        compatible = item.compatible,
        androidName = Store.androidVersionName(item.app.minSdk),
        description = item.app.description,
        download = download,
    )
    val updating = item.installedVersion != null && item.updateAvailable && download == null
    RowFrame(
        icon = { AppIcon(item, 52.dp, placeholder = true) },
        name = item.app.name,
        prerelease = item.app.prerelease,
        line = line,
        note = if (updating) whatsNew(item) else null,
        download = download,
        onOpen = onOpen,
    ) {
        RowAction(item, actions)
    }
}

/** The store itself, shown among the updates when a newer version exists. */
@Composable
fun StoreRow(release: Release, download: DownloadState?, actions: DownloadActions, onOpen: () -> Unit) {
    val context = LocalContext.current
    val icon = remember { AppIcons.installed(context, context.packageName, BuildConfig.VERSION_NAME) }
    val size = if (release.apkSize > 0) " · ${DownloadText.size(release.apkSize)}" else ""
    val line = if (download != null) {
        RowLine(DownloadText.line(download), if (download is DownloadState.Failed) Tone.Error else Tone.Normal)
    } else {
        RowLine("${BuildConfig.VERSION_NAME} → ${release.version}$size")
    }
    RowFrame(
        icon = {
            if (icon != null) {
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)),
                )
            }
        },
        name = "App Store",
        prerelease = false,
        line = line,
        note = if (download == null) ReleaseNotes.clean(release.notes).lineSequence().firstOrNull { it.isNotBlank() } else null,
        download = download,
        onOpen = onOpen,
    ) {
        DownloadControls(download, actions) {
            Button(onClick = actions.onStart, modifier = Modifier.height(44.dp)) { Text("Update") }
        }
    }
}

/** The shared look of a row: icon, name (and the Pre-release tag), a line, an optional note and bar, and a control. */
@Composable
private fun RowFrame(
    icon: @Composable () -> Unit,
    name: String,
    prerelease: Boolean,
    line: RowLine,
    note: String?,
    download: DownloadState?,
    onOpen: () -> Unit,
    control: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Open $name", onClick = onOpen)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (prerelease) PreReleaseTag()
            }
            val color = when (line.tone) {
                Tone.Good -> storeColors.good
                Tone.Error -> MaterialTheme.colorScheme.error
                Tone.Normal -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (line.tone == Tone.Good) Icon(StoreIcons.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
                Text(
                    line.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    maxLines = if (line.tone == Tone.Error) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (download is DownloadState.Downloading || download is DownloadState.Installing) {
                val fraction = DownloadText.fraction(download)
                val bar = Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = bar) else LinearProgressIndicator(modifier = bar)
            }
        }
        control()
    }
}

/** The amber "Pre-release" tag next to a test build's name. */
@Composable
fun PreReleaseTag() {
    Surface(shape = RoundedCornerShape(10.dp), color = storeColors.warnBackground) {
        Text(
            "Pre-release",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = storeColors.warnText,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Cancel while a download can be stopped, Retry and Dismiss after a failure, nothing while Android installs; else [idle]. */
@Composable
private fun DownloadControls(download: DownloadState?, actions: DownloadActions, idle: @Composable () -> Unit) {
    when (download) {
        null -> idle()
        DownloadState.Queued, is DownloadState.Downloading ->
            IconButton(onClick = actions.onCancel) {
                Icon(StoreIcons.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        DownloadState.Installing -> Unit
        is DownloadState.Failed -> Column(horizontalAlignment = Alignment.End) {
            Button(onClick = actions.onStart, modifier = Modifier.height(44.dp)) { Text("Retry") }
            TextButton(onClick = actions.onDismiss) { Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** What goes on the right of an app's row: Update, Install, Open, or why it can't be installed. */
@Composable
private fun RowAction(item: AppItem, actions: DownloadActions) {
    val context = LocalContext.current
    DownloadControls(item.download, actions) {
        val installed = item.installedVersion != null
        val release = item.release
        when {
            release != null && installed && item.updateAvailable && item.compatible ->
                Button(onClick = actions.onStart, modifier = Modifier.height(44.dp)) { Text("Update") }
            release != null && !installed && item.compatible ->
                FilledTonalButton(onClick = actions.onStart, modifier = Modifier.height(44.dp)) { Text("Install") }
            release != null && !item.compatible && (!installed || item.updateAvailable) ->
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.outlineVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) { Text("Needs Android ${Store.androidVersionName(item.app.minSdk)}+", fontSize = 13.sp) }
            installed -> launchIntent(context, item)?.let { launch ->
                TextButton(onClick = { context.startActivity(launch) }) { Text("Open") }
            }
        }
    }
}

private fun launchIntent(context: Context, item: AppItem) =
    if (item.app.packageName == context.packageName) null else context.packageManager.getLaunchIntentForPackage(item.app.packageName)

/** The words and button that go with the Update all count: the button, or progress with Cancel all. */
@Composable
fun UpdateAllControl(count: Int, run: UpdateAllRun?, onUpdateAll: () -> Unit, onCancel: () -> Unit) {
    if (run == null) {
        FilledTonalButton(onClick = onUpdateAll, modifier = Modifier.height(44.dp)) { Text("Update all") }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Updating ${minOf(run.done + 1, run.total)} of ${run.total}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onCancel) { Text("Cancel all") }
        }
    }
}
