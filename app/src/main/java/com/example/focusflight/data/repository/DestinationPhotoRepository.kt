package com.example.focusflight.data.repository

interface DestinationPhotoRepository {
    /**
     * Looks up one portrait-oriented photo URL for the given destination via Pexels. Returns null
     * uniformly for "no result", "not found", or any failure (bad key, no network, timeout,
     * unexpected response shape) - callers never need their own try/catch, and a missing photo
     * always means "fall back to the flat arrival background", never a crash.
     */
    suspend fun fetchDestinationPhotoUrl(municipality: String, isoCountry: String): String?
}
