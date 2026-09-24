package com.example.focusflight.domain

import com.example.focusflight.data.network.NetworkMode
import com.example.focusflight.engine.live.CesiumLiveJniBridge.MAP_STYLE_OFFLINE
import com.example.focusflight.engine.live.CesiumLiveJniBridge.MAP_STYLE_SATELLITE_TERRAIN
import com.example.focusflight.engine.live.CesiumLiveJniBridge.MAP_STYLE_STANDARD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineMapPolicyTest {

    private val allStyles = listOf(MAP_STYLE_STANDARD, MAP_STYLE_SATELLITE_TERRAIN, MAP_STYLE_OFFLINE)

    @Test
    fun `online keeps the preferred style`() {
        allStyles.forEach { assertEquals(it, resolveEffectiveMapStyle(it, isOffline = false)) }
    }

    @Test
    fun `offline always shows the offline map`() {
        allStyles.forEach { assertEquals(MAP_STYLE_OFFLINE, resolveEffectiveMapStyle(it, isOffline = true)) }
    }

    @Test
    fun `losing the connection announces the switch to the offline map`() {
        assertEquals(
            NetworkNotice.SwitchedToOffline(dataSaver = false),
            networkNoticeFor(NetworkMode.ONLINE, NetworkMode.OFFLINE_NO_CONNECTION, MAP_STYLE_STANDARD)
        )
        assertEquals(
            NetworkNotice.SwitchedToOffline(dataSaver = true),
            networkNoticeFor(NetworkMode.ONLINE, NetworkMode.OFFLINE_DATA_SAVER, MAP_STYLE_SATELLITE_TERRAIN)
        )
    }

    @Test
    fun `coming back online announces the restored style`() {
        assertEquals(
            NetworkNotice.RestoredMap(MAP_STYLE_SATELLITE_TERRAIN),
            networkNoticeFor(NetworkMode.OFFLINE_NO_CONNECTION, NetworkMode.ONLINE, MAP_STYLE_SATELLITE_TERRAIN)
        )
    }

    @Test
    fun `no notice when the map does not visibly change`() {
        // Pilot already chose the offline map.
        assertNull(networkNoticeFor(NetworkMode.ONLINE, NetworkMode.OFFLINE_NO_CONNECTION, MAP_STYLE_OFFLINE))
        assertNull(networkNoticeFor(NetworkMode.OFFLINE_NO_CONNECTION, NetworkMode.ONLINE, MAP_STYLE_OFFLINE))
        // Offline for a different reason is still offline.
        assertNull(networkNoticeFor(NetworkMode.OFFLINE_NO_CONNECTION, NetworkMode.OFFLINE_DATA_SAVER, MAP_STYLE_STANDARD))
        assertNull(networkNoticeFor(NetworkMode.ONLINE, NetworkMode.ONLINE, MAP_STYLE_STANDARD))
    }
}
