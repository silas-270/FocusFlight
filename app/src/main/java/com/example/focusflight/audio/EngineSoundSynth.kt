package com.example.focusflight.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * The engine-sound DSP: turns a fan speed (N1, 0f..1f, from
 * [EnginePowerModel][com.example.focusflight.domain.EnginePowerModel]) into interleaved stereo
 * PCM. Deliberately free of any Android dependency so it can be rendered and measured on the JVM -
 * see `EngineSoundSynthTest`, which checks levels and band balance rather than anyone having to
 * build the app and listen. [EngineSoundEngine] is the `AudioTrack` plumbing around it.
 *
 * See docs/engine-sound.md for the feature as a whole; this doc covers the synthesis itself.
 *
 * ## What a Trent XWB actually sounds like, and how each part is generated
 *
 * The previous version was pink noise through one low-pass plus a saturated 42–68 Hz sine. That is
 * a vacuum cleaner, and for a specific reason: a single one-pole slope over noise has no spectral
 * *shape* - no hump, no tone structure, and, being stationary, no movement. A real engine's
 * spectrum has distinct, separately-moving parts:
 *
 *  - **Jet and bypass mixing noise** - the broadband roar, from the shear between the exhaust and
 *    the air around it. Loud, low-centred, and it grows far faster than thrust does. Here:
 *    [rumbleFilter], a resonant band-pass giving the low hump that carries the weight, plus
 *    [roarL]/[roarR], a gentler low-pass over separately-seeded noise giving the mid body. Both are
 *    modulated by slow drift ([roarDriftL] and friends) - a jet breathes, and a static filtered
 *    noise bed is most of what made the old one sound like an appliance.
 *  - **Buzz-saw / multiple pure tones** - the part the brief specifically asked for. Past about
 *    N1 0.85 the fan blade tips go supersonic ([tipMach]), and each blade drags a weak shock
 *    spiralling forward up the intake. Because no two blades are identical, the pressure pattern
 *    repeats once per *shaft revolution* rather than once per blade passage, so its harmonics land
 *    on every engine order - multiples of the ~45 Hz shaft rate, mostly *below* blade-pass
 *    frequency. That is why it is heard as a deep, ragged drone rather than a whistle, and why it
 *    only shows up near full power. [BuzzSawGenerator] reproduces the mechanism rather than
 *    imitating the result: an irregular per-blade shock train around one revolution.
 *  - **Fan tone at blade-pass frequency** - N1 × 22 blades, so ~200 Hz at idle and ~1 kHz at
 *    takeoff. The whine you hear on the stand and on approach. Faded down as the buzz-saw comes up,
 *    which is what happens: at high power the broadband and the shocks bury it.
 *  - **Core/HP whine** - the thin high tone that dominates at idle and disappears under everything
 *    else at power.
 *
 * ## Two engines
 *
 * An airliner has two, and they are never at exactly the same N1. The few-Hz beat between their
 * blade-pass tones is a large part of why a real recording sounds like an aircraft and a single
 * synthesised engine does not, so [engineA] and [engineB] run at [ENGINE_N1_SPLIT] either side of
 * the commanded speed and are panned apart (with [PAN_FAR] cross-bleed, since from inside the cabin
 * you hear both).
 *
 * ## Frequencies are derived, not dialled in
 *
 * Every frequency here comes from published Trent XWB geometry - [FAN_DIAMETER_M],
 * [FAN_BLADE_COUNT], [FAN_REDLINE_SHAFT_HZ] - rather than being tuned by ear. So N1 0.2 gives a
 * 9 Hz shaft and a 198 Hz fan tone; N1 1.0 gives 45 Hz, a 990 Hz fan tone, and a blade-tip Mach of
 * about 1.25, which is what puts the buzz-saw where it belongs on the power curve instead of at an
 * arbitrary threshold.
 *
 * ## Threading
 *
 * [setFanSpeed] is safe from any thread; [render] must only ever be called from one (the audio
 * thread). Control values are recomputed once per [render] call and held constant across it -
 * standard control-rate/audio-rate split. At [EngineSoundEngine]'s block size that is every ~12 ms,
 * far faster than the engine spools, and the per-block glide on [n1] keeps even a large jump in
 * [setFanSpeed] from stepping.
 */
