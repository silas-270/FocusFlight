# State ownership

Every piece of state the app persists, who writes it, who reads it, and whether two
stores can disagree about it.

This exists because they used to. The home airport lived in `SharedPreferences` *and* the
Room `user_profile` row, written by three separate paths with no reconciliation, and read
by different features from different stores — so one failed write left the pilot
teleporting to one airport while their passport credited a different country, with nothing
able to detect or repair it. This table is the check that stops that recurring.

**The rule:** every value has exactly one owning store. A cache may be discarded and
rebuilt at any moment without changing what the user sees. If that is not true of
something, it is not a cache — it is a second source of truth, and it needs a much
higher bar than "it was convenient".

---

## SharedPreferences — `focus_flight_prefs`

Owns short-lived, fast-changing, per-device session state. Nothing derived lives here.

| Key | Writers | Readers | Notes |
|---|---|---|---|
| `onboarding_completed` | `OnboardingViewModel.saveHomeAirport()` | `CesiumGameActivity` start destination | Written **last**, only after the profile row exists. See below. |
| `current_airport_iata` | `OnboardingViewModel.saveHomeAirport()`; `InFlightViewModel.completeFlight()` (STORY only, and only if the logbook write succeeded); `AccountViewModel.returnHome()`; `FlightSearchViewModel.fetchRoutes()` (LHR rehome) | `domain.resolveCurrentAirportIata()` only | Four writers, one store. The LHR rehome is the surprising one — see *Known sharp edges*. |
| `last_return_home_at` | `AccountViewModel.returnHome()` | `AccountViewModel` | 7-day cooldown. Never conflated with the 30-day one. |
| `last_home_base_changed_at` | `OnboardingViewModel` (seeds 31 days in the past); `AccountViewModel.changeHomeBase()` | `AccountViewModel` | Written only after the Room home-airport write succeeds. |
| `focused_route_challenge_id` | `ChallengesViewModel`; `HubViewModel` (self-healing clear); `CesiumGameActivity` (clear on Route completion) | `HubViewModel`, `ChallengesViewModel` | Display-only pointer. Never mutates the challenge row. |
| `paused_flight` | `CesiumGameActivity` check-in; `InFlightViewModel` | `HubViewModel`, `InFlightViewModel` | STORY slot. |
| `paused_free_flight` | same | `ChallengesViewModel`, `InFlightViewModel` | FREE slot, deliberately separate so the two can coexist. |
| ~~`home_airport_iata`~~ | **removed** | — | Moved to Room. This key is the bug this document exists for. |

## Room — `user_data.db`

Owns durable, derivable-from, user-meaningful data.

| Column / table | Writers | Readers | Notes |
|---|---|---|---|
| `user_profile.home_airport_iata` | `OnboardingViewModel.saveHomeAirport()`; `AccountViewModel.changeHomeBase()` | `domain.resolveHomeAirportIata()` only | **Sole owner.** Home counts as a visited country, so a wrong value here silently changes the map and Geographic achievements. |
| `user_profile.username` | `OnboardingViewModel` (generated); `AccountViewModel.updateUsername()` | Passport header | |
| `flight_log` | `InFlightViewModel` landing pipeline | Everything derived (below) | The only durable record that a flight happened. Written **before** `current_airport_iata` moves. |
| `challenges.*` | `LocalChallengeRepository` only | Hub, Challenges, landing pipeline | Genuinely mutated state, correctly so: challenge instances start fresh and are **not** derivable from history. |
| `challenges.paused_flight` | `CesiumGameActivity` check-in; `InFlightViewModel` | Hub, Challenges, InFlight | Per-challenge slot, independent of both global slots. |
| `achievement_unlocks` | `LocalAchievementsRepository` (lazy upsert, `IGNORE` on conflict) | Passport badge ordering | The **only** persisted achievement state, and only a timestamp. Unlock status itself stays derived. |

## Derived — never persisted

`VisitedGeography` · `AchievementBoard` · `FlightStats` · `FlightHighlights`

All four are derived **once**, by `PilotProgressRepository`, and shared by the Passport, the
Challenges screen, the Hub and Flight Search. Each of those used to rebuild its own copy on
every visit.

