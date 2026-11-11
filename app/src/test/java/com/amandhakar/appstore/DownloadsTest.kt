package com.amandhakar.appstore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadTextTest {
    private val dot = " · "

    @Test fun sizes() {
        assertEquals("0 B", DownloadText.size(0))
        assertEquals("999 B", DownloadText.size(999))
        assertEquals("1 KB", DownloadText.size(1_000))
        assertEquals("2 KB", DownloadText.size(1_500))
        assertEquals("850 KB", DownloadText.size(850_000))
        assertEquals("1.0 MB", DownloadText.size(999_600)) // would round to "1000 KB"
        assertEquals("7.4 MB", DownloadText.size(7_400_000))
    }

    @Test fun aBigFileShowsDoneOfTotalInMegabytesAndAPercentage() = assertEquals(
        "Downloading${dot}3.2 of 7.4 MB${dot}43%",
        DownloadText.line(DownloadState.Downloading(3_200_000, 7_400_000)),
    )

    @Test fun aSmallFileShowsEachSizeInItsOwnUnit() = assertEquals(
        "Downloading${dot}450 KB of 900 KB${dot}50%",
        DownloadText.line(DownloadState.Downloading(450_000, 900_000)),
    )

    @Test fun anUnknownTotalShowsOnlyWhatIsDone() =
        assertEquals("Downloading${dot}2.0 MB", DownloadText.line(DownloadState.Downloading(2_000_000, 0)))

    @Test fun nothingYetSaysStarting() =
        assertEquals("Starting download…", DownloadText.line(DownloadState.Downloading(0, 0)))

    @Test fun thePercentageNeverPassesOneHundred() =
        assertEquals("Downloading${dot}7.5 of 7.4 MB${dot}100%", DownloadText.line(DownloadState.Downloading(7_500_000, 7_400_000)))

    @Test fun otherStates() {
        assertEquals("Waiting for its turn…", DownloadText.line(DownloadState.Queued))
        assertEquals(
            "Installing… confirm on the Android screen if it asks",
            DownloadText.line(DownloadState.Installing),
        )
        assertEquals("No network", DownloadText.line(DownloadState.Failed("No network")))
    }

    @Test fun theBarFractionIsKnownOnlyWhileDownloadingAFileOfKnownSize() {
        assertEquals(0.5f, DownloadText.fraction(DownloadState.Downloading(50, 100))!!, 0.0001f)
        assertEquals(1f, DownloadText.fraction(DownloadState.Downloading(150, 100))!!, 0f)
        assertNull(DownloadText.fraction(DownloadState.Downloading(50, 0)))
        assertNull(DownloadText.fraction(DownloadState.Queued))
        assertNull(DownloadText.fraction(DownloadState.Installing))
        assertNull(DownloadText.fraction(DownloadState.Failed("x")))
    }
}

class UpdateAllTest {
    private fun release(version: String) = Release(tag = "v$version", version = version, notes = "", apkUrl = "u", apkSize = 1)

    private fun app(
        id: String,
        installed: String? = "1.0",
        latest: String? = "2.0",
        download: DownloadState? = null,
    ) = AppItem(
        app = CatalogApp(name = id, packageName = id, repo = "me/$id", description = ""),
        release = latest?.let { release(it) },
        installedVersion = installed,
        download = download,
    )

    private fun pending(vararg items: AppItem, compatible: (AppItem) -> Boolean = { true }) =
        UpdateAll.pending(items.toList(), compatible).map { it.app.packageName }

    @Test fun appsWithAnUpdateAreIncludedInListOrder() = assertEquals(listOf("b", "a"), pending(app("b"), app("c", latest = "1.0"), app("a")))

    @Test fun upToDateAppsAreLeftOut() = assertEquals(emptyList<String>(), pending(app("a", installed = "2.0"), app("b", installed = "3.0")))

    @Test fun appsThatAreNotInstalledAreLeftOut() = assertEquals(emptyList<String>(), pending(app("a", installed = null)))

    @Test fun appsWithNoReleaseAreLeftOut() = assertEquals(emptyList<String>(), pending(app("a", latest = null)))

    @Test fun appsThisPhoneCannotRunAreLeftOut() =
        assertEquals(listOf("b"), pending(app("a"), app("b"), compatible = { it.app.packageName == "b" }))

    @Test fun appsAlreadyBusyOrFailedAreLeftOut() = assertEquals(
        listOf("d"),
        pending(
            app("a", download = DownloadState.Downloading(1, 2)),
            app("b", download = DownloadState.Queued),
            app("c", download = DownloadState.Failed("x")),
            app("d"),
        ),
    )

    @Test fun summaryAndLabel() {
        assertEquals("1 update available", UpdateAll.summary(1))
        assertEquals("3 updates available", UpdateAll.summary(3))
        assertEquals("Update all (3)", UpdateAll.label(3))
    }
}
