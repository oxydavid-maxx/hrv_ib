package com.hrvib.app.data.ble

import com.hrvib.app.domain.BleDevice
import com.hrvib.app.domain.ConnectionState
import com.hrvib.app.domain.HrRrSample
import kotlinx.coroutines.flow.Flow

interface BleClient {
    val connectionState: Flow<ConnectionState>
    val scanResults: Flow<List<BleDevice>>
    val samples: Flow<HrRrSample>

    suspend fun startScan()
    suspend fun stopScan()
    suspend fun connect(deviceId: String)
    suspend fun disconnect()
}
