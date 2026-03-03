package com.hrvib.app.data

import com.hrvib.app.data.ble.BleClient
import com.hrvib.app.data.ble.FakeBleClient
import com.hrvib.app.domain.BleDevice
import com.hrvib.app.domain.ConnectionState
import com.hrvib.app.domain.HrRrSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class BleGateway(
    private val realClient: BleClient,
    private val fakeClient: FakeBleClient,
    private val settingsStore: SettingsStore,
    private val forceFake: Boolean
) {
    private val demoModeState = MutableStateFlow(forceFake)

    fun setDemoMode(enabled: Boolean) {
        demoModeState.value = if (forceFake) true else enabled
    }

    private fun currentClient(enabled: Boolean): BleClient = if (enabled) fakeClient else realClient

    val connectionState: Flow<ConnectionState> = demoModeState.flatMapLatest { currentClient(it).connectionState }
    val scanResults: Flow<List<BleDevice>> = demoModeState.flatMapLatest { currentClient(it).scanResults }
    val samples: Flow<HrRrSample> = demoModeState.flatMapLatest { currentClient(it).samples }
    val demoMode: Flow<Boolean> = if (forceFake) flowOf(true) else settingsStore.demoMode
    val showExcluded: Flow<Boolean> = settingsStore.showExcluded

    suspend fun startScan() = currentClient(demoModeState.value).startScan()
    suspend fun stopScan() = currentClient(demoModeState.value).stopScan()
    suspend fun connect(deviceId: String) = currentClient(demoModeState.value).connect(deviceId)
    suspend fun disconnect() = currentClient(demoModeState.value).disconnect()
    suspend fun persistDemoMode(enabled: Boolean) {
        if (!forceFake) settingsStore.setDemoMode(enabled)
        demoModeState.value = if (forceFake) true else enabled
    }
    suspend fun persistShowExcluded(enabled: Boolean) = settingsStore.setShowExcluded(enabled)
    fun setFakeVector(fileName: String) {
        fakeClient.vectorName = fileName
    }
}
