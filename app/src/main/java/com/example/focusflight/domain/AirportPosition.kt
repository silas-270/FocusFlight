package com.example.focusflight.domain

import com.example.focusflight.data.repository.PreferencesRepository
import com.example.focusflight.data.repository.UserRepository

/**
 * The two "where is the pilot" questions, each answered from exactly one store.
 *
 * Before this existed the home airport lived in *both* `SharedPreferences` and the Room
 * `user_profile` row, written by three separate paths (onboarding, change-home-base, and the
 * legacy flight-log migrator's profile bootstrap) with no reconciliation between them. Reads were
 * split too: the return-home teleport read prefs to decide where to teleport, while the Passport
 * header and - more importantly - `AirportRepository.getVisitedGeography`'s "home counts as
 * visited" rule read Room. A single failed write left the two permanently disagreeing, with the
 * pilot teleporting to one airport while their passport credited a different country, and nothing
 * anywhere able to detect or repair it.
 *
 * Room is now the sole owner of the home airport, and `SharedPreferences` remains the sole owner
 * of the *current* airport - a fast-changing, per-session position with no reason to
 * live in the profile row. Neither value is duplicated any more, so these resolvers are the only
 * place that needs to know which store answers which question.
 */

/** The pilot's home base. Room's profile row is the only source; there is no fallback, because a
 *  missing profile means onboarding has not completed rather than a value to guess at. */
suspend fun resolveHomeAirportIata(userRepository: UserRepository): String? =
    userRepository.getProfile()?.homeAirportIata?.takeIf { it.isNotBlank() }

/**
 * Where the pilot currently is: the stored current airport, falling back to their home base.
 *
 * The fallback is defensive rather than routine - onboarding writes both - but it is why this has
 * to be a suspend function now that home lives in Room. Every caller was already inside a
 * coroutine, so this costs nothing at the call sites; it just makes the (previously silent,
 * synchronous, prefs-only) fallback honest about the fact that it reads the database.
 */
suspend fun resolveCurrentAirportIata(
    preferencesRepository: PreferencesRepository,
    userRepository: UserRepository
): String? = preferencesRepository.getCurrentAirport()
    ?: resolveHomeAirportIata(userRepository)
