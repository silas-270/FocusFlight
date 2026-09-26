package com.silas270.blocktime.data.repository

/**
 * One destination photo and the credit Pexels requires alongside it ("Photo by X on Pexels",
 * linking the photo's own page - see https://www.pexels.com/api/documentation/#guidelines).
 * [photographer] is null when the response didn't name one; the credit then falls back to
 * crediting Pexels alone.
 */
data class DestinationPhoto(
    val imageUrl: String,
    val photographer: String?,
    val photoPageUrl: String?
)

interface DestinationPhotoRepository {
    /**
     * Looks up one portrait-oriented photo for the given destination via Pexels. Returns null
     * uniformly for "no result", "not found", or any failure (bad key, no network, timeout,
     * unexpected response shape) - callers never need their own try/catch, and a missing photo
     * always means "fall back to the flat arrival background", never a crash.
     */
    suspend fun fetchDestinationPhoto(municipality: String, isoCountry: String): DestinationPhoto?
}
