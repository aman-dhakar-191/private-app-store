package com.amandhakar.appstore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class ChecksumsTest {
    private val abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad" // SHA-256 of "abc"

    @Test fun hexOfBytes() = assertEquals("00ff10", Checksums.hex(byteArrayOf(0x00, 0xFF.toByte(), 0x10)))

    @Test fun hexOfARealDigest() =
        assertEquals(abc, Checksums.hex(MessageDigest.getInstance("SHA-256").digest("abc".toByteArray())))

    @Test fun normaliseAcceptsGitHubsFormat() = assertEquals(abc, Checksums.normalise("sha256:$abc"))

    @Test fun normaliseLowercasesAndTrims() = assertEquals(abc, Checksums.normalise("  SHA256:${abc.uppercase()} \n"))

    @Test fun normaliseAcceptsPlainHex() = assertEquals(abc, Checksums.normalise(abc))

    @Test fun normaliseRejectsAnythingElse() {
        assertEquals("", Checksums.normalise(null))
        assertEquals("", Checksums.normalise(""))
        assertEquals("", Checksums.normalise("sha256:abc"))
        assertEquals("", Checksums.normalise("md5:" + "a".repeat(32)))
        assertEquals("", Checksums.normalise("sha256:" + "g".repeat(64)))
        assertEquals("", Checksums.normalise("sha256:" + "a".repeat(65)))
    }

    @Test fun aMatchingFileIsAccepted() = assertTrue(Checksums.matches("sha256:$abc", abc))

    @Test fun matchingIgnoresCase() = assertTrue(Checksums.matches(abc, abc.uppercase()))

    @Test fun aDifferentFileIsRefused() = assertFalse(Checksums.matches("sha256:$abc", "0".repeat(64)))

    @Test fun anUnreadableExpectedValueNeverMatches() {
        assertFalse(Checksums.matches(null, abc))
        assertFalse(Checksums.matches("", abc))
        assertFalse(Checksums.matches("sha256:abc", "abc"))
    }

    @Test fun aSingleChangedByteChangesTheDigest() {
        val md = { data: String -> Checksums.hex(MessageDigest.getInstance("SHA-256").digest(data.toByteArray())) }
        assertFalse(Checksums.matches(md("release apk"), md("release apK")))
        assertTrue(Checksums.matches(md("release apk"), md("release apk")))
    }
}
