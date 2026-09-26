# Airport time zones

The in-flight HUD shows the local time at both ends of the flight: when the flight left the origin,
in the origin's clock, and when it will arrive, in the destination's, with a "+1" when it lands on a
later calendar day. That needs the time zone of every airport, and `flights.db` has no time zone
column. This file explains how the app gets exact local times anyway, with no network and no
per-airport table.

## The approach

The API that asks for a local time only receives what the app already has for an airport: its
country code and its coordinates. `util/FlightClock.kt` resolves an IANA zone from those two inputs
through a generated lookup, and `java.time` applies that zone's offset and daylight-saving rules for
the instant in question.

Splitting the problem that way puts each half where it belongs. **Which zone an airport is in** is
static geography, fixed when the database is built, so it is generated once. **What that zone's
offset is on a given date** changes whenever a government changes its rules, so it comes from the
device's own tz database at runtime and stays current without an app update.

The lookup (`util/AirportTimeZones.kt`) has two tables:

- `SINGLE_ZONE_COUNTRIES`: for the 215 countries whose airports all keep the same clock, the
  country's zone directly.
- `ZONE_ANCHORS`: for the 21 countries whose airports keep different clocks, a small set of anchor
  airports with their zones, 303 anchors in all. An airport takes the zone of the nearest anchor in
  its own country.

The multi-zone countries are the expected ones (United States 63 anchors, Canada 64, Russia 51,
Australia 24, Mexico 21, Indonesia 17, Brazil 12) plus some less obvious ones: China, where the tz database gives
Xinjiang's airports `Asia/Urumqi`, Xinjiang's local time rather than Beijing time; Spain and Portugal, whose Canary Islands and Azores are an hour behind the
mainland; Ecuador with Galápagos; Kiribati, French Polynesia, Micronesia, Papua New Guinea and
others.

At runtime the nearest anchor is found with the haversine term `a` alone, without the `asin`,
because `a` is monotonic in great-circle distance and only the ordering matters. A country the
tables do not know at all falls back to a whole-hour offset from longitude (`lon / 15`, clamped to
−12..+14), which no airport in the bundled data reaches. Resolved `ZoneId`s are cached by name.

## Generating the tables

`tools/gen_airport_timezones.py` produces `AirportTimeZones.kt` from `flights.db`:

1. Each IATA airport's zone is looked up with the `timezonefinder` package from its coordinates,
   falling back to the closest zone for points just offshore. The country uses the same Namibia
   repair as the app ([flight-data.md](flight-data.md#rows-the-app-does-not-use)).
2. Zones are compared **by behaviour, not by name.** A zone's signature is its UTC offset at 240
   instants across 2026 and 2027 (days 1, 8, 15, 22 and 28 of every month, at 00:00 and 12:00 UTC),
   which captures standard time and both daylight-saving transitions. America/Detroit and
   America/New_York have the same signature and count as the same clock; a country whose airports
   all share one signature needs no anchors.
3. For every other country, anchors are chosen by **condensed nearest-neighbour**: start with one
   airport, then repeatedly scan all of the country's airports and add any airport whose nearest
   anchor so far would give it the wrong signature, until a full pass adds nothing. The result is a
   small set of airports that, used as nearest-neighbour references, classify every airport in the
   country correctly. The US needs 63 anchors for its 679 airports.
4. The script then re-resolves every airport from the generated tables alone, using the same
   great-circle ranking the app uses, and fails if any one of them would get a zone with a different
   signature.

The output is therefore exact for every airport the app can show, by construction and by check. It
has to be regenerated whenever `flights.db` changes, since a new airport near a zone boundary may
need an anchor of its own. Coordinates are rounded to three decimals (about 100 m) in the output.

`AirportDataLogicTest` pins a set of clocks that a naive approach gets wrong: Seoul, Singapore,
Moscow, Mexico City (no DST), Atlanta in both July and January, Iceland (no DST), Tel Aviv, Buenos
Aires and Delhi's half-hour offset.

## How the HUD uses it

`airportClock(epochMs, lon, lat, country, relativeToDate)` returns the `HH:mm` label and a day
offset, the airport's local calendar date minus a reference date.

- **Departure** is the wall-clock moment the session effectively started: now minus the elapsed time
  the flight started or resumed with. It is shown in the origin's zone, and its local date there is
  the reference date for both clocks.
- **Arrival** is now plus the remaining time, in the destination's zone, so a pause pushes the
  arrival later the way a delay would. The label is keyed to the minute it shows, so the 30 Hz
  recomposition of the HUD does not redo the zone work every tick.

The offset can be negative as well as positive: an early-morning departure from Tokyo reaches Los Angeles
on the previous calendar day and reads "-1", while the return flight reads "+1". Nothing in the app
knows about the date line; it falls out of comparing two local dates.

## Where the airport's clock is not used

Some questions are about the pilot's own calendar, not about an airport, and use the device's zone
instead:

- tour boundaries ("did I fly today") in `TourSegmentation` and `Tour.isOpenAt`;
- streak days, which `LocalChallengeRepository` computes with its injected `Clock` (the system
  default zone in production);
- the Red-Eye achievement's "between midnight and five in the morning".

Using an airport's zone for these would make a flight to Tokyo count for a different day than the
pilot lived through.
