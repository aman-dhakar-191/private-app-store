package com.amandhakar.appstore

/**
 * Turns a GitHub release's text into something readable on a phone. Releases made by GitHub's
 * "generate release notes" come as markdown with headings, "by @user in <link>" credits and a
 * "Full Changelog" link; none of that reads well as plain text.
 */
object ReleaseNotes {
    private val heading = Regex("""^\s{0,3}#{1,6}\s+(.*?)\s*#*\s*$""")
    private val fullChangelog = Regex("""^\s*\*{0,2}\s*Full Changelog\s*\*{0,2}\s*:.*$""", RegexOption.IGNORE_CASE)
    private val bullet = Regex("""^\s*[*+-]\s+(.*)$""")
    private val credit = Regex("""\s+by\s+@[\w-]+(\[bot])?\s+in\s+https?://\S+\s*$""")
    private val link = Regex("""\[([^\]]*)]\([^)]*\)""")
    private val bold = Regex("""\*\*(.+?)\*\*|__(.+?)__""")
    private val italicStar = Regex("""(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])""")
    private val italicUnderscore = Regex("""(?<!\w)_(?!\s)(.+?)(?<!\s)_(?!\w)""")
    private val code = Regex("""`([^`]*)`""")
    private val blankRuns = Regex("""\n{3,}""")

    private class Section(val title: String?, val lines: MutableList<String> = mutableListOf())

    /** Plain text: no markdown, "•" bullets, and without the parts GitHub generates. Empty if nothing is left. */
    fun clean(raw: String): String {
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val sections = mutableListOf(Section(null))
        for (line in lines) {
            val h = heading.matchEntire(line)
            if (h != null) sections += Section(h.groupValues[1].trim()) else sections.last().lines += line
        }

        val parts = mutableListOf<String>()
        tidy(sections[0].lines).takeIf { it.isNotEmpty() }?.let { parts += it }
        val named = sections.drop(1)
        for (section in named.filter { !isGenerated(it.title) }) {
            val body = tidy(section.lines)
            val title = inline(section.title.orEmpty())
            parts += listOf(title, body).filter { it.isNotEmpty() }.joinToString("\n")
        }
        // GitHub's "What's Changed" list is the titles of the merged pull requests. It repeats what
        // the author wrote when they wrote something, so it is only shown when they wrote nothing.
        if (parts.isEmpty()) {
            named.firstOrNull { normalise(it.title) == "what's changed" }
                ?.let { tidy(it.lines) }?.takeIf { it.isNotEmpty() }?.let { parts += it }
        }
        return parts.filter { it.isNotEmpty() }.joinToString("\n\n")
    }

    private fun isGenerated(title: String?): Boolean = normalise(title) in setOf("what's changed", "new contributors")

    private fun normalise(title: String?): String = title.orEmpty().lowercase().replace('’', '\'').trim()

    private fun tidy(lines: List<String>): String =
        lines.filterNot { fullChangelog.matches(it) }
            .joinToString("\n") { line ->
                val b = bullet.matchEntire(line)
                if (b != null) "• " + inline(b.groupValues[1].replace(credit, "")) else inline(line.trimEnd())
            }
            .replace(blankRuns, "\n\n")
            .trim()

    private fun inline(text: String): String =
        text.replace(link) { it.groupValues[1] }
            .replace(code) { it.groupValues[1] }
            .replace(bold) { it.groupValues[1].ifEmpty { it.groupValues[2] } }
            .replace(italicStar) { it.groupValues[1] }
            .replace(italicUnderscore) { it.groupValues[1] }
}
