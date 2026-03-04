package com.hrvib.app.data.ble

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.getSystemService
import androidx.core.content.ContextCompat
import com.hrvib.app.domain.BleDevice
import com.hrvib.app.domain.ConnectionState
import com.hrvib.app.domain.HrRrSample
import com.hrvib.app.domain.PolarHrParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class RealBleClient(
    private val context: Context
) : BleClient {
    private val manager = context.getSystemService<BluetoothManager>()
    private val adapter: BluetoothAdapter? = manager?.adapter
    private var gatt: BluetoothGatt? = null
    private val discovered = LinkedHashMap<String, BleDevice>()

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    override val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()

    private val _scanResults = MutableStateFlow<List<BleDevice>>(emptyList())
    override val scanResults: Flow<List<BleDevice>> = _scanResults.asStateFlow()

    private val _samples = MutableSharedFlow<HrRrSample>(extraBufferCapacity = 64)
    override val samples: Flow<HrRrSample> = _samples.asSharedFlow()

    private val hrServiceUuid = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val hrMeasurementUuid = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private fun hasBlePermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasBlePermission(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            true
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasBlePermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun startScan() {
        if (!hasScanPermission()) {
            _connectionState.value = ConnectionState.Disconnected
            return
        }
        val scanner = adapter?.bluetoothLeScanner ?: return
        _connectionState.value = ConnectionState.Scanning
        scanner.startScan(null, ScanSettings.Builder().build(), scanCallback)
    }

    @SuppressLint("MissingPermission")
    override suspend fun stopScan() {
        if (!hasScanPermission()) return
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (_connectionState.value == ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(deviceId: String) {
        if (!hasConnectPermission()) {
            _connectionState.value = ConnectionState.Disconnected
            return
        }
        stopScan()
        val device = adapter?.getRemoteDevice(deviceId) ?: return
        _connectionState.value = ConnectionState.Connecting
        gatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() {
        if (!hasConnectPermission()) return
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connectionState.value = ConnectionState.Disconnected
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = device.name ?: return
            if (!name.contains("polar", ignoreCase = true)) return
            discovered[device.address] = BleDevice(device.address, name, result.rssi)
            _scanResults.value = discovered.values.toList()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!hasConnectPermission()) {
                _connectionState.value = ConnectionState.Disconnected
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = ConnectionState.Connected
                gatt.discoverServices()
            } else {
                _connectionState.value = ConnectionState.Disconnected
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!hasConnectPermission()) {
                _connectionState.value = ConnectionState.Disconnected
                return
            }
            val characteristic = gatt.getService(hrServiceUuid)?.getCharacteristic(hrMeasurementUuid) ?: return
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(cccdUuid) ?: return
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == hrMeasurementUuid) {
                _samples.tryEmit(PolarHrParser.parse(value, System.currentTimeMillis()))
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == hrMeasurementUuid) {
                _samples.tryEmit(
                    PolarHrParser.parse(
                        characteristic.value ?: return,
                        System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
