package com.amandhakar.appstore

/** The GitHub pages the About screen links to. Free of Android calls so it can be unit tested. */
object AboutLinks {
    fun releases(repo: String): String = "https://github.com/$repo/releases"

    fun source(repo: String): String = "https://github.com/$repo"
}
