# Maps: globe renders and the world map

Apart from the live 3D scene, the app draws the world in two ways: pre-rendered globe images with a
fan of route arcs (the Hub background, the onboarding and home-base previews), and a flat 2D world
map of visited countries (the passport and Flight Search). They share no code, and they solve
different problems. This file covers both.

## Headless globe renders

The Hub's background is a picture of the globe from high above the current airport, with arcs to
some of its destinations. Drawing it live would need the full engine running on a screen the pilot
opens dozens of times a day, and the live engine is already busy being suspended outside flight
screens. So it is rendered **offscreen, once, into a PNG**, through CesiumRS's headless C API, and
displayed as an ordinary image.

### The call

`CesiumHeadlessJnaBindings` declares the C function with JNA:

```
render_routes_headless(width, height, routes: *const HeadlessRoute, count, out_path) -> bool
```

JNA rather than JNI because this is a plain C function over an array of structs, with no JVM object
involved and nothing to call back. `LatLon` and `HeadlessRoute` are JNA `Structure`s whose field
order (`@Structure.FieldOrder`) mirrors the `#[repr(C)]` Rust structs exactly; a mismatch there would
be silent memory corruption, not a compile error.

Rust reads the routes as one contiguous block of `count` structs. A Kotlin array of separately
allocated `Structure`s is not contiguous, so `renderRoutes` allocates the block with
`Structure.toArray(count)`, fills each element, calls `write()` to copy it to native memory, and
passes the **first** element as the pointer. The first route's start is the point the camera frames.

The call blocks for the whole render, so every caller runs it on an IO dispatcher. It always draws
the offline Natural Earth vector map compiled into the library, whatever the pilot's in-flight map
style and whether or not there is a connection, so the Hub, onboarding and settings globes never
need the network and look the same everywhere.

### What gets drawn

`CesiumHeadlessMapRenderer.renderRouteMapForAirport` fetches the airport's bookable outbound routes
and picks which ones become arcs (`selectRoutesToRender`):

- routes up to 10,000 km only;
- at most 12 of those, chosen after a shuffle, so repeated renders of a hub with hundreds of
  destinations do not always show the same subset.

