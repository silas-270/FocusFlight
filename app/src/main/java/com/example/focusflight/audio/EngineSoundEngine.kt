package com.example.focusflight.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process

/**
 * Streams [EngineSoundSynth]'s output to the device, driven continuously by [setEnginePower]
 * (0f..1f fan speed - see [EnginePowerModel][com.example.focusflight.domain.EnginePowerModel]).
 *
 * This class is only the plumbing; all of the sound is in [EngineSoundSynth], which is kept free of
 * Android types so it can be rendered and measured off-device. What lives here is the `AudioTrack`,
 * the audio thread, and the lifecycle.
 *
 * `MODE_STREAM` rather than a static looped buffer, because the synth's output depends on the
 * current power setting and has to be produced continuously. Stereo, because the two engines are
 * panned apart - see [EngineSoundSynth]'s note on why that matters.
 *
 * [start]/[stop] are idempotent and cheap to call from Compose lifecycle effects. [setEnginePower]
 * is safe to call from any thread at any rate; it stores the latest target and the audio thread
 * glides toward it, so even a large jump cannot click.
 */
class EngineSoundEngine {

    private val synth = EngineSoundSynth(SAMPLE_RATE)

    @Volatile private var running: Boolean = false
    private var thread: Thread? = null

    /** Commanded fan speed, 0f..1f. */
    fun setEnginePower(power: Float) {
        synth.setFanSpeed(power)
    }

    fun start() {
        if (running) return
        running = true
        // Whatever power the flight is already at is where the engine should come back in - a
        // resumed cruise should not spool up from idle just because the screen was backgrounded.
        synth.snapToCommandedSpeed()
        thread = Thread(::runLoop, "EngineSoundEngine").apply { start() }
    }

    fun stop() {
        if (!running) return
        running = false
        thread?.join(JOIN_TIMEOUT_MS)
        thread = null
    }

    private fun runLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
        )
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf * 4, BLOCK_FRAMES * BYTES_PER_FRAME * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            e.printStackTrace()
            running = false
            return
        }

        track.play()

        // Interleaved stereo, so two shorts per frame.
        val block = ShortArray(BLOCK_FRAMES * 2)
        while (running) {
            synth.render(block, BLOCK_FRAMES)
            track.write(block, 0, block.size)
        }

        try {
            track.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        track.release()
    }

    private companion object {
        const val SAMPLE_RATE = 44100
        const val BYTES_PER_FRAME = 4 // stereo, 16-bit

        /** ~12 ms. Also the synth's control rate - fast enough that no gain change steps. */
        const val BLOCK_FRAMES = 512

        const val JOIN_TIMEOUT_MS = 500L
    }
}
