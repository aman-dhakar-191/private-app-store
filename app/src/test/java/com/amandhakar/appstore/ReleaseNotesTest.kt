package com.amandhakar.appstore

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {
    private fun clean(raw: String) = ReleaseNotes.clean(raw)

    @Test fun anAuthorsTextIsKeptAndGitHubsGeneratedPartIsDropped() {
        val raw = "Fixes lag when dragging a text selection quickly: the selection keeps up.\n\n" +
            "## What's Changed\n" +
            "* Fix lag when dragging a text selection quickly by @aman-dhakar-191 in https://github.com/o/r/pull/7\n\n\n" +
            "**Full Changelog**: https://github.com/o/r/compare/v0.4.1...v0.4.2"
        assertEquals("Fixes lag when dragging a text selection quickly: the selection keeps up.", clean(raw))
    }

    @Test fun whenTheAuthorWroteNothingTheChangeListIsShownWithoutCreditsOrLinks() {
        val raw = "## What's Changed\n" +
            "* Backup & recovery: allow screenshots in the GitHub build by @aman-dhakar-191 in https://github.com/o/r/pull/12\n\n" +
            "**Full Changelog**: https://github.com/o/r/compare/v2.1.0...v2.1.1"
        assertEquals("• Backup & recovery: allow screenshots in the GitHub build", clean(raw))
    }

    @Test fun theNewContributorsSectionIsDropped() {
        val raw = "## What's Changed\n* One by @a in https://x/pull/1\n\n" +
            "## New Contributors\n* @a made their first contribution in https://x/pull/1\n\n" +
            "**Full Changelog**: https://x"
        assertEquals("• One", clean(raw))
    }

    @Test fun creditsFromBotsAreRemovedToo() =
        assertEquals("• Bump x", clean("* Bump x by @dependabot[bot] in https://github.com/o/r/pull/3"))

    @Test fun markdownMarkersAreRemoved() {
        val raw = "Some **bold**, __also bold__, *italic*, _also italic_, `code` and a [link](https://example.com)."
        assertEquals("Some bold, also bold, italic, also italic, code and a link.", clean(raw))
    }

    @Test fun otherBulletStylesBecomeDots() = assertEquals("• a\n• b\n• c", clean("- a\n+ b\n* c"))

    @Test fun anAuthorsOwnHeadingsAreKeptAsPlainLines() {
        val raw = "Intro line.\n\n## Fixes\n* one\n* two\n\n## What's Changed\n* a by @x in https://y/pull/1"
        assertEquals("Intro line.\n\nFixes\n• one\n• two", clean(raw))
    }

    @Test fun plainTextIsLeftAlone() {
        val raw = "Mo 2.0, part 1: Mo knows when to hush (quiet at night, quiet for an hour)."
        assertEquals(raw, clean(raw))
    }

    @Test fun underscoresInsideWordsAndStarsInMathAreNotTreatedAsMarkdown() {
        assertEquals("Uses snake_case_names here", clean("Uses snake_case_names here"))
        assertEquals("2 * 3 * 4 = 24", clean("2 * 3 * 4 = 24"))
    }

    @Test fun aLoneChangelogLinkIsRemoved() = assertEquals("Text", clean("Text\n\n**Full Changelog**: https://x"))

    @Test fun windowsLineEndingsWork() = assertEquals("Line one\n\nLine two", clean("Line one\r\n\r\nLine two"))

    @Test fun extraBlankLinesAreCollapsed() = assertEquals("a\n\nb", clean("a\n\n\n\n\nb"))

    @Test fun nothingInNothingOut() {
        assertEquals("", clean(""))
        assertEquals("", clean("   \n\n  "))
        assertEquals("", clean("## What's Changed"))
        assertEquals("", clean("**Full Changelog**: https://x"))
    }

    @Test fun aCurlyApostropheInTheHeadingIsRecognised() =
        assertEquals("• One", clean("## What’s Changed\n* One by @a in https://x/pull/1"))
}
