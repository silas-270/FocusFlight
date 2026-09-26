# The route network, search and booking

In Story Mode the pilot's position changes only by flying, so the set of routes is not just a
catalogue but the board the game is played on. This file explains which airports the app offers,
how they are found, how a route is chosen by its duration, and how an authored itinerary books its
own next leg.

## The main route network

Treat every sane route ([flight-data.md](flight-data.md#rows-the-app-does-not-use)) as a directed
edge. For a pilot who can only move by flying, two kinds of airport are traps:

- an airport that can be flown **to** but has no departures, where the pilot would be stuck for
  good;
- an airport in a small cluster that only connects to itself, which can be entered only from inside
  the cluster, or left but never returned to.

The airports without either problem are exactly the **strongly connected component** of the route
graph that contains its busiest hub: from any of them the pilot can reach any other and come back.
`RouteNetwork.mainComponent` finds it without a full Tarjan pass. It takes the airport with the
most outgoing edges as the hub, runs one breadth-first search forward from it and one along the
reversed edges, and intersects the two. An airport is in the component if and only if it is
reachable from the hub and can reach the hub. The busiest hub of any real airline network is in its
giant component, so this finds the right one, and two BFS passes are much easier to read than
Tarjan's algorithm.

With the bundled data:

| | Airports |
|---|---|
| IATA airports in `flights.db` | 4,170 |
| hub | Istanbul (IST), 313 destinations |
| in the main network | 3,436 |
| hidden | 734 |

Of the hidden ones, 666 have no usable route at all (665 have no route of any kind), 34 can only be
flown to, 11 can only be left, and the rest sit in closed clusters such as ISC/LEQ in the Isles of
Scilly and Cornwall. The component is further intersected with the airports table, since a route to
an unknown code could never be displayed.

**Only the main network is offered anywhere a pilot chooses.** Every airport picker (onboarding,
change home base, Free Mode origin, custom Route challenges) searches only network airports, and
destination lists only contain destinations in the network. Once a pilot is inside the component,
every booking keeps them there, so the network is closed under play.

`findRoute`, the lookup for a flight that is already booked, is deliberately not restricted to the
network. It resolves check-in, in-flight and paused flights whose destination was valid when it was
picked; a data update that later hides that destination would otherwise break a flight in progress.

## The dead-end rescue

The pickers never offer a dead end, but the pilot's current airport is persisted data while the
reference data is replaced with every app update, so an airport that was connected when the pilot
landed there is not guaranteed to stay connected. Flight Search handles this: if the Story origin
returns an empty route list, the pilot is moved to the nearest `large_airport` in the network by
great-circle distance (London Heathrow if even that cannot be resolved), the move is persisted to the
current airport, and the screen shows a notice naming the new airport. Nearest rather than a fixed
airport keeps the pilot in the part of the world they were already flying in.

Two conditions guard it. It fires only on an **empty** result, never on a **failed** query: route
queries throw on failure rather than returning an empty list, and a failure shows the empty state
without moving anyone ([flight-data.md](flight-data.md#failing-loudly-where-it-matters)). And it
fires only in Story Mode, because it writes the current airport, which a Free session never
touches.

## Countries and continents

The geographic achievements need a denominator: which countries exist, and on which continent each
one counts. `RouteNetwork.continentCountryMap` builds it from the network's airports only, so every
country in it is one a pilot can actually fly to.

Each country is placed under exactly **one** continent: the continent most of its network airports
are on, ties broken alphabetically. The raw airport data files six countries under two continents
(US airports in Oceania, Russian ones in Asia and Europe, Spain's Canary Islands in Africa, a few
Turkish, Greek and Egyptian ones across the Bosporus, the Aegean and Sinai). Counting them twice
would put Spain in "Master of Africa" and make the sum over continents larger than the number of
countries. Russia is the one override (`PRIMARY_CONTINENT_OVERRIDES`): most of its airports are in
Asia, but it counts under Europe, where it is conventionally grouped.

The result is 234 countries on six continents; the per-continent counts are in
[achievements.md](achievements.md#geographic-sets). Antarctica does not appear because its only
airport in the data has no routes.

## Airport search

Every airport picker goes through `AirportSearchIndex`, an in-memory index over the few thousand
airport rows. Scanning a normalised list per keystroke is cheaper than any SQL query, and SQLite's
`LIKE` cannot fold "ü" to "u" at all, so doing it in SQL would make "dusseldorf" or "sao paulo" find
nothing.

**Normalisation** (`normalizeForSearch`) lowercases in the root locale, decomposes to NFD and drops
combining marks, maps the handful of Latin letters that have no decomposition (ø, ł, đ, ð, ı, ß, æ,
œ, þ) by hand, and collapses punctuation to single spaces. Both the airport fields and the query go
through the same function, so they can never be normalised differently.

**Ranking.** Each airport is matched against the query and placed in the first tier it satisfies:

| Tier | Match |
|---|---|
| 0 | exact IATA code |
| 1 | exact ICAO code |
| 2 | IATA code starting with the query |
| 3 | the city starting with the query, at any word |
| 4 | the airport name starting with the query, at any word |
| 5 | the query anywhere in the city or airport name |
| 6 | the country's English name starting with the query ("germany" lists German airports) |

Ties go to bigger airports (large, then medium, then everything else) and then alphabetically by
name, so "Doha" lists Hamad International ahead of an airfield that merely shares the city. Queries
shorter than two characters return nothing, and results are cut to 15 after the network filter.

**City aliases.** The data spells cities in English ("Munich", "Cologne", "Vienna"). A short table
maps local names a pilot might type, such as "münchen", "wien", "praha", "genf", "peking" or
"bombay", to the spelling in the data. An alias only ever adds candidates, and only once at least
four characters are typed, so a plain query ranks exactly as it would without it.

Pickers feed the index through `AirportSearchController`: a 300 ms debounce, the two-character
minimum, and the search on an IO dispatcher. Before anything is typed, pickers show four suggestion
tiles (FRA, LHR, BER, MUC).

The destination search inside Flight Search works differently, because its candidates are already
loaded: it filters the origin's route list by IATA code, airport name, city, and the start of the
country name, with the same normalisation.

## Booking by duration

Flight Search loads the origin's routes sorted by flight time, then builds its timeline: ten-minute
buckets from the shortest route's bucket to the longest's, keeping only buckets that contain at
least one route. Scrolling to a bucket selects its shortest route. Across the network, bookable
durations run from 5 minutes to 20 hours 30 minutes, so the same control has to serve a pilot with
half an hour and a pilot with a whole day.

Durations are used exactly as scheduled. A long-haul flight is not compressed to fit a sitting; it
is meant to be paused and resumed ([paused-flights.md](paused-flights.md)).

**Flight numbers** are cosmetic: `"FF-" + (|hash(destIata)| mod 1000 + 100)`, so between FF-100 and
FF-1099. They depend only on the destination and are not unique, which is harmless because nothing
is keyed by them; paused flights are stored per slot. `flightNumberFor` lives in the domain layer so
that Flight Search bookings and predefined-itinerary bookings produce numbers the same way.

## The next leg of an itinerary

A predefined-itinerary challenge ([challenges.md](challenges.md#the-predefined-route-submode)) has
no choice left in it: the next hop is authored. `resolveNextLeg(challengeId)` returns a fully
resolved `PendingLeg` (origin, destination, duration, flight number) for an active predefined
challenge, and null for anything else. The caller books the leg directly and navigates to
Check-In, skipping Flight Search; a null sends a free-form challenge to Flight Search as usual.

The leg's origin and destination come from the itinerary at the challenge's `legIndex`, and the
duration is the route's real scheduled time from `flights.db`. If the route is somehow missing from
the database, the leg falls back to a great-circle estimate at 800 km/h, at least 30 minutes.
`PredefinedRouteCatalogTest` is meant to make that fallback unreachable; if it is ever reached,
flying an estimate is better than stranding a live challenge.
