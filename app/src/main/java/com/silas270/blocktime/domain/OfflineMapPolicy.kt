package com.silas270.blocktime.domain

import com.silas270.blocktime.data.network.NetworkMode
import com.silas270.blocktime.engine.live.CesiumLiveJniBridge

/**
 * The map style the live globe should actually show. While the app is offline the network
 * styles can't load tiles, so the bundled vector map takes over. The pilot's own choice
 * ([preferredStyle]) is never overwritten, so it comes back as soon as the app is online again.
 */
fun resolveEffectiveMapStyle(preferredStyle: Int, isOffline: Boolean): Int =
    if (isOffline) CesiumLiveJniBridge.MAP_STYLE_OFFLINE else preferredStyle

/** A short in-flight notice telling the pilot the map switched on its own. */
sealed interface NetworkNotice {
    /** The globe fell back to the offline map. [dataSaver] means the pilot turned it on in Settings. */
    data class SwitchedToOffline(val dataSaver: Boolean) : NetworkNotice

    /** Back online; the globe returned to [restoredStyle], one of the `MAP_STYLE_*` ids. */
    data class RestoredMap(val restoredStyle: Int) : NetworkNotice
}

/**
 * The notice to show when the network mode goes from [previous] to [current], or null when the
 * map doesn't visibly change: offline stayed offline (e.g. data saver turned on during an outage),
 * or the pilot already chose the offline map anyway.
 */
fun networkNoticeFor(previous: NetworkMode, current: NetworkMode, preferredStyle: Int): NetworkNotice? = when {
    previous.isOffline == current.isOffline -> null
    preferredStyle == CesiumLiveJniBridge.MAP_STYLE_OFFLINE -> null
    current.isOffline -> NetworkNotice.SwitchedToOffline(dataSaver = current == NetworkMode.OFFLINE_DATA_SAVER)
    else -> NetworkNotice.RestoredMap(restoredStyle = preferredStyle)
}