class EngineSoundSynth(
    private val sampleRate: Int = 44100,
    seed: Long = 0xA350_FA17L
) {

    @Volatile private var targetN1: Float = IDLE_N1

    /** The glided fan speed actually being synthesised. See [GLIDE_TAU_S]. */
    private var n1: Float = IDLE_N1

    private val pinkRoarL = PinkNoise(seed)
    private val pinkRoarR = PinkNoise(seed * 6364136223846793005L + 1442695040888963407L)
    private val pinkRumble = PinkNoise(seed xor 0x5DEECE66DL)

    private val rumbleFilter = StateVariableFilter()
    private val roarL = StateVariableFilter()
    private val roarR = StateVariableFilter()

    private val roarHighPassL = OnePoleHighPass()
    private val roarHighPassR = OnePoleHighPass()
    private val hissHighPassL = OnePoleHighPass()
    private val hissHighPassR = OnePoleHighPass()

    private val roarDriftL = DriftNoise(seed + 11, ROAR_DRIFT_HZ, sampleRate)
    private val roarDriftR = DriftNoise(seed + 23, ROAR_DRIFT_HZ, sampleRate)
    private val rumbleDrift = DriftNoise(seed + 37, RUMBLE_DRIFT_HZ, sampleRate)

    private val engineA = SingleEngine(seed + 101, sampleRate)
    private val engineB = SingleEngine(seed + 211, sampleRate)

    private var whinePhase = 0f
    private val dcBlockL = DcBlocker()
    private val dcBlockR = DcBlocker()

    /** Commanded fan speed, 0f..1f. Safe to call at any rate from any thread. */
    fun setFanSpeed(fanSpeed: Float) {
        targetN1 = fanSpeed.coerceIn(0f, 1f)
    }

    /** Drops the glide straight onto the commanded speed - for starting up, not for live changes. */
    fun snapToCommandedSpeed() {
        n1 = targetN1
    }

    /**
     * Renders [frames] stereo frames into [out] as interleaved 16-bit samples, so [out] must hold
     * at least `frames * 2` entries. Audio thread only.
     */
    fun render(out: ShortArray, frames: Int) {
        // ── Control rate: once per block ─────────────────────────────────────────────────────
        val glide = 1f - exp(-(frames.toFloat() / sampleRate) / GLIDE_TAU_S)
        n1 += (targetN1 - n1) * glide

        // 0 at idle, 1 at takeoff. N1 only spans about a fifth of its range to full, so almost
        // all of the audible change - level, brightness, bite - is driven off this rather than
        // off N1 directly.
        val drive = ((n1 - IDLE_N1) / (1f - IDLE_N1)).coerceIn(0f, 1f)

        val shaftHz = FAN_REDLINE_SHAFT_HZ * n1
        val bladePassHz = shaftHz * FAN_BLADE_COUNT
        // Rotational tip speed over the speed of sound. Crosses 1 at about N1 0.8, which is where
        // the shocks - and the drone - start to exist at all.
        val tipMach = (PI.toFloat() * FAN_DIAMETER_M * shaftHz) / SPEED_OF_SOUND_MPS
        val buzz = smoothstep(tipMach, BUZZ_ONSET_MACH, BUZZ_FULL_MACH)

        val rumbleF = svfCoefficient(RUMBLE_HZ_MIN + (RUMBLE_HZ_MAX - RUMBLE_HZ_MIN) * drive)
        val roarF = svfCoefficient(ROAR_HZ_MIN + (ROAR_HZ_MAX - ROAR_HZ_MIN) * drive * drive)
        val roarHpA = onePoleCoefficient(ROAR_HIGH_PASS_HZ)
        val hissHpA = onePoleCoefficient(HISS_HIGH_PASS_HZ)

        val rumbleGain = RUMBLE_GAIN
        val roarGain = ROAR_GAIN_MIN + (ROAR_GAIN_MAX - ROAR_GAIN_MIN) * drive
        val hissGain = HISS_GAIN_MIN + (HISS_GAIN_MAX - HISS_GAIN_MIN) * drive * drive
        // The fan tone recedes as the buzz-saw arrives - at high power the shocks and the
        // broadband roar bury the blade-pass tone that dominates at idle.
        val fanToneGain = (FAN_TONE_GAIN_MIN + (FAN_TONE_GAIN_MAX - FAN_TONE_GAIN_MIN) * drive) *
            (1f - FAN_TONE_BUZZ_DUCK * buzz)
        val buzzGain = buzz * BUZZ_GAIN
        val whineGain = WHINE_GAIN_MIN + (WHINE_GAIN_MAX - WHINE_GAIN_MIN) * (1f - drive)
        val whineIncrement = (WHINE_HZ_MIN + (WHINE_HZ_MAX - WHINE_HZ_MIN) * n1) / sampleRate

        // Loudness. Steeply super-linear because jet noise power grows with a high power of jet
        // velocity - the difference between idle and takeoff is not a fader move.
        val master = MASTER_MIN + (1f - MASTER_MIN) * drive * drive * drive

        engineA.setSpeed(shaftHz * (1f - ENGINE_N1_SPLIT), bladePassHz * (1f - ENGINE_N1_SPLIT))
        engineB.setSpeed(shaftHz * (1f + ENGINE_N1_SPLIT), bladePassHz * (1f + ENGINE_N1_SPLIT))

        // ── Audio rate ───────────────────────────────────────────────────────────────────────
        for (i in 0 until frames) {
            // Low hump: the weight of the sound, and the part a phone speaker still hints at.
            rumbleFilter.step(pinkRumble.next(), rumbleF, RUMBLE_DAMPING)
            val rumble = rumbleFilter.band * rumbleGain * (1f + RUMBLE_DRIFT_DEPTH * rumbleDrift.next())

            // Mid roar, independently seeded per channel so the bed has width instead of being a
            // single mono source pushed to both ears.
            val roarSourceL = roarHighPassL.step(pinkRoarL.next(), roarHpA)
            roarL.step(roarSourceL, roarF, ROAR_DAMPING)
            val roarOutL = roarL.low * roarGain * (1f + ROAR_DRIFT_DEPTH * roarDriftL.next())

            val roarSourceR = roarHighPassR.step(pinkRoarR.next(), roarHpA)
            roarR.step(roarSourceR, roarF, ROAR_DAMPING)
            val roarOutR = roarR.low * roarGain * (1f + ROAR_DRIFT_DEPTH * roarDriftR.next())

            // The sharp edge on top. Kept quiet on purpose: this band is what tips a jet into
            // sounding like a hairdryer if it is allowed to lead.
            val hissL = hissHighPassL.step(roarSourceL, hissHpA) * hissGain
            val hissR = hissHighPassR.step(roarSourceR, hissHpA) * hissGain

            whinePhase += whineIncrement
            if (whinePhase >= 1f) whinePhase -= 1f
            val whine = sin(TWO_PI * whinePhase) * whineGain

            val a = engineA.next(buzzGain, fanToneGain)
            val b = engineB.next(buzzGain, fanToneGain)

            val centre = rumble + whine
            val left = dcBlockL.step(centre + roarOutL + hissL + a * PAN_NEAR + b * PAN_FAR)
            val right = dcBlockR.step(centre + roarOutR + hissR + b * PAN_NEAR + a * PAN_FAR)

            out[i * 2] = toPcm(left * master)
            out[i * 2 + 1] = toPcm(right * master)
        }
    }

    private fun toPcm(sample: Float): Short =
        (tanh(sample) * Short.MAX_VALUE * HEADROOM).toInt().toShort()

    /** Chamberlin SVF tuning coefficient, clamped short of the topology's stability limit. */
    private fun svfCoefficient(cutoffHz: Float): Float {
        val safe = min(cutoffHz, sampleRate / 6f)
        return 2f * sin(PI.toFloat() * safe / sampleRate)
    }

    private fun onePoleCoefficient(cutoffHz: Float): Float =
        1f - exp(-TWO_PI * min(cutoffHz, sampleRate / 3f) / sampleRate)

    /**
     * One of the two engines: everything that is *per-engine* rather than shared, which is the
     * tonal half. The broadband bed is common to both because at any listening position it has
     * long since merged into one wash.
     */
    private class SingleEngine(seed: Long, private val sampleRate: Int) {
        private val buzzSaw = BuzzSawGenerator(seed, FAN_BLADE_COUNT)
        private val buzzLowPass = OnePoleLowPass()
        private var fanPhase = 0f
        private var shaftIncrement = 0f
        private var fanIncrement = 0f
        private var buzzShockAlpha = 0f
        private var buzzLowPassAlpha = 0f

        fun setSpeed(shaftHz: Float, bladePassHz: Float) {
            shaftIncrement = shaftHz / sampleRate
            fanIncrement = bladePassHz / sampleRate
            // Both tied to blade-pass rate: the shocks are what is being band-limited, and their
            // natural rate is one per blade. Below Nyquist by construction, since blade-pass tops
            // out near 1 kHz.
            buzzShockAlpha = onePole(bladePassHz * BUZZ_SHOCK_SOFTEN)
            buzzLowPassAlpha = onePole(bladePassHz * BUZZ_LOW_PASS)
        }

        fun next(buzzGain: Float, fanToneGain: Float): Float {
            val shocks = buzzSaw.next(shaftIncrement, buzzShockAlpha)
            val buzzOut = buzzLowPass.step(shocks, buzzLowPassAlpha) * buzzGain

            fanPhase += fanIncrement
            if (fanPhase >= 1f) fanPhase -= 1f
            // Second harmonic at a third the level - a bare sine reads as a test tone, and a real
            // blade-pass tone always carries one.
            val fundamental = sin(TWO_PI * fanPhase)
            val harmonic = sin(2f * TWO_PI * fanPhase) * FAN_TONE_HARMONIC
            return buzzOut + (fundamental + harmonic) * fanToneGain
        }

        private fun onePole(cutoffHz: Float): Float =
            1f - exp(-TWO_PI * min(cutoffHz, sampleRate / 3f) / sampleRate)
    }

    /**
     * The buzz-saw source: one weak shock per blade passage, with each blade's shock a slightly
     * different strength and leaving a slightly different pressure step behind it.
     *
     * The irregularity is the whole point, and it is drawn **once** rather than per revolution,
     * because it stands for how this particular fan was built - blade-to-blade differences in
     * setting angle and profile that do not change from turn to turn. A perfectly regular fan would
     * put all its energy at blade-pass frequency and its harmonics, i.e. a whistle around 1 kHz. An
     * irregular one produces a pattern that only repeats once per shaft revolution, so its
     * harmonics fall on every engine order - 45 Hz, 90 Hz, 135 Hz and up - which is the low, ragged
     * drone that is audible.
     *
     * [next]'s `shockSoftening` band-limits the pressure steps in place, before they can alias,
     * rather than filtering afterwards when it would be too late.
     */
    private class BuzzSawGenerator(seed: Long, private val bladeCount: Int) {
        private val rng = Rng(seed)
        private val bladeStrength = FloatArray(bladeCount) { 0.55f + rng.nextUnit() * 0.9f }
        private val bladeStep = FloatArray(bladeCount) { rng.nextBipolar() * 0.4f }
        private var revolutionPhase = 0f
        private var softened = 0f

        init {
            // Zero-mean, so the once-per-revolution staircase closes on itself and leaves no DC
            // term for the output stage to wander on.
            var sum = 0f
            for (step in bladeStep) sum += step
            val mean = sum / bladeCount
            for (i in bladeStep.indices) bladeStep[i] -= mean
        }

        fun next(shaftIncrement: Float, shockSoftening: Float): Float {
            revolutionPhase += shaftIncrement
            if (revolutionPhase >= 1f) revolutionPhase -= 1f
            val position = revolutionPhase * bladeCount
            val blade = position.toInt().coerceIn(0, bladeCount - 1)
            val withinBlade = position - blade
            val target = (withinBlade - 0.5f) * bladeStrength[blade] + bladeStep[blade]
            softened += (target - softened) * shockSoftening
            return softened
        }
    }

    /**
     * Chamberlin state-variable filter. Used rather than another cascade of one-poles because the
     * low hump needs an actual resonant peak - a slope alone is what the old version had, and it is
     * why it had no identifiable centre of gravity.
     */
    private class StateVariableFilter {
        var low = 0f; private set
        var band = 0f; private set

        fun step(input: Float, f: Float, damping: Float) {
            low += f * band
            val high = input - low - damping * band
            band += f * high
        }
    }

    private class OnePoleLowPass {
        private var state = 0f
        fun step(input: Float, alpha: Float): Float {
            state += (input - state) * alpha
            return state
        }
    }

    private class OnePoleHighPass {
        private var state = 0f
        fun step(input: Float, alpha: Float): Float {
            state += (input - state) * alpha
            return input - state
        }
    }

    /** Removes any residual DC before the soft clipper, so none of the headroom is spent on it. */
    private class DcBlocker {
        private var lastInput = 0f
        private var lastOutput = 0f
        fun step(input: Float): Float {
            val out = input - lastInput + DC_BLOCK_POLE * lastOutput
            lastInput = input
            lastOutput = out
            return out
        }
    }

    /** Paul Kellett's economy pink-noise filter over a cheap unsynchronised PRNG. */
    private class PinkNoise(seed: Long) {
        private val rng = Rng(seed)
        private var b0 = 0.0
        private var b1 = 0.0
        private var b2 = 0.0

        fun next(): Float {
            val white = rng.nextBipolar().toDouble()
            b0 = 0.99765 * b0 + white * 0.0990460
            b1 = 0.96300 * b1 + white * 0.2965164
            b2 = 0.57000 * b2 + white * 1.0526913
            return ((b0 + b1 + b2 + white * 0.1848) * 0.11).toFloat()
        }
    }

    /**
     * Very slow filtered noise, roughly unit RMS, for the wander that keeps the broadband bed from
     * sitting perfectly still. Static filtered noise is the single most appliance-like thing a
     * synthesised engine can do.
     */
    private class DriftNoise(seed: Long, rateHz: Float, sampleRate: Int) {
        private val rng = Rng(seed)
        private val alpha = 1f - exp(-TWO_PI * rateHz / sampleRate)
        // A one-pole over unit-variance noise has variance alpha/(2-alpha); undo that so `depth`
        // constants read as the fraction of modulation they actually apply.
        private val normalise = kotlin.math.sqrt((2f - alpha) / alpha)
        private var state = 0f

        fun next(): Float {
            state += (rng.nextBipolar() - state) * alpha
            return (state * normalise).coerceIn(-1f, 1f)
        }
    }

    /**
     * xorshift64*. `java.util.Random` is synchronised, and this is called several times per sample
     * per noise source - roughly a quarter of a million times a second - so its lock is not free.
     */
    private class Rng(seed: Long) {
        private var state = if (seed == 0L) -0x61c8864680b583ebL else seed

        private fun nextBits(): Long {
            state = state xor (state shl 13)
            state = state xor (state ushr 7)
            state = state xor (state shl 17)
            return state
        }

        /** Uniform in [-1, 1). */
        fun nextBipolar(): Float = (nextBits() shr 40).toFloat() * (1f / 8388608f)

        /** Uniform in [0, 1). */
        fun nextUnit(): Float = nextBipolar() * 0.5f + 0.5f
    }

    companion object {
        private const val TWO_PI = (2.0 * PI).toFloat()

        /** Mirrors `EnginePowerModel.IDLE_N1`; the bottom of the fan's usable range. */
        const val IDLE_N1 = 0.20f

        // ── Trent XWB geometry, which every frequency below is derived from ──────────────────
        /** 118 in fan. */
        private const val FAN_DIAMETER_M = 3.0f
        /** The XWB's fan has 22 blades - two more than a Trent 1000's. */
        private const val FAN_BLADE_COUNT = 22
        /** LP shaft speed at redline, ~2700 rpm, in Hz. Blade-pass is this times the blade count. */
        private const val FAN_REDLINE_SHAFT_HZ = 45f
        private const val SPEED_OF_SOUND_MPS = 340f

        /** Tip Mach where the shocks start, and where they are fully established. */
        private const val BUZZ_ONSET_MACH = 1.02f
        private const val BUZZ_FULL_MACH = 1.22f

        /** The two engines are never at the same N1; the few-Hz beat is most of the realism. */
        private const val ENGINE_N1_SPLIT = 0.003f
        private const val PAN_NEAR = 0.72f
        private const val PAN_FAR = 0.42f

        // ── Layer tuning ─────────────────────────────────────────────────────────────────────
        private const val RUMBLE_HZ_MIN = 48f
        private const val RUMBLE_HZ_MAX = 96f
        private const val RUMBLE_DAMPING = 0.9f // ≈ Q 1.1: a hump, not a whistle
        private const val RUMBLE_GAIN = 3.2f    // a resonant band-pass passes very little of its input
        private const val RUMBLE_DRIFT_HZ = 0.35f
        private const val RUMBLE_DRIFT_DEPTH = 0.14f

        private const val ROAR_HZ_MIN = 330f
        private const val ROAR_HZ_MAX = 1850f
        private const val ROAR_DAMPING = 1.45f // ≈ Q 0.7: a body, deliberately unresonant
        private const val ROAR_HIGH_PASS_HZ = 140f
        private const val ROAR_GAIN_MIN = 0.9f
        private const val ROAR_GAIN_MAX = 2.1f
        private const val ROAR_DRIFT_HZ = 1.6f
        private const val ROAR_DRIFT_DEPTH = 0.22f

        private const val HISS_HIGH_PASS_HZ = 2200f
        private const val HISS_GAIN_MIN = 0.10f
        private const val HISS_GAIN_MAX = 0.55f

        private const val FAN_TONE_GAIN_MIN = 0.055f
        private const val FAN_TONE_GAIN_MAX = 0.035f
        private const val FAN_TONE_HARMONIC = 0.33f
        private const val FAN_TONE_BUZZ_DUCK = 0.55f

        private const val BUZZ_GAIN = 0.80f
        /** Band-limits the pressure steps at generation time; see [BuzzSawGenerator]. */
        private const val BUZZ_SHOCK_SOFTEN = 1.6f
        /** Left fairly open: the rip in the drone *is* the upper engine orders. */
        private const val BUZZ_LOW_PASS = 1.7f

        private const val WHINE_HZ_MIN = 2400f
        private const val WHINE_HZ_MAX = 4800f
        private const val WHINE_GAIN_MIN = 0.008f
        private const val WHINE_GAIN_MAX = 0.030f

        /** Idle is quiet, but never silent. */
        private const val MASTER_MIN = 0.16f
        private const val HEADROOM = 0.88f
        private const val DC_BLOCK_POLE = 0.997f

        /** Anti-click glide only - the engine's actual spool lag lives in `EnginePowerModel`. */
        private const val GLIDE_TAU_S = 0.03f

        private fun smoothstep(x: Float, edge0: Float, edge1: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }
}
