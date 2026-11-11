package com.amandhakar.appstore

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSizingTest {
    @Test fun smallPictureIsNotShrunk() = assertEquals(1, ImageSizing.sampleSize(800, 600, 1280))

    @Test fun halvesWhileStillBigEnough() {
        assertEquals(2, ImageSizing.sampleSize(2560, 1440, 1280))
        assertEquals(4, ImageSizing.sampleSize(5120, 2880, 1280))
    }

    @Test fun neverGoesBelowTheWantedSize() = assertEquals(2, ImageSizing.sampleSize(3000, 2000, 1280))

    @Test fun usesTheLongerSide() = assertEquals(2, ImageSizing.sampleSize(1000, 2600, 1280))

    @Test fun badSizesDoNotShrink() {
        assertEquals(1, ImageSizing.sampleSize(0, 0, 1280))
        assertEquals(1, ImageSizing.sampleSize(-5, 100, 1280))
        assertEquals(1, ImageSizing.sampleSize(4000, 4000, 0))
    }
}
