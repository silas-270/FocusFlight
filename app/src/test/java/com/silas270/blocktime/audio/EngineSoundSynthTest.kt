package com.silas270.blocktime.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Measures the synthesised signal instead of asserting on the code that produces it - the point of
 * keeping [EngineSoundSynth] free of Android types is that its output can be rendered and analysed
 * on the JVM, so "does this still sound like an engine" is a check rather than a listening session.
 *
 * The band-balance assertions are the ones that matter. The complaint that started this work was
 * that the previous version sounded like a vacuum cleaner, and that has a measurable signature: a
 * hiss-forward spectrum with nothing holding down the bottom. So the tests below pin the shape -
 * low band dominant at every power setting - rather than pinning the constants that produce it.
 */
class EngineSoundSynthTest {

    private val sampleRate = 44100

    private fun render(fanSpeed: Float, seconds: Float = 1.5f): Pair<FloatArray, FloatArray> {
        val synth = EngineSoundSynth(sampleRate)
        synth.setFanSpeed(fanSpeed)
        synth.snapToCommandedSpeed()

        val blockFrames = 512
        val block = ShortArray(blockFrames * 2)
        val totalFrames = (sampleRate * seconds).toInt()
        val left = FloatArray(totalFrames)
        val right = FloatArray(totalFrames)

        var written = 0
        while (written < totalFrames) {
            synth.render(block, blockFrames)
            val take = minOf(blockFrames, totalFrames - written)
            for (i in 0 until take) {
                left[written + i] = block[i * 2] / 32768f
                right[written + i] = block[i * 2 + 1] / 32768f
            }
            written += take
        }
        return left to right
    }

    private fun FloatArray.rms(): Float {
        var sum = 0.0
        for (v in this) sum += v.toDouble() * v
        return sqrt(sum / size).toFloat()
    }

    private fun FloatArray.peak(): Float {
        var max = 0f
        for (v in this) max = maxOf(max, abs(v))
        return max
    }

    /**
     * Energy in a narrow band around [centreHz], by direct correlation against a windowed
     * sine/cosine pair. A full FFT would be more efficient, but nothing here needs a whole spectrum
     * - only a handful of probe frequencies - and this keeps the test dependency-free.
     */
    private fun FloatArray.energyAt(centreHz: Float, sampleRate: Int): Float {
        var re = 0.0
        var im = 0.0
        val w = 2.0 * PI * centreHz / sampleRate
        for (i in indices) {
            // Hann window, so the probe has a defined bandwidth instead of leaking across the
            // whole spectrum.
            val window = 0.5 - 0.5 * cos(2.0 * PI * i / (size - 1))
            re += this[i] * window * cos(w * i)
            im += this[i] * window * sin(w * i)
        }
        return (sqrt(re * re + im * im) / size).toFloat()
    }

    /** Total energy across a band, sampled at a few probe points. */
    private fun FloatArray.bandEnergy(fromHz: Float, toHz: Float, probes: Int = 12): Float {
        var total = 0f
        repeat(probes) {
            val f = fromHz + (toHz - fromHz) * (it + 0.5f) / probes
            total += this.energyAt(f, sampleRate)
        }
        return total / probes
    }

    private val powerSettings = listOf(0.20f, 0.32f, 0.58f, 0.82f, 0.88f, 1.0f)

    @Test
    fun `never clips or goes silent, at any power setting`() {
        powerSettings.forEach { power ->
            val (left, right) = render(power)
            listOf("left" to left, "right" to right).forEach { (name, channel) ->
                val peak = channel.peak()
                val rms = channel.rms()
                assertTrue("$name at N1 $power peaked at $peak - clipping", peak < 0.99f)
                assertTrue("$name at N1 $power is silent (rms $rms)", rms > 0.005f)
                assertTrue("$name at N1 $power is far too hot (rms $rms)", rms < 0.5f)
                assertTrue("$name at N1 $power produced a non-finite sample", channel.all { it.isFinite() })
            }
        }
    }

    @Test
    fun `gets louder with power, and by a lot`() {
        val levels = powerSettings.map { it to render(it).first.rms() }
        levels.zipWithNext { (lowN1, low), (highN1, high) ->
            assertTrue(
                "N1 $highN1 (rms $high) should be at least as loud as N1 $lowN1 (rms $low)",
                high >= low * 0.98f
            )
        }
        val idle = levels.first().second
        val takeoff = levels.last().second
        assertTrue(
            "takeoff (rms $takeoff) should tower over idle (rms $idle) - a jet is not a fader",
            takeoff > idle * 4f
        )
    }

    @Test
    fun `the spectrum is low-heavy at every power setting, which is what a jet is and a vacuum is not`() {
        powerSettings.forEach { power ->
            val left = render(power).first
            val low = left.bandEnergy(40f, 300f)
            val high = left.bandEnergy(3000f, 9000f)
            assertTrue(
                "at N1 $power the low band ($low) should dominate the high band ($high); a " +
                    "hiss-forward balance is the vacuum-cleaner signature this replaces",
                low > high * 4f
            )
        }
    }

