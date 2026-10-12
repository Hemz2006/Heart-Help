package com.hearthelp.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

/** Streams bpm from a BLE Heart Rate Profile device. autoConnect=true makes Android reconnect after drops. */
class HeartRateSource(private val ctx: Context) {
    companion object {
        val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    @SuppressLint("MissingPermission")
    fun readings(address: String): Flow<Int> = callbackFlow {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            close(IllegalStateException("Bluetooth is off"))
            return@callbackFlow
        }
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) gatt.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val ch = gatt.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT) ?: return
                gatt.setCharacteristicNotification(ch, true)
                val d = ch.getDescriptor(CCCD) ?: return
                if (Build.VERSION.SDK_INT >= 33) {
                    gatt.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(d)
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
                HeartRateParser.parse(value)?.let { trySend(it) }
            }

            @Deprecated("Pre-API 33 callback")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT < 33) HeartRateParser.parse(ch.value ?: return)?.let { trySend(it) }
            }
        }
        val gatt = adapter.getRemoteDevice(address).connectGatt(ctx, true, callback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) close(IllegalStateException("Could not connect to band"))
        awaitClose { gatt?.close() }
    }
}
