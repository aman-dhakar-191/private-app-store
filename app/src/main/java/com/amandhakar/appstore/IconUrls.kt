package com.amandhakar.appstore

/** Works out where an app's icon is downloaded from. Free of Android calls so it can be unit tested. */
object IconUrls {
    /**
     * A screenshot's address from the catalog's "screenshots" list: an `https://` address is used
     * as it is, another path is read from the store repo, and anything else (http://, file://, ...
     * or an empty entry) gives null, meaning "skip this one".
     */
    fun screenshot(rawBase: String, path: String): String? {
        val given = path.trim()
        return when {
            given.isEmpty() -> null
            given.startsWith("https://") -> given
            "://" in given -> null
            else -> rawBase + given.trimStart('/')
        }
    }

    /**
     * - an `https://` address in the catalog's "icon" field is used as it is;
     * - another path in "icon" is read from the store repo (relative to [rawBase]);
     * - nothing given means `icons/<package>.png` in the store repo.
     *
     * Any other kind of address (http://, file://, ...) is ignored in favour of the default, so
     * the catalog can't point the store at anything but a secure download.
     */
    fun resolve(rawBase: String, packageName: String, icon: String): String {
        val given = icon.trim()
        val default = "${rawBase}icons/$packageName.png"
        return when {
            given.isEmpty() -> default
            given.startsWith("https://") -> given
            "://" in given -> default
            else -> rawBase + given.trimStart('/')
        }
    }
}
