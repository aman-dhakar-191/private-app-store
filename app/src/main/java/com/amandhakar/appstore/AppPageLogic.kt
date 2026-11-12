package com.amandhakar.appstore

/** The three facts in the strip on an app's page. Free of Android calls so it can be unit tested. */
object AppFacts {
    const val NONE = "—"

    /** The latest release's version; the installed one when there is no release; a dash when neither. */
    fun version(latest: String?, installed: String?): String =
        latest?.takeIf { it.isNotBlank() } ?: installed?.takeIf { it.isNotBlank() } ?: NONE

    /** The download size, or a dash when it isn't known. */
    fun size(bytes: Long?): String = bytes?.takeIf { it > 0 }?.let { DownloadText.size(it) } ?: NONE

    /** The lowest Android version the app runs on, or "Any" when it doesn't say. */
    fun android(minSdk: Int): String = if (minSdk > 0) "${Store.androidVersionName(minSdk)}+" else "Any"
}

/** The tag under an app's name on its page. */
enum class PageTag { UpdateAvailable, UpToDate, NeedsNewerAndroid, None }

object PageStatus {
    fun tag(kind: StatusText.Kind): PageTag = when (kind) {
        StatusText.Kind.UpdateAvailable -> PageTag.UpdateAvailable
        StatusText.Kind.UpToDate -> PageTag.UpToDate
        StatusText.Kind.NeedsNewerAndroid -> PageTag.NeedsNewerAndroid
        StatusText.Kind.NotInstalled, StatusText.Kind.NoRelease -> PageTag.None
    }

    /** The one line under the tag: what is installed and what is newest, or why there is nothing to install. */
    fun detail(installedVersion: String?, hasRelease: Boolean): String? = when {
        installedVersion != null -> "Installed ${installedVersion.ifBlank { "?" }}"
        !hasRelease -> "No APK release yet"
        else -> null
    }
}
