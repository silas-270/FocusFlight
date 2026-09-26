# Achievements, the passport, and tours

Everything on this page is **derived from the flight log**, not stored. Achievements are
a query over history; challenges are instances with their own state. That distinction is
the reason there are two systems, and is the first thing to check when deciding which one
a new idea belongs in.

## The four v1 categories

Three are progress-bar shaped and modelled by `AchievementCategory`:

| Category | Reads | Catalog |
|---|---|---|
| **Geographic sets** | `VisitedGeography` — countries and continents visited | `GeographicAchievementCatalog` |
| **Distance milestones** | Cumulative `STORY` distance flown | `DistanceAchievementCatalog` |
| **Behavioral** | Properties of individual flights | inline in `AchievementProgress` |

The fourth, **challenges completed**, is not a member of that enum and has no
`AchievementStatus` representation at all. It is a flat log
(`ChallengeRepository.listCompletedChallenges`), not a progress bar, so giving it a
percentage would have been meaningless.

### Geographic sets

Every geographic goal must be able to **enumerate its own members**, not just count
them. `memberProgress` is abstract on purpose: a goal is a *set*, and one that could only
report `12 / 54` would be exactly the dead end that requirement exists to prevent. The
scalar progress bar is a derived summary of the checklist, not the only thing the goal
knows about itself.

Progress is read live off `VisitedGeography`, which is already `STORY`-filtered at the
`AirportRepository.getVisitedGeography` boundary. A visit is the origin or destination of a
`STORY` flight, or the home base.

**Only reachable geography counts.** The denominators come from `getContinentCountryMap`, built
from airports in the main route network only, with each country under exactly **one** continent
(the continent most of its airports are on; Russia is overridden to Europe). The raw data files
Spain, Egypt, Greece, Russia, Turkey and the US under two continents, which used to make "World
Traveler" need 239 of 233 countries and put Spain on "Master of Africa". World Traveler now
targets the distinct reachable countries. Globetrotter targets the continents the network
reaches (six — no Antarctic airport has a route, so there is no "Master of Antarctica"), and a
continent counts as *reached* if a visited country counts under it **or** a visited airport sits
on it, so Honolulu still reaches Oceania.

### Distance milestones

Thematic rather than linear — a few big, flavourful targets rather than "fly 1 / 5 / 10
flights" counters. The five current ones are **one ladder, not five independent goals**:
they all read the same cumulative distance, so passing the furthest necessarily means
passing the nearer ones. That is what `familyId` / `familyRank` on `AchievementStatus`
express, so the UI can collapse a ladder into a single tiered badge instead of showing
five bars that all move together. Distances are stored in km (as the flight log is) and
reported in miles, like every distance in the UI.

### Behavioral

| Achievement | Condition |
|---|---|
| Marathon Flight | A single `STORY` flight ≥ 480 minutes |
| Red-Eye Pilot | A `STORY` landing between 00:00 and 04:59 **local device time** |
| High Altitude Club | Origin or destination in a fixed set of airports above 10,000 ft |
| Equator Crossing | A long-haul `STORY` flight between a northern and a southern hub |

Red-Eye uses the device's default timezone, the same basis as every other
`completedAt`-derived display in the app — not UTC.

> High Altitude and Equator Crossing are both evaluated against **hardcoded IATA sets**
> rather than the airport table's `elevation_ft` and `lat` columns, which are present in
> `flights.db` and would give exact answers. This is a known shortcut.

## One shape for every achievement

`AchievementStatus` is always fully populated, whether or not it is unlocked. There is no
hidden or mystery variant.

**Reveal style: locked achievements are fully visible with their progress shown.** The
app is a study tool; a mystery box that rewards poking at it is friction pointed the
wrong way.

`current` / `target` share the unit named by `unitLabel`. A boolean-style achievement uses
`target == 1.0` with an empty `unitLabel`, so a renderer should special-case `target <= 1.0`
rather than printing "0 / 1".

`members`, `familyId` and `familyRank` are nullable fields on this one shape rather than
per-shape subclasses — the same call `Challenge` makes for its four types, for the same
reason: every consumer wants one list to sort, filter and render.

## Evaluation is pure; unlock time is not

`AchievementProgress` has no Room, `AirportRepository` or JNI dependency, which is what
makes it JVM-unit-testable. It takes the **full, unfiltered** flight history and filters
to `STORY` internally, so a caller who passes the wrong list still gets the right answer.

Evaluation always leaves `unlockedAt` null. `AchievementsRepository` fills it in
afterwards from the `achievement_unlocks` table — a lazy upsert with `IGNORE` on
conflict, holding **only a timestamp**. Unlock *status* itself stays derived; the table
exists solely so the passport can order badges by when they were earned.

