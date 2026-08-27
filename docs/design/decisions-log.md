# Decisions log

Running log, newest first. One entry per decision, with the "why" —
so future-us (or a future session) doesn't have to re-litigate it.

## 2026-08-27

- **`codebase-map.md` added** after two read-only exploration passes
  over the real codebase (data-model/post-landing side, Hub/nav side).
  Confirms `ArrivalCelebrationScreen` and the achievements groundwork
  (`visitedCountries`/`completedContinents`) exist as assumed, but
  found: no mode/tag concept exists anywhere yet; two pre-existing
  duplication issues the game-modes work will touch regardless
  (`InFlightViewModel`'s landing sequence duplicated between normal
  completion and debug `skipFlight()`; the visited-set computation
  independently duplicated between `FlightSearchViewModel` and
  `AccountViewModel`); and one real gap — no existing analog for a
  Route challenge's isolated position pointer, since
  `FlightSearchViewModel` hardcodes the single global `currentAirport`
  preference as its origin source. Also confirmed the app's one
  existing modal convention (scrim+card `Box`, not `AlertDialog`) as
  the pattern to reuse for the Route-challenge confirmation and future
  home-base-teleport modals. See [codebase-map.md](codebase-map.md).
- **Landing screen always sequences rank stamp then challenge event,
  never replaces one with the other.** The existing duration-based
  arrival screen shows first unchanged; if a flight also advanced or
  completed a challenge, that leads into a second beat (tick-up or
  completion celebration) rather than superseding the first. See
  [mechanics.md](mechanics.md#post-landing-pipeline).
- **Challenge persistence and Route-scoping mechanism decided**: one
  challenges store (not per-type tables) holds every active/completed
  challenge, with a position-pointer field used only by Route;
  continuing a Route challenge threads its id through the booking flow
  so the existing flight-picking screen reads/writes that row instead
  of `currentAirport`. Resolved myself as a functional (not taste)
  call, following the pattern the codebase mapping already found for
  threading per-session values. See
  [challenges.md](challenges.md#persistence--route-scoping).
- **Mode tag confirmed write-once**, not mutable after a flight is
  logged — nothing in the design needs reclassification, and it's the
  simpler migration. See [codebase-map.md](codebase-map.md).
- **Restructure: new `mechanics.md` added as the canonical home for
  cross-cutting mechanics**, after the review pass showed the same
  tag/isolation logic being independently re-derived (and drifting
  slightly) across `free-mode.md`, `challenges.md`, and
  `achievements.md`. It now defines, once: the STORY/FREE/CHALLENGE
  session tag, the isolation matrix (what each tag does and doesn't
  affect), and a deliberately loose post-landing pipeline. The four
  feature files were trimmed to reference it instead of restating it.
  Added to the README file table. See [mechanics.md](mechanics.md).
- **Post-landing event ordering is intentionally left loose, not
  specced.** This is a high-level design pass — a separate
  implementation effort will work out the actual code, so
  `mechanics.md`'s pipeline section is a numbered list of what must
  happen (logbook write, achievement/challenge check, landing-screen
  variant) rather than a strict contract on exact sequencing. See
  [mechanics.md](mechanics.md#post-landing-pipeline).
- **A CHALLENGE-tagged (Route challenge) flight never writes
  `currentAirport`** — confirmed, matching the existing isolation
  principle. Because that's a surprising outcome from the player's
  seat (a real flight lands somewhere, main Story position doesn't
  move), continuing a Route challenge now shows a small confirmation
  modal before entering the booking flow, making that explicit
  up front. See [challenges.md](challenges.md) and
  [mechanics.md](mechanics.md).
- **Free Mode flights do appear in the logbook** — they're real,
  valid, logged flights, the core loop's "Logged" step is unchanged —
  they're just excluded from every statistic, achievement, and
  challenge computation. Logbook visibility and "counts toward
  something" are now explicitly separate concerns. See
  [free-mode.md](free-mode.md) and [mechanics.md](mechanics.md).
- **Route-challenge continuation gets its own entry diagram** in
  `challenges.md`, mirroring Free Mode's own hand-off diagram — makes
  explicit that it reuses the core loop (tagged CHALLENGE, scoped to
  that instance's pointer) rather than needing new screens. Distance
  and Set-completion don't need an equivalent, since they have no
  position pointer and credit passively. See
  [challenges.md](challenges.md#entry--management-surface).
- **Review-pass follow-ups resolved, closing gaps found in a fresh
  read of all five files:**
  - **Free Mode flights never count toward Challenges** (not just
    Story Mode progress/achievements as originally written). Without
    this, a Set-completion challenge like "visit all continents"
    would be trivialized by quick 30-minute Free Mode hops. See
    [free-mode.md](free-mode.md#isolation) and
    [challenges.md](challenges.md#which-flights-count).
  - **A single post-landing check drives both achievements and active
    challenges.** Eligible flights are STORY and CHALLENGE-tagged
    only; FREE-tagged flights are excluded from both. Route
    challenges still require an explicit per-challenge session
    (isolated position pointer); Distance/Set-completion passively
    credit any other eligible flight. See
    [challenges.md](challenges.md#which-flights-count) and
    [achievements.md](achievements.md#relationship-to-challenges).
  - **Route challenge progress can legitimately decrease** (straight-
    line proxy metric, a real route can point away from the
    destination) and **dead ends still have no exit besides abandon.**
    Both accepted as known v1 limitations rather than solved now — no
    fix was found during design; revisit once the mode is live and
    real usage shows how often either actually bites. See
    [challenges.md](challenges.md).
  - **Quest-log entry surface decided**: the Hub's new secondary
    button opens one menu combining the Challenges quest log (active
    list, start-new, abandon) and the Free Mode entry point — not a
    three-way Story/Free/Challenges picker, since Story Mode already
    has its entry point via "Book a flight." See
    [core-loop.md](core-loop.md#mode-select-addition) and
    [challenges.md](challenges.md#entry--management-surface).
  - **Achievements vs. Challenges framing made explicit**: an
    achievement is effectively a non-repeatable challenge that
    auto-starts at first launch, which is *why* Set-completion
    achievements read persistent Story Mode state while the matching
    challenge type deliberately starts at zero every time — a
    challenge must stay meaningfully replayable (e.g. someone re-
    running "visit all continents"), an achievement doesn't. Reaffirms
    the existing "starts fresh at zero" decision rather than changing
    it. See [achievements.md](achievements.md#relationship-to-challenges).
  - **"Challenges completed" achievement is a log, not a counter.**
    Dropped the "3/10"-style tally (no fixed denominator once custom
    challenges and repeat completions exist) in favor of a single
    completed-challenges log, modeled on the flight logbook — every
    completion appended, duplicates included. See
    [achievements.md](achievements.md) and
    [challenges.md](challenges.md#isolation-1).
- **`achievements.md` fully resolved.** Four category types for v1:
  geographic sets, thematic distance milestones (not linear counters),
  behavioral/session-based, and the already-decided challenges-
  completed cross-mode exception. Surfaced as a section within the
  existing Account/Passport screen, not a new destination. Locked
  achievements are fully visible with progress shown, not hidden/
  mystery. See [achievements.md](achievements.md).
- **`story-mode.md` fully resolved.** Return-home is an **instant
  teleport with a 7-day cooldown**, reversing the earlier "must be a
  real flight" decision (considered reusing the Route-challenge
  multi-leg mechanic instead, but that would remove the point of the
  feature — explicitly a "try it, revisit if it doesn't feel right"
  call on the teleport approach itself, though the 7-day length is
  deliberate). Changing home base gets its own separate **30-day**
  cooldown — no separate grace-change mechanic needed, since seeding
  the last-changed timestamp 31 days in the past at onboarding makes
  the first change immediately available through the same cooldown
  check everyone else uses. Also captured for later: a modal +
  animation during the teleport, not yet designed. See
  [story-mode.md](story-mode.md).
- **`challenges.md` fully resolved** — all remaining open items
  settled in one batch: Route progress formula
  (`1 - straight_line(current,dest)/straight_line(origin,dest)`,
  clamped 0–100%); per-leg feedback is a brief animated tick-up (not
  a quiet static number); custom Route challenges are named by city,
  not IATA code; Distance challenges get a custom target, Set-
  completion stays curated-only; active-challenge cap locked at 3;
  multiplayer defaults to Route first (low-stakes, revisit later).
  File status flipped to 🟢 decided.
- **Challenges are a family of three types, not one mechanic**: Route
  (real-route pathfinding, progress = straight-line distance to
  destination), Set-completion (e.g. "all continents," progress =
  count reached / total), Distance (cumulative flown distance toward
  a target, no destination). Complete v1 list, no other types planned.
  See [challenges.md](challenges.md).
- **Set-completion challenges start fresh at zero**, not inheriting
  your existing Story Mode visited-set, to keep the isolation rule
  consistent across all challenge types. See [challenges.md](challenges.md).
- **Every leg landing during a challenge shows progress feedback**
  (bar/percentage ticking up), not just the final leg — this repeated
  feedback loop matters more for retention than the one-time finale.
  Scale/prominence still open. See [challenges.md](challenges.md).
- **Challenge completion gets a dedicated celebration**, not a reuse
  of `ArrivalCelebrationScreen`: progress bar fills to 100%, turns
  golden, confetti. A normal leg landing during a challenge still uses
  the standard landing flow — only the final leg triggers this. See
  [challenges.md](challenges.md).
- **Completed challenges get their own category in the Achievements
  screen** (e.g. "Challenges completed: 3/10") — a deliberate, narrow
  exception to "challenges don't feed Story Mode progress": display/
  recognition only, no functional effect (no visited-airports, no
  stats, no unlocks). See [challenges.md](challenges.md) and
  [achievements.md](achievements.md).
- **Dead-end handling in challenges: abandon only, no in-place reset.**
  Abandoning a challenge removes it from the active list entirely and
  frees its slot in the active-challenge cap — also doubles as "get me
  out of this one, I'll start a fresh instance if I want to retry."
  See [challenges.md](challenges.md).
- **Active-challenge cap: small (working assumption 3), curated +
  custom combined.** Avoids MMO-style quest-log clutter. See
  [challenges.md](challenges.md).
- **Challenge routes come from both a curated list and user-defined
  custom pairs**, sharing the same active-challenge cap. See
  [challenges.md](challenges.md).
- **Challenge session mechanic confirmed**: no new flying mechanic —
  reuses Story Mode's flight-picking UI, scoped to a challenge-local
  position pointer instead of the global one. See
  [challenges.md](challenges.md).
- **Doc structure**: one file per feature under `docs/design/`, plus
  this log and a README index. Chosen over a single big markdown file
  to match the "work on one feature at a time" working agreement.
- **Multiplayer challenges: cooperative, not competitive**, for
  whenever multiplayer gets built (not v1). A shared target avoids
  attaching a "losing" feeling to not studying enough, which fits a
  study app's tone better than competition. See
  [challenges.md](challenges.md).
- **Home base return is a real flight, not an instant teleport.** An
  instant teleport would undermine the origin-lock constraint from the
  return direction the same way fast-travel undermined it from the
  departure direction. See [story-mode.md](story-mode.md).
- **Fast-travel to any visited airport: rejected.** Replaced with a
  single home base + return mechanic (with a not-yet-decided cooldown
  on changing home base). Unlimited fast-travel makes the origin-lock
  constraint meaningless. See [story-mode.md](story-mode.md).
- **Challenges use real connected routes, not freeform distance
  accumulation.** The flight DB only has real, currently-scheduled
  routes — no authored waypoint chains, no fictional legs. This turns
  a challenge into the same positional mechanic as Story Mode
  (isolated per-challenge position + destination as win condition)
  rather than a progress-bar abstraction. See [challenges.md](challenges.md).
- **Achievements are category/set-based (continents, countries,
  regions), not just incrementing counters.** Avoids "achievement
  soup." See [achievements.md](achievements.md).
- **Streaks shelved for now.** 30-minute minimum session doesn't fit a
  strict daily-chain mechanic the way short daily habits do. Revisit
  later, likely as a weekly target instead of a daily chain. See
  [streaks.md](streaks.md).
- **Free Mode and Challenges are fully isolated from Story Mode
  progress.** Neither touches `currentAirport`, visited-airports,
  country/continent completion, or achievements. Keeps the mental
  model simple.
- **Mode-select is a new secondary button next to the Hub's profile
  button** — the primary "book a flight" path into Story Mode is
  unchanged. See [core-loop.md](core-loop.md).
