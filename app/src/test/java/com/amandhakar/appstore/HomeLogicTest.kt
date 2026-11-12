package com.amandhakar.appstore

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGroupingTest {
    private fun app(pkg: String) = CatalogApp(name = pkg, packageName = pkg, repo = "o/$pkg", description = "")
    private fun release(version: String) = Release(tag = "v$version", version = version, notes = "", apkUrl = "u", apkSize = 1)
    private fun item(pkg: String, installed: String?, latest: String?) =
        AppItem(app(pkg), latest?.let { release(it) }, installed)

    private val yes: (AppItem) -> Boolean = { true }
    private fun names(rows: List<HomeRow>) = rows.map { if (it is HomeRow.App) it.item.app.packageName else "store" }

    @Test fun anInstalledAppWithANewerReleaseIsInUpdates() {
        val g = HomeGrouping.group(listOf(item("a", "1.0", "1.1")), null, yes)
        assertEquals(listOf("a"), names(g.updates))
        assertEquals(emptyList<AppItem>(), g.installed)
    }

    @Test fun anUpToDateInstalledAppIsInInstalled() {
        val g = HomeGrouping.group(listOf(item("a", "1.1", "1.1")), null, yes)
        assertEquals(emptyList<String>(), names(g.updates))
        assertEquals(listOf("a"), g.installed.map { it.app.packageName })
    }

    @Test fun anAppThatIsNotInstalledIsInNotInstalled() {
        val g = HomeGrouping.group(listOf(item("a", null, "1.0"), item("b", null, null)), null, yes)
        assertEquals(listOf("a", "b"), g.notInstalled.map { it.app.packageName })
    }

    @Test fun anUpdateThePhoneCantRunStaysInInstalled() {
        val g = HomeGrouping.group(listOf(item("a", "1.0", "2.0")), null) { false }
        assertEquals(emptyList<String>(), names(g.updates))
        assertEquals(listOf("a"), g.installed.map { it.app.packageName })
    }

    @Test fun theStoreComesFirstInUpdates() {
        val g = HomeGrouping.group(listOf(item("a", "1.0", "1.1")), release("0.8.0"), yes)
        assertEquals(listOf("store", "a"), names(g.updates))
    }

    @Test fun noStoreUpdateMeansNoStoreRow() = assertEquals(emptyList<String>(), names(HomeGrouping.group(emptyList(), null, yes).updates))

    @Test fun eachGroupKeepsTheCatalogOrder() {
        val g = HomeGrouping.group(listOf(item("c", "1", "2"), item("a", "1", "2"), item("b", "1", "1")), null, yes)
        assertEquals(listOf("c", "a"), names(g.updates))
    }

    @Test fun anInstalledAppWithNoReleaseYetIsInInstalled() {
        val g = HomeGrouping.group(listOf(item("a", "1.0", null)), null, yes)
        assertEquals(listOf("a"), g.installed.map { it.app.packageName })
    }
}

class RowTextTest {
    private fun line(
        installed: String? = null,
        latest: String? = null,
        size: Long = 0,
        update: Boolean = false,
        ok: Boolean = true,
        description: String = "Does things.",
        download: DownloadState? = null,
    ) = RowText.line(installed, latest, size, update, ok, "15", description, download)

    @Test fun anUpdateShowsTheVersionChangeAndSize() =
        assertEquals(RowLine("0.11.0 → 0.12.0 · 15.7 MB"), line("0.11.0", "0.12.0", 15_668_161, update = true))

    @Test fun anUpdateWithoutASizeLeavesItOut() =
        assertEquals(RowLine("1.0 → 1.1"), line("1.0", "1.1", 0, update = true))

    @Test fun upToDateIsGood() = assertEquals(RowLine("2.1.1 · Up to date", Tone.Good), line("2.1.1", "2.1.1"))

    @Test fun notInstalledShowsTheDescription() = assertEquals(RowLine("Does things."), line(latest = "1.0"))

    @Test fun notInstalledWithNoDescriptionShowsTheLatestVersion() =
        assertEquals(RowLine("Latest 1.0"), line(latest = "1.0", description = " "))

    @Test fun noReleaseYet() {
        assertEquals(RowLine("No APK release yet"), line())
        assertEquals(RowLine("Installed 1.0 · No APK release yet"), line(installed = "1.0"))
    }

    @Test fun aPhoneThatIsTooOldIsToldWhy() {
        assertEquals(RowLine("Requires Android 15 or newer"), line(latest = "1.0", ok = false))
        assertEquals(RowLine("Requires Android 15 or newer"), line("1.0", "2.0", update = true, ok = false))
    }

    @Test fun aBlankInstalledVersionShowsAQuestionMark() = assertEquals(RowLine("? · Up to date", Tone.Good), line("", "1.0"))

    @Test fun aDownloadReplacesTheLine() {
        assertEquals(
            RowLine(DownloadText.line(DownloadState.Queued)),
            line("1.0", "2.0", update = true, download = DownloadState.Queued),
        )
    }

    @Test fun aFailedDownloadIsAnError() {
        val failed = DownloadState.Failed("The file didn't match.")
        assertEquals(RowLine("The file didn't match.", Tone.Error), line("1.0", "2.0", update = true, download = failed))
    }
}

class TimeTextTest {
    private val now = 1_000_000_000_000L
    private fun ago(seconds: Long) = TimeText.ago(now, now - seconds * 1000)

    @Test fun underAMinuteIsJustNow() {
        assertEquals("Just now", ago(0))
        assertEquals("Just now", ago(59))
    }

    @Test fun minutes() {
        assertEquals("1 min ago", ago(60))
        assertEquals("4 min ago", ago(4 * 60 + 30))
        assertEquals("59 min ago", ago(59 * 60 + 59))
    }

    @Test fun hoursAndDays() {
        assertEquals("1 h ago", ago(3600))
        assertEquals("23 h ago", ago(23 * 3600 + 5))
        assertEquals("1 d ago", ago(86_400))
        assertEquals("3 d ago", ago(3 * 86_400 + 100))
    }

    @Test fun aTimeInTheFutureIsJustNow() = assertEquals("Just now", TimeText.ago(now, now + 5000))

    @Test fun neverCheckedIsEmpty() {
        assertEquals("", TimeText.ago(now, 0))
        assertEquals("Not checked yet", TimeText.checked(now, 0))
    }

    @Test fun checkedReadsAsASentence() {
        assertEquals("Checked just now", TimeText.checked(now, now))
        assertEquals("Checked 4 min ago", TimeText.checked(now, now - 4 * 60_000))
    }
}
