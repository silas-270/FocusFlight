# Modes and the home base

## Mode tags

Every flight session carries exactly one `FlightMode`, written at session start and
stored directly on the `FlightLog` row. It is write-once — nothing reclassifies a flight
afterwards — and it exists so the post-landing pipeline can branch on it rather than
re-deriving it after the fact.

| Tag | What it is |
|---|---|
| `STORY` | Normal play. The pilot's position, the world map, and everything derived from history. |
| `FREE` | A one-off session flown from anywhere to anywhere, fully isolated from story progress. |
| `CHALLENGE` | A session flown explicitly under one active Route challenge. |

`STORY` is the column default, so every flight logged before the tag existed reads back
correctly.

## The isolation matrix

This is the rule the three modes exist to express. Isolation was chosen over a shared
position because reconciling one global position across three tracks makes the mental
model much worse for very little gain.

| | `STORY` | `FREE` | `CHALLENGE` |
|---|---|---|---|
| Written to the logbook | ✅ | ✅ | ✅ |
| Moves `current_airport_iata` | ✅ | ❌ | ❌ |
| Counts toward visited countries / the map | ✅ | ❌ | ❌ |
| Counts toward achievements | ✅ | ❌ | ❌ |
| Credits Distance / Set / Streak challenges | ✅ | ❌ | ✅ |
| Moves a Route challenge's position pointer | ❌ | ❌ | ✅ (its own only) |
| Counts toward `totalFlights` / `totalMinutes` | ✅ | ✅ | ✅ |
| Counts toward `airportsVisited` | ✅ | ❌ | ❌ |
| Appears in the logbook UI | ✅ | ✅ | ✅ |
| Has its own paused-flight slot | ✅ | ✅ | ✅ (per challenge) |

Two rows are worth reading twice.

**A `CHALLENGE` flight credits the passive challenge types but not the story.** It moves
its own Route challenge's pointer and passively credits every active Distance, Set and
Streak challenge, while leaving `current_airport_iata` and the visited-set alone.

**Activity stats are not isolated; geography stats are.** `totalFlights` and
`totalMinutes` count every mode — a Free Mode flight really was flown, and the pilot
really did spend that time focused. `airportsVisited` is `STORY`-only, because it sits
next to the world map and the two must agree. This is a judgment call rather than
something derived, and it is a one-line change if the product answer turns out different.

## Where the filtering happens

Not at each call site. Filtering to `STORY` happens **once**, at the boundary, so
callers cannot forget:

| Boundary | Filters |
|---|---|
| `AirportRepository.getVisitedGeography` | `STORY` only, for the map and geography |
| `FlightLogDao.getDistinctDestinationsInMode` | scoped by mode in SQL |
| `AchievementProgress.evaluate*` | takes the *full* history and filters to `STORY` internally |

`AchievementProgress` taking unfiltered history and filtering inside is deliberate: a
caller who passes the wrong list gets the right answer anyway. A mode-blind count is
exactly how a Free Mode flight once raised "places visited".

## Story Mode

The permanent mode, and the one the app is really about.

**Origin lock.** A flight departs from wherever the pilot currently is. There is no
picker, and no fast travel — position moves only by flying. That constraint is what makes
the world map worth filling in.

**Home base.** Chosen during onboarding and stored in `user_profile.home_airport_iata`,
which is its sole owner. It counts as a visited country, so a wrong value here silently
changes both the map and Geographic achievements. A blank field means "no home base";
`resolveHomeAirportIata` and `getVisitedGeography` both normalise blank to null so the two
read paths cannot disagree.

### The two cooldowns

Two distinct actions on the Account screen, two separate timestamps, deliberately never
conflated. Both live in `HomeBaseCooldown`.

| Action | Cooldown | Timestamp key | Effect |
|---|---|---|---|
| **Return home** | 7 days | `last_return_home_at` | Moves `current_airport_iata` back to the home base. Home does not change. |
| **Change home base** | 30 days | `last_home_base_changed_at` | Rewrites `user_profile.home_airport_iata`. |

Both are direct state mutations — the only two in the app that move the pilot without a
flight — which is exactly why they are gated at all.

**Write order matters in both, in the same direction.** The real change lands first, and
the cooldown is stamped only after it succeeds. Burning a 30-day cooldown for a change
that did not happen is the worst of both outcomes. `returnHome` distinguishes its two
failures: a cooldown refusal reports `Ineligible`, while a missing home base reports
`Failed` — onboarding always writes one, so its absence is a broken profile, not a rule.

**Onboarding seeds the change-home timestamp 31 days in the past**, so a new pilot is
immediately eligible rather than locked out of a decision they have not made yet.

Return home also pins the home base's route map in the disk cache
(`MapImageCache.pinnedIatas`). With a 7-day cooldown, returning home would otherwise
always find a cold cache.

> `DEV_FEATURES_TO_REVERT.md`: both cooldowns are currently disabled by a dev override at
> the top of `HomeBaseCooldown.isEligible()`. One line disables both.

## Free Mode

An unconstrained one-off session: pick any origin and any destination, fly it, and
nothing about story progress changes. Entered from the Challenges screen.

Because the origin is a real choice rather than the pilot's current airport, it has to
travel with the booking through the nav arguments — see [navigation.md](navigation.md).

Free Mode has its own paused-flight slot (`paused_free_flight`), separate from Story
Mode's, so a paused Free Mode session and a paused Story Mode session can coexist. That
separation is deliberate, not incidental: sharing one slot would make starting a Free
Mode flight silently discard a story flight in progress.

## Challenge Mode

Not an entry point of its own — a tag applied to a session started from a specific active
Route challenge. Its rules are in [challenges.md](challenges.md); its isolation
properties are the `CHALLENGE` column above.
