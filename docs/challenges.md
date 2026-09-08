# Challenges

Long-running, quest-log style goals that run alongside normal play. A challenge is an
*instance* with its own state, not a query over history — that is the core distinction
from [achievements.md](achievements.md), and it is what makes "start fresh" meaningful.

## The four types

| Type | Metric | Position pointer | Source |
|---|---|---|---|
| `ROUTE` | Progress toward a destination | ✅ its own, isolated | curated or custom |
| `SET_COMPLETION` | Members reached / total members | — | curated only |
| `DISTANCE` | Cumulative km flown while active / target | — | curated or custom |
| `STREAK` | Consecutive local calendar days with a flight / target | — | curated or custom |

**Set-completion is curated-only** because a set has to be authored to mean anything.
A player-defined "visit these five airports" is just a checklist; the curated
definitions in `ChallengeSetDefinition` are the authoring seam.

**Streak is deliberately short and unforgiving** — targets of 3–5 days, not 30. It
covers the final push before an exam, where breaking on a miss is the point. It can
afford that fragility precisely because [Tours](achievements.md#tours) cover the opposite
horizon: tours are weeks long and never break. A long *unforgiving* streak would be the
worst of both.

## Where it comes from

Curated challenges are templates in `CuratedChallengeCatalog` — name, description, type
and definition, but no instance state. `startCuratedChallenge` assigns an id, a position
pointer where relevant, and zeroed progress when it instantiates a `challenges` row from
one. Custom challenges are created directly by the player through
`startCustomRouteChallenge`, `startCustomDistanceChallenge` and
`startCustomStreakChallenge`.

### The predefined-route submode

A `ROUTE` challenge is normally free-form: an origin, a destination, and whatever path
the pilot finds. The predefined submode instead references an authored itinerary in
`PredefinedRouteCatalog` via `predefined_route_id`, and tracks `leg_index` through it.

**It exists because the free-form shape cannot express a circuit.** With
`start == end`, the straight-line proxy's `originToDest <= 0.0` guard reports `1f` before
a single leg is flown — and even without the guard, a pilot would satisfy "around the
world" by never leaving the origin. Scoring by distance along a known itinerary, with
completion as a leg count rather than a coordinate comparison, is what makes
`waypoints.first() == waypoints.last()` a legitimate route.

Progress is **distance-weighted, not leg-counted**, because legs are not interchangeable:
on `AROUND_THE_WORLD`, Hong Kong→Tokyo is 2,962 km and Tokyo→Los Angeles is 8,772 km.
Counting legs would pay both the same 12.5%, making the hardest leg feel like the
cheapest. Leg distances are authored alongside the waypoints so `progressAt` stays a pure
function — the slot ring, the Hub card and the info modal all call it while composing, and
none can reach the airports database from there. An empty distance list is legal and means
"score by leg count".

Predefined routes are **curated-only**; `startCustomRouteChallenge` keeps producing
free-form ones.

The two are scored differently, and this matters:

- Free-form uses the straight-line proxy below.
- Predefined uses **kilometres flown along its own itinerary** (`PredefinedRoute.progressAt`),
  which is the whole point of the submode.

`progressFraction()` derives a predefined route's progress live from the catalog rather
than trusting the cached column, so editing an itinerary can never leave a live challenge
reporting against legs that no longer exist.

The submode also changes the UI path: a predefined challenge's next hop is authored, so
`resolveNextLeg` books it directly and drops the pilot on the boarding card, skipping a
destination picker that would have exactly one correct answer. See
[navigation.md](navigation.md)'s `continueRouteChallenge`.

## Which flights count

The rule per type, applied in `processLandingForChallenges` at
[core-loop.md](core-loop.md)'s post-landing pipeline step 4:

| | Advances a `ROUTE` pointer | Credits Distance / Set / Streak |
|---|---|---|
| `FREE` flight | ❌ | ❌ (returns before anything) |
| `STORY` flight | ❌ | ✅ |
| `CHALLENGE` flight, scoped to challenge *N* | ✅ challenge *N* only | ✅ all active |

So a Route challenge moves **only** when a flight was explicitly flown under it. The
other three types have no position pointer, so they credit passively from any eligible
flight regardless of what it was scoped to — including one flown under a different Route
challenge.

Crediting reads the landed flight's own `FlightLog.completedAt`, not a `now` read at
credit time. They differ by microseconds in practice and by a whole calendar day either
side of midnight — which is exactly when a streak is most likely to be riding on it.

## Route progress formula

```
progress = 1 − ( straight_line(current, destination) / straight_line(origin, destination) )
```

Clamped to `0f..1f`, using haversine great-circle distance (`ChallengeProgress.routeProgress`).

This is an **accepted v1 limitation**, not a bug: a straight line is only a proxy for real
routing, so progress can legitimately *decrease* when a real onward flight happens to
point away from the destination. Fixing it would mean modelling real route graphs, which
is a much larger change than the display it would improve.

### Per-type metrics

| Type | `progressFraction()` |
|---|---|
| `ROUTE`, predefined | `PredefinedRoute.progressAt(legIndex)` |
| `ROUTE`, free-form | the cached `route_progress_fraction` above |
| `SET_COMPLETION` | `visitedSetMembers.size / setTotalMembers` |
| `DISTANCE` | `cumulativeDistanceKm / targetDistanceKm` |
| `STREAK` | `streakDays / targetDays` |

`progressFraction()` is an extension function rather than a member of `Challenge`, so
Room's entity field-scanning never mistakes it for a column. The same applies to every
other computed property on that entity.

## Per-leg progress feedback

Landing does not just update a number. `resolveLandingOutcome` diffs the challenge list
from before and after the credit and produces, per challenge, either
`Advanced(fromFraction, toFraction)` or `Completed`. The Challenge Outcome screen animates
each tick-up from its old value, so the pilot sees the movement their flight caused
rather than only its result. When nothing changed, the result is `LandingResult.None`
and the screen is skipped entirely.

## Completion presentation

Reaching `COMPLETED` and being *shown* that completion are two different moments, tracked by one
column: `challenges.celebrated`, `false` by default. A challenge with `celebrated = false` keeps
occupying its slot on the Challenges screen (and keeps counting against the active-challenge cap,
`hasCapSlot`) and is excluded from the completed-challenges log — `ChallengeDao.getSlotDisplayFlow`
and `getCelebratedCompletedOrderedByCompletedAt` are the two queries this splits across. Nothing
else about completion (crediting, `status`, `completed_at`) waits on it; only the presentation does.

Any landing that completes at least one challenge sends CONTINUE (from the Challenge Outcome
screen) to the Challenges screen instead of Hub — see [navigation.md](navigation.md). There, each
uncelebrated completion plays a short animation, left to right in slot order: the card duplicates
from its slot and grows to center stage with a confetti burst while the slot row immediately
compacts to slide remaining challenges left and reveal the newly opened empty slot, then — on tap —
flies up and vanishes into a new entry at the top of the log, at which point
`LocalChallengeRepository.markCelebrated` finally flips the flag. `ChallengesViewModel.celebrationQueue`
is computed **once**, from the database, when the ViewModel is constructed — not kept live — so a
challenge already mid-animation this session is never re-queued, and the queue is naturally rebuilt
correctly no matter when or how the app was last closed: any `COMPLETED, celebrated = false` row is
still sitting in its slot the next time Challenges opens, regardless of what screen the pilot was on
when they left.

## Isolation

A `SET_COMPLETION` challenge starts **fresh**. It is never seeded from Story Mode's
visited-set, even though the app already knows the pilot has been to half its members.
The goal is "visit these while this challenge is running", and pre-filling it would make
starting one meaningless. This is the sharpest difference from a Geographic *achievement*,
which reads persistent state by design — see
[achievements.md](achievements.md#relationship-to-challenges).

## Lifecycle

```
(start) ──▶ ACTIVE ──▶ COMPLETED     final qualifying flight
              │
              └──────▶ (row deleted)  abandon
```

**Abandon, not reset.** Abandoning deletes the row entirely rather than storing an
`ABANDONED` status. There is no in-place reset — restarting means starting a new
instance, with a new id and genuinely zeroed progress.

**Active-challenge cap: 3**, shared across all four types and both sources
(`MAX_ACTIVE_CHALLENGES`). `startCuratedChallenge` and the custom starters return
`StartChallengeResult.CapReached` rather than throwing. Abandoning frees a slot.

Completed challenges are not deleted — they stay as a flat log surfaced through
`listCompletedChallenges`, and appear in the passport. That log is why "challenges
completed" has no `AchievementStatus` representation: it is a list, not a progress bar.

## Persistence & route scoping

Challenge instances live in the Room `challenges` table, written **only** by
`LocalChallengeRepository`. This is genuinely mutated state, correctly so: an instance
starts fresh and is not derivable from history, which is why it is persisted at all while
achievements are not.

**Scoping a session to a challenge** works through the same nav-argument mechanism
everything else uses. `challengeId` is threaded from Flight Search through Check-In into
In-Flight, encoded as an `Int` with a `-1` sentinel for "none" — see
[navigation.md](navigation.md). Check-In only passes it through; In-Flight reads it, and
it is what tells step 4 which Route challenge to advance.

`focused_route_challenge_id` in `SharedPreferences` is a different thing and easy to
confuse with it: a display-only pointer for which challenge the Hub highlights. It never
mutates a challenge row.

**Each challenge has its own paused-flight slot**, `challenges.paused_flight`, independent
of both the Story and Free global slots. A pilot can have a paused story flight, a paused
free flight, and a paused leg of each active challenge, all at once.

### Concurrent writes

The `challenges` row is the app's most contended piece of state, and the rules that keep
it correct are load-bearing enough that they live in [state.md](state.md)'s
*Concurrent writers* section rather than here. The short version, for anyone adding a
write path:

- **Never use a whole-row `@Update`.** Use a scoped statement
  (`updatePausedFlight`, `updateRouteProgress`). A whole-row update writes every column,
  so two racing ones are a lost update, never a merge.
- All public writes go through `LocalChallengeRepository.writeMutex`. It is **not
  reentrant** — every private helper below the public entry points is deliberately
  lock-free.
- The cap check is a count-then-insert, which two taps could otherwise both pass. The
  mutex is what makes it safe.

### Facts vs. the clock

`streak_days` and `last_flown_day` record what happened, not what is true now: nothing
runs at midnight to notice a streak dying. A pilot who flew Monday and Tuesday and
skipped Wednesday still has `streak_days = 2` stored on Thursday, and that row is
*correct* about the past.

The resolution happens once, on read, at the database boundary:
`LocalChallengeRepository` maps every active challenge through
`Challenge.withStreakEvaluatedAt`, zeroing a run whose last day is older than yesterday.
Everything downstream — `progressFraction()`, the Hub card, the slot row,
`resolveLandingOutcome` — is handed an already-correct row and needs no clock of its own.
The stored row catches up on the next credited flight.

## Entry & management surface

The **Challenges screen** (`Screen.Challenges`) is a full destination, not a Hub bottom
sheet. It carries:

- the three active challenge slots, each with progress, a resume action for a paused leg,
  and a continue action
- Free Mode entry
- the completed-challenge log
- the still-unearned achievements tab

It was a bottom sheet originally, which needed its own inner scroll and swapped five view
states inside a single card. The Hub keeps only the focused-challenge card, pointed at by
`focused_route_challenge_id` — see [core-loop.md](core-loop.md), since a focused challenge
also changes which airport the Hub displays.

**Achievements share this screen deliberately**, as its second tab (`ChallengesTab`). They
are the same kind of thing from the pilot's side — a goal with progress — even though the
two are implemented completely differently (instance vs. query, see
[achievements.md](achievements.md#relationship-to-challenges)). The tab shows only
still-unearned ones; earned badges live in the passport.
