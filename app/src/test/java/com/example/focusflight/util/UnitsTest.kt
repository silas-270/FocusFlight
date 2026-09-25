package com.example.focusflight.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {

    @Test
    fun `durations under an hour show minutes only`() {
        assertEquals("0m", formatDuration(0))
        assertEquals("45m", formatDuration(45))
    }

    @Test
    fun `whole hours drop the minutes`() {
        assertEquals("1h", formatDuration(60))
    }

    @Test
    fun `hours and minutes are both shown`() {
        assertEquals("1h 25m", formatDuration(85))
        assertEquals("134h 27m", formatDuration(8067))
    }

    @Test
    fun `negative input is treated as zero`() {
        assertEquals("0m", formatDuration(-5))
    }
}
