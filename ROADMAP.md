# FocusFlight — Development Roadmap

This document outlines the step-by-step implementation roadmap for building FocusFlight. It references the screen details and requirements defined in the [CONSTRUCTION_PLAN.md](file:///c:/Users/kamme/AndroidStudioProjects/FocusFlight/CONSTRUCTION_PLAN.md).

---

## 🗺️ Architectural Dependencies & Order of Work

To minimize refactoring, we build from the core data layer upward to the presentation layer, followed by navigation:

```
  ┌──────────────────────────────────────────────────────────┐
  │ 1. Database & Assets (Setup SQLite access & Room Models)  │
  └────────────────────────────┬─────────────────────────────┘
                               ▼
  ┌──────────────────────────────────────────────────────────┐
  │ 2. Navigation & Shell (Compose NavHost & Theme Setup)     │
  └────────────────────────────┬─────────────────────────────┘
                               ▼
  ┌──────────────────────────────────────────────────────────┐
  │ 3. Onboarding & State (Home Airport setup & user profile)│
  └────────────────────────────┬─────────────────────────────┘
                               ▼
  ┌──────────────────────────────────────────────────────────┐
  │ 4. Main Screens (Hub -> Search -> Boarding Pass)         │
  └────────────────────────────┬─────────────────────────────┘
                               ▼
  ┌──────────────────────────────────────────────────────────┐
  │ 5. In-Flight Focus Session (Timer + Mock Map Render)      │
  └────────────────────────────┬─────────────────────────────┘
                               ▼
  ┌──────────────────────────────────────────────────────────┐
  │ 6. Landing & Flight Log (Passport save & statistics)     │
  └──────────────────────────────────────────────────────────┘
```

---

## 🏁 Phase 1: Data Infrastructure & Core Navigation — ✅ Completed

### Task 1.1: Database Asset Copy Helper — ✅ Completed
*   **Description**: Write a utility to copy `flights.db` from assets to internal database storage on first run.
*   **Details**: Read SQLite file dynamically, check existence, and ensure it isn't copied on subsequent launches.
*   **Verification**: Check that the database file exists in `/data/data/com.example.focusflight/databases/` using standard checks.

### Task 1.2: Room/SQLite Data Models & DAOs — ✅ Completed
*   **Description**: Create Room entity representations or SQL queries to read tables.
*   **References**: [CONSTRUCTION_PLAN.md - Section 1 (flights.db)](file:///c:/Users/kamme/AndroidStudioProjects/FocusFlight/CONSTRUCTION_PLAN.md#1-project-inventory)
*   **Files**:
    *   `data/model/AirportEntity.kt`
    *   `data/model/RouteEntity.kt`
    *   `data/model/FlightLogEntity.kt` (New table to log completed sessions)
*   **DAO Functions**:
    *   `getAirportByIata(iata: String)`
    *   `searchAirports(query: String)` (Matches IATA, city name, or airport name)
    *   `getOutboundRoutes(originIata: String)`

### Task 1.3: Navigation Setup & State Management — ✅ Completed
*   **Description**: Define the Navigation routes enum and set up `NavHost` in `CesiumActivity`.
*   **References**: [CONSTRUCTION_PLAN.md - Section 4 (Screen Flow)](file:///c:/Users/kamme/AndroidStudioProjects/FocusFlight/CONSTRUCTION_PLAN.md#4-screen-flow)
*   **Routes**:
    *   `Onboarding`
    *   `Hub`
    *   `FlightSearch`
    *   `CheckIn`
    *   `InFlight`
    *   `Landing`
    *   `Passport`

---

## 🛫 Phase 2: Onboarding & First-Launch Experience — ✅ Completed

### Task 2.1: Local Preferences Storage — ✅ Completed
*   **Description**: Implement a repository to store user preferences (home airport IATA, onboarding complete flag, current base airport).
*   **Files**: `data/repository/PreferencesRepository.kt`

### Task 2.2: Onboarding Screen — ✅ Completed
*   **Description**: Implement the search and selection of the home base airport.
*   **References**: [CONSTRUCTION_PLAN.md - Screen 1 · Onboarding](file:///c:/Users/kamme/AndroidStudioProjects/FocusFlight/CONSTRUCTION_PLAN.md#screen-1--onboarding-first-launch-only)
*   **UX Flow**: Type in search bar -> query database -> select airport -> save to preferences -> navigate to Hub.

---

## 🏠 Phase 3: Airport Hub (The Main Base) — ✅ Completed

### Task 3.1: Hub Screen Layout — ✅ Completed
*   **Description**: Build the Main Hub Screen interface.
*   **References**: [CONSTRUCTION_PLAN.md - Screen 2 · Airport Hub](file:///c:/Users/kamme/AndroidStudioProjects/FocusFlight/CONSTRUCTION_PLAN.md#screen-2--airport-hub-home-screen)
*   **UI Specs**:
    *   Top portion: Flat dark minimalist visual map placeholder showing current base.
    *   Bottom portion: Draggable bottom sheet anchored to the bottom edge with rounded top corners.
    *   Display total flight hours, flight counts, and the primary "Book a Flight" button.

---

## 🔍 Phase 4: Route Selection & Boarding Pass — ✅ Completed

### Task 4.1: Flight Search Screen — ✅ Completed
*   **Description**: Browse and search outbound connections from the user's current base airport.
*   **References**: [CONSTRUCTION_PLAN.md - Screen 3 · Flight Search](file:///c:/Users/kamme/AndroidStudioProjects/FocusFlight/CONSTRUCTION_PLAN.md#screen-3--flight-search)
*   **UI Specs**:
    *   Outbound connections queried directly from `routes` table where `origin_iata` matches current base.
    *   Filters: Shortest flight time, longest flight time, popular (most carriers).

### Task 4.2: Check-In & Boarding Pass Card — ✅ Completed
*   **Description**: Display the summary ticket before flying.
*   **References**: [CONSTRUCTION_PLAN.md - Screen 4 · Check-In](file:///c:/Users/kamme/AndroidStudioProjects/FocusFlight/CONSTRUCTION_PLAN.md#screen-4--check-in--boarding-pass)
*   **UI Specs**:
    *   Ticket-like layout with monospaced text fields for flight details, distance, IATA codes, and date.
    *   Action: "Start Flight" button.

---

## ⏳ Phase 5: In-Flight Focus Experience

### Task 5.1: Strict Focus Timer Logic
*   **Description**: Implement a timer that counts down in seconds, retaining state, and strictly pauses if the app enters the background.
*   **Details**: Listen to lifecycle events (`onStop`/`onStart`) to pause/resume the countdown.

### Task 5.2: In-Flight UI (HUD & Bottom Sheet)
*   **Description**: Create the screen containing the plane status HUD.
*   **References**: [CONSTRUCTION_PLAN.md - Screen 5 · In-Flight](file:///c:/Users/kamme/AndroidStudioProjects/FocusFlight/CONSTRUCTION_PLAN.md#screen-5--in-flight-experience-core-focus-screen)
*   **UI Specs**:
    *   Full-screen mock background showing an animated placeholder flight progress bar.
    *   Top right: Settings button.
    *   Bottom: Docked info panel (drag handle, flat bottom, rounded top corners) showing time left, speed, progress. Swiping up expands it to show full details.

---

## 🛬 Phase 6: Landing & Log History

### Task 6.1: Arrival Screen
*   **Description**: Celebratory landing card on countdown completion.
*   **References**: [CONSTRUCTION_PLAN.md - Screen 6 · Landing](file:///c:/Users/kamme/AndroidStudioProjects/FocusFlight/CONSTRUCTION_PLAN.md#screen-6--landing--arrival)
*   **Actions**:
    *   Update current base airport to the destination in local preferences.
    *   Record flight log to local database.

### Task 6.2: Passport & Flight Log History
*   **Description**: List of all completed flights and cumulative travel stats.
*   **References**: [CONSTRUCTION_PLAN.md - Screen 7 · Passport](file:///c:/Users/kamme/AndroidStudioProjects/FocusFlight/CONSTRUCTION_PLAN.md#screen-7--passport--flight-log)
*   **UI Specs**: Styled scrollable list showing past flights as cards.
