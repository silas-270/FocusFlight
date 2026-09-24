package com.example.focusflight.domain

import kotlin.math.abs
import kotlin.math.exp

/**
 * Derives a continuous fan-speed value - **N1 as a fraction of redline**, 0f..1f - from the live
 * telemetry the engine already reports, driving
 * [EngineSoundEngine][com.example.focusflight.audio.EngineSoundEngine].
 *
 * See docs/engine-sound.md for the whole feature, including where the frequencies come from.
 *
 * The output is deliberately N1 and not "loudness": a turbofan idles at roughly a fifth of its
 * redline fan speed and never actually reaches zero in flight, so the useful range here is about
 * [IDLE_N1]..1.0 and the synth is what turns that into a level. See
 * [EngineSoundSynth][com.example.focusflight.audio.EngineSoundSynth], which reads this same number
 * as a fan speed to derive shaft frequency, blade-pass frequency and blade-tip Mach from.
 *
 * ## The thrust schedule this reproduces
 *
 * Ordered the way an actual departure and arrival goes, because the previous version had it close
 * to backwards:
 *
 *  - **Takeoff roll: maximum.** This is the loudest the engines ever are. The old model had the
 *    ground at an idle-ish floor because nothing in it distinguished a takeoff roll from parking.
 *  - **Initial climb: still maximum,** until the thrust reduction altitude (~1000 ft), where an
 *    A350 comes back from takeoff thrust to climb thrust. That step down a minute or so after
 *    liftoff is exactly what a passenger hears.
 *  - **Climb: [CLIMB_N1],** easing to **cruise: [CRUISE_N1]** as the aircraft levels off - lower
 *    than the climb, higher than anything on the way down.
 *  - **Descent: [FLIGHT_IDLE_N1].** A jet descends at flight idle; it is the quietest part of a
 *    flight that isn't parked.
 *  - **Approach: [APPROACH_N1].** Thrust does come back up on final to hold the glideslope with
 *    the gear and flaps out - but to a bit over half N1, not to the near-takeoff surge the old
 *    reverse-thrust term produced anywhere below 1200 m while the aircraft was still decelerating.
 *  - **Flare and touchdown: back to ground idle,** then the **reverse-thrust roar**
 *    ([REVERSE_N1]) - but only on the runway, and only over the speed band where the buckets are
 *    actually deployed, so it is a few seconds of noise after touchdown rather than the whole
 *    approach.
 *  - **Rollout below [REVERSE_STOW_SPEED_MPS]: taxi idle** ([IDLE_N1]).
 *
 * ## Why it is memoryless
 *
 * Every gate below is a smooth function of the *current* telemetry sample only - no rates, no
 * differences between ticks. That matters for more than tidiness:
 *
 *  - The previous version inferred climb and descent by differencing altitude between ticks. The
 *    engine reports pitch directly ([update]'s `pitchRadians`), so the derivative was reconstructing
 *    a signal we are already given, badly: altitude arrived as a truncated Int, and at a 33 ms tick
 *    the real per-tick change is a fraction of a metre, so the "rate" was mostly quantisation noise.
 *  - The same went for acceleration. A takeoff roll accelerates at barely over 1 m/s²; differencing
 *    an Int km/h reading at 30 Hz cannot see that at all, which is why the old ground-idle floor
 *    never lifted on the runway.
 *  - Being memoryless makes the debug scrubber honest - drag to any point in the flight and the
 *    power is what it would have been had you flown there, with no dependence on how you arrived.
 *
 * The one piece of state is [smoothedN1]: the spool. That is real engine behaviour rather than
 * signal conditioning - a large turbofan takes seconds to wind up or down, and it is asymmetric
 * (see [SPOOL_UP_TAU_S] / [SPOOL_DOWN_TAU_S]). Everything that made the old model's output jumpy
 * is gone, so this lag no longer has to double as a noise filter and can be as slow as the real
 * thing.
 *
 * There is still no discrete "flight phase" enum, for the reason the original note gave: bucketing
 * ticks into GROUND/CLIMB/CRUISE produced audible jump-cuts as a reading ticked across a threshold.
 * Every blend here is a [smoothstep] over a continuous quantity instead, so there is no boundary to
 * cross. [landingSide] is the only term keyed to progress, and it crosses at mid-cruise where
 * [airborne] holds its weight at zero, so it is inaudible by construction.
 *
 * A single instance is expected to live for the whole session, but only so the spool is continuous;
 * recreating one mid-flight costs a few seconds of wind-up, not a wrong answer.
 */
