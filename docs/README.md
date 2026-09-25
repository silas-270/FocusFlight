# Blocktime documentation

The app explained without reading the code. If a change makes something here wrong,
the change is not finished until this is fixed too.

> [!NOTE]
> These docs have been fully scanned and verified against the current codebase structure.

## What this is for

These files exist so that a person or an agent can answer "what happens if I change
this?" before changing it. Every file is scoped to one concern and is meant to be
edited in the same commit as the code it describes.

They describe the app **as it is**, not how it got here. Rationale is included only
where the reason is load-bearing — where not knowing it would make a plausible-looking
change wrong. There is no changelog here; git is the changelog.

## Files

| File | Read it when |
|---|---|
| [architecture.md](architecture.md) | You need the shape of the whole thing: what the app does, its layers, its build, where a given concept lives |
| [engine.md](engine.md) | You are touching CesiumRS, the JNI/JNA bridges, the native build, or map rendering |
| [engine-sound.md](engine-sound.md) | You are touching the synthesised engine noise, the thrust schedule it follows, or anything that reads flight telemetry for audio |
| [navigation.md](navigation.md) | You are adding a screen, changing a route, or threading an argument between screens |
| [core-loop.md](core-loop.md) | You are touching the book → check-in → fly → land session, or the post-landing pipeline |
| [modes.md](modes.md) | You are touching Story/Free/Challenge behaviour, the isolation rules, or the home base |
| [challenges.md](challenges.md) | You are touching challenge types, slots, progress, or the curated catalog |
| [achievements.md](achievements.md) | You are touching achievements, the passport, stats, or tours |
| [state.md](state.md) | You are adding *any* persisted value, or two things disagree about one |

## How to keep them current

The routine is per commit, not per release.

**1. Ask which file the change lands in.** Most changes land in exactly one. A change
that seems to land in three usually means the change is bigger than intended — that is
useful information, not an inconvenience.

**2. Edit the file in the same commit as the code.** A separate "update docs" commit
gets deferred and then skipped. If the diff touches a `.kt` file whose behaviour is
described here, the `.md` edit belongs in the same diff.

**3. Only these changes require a doc edit:**

- A new persisted value, or a new writer of an existing one → [state.md](state.md), always
- A new screen or nav argument → [navigation.md](navigation.md)
- A change to what a mode does or does not write → [modes.md](modes.md)
- A new challenge or achievement *type* (not new catalog entries) → the relevant file
- A new native call across either bridge → [engine.md](engine.md)
- A new invariant, ordering rule, or concurrency guard → wherever it lives, phrased as a rule

Renaming a variable, adding a catalog entry, or restyling a component does not.

**4. Delete rather than annotate.** When something stops being true, remove the
sentence. Do not leave it with a "previously…" note attached; that is what makes
documentation stop being trustworthy, and git already remembers.

**5. Prefer a rule to an inventory.** "Every value has exactly one owning store" stays
true as the app grows. A list of all thirty current values does not, and goes stale
invisibly. Where a list is genuinely needed (the state tables, the nav routes), keep it
short enough that it is obviously incomplete-or-correct rather than plausibly stale.

## Conventions

- Code references use `path/to/File.kt` or `ClassName.method()`, never line numbers.
- Anchors in these files are linked from KDoc across the codebase. Renaming a heading
  breaks those links — `grep -rn "docs/<file>" app/src` before you do.
- Dates are absolute. "Recently" means nothing six months from now.
