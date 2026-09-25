package com.silas270.blocktime.data.network

import com.silas270.blocktime.data.repository.PreferencesRepository
import com.silas270.blocktime.testutil.FakeSharedPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineModeControllerTest {

    @Test
    fun `resolveNetworkMode covers every combination, data saver winning`() {
        assertEquals(NetworkMode.ONLINE, resolveNetworkMode(connected = true, dataSaver = false))
        assertEquals(NetworkMode.OFFLINE_NO_CONNECTION, resolveNetworkMode(connected = false, dataSaver = false))
        assertEquals(NetworkMode.OFFLINE_DATA_SAVER, resolveNetworkMode(connected = true, dataSaver = true))
        assertEquals(NetworkMode.OFFLINE_DATA_SAVER, resolveNetworkMode(connected = false, dataSaver = true))
    }

    @Test
    fun `mode follows connectivity and the data saver setting`() = runTest(UnconfinedTestDispatcher()) {
        val connected = MutableStateFlow(true)
        val prefs = PreferencesRepository(FakeSharedPreferences())
        val controller = OfflineModeController(connected, prefs, backgroundScope)

        assertEquals(NetworkMode.ONLINE, controller.mode.value)
        assertFalse(controller.isOffline.value)

        connected.value = false
        assertEquals(NetworkMode.OFFLINE_NO_CONNECTION, controller.mode.value)
        assertTrue(controller.isOffline.value)

        connected.value = true
        controller.setDataSaverEnabled(true)
        assertEquals(NetworkMode.OFFLINE_DATA_SAVER, controller.mode.value)
        assertTrue(prefs.isOfflineDataSaverEnabled())

        controller.setDataSaverEnabled(false)
        assertEquals(NetworkMode.ONLINE, controller.mode.value)
    }

    @Test
    fun `data saver is restored from preferences`() = runTest(UnconfinedTestDispatcher()) {
        val prefs = PreferencesRepository(FakeSharedPreferences()).apply { setOfflineDataSaverEnabled(true) }
        val controller = OfflineModeController(MutableStateFlow(true), prefs, backgroundScope)

        assertTrue(controller.dataSaverEnabled.value)
        assertEquals(NetworkMode.OFFLINE_DATA_SAVER, controller.mode.value)
    }
}
