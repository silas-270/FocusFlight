# Modes and the home base

Blocktime has one permanent track, Story Mode, and two ways to fly without touching it: Free
Mode and challenge legs. This file explains how a flight is tagged with its mode, what each mode
may and may not change, and the home base that anchors Story Mode.

## Mode tags

Every session carries exactly one `FlightMode`, fixed when the flight is booked and stored on its
`FlightLog` row. Nothing reclassifies a flight afterwards. The tag exists so that the landing
pipeline, and every later reader of the logbook, can branch on what a flight was rather than
trying to infer it after the fact.

| Tag | What it is |
|---|---|
| `STORY` | Normal play. The pilot's position, the world map and everything derived from history. |
| `FREE` | A one-off session from anywhere to anywhere, isolated from Story progress. |
| `CHALLENGE` | A leg flown under one active Route challenge. |

`STORY` is the column's default value, both in the entity and in the database schema, and the
enum converter falls back to it for an unknown name.

## The isolation matrix

The three modes exist to express this table. Isolation was chosen over one shared position
because a single global position being moved by three different tracks makes the mental model
much worse for very little gain: a pilot would finish a challenge leg somewhere unrelated to
their story and have to fly back.

| | `STORY` | `FREE` | `CHALLENGE` |
|---|---|---|---|
| Written to the logbook | ✅ | ✅ | ✅ |
| Moves the current airport | ✅ | ❌ | ❌ |
| Counts toward visited countries and the map | ✅ | ❌ | ❌ |
| Counts toward achievements | ✅ | ❌ | ❌ |
| Credits Distance, Set and Streak challenges | ✅ | ❌ | ✅ |
| Moves a Route challenge's position pointer | ❌ | ❌ | ✅ its own only |
| Counts toward total flights and flight time | ✅ | ✅ | ✅ |
| Counts toward airports visited | ✅ | ❌ | ❌ |
| Longest-flight and distance highlights | ✅ | ✅ | ✅ |
| Counts toward tours | ✅ | ✅ | ✅ |
| Paused-flight slot | the Story slot | the Free slot | the challenge's own row |

Two rows carry most of the reasoning.

**A challenge leg credits the passive challenge types but not the story.** It moves its own Route
challenge and passively credits every active Distance, Set and Streak challenge, while leaving
the current airport and the visited set untouched. Distance, Set and Streak challenges have no
position of their own, so any flight that is not a Free Mode flight is a legitimate contribution
to them.

**Activity statistics are not isolated; geography statistics are.** Total flights, total flight
time, tours and the longest-flight and distance highlights count every mode, because a Free Mode flight
really was flown and the pilot really did spend that time focused. Airports visited is
Story-only, because it sits next to the visited-country map on the passport and the two have to
agree.

## Where the filtering happens

Filtering to Story flights happens once, at the boundary where history becomes geography or
achievements, so no caller can forget it:

| Boundary | Filtering |
|---|---|
| `AirportRepository.getVisitedGeography` | Story origins and destinations, plus the home base |
| `FlightLogDao.getDistinctAirportsInMode` | scoped by mode in SQL, home excluded and added back once |
| `AchievementProgress.evaluateDistance` / `evaluateBehavioral` | take the full history and filter to Story internally |

`AchievementProgress` taking the unfiltered history is deliberate. The Passport already holds
the all-modes history for its logbook, and a caller that passes it still gets the right answer.
The mode is passed to the DAO as a `FlightMode` rather than a `'STORY'` literal so that the Room
type converter owns the enum's on-disk spelling everywhere.

## Story Mode

The permanent track, and the one the app is really about.

**The origin lock.** A Story flight departs from wherever the pilot currently is. There is no
origin picker and no fast travel; position moves only by flying. That constraint is what makes
the world map worth filling in, and it is also what makes a Story origin trustworthy as a visit.

**The home base** is chosen during onboarding and stored in `user_profile.home_airport_iata`,
its only store. It counts as visited, so its country is on the map and in every geographic
achievement from the start. So does the origin of every Story flight: under the origin lock a
Story origin is always a place the pilot really was, and counting it means that changing home
base never un-visits the old home's country. A blank value means "no home base";
`resolveHomeAirportIata` and `getVisitedGeography` both normalise blank to null, so the two
ways of reading the value cannot disagree about it.

