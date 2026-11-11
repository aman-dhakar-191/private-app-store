package com.amandhakar.appstore

/** SHA-256 helpers for checking a downloaded APK. Free of Android calls so it can be unit tested. */
object Checksums {
    /**
     * "sha256:ABC..." (as GitHub writes it) or plain hex, as 64 lowercase hex digits. Anything else
     * (missing, another algorithm, wrong length, not hex) gives "", meaning "can't be checked".
     */
    fun normalise(value: String?): String {
        val v = value.orEmpty().trim().lowercase().removePrefix("sha256:")
        return if (v.length == 64 && v.all { it in '0'..'9' || it in 'a'..'f' }) v else ""
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /** Whether a file whose SHA-256 is [actualHex] is the one [expected] describes. False if [expected] can't be read. */
    fun matches(expected: String?, actualHex: String): Boolean {
        val want = normalise(expected)
        return want.isNotEmpty() && want == actualHex.lowercase()
    }
}