A null `unlockedAt` means "unlock time unknown", never "locked". `isUnlocked` is the only
authority on that.

## The shared derivation

Everything on this page, plus the stats and tours below, is computed **once** by
`PilotProgressRepository` and shared by the passport, the Challenges screen, the Hub and
Flight Search. Each of those used to rebuild its own slice on every visit, and each of
their ViewModels is destroyed on `popBackStack` — so reopening the passport meant
re-reading the whole flight log, re-deriving geography with a full airports-table scan,
and re-evaluating every achievement, for an answer identical to the one discarded seconds
earlier.

`PilotProgress` carries: `history`, `homeAirportIata`, `geography`, `achievements`,
`stats`, `highlights`, `tours`.

**Why this is a cache and not a second source of truth.** Nothing is persisted. The chain
is rooted in two Room `Flow`s — the profile row and the flight log — so Room's own
invalidation tracker re-emits on any write to either table. Discard the object at any
moment and the rebuild produces identical values, which is the test for whether something
is a cache at all. It is keyed deliberately narrowly: a username edit changes the profile
row but cannot change any number derived here, so it does not trigger a recompute.

A failed derivation **skips the emission** rather than publishing a partial result, so
the last good snapshot stays on screen and the next write retries automatically. Both
behaviours are pinned by `PilotProgressRepositoryTest`.

See [state.md](state.md) for where this sits among the app's other state.

## Passport surfaces

The Account screen ("passport") is the read-only view of everything derived:

| Surface | Source |
|---|---|
| Visited-country map | `VisitedGeography` over `world-map.svg`, via `WorldMapParser` and `WorldMapProjection` |
| Stats grid | `FlightStats` — `totalFlights`, `totalMinutes`, `airportsVisited` |
| Highlights row | `FlightHighlights` — longest flight, most-visited airport, and total distance as a multiple of the equator's circumference |
| Badge grid | `AchievementBoard`, families collapsed into tiers |
| Unfinished achievements | The same board, shown as the Challenges screen's second tab |
| Logbook | The flight log, collapsible and sortable |
| Completed challenges | `listCompletedChallenges` |
| Home base section | Return home / change home base — see [modes.md](modes.md) |

`airportsVisited` is `STORY`-scoped so it agrees with the map beside it (Story origins and
destinations), and counts the home airport once. `totalFlights`, `totalMinutes` and the three
highlights (longest flight, most-visited airport, equator ratio) are mode-blind, because they
describe flying the pilot actually did — a Free Mode flight really was flown and landed.
The isolation matrix in [modes.md](modes.md) has the full rules.

## Tours

A **tour** is one campaign of flying: a run of flights with no gap longer than
`TOUR_GAP_DAYS` (10) between consecutive ones. It is a pure fold over the flight log.

Tours exist because a daily streak is the wrong shape for how this app is used. Sessions
come in bursts of a few weeks before an exam and then stop for months, so a streak
counter spends most of the year at zero and punishes an off-season that is not a failure.
A tour **ends** rather than breaking: it closes, keeps its numbers, and the next one
starts fresh with nothing lost.

The 10-day gap is deliberately generous because the two errors are not symmetric.
Splitting one real exam period into two tours makes both halves look shorter and less
impressive than the work actually was — which is the thing tours exist to avoid. Merging
two genuinely separate periods is the milder mistake.

`activeDays` over `spanDays` is the pairing that matters for display: "12" alone reads as
a small number, "12 of 14 days" reads as near-perfect attendance, which is what it is.

**Whether a tour is still running is not a field.** That answer changes with the clock
rather than with the data, and this snapshot only recomputes when Room writes — so a
stored `isOpen` would keep claiming a tour was live for exactly as long as the pilot did
not fly. It is the function `Tour.isOpenAt(now, zone)` instead. `Challenge.currentStreak(today)`
and `HomeBaseCooldown.isEligible(now, ...)` are the same rule on the persisted side; see
[state.md](state.md)'s *Facts vs. the clock*.

## Relationship to challenges

The two systems look similar and are not.

| | Achievement | Challenge |
|---|---|---|
| Nature | A query over history | An instance with its own row |
| Persisted | Only an unlock timestamp | Full progress state |
| Starting point | Reads all existing Story Mode progress | Starts at zero |
| Can be abandoned | No | Yes — deletes the row |
| Count limit | None | 3 active |

**A Geographic achievement and a Set-completion challenge can name the same set and still
behave differently, correctly.** The achievement credits countries the pilot visited years
ago, because an achievement is meant to recognise persistent progress. The challenge
starts fresh, because the goal is "visit these while this is running". Pre-filling the
challenge from the achievement's data would make starting one meaningless.

The one deliberate crossing point is the other direction: completed challenges get their
own recognition-only category in the passport. That is the flat log above — it grants no
achievement progress.