The output size is a 1080 × 1670 reference, which fits the Hub's globe box on a 360 dp-wide phone
exactly, scaled to the display: the width is the screen's shortest side in pixels, clamped between
1,080 and 1,440, and the height keeps the reference aspect. On a denser screen, where the UI is
upscaled ([ui.md](ui.md#the-design-scale)), the globe stays pixel-sharp instead of stretching a
1,080-pixel image; the upper bound keeps a very dense panel from making the render arbitrarily
expensive.

The render leaves empty sky above the horizon, so the Hub crops the image to its box and lifts it by
12% of its height (`GlobeLiftFraction`), which puts the horizon just under the header buttons. That
constant is tied to the render's framing.

The renderer returns a sealed `Result`, `Success(path, fromCache)` or `Failure(message)`, instead of
throwing. Every caller runs it in a background scope whose only sensible reaction to a failure is a
retryable "map unavailable" state, and a route-query failure (which throws
`AirportDataException`) is folded into `Failure` for the same reason.

### Who renders what

| Caller | Airport | `reuseCachedFile` | Why |
|---|---|---|---|
| `HubViewModel` | current airport (or focused challenge's) | true | usually already on disk; re-rendering would delay every visit |
| `AccountViewModel.prepareReturnHome` | home base | true | the ten-second return-home animation is idle time to warm the Hub |
| `OnboardingViewModel` | the airport just selected | false | a fresh home base, rendered while the pilot confirms it |
| `InFlightViewModel` | the destination | false | started at landing, in parallel with the data writes, so the destination's Hub is ready on arrival |

### The cache

Renders are files in the app's cache directory named by `MapImageCache.fileNameFor(iata)`:
`hub_route_map_v2_<IATA>.png`. The version number, `RENDER_VERSION`, changes whenever the look of
the renders changes, and pruning deletes files from any other version outright, so a stale style is
never shown.

With `reuseCachedFile`, an existing non-empty file is used as is and its modification time is
touched, which turns the modification time into a last-used time. After every render or reuse,
`pruneMapCache` keeps the **five** most recently used files and deletes the rest.

The home base is **pinned**: `MapImageCache.pinnedIatas` holds it, and pinned files are kept in
addition to the five rather than inside that budget, so pinning can never squeeze the normal cache
to nothing. The pin exists because the home base is the one render guaranteed to be needed again
and guaranteed to be evicted: return home is on a seven-day cooldown, by which time five other
airports have long since pushed it out, so the one render the pilot waits for after the return-home
animation would otherwise always be cold. `CesiumGameActivity` keeps the pin in sync by collecting
the profile flow, so changing home base moves it. The pin is only a retention hint; losing it costs a
re-render and nothing else.

`MapImageCacheTest` covers versioned names, stale-version deletion and pruning with pins, and
`SelectRoutesToRenderTest` the distance filter and the cap.

## The 2D world map

The passport's travel map and Flight Search's route map are Compose `Canvas` drawings of a vector
world map, with visited countries filled, completed continents outlined, and route arcs on top.

### The artwork

`assets/world-map.svg` is "Simple World Map" by Al MacDonald, edited by Fritz Lekschas (CC BY-SA
3.0): 784 × 459 units, one `<path>` or `<g>` group per country, each identified by its lowercase ISO
3166-1 code (or an underscore-prefixed name where no code exists). `WorldMapParser` pulls those into
`CountryPath`s, one list of Compose `Path`s per uppercased code, using `PathParser` on each path's
`d` attribute. That uppercased code is exactly what `VisitedGeography` reports, so colouring a
country is a set lookup. The parse runs once at start-up on `Dispatchers.Default`, since it is
CPU-bound, and the result is cached for the process.

### Placing an airport on it

Drawing an arc needs airport coordinates in the artwork's space, and the artwork is a simplified,
hand-edited map that matches no textbook projection exactly. `WorldMapProjection` handles it in two
stages:

1. **Winkel Tripel**, centred on a central meridian of 5.66°E, which is the standard projection the
   artwork is closest to. Longitude is deliberately not wrapped to ±180° after the shift: the
   artwork's left and right edges sit at the antimeridian, and a point just past it has to stay on
   its own side.
2. **A cubic polynomial correction**, `svg = Σ cᵢⱼ · xⁱ · yʲ` over all terms up to degree three, with
   separate coefficients for the SVG's x and y. It absorbs the artwork's drift from the projection.
   The coefficients were fitted so that the airports in `flights.db` land inside their own
   country's outline.

Measured against the artwork, over the 3,995 airports whose country has an outline in it, two thirds
land strictly inside their country, the median distance from the country is zero, and 90% are within
2.4 SVG units of it. The misses are mostly coastal airports falling just off a simplified coastline.
`WorldMapProjectionTest` pins five reference airports (LHR, SIN, LAX, SYD, GRU).

### Drawing it quickly

The map is about a thousand country sub-paths, and drawing each one separately, for fills and again
for borders, is a thousand-odd immediate Skia calls on the composition thread. So the paths are
merged by what they look like into five `android.graphics.Path`s (visited fill, unvisited fill,
visited border, unvisited border, completed-continent border) and each is drawn in one call.

The merge depends only on the paths, the visited set, the country → continent map and the completed
continents, which change only when the pilot visits somewhere new. `MergedMapPathsCache` memoises one
entry keyed on those inputs, outside the composition, because both screens that draw the map own a
ViewModel destroyed on every pop and a `remember` would not survive a reopen. One entry is enough:
the passport and Flight Search draw the same visited set.

The canvas has its own graphics layer, so its display list is recorded once and only repositioned
while the passport scrolls.

### Arcs and framing

Routes are quadratic Bézier curves whose control point sits above the midpoint by 18% of the arc's
chord length, so every arc has the same shape whatever its length; a fixed lift would turn short hops
into loops much larger than the route itself.

In Flight Search the map zooms onto the origin and the selected route instead of always showing the
whole world, where a short hop is a few pixels. The framing box covers both airports and the arc's
peak (halfway to the control point), padded by 60% of its span and at least 12 units, and the zoom is
limited to 1× to 14×. The centre is clamped so the view never shows empty space past the artwork's
edge, and centre and zoom animate over 450 ms between selections. Every stroke width and marker radius
is divided by the total scale, so lines keep the same on-screen thickness at any zoom. A dot travels
along the selected arc on a 2.5-second loop.
