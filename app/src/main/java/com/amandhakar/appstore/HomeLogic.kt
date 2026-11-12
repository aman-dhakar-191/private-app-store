package com.amandhakar.appstore

/** One row at the top of the home list: an app, or the store itself when it has an update. */
sealed interface HomeRow {
    data class App(val item: AppItem) : HomeRow
    data class Store(val release: Release) : HomeRow
}

/** The three groups on the home screen. */
data class HomeSections(val updates: List<HomeRow>, val installed: List<AppItem>, val notInstalled: List<AppItem>)

/** Which group each app goes in. Free of Android calls so it can be unit tested. */
object HomeGrouping {
    /**
     * Updates: the store (first, when it has a newer version) and installed apps with an update this phone can run.
     * Installed: every other installed app. Not installed: the rest. Each group keeps the catalog order.
     */
    fun group(items: List<AppItem>, storeUpdate: Release?, compatible: (AppItem) -> Boolean = { it.compatible }): HomeSections {
        val updating = items.filter { it.installedVersion != null && it.updateAvailable && compatible(it) }
        val installed = items.filter { it.installedVersion != null && it !in updating }
        val rest = items.filter { it.installedVersion == null }
        val updates = listOfNotNull<HomeRow>(storeUpdate?.let { HomeRow.Store(it) }) + updating.map { HomeRow.App(it) }
        return HomeSections(updates, installed, rest)
    }
}

/** How a line of text under an app's name should look. */
enum class Tone { Normal, Good, Error }

data class RowLine(val text: String, val tone: Tone = Tone.Normal)

/** The second line of an app's row: what matters about it right now. Free of Android calls so it can be unit tested. */
object RowText {
    fun line(
        installedVersion: String?,
        latestVersion: String?,
        latestSizeBytes: Long,
        updateAvailable: Boolean,
        compatible: Boolean,
        androidName: String,
        description: String,
        download: DownloadState?,
    ): RowLine {
        if (download != null) {
            return RowLine(DownloadText.line(download), if (download is DownloadState.Failed) Tone.Error else Tone.Normal)
        }
        val needs = "Requires Android $androidName or newer"
        val installed = installedVersion?.ifBlank { "?" }
        val size = if (latestSizeBytes > 0) " · ${DownloadText.size(latestSizeBytes)}" else ""
        return when {
            installed != null && latestVersion != null && updateAvailable ->
                if (compatible) RowLine("$installed → $latestVersion$size") else RowLine(needs)
            installed != null && latestVersion != null -> RowLine("$installed · Up to date", Tone.Good)
            installed != null -> RowLine("Installed $installed · No APK release yet")
            latestVersion == null -> RowLine("No APK release yet")
            !compatible -> RowLine(needs)
            else -> RowLine(description.ifBlank { "Latest $latestVersion" })
        }
    }
}

/** "Checked 4 min ago". Free of Android calls so it can be unit tested. */
object TimeText {
    fun ago(nowMillis: Long, thenMillis: Long): String {
        if (thenMillis <= 0) return ""
        val seconds = (nowMillis - thenMillis) / 1000
        return when {
            seconds < 60 -> "Just now"
            seconds < 3600 -> "${seconds / 60} min ago"
            seconds < 86_400 -> "${seconds / 3600} h ago"
            else -> "${seconds / 86_400} d ago"
        }
    }

    fun checked(nowMillis: Long, thenMillis: Long): String {
        val ago = ago(nowMillis, thenMillis)
        return if (ago.isEmpty()) "Not checked yet" else "Checked ${ago.replaceFirstChar { it.lowercase() }}"
    }
}