Nothing here is written to disk. The chain is rooted in two Room `Flow`s — the profile row and
the flight log — so Room's own invalidation tracker re-emits on any write to either table. The
cached value's lifetime is therefore bound to the same mechanism that guarantees the underlying
data is correct: **it cannot go stale unless Room is wrong, and there is no second writer that
could disagree with it.** Discard it at any moment and the rebuild produces identical values,
which is the test for whether something is a cache at all.

A failed derivation *skips the emission* rather than publishing a partial result, so the last
good snapshot stays on screen and the next write retries automatically.

## Process-level caches

Discardable. Rebuilt on demand, never a source of truth.

| Cache | Scope | Why it is safe |
|---|---|---|
| `AirportRouteSqliteDataSource.connection` | Process | Read-only bundled asset; nothing writes it |
| `cachedContinentCountryMap` | Process | Immutable reference data. Populated **only on success** — a failure throws, and a throw cannot be memoised. That is why `AirportDataException` had to exist before this cache could |
| `airportByIataCache` (LRU 128) | Process | Airports are immutable within a build. Successful lookups only |
| `WorldMapParser.cachedMap` | Process | Parsed from a bundled SVG; warmed at app start |
| `MergedMapPathsCache` | Process | Pure function of its key; single entry |
| `PaperGrainTexture.brush` | Process | Deterministic, seeded |
| `hub_route_map_<IATA>.png` | Disk, 5 files | Pruned oldest-first. The home base is **pinned** (`MapImageCache.pinnedIatas`) and kept *in addition to* the five, because return-home is on a 7-day cooldown and would otherwise always find a cold cache |

---

## Ordering invariants

Write order that is load-bearing, not incidental:

1. **Landing** (`InFlightViewModel.completeFlight`): logbook → position → clear paused flight.
   Position and clear are conditional on the logbook write succeeding. A pilot standing at a
   destination with no flight explaining it is unrepairable; a pilot still at their origin with
   a resumable flight is not.
2. **Onboarding** (`OnboardingViewModel.saveHomeAirport`): profile row → current airport →
   cooldown seed → `onboarding_completed`. The completion flag is last so the only state that
   can survive a failure is "not yet onboarded", which is true and recoverable. Previously this
   was first, and a failed profile insert left the app permanently onboarded with no profile —
   which crashes the Challenges screen on open, forever, via `requireProfileId()`.
3. **Change home base** (`AccountViewModel.changeHomeBase`): Room write → cooldown stamp.
   Burning a 30-day cooldown for a change that did not happen is the worst of both outcomes.

## Test coverage

`PilotProgressRepositoryTest` pins the shared derivation's two invisible
behaviours: a failed derivation keeps the previous snapshot rather than blanking every screen,
and a username edit does not trigger a recompute.

Not covered, and worth knowing: the new DAO SQL (`getDistinctDestinationsInMode`) is only
exercised by Room's generated code and would need Robolectric or an instrumented test to verify
directly.

## Known sharp edges

- **The LHR rehome.** `FlightSearchViewModel.fetchRoutes()` rewrites `current_airport_iata`
  when a Story Mode origin has no outbound routes at all. It no longer fires on a *failed*
  query (that was the actual bug — a transient SQLite error silently teleported the pilot and
  persisted it), and Flight Search now shows a notice when it happens rather than moving the
  pilot in silence.
- **There are no data migrations any more.** All five Room migrations and both one-time
  preference migrators were deleted once they had run on the only device this app has ever been
  installed on. Room now uses `fallbackToDestructiveMigration(dropAllTables = true)`: a schema
  change wipes and rebuilds rather than crashing. **This must be revisited before the app reaches
  anyone else** — from the first real user onward it silently deletes their logbook on any schema
  bump.
- **A blank home-airport field means "no home base".** Callers reach the value by two routes —
  `domain.resolveHomeAirportIata` (which normalises blank to null) and, in two places, a direct
  read of the profile field. `AirportRepository.getVisitedGeography` normalises blank itself so
  the two routes cannot disagree about what an empty field means.
- **`airportsVisited` is STORY-scoped; `totalFlights` and `totalMinutes` are not.** The first is
  a geography stat and must agree with the map next to it. The other two are activity stats — a
  Free Mode flight really was flown. This is a judgment call, not a derivation, and it is a
  one-line change if the product answer is different.
