# FocusFlight — Construction Plan

> A focus timer disguised as a real-time flight experience.  
> *"Earn your miles."*

---

## 1. Project Inventory

### Current State

| Item | Detail |
|------|--------|
| **Package** | `com.example.focusflight` |
| **Min SDK** | 26 (Android 8.0) |
| **UI Framework** | Jetpack Compose + Material 3 |
| **3D Engine** | CesiumRS — native Rust/wgpu library loaded via `GameActivity` (Disabled for emulator/UI development) |
| **Entry Point** | [CesiumActivity.kt](file:///c:/Users/kamme/AndroidStudioProjects/FocusFlight/app/src/main/java/com/example/focusflight/CesiumActivity.kt) — changed to `ComponentActivity` for UI development |
| **Build** | Cargo NDK cross-compiles CesiumRS for `arm64-v8a` + `x86_64`, copies `.so` into `jniLibs/` (Disabled for emulator/UI development) |

### Database — `flights.db`

Pre-built SQLite database. **This is the single source of truth for all flight/airport data.**

| Table | Rows | Key Columns |
|-------|------|-------------|
| `airports` | 4,170 | `iata_code`, `name`, `lat`, `lon`, `elevation_ft`, `municipality`, `iso_country`, `continent`, `type` |
| `routes` | 57,562 | `origin_iata`, `dest_iata`, `distance_km`, `flight_time_min`, `carriers` |
| `runways` | 5,670 | `airport_id`, `length_ft`, `width_ft`, `surface`, `le_heading`/`he_heading`, endpoint lat/lon |

**Notable stats:**
- 3,474 airports serve as origins (have at least one outbound route)
- Flight times range from **1 min to 1,150 min (~19h)**
- Average flight time: **~186 min (~3h)**
- STR (Stuttgart) has **120 outbound routes**

**Final location:** `app/src/main/assets/flights.db` — shipped as a bundled asset, opened read-only via Room or raw SQLiteDatabase.

---

## 2. Brand Identity

### Design Philosophy
**Minimalist aviation.** Clean, spacious layouts. Generous whitespace. No visual clutter. The 3D globe is the hero element — everything else stays out of the way. Think instrument panel clarity: every element has a purpose, nothing decorative.

### Color Palette

| Role | Name | Hex | Usage |
|------|------|-----|-------|
| **Background** | Midnight | `#0A0E1A` | Primary backgrounds, system bars |
| **Surface** | Deep Navy | `#121829` | Cards, sheets, elevated surfaces |
| **Surface Elevated** | Slate | `#1C2333` | Bottom sheets, dialogs |
| **Accent** | Amber | `#F5A623` | Primary CTA buttons, active states, highlights |
| **Accent Muted** | Soft Amber | `#F5A62340` | Accent backgrounds, subtle highlights (25% opacity) |
| **Text Primary** | Off White | `#ECEEF3` | Headlines, primary body text |
| **Text Secondary** | Haze | `#6B7280` | Subtitles, labels, secondary info |
| **Text Tertiary** | Dim | `#3D4555` | Disabled text, faint labels |
| **Success** | Green | `#34D399` | Completed flights, landing confirmation |
| **Divider** | Border | `#1E2636` | Subtle dividers, card borders |

### Typography
- **Display / Headlines**: *Space Grotesk* — bold, wide-tracked (airport signage feel)
- **Body / UI**: *Inter* — clean, highly legible at all sizes
- **Monospace accents**: *JetBrains Mono* — IATA codes, flight numbers, timers, coordinates

### Design Language
- **No glassmorphism, no gradients, no shadows.** Surfaces are distinguished by flat color steps only.
- Boarding pass motifs: dashed separator lines (`─ ─ ─`), tear-off sections
- Monospaced data fields for codes and numbers
- Generous padding (16-24dp standard)
- Rounded corners: 12dp for cards, 24dp for buttons, 28dp for bottom sheet handles
- Subtle fade transitions between screens (no bouncy animations)
- Amber accent used sparingly — only for the single most important action on each screen

---

## 3. App Structure — Screen by Screen

### Screen 0 · Splash

A minimal launch screen. App logo centered on `#0A0E1A` background. Lasts ~1s while the app initializes. No interaction.

---

### Screen 1 · Onboarding (First Launch Only)

> *Pick your home airport. That's it.*

Simple, one-screen onboarding. No carousel.

**Layout:**
```
┌──────────────────────────────────┐
│                                  │
│        (app logo / icon)         │
│                                  │
│         FOCUSFLIGHT              │
│      "Earn your miles."          │
│                                  │
│                                  │
│   Where do you fly from?         │
│  ┌────────────────────────────┐  │
│  │  🔍  Search airport...     │  │
│  └────────────────────────────┘  │
│                                  │
│  (search results appear here    │
│   as a flat list of matches)    │
│                                  │
│                                  │
│  ┌────────────────────────────┐  │
│  │     SET HOME AIRPORT       │  │
│  └────────────────────────────┘  │
│                                  │
└──────────────────────────────────┘
```

**Behavior:**
- Search queries both `iata_code` and `municipality` + `name` from `airports` table
- Only airports with at least one outbound route in `routes` are selectable
- CTA disabled until an airport is selected
- On confirm → save home airport to local preferences, navigate to Airport Hub

---

### Screen 2 · Airport Hub (Home Screen)

> Your current base. The main screen you return to after every flight.

**Layout:**
```
┌──────────────────────────────────────────┐
│                                          │
│  ┌──────────────────────────────────┐    │
│  │                                  │    │
│  │     CesiumRS Globe View          │    │
│  │     (top ~55% of screen)         │    │
│  │                                  │    │
│  │  • Centered on current airport   │    │
│  │  • Amber polyline arcs to all    │    │
│  │    connected destinations        │    │
│  │  • Slow auto-rotation            │    │
│  │                                  │    │
│  └──────────────────────────────────┘    │
│                                          │
│  ── Bottom Sheet (draggable) ──────────  │
│                                          │
│   YOUR BASE                              │
│   STR  ·  Stuttgart Airport              │
│   ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─   │
│   12 flights  ·  47.3h  ·  14 airports   │
│                                          │
│   ┌──────────────────────────────────┐   │
│   │        ✈  BOOK A FLIGHT          │   │
│   └──────────────────────────────────┘   │
│                                          │
│  (expanded: recent flights, passport     │
│   link, settings)                        │
│                                          │
└──────────────────────────────────────────┘
```

**Bottom Sheet — Collapsed (default):**
- Airport label, IATA code, name
- One-line stats: total flights · total hours · airports visited
- Amber CTA: **"Book a Flight"**

**Bottom Sheet — Expanded (drag up):**
- Same header content
- **Recent Flights** — last 3 completed flights as compact cards
- **View Passport** button → navigates to Passport screen
- **Settings** gear icon → navigates to Settings

---

### Screen 3 · Flight Search

> Pick your destination. Classic, clean flight selector.

**Layout:**
```
┌──────────────────────────────────────────┐
│  ← Back                                  │
│                                          │
│  ┌──────────────────────────────────┐    │
│  │  CesiumRS Globe (top portion)    │    │
│  │  Zoomed to show origin +         │    │
│  │  all connections as arcs         │    │
│  │  Selected route highlighted      │    │
│  └──────────────────────────────────┘    │
│                                          │
│  ── Bottom Sheet ──────────────────────  │
│                                          │
│   FROM                                   │
│   STR — Stuttgart          (locked)      │
│                                          │
│   TO                                     │
│   ┌────────────────────────────────┐     │
│   │  🔍  Search destination...     │     │
│   └────────────────────────────────┘     │
│                                          │
│   ── Or browse by ──                     │
│   Popular  ·  Shortest  ·  Longest       │
│                                          │
│   (Filtered list of destinations:)       │
│   ┌────────────────────────────────┐     │
│   │ JFK  New York     8h 45m  6378km│    │
│   │ FRA  Frankfurt    0h 50m   157km│    │
│   │ AMS  Amsterdam    1h 25m   510km│    │
│   │ PMI  Palma        2h 00m  1139km│    │
│   └────────────────────────────────┘     │
│                                          │
└──────────────────────────────────────────┘
```

**Behavior:**
- FROM is always locked to your current base airport
- TO search field queries `routes` joined with `airports` for the current origin
- Quick filter tabs: Popular (most carriers), Shortest, Longest
- Each destination row shows: IATA, city name, flight time, distance
- Tapping a destination → globe animates the arc highlight → transition to Check-In

---

### Screen 4 · Check-In / Boarding Pass

> The point of no return. Confirm and start your focus session.

**Layout — full screen, no globe:**
```
╔════════════════════════════════════════════╗
║                                            ║
║   FOCUSFLIGHT                BOARDING PASS  ║
║                                            ║
║   PASSENGER                                ║
║   You                                      ║
║   ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─  ║
║   FROM             TO                      ║
║   STR              JFK                     ║
║   Stuttgart        New York                ║
║   ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─  ║
║   DURATION         DISTANCE                ║
║   8h 45m           6,378 km                ║
║   ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─  ║
║   FLIGHT           DATE                    ║
║   FF-2847          10 JUL 2026             ║
║                                            ║
║          ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓              ║
║                                            ║
║   ┌────────────────────────────────────┐   ║
║   │         ✈  START FLIGHT            │   ║
║   └────────────────────────────────────┘   ║
║                                            ║
║              Cancel Booking                ║
║                                            ║
╚════════════════════════════════════════════╝
```

**Details:**
- Flight number `FF-XXXX` is auto-generated (random 4-digit, or sequential)
- Date is today's date
- All text uses monospace for the data fields
- Amber CTA: **"Start Flight"** — tapping this starts the timer and transitions to In-Flight
- "Cancel Booking" is a subtle text button below
- The whole card is styled like a physical boarding pass: dashed separators, structured layout

---

### Screen 5 · In-Flight Experience (Core Focus Screen)

> Full-screen CesiumRS. You focus. The plane flies.

**Layout:**
```
┌──────────────────────────────────────────┐
│                                     ⚙ │  ← Settings button (top right)
│                                          │
│                                          │
│          CesiumRS Full Screen             │
│          Plane flying along               │
│          great-circle route               │
│                                          │
│                                          │
│                                          │
│                                          │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │            ── drag handle ──       │  │  ← Rounded top-left/top-right corners only
│  │   3:22:14   ·   842 km/h  ·  38%   │  │  ← Flushed to bottom of screen, flat bottom
└──┴────────────────────────────────────┴──┘
```

#### Compact Bottom Info Panel (always visible)
- **Anchored to bottom border**: Goes all the way down to the screen's bottom edge (no bottom padding, no rounded bottom corners).
- **Top Corners Rounded**: Left and right top corners are rounded (e.g. 16dp).
- **Width**: Spans the full width in portrait mode. In horizontal/landscape layout, it may be centered or offset to one side (like Google Maps on a tablet), but still anchors flush to the bottom edge of the screen.
- **Drag Handle**: Has a horizontal grab/drag line ("Apple" style bar line) centered at the top.
- **Metrics**: Shows key stats horizontally: **Time Remaining**, **Speed**, **Progress %**.
- **Minimalist**: Clean, dark background matching the brand's surface colors, completely flat.

#### Settings Button (top right)
- Small, subtle gear/cog icon in the top-right corner
- Opens a **settings overlay/modal** with:
  - Camera mode: Chase · Overhead · Cockpit · Free Look
  - Map surface: Minimal · Satellite
  - Sound: Engine ambience on/off

#### Expanded Info Panel (swipe info bar up)
- The compact panel expands upward into a taller bottom sheet (Google Maps style).
- Shows detailed flight information while remaining anchored to the bottom border:

```
┌──────────────────────────────────────────┐
│                                          │
│          CesiumRS (top portion)           │
│                                          │
├──────────────────────────────────────────┤
│               ── drag handle ──          │
│                                          │
│  STR → JFK                     FF-2847   │
│  ████████████░░░░░░░░  62%               │
│                                          │
│  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─  │
│                                          │
│  TIME LEFT     ELAPSED     TOTAL         │
│  3:22:14       5:22:46     8:45:00       │
│                                          │
│  SPEED         ALTITUDE    DISTANCE LEFT │
│  842 km/h      10,668 m    3,841 km      │
│                                          │
│  POSITION                                │
│  52.31°N  34.12°W                        │
│                                          │
│  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─  │
│  (Mini 2D route map with position dot)   │
│                                          │
└──────────────────────────────────────────┘
```

#### Timer Behavior
- **Timer pauses when the app goes to background.** This is the core focus enforcement.
- Wake lock keeps the screen on during the flight
- On flight start, suggest enabling Do Not Disturb (system prompt)

---

### Screen 6 · Landing / Arrival

> Congratulations screen. Quick, satisfying, minimal.

**Layout:**
```
┌──────────────────────────────────────────┐
│                                          │
│                                          │
│              🛬  LANDED                  │
│                                          │
│      Welcome to New York (JFK)           │
│                                          │
│      ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─      │
│                                          │
│      Focus Time     8h 45m               │
│      Distance       6,378 km             │
│                                          │
│      ┌──────────────────────────┐        │
│      │   NEW AIRPORT UNLOCKED  │        │
│      └──────────────────────────┘        │
│                                          │
│                                          │
│   [ View Passport ]      [ Home ]        │
│                                          │
└──────────────────────────────────────────┘
```

**Behavior:**
- Shown after timer reaches zero (and landing animation completes on CesiumRS, if implemented)
- Subtle confetti or particle effect — brief, not over the top
- User's base airport is now updated to the destination
- Two actions: view passport (flight log) or go home (Airport Hub with new base)

---

### Screen 7 · Passport / Flight Log

> Your flight history.

**Layout:**
```
┌──────────────────────────────────────────┐
│  ← Back                       PASSPORT   │
│                                          │
│  ┌──────────────────────────────────┐    │
│  │  Total Flights: 12               │    │
│  │  Total Hours:   47.3h            │    │
│  │  Airports:      14               │    │
│  │  Distance:      38,420 km        │    │
│  └──────────────────────────────────┘    │
│                                          │
│  ── Flight Log ──                        │
│                                          │
│  ┌──────────────────────────────────┐    │
│  │  FF-2847  ·  10 Jul 2026         │    │
│  │  STR ───── ✈ ────── JFK          │    │
│  │  8h 45m  ·  6,378 km             │    │
│  └──────────────────────────────────┘    │
│                                          │
│  ┌──────────────────────────────────┐    │
│  │  FF-1203  ·  8 Jul 2026          │    │
│  │  FRA ───── ✈ ────── STR          │    │
│  │  0h 50m  ·  157 km               │    │
│  └──────────────────────────────────┘    │
│                                          │
│  (scrollable list continues...)          │
│                                          │
└──────────────────────────────────────────┘
```

**Data stored locally** (Room database or SharedPreferences):
- Flight number, date, origin, destination, duration, distance
- Listed in reverse chronological order

---

## 5. Data Architecture (MVP)

### Shipped Assets
- `flights.db` → `app/src/main/assets/flights.db` (read-only, copied to app internal storage on first launch)

### Local Persistence (Room or SharedPreferences)
- **User state**: current base airport IATA, onboarding completed flag
- **Flight log**: completed flights table (flight_number, date, origin_iata, dest_iata, duration_min, distance_km)
- **Settings**: map style, camera mode, sound preference

### No Backend / No Network (MVP)
Everything runs offline. The database is self-contained. CesiumRS tile loading is the only network dependency.

---

## 6. Build Order

Build screen-by-screen, each one fully working before moving to the next.

| Phase | Screen | Depends On |
|-------|--------|------------|
| **1** | Onboarding | Database access (Room/SQLite), basic Compose navigation |
| **2** | Airport Hub | Phase 1 + CesiumRS integration (globe with arcs), bottom sheet |
| **3** | Flight Search | Phase 2 + destination query, search/filter logic |
| **4** | Check-In | Phase 3 + boarding pass layout, timer preparation |
| **5** | In-Flight | Phase 4 + full CesiumRS flight rendering, timer logic, wake lock, info bar |
| **6** | Landing | Phase 5 + arrival screen, base airport update |
| **7** | Passport | Phase 6 + flight log persistence, history list |
| **Polish** | Settings, transitions, sound | All phases |

---

## 7. Open Questions

> [!IMPORTANT]
> **Navigation architecture:** Single Activity with Compose navigation, or multiple Activities? The CesiumRS `GameActivity` currently takes over the full window. We'll likely need a **Compose Activity** for all UI screens and a separate **CesiumActivity** for the in-flight 3D rendering. The transition between them needs careful handling.

> [!NOTE]
> **Compose overlay on CesiumRS:** For the in-flight info bar and settings button, we need to render Compose UI *on top of* the native CesiumRS surface. This may require a `SurfaceView`/`TextureView` approach with a Compose overlay, or communication between the two layers. This is an implementation detail we'll solve when we get to Phase 5.

> [!NOTE]
> **Flight number generation:** Currently planned as random `FF-XXXX`. Alternative: sequential per user (FF-0001, FF-0002, ...) for a nicer passport/log. Which do you prefer?
