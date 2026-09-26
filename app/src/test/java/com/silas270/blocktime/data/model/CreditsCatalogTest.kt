package com.silas270.blocktime.data.model

import org.junit.Assert.assertTrue
import org.junit.Test

class CreditsCatalogTest {

    private val credits = CreditsCatalog.sections.flatMap { it.credits }

    @Test
    fun `every credit names its source and license`() {
        credits.forEach { credit ->
            assertTrue("blank title in $credit", credit.title.isNotBlank())
            assertTrue("blank detail for ${credit.title}", credit.detail.isNotBlank())
            assertTrue("blank license for ${credit.title}", credit.license.isNotBlank())
        }
    }

    @Test
    fun `every credit links an https source`() {
        credits.forEach { credit ->
            assertTrue("${credit.title} links ${credit.url}", credit.url.startsWith("https://"))
        }
    }

    @Test
    fun `every section has credits and titles are unique within it`() {
        CreditsCatalog.sections.forEach { section ->
            assertTrue("${section.title} is empty", section.credits.isNotEmpty())
            val titles = section.credits.map { it.title }
            assertTrue("duplicate title in ${section.title}", titles.size == titles.toSet().size)
        }
    }

    @Test
    fun `the on-map credits name the providers their licenses require`() {
        assertTrue(CreditsCatalog.CARTO_MAP_CREDIT.contains("CARTO"))
        assertTrue(CreditsCatalog.CARTO_MAP_CREDIT.contains("OpenStreetMap"))
        assertTrue(CreditsCatalog.ESRI_MAP_CREDIT.startsWith("Powered by Esri"))
    }
}
