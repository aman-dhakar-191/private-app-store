package com.amandhakar.appstore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

class HistoryTest {
    private val utc = ZoneOffset.UTC
    private val en = Locale.ENGLISH

    private fun raw(tag: String, draft: Boolean = false, pre: Boolean = false, at: String = "2026-11-10T20:00:00Z", body: String = "Notes") =
        RawRelease(tag, draft, pre, at, body)

    private fun select(list: List<RawRelease>, prefix: String = "v", pre: Boolean = false, limit: Int = 10) =
        History.select(list, prefix, pre, limit, utc, en)

    @Test fun formatsADate() = assertEquals("10 Nov 2026", History.formatDate("2026-11-10T20:00:00Z", utc, en))

    @Test fun anUnreadableDateIsEmpty() {
        assertEquals("", History.formatDate("", utc, en))
        assertEquals("", History.formatDate("yesterday", utc, en))
    }

    @Test fun newestVersionFirstEvenWhenListedOtherwise() {
        val out = select(listOf(raw("v0.9.0"), raw("v0.10.0"), raw("v0.2.0")))
        assertEquals(listOf("0.10.0", "0.9.0", "0.2.0"), out.map { it.version })
    }

    @Test fun draftsAreLeftOut() = assertEquals(listOf("1.0.0"), select(listOf(raw("v1.0.0"), raw("v2.0.0", draft = true))).map { it.version })

    @Test fun prereleasesOnlyWhenAsked() {
        val list = listOf(raw("v1.0.0"), raw("v1.1.0", pre = true))
        assertEquals(listOf("1.0.0"), select(list).map { it.version })
        assertEquals(listOf("1.1.0", "1.0.0"), select(list, pre = true).map { it.version })
    }

    @Test fun onlyTagsWithThePrefixAndItIsRemoved() {
        val list = listOf(raw("app-v1.2.0"), raw("other-v9.0.0"), raw("v3.0.0"))
        assertEquals(listOf("1.2.0"), select(list, prefix = "app-").map { it.version })
    }

    @Test fun stopsAtTheLimit() = assertEquals(2, select(listOf(raw("v1.0.0"), raw("v1.1.0"), raw("v1.2.0")), limit = 2).size)

    @Test fun notesAreCleanedAndDatesFormatted() {
        val entry = select(listOf(raw("v1.0.0", body = "## What's new\n- Fast")))[0]
        assertEquals("10 Nov 2026", entry.date)
        assertEquals(ReleaseNotes.clean("## What's new\n- Fast"), entry.notes)
    }

    @Test fun noReleasesGivesAnEmptyList() = assertEquals(emptyList<HistoryEntry>(), select(emptyList()))

    @Test fun marksPrereleases() = assertEquals(true, select(listOf(raw("v1.0.0", pre = true)), pre = true)[0].prerelease)
}
