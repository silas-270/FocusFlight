# Paused and resumed flights

A long-haul session is not flown in one sitting. The Hong Kong–San Francisco leg of the
circumnavigation challenge is scheduled at twelve and a half hours, and even a two-hour flight gets
interrupted by a phone call. This file explains how
a flight survives being backgrounded, left, killed by the system, and finally landed, without ever
being resumable twice or lost.

## One record per slot

Everything needed to continue a flight is one value, `PausedFlight`:

| Field | Meaning |
|---|---|
| `flightNumber`, `originIata`, `destIata`, `durationMin` | the booking |
| `mode`, `challengeId` | which track it belongs to |
| `elapsedMs` | how far into the flight it was, or null if it never ticked |
| `camera` | the saved camera mode, position and rotation, or null if none was saved |

It lives in exactly one of three kinds of **slot**:

| Slot | Store | Shown on |
|---|---|---|
| Story | `SharedPreferences` key `paused_flight` | the Hub |
| Free | `SharedPreferences` key `paused_free_flight` | the Free Mode row on Challenges |
| Route challenge *N* | `challenges.paused_flight` on row *N* | challenge *N* on Challenges, and the Hub while *N* is focused |

A pilot can hold one paused flight in each slot at the same time. Keeping them apart is what lets a
Free flight or a challenge leg start without discarding a Story flight in progress
([modes.md](modes.md#the-isolation-matrix)).

The booking and the live state (elapsed time, camera) are deliberately one record rather than
several keys. Flight numbers are derived from the destination alone and are not unique (two
bookings to the same airport get the same number), so anything keyed by flight number could let two
paused flights collide and overwrite each other's progress. Keyed by slot instead, the record simply
is the slot's contents.

`PausedFlight.serialize()` writes the fifteen fields pipe-delimited, empty for null, and `parse()`
refuses anything that does not have exactly fifteen fields or whose numbers do not parse. The same
format is used for the preferences string and, through a Room type converter, for the challenge
column, so there is one serialisation to trust.

`PausedFlightStore` (`get`, `save`, `clear`) is the common interface over all three. A session
chooses its store once, from its mode and challenge id, and never branches on mode again.

## Starting a flight

Check-In's **Start flight** writes a fresh `PausedFlight` (no elapsed time, no camera) into the
session's slot, which is also how an older paused flight in that slot is discarded. The Hub and the
Free Mode row ask for confirmation before booking over a paused flight for that reason.

The write completes **before** the navigation to In-Flight. The navigation pops Check-In, which
cancels the coroutine scope the write runs in, and for a challenge slot the write begins with a
suspending Room read. Navigating first would let the write be cancelled; with no record in the slot,
every later elapsed-time save would find nothing to update, and a backgrounded flight would lose all
its progress. Writing first removes the race rather than narrowing it.

## While flying

The timer persists elapsed time into the slot once per second of flight, reading the current record
and writing it back with the new `elapsedMs`. The camera pose is saved on `ON_STOP` only, since it
only needs to be current when the pilot leaves; `nativeGetCameraPose()` returns all zeros when the
engine has no camera yet, and a zero quaternion is never saved over a real one.

Leaving the app stops the timer on `ON_STOP` and restarts it on `ON_START`, unless the leave dialog
was open, in which case only RESUME restarts it. Nothing keeps a flight running in the background:
there is no foreground service, so the clock simply does not advance while the app is not visible.
That is intentional for a focus timer, where time away from the app is not time spent focused.

## Resuming

`resumeFlight(...)` in `CesiumGameActivity` loads the paused booking into the engine and navigates
straight to In-Flight, skipping Check-In. `InFlightViewModel` reads the slot on start:

- `elapsedMs` becomes the starting clock. A never-saved record starts at −3,000 ms, the countdown
  hold of a fresh flight ([core-loop.md](core-loop.md#the-clock)).
- If a camera pose was saved, the camera mode is set first and then the pose, so the mode is already
  correct when the pose lands, and the camera picker shows the restored mode even though it arrives
  after the first frame.
- The departure clock on the HUD is back-computed as now minus the elapsed time, so a resumed flight
  "departed" when it would have if it had never stopped.

## Process death

Android can kill the process while the app is in the background, and Navigation then restores the
back stack into a fresh process with a fresh, empty engine. Three things make that survivable:

- **The engine is reloaded on entry.** Check-In and In-Flight call
  `PendingFlightLoader.ensureLoaded(...)` when they appear. On the normal path the booking or resume
  code has already loaded this flight and the call is a no-op; after process death nothing has, and
  without this the restored screen would show a globe with no aircraft and zeroed telemetry.
- **The clock comes from the slot**, which lives in persistent storage, not in the ViewModel.
- **The landing screens do not wait for a result that cannot arrive.** A restored arrival screen
  finds `LandingResultChannel` unarmed and continues straight to the Hub, and a restored outcome
  screen with nothing to show forwards itself there
  ([navigation.md](navigation.md#what-travels-outside-the-route)).

## Landing

The landing clears the slot as part of step 3 of the pipeline, and only if the logbook write
succeeded; a failed write leaves the flight resumable, which is the recoverable state
([core-loop.md](core-loop.md#the-post-landing-pipeline)).

Clearing has to be final. The elapsed-time persist and the `ON_STOP` camera save both run outside
the landing's coroutine, and the camera save is triggered by the very navigation the landing causes.
Either could read the slot before the clear and write it back afterwards, and the pilot would find
the flight they just landed offered for resumption. `SessionPausedFlightStore` wraps the session's
store so that `clear()` also seals it: a save arriving before the clear is overwritten by it, and a
save arriving after is refused. The seal lives on the session wrapper rather than on the store,
because the Story and Free stores are shared objects and the next flight must be able to write them
([state.md](state.md#concurrent-writers)). For a challenge slot, every write is additionally a
scoped `UPDATE` of the `paused_flight` column only, so it can never revert the leg the landing just
credited.

## Discarding without landing

Two other paths remove a paused flight:

- **Booking over it**, as above, after a confirmation.
- **Return home** clears the Story slot when its flight departs from anywhere but home, because
  resuming it after the teleport would depart from an airport the pilot is no longer at
  ([modes.md](modes.md#the-two-cooldowns)). A flight paused at home stays valid.

Abandoning a Route challenge deletes its row, and its paused leg with it.
