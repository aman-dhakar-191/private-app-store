package com.amandhakar.appstore

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusTextTest {
    private val dot = " · "

    @Test fun notInstalled() =
        assertEquals("Not installed${dot}Latest 1.4.0${dot}7.1 MB", StatusText.line(null, "1.4.0", 7_100_000, false))

    @Test fun notInstalledWithoutAKnownSize() =
        assertEquals("Not installed${dot}Latest 1.4.0", StatusText.line(null, "1.4.0", 0, false))

    @Test fun installedAndUpToDate() =
        assertEquals("Installed 1.3.0${dot}Up to date", StatusText.line("1.3.0", "1.3.0", 7_400_000, false))

    @Test fun updateAvailableShowsTheSize() =
        assertEquals("Installed 1.3.0${dot}Update to 1.4.0 (7.4 MB)", StatusText.line("1.3.0", "1.4.0", 7_400_000, true))

    @Test fun updateAvailableWithoutAKnownSize() =
        assertEquals("Installed 1.3.0${dot}Update to 1.4.0", StatusText.line("1.3.0", "1.4.0", 0, true))

    @Test fun noReleaseYet() = assertEquals("No APK release yet", StatusText.line(null, null, 0, false))

    @Test fun installedButNoReleaseYet() =
        assertEquals("Installed 1.0${dot}No APK release yet", StatusText.line("1.0", null, 0, false))

    @Test fun anInstalledVersionWithNoNameShowsAQuestionMark() =
        assertEquals("Installed ?${dot}Up to date", StatusText.line("", "1.0", 0, false))
}
