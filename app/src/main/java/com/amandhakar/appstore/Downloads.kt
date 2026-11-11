package com.amandhakar.appstore

import java.util.Locale
import kotlin.math.roundToInt

/** Where an install is up to. The progress component shows one of these. */
sealed interface DownloadState {
    /** Waiting its turn in "Update all". */
    data object Queued : DownloadState

    /** [bytesTotal] is 0 when the server didn't say how big the file is. */
    data class Downloading(val bytesDone: Long, val bytesTotal: Long) : DownloadState

    /** Handed to Android's installer; waiting for it to finish (it may be asking the user to confirm). */
    data object Installing : DownloadState

    data class Failed(val message: String) : DownloadState
}

/** Words and numbers for the progress component. Free of Android calls so it can be unit tested. */
object DownloadText {
    fun size(bytes: Long): String {
        if (bytes < 1_000) return "${bytes.coerceAtLeast(0)} B"
        val kb = (bytes / 1_000.0).roundToInt()
        if (kb < 1_000) return "$kb KB"
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
    }

    fun line(state: DownloadState): String = when (state) {
        DownloadState.Queued -> "Waiting for its turn\u2026"
        is DownloadState.Downloading -> when {
            state.bytesTotal > 0 -> "Downloading \u00b7 ${amount(state.bytesDone, state.bytesTotal)} \u00b7 ${percent(state)}%"
            state.bytesDone > 0 -> "Downloading \u00b7 ${size(state.bytesDone)}"
            else -> "Starting download\u2026"
        }
        DownloadState.Installing -> "Installing\u2026 confirm on the Android screen if it asks"
        is DownloadState.Failed -> state.message
    }

    /** 0..1 while the size is known, null otherwise (the bar then just keeps moving). */
    fun fraction(state: DownloadState): Float? =
        if (state is DownloadState.Downloading && state.bytesTotal > 0) {
            (state.bytesDone.toFloat() / state.bytesTotal).coerceIn(0f, 1f)
        } else {
            null
        }

    private fun percent(state: DownloadState.Downloading): Int =
        (state.bytesDone * 100 / state.bytesTotal).toInt().coerceIn(0, 100)

    /** "3.2 of 7.4 MB" for big files, "850 KB of 900 KB" for small ones. */
    private fun amount(done: Long, total: Long): String =
        if (total >= 1_000_000) {
            String.format(Locale.ROOT, "%.1f of %.1f MB", done / 1_000_000.0, total / 1_000_000.0)
        } else {
            "${size(done)} of ${size(total)}"
        }
}

/** "Update all": which apps it does, and what it says. */
object UpdateAll {
    /**
     * The apps with an update this phone can run that nothing else is busy with, in list order.
     * An app whose last try failed is left out: it has its own Retry button.
     */
    fun pending(items: List<AppItem>, compatible: (AppItem) -> Boolean = { it.compatible }): List<AppItem> =
        items.filter { it.updateAvailable && compatible(it) && it.download == null }

    fun summary(count: Int): String = if (count == 1) "1 update available" else "$count updates available"

    fun label(count: Int): String = "Update all ($count)"
}

/** How far "Update all" has got: [done] apps are finished and the next is being handled. */
data class UpdateAllRun(val done: Int, val total: Int)
