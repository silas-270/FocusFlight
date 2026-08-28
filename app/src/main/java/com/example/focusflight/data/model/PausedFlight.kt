package com.example.focusflight.data.model

/** A saved camera perspective: mode (0=Free/1=Tracking/2=Cockpit) plus position/rotation,
 *  matching CesiumLiveJniBridge.nativeGetCameraPose()'s array layout. */
data class CameraPose(
    val mode: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    val qx: Double,
    val qy: Double,
    val qz: Double,
    val qw: Double
)

/**
 * Everything needed to resume an in-progress (paused) flight - the booking (which flight, from
 * where, to where, how long, under which mode/challenge) plus the live state accumulated while
 * flying it (elapsed time, camera framing). One instance per "slot" that can hold a paused
 * flight: the global Story/Free slot ([com.example.focusflight.data.repository
 * .PreferencesRepository.getPausedFlight]) or a Route challenge's own row ([Challenge
 * .pausedFlight]) - so nothing about resuming a flight is ever split across separately-keyed
 * stores (that split is what let two paused flights to the same destination silently collide and
 * corrupt each other's progress/camera, since flight numbers are derived from destination alone
 * and aren't globally unique).
 */
data class PausedFlight(
    val flightNumber: String,
    val originIata: String,
    val destIata: String,
    val durationMin: Int,
    val mode: FlightMode,
    val challengeId: Int? = null,
    /** Elapsed flight time in ms, or null if this flight has never ticked/been saved yet (a
     *  fresh check-in). */
    val elapsedMs: Long? = null,
    /** The saved camera framing, or null if this flight has never had one saved (e.g. the player
     *  never backgrounded it before it landed). */
    val camera: CameraPose? = null
) {
    fun serialize(): String = listOf(
        flightNumber, originIata, destIata, durationMin.toString(), mode.name,
        challengeId?.toString() ?: "",
        elapsedMs?.toString() ?: "",
        camera?.mode?.toString() ?: "",
        camera?.x?.toString() ?: "",
        camera?.y?.toString() ?: "",
        camera?.z?.toString() ?: "",
        camera?.qx?.toString() ?: "",
        camera?.qy?.toString() ?: "",
        camera?.qz?.toString() ?: "",
        camera?.qw?.toString() ?: ""
    ).joinToString("|")

    companion object {
        private const val FIELD_COUNT = 15

        fun parse(raw: String): PausedFlight? {
            val p = raw.split("|")
            if (p.size != FIELD_COUNT) return null
            val durationMin = p[3].toIntOrNull() ?: return null
            val mode = runCatching { FlightMode.valueOf(p[4]) }.getOrDefault(FlightMode.STORY)
            val challengeId = p[5].toIntOrNull()
            val elapsedMs = p[6].toLongOrNull()
            val camera = if (p[7].isNotBlank()) {
                CameraPose(
                    mode = p[7].toIntOrNull() ?: return null,
                    x = p[8].toDoubleOrNull() ?: return null,
                    y = p[9].toDoubleOrNull() ?: return null,
                    z = p[10].toDoubleOrNull() ?: return null,
                    qx = p[11].toDoubleOrNull() ?: return null,
                    qy = p[12].toDoubleOrNull() ?: return null,
                    qz = p[13].toDoubleOrNull() ?: return null,
                    qw = p[14].toDoubleOrNull() ?: return null
                )
            } else {
                null
            }
            return PausedFlight(p[0], p[1], p[2], durationMin, mode, challengeId, elapsedMs, camera)
        }
    }
}
