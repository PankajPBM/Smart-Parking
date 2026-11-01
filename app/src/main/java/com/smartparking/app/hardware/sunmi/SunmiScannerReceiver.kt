package com.smartparking.app.hardware.sunmi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * BroadcastReceiver for handling SUNMI hardware scanner barcode/QR code scan events.
 *
 * SUNMI scanner broadcasts data via Intent when a barcode/QR is scanned.
 * This receiver captures the scan event and exposes it as a Flow for reactive consumption.
 */
class SunmiScannerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_DATA_CODE_RECEIVED) {
            val scanData = intent.getStringExtra(DATA) ?: ""
            val source = intent.getStringExtra(SOURCE) ?: "unknown"

            if (scanData.isNotEmpty()) {
                // Emit scan result to Flow subscribers
                instance?.emitScan(ScanResult(scanData, source))
            }
        }
    }

    data class ScanResult(
        val data: String,
        val source: String, // "scanner" or "camera"
        val timestamp: Long = System.currentTimeMillis()
    )

    companion object {
        // SUNMI Scanner Intent Actions and Extras
        const val ACTION_DATA_CODE_RECEIVED = "com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED"
        const val DATA = "data"
        const val SOURCE = "source"

        private val _scanFlow = MutableSharedFlow<ScanResult>(extraBufferCapacity = 10)
        val scanFlow: SharedFlow<ScanResult> = _scanFlow.asSharedFlow()

        private var instance: SunmiScannerReceiver? = null

        init {
            instance = SunmiScannerReceiver()
        }

        private fun emitScan(result: ScanResult) {
            _scanFlow.tryEmit(result)
        }
    }
}

/**
 * Manager for SUNMI Hardware Scanner Control.
 * Provides programmatic trigger/stop commands for handheld scanner devices.
 */
class SunmiScannerManager(private val context: Context) {

    /**
     * Triggers hardware scanner (laser/camera) on supported SUNMI devices.
     */
    fun triggerScan() {
        try {
            val intent = Intent("com.sunmi.scanner.ACTION_SCAN")
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Stops active scan session.
     */
    fun stopScan() {
        try {
            val intent = Intent("com.sunmi.scanner.ACTION_STOP_SCAN")
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Configures scanner settings (beep, vibrate, light).
     * Values: 0 = OFF, 1 = ON
     */
    fun configureScannerSettings(
        enableBeep: Boolean = true,
        enableVibrate: Boolean = true,
        enableLight: Boolean = true
    ) {
        try {
            val intent = Intent("com.sunmi.scanner.ACTION_SETTING").apply {
                putExtra("beep", if (enableBeep) 1 else 0)
                putExtra("vibrate", if (enableVibrate) 1 else 0)
                putExtra("light", if (enableLight) 1 else 0)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Returns a Flow of scan results for reactive observation.
     */
    fun observeScans(): SharedFlow<SunmiScannerReceiver.ScanResult> {
        return SunmiScannerReceiver.scanFlow
    }
}