**The current airport** lives in `SharedPreferences` (`current_airport_iata`) and is resolved
through `resolveCurrentAirportIata`, which falls back to the home base if it is somehow missing.
Onboarding writes both, so the fallback is defensive rather than routine.

### The two cooldowns

Two actions on the Settings screen move the pilot or their home without a flight. They are the
only such actions in the app, which is why they are rate-limited. Each has its own timestamp and
its own cooldown, and the two are never conflated. The arithmetic lives in `HomeBaseCooldown`,
a pure object that takes `now` as a parameter so it can be tested against fixed clocks.

| Action | Cooldown | Timestamp key | Effect |
|---|---|---|---|
| Return home | 7 days | `last_return_home_at` | The current airport becomes the home base. Home itself does not change. |
| Change home base | 30 days | `last_home_base_changed_at` | Rewrites `user_profile.home_airport_iata`. The pilot does not move. |

Both cooldowns are rolling windows (`now - last >= days × 24 h`) rather than calendar-day
boundaries; nothing about the actions calls for calendar semantics, and a rolling window has no
edge cases around midnight or time zones.

**Onboarding seeds the change timestamp 31 days in the past.** A new pilot is then eligible to
change their home base immediately, through the very same check every later change uses, with
no special "first change is free" path to maintain.

**Both actions re-check their cooldown inside the coroutine** that performs them, not only in the
UI's enabled state, because the screen's copy of eligibility can be stale. Every exit path
reports a `HomeBaseActionResult` (`ReturnedHome`, `HomeBaseSet`, `Ineligible`, `Failed`), and the
celebration screens are driven off that result rather than off the tap that started them, so "we
celebrated" and "it actually happened" cannot diverge. A missing home base during return home is
`Failed`, not `Ineligible`: onboarding always writes one, so its absence is a broken profile
rather than a rule being enforced.

**The real change is written before the cooldown is stamped.** Change home base writes Room first
and stamps the 30-day cooldown only if that write succeeded; burning a month-long cooldown on a
change that did not happen would be the worst of both outcomes.

**Return home and a paused Story flight.** A paused Story flight departs from where the pilot
was. Left resumable after the teleport, the Hub would offer to resume a flight from an airport
the pilot is no longer at, and landing it would break the origin lock. Return home therefore
clears the Story slot when the paused flight departs from anywhere other than home, and the
confirmation modal says so beforehand. The slot is cleared before the pilot is moved, so a
failure between the two leaves the pilot where they were with nothing to resume, rather than at
home with a flight from elsewhere. Free and challenge slots are untouched. Change home base
leaves the Story slot alone: it moves home, not the pilot.

**Return home warms the Hub.** The return-home animation lasts ten seconds and cannot be
dismissed, so the moment it starts `prepareReturnHome()` renders the home base's globe into the
image cache. By the time the pilot lands on the Hub, the image is usually there. The home base's
render is also pinned in the cache permanently ([maps.md](maps.md#the-cache)), because with a
seven-day cooldown it would otherwise always have been evicted by the time it was needed.

## Free Mode

An unconstrained one-off session: any origin in the main network, any destination on offer from
it, and nothing about Story progress changes. It is entered from the Challenges screen, behind a
short notice saying exactly that: the flight goes in the logbook and counts toward totals, tours
and highlights, but not toward achievements, challenges or the map, and the pilot stays at their
current airport.

Because the origin is a real choice rather than the current airport, it travels with the booking
as a route argument ([navigation.md](navigation.md#argument-threading)).

Free Mode has its own paused-flight slot, `paused_free_flight`, separate from Story Mode's. A
paused Free flight and a paused Story flight can therefore coexist, and starting a Free flight
can never discard a Story flight in progress. The Hub only ever shows the Story slot; the Free
slot has its own Resume row on the Challenges screen.

A Free Mode flight is never rescued from a dead-end origin the way a Story flight is. The rescue
rewrites the current airport, which a Free session never touches; a Free pilot simply sees "No
flights available" and picks another origin.

## Challenge Mode

Not an entry point of its own: the tag applied to a leg booked from an active Route challenge.
The origin is the challenge's position pointer, the paused-flight slot is the challenge's own
row, and on landing the leg advances that challenge only. The rules are in
[challenges.md](challenges.md).
