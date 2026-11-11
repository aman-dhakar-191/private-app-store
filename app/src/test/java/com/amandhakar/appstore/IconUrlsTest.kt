package com.amandhakar.appstore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconUrlsTest {
    private val base = "https://raw.example/store/main/"
    private fun resolve(icon: String) = IconUrls.resolve(base, "com.x", icon)

    @Test fun noIconGivenUsesTheStoreReposIconsFolder() = assertEquals("${base}icons/com.x.png", resolve(""))

    @Test fun blankIsTheSameAsNoIcon() = assertEquals("${base}icons/com.x.png", resolve("   "))

    @Test fun anHttpsAddressIsUsedAsItIs() = assertEquals("https://a.example/b.png", resolve("https://a.example/b.png"))

    @Test fun spacesAroundAnAddressAreIgnored() = assertEquals("https://a.example/b.png", resolve("  https://a.example/b.png "))

    @Test fun aPathIsReadFromTheStoreRepo() {
        assertEquals("${base}art/x.png", resolve("art/x.png"))
        assertEquals("${base}art/x.png", resolve("/art/x.png"))
    }

    @Test fun addressesThatAreNotHttpsAreIgnored() {
        assertEquals("${base}icons/com.x.png", resolve("http://insecure.example/x.png"))
        assertEquals("${base}icons/com.x.png", resolve("file:///sdcard/x.png"))
        assertEquals("${base}icons/com.x.png", resolve("javascript://x"))
    }

    private fun shot(path: String) = IconUrls.screenshot(base, path)

    @Test fun screenshotPathIsReadFromTheStoreRepo() {
        assertEquals("${base}screenshots/a.png", shot("screenshots/a.png"))
        assertEquals("${base}screenshots/a.png", shot(" /screenshots/a.png "))
    }

    @Test fun screenshotHttpsAddressIsUsedAsItIs() = assertEquals("https://a.example/s.png", shot("https://a.example/s.png"))

    @Test fun screenshotsThatAreNotHttpsOrEmptyAreSkipped() {
        assertNull(shot(""))
        assertNull(shot("   "))
        assertNull(shot("http://a.example/s.png"))
        assertNull(shot("file:///sdcard/s.png"))
    }
}