class EnginePowerModel {

    /** The spool. See the class doc - this is the model's only state. */
    private var smoothedN1: Float = IDLE_N1

    /**
     * One tick of the engine. All arguments come straight from
     * `CesiumLiveJniBridge.nativeGetTelemetry()`, in its native units - no rounding on the way in,
     * since the truncated Int copies the HUD displays are exactly what made the old rate estimates
     * useless.
     *
     * @param progress flight progress, 0f..1f (telemetry index 0)
     * @param altitudeMeters altitude above the departure field (telemetry index 3)
     * @param speedMetersPerSecond true airspeed (telemetry index 4, *not* the km/h HUD value)
     * @param pitchRadians nose-up pitch attitude (telemetry index 6)
     * @param deltaSeconds wall-clock time since the previous [update], for the spool lag
     */
    fun update(
        progress: Float,
        altitudeMeters: Float,
        speedMetersPerSecond: Float,
        pitchRadians: Float,
        deltaSeconds: Float
    ): Float {
        val demand = demandedN1(progress, altitudeMeters, speedMetersPerSecond, pitchRadians)

        // Clamped because a tick straddling a backgrounded app can report an arbitrarily long
        // delta, and an unclamped one would spool the engine instantly - the audible jump this
        // lag exists to prevent.
        val dt = deltaSeconds.coerceIn(0f, MAX_SPOOL_STEP_S)
        val tau = if (demand > smoothedN1) SPOOL_UP_TAU_S else SPOOL_DOWN_TAU_S
        // Exponential glide expressed in real time, so the result is independent of tick rate.
        val alpha = 1f - exp(-dt / tau)
        smoothedN1 += (demand - smoothedN1) * alpha
        return smoothedN1.coerceIn(0f, 1f)
    }

    /**
     * The same reading with the spool already settled - what the engine *would* be doing after
     * holding this telemetry indefinitely.
     *
     * For the debug scrubber, which jumps to an arbitrary point in the flight: running [update]
     * there would spend seconds spooling from wherever the previous scrub position left the engine,
     * so the sound would lag the slider rather than describing where it points. The audio engine
     * still applies its own short anti-click glide, so this cannot produce a discontinuity in the
     * output signal.
     */
    fun settleAt(
        progress: Float,
        altitudeMeters: Float,
        speedMetersPerSecond: Float,
        pitchRadians: Float
    ): Float {
        smoothedN1 = demandedN1(progress, altitudeMeters, speedMetersPerSecond, pitchRadians)
            .coerceIn(0f, 1f)
        return smoothedN1
    }

