package com.example.focusflight.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The checklist for "Master of Africa" is 54 rows and "World Traveler" is nearly 200, so the
 * difference between "DZ" and "Algeria" is the difference between a usable list and a puzzle.
 * These pin the two behaviours that matter: real codes resolve, and anything else degrades to the
 * code rather than throwing or rendering blank.
 */
class CountryNamesTest {

    @Test
    fun `resolves a known ISO code to its English name`() {
        assertEquals("Algeria", countryDisplayName("DZ"))
        assertEquals("Japan", countryDisplayName("JP"))
    }

    @Test
    fun `accepts lowercase and surrounding whitespace`() {
        assertEquals("Algeria", countryDisplayName("dz"))
        assertEquals("Algeria", countryDisplayName("  DZ "))
    }

    @Test
    fun `falls back to the raw code for a region ICU does not know`() {
        // A made-up code must not blow up a 54-row list.
        assertEquals("ZZ", countryDisplayName("ZZ"))
    }

    @Test
    fun `falls back for malformed input rather than throwing`() {
        // Locale.Builder rejects a non-2-letter region outright; the runCatching is what keeps
        // that from reaching the UI.
        assertEquals("", countryDisplayName(""))
        assertEquals("NOT-A-REGION", countryDisplayName("NOT-A-REGION"))
    }
}
