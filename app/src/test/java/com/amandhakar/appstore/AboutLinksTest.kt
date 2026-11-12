package com.amandhakar.appstore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutLinksTest {
    @Test fun releasesPointAtTheReleasesPage() = assertEquals("https://github.com/me/store/releases", AboutLinks.releases("me/store"))
    @Test fun sourcePointsAtTheRepository() = assertEquals("https://github.com/me/store", AboutLinks.source("me/store"))
    @Test fun linksAreAlwaysHttpsOnGitHub() {
        assertTrue(AboutLinks.releases("a/b").startsWith("https://github.com/"))
        assertTrue(AboutLinks.source("a/b").startsWith("https://github.com/"))
    }
}
