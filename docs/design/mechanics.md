# Mechanics

**Status: 🟢 decided.**

Cross-cutting concepts every mode/feature file depends on, defined
once here instead of restated (and drifting) per file: session mode
tags, what each tag is allowed to affect, and the shape of the
post-landing pipeline. Added 2026-08-27 after the first review pass
found the same tag/isolation logic being independently re-derived in
`free-mode.md`, `challenges.md`, and `achievements.md`.

This file doesn't change [core-loop.md](core-loop.md) — it defines
what the existing "Logged" step now does internally, governed by
tag, so every other file can point here instead of restating it.

## Mode tags

Every session run through the core loop is tagged with exactly one of
three values. The tag is stored on the flight/logbook record itself,
not computed after the fact.

- **STORY** — normal Story Mode flying (the default, unchanged). Full
  read/write of `currentAirport` and the visited-set. See
  [story-mode.md](story-mode.md).
- **FREE** — a Free Mode session. See [free-mode.md](free-mode.md).
- **CHALLENGE** — a session flown explicitly under one specific active
  Route challenge. Only Route needs this: Distance and Set-completion
  challenges have no position pointer, so they don't need their own
  tagged session — they simply credit from any eligible flight (see
  below), whatever tag it carries. See
  [challenges.md](challenges.md#which-flights-count).

## The isolation matrix

What a flight counts toward is entirely a function of its tag. This
table is the canonical source — feature files should link here rather
than re-list it.

| Tag | Logbook | `currentAirport` + visited-set | Story-scoped achievements | Its own Route challenge's position pointer | Other active Distance/Set-completion challenges |
|---|---|---|---|---|---|
| STORY | yes | yes | yes | no — a Route pointer only moves under a CHALLENGE-tagged session for that specific instance | yes |
| FREE | yes (logged, but doesn't count toward anything below) | no | no | no | no |
| CHALLENGE (a Route session) | yes | no | no | yes, for that one challenge instance only | yes |

Two things worth calling out explicitly because they're easy to get
wrong by symmetry:

- **A CHALLENGE-tagged flight does not write `currentAirport`.**
  Confirmed 2026-08-27. The player is shown a small confirmation modal
  when they choose to continue a Route challenge, before entering the
  booking flow, making clear up front that this flight is scoped to
  the challenge and won't move their main Story position. (Exact
  modal copy/visual is a build-time detail.)
- **Logbook visibility is a separate concern from counting toward
  stats.** Confirmed 2026-08-27: every flight is appended to the
  logbook regardless of tag — Free Mode flights are real, valid,
  logged flights (the core loop's "Logged" step doesn't change), they
  just don't count toward any statistic, achievement, or challenge.
  Don't conflate "is this flight logged" with "does this flight
  count."

## Post-landing pipeline

Deliberately kept high-level. This isn't a strict contract to
implement line-by-line — exact intra-step ordering, animation timing,
and how simultaneous events get sequenced on screen are implementation
decisions for whoever builds this. What matters at the design level is
that steps 2–4 are driven consistently by the tag, in one place,
rather than each feature inventing its own version:

1. Flight lands, session ends (existing, unchanged).
2. A logbook entry is written, tagged with its mode. Always happens.
3. If tagged STORY: `currentAirport` and the visited-set update
   (existing, unchanged).
4. Every eligible flight (STORY or CHALLENGE — never FREE) is checked
   against all achievements and all active challenges, per the
   isolation matrix above.
5. The landing screen reflects whatever step 4 found. Confirmed
   2026-08-27: **always sequenced, never replaced** — the existing
   duration-based arrival screen (rank stamp) shows first exactly as
   it does today, then, if step 4 found a challenge advanced or
   completed, that leads into a second beat: the per-leg tick-up, or
   the dedicated completion celebration. A flight with no active
   challenge progress stops after the first beat, unchanged from
   today. Achievement-unlock presentation is content/build work, not
   specced here.

## Open items

None at the design level. Exact pipeline sequencing, the tag's
concrete storage representation, and modal/animation details are all
intentionally left for implementation.

See [codebase-map.md](codebase-map.md) for where each concept above
actually anchors in the existing code, gathered by a read-only
exploration pass — including two pre-existing duplication issues
(unrelated to this design) that this work will touch anyway and
should clean up rather than build on top of.
