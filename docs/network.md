# Network use and offline mode

Blocktime has no server and no account. It makes exactly two kinds of outbound request, both
optional, and the app is fully usable without either. This file lists them, explains how the app
decides it is offline, and what changes when it is.

## What goes out

| Request | From | When | Without it |
|---|---|---|---|
| Map tiles | CesiumRS, inside the native library | In-Flight, with the Standard or Satellite style | the globe shows the built-in offline map |
| One destination photo | `PexelsDestinationPhotoRepository` | during a flight, once per flight | the arrival screen has a plain background |

Everything else is local: the airport and route data are bundled, the headless globes are drawn
from the vector map in the library, and the 2D world map is an asset.

## Map tiles

The Standard style streams CARTO's dark basemap; the Satellite style streams Esri World Imagery and
Terrarium elevation tiles for 3D relief. The Offline style, and every headless render, uses the
Natural Earth vector map compiled into `libcesium_rs.so`, rasterised on the CPU, and needs nothing.

Two of the tile sources take a key, supplied at build time from `local.properties` and read by
CesiumRS at compile time ([engine.md](engine.md#building-the-native-library)). Without
`CARTO_API_KEY`, the dark basemap's tiles come back stamped "API KEY REQUIRED". Without
`ESRI_API_KEY`, the satellite style falls back to Esri's keyless service, which is not licensed for
commercial use.

**Attribution is part of the source.** CARTO and Esri require their credit on the map itself while
their tiles are showing, so In-Flight draws a small, uncoloured, non-clickable credit line in the
bottom-left corner for the two network styles (`CreditsCatalog.CARTO_MAP_CREDIT` and
`ESRI_MAP_CREDIT`), and none for the public-domain offline map. The full list of third-party sources
is `CreditsCatalog`, shown under Settings → Credits & licenses.

## Deciding the app is offline

`OfflineModeController` (`data/network/`) is the single place that decides. Screens read its `mode`
or `isOffline` and never ask the connectivity monitor directly, so both kinds of offline behave the
same way everywhere.

It combines two inputs:

- **`ConnectivityMonitor`**, a default-network callback. The device counts as connected only when
  the network is both `INTERNET`-capable and `VALIDATED`. A captive portal or a Wi-Fi network with
  no uplink is offline, because tile downloads there would hang until they timed out. The value is
  read synchronously in the constructor, so the first frame already shows the right state, and then
  kept current by the callback.
- **The "Offline maps" switch** in Settings, a data saver that forces offline mode on a working
  connection.

```kotlin
when {
    dataSaver  -> OFFLINE_DATA_SAVER
    !connected -> OFFLINE_NO_CONNECTION
    else       -> ONLINE
}
```

Data saver wins over a live connection, because the pilot asked for no downloads. The two offline
states are kept apart only so the UI can say why.

The controller is a process-wide singleton (`getInstance`) on its own coroutine scope, registered
on the application context. It is handed to ViewModels that the Activity's factories create, and one
network callback per process is all it needs; the platform also limits how many a single app may
register.

## What changes while offline

### The live globe

`resolveEffectiveMapStyle(preferred, isOffline)` returns the offline map while offline and the
pilot's preference otherwise. In-Flight pushes that **effective** style to the engine, never the
preference, so the preference is never overwritten: when the connection comes back, the pilot's
chosen style returns on its own.

In the map picker the network styles are dimmed and cannot be picked while offline, and a hint below
it explains why: "Offline maps is on in Settings" for data saver, "No connection" when the offline
map was the preference anyway, or "No connection · MAP returns when back online" naming the style
that will come back.

`networkNoticeFor(previous, current, preferredStyle)` produces a short notice pill when the map
switches on its own: "No connection · offline map" or "Data saver on · offline map" when the app
goes offline, and "Back online · MAP restored" (naming the style) when it comes back.
It returns nothing when the map does not visibly change, that is when offline stays offline (data
saver switched on during an outage) or when the pilot's preference is the offline map already.

### Status indicators

The Hub header shows an OFFLINE badge. In-Flight shows its own smaller status pill, deliberately
shaped as a rounded pill without a click handler rather than as one of the square top-bar buttons
next to it, so it reads as status rather than as a control that does nothing. In scenic mode it
shrinks to a bare icon.

### Destination photos

The photo for the arrival screen is fetched during the flight, well before landing, so the arrival
screen never waits on the network. While the app is offline the prefetch simply waits for the
controller to report online, instead of spending its timeout against a network that is not there.
If the app comes online during the flight the photo is fetched then; if not, the arrival screen uses
its plain background.

## Destination photos

`PexelsDestinationPhotoRepository` finds one photo of the destination city through the Pexels search
API, with the key from `BuildConfig.PEXELS_API_KEY`. A blank key or a blank city returns nothing at
once.

**Queries cascade from specific to general**: "*city* skyline", "*city* City", "*city* landmark",
"*city* *country code*", and finally the bare city name. Each query asks for five results, and the
first query that yields a usable photo wins. Within a result, a photo whose description (`alt`)
mentions the city is preferred over the first result, which filters out generic stock images that
merely match a keyword. The image URL prefers `large2x`, then `original`, `large` and `portrait`.

**Every failure is the same answer.** No result, a bad key, no network, a timeout and an unexpected
response all return null, so callers never need their own error handling and a missing photo always
means the plain background. Each request has 8-second connect and read timeouts, and the whole
cascade is bounded to 12 seconds; the in-flight request is run interruptibly so that the overall
timeout can abort it rather than only skip the queries not yet started. Without that bound, a city
whose first queries all come back empty could chain several timeouts into most of a minute.

The photo, its photographer and its Pexels page travel to the arrival screen through
`DestinationPhotoChannel` ([navigation.md](navigation.md#what-travels-outside-the-route)). Pexels'
guidelines require crediting the photographer, so the arrival screen shows "Photo by *name* on
Pexels" above CONTINUE, linking to the photo's page, or "Photo from Pexels" when the response named
no one. There is no caching layer: one photo per flight is fetched once and forgotten.

The query building and response parsing are separate internal functions so that
`PexelsDestinationPhotoRepositoryTest` can check them without a network.
