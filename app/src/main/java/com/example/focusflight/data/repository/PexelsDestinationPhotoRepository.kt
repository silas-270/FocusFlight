package com.example.focusflight.data.repository

import com.example.focusflight.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Gson shape for the subset of Pexels' /v1/search response this repository needs - see
 * https://www.pexels.com/api/documentation/#photos-search. Every other field Pexels returns is
 * simply ignored by Gson, not modeled here.
 */
private data class PexelsSearchResponse(val photos: List<PexelsPhoto>?)
private data class PexelsPhoto(
    val alt: String?,
    val src: PexelsPhotoSrc?
)
private data class PexelsPhotoSrc(
    val large2x: String?,
    val original: String?,
    val large: String?,
    val portrait: String?
)

/**
 * Fetches a high-resolution, iconic destination photo from Pexels, keyed on the destination's municipality + country.
 * Uses a prioritized query cascade (skyline -> city -> landmark) and matches against photo descriptions (alt) to
 * ensure characteristic city views (like iconic skylines and monuments) rather than generic stock snapshots.
 */
class PexelsDestinationPhotoRepository(
    private val apiKey: String = BuildConfig.PEXELS_API_KEY,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) : DestinationPhotoRepository {

    override suspend fun fetchDestinationPhotoUrl(municipality: String, isoCountry: String): String? {
        val trimmedMunicipality = municipality.trim()
        if (apiKey.isBlank() || trimmedMunicipality.isBlank()) return null
        return withContext(Dispatchers.IO) {
            // Bounds the whole cascade, not just each request - without this, a municipality
            // whose first few queries all come back empty can chain up to 5 * 8s of timeouts
            // (~40s+) before giving up, stalling the arrival screen's photo load.
            withTimeoutOrNull(TOTAL_FETCH_BUDGET_MS) {
                val queries = buildQueryCandidates(trimmedMunicipality, isoCountry.trim())
                for (query in queries) {
                    // runInterruptible so the timeout above can actually abort an in-flight
                    // OkHttp call instead of only skipping queries that hadn't started yet.
                    val photoUrl = kotlinx.coroutines.runInterruptible {
                        searchSingleQuery(query, trimmedMunicipality)
                    }
                    if (photoUrl != null) {
                        return@withTimeoutOrNull photoUrl
                    }
                }
                null
            }
        }
    }

    private companion object {
        const val TOTAL_FETCH_BUDGET_MS = 12_000L
    }

    internal fun buildQueryCandidates(municipality: String, isoCountry: String): List<String> {
        val trimmedMunicipality = municipality.trim()
        if (trimmedMunicipality.isBlank()) return emptyList()
        val trimmedCountry = isoCountry.trim()

        return listOfNotNull(
            "$trimmedMunicipality skyline",
            "$trimmedMunicipality City",
            "$trimmedMunicipality landmark",
            if (trimmedCountry.isNotBlank()) "$trimmedMunicipality $trimmedCountry" else null,
            trimmedMunicipality
        ).distinct()
    }

    internal fun buildSearchUrl(query: String): okhttp3.HttpUrl {
        return "https://api.pexels.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("per_page", "5")
            .build()
    }

    private fun searchSingleQuery(query: String, municipality: String): String? {
        return try {
            val url = buildSearchUrl(query)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", apiKey)
                .header("User-Agent", "FocusFlight/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                parseHighResPhotoUrl(body, municipality)
            }
        } catch (e: Exception) {
            // Network failure, timeout, malformed JSON, etc.
            null
        }
    }

    /** Pulled out of [fetchDestinationPhotoUrl] so it's unit-testable without a live/mocked
     *  network call - see PexelsDestinationPhotoRepositoryTest. */
    internal fun parseHighResPhotoUrl(responseBody: String, municipality: String): String? = try {
        val photos = gson.fromJson(responseBody, PexelsSearchResponse::class.java)?.photos ?: emptyList()
        if (photos.isEmpty()) null
        else {
            val mLower = municipality.lowercase()
            // 1. Prefer photo where description explicitly mentions the destination city
            val matched = photos.firstOrNull { (it.alt ?: "").lowercase().contains(mLower) } ?: photos.first()
            matched.src?.large2x?.takeIf { it.isNotBlank() }
                ?: matched.src?.original?.takeIf { it.isNotBlank() }
                ?: matched.src?.large?.takeIf { it.isNotBlank() }
                ?: matched.src?.portrait?.takeIf { it.isNotBlank() }
        }
    } catch (e: Exception) {
        null
    }
}
