package com.example.focusflight.data.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.focusflight.data.local.airport.AirportRouteSqliteDataSource
import com.example.focusflight.data.repository.LocalAirportRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Authoring integrity for [PredefinedRouteCatalog].
 *
 * A predefined route takes the destination picker away from the pilot: the next hop is whatever the
 * catalog says it is, booked directly onto the boarding card. That is only safe while every leg is
 * a route that actually exists - a single typo'd IATA code produces a challenge that either cannot
 * be flown at all or quietly falls back to an estimated duration nobody authored. Flight Search
 * would have surfaced that immediately by simply not offering the leg; this submode has no such
 * moment, so the check has to live here.
 *
 * Instrumented rather than JVM because it reads the real `flights.db` asset - the same source the
 * booking flow reads, which is the only thing worth asserting against.
 *
 * Run with `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class PredefinedRouteCatalogTest {

    private lateinit var airportRepository: LocalAirportRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        airportRepository = LocalAirportRepository(AirportRouteSqliteDataSource(context))
        airportRepository.ensureDatabaseCopied()
    }

    @Test
    fun everyLegOfEveryPredefinedRouteIsARealRoute() {
        for (route in PredefinedRouteCatalog.ALL) {
            for (leg in 0 until route.legCount) {
                val origin = route.originOf(leg)!!
                val dest = route.destOf(leg)!!
                val found = airportRepository
                    .getOutboundRoutes(originIata = origin, searchQuery = dest)
                    .find { it.destIata == dest }

                assertNotNull("${route.id} leg $leg ($origin -> $dest) is not a real route", found)
                assertTrue(
                    "${route.id} leg $leg ($origin -> $dest) has no usable duration",
                    found!!.durationMin > 0
                )
            }
        }
    }

    /**
     * The authored [PredefinedRoute.legDistancesKm] have to be the distances the legs are actually
     * flown at, because they are what progress is scored against - a wrong number here does not
     * fail anywhere, it just quietly pays the pilot the wrong percentage for a leg they really flew.
     *
     * Omitting them entirely is allowed (the route falls back to counting legs); getting them wrong
     * is not. The failure message carries the correct list, so fixing it is a paste.
     */
    @Test
    fun authoredLegDistancesMatchTheRoutesDatabase() {
        for (route in PredefinedRouteCatalog.ALL) {
            if (route.legDistancesKm.isEmpty()) continue

            assertEquals(
                "${route.id} needs one distance per leg",
                route.legCount,
                route.legDistancesKm.size
            )

            val actual = (0 until route.legCount).map { leg ->
                val origin = route.originOf(leg)!!
                val dest = route.destOf(leg)!!
                airportRepository.getOutboundRoutes(originIata = origin, searchQuery = dest)
                    .find { it.destIata == dest }!!
                    .distanceKm
            }

            for (leg in 0 until route.legCount) {
                assertEquals(
                    "${route.id} leg $leg (${route.originOf(leg)} -> ${route.destOf(leg)}) has the " +
                        "wrong distance. Correct list: " +
                        actual.joinToString(", ", "listOf(", ")") { "%.1f".format(it) },
                    actual[leg],
                    route.legDistancesKm[leg],
                    1.0
                )
            }
        }
    }

    @Test
    fun everyWaypointIsARealAirport() {
        for (route in PredefinedRouteCatalog.ALL) {
            for (iata in route.waypoints.distinct()) {
                assertNotNull(
                    "${route.id} names an airport that does not exist: $iata",
                    airportRepository.getAirportByIata(iata)
                )
            }
        }
    }

    /** Two waypoints would be a free-form route with the choosing removed, which is not a thing
     *  worth authoring - the submode earns its keep on itineraries with a shape. */
    @Test
    fun everyPredefinedRouteHasAtLeastThreeWaypoints() {
        for (route in PredefinedRouteCatalog.ALL) {
            assertTrue("${route.id} needs at least three waypoints", route.waypoints.size >= 3)
        }
    }

    @Test
    fun predefinedRouteIdsAreUnique() {
        val ids = PredefinedRouteCatalog.ALL.map { it.id }
        assertEquals("duplicate predefined route ids", ids.size, ids.distinct().size)
    }

    /** Every curated template that claims a predefined route has to point at one that exists -
     *  otherwise starting it returns `UnknownTemplate` and the picker offers a dead entry. */
    @Test
    fun everyCuratedTemplateResolvesItsPredefinedRoute() {
        for (template in CuratedChallengeCatalog.ALL) {
            val id = template.predefinedRouteId ?: continue
            assertNotNull(
                "${template.catalogId} references unknown predefined route $id",
                PredefinedRouteCatalog.find(id)
            )
        }
    }
}
