package com.silas270.blocktime.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The assertions here are about the *shape* of the thrust schedule - which phase is louder than
 * which - rather than about the exact constants, so retuning the numbers in [EnginePowerModel]
 * does not mean rewriting the suite. Every helper below reproduces a point on the flight profile
 * `cesium-flight`'s telemetry generator actually produces (see docs/engine.md): ground roll at 5 m
 * with speed rising to ~89 m/s, a ~25° climb, level cruise, a ~15° descent, and a rollout from
 * ~72 m/s to a stop.
 */
class EnginePowerModelTest {

    private val tick = 1f / 30f // the ViewModel's 33 ms timer

    /** Holds one telemetry reading long enough for the spool to settle, and returns the result. */
    private fun EnginePowerModel.hold(
        progress: Float,
        altitudeMeters: Float,
        speedMetersPerSecond: Float,
        pitchRadians: Float,
        seconds: Float = 20f
    ): Float {
        var power = 0f
        repeat((seconds / tick).toInt()) {
            power = update(progress, altitudeMeters, speedMetersPerSecond, pitchRadians, tick)
        }
        return power
    }

    private fun parked() = Reading(progress = 0f, altitude = 5f, speed = 0f, pitch = 0f)
    private fun takeoffRoll() = Reading(progress = 0.02f, altitude = 5f, speed = 70f, pitch = 0f)
    private fun initialClimb() = Reading(progress = 0.04f, altitude = 200f, speed = 110f, pitch = 0.42f)
    private fun climb() = Reading(progress = 0.12f, altitude = 6000f, speed = 200f, pitch = 0.42f)
    private fun cruise() = Reading(progress = 0.5f, altitude = 11500f, speed = 250f, pitch = 0f)
    private fun descent() = Reading(progress = 0.9f, altitude = 8000f, speed = 200f, pitch = -0.26f)
    private fun approach() = Reading(progress = 0.97f, altitude = 250f, speed = 80f, pitch = -0.26f)
    private fun touchdown() = Reading(progress = 0.985f, altitude = 5f, speed = 71f, pitch = 0f)
    private fun reversing() = Reading(progress = 0.99f, altitude = 5f, speed = 45f, pitch = 0f)
    private fun taxiIn() = Reading(progress = 0.999f, altitude = 5f, speed = 5f, pitch = 0f)

    private data class Reading(
        val progress: Float,
        val altitude: Float,
        val speed: Float,
        val pitch: Float
    )

    private fun settled(reading: Reading): Float = EnginePowerModel().hold(
        reading.progress, reading.altitude, reading.speed, reading.pitch
    )

    @Test
    fun `takeoff roll is the loudest point of the whole flight`() {
        val roll = settled(takeoffRoll())
        val everythingElse = mapOf(
            "parked" to settled(parked()),
            "climb" to settled(climb()),
            "cruise" to settled(cruise()),
            "descent" to settled(descent()),
            "approach" to settled(approach()),
            "reversing" to settled(reversing()),
            "taxi in" to settled(taxiIn())
        )
        everythingElse.forEach { (phase, power) ->
            assertTrue(
                "expected the takeoff roll ($roll) to out-power $phase ($power)",
                roll >= power
            )
        }
    }

    @Test
    fun `power steps down from takeoff through the climb to cruise, not up`() {
        val roll = settled(takeoffRoll())
        val initial = settled(initialClimb())
        val climb = settled(climb())
        val cruise = settled(cruise())

        // Below the thrust reduction altitude the engines are still at takeoff thrust.
        assertEquals("initial climb should still be at takeoff thrust", roll, initial, 0.02f)
        assertTrue("climb ($climb) should be below takeoff ($roll)", climb < roll - 0.05f)
        assertTrue("cruise ($cruise) should be below the climb ($climb)", cruise < climb)
    }

    @Test
    fun `a parked aircraft sits at idle rather than at takeoff thrust`() {
        val parked = settled(parked())
        assertEquals(EnginePowerModel.IDLE_N1, parked, 0.02f)
    }

    @Test
    fun `descent is the quietest part of the flight that is not on the ground`() {
        val descent = settled(descent())
        assertTrue("descent ($descent) should be below cruise", descent < settled(cruise()))
        assertTrue("descent ($descent) should be below the climb", descent < settled(climb()))
        assertTrue("descent ($descent) should be below the approach", descent < settled(approach()))
    }

    @Test
    fun `approach thrust comes up, but nowhere near takeoff`() {
        val approach = settled(approach())
        val descent = settled(descent())
        val roll = settled(takeoffRoll())
        assertTrue("approach ($approach) should be above idle descent ($descent)", approach > descent)
        assertTrue(
            "approach ($approach) should stay well below takeoff thrust ($roll) - the old model's " +
                "reverse-thrust term surging here is the bug this replaces",
            approach < roll - 0.3f
        )
    }

