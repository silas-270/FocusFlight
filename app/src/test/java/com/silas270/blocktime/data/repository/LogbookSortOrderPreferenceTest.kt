package com.silas270.blocktime.data.repository

import com.silas270.blocktime.data.model.FlightSortOrder
import com.silas270.blocktime.testutil.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Passport logbook's persisted sort order (docs/state.md, `logbook_sort_order`). */
class LogbookSortOrderPreferenceTest {

    @Test
    fun `defaults to newest first when nothing is stored`() {
        val repo = PreferencesRepository(FakeSharedPreferences())
        assertEquals(FlightSortOrder.DATE_DESC, repo.getLogbookSortOrder())
    }

    @Test
    fun `round-trips every sort order`() {
        val repo = PreferencesRepository(FakeSharedPreferences())
        FlightSortOrder.entries.forEach { order ->
            repo.setLogbookSortOrder(order)
            assertEquals(order, repo.getLogbookSortOrder())
        }
    }

    @Test
    fun `an unknown stored name falls back to newest first`() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString("logbook_sort_order", "NOT_A_REAL_ORDER").apply()
        assertEquals(FlightSortOrder.DATE_DESC, PreferencesRepository(prefs).getLogbookSortOrder())
    }
}
