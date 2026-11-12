package com.amandhakar.appstore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppFactsTest {
    @Test fun versionPrefersTheLatestRelease() = assertEquals("1.2.0", AppFacts.version("1.2.0", "1.0.0"))
    @Test fun versionFallsBackToTheInstalledOne() = assertEquals("1.0.0", AppFacts.version(null, "1.0.0"))
    @Test fun versionIgnoresBlanks() = assertEquals("1.0.0", AppFacts.version(" ", "1.0.0"))
    @Test fun versionIsADashWhenNothingIsKnown() = assertEquals(AppFacts.NONE, AppFacts.version(null, null))
    @Test fun versionIsADashWhenBothAreBlank() = assertEquals(AppFacts.NONE, AppFacts.version("", ""))

    @Test fun sizeIsADashWhenUnknown() {
        assertEquals(AppFacts.NONE, AppFacts.size(null))
        assertEquals(AppFacts.NONE, AppFacts.size(0))
        assertEquals(AppFacts.NONE, AppFacts.size(-5))
    }

    @Test fun sizeUsesTheDownloadFormat() = assertEquals(DownloadText.size(7_400_000), AppFacts.size(7_400_000))

    @Test fun androidIsAnyWithoutAMinimum() {
        assertEquals("Any", AppFacts.android(0))
        assertEquals("Any", AppFacts.android(-1))
    }

    @Test fun androidShowsTheMinimumWithAPlus() = assertEquals("${Store.androidVersionName(26)}+", AppFacts.android(26))
}

class PageStatusTest {
    @Test fun everyKindHasATag() {
        assertEquals(PageTag.UpdateAvailable, PageStatus.tag(StatusText.Kind.UpdateAvailable))
        assertEquals(PageTag.UpToDate, PageStatus.tag(StatusText.Kind.UpToDate))
        assertEquals(PageTag.NeedsNewerAndroid, PageStatus.tag(StatusText.Kind.NeedsNewerAndroid))
        assertEquals(PageTag.None, PageStatus.tag(StatusText.Kind.NotInstalled))
        assertEquals(PageTag.None, PageStatus.tag(StatusText.Kind.NoRelease))
    }

    @Test fun detailNamesTheInstalledVersion() = assertEquals("Installed 1.0.0", PageStatus.detail("1.0.0", true))
    @Test fun detailMarksABlankInstalledVersion() = assertEquals("Installed ?", PageStatus.detail("", true))
    @Test fun detailSaysWhenThereIsNoRelease() = assertEquals("No APK release yet", PageStatus.detail(null, false))
    @Test fun detailIsEmptyForANormalNotInstalledApp() = assertNull(PageStatus.detail(null, true))
}
