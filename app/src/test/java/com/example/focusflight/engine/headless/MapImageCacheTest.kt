package com.example.focusflight.engine.headless

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MapImageCacheTest {

    private val dir: File = Files.createTempDirectory("map-cache").toFile()

    @After
    fun tearDown() {
        MapImageCache.pinnedIatas = emptySet()
        dir.deleteRecursively()
    }

    private fun touch(name: String, modified: Long = 0L) =
        File(dir, name).apply { writeText("png"); setLastModified(modified) }

    @Test
    fun `file names carry the render version`() {
        assertEquals("hub_route_map_v${MapImageCache.RENDER_VERSION}_ZRH.png", MapImageCache.fileNameFor("ZRH"))
    }

    @Test
    fun `renders from older versions are removed`() {
        val legacy = touch("hub_route_map_ZRH.png")
        val current = touch(MapImageCache.fileNameFor("ZRH"))
        val unrelated = touch("something_else.png")

        MapImageCache.pruneMapCache(dir)

        assertFalse(legacy.exists())
        assertTrue(current.exists())
        assertTrue(unrelated.exists())
    }

    @Test
    fun `oldest current renders are pruned past the limit, pinned ones kept`() {
        MapImageCache.pinnedIatas = setOf("AAA")
        val pinned = touch(MapImageCache.fileNameFor("AAA"), modified = 1_000L)
        val oldest = touch(MapImageCache.fileNameFor("BBB"), modified = 2_000L)
        val newer = touch(MapImageCache.fileNameFor("CCC"), modified = 3_000L)

        MapImageCache.pruneMapCache(dir, maxFiles = 1)

        assertTrue(pinned.exists())
        assertFalse(oldest.exists())
        assertTrue(newer.exists())
    }
}
