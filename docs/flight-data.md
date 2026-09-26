# The flight database

Every airport, route and runway in Blocktime comes from one bundled SQLite file,
`app/src/main/assets/flights.db` (about 4 MB). This file describes what is in it, where it comes
from, which of its rows the app refuses to use and why, and how it is read at runtime.

## Provenance

- **Routes** are derived from the [OpenFlights](https://openflights.org/data) route data, under the
  Open Database License. The `carriers` column is a comma-separated list of the IATA airline codes
  that fly each pair.
- **Airports and runways** come from [OurAirports](https://ourairports.com/data/), which is in the
  public domain. That is where the ICAO `ident`, the `type`, `continent`, `iso_country`,
  `iso_region`, `municipality` and `elevation_ft` columns, and the runway threshold coordinates,
  originate.
- Each route carries a `distance_km` and a scheduled `flight_time_min`, the session length the app
  uses. The two directions of a pair can have different times, as real schedules do: Sydney →
  London is 1,230 minutes and London → Sydney 1,160, the westbound flight fighting the jet stream.

The table rows are ordered by origin code, except for the last eight: four Qantas ultra-long-haul
pairs in both directions (Sydney and Melbourne to London and New York), appended after the rest.
They include the longest route in the app, Sydney → London at 17,021 km and 20 h 30 min.

## Contents

```sql
airports(airport_id PK, ident, iata_code UNIQUE, name, lat, lon, elevation_ft,
         continent, iso_country, iso_region, municipality, type)
routes  (id PK, origin_iata, dest_iata, distance_km, flight_time_min, carriers)
runways (runway_id PK, airport_id, airport_ident, length_ft, width_ft, surface,
         le_ident, le_heading, he_ident, he_heading, le_lat, le_lon, he_lat, he_lon)
```

with indexes on airport IATA code and country, runway airport, and route origin and destination.

| Table | Rows |
|---|---|
| `airports` | 4,170, every one with an IATA code |
| `routes` | 57,570 |
| `runways` | 5,670 |

By type, the airports are 1,156 large, 2,118 medium and 771 small airports, 71 seaplane bases and
54 heliports.

## Rows the app does not use

The raw route data has errors that would be visible as nonsense in the app, so both the route lists
and the route network are built through one SQL predicate, `SANE_ROUTE` in
`AirportRouteSqliteDataSource`:

```sql
origin_iata != dest_iata AND flight_time_min > 0 AND distance_km > 0
AND distance_km * 60 <= flight_time_min * 1200
```

- **Four self-routes** from an airport to itself, with zero distance.
- **Physically impossible durations.** No airliner averages more than 1,200 km/h over a whole
  block time, gate to gate, even in a strong jet stream; the data has routes like
  Balkanabat → Türkmenabat (BKN → CRZ) at 797 km in one minute. A route faster than that is a data error, not a
  flight. Together with the self-routes this removes 41 rows, leaving 57,529.
- **Duplicates.** The data has 56 exact repeats of an origin–destination pair. Route queries group
  by destination and keep the row with the lowest id, so each destination is offered once.

Short hops are kept on purpose. A 5-minute, 66 km island hop is a real, if short, focus session.

Because the same predicate feeds both the bookable lists and the network graph
([route-network.md](route-network.md)), the two can never disagree about which routes exist.

Two per-column repairs happen in code:

- **Namibia.** Its ISO code is `NA`, which the tool that built the database read as a missing value,
  so all nine Namibian airports have a blank `iso_country`. The code recovers the country from the
  region code (`NA-KH` → `NA`), both when loading airports and in the destination query
  (`COALESCE(NULLIF(iso_country, ''), substr(iso_region, 1, 2))`). Without it Namibia would be
  missing from the map and from every country count.
- **Runways without coordinates** are skipped, since the engine needs threshold positions to lay a
  runway down.

## Copying and opening it

Android cannot open a SQLite database inside the APK, so `ensureDatabaseCopied()` copies the asset
into the app's databases directory. It copies when the file is missing **or stale**. Stale means the
package's `lastUpdateTime` differs from the one recorded in a `flights.db.stamp` file next to it, so
every install and update, including a restore from backup, refreshes the reference data instead of
keeping whatever was copied first.

The copy is written to `flights.db.tmp`, flushed with `fd.sync()`, and then renamed into place,
after deleting any leftover `-journal`, `-wal` and `-shm` files. A process killed mid-copy therefore
leaves only a temp file, never a truncated database that a later launch would trust. A failed
refresh keeps the previous copy; only a failure with no usable copy at all is an error.

The database is opened **once**, read-only, and never closed. Nothing ever writes it, so there is
nothing to flush, and `SQLiteDatabase` locks internally, so one shared read-only handle is safe from
any IO thread. Every airport lookup, route query and runway lookup in the app goes through this one
connection.

## In memory

A few thousand small rows are cheap to hold, so the first airport query loads **every airport** into
a `HashMap` keyed by IATA code, together with the search index built from it
([route-network.md](route-network.md#airport-search)). `getAirportByIata`, the hottest lookup in the
app (Hub, Check-In, In-Flight, challenge crediting and the landing pipeline all resolve codes through
it), is then a map read rather than a query.

Three derived structures are memoised for the life of the process: the airport index, the main route
network, and the continent → countries map. All three are pure functions of an immutable file, so
caching them can never serve a stale answer, and all three are **assigned only on success**.

Routes and runways are queried on demand: they are needed per origin, not all at once.

## Failing loudly where it matters

The data source has two error policies, chosen per query by what a caller would do with an empty
answer.

Lookups where "found nothing" is an ordinary answer that nothing acts on (search, a single airport,
runways) log a failure and return empty or null. A search that fails shows "no results", which is no
worse than a miss, and an airport without runway rows gets a generic approach from the engine.

Three queries throw `AirportDataException` instead, because an empty result would be acted on:

- `getContinentCountryMap` and `getCountriesForAirports` feed the visited-country map, the continent
  breakdown and every geographic achievement. A swallowed failure would be indistinguishable from
  "this pilot has visited nothing": a wiped passport with no error anywhere, and, once cached, for
  the rest of the process.
- `getOutboundRoutes` (and `findRoute`) feed Flight Search, where Story Mode responds to an empty
  list by moving the pilot to another airport and persisting it
  ([route-network.md](route-network.md#the-dead-end-rescue)). A swallowed failure would teleport the
  pilot because a query briefly broke.

A throw cannot be cached by accident; an empty set can. Callers that cannot afford to crash catch the
exception and degrade explicitly: Flight Search shows its empty state without moving anyone, the
headless renderer returns a retryable failure, and `loadRouteContext` treats it as "no such route".