    @Test
    fun `gets brighter with power without ever becoming hiss-led`() {
        val idleTilt = render(0.20f).first.let { it.bandEnergy(700f, 2500f) / it.bandEnergy(40f, 300f) }
        val takeoffTilt = render(1.0f).first.let { it.bandEnergy(700f, 2500f) / it.bandEnergy(40f, 300f) }
        assertTrue(
            "takeoff should carry proportionally more high-frequency energy than idle " +
                "($takeoffTilt vs $idleTilt)",
            takeoffTilt > idleTilt
        )
    }

    /**
     * The two engines sit a few tenths of a percent either side of the commanded speed, so a probe
     * placed exactly on the nominal frequency falls *between* both tones at this analysis
     * resolution and reads the gap rather than the peak. Every tonal measurement below probes both.
     */
    private fun FloatArray.tonePairEnergy(nominalHz: Float): Float = maxOf(
        energyAt(nominalHz * 0.997f, sampleRate),
        energyAt(nominalHz * 1.003f, sampleRate)
    )

    @Test
    fun `the buzz-saw appears only near full power, and lands on the engine orders`() {
        // The signature of a supersonic fan is energy piled onto multiples of the *shaft* rate -
        // 45 Hz x N1 - rather than only onto blade-pass. So the test is whether the engine orders
        // stand above the broadband between them, and whether that only happens once the tips are
        // actually supersonic (about N1 0.85 for a 3 m fan at 45 Hz).
        fun engineOrderProminence(power: Float): Float {
            val left = render(power).first
            val shaftHz = 45f * power
            // Orders 8-16: clear of the resonant rumble band below, clear of blade-pass above.
            val onOrder = (8..16).map { left.tonePairEnergy(shaftHz * it) }.average().toFloat()
            val between = (8..16).map { left.energyAt(shaftHz * (it + 0.5f), sampleRate) }
                .average().toFloat()
            return onOrder / between
        }

        val takeoff = engineOrderProminence(1.0f)
        val climb = engineOrderProminence(0.88f)
        val cruise = engineOrderProminence(0.82f)
        val idle = engineOrderProminence(0.20f)

        assertTrue(
            "at takeoff the engine orders should stand clearly above the broadband between them, " +
                "got a prominence of $takeoff",
            takeoff > 3f
        )
        assertTrue("takeoff ($takeoff) should out-buzz the climb ($climb)", takeoff > climb)
        assertTrue(
            "at cruise the tips are subsonic, so there should be no engine-order structure - " +
                "got $cruise",
            cruise < 2f
        )
        assertTrue("at idle there should be none either - got $idle", idle < 2f)
    }

    @Test
    fun `the blade-pass tone tracks fan speed`() {
        // 22 blades, 45 Hz shaft at redline. If the tone is really being derived from the geometry
        // rather than dialled in, the peak moves with N1.
        listOf(0.3f, 0.6f, 0.82f).forEach { power ->
            val left = render(power).first
            val bladePass = 45f * power * 22f
            val onTone = left.tonePairEnergy(bladePass)
            // Compared against the broadband immediately either side, close enough that the bed's
            // own spectral slope between the probes is negligible - so this measures a peak rather
            // than just "there is sound here".
            val neighbours = (left.energyAt(bladePass * 0.90f, sampleRate) +
                left.energyAt(bladePass * 1.10f, sampleRate)) / 2f
            assertTrue(
                "at N1 $power expected a blade-pass peak at ${bladePass}Hz ($onTone) above the " +
                    "surrounding broadband ($neighbours)",
                onTone > neighbours * 2f
            )
        }
    }

    @Test
    fun `the two engines are panned apart rather than the same signal twice`() {
        val (left, right) = render(0.9f)
        var dot = 0.0
        for (i in left.indices) dot += left[i].toDouble() * right[i]
        val correlation = (dot / (left.size * left.rms() * right.rms())).toFloat()
        assertTrue("channels are identical (correlation $correlation) - no stereo image", correlation < 0.97f)
        assertTrue("channels are unrelated (correlation $correlation) - the image should still be centred", correlation > 0.1f)
    }

    @Test
    fun `a large jump in commanded power cannot produce a click`() {
        val synth = EngineSoundSynth(sampleRate)
        synth.setFanSpeed(0.2f)
        synth.snapToCommandedSpeed()
        val block = ShortArray(1024)
        repeat(40) { synth.render(block, 512) }

        // Straight from idle to takeoff between one block and the next.
        synth.setFanSpeed(1.0f)
        var previous = block[1022] / 32768f
        var maxStep = 0f
        repeat(200) {
            synth.render(block, 512)
            for (i in 0 until 512) {
                val sample = block[i * 2] / 32768f
                maxStep = maxOf(maxStep, abs(sample - previous))
                previous = sample
            }
        }
        // Broadband noise legitimately moves fast sample to sample; a click is a step far larger
        // than the signal's own peak-to-peak swing.
        assertTrue("sample-to-sample step of $maxStep looks like a discontinuity", maxStep < 0.9f)
    }
}
