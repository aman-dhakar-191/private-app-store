package com.amandhakar.appstore

import org.junit.Assert.assertEquals
import org.junit.Test

class CompareVersionsTest {
    @Test fun glimpseReleaseTagAndInstalledVersionAreTheSameBuild() {
        assertEquals(0, compareVersions("0.57.0-build272", "0.57.0+272"))
    }

    @Test fun aLaterGlimpseBuildIsNewer() {
        assertEquals(1, compareVersions("0.57.0-build273", "0.57.0+272"))
    }

    @Test fun anEarlierGlimpseBuildIsOlder() {
        assertEquals(-1, compareVersions("0.57.0-build271", "0.57.0+272"))
    }

    @Test fun numericVersionSegmentsAreComparedNumerically() {
        assertEquals(1, compareVersions("1.10.0", "1.9.0"))
    }
}
