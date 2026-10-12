package com.hearthelp.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class BandScanner(private val ctx: Context) {
    data class Found(val name: String, val address: String)

    @SuppressLint("MissingPermission")
    fun scan(): Flow<Found> = callbackFlow {
        val scanner = ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("Bluetooth is off"))
            return@callbackFlow
        }
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, r: ScanResult) {
                trySend(Found(r.device.name ?: r.scanRecord?.deviceName ?: "Unknown device", r.device.address))
            }
            override fun onScanFailed(errorCode: Int) { close(IllegalStateException("Scan failed ($errorCode)")) }
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(HeartRateSource.HR_SERVICE)).build()
        scanner.startScan(listOf(filter), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
        awaitClose { scanner.stopScan(cb) }
    }
}
