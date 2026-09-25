# State ownership

Every piece of state the app persists, who writes it, who reads it, and whether two
stores can disagree about it. **Any change that adds a persisted value, or a new writer
of an existing one, belongs in this file** — see [README.md](README.md)'s update routine.

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
| `logbook_sort_order` | `AccountViewModel.setSortOrder()` | `AccountViewModel` (seeds the Passport logbook's sort) | A `FlightSortOrder` name. Display preference only; an unknown value falls back to `DATE_DESC`. |
| ~~`home_airport_iata`~~ | **removed** | — | Moved to Room. This key is the bug this document exists for. |

## Room — `user_data.db`

Owns durable, derivable-from, user-meaningful data.

| Column / table | Writers | Readers | Notes |
|---|---|---|---|
| `user_profile.home_airport_iata` | `OnboardingViewModel.saveHomeAirport()`; `AccountViewModel.changeHomeBase()` | `domain.resolveHomeAirportIata()` only | **Sole owner.** Home counts as a visited country, so a wrong value here silently changes the map and Geographic achievements. |
| `user_profile.username` | `OnboardingViewModel` (generated); `AccountViewModel.updateUsername()` | Passport header | |
| `flight_log` | `InFlightViewModel` landing pipeline | Everything derived (below) | The only durable record that a flight happened. Written **before** `current_airport_iata` moves. |
| `challenges.*` | `LocalChallengeRepository` only | Hub, Challenges, landing pipeline | Genuinely mutated state, correctly so: challenge instances start fresh and are **not** derivable from history. One owning store, but several concurrent writers — see *Concurrent writers*. |
| `challenges.paused_flight` | `CesiumGameActivity` check-in; `InFlightViewModel` (through `SessionPausedFlightStore`) | Hub, Challenges, InFlight | Per-challenge slot, independent of both global slots. Written by a *scoped* statement, never a whole-row update — see *Concurrent writers*. |
| `challenges.streak_days` / `last_flown_day` | `LocalChallengeRepository.creditStreak` only | Never read raw — see *Facts vs. the clock* | Records the run **as of the last day flown**, not as of now. |
| `challenges.celebrated` | `LocalChallengeRepository.markCelebrated`, called by the Challenges screen's completion-presentation overlay the instant a challenge's fly-out animation finishes | Slot-display and completed-log queries (`ChallengeDao.getSlotDisplayFlow`, `getCelebratedCompletedOrderedByCompletedAt`, `countOccupyingSlots`) | Whether a COMPLETED challenge's completion has been *shown* to the player yet. Written by a *scoped* statement, never a whole-row update — same reason as `paused_flight` above. A COMPLETED row with this still `false` keeps occupying a slot (and the active-challenge cap) and is excluded from the log — see docs/challenges.md. |
| `achievement_unlocks` | `LocalAchievementsRepository` (lazy upsert, `IGNORE` on conflict) | Passport badge ordering | The **only** persisted achievement state, and only a timestamp. Unlock status itself stays derived. |

## Derived — never persisted

`VisitedGeography` · `AchievementBoard` · `FlightStats` · `FlightHighlights` · `List<Tour>`

All five are derived **once**, by `PilotProgressRepository`, and shared by the Passport, the
Challenges screen, the Hub and Flight Search. Each of those used to rebuild its own copy on
every visit.

`Tour` is a pure fold over the flight log — flights grouped into campaigns separated by more than
`TOUR_GAP_DAYS`. Note what is deliberately **not** on it: whether a tour is still running. That
answer changes with the clock rather than with the data, and this snapshot only recomputes when
Room writes, so a stored `isOpen` would keep claiming a tour was live for exactly as long as the
pilot did not fly. It is a function instead — `Tour.isOpenAt(now, zone)` — which is what keeps
this table's rule true of it. `Challenge.currentStreak(today)` exists for the same reason on the
persisted side: see *Facts vs. the clock* below.

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

## Concurrent writers

Ordering invariants above are about the sequence *within* one path. This is the other half: two
paths writing the same thing at the same time. The rule at the top of this document — one owning
store per value — says nothing about that, and the gap was a real bug.

**A whole-row `@Update` is a write of every column, not of the field you changed.** Two of them
racing is a lost update, never a merge: whichever writer read first and writes last silently
reverts the other's columns. The `challenges` row had three such writers — the landing pipeline
clearing the paused-flight slot, the landing pipeline crediting the Route leg, and
`InFlightViewModel`'s camera/elapsed-time saves — each doing read → `copy()` → write with nothing
between them.

`InFlightScreen`'s `ON_STOP` observer is what made that the *common* path rather than an unlucky
one. Inside a `NavHost` the lifecycle owner is the **back stack entry**, not the Activity, so
popping InFlight for the arrival celebration fires `ON_STOP` — and therefore `saveCameraState()` —
while the landing is still running, several hundred ms before `onCleared` cancels `viewModelScope`.
The symptom was a Route challenge that showed its completion celebration and was then found back at
its previous progress, with the just-landed flight still offered as resumable. The debug skip
button was not required: a natural timer landing takes the identical path.

Three things now hold, at three different layers:

| Layer | Mechanism | What it makes impossible |
|---|---|---|
| Schema | `ChallengeDao.updatePausedFlight` / `updateRouteProgress` | One concern's writer reverting another concern's columns |
| Repository | `LocalChallengeRepository.writeMutex` | Two read-modify-writes of the *same* concern interleaving; the `start*` cap check passing twice |
| Session | `SessionPausedFlightStore` | A landed flight's slot being written again — the clear seals it |

The mutex is a plain in-process lock, which is only sound because this repository is a singleton
and the sole writer of the table. It is **not reentrant**: every private helper below the public
entry points is deliberately lock-free.

`SessionPausedFlightStore` is worth reading for the shape rather than the fix. The seal is on
`clear()` rather than a separate `markLanded()` call, because "the landing has taken this slot" is
exactly the condition — coupling the two removes the ordering question instead of relocating it. A
write arriving *before* the clear is discarded by the clear; one arriving *after* is refused. And
it wraps per **session**, not per store: `PreferencesRepository`'s Story and Free slots are
process-wide singletons, so sealing one of those directly would refuse the *next* flight's writes
too.

The generalisation, and the thing to check on any new write path: **owning the store is not owning
the write.** Ask who else can be mid-`copy()` of this row right now, and whether the answer changes
depending on a screen transition.

## Test coverage

`PilotProgressRepositoryTest` pins the shared derivation's two invisible
behaviours: a failed derivation keeps the previous snapshot rather than blanking every screen,
and a username edit does not trigger a recompute.

`ChallengeRowConcurrentWriteTest` pins the *Concurrent writers* section above, in both failure
directions — a paused-flight write straddling the landing, and a landing carrying a stale slot —
plus the resurrect case and an uncontended control. Its fake DAO models Room's real write
granularity (`@Update` rewrites everything, the scoped updates do not), so it cannot pass against a
DAO that could not exist.

Not covered, and worth knowing: the new DAO SQL (`getDistinctDestinationsInMode`,
`updatePausedFlight`, `updateRouteProgress`) is only exercised by Room's generated code and would
need Robolectric or an instrumented test to verify directly. And `InFlightViewModel` itself has no
unit test at all — it loads the native Cesium engine on first touch — which is why the landing's
concurrency rule lives in `SessionPausedFlightStore`, a JVM-testable seam, rather than only in a
flag inside the ViewModel.

## Facts vs. the clock

A third category, distinct from both "owned" and "derived": state that is a correct record of the
past but not an answer about the present.

`challenges.streak_days` and `challenges.last_flown_day` are the case. Together they say "the run
was N days long as of this date". Whether that run is *still alive* depends on today's date, and
**nothing runs at midnight to notice when it dies** — a pilot who flew Monday and Tuesday and then
skipped Wednesday still has `streak_days = 2` in the database on Thursday. That is not a stale
write to repair; the row is accurate about what happened.

So the resolution happens on read, once, at the database boundary:
`LocalChallengeRepository` maps every active challenge through `Challenge.withStreakEvaluatedAt`,
which zeroes a run whose last day is older than yesterday. Everything downstream —
`progressFraction()`, the Hub card, the slot row, `resolveLandingOutcome` — is handed an
already-correct row and needs no clock of its own. The stored row catches up on the next credited
flight.

The rule this generalises to: **if answering a question needs the current time, the answer is not
state.** Store the facts, resolve them on read. [`Tour.isOpenAt`](achievements.md#tours) is the same rule applied on the
derived side, and `HomeBaseCooldown.isEligible(now, ...)` is the same rule again.

## Known sharp edges

- **The LHR rehome.** `FlightSearchViewModel.fetchRoutes()` rewrites `current_airport_iata`
  when a Story Mode origin has no outbound routes at all. It no longer fires on a *failed*
  query (that was the actual bug — a transient SQLite error silently teleported the pilot and
  persisted it), and Flight Search now shows a notice when it happens rather than moving the
  pilot in silence.
- **Migrations are real again, and required.** The destructive fallback that used to be here
  (`fallbackToDestructiveMigration(dropAllTables = true)`) silently deleted the entire logbook and
  every challenge on any schema bump. It is gone. `AppDatabase` now carries explicit `Migration`
  objects, `exportSchema = true`, and a committed `app/schemas/<db>/<version>.json` per version.
  The trade: a schema change shipped *without* a matching migration now crashes on launch instead
  of quietly wiping. That is the better failure — `MigrationTest` catches it before a device does,
  and a crash is fixable while deleted flights are not. **Every version bump from here needs a
  migration and its schema JSON.** Never delete an old version's JSON: a migration can only be
  tested against the schema it migrates *from*.
- **A blank home-airport field means "no home base".** Callers reach the value by two routes —
  `domain.resolveHomeAirportIata` (which normalises blank to null) and, in two places, a direct
  read of the profile field. `AirportRepository.getVisitedGeography` normalises blank itself so
  the two routes cannot disagree about what an empty field means.
- **`airportsVisited` is STORY-scoped; `totalFlights` and `totalMinutes` are not.** The first is
  a geography stat and must agree with the map next to it. The other two are activity stats — a
  Free Mode flight really was flown. This is a judgment call, not a derivation, and it is a
  one-line change if the product answer is different.
