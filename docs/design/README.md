# FocusFlight — Game Modes Design

Planning docs for the "game modes" feature (Story / Free / Challenges).
Started 2026-08-27. **No implementation yet** — this is the brainstorming
and spec phase. Nothing here is code, and nothing should be built until a
file's status says so.

Working agreement: we design **one feature at a time**, not several
threads in parallel. Each file below is one feature. Don't open a new
one until the current one reaches "ready to build" or is deliberately
shelved.

## Status legend

- 🟡 brainstorming — ideas being shaped, decisions still open
- 🟢 decided — shape is settled, details may still need filling in
- 🔵 ready to build — spec is complete enough to start Phase 1 work
- ⚪ shelved — considered, deliberately not doing yet

## Files

| File | Status | What it covers |
|---|---|---|
| [core-loop.md](core-loop.md) | 🟢 decided | The unchanged core session loop, and how mode-selection attaches to it without disturbing it |
| [mechanics.md](mechanics.md) | 🟢 decided | Cross-cutting foundation every mode builds on: the STORY/FREE/CHALLENGE session tag, the isolation matrix, and the post-landing pipeline |
| [codebase-map.md](codebase-map.md) | 🟢 decided | Snapshot of where each concept above anchors in the real code today, and the minimal-diff slot-in point for each — gathered read-only so implementation doesn't have to rediscover it |
| [story-mode.md](story-mode.md) | 🟢 decided | The existing permanent mode: origin-lock, home base + return, no fast-travel |
| [free-mode.md](free-mode.md) | 🟢 decided | Unconstrained one-off sessions, fully isolated from story progress |
| [challenges.md](challenges.md) | 🟢 decided | Quest-log style long-running goals across 3 types (Route/Set-completion/Distance); cooperative multiplayer (later) |
| [achievements.md](achievements.md) | 🟢 decided | Category-based achievements: geographic sets, distance milestones, behavioral, challenges-completed |
| [streaks.md](streaks.md) | ⚪ shelved | Daily/weekly engagement mechanic — parked, revisit after modes ship |
| [decisions-log.md](decisions-log.md) | — | Dated running log of every decision made and why, newest first |

## Cross-cutting principles (apply to every file above)

1. **Don't touch the existing core loop.** Story Mode's booking →
   check-in → in-flight → landing flow stays exactly as it is today.
   New modes are additive entry points, not changes to that flow.
2. **Free Mode and Challenges are fully isolated from Story Mode
   progress.** Neither touches `currentAirport`, visited-airports, or
   country/continent completion. Confirmed 2026-08-27 — keeps the
   mental model simple, avoids reconciling a single global position
   across three different tracks. One narrow, deliberate exception:
   completed challenges get their own recognition-only category in
   the Achievements screen — see [challenges.md](challenges.md). The
   full mechanism (session tags, exactly what each does and doesn't
   affect, including logbook visibility) is canonical in
   [mechanics.md](mechanics.md) — this principle is the *why*, that
   file is the *how*.
3. **This app is a study tool first, a game second.** Any mechanic
   that adds pre-session friction (mode pickers, menus, decisions)
   needs to justify itself against that. The one-tap "just start a
   session" path must never get harder to reach.
4. Everything real-world stays real-world: the flight database only
   contains actual scheduled routes, updated weekly. No fictional
   routes, no authored waypoint chains — the puzzle is *finding* a
   real path, not following a scripted one.
