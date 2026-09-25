package com.silas270.blocktime.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PexelsDestinationPhotoRepositoryTest {

    private val repository = PexelsDestinationPhotoRepository(apiKey = "test-key")

    @Test
    fun `parseHighResPhotoUrl returns large2x url when available`() {
        val json = """
            {
                "photos": [
                    {
                        "alt": "Skyline of Lubango at sunset",
                        "src": {
                            "large2x": "https://images.pexels.com/photos/1/large2x.jpeg",
                            "original": "https://images.pexels.com/photos/1/original.jpeg"
                        }
                    }
                ],
                "total_results": 1
            }
        """.trimIndent()
        assertEquals(
            "https://images.pexels.com/photos/1/large2x.jpeg",
            repository.parseHighResPhotoUrl(json, "Lubango")
        )
    }

    @Test
    fun `parseHighResPhotoUrl prioritizes photo with matching city in alt description`() {
        val json = """
            {
                "photos": [
                    {
                        "alt": "Random African city in Rwanda",
                        "src": { "large2x": "https://images.pexels.com/photos/rwanda.jpeg" }
                    },
                    {
                        "alt": "Aerial view of Lubango city center",
                        "src": { "large2x": "https://images.pexels.com/photos/lubango.jpeg" }
                    }
                ],
                "total_results": 2
            }
        """.trimIndent()
        assertEquals(
            "https://images.pexels.com/photos/lubango.jpeg",
            repository.parseHighResPhotoUrl(json, "Lubango")
        )
    }

    @Test
    fun `parseHighResPhotoUrl returns null when photos array is empty`() {
        assertNull(repository.parseHighResPhotoUrl("""{"photos":[],"total_results":0}""", "Lubango"))
    }

    @Test
    fun `parseHighResPhotoUrl returns null on malformed json`() {
        assertNull(repository.parseHighResPhotoUrl("not json at all", "Lubango"))
    }

    @Test
    fun `buildQueryCandidates generates skyline, City, landmark, country, and municipality in order`() {
        val candidates = repository.buildQueryCandidates("Stuttgart", "DE")
        assertEquals(
            listOf("Stuttgart skyline", "Stuttgart City", "Stuttgart landmark", "Stuttgart DE", "Stuttgart"),
            candidates
        )
    }

    @Test
    fun `buildQueryCandidates omits country when isoCountry is blank`() {
        val candidates = repository.buildQueryCandidates("Paris", "  ")
        assertEquals(
            listOf("Paris skyline", "Paris City", "Paris landmark", "Paris"),
            candidates
        )
    }

    @Test
    fun `buildQueryCandidates returns empty list when municipality is blank`() {
        val candidates = repository.buildQueryCandidates("   ", "US")
        assertEquals(emptyList<String>(), candidates)
    }

    @Test
    fun `buildSearchUrl includes query and per_page=5`() {
        val url = repository.buildSearchUrl("New York skyline")
        assertEquals("New York skyline", url.queryParameter("query"))
        assertEquals("5", url.queryParameter("per_page"))
    }
}
