package com.example.focusflight.ui.viewmodel

import android.util.Log
import java.io.File

object CacheUtils {
    /**
     * Scans the cache directory for pre-rendered airport route maps
     * and prunes the oldest files if the limit is exceeded.
     */
    fun pruneMapCache(cacheDir: File, maxFiles: Int = 5) {
        try {
            val mapFiles = cacheDir.listFiles { file ->
                file.isFile && file.name.startsWith("hub_route_map_") && file.name.endsWith(".png")
            } ?: return

            if (mapFiles.size > maxFiles) {
                // Sort by lastModified in ascending order (oldest first)
                val sortedFiles = mapFiles.sortedBy { it.lastModified() }
                val filesToDeleteCount = mapFiles.size - maxFiles
                
                for (i in 0 until filesToDeleteCount) {
                    val fileToDelete = sortedFiles[i]
                    if (fileToDelete.exists()) {
                        val deleted = fileToDelete.delete()
                        Log.d("CacheUtils", "Pruned old route map cache file: ${fileToDelete.name} (success: $deleted)")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CacheUtils", "Error pruning map cache", e)
        }
    }
}
