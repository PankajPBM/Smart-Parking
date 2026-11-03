package com.smartparking.app.hardware.sunmi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * BroadcastReceiver for handling SUNMI hardware scanner barcode/QR code scan events.
 *
 * Supports SUNMI V2, V2 Pro, V2s, V3, V3 Mix, L2, and all SUNMI OS versions (Android 7.1 through 13+).
 * Captures broadcast intents from both internal 2D laser scan engines and camera barcode decoders.
 */
class SunmiScannerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "Scanner broadcast received: action=$action")

        if (action in SUPPORTED_ACTIONS) {
            // Extract barcode data across all known SUNMI firmware intent extra keys
            val scanData = intent.getStringExtra(EXTRA_DATA)
                ?: intent.getStringExtra(EXTRA_DATA_STRING)
                ?: intent.getStringExtra(EXTRA_BARCODE_DATA)
                ?: intent.getStringExtra(EXTRA_BARCODE)
                ?: intent.getStringExtra(EXTRA_VALUE)
                ?: intent.getStringExtra(EXTRA_SCANNER_DATA)
                ?: intent.getByteArrayExtra(EXTRA_DATA)?.let { String(it).trim() }
                ?: ""

            val source = intent.getStringExtra(EXTRA_SOURCE) ?: "scanner"

            if (scanData.isNotBlank()) {
                val cleanData = scanData.trim()
                Log.i(TAG, "Scanned code received: '$cleanData' (source: $source)")
                emitScan(ScanResult(cleanData, source))
            }
        }
    }

    data class ScanResult(
        val data: String,
        val source: String, // "scanner" or "camera"
        val timestamp: Long = System.currentTimeMillis()
    )

    companion object {
        private const val TAG = "SunmiScannerReceiver"

        // SUNMI Scanner Intent Actions
        const val ACTION_DATA_CODE_RECEIVED = "com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED"
        const val ACTION_SUNMI_SCAN_RESULT = "com.sunmi.action.SCAN_RESULT"
        const val ACTION_GENERIC_SCAN_RESULT = "android.intent.action.SCANRESULT"
        const val ACTION_SUNMI_ALT = "com.sunmi.scan.ACTION_DATA_CODE_RECEIVED"

        val SUPPORTED_ACTIONS = setOf(
            ACTION_DATA_CODE_RECEIVED,
            ACTION_SUNMI_SCAN_RESULT,
            ACTION_GENERIC_SCAN_RESULT,
            ACTION_SUNMI_ALT
        )

        // Extra Keys
        const val EXTRA_DATA = "data"
        const val EXTRA_DATA_STRING = "data_string"
        const val EXTRA_BARCODE_DATA = "barcode_data"
        const val EXTRA_BARCODE = "barcode"
        const val EXTRA_VALUE = "value"
        const val EXTRA_SCANNER_DATA = "scannerdata"
        const val EXTRA_SOURCE = "source"

        private val _scanFlow = MutableSharedFlow<ScanResult>(extraBufferCapacity = 20)
        val scanFlow: SharedFlow<ScanResult> = _scanFlow.asSharedFlow()

        fun emitScan(result: ScanResult) {
            _scanFlow.tryEmit(result)
        }
    }
}

/**
 * Manager for SUNMI Hardware Scanner Control.
 * Provides programmatic trigger/stop commands and feedback configuration for handheld devices.
 */
class SunmiScannerManager(private val context: Context) {

    /**
     * Triggers hardware scanner (laser/camera) on supported SUNMI devices.
     */
    fun triggerScan() {
        try {
            // Send primary SUNMI scanner trigger broadcast
            val intent1 = Intent("com.sunmi.scanner.ACTION_SCAN")
            context.sendBroadcast(intent1)

            // Send secondary trigger for alternate SUNMI firmware builds
            val intent2 = Intent("com.sunmi.action.START_SCAN")
            context.sendBroadcast(intent2)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Stops active scan session.
     */
    fun stopScan() {
        try {
            val intent1 = Intent("com.sunmi.scanner.ACTION_STOP_SCAN")
            context.sendBroadcast(intent1)

            val intent2 = Intent("com.sunmi.action.STOP_SCAN")
            context.sendBroadcast(intent2)
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