    /**
     * The thrust schedule itself: what N1 the flight crew would be commanding for this telemetry,
     * before the engine's own spool lag. Pure - see the class doc on why.
     */
    private fun demandedN1(
        progress: Float,
        altitudeMeters: Float,
        speedMetersPerSecond: Float,
        pitchRadians: Float
    ): Float {
        val speed = speedMetersPerSecond.coerceAtLeast(0f)

        // --- Gates -----------------------------------------------------------------------------
        // How airborne we are. The engine parks the aircraft a few metres up (its altitude is
        // measured to the fuselage, not the wheels), so the lower edge sits above that rather
        // than at zero.
        val airborne = smoothstep(altitudeMeters, GROUND_ALT_M, AIRBORNE_ALT_M)

        // Which end of the flight a *ground* reading belongs to. The takeoff roll and the landing
        // rollout are the same altitude at overlapping speeds, so nothing local separates them.
        // This crosses at mid-flight, where `airborne` is 1 and this term's weight is 0.
        val landingSide = smoothstep(progress, GROUND_SPLIT_LOW, GROUND_SPLIT_HIGH)

        // Climb and descent read straight off pitch attitude. Saturating well inside the pitch the
        // flight profile actually generates (~25° in the climb, ~15° in the descent), so this is a
        // firm "climbing" / "descending" rather than a proportional pitch-to-thrust mapping.
        val climbing = smoothstep(pitchRadians, PITCH_DEADBAND_RAD, CLIMB_PITCH_RAD)
        val descending = smoothstep(-pitchRadians, PITCH_DEADBAND_RAD, DESCENT_PITCH_RAD)

        // --- On the ground ---------------------------------------------------------------------
        // Takeoff: the engines spool up over the first part of the roll, then hold takeoff thrust
        // all the way to rotation. Keyed to speed so a parked aircraft sits at idle.
        val takeoffRoll = lerp(IDLE_N1, TAKEOFF_N1, smoothstep(speed, 0f, TAKEOFF_SPOOL_SPEED_MPS))

        // Landing: reverse thrust is a *window*, not a floor. The buckets are not out at the
        // moment of touchdown (hence the upper edge, which keeps the flare quiet), they do their
        // work through the fast part of the rollout, and they are stowed well before taxi speed.
        val reverseDeployed = 1f - smoothstep(speed, REVERSE_FULL_SPEED_MPS, REVERSE_DEPLOY_SPEED_MPS)
        val reverseActive = smoothstep(speed, REVERSE_STOW_SPEED_MPS, REVERSE_ACTIVE_SPEED_MPS)
        val rollout = lerp(IDLE_N1, REVERSE_N1, reverseDeployed * reverseActive)

        val groundN1 = lerp(takeoffRoll, rollout, landingSide)

        // --- In the air ------------------------------------------------------------------------
        // Takeoff thrust is held until the thrust reduction altitude and then comes back to climb
        // thrust - the step down a minute after liftoff.
        val climbN1 = lerp(TAKEOFF_N1, CLIMB_N1, smoothstep(altitudeMeters, THRUST_REDUCTION_START_M, THRUST_REDUCTION_END_M))

        // On the way down, idle at altitude gives way to approach thrust as the aircraft gets low,
        // slow and dirty. A real rise, but a modest one, and it is over by the flare because
        // `airborne` has handed the weight back to `groundN1` by then.
        val descentN1 = lerp(FLIGHT_IDLE_N1, APPROACH_N1, 1f - smoothstep(altitudeMeters, APPROACH_ALT_M, DESCENT_ALT_M))

        // What level flight costs *at this altitude*, and the value the climb and descent terms
        // pull away from. Altitude-dependent rather than a flat cruise setting because the pitch
        // gates necessarily relax as an attitude flattens out - and the one place that happens near
        // the ground is the flare, where a cruise-thrust baseline would spool the engines back up
        // seconds before touchdown. That is the same wrong-way-round surge on short final the old
        // reverse-thrust term produced, arriving by a different route.
        val levelN1 = lerp(IDLE_N1, CRUISE_N1, smoothstep(altitudeMeters, FLARE_ALT_M, DESCENT_ALT_M))

        // Climbing and descending pull away from that baseline. They are mutually exclusive in
        // practice (pitch has one sign), so the two terms never fight.
        val airN1 = levelN1 +
            climbing * (climbN1 - levelN1) +
            descending * (descentN1 - levelN1)

        return lerp(groundN1, airN1, airborne).coerceIn(0f, 1f)
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float =
        start + (end - start) * fraction.coerceIn(0f, 1f)

    private fun smoothstep(x: Float, edge0: Float, edge1: Float): Float {
        if (abs(edge1 - edge0) < 1e-6f) return if (x >= edge1) 1f else 0f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    companion object {
        // ── The thrust schedule, as fractions of redline fan speed ───────────────────────────
        // A turbofan's usable N1 band is roughly a fifth of redline to redline; these are the
        // published-ish settings for a large twin, not a 0..1 "loudness" scale. The synth turns
        // the top of this band into most of the audible dynamic range - see EngineSoundSynth's
        // `drive`.
        /** Ground/taxi idle, and the floor a parked aircraft sits at. */
        const val IDLE_N1 = 0.20f
        /** Takeoff and initial climb - the loudest setting in the whole flight. */
        const val TAKEOFF_N1 = 1.0f
        /** After the thrust reduction altitude. */
        const val CLIMB_N1 = 0.88f
        /** Level cruise: below the climb, above everything on the way down. */
        const val CRUISE_N1 = 0.82f
        /** Descent at flight idle - higher than ground idle, as the real setting is. */
        const val FLIGHT_IDLE_N1 = 0.32f
        /** Stabilised approach, gear and flaps out. */
        const val APPROACH_N1 = 0.58f
        /** Reverse thrust during the rollout. */
        const val REVERSE_N1 = 0.78f

        // ── Gate edges ───────────────────────────────────────────────────────────────────────
        // The flight profile parks the aircraft at 5 m (altitude is to the fuselage), so the
        // ground edge has to clear that without reaching into the rotation.
        private const val GROUND_ALT_M = 25f
        private const val AIRBORNE_ALT_M = 130f

        // Crosses at mid-cruise, where `airborne` is saturated and this contributes nothing.
        private const val GROUND_SPLIT_LOW = 0.4f
        private const val GROUND_SPLIT_HIGH = 0.6f

        private const val PITCH_DEADBAND_RAD = 0.017f // ~1°, so level flight is unambiguously cruise
        private const val CLIMB_PITCH_RAD = 0.21f     // ~12°, well inside the profile's ~25° climb
        private const val DESCENT_PITCH_RAD = 0.14f   // ~8°, well inside the profile's ~15° descent

        /** ~1000 ft to ~4000 ft: the thrust reduction, spread out so it is a fade, not a step. */
        private const val THRUST_REDUCTION_START_M = 300f
        private const val THRUST_REDUCTION_END_M = 1200f

        /** Below this the descent is being flown as an approach; above it, at flight idle. */
        private const val APPROACH_ALT_M = 400f
        private const val DESCENT_ALT_M = 2500f
        /** Below this, level flight means the flare, and the flare is flown at idle. */
        private const val FLARE_ALT_M = 150f

        /** Roughly 50 kt: by here the engines are at takeoff thrust for the rest of the roll. */
        private const val TAKEOFF_SPOOL_SPEED_MPS = 25f

        // The reverse-thrust window, in true airspeed. The profile touches down at ~72 m/s and
        // decelerates to a stop, so these are crossed once each, in order: quiet through the
        // flare and touchdown, full reverse through the fast part of the rollout, stowed well
        // before taxi speed. It is a few seconds of noise, not the whole arrival.
        private const val REVERSE_DEPLOY_SPEED_MPS = 70f
        private const val REVERSE_FULL_SPEED_MPS = 62f
        private const val REVERSE_ACTIVE_SPEED_MPS = 45f
        private const val REVERSE_STOW_SPEED_MPS = 30f

        // ── Spool ────────────────────────────────────────────────────────────────────────────
        // A large turbofan takes several seconds to wind up and rather less to wind down; that
        // asymmetry is audible on every thrust change.
        private const val SPOOL_UP_TAU_S = 3.0f
        private const val SPOOL_DOWN_TAU_S = 1.8f
        /** Caps the glide a single tick can apply - see [update]. */
        private const val MAX_SPOOL_STEP_S = 0.5f
    }
}
