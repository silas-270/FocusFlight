package com.example.focusflight.data.repository

import com.example.focusflight.data.model.FlightSortOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class FlightSortOrderTest {

    @Test
    fun `every sort order has a non-blank display name`() {
        FlightSortOrder.entries.forEach { order ->
            assertEquals(true, order.displayName.isNotBlank())
        }
    }

    @Test
    fun `display names are unique so the dropdown never shows duplicates`() {
        val names = FlightSortOrder.entries.map { it.displayName }
        assertEquals(names.size, names.toSet().size)
    }
}
