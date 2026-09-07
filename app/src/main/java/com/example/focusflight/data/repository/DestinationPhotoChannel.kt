package com.example.focusflight.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridges the prefetched Pexels destination photo URL across the InFlight -> ArrivalCelebration
 * navigation hop, the same way [LandingResultChannel] bridges the challenge-check result across
 * the same hop. A plain nav arg can't carry a photo URL - it contains `:`, `/`, `?`, `&`, which
 * would break `Screen.kt`'s un-encoded path-segment/query-param convention - and like the landing
 * result, this only ever needs to reach the very next screen within the same process, never
 * survive process death, never get cached (see PexelsDestinationPhotoRepository's doc for why no
 * caching layer exists here at all).
 *
 * Unlike [LandingResultChannel] there is no `Pending` sentinel: nothing ever awaits resolution
 * here. `ArrivalCelebrationScreen` takes a synchronous snapshot of [url] when composed and falls
 * back to the flat background immediately if it's still null - not-yet-resolved, no match, and a
 * failed fetch are all indistinguishable and all just mean "no photo".
 */
class DestinationPhotoChannel {
    private val _url = MutableStateFlow<String?>(null)
    val url: StateFlow<String?> = _url.asStateFlow()

    /** Call at the start of every new flight (`InFlightViewModel.init`), same as
     *  `LandingResultChannel.reset()`, so a stale photo from a previous flight's destination can
     *  never leak into this one's arrival screen. */
    fun reset() {
        _url.value = null
    }

    fun publish(url: String?) {
        _url.value = url
    }
}
