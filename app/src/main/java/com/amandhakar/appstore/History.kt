package com.amandhakar.appstore

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A release as GitHub lists it, before choosing and tidying. */
data class RawRelease(
    val tag: String,
    val draft: Boolean,
    val prerelease: Boolean,
    /** ISO time such as "2026-11-10T20:00:00Z", or "" when unknown. */
    val publishedAt: String,
    val body: String,
)

/** One line of an app's version history. */
data class HistoryEntry(val version: String, val date: String, val notes: String, val prerelease: Boolean)

/** What the app page shows under "Version history". */
sealed interface HistoryState {
    data object Loading : HistoryState
    data class Loaded(val entries: List<HistoryEntry>) : HistoryState
    data class Failed(val message: String) : HistoryState
}

object History {
    /** "10 Nov 2026" from an ISO time, or "" when it can't be read. */
    fun formatDate(
        iso: String,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String = try {
        DateTimeFormatter.ofPattern("d MMM yyyy", locale).withZone(zone).format(Instant.parse(iso))
    } catch (e: java.time.format.DateTimeParseException) {
        ""
    }

    /**
     * The versions to list, newest version first: drafts and tags that don't start with [prefix] are
     * left out, and so are pre-releases unless [includePrereleases] (apps published only as
     * pre-releases need them). Notes are cleaned to plain text.
     */
    fun select(
        raw: List<RawRelease>,
        prefix: String,
        includePrereleases: Boolean,
        limit: Int,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): List<HistoryEntry> = raw
        .filter { !it.draft && it.tag.startsWith(prefix) && (includePrereleases || !it.prerelease) }
        .map {
            HistoryEntry(
                version = it.tag.removePrefix(prefix).removePrefix("v"),
                date = formatDate(it.publishedAt, zone, locale),
                notes = ReleaseNotes.clean(it.body),
                prerelease = it.prerelease,
            )
        }
        .sortedWith { a, b -> compareVersions(b.version, a.version) }
        .take(limit)
}