    @Test
    fun `reverse thrust is a window after touchdown, not the whole arrival`() {
        val approach = settled(approach())
        val touchdown = settled(touchdown())
        val reversing = settled(reversing())
        val taxi = settled(taxiIn())

        assertTrue("the flare/touchdown ($touchdown) should be quieter than the approach ($approach)",
            touchdown < approach)
        assertTrue("reverse ($reversing) should be much louder than touchdown ($touchdown)",
            reversing > touchdown + 0.3f)
        assertTrue("reverse should be stowed by taxi speed, got $taxi", taxi < reversing - 0.3f)
        assertEquals("taxi-in should be back at idle", EnginePowerModel.IDLE_N1, taxi, 0.02f)
    }

    @Test
    fun `the takeoff roll is not mistaken for a landing rollout at the same speed and altitude`() {
        // Identical altitude, near-identical speed - only the point in the flight differs.
        val takeoff = settled(Reading(progress = 0.02f, altitude = 5f, speed = 45f, pitch = 0f))
        val rollout = settled(reversing())
        assertTrue(
            "the takeoff roll ($takeoff) should out-power the reverse-thrust rollout ($rollout)",
            takeoff > rollout
        )
    }

    @Test
    fun `power moves smoothly - no single tick can step the engine`() {
        val model = EnginePowerModel()
        model.hold(descent().progress, descent().altitude, descent().speed, descent().pitch)

        // Slam from an idle descent straight to a full-power takeoff roll: the biggest demand
        // change the schedule can produce.
        var previous = model.update(descent().progress, descent().altitude, descent().speed, descent().pitch, tick)
        var maxStep = 0f
        repeat(300) {
            val next = model.update(takeoffRoll().progress, takeoffRoll().altitude, takeoffRoll().speed, 0f, tick)
            maxStep = maxOf(maxStep, kotlin.math.abs(next - previous))
            previous = next
        }
        assertTrue("expected every single-tick step to stay bounded, got max step=$maxStep", maxStep < 0.02f)
    }

    @Test
    fun `the spool takes seconds, and winds down faster than it winds up`() {
        val up = EnginePowerModel()
        up.hold(parked().progress, parked().altitude, parked().speed, parked().pitch)
        var upTicks = 0
        var power = 0f
        while (power < 0.9f && upTicks < 3000) {
            power = up.update(takeoffRoll().progress, takeoffRoll().altitude, takeoffRoll().speed, 0f, tick)
            upTicks++
        }
        val spoolUpSeconds = upTicks * tick
        assertTrue("spool-up should take seconds, took ${spoolUpSeconds}s", spoolUpSeconds > 2f)

        val down = EnginePowerModel()
        down.hold(takeoffRoll().progress, takeoffRoll().altitude, takeoffRoll().speed, 0f)
        var downTicks = 0
        // Same fraction of the way to the new target, so the two are comparable.
        val start = down.update(takeoffRoll().progress, takeoffRoll().altitude, takeoffRoll().speed, 0f, tick)
        val threshold = start - (start - EnginePowerModel.IDLE_N1) * 0.9f
        power = start
        while (power > threshold && downTicks < 3000) {
            power = down.update(parked().progress, parked().altitude, parked().speed, 0f, tick)
            downTicks++
        }
        assertTrue(
            "spool-down ($downTicks ticks) should be quicker than spool-up ($upTicks ticks)",
            downTicks < upTicks
        )
    }

    @Test
    fun `a long tick delta cannot spool the engine instantly`() {
        val model = EnginePowerModel()
        model.hold(parked().progress, parked().altitude, parked().speed, 0f)
        // A tick straddling the app being backgrounded for five minutes.
        val after = model.update(takeoffRoll().progress, takeoffRoll().altitude, takeoffRoll().speed, 0f, 300f)
        assertTrue("a 300 s delta should still be capped, got $after", after < 0.5f)
    }

    @Test
    fun `settleAt lands on the demanded power without any spool`() {
        val model = EnginePowerModel()
        val settledByHolding = settled(climb())
        val jumped = model.settleAt(climb().progress, climb().altitude, climb().speed, climb().pitch)
        assertEquals(settledByHolding, jumped, 0.01f)
    }

    @Test
    fun `output stays in range for readings the engine could never produce`() {
        val model = EnginePowerModel()
        val absurd = listOf(
            Reading(-5f, -1000f, -300f, -9f),
            Reading(17f, 500000f, 40000f, 9f),
            Reading(0.5f, 0f, 0f, 0f),
            Reading(0f, 0f, 0f, 0f)
        )
        absurd.forEach {
            val power = model.update(it.progress, it.altitude, it.speed, it.pitch, 1f)
            assertTrue("power out of range: $power for $it", power in 0f..1f)
        }
    }
}
