package com.amandhakar.appstore

/** How much to shrink a big picture while decoding it, so a screenshot doesn't take 10 MB of memory. */
object ImageSizing {
    /**
     * The biggest power of two to divide both sides by while the longer side stays at least [maxSide]
     * pixels. 1 means "don't shrink".
     */
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        val longest = maxOf(width, height)
        if (longest <= 0 || maxSide <= 0) return 1
        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2
        return sample
    }
}
