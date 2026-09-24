# Architecture

## What FocusFlight is

A focus timer shaped like a flight simulator. The pilot books a real scheduled route
between two real airports, and the flight's duration *is* the study session. While the
timer runs, a native 3D globe renders the aircraft travelling the actual great-circle
path. On landing, the flight is written to a logbook, the pilot's position moves to the
destination, and everything derived from that history — visited countries, achievements,
challenge progress — updates.

Two properties are load-bearing and constrain most design decisions:

1. **It is a study tool first, a game second.** Any mechanic that adds friction before a
   session starts has to justify itself against that. The one-tap "start a session" path
   must not get harder to reach.
2. **Everything real-world stays real-world.** The bundled database contains only
   actual scheduled routes between actual airports. There are no fictional routes or
   airports — in normal play the puzzle is *finding* a real path across the world.

   Authored itineraries do exist, in `PredefinedRouteCatalog`, but they do not weaken
   this: every leg must be a real route present in `flights.db`, and
   `PredefinedRouteCatalogTest` checks each one leg by leg. That test is what stands
   between an authoring typo and a challenge nobody can fly.

Single-player, with no account, no sync, and no server. The network is only used for
live-globe map tiles and one optional destination-photo fetch, and the app works fully
without it (see [Network](#network)).

## Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Presentation | ViewModels with `StateFlow`, manual `ViewModelProvider.Factory` per screen |
| Persistence | Room (`user_data.db`, mutable) + a bundled read-only SQLite asset (`flights.db`) + `SharedPreferences` |
| 3D engine | CesiumRS — an out-of-tree Rust/wgpu renderer, linked as `libcesium_rs.so` |
| Native bridge | JNI for the live engine, JNA for the headless renderer |
| Host activity | `GameActivity` (androidx.games), not `ComponentActivity` |
| Build | Gradle KTS, KSP for Room, `cargo-ndk` for the Rust library |

`minSdk` 26, `targetSdk` 36, `arm64-v8a` only (the sole ABI the native library is built
for — see [engine.md](engine.md)).

There is no dependency-injection framework. Every repository is constructed once in
`CesiumGameActivity.onCreate()` and passed down explicitly through ViewModel factories.
This is deliberate: the graph is small enough to read in one screen, and the construction
order is itself meaningful (see [state.md](state.md)'s ordering invariants).

## Layers

```
CesiumGameActivity          composition root · NavHost · engine lifecycle
        │
        ├── ui/screens/     Compose screens, one package per destination
        ├── ui/components/  cross-screen reusable composables
        ├── ui/viewmodel/   one ViewModel per screen, StateFlow-exposed
        │
        ├── domain/         pure orchestration over repositories, no Android deps
        │
        ├── audio/          real-time engine-sound synthesis — see [engine-sound.md](engine-sound.md)
        │
        ├── data/repository/  interface + Local* implementation per concern
        ├── data/model/       entities, enums, catalogs, pure progress math
        ├── data/local/       Room DAOs, migrations, the flights.db data source
        │
        └── engine/live/      JNI bridge to the running 3D engine
            engine/headless/  JNA bridge to the offscreen map renderer
```

The dependency direction is strictly downward. `data/model` in particular has no
Android, Room-behaviour, or JNI dependency in its pure parts (`ChallengeProgress`,
`AchievementProgress`, `Tour`, `HomeBaseCooldown`), which is what makes those
JVM-unit-testable without a device.

**Repositories are interfaces with a single `Local*` implementation.** The interface is
not there for a future remote backend — it is the seam that lets JVM tests substitute a
fake without Room or the NDK. Where a piece of logic needed to be testable but lived
inside a ViewModel that loads the native engine on touch, it was pulled out into a
standalone function instead: `processLandingForChallenges`, `resolveLandingOutcome`,
`resolveNextLeg`, `loadRouteContext`.

## Where each concept lives

| Concept | Anchor |
|---|---|
| App entry, nav graph, repository construction | `CesiumGameActivity.kt` |
| Route definitions and argument encoding | `ui/Screen.kt` — see [navigation.md](navigation.md) |
| A completed flight | `data/model/FlightLog.kt` — the only durable record a flight happened |
| Session mode tag | `data/model/FlightMode.kt` — see [modes.md](modes.md) |
| Landing pipeline | `InFlightViewModel.completeFlight()` — see [core-loop.md](core-loop.md) |
| Challenge instances and progress | `data/repository/LocalChallengeRepository.kt`, `data/model/ChallengeProgress.kt` — see [challenges.md](challenges.md) |
| Achievement evaluation | `data/model/AchievementProgress.kt` — see [achievements.md](achievements.md) |
| Everything derived from history | `data/repository/PilotProgressRepository.kt` |
| Persisted-value ownership | [state.md](state.md) |
| Native engine | `engine/live/`, `engine/headless/` — see [engine.md](engine.md) |
| Engine sound | `domain/EnginePowerModel.kt` (telemetry → fan speed), `audio/` (fan speed → PCM) — see [engine-sound.md](engine-sound.md) |

## The two databases

They are unrelated and must not be confused.

**`flights.db`** — bundled in `assets/`, copied to the databases directory on first run
by `AirportRepository.ensureDatabaseCopied()`, then opened read-only through
`AirportRouteSqliteDataSource`. It is reference data, not user data.

| Table | Rows | Contents |
|---|---|---|
| `airports` | 4,170 | IATA/ICAO, name, lat/lon, elevation, continent, ISO country/region, municipality |
| `routes` | 57,570 | origin/dest IATA, distance km, flight time min, carriers |
| `runways` | 5,670 | per-airport length, width, both ends' heading and lat/lon |

Nothing writes it. It is replaced wholesale by shipping a new asset. Because it is
immutable within a build, several process-level caches on top of it are safe — see
[state.md](state.md)'s cache table.

**`user_data.db`** — Room, version 9, `exportSchema = true`. Four entities:
`user_profile`, `flight_log`, `challenges`, `achievement_unlocks`.

Migrations are explicit and required. The destructive fallback that used to be here
silently deleted the whole logbook on any schema bump; it is gone. Every version bump
needs a `Migration` object in `AppDatabase.kt` and a committed
`app/schemas/<db>/<version>.json`. Never delete an old version's JSON — a migration can
only be tested against the schema it migrates *from*, and `MigrationTest` is what stops
a broken migration reaching a device.

## Assets

| Asset | Used by |
|---|---|
| `flights.db` | `AirportRouteSqliteDataSource` (above) |
| `world-map.svg` | `WorldMapParser` → `InteractiveWorldMap`, the passport's visited-country map |
| `Boeing787Cockpit.glb` | CesiumRS, cockpit camera mode |

## Network

Two things go outbound:

- **Live-globe tiles.** The in-flight map styles Standard (CARTO) and Satellite + Terrain
  (Esri imagery, Terrarium heights) stream tiles from inside CesiumRS. The Offline style
  and every headless render use the vector map built into the `.so` and need nothing.
- **Destination photo.** `PexelsDestinationPhotoRepository` fetches a photo of the
  destination city for the arrival screen. The API key comes from `local.properties` via a
  `buildConfigField`, so it never enters the repo. A failure is silent and the arrival
  screen renders without a photo.

**Offline mode.** `OfflineModeController` (`data/network/`) is the single source of truth.
It combines two inputs:

- `ConnectivityMonitor`: a default-network callback. The device counts as connected only
  when the network is INTERNET-capable *and* VALIDATED.
- The **Offline maps** switch in Settings (`PreferencesRepository.isOfflineDataSaverEnabled`),
  which forces offline mode to save data.

The result is a `NetworkMode`: `ONLINE`, `OFFLINE_NO_CONNECTION` or `OFFLINE_DATA_SAVER`.
The controller is process-wide (`getInstance`), because ViewModels can outlive a recreated
Activity. While offline:

- the Hub header and the in-flight HUD show an OFFLINE badge (`OfflineBadge`);
- the live globe switches to the Offline style, and the network styles are locked in the
  map picker. The pilot's stored style is kept and restored when the app is back online,
  with a short notice pill in both directions;
- the destination-photo prefetch waits for the app to be online instead of timing out.

## Build

```
./gradlew :app:assembleDebug         # also runs cargoNdkBuild via preBuild
./gradlew test                       # JVM unit tests
./gradlew connectedAndroidTest       # instrumented: MigrationTest, catalog tests
```

`preBuild` depends on `cargoNdkBuild`, which cross-compiles CesiumRS and copies the
resulting `.so` into `jniLibs/arm64-v8a/`. It needs a CesiumRS checkout — see
[engine.md](engine.md) for the environment variables and the profiling build.

Machine-local configuration lives in `local.properties` (gitignored): SDK paths and
`PEXELS_API_KEY`.

## Testing

JVM unit tests cover the pure layers: progress math, tour segmentation, cooldowns,
sorting, achievement evaluation, and the two seams pulled out of ViewModels for exactly
this purpose. Two tests are load-bearing rather than incidental and should not be
weakened:

- `ChallengeRowConcurrentWriteTest` pins the concurrent-writer rules in
  [state.md](state.md). Its fake DAO models Room's real write granularity, so it cannot
  pass against a DAO that could not exist.
- `MigrationTest` (instrumented) validates every migration against its committed schema
  JSON.
- `EngineSoundSynthTest` renders the synthesised engine audio and measures it, so its
  spectral shape is a check rather than a listening session. Two of its assertions pin
  that shape and should not be weakened — see [engine-sound.md](engine-sound.md).

The same seam argument applies to audio: `EngineSoundSynth` is split from
`EngineSoundEngine` precisely so the DSP carries no Android type and can be rendered on the
JVM, leaving only the `AudioTrack` and its thread untested.

`InFlightViewModel` has no unit test — it loads the native engine on first touch. That
is why its concurrency rule lives in `SessionPausedFlightStore`, a JVM-testable seam,
rather than only in a flag inside the ViewModel.

## Known scaffolding

`DEV_FEATURES_TO_REVERT.md` tracks temporary shortcuts that must be undone before
release (disabled cooldowns, the in-flight skip button). While the cooldown override is
in place, two assertions in `HomeBaseCooldownTest` fail by design — `./gradlew test` is
red, and reverting the override turns it green.
