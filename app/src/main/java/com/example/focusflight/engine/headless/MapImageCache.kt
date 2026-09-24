package com.example.focusflight.engine.headless

import android.util.Log
import java.io.File

object MapImageCache {
    private const val PREFIX = "hub_route_map_"

    /**
     * Bumped whenever the look of headless renders changes, so stale PNGs are never reused.
     * v2: globes are drawn from the bundled offline vector map instead of CARTO tiles.
     */
    const val RENDER_VERSION = 2

    /** Cache file name for the route map centred on [iata]. */
    fun fileNameFor(iata: String): String = "${PREFIX}v${RENDER_VERSION}_$iata.png"

    /**
     * IATA codes whose rendered map must survive pruning regardless of age.
     *
     * Purely a cache-retention hint - nothing reads this for truth, and clearing it only costs a
     * re-render. It exists because the home base is the one airport that is both guaranteed to be
     * needed again and guaranteed to go stale: the return-home teleport is on a 7-day cooldown, by
     * which point five other airports have almost certainly pushed it out of a five-file cache. So
     * the one render the pilot waits on after a 10-second animation was, in practice, always a
     * cold one.
     *
     * Set once at startup and again whenever the home base changes; see
     * `CesiumGameActivity.pinHomeBaseMap`.
     */
    @Volatile
    var pinnedIatas: Set<String> = emptySet()

    /**
     * Scans the cache directory for pre-rendered airport route maps
     * and prunes the oldest files if the limit is exceeded.
     *
     * [pinnedIatas] are excluded from both the count and the deletion candidates: they are kept
     * *in addition to* [maxFiles], not out of that budget, so pinning can never squeeze the
     * normal cache down to nothing.
     *
     * Renders from an older [RENDER_VERSION] are deleted outright first; they would never be
     * reused anyway.
     */
    fun pruneMapCache(cacheDir: File, maxFiles: Int = 5) {
        try {
            val currentPrefix = "${PREFIX}v${RENDER_VERSION}_"
            val allMapFiles = cacheDir.listFiles { file ->
                file.isFile && file.name.startsWith(PREFIX) && file.name.endsWith(".png")
            } ?: return

            val (currentFiles, staleFiles) = allMapFiles.partition { it.name.startsWith(currentPrefix) }
            for (stale in staleFiles) {
                val deleted = stale.delete()
                Log.d("MapImageCache", "Removed outdated route map render: ${stale.name} (success: $deleted)")
            }

            val pinnedFileNames = pinnedIatas.map(::fileNameFor).toSet()
            val mapFiles = currentFiles.filter { it.name !in pinnedFileNames }

            if (mapFiles.size > maxFiles) {
                // Sort by lastModified in ascending order (oldest first)
                val sortedFiles = mapFiles.sortedBy { it.lastModified() }
                val filesToDeleteCount = mapFiles.size - maxFiles
                
                for (i in 0 until filesToDeleteCount) {
                    val fileToDelete = sortedFiles[i]
                    if (fileToDelete.exists()) {
                        val deleted = fileToDelete.delete()
                        Log.d("MapImageCache", "Pruned old route map cache file: ${fileToDelete.name} (success: $deleted)")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MapImageCache", "Error pruning map cache", e)
        }
    }
}
