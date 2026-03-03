package com.hrvib.app.data.ble

import android.content.Context
import com.hrvib.app.domain.BleDevice
import com.hrvib.app.domain.ConnectionState
import com.hrvib.app.domain.HrRrSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class FakeBleClient(
    private val context: Context,
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : BleClient {
    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    override val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()

    private val _scanResults = MutableStateFlow<List<BleDevice>>(emptyList())
    override val scanResults: Flow<List<BleDevice>> = _scanResults.asStateFlow()

    private val sampleState = MutableStateFlow<HrRrSample?>(null)
    override val samples: Flow<HrRrSample> = sampleState.filterNotNull()

    private var replayJob: Job? = null
    var vectorName: String = "hr_rr_valid.json"

    override suspend fun startScan() {
        _connectionState.value = ConnectionState.Scanning
        delay(500)
        _scanResults.value = listOf(BleDevice("fake-polar-h10", "Fake Polar H10", -42))
    }

    override suspend fun stopScan() {
        if (_connectionState.value == ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    override suspend fun connect(deviceId: String) {
        _connectionState.value = ConnectionState.Connecting
        delay(500)
        _connectionState.value = ConnectionState.Connected
        replay(vectorName)
    }

    override suspend fun disconnect() {
        replayJob?.cancel()
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun replay(fileName: String) {
        replayJob?.cancel()
        replayJob = ioScope.launch {
            eventsFromAsset(fileName).collect { event ->
                when (event.type) {
                    "sample" -> {
                        val now = System.currentTimeMillis()
                        sampleState.value = HrRrSample(
                            timestampMs = now,
                            hrBpm = event.hr,
                            rrIntervalsMs = event.rr ?: emptyList()
                        )
                        delay(event.delayMs)
                    }
                    "disconnect" -> {
                        _connectionState.value = ConnectionState.Disconnected
                        delay(event.delayMs)
                        _connectionState.value = ConnectionState.Connected
                    }
                }
            }
        }
    }

    private fun eventsFromAsset(fileName: String): Flow<FakeEvent> = flow {
        val text = context.assets.open("vectors/$fileName").bufferedReader().use { it.readText() }
        Json.decodeFromString<List<FakeEvent>>(text).forEach { emit(it) }
    }

    @Serializable
    data class FakeEvent(
        val type: String,
        val delayMs: Long = 1000,
        val hr: Int? = null,
        val rr: List<Double>? = null
    )
}
