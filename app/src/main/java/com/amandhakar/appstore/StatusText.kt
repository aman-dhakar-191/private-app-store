package com.amandhakar.appstore

import java.util.Locale

/** The message after checking GitHub for a newer store. Free of Android calls so it can be unit tested. */
object StoreUpdateText {
    /** [latest] is the newest release's version, or null when GitHub has none; [time] is when the check ran. */
    fun result(current: String, latest: String?, time: String): String = when {
        latest == null -> "No release found on GitHub. Checked at $time."
        compareVersions(latest, current) > 0 -> "Version $latest is available (you have $current). Checked at $time."
        else -> "You have the latest version ($current). Checked at $time."
    }
}

/** The one-line status under an app's name. Free of Android calls so it can be unit tested. */
object StatusText {
    fun line(
        installedVersion: String?,
        latestVersion: String?,
        latestSizeBytes: Long,
        updateAvailable: Boolean,
    ): String {
        val size = if (latestSizeBytes > 0) String.format(Locale.ROOT, "%.1f MB", latestSizeBytes / 1_000_000.0) else ""
        val installed = installedVersion?.let { "Installed ${it.ifBlank { "?" }}" }
        return when {
            latestVersion == null -> listOfNotNull(installed, "No APK release yet").joinToString(" · ")
            installed == null -> listOf("Not installed", "Latest $latestVersion", size)
                .filter { it.isNotEmpty() }.joinToString(" · ")
            updateAvailable -> {
                val update = "Update to $latestVersion" + if (size.isNotEmpty()) " ($size)" else ""
                "$installed · $update"
            }
            else -> "$installed · Up to date"
        }
    }
}
