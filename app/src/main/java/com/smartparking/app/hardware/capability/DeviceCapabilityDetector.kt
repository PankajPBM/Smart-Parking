package com.smartparking.app.hardware.capability

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.nfc.NfcAdapter
import android.os.Build

/**
 * App, Publisher and Hardware metadata
 */
data class AppMetadata(
    val appName: String = "Smart Parking",
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val publisher: String = "PB Tech Zone",
    val websiteUrl: String = "https://prakash77.com.np",
    val supportEmail: String = "info@prakash77.com.np",
    val supportPhone: String = "+9779814891277",
    val supportPhoneFormatted: String = "+977 9814891277",
    val whatsappNumber: String = "9779814891277",
    val releaseDate: String = "September 2026",
    val targetPlatform: String = "SUNMI POS Enterprise OS"
)

data class DeviceCapabilities(
    val isSunmiDevice: Boolean,
    val manufacturer: String,
    val model: String,
    val serialNumber: String,
    val androidVersion: String,
    val sunmiOsVersion: String?,
    val hasSunmiPrinterService: Boolean,
    val hasHardwareScanner: Boolean,
    val hasCamera: Boolean,
    val hasNfc: Boolean,
    val paperWidth: PaperWidth,
    val maxColumns: Int
)

enum class PaperWidth(val mm: Int, val dots: Int, val defaultColumns: Int) {
    WIDTH_58MM(58, 384, 32),
    WIDTH_80MM(80, 576, 48),
    UNKNOWN(58, 384, 32)
}

class DeviceCapabilityDetector(private val context: Context) {

    fun detectCapabilities(): DeviceCapabilities {
        val manufacturer = Build.MANUFACTURER ?: "Unknown"
        val brand = Build.BRAND ?: "Unknown"
        val model = Build.MODEL ?: "Unknown"
        val isSunmi = brand.contains("SUNMI", ignoreCase = true) ||
                manufacturer.contains("SUNMI", ignoreCase = true) ||
                model.contains("SUNMI", ignoreCase = true)

        val serialNumber = getDeviceSerialNumber()
        val hasSunmiPrinterService = checkServiceAvailable("woyou.aidlservice.jiuiv5.IWoyouService")
        val hasHardwareScanner = checkScannerAvailable()
        val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        val hasNfc = NfcAdapter.getDefaultAdapter(context) != null

        val sunmiOsVersion = getSystemProperty("ro.version.sunmios")

        val paperWidth = if (model.contains("PLUS", ignoreCase = true) ||
            model.contains("80", ignoreCase = true) ||
            model.startsWith("T", ignoreCase = true) ||
            model.startsWith("S", ignoreCase = true)
        ) {
            PaperWidth.WIDTH_80MM
        } else {
            PaperWidth.WIDTH_58MM
        }

        return DeviceCapabilities(
            isSunmiDevice = isSunmi,
            manufacturer = manufacturer,
            model = model,
            serialNumber = serialNumber,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            sunmiOsVersion = sunmiOsVersion,
            hasSunmiPrinterService = hasSunmiPrinterService,
            hasHardwareScanner = hasHardwareScanner,
            hasCamera = hasCamera,
            hasNfc = hasNfc,
            paperWidth = paperWidth,
            maxColumns = paperWidth.defaultColumns
        )
    }

    /**
     * Reads device serial number using SUNMI-specific system properties,
     * Build.getSerial() (with fallback), or hardware identifiers.
     */
    @SuppressLint("HardwareIds")
    private fun getDeviceSerialNumber(): String {
        // 1. Try SUNMI system properties first (always works on SUNMI OS without runtime permissions)
        val sunmiSn = getSystemProperty("ro.serialno")
            ?: getSystemProperty("ro.sunmi.serial")
            ?: getSystemProperty("ro.boot.serialno")

        if (!sunmiSn.isNullOrBlank() && sunmiSn != "unknown") {
            return sunmiSn
        }

        // 2. Try standard Android Build.getSerial() if permitted
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val serial = Build.getSerial()
                if (serial.isNotBlank() && serial != Build.UNKNOWN) {
                    return serial
                }
            }
        } catch (e: SecurityException) {
            // Permission restricted on non-system apps
        }

        // 3. Fallback to standard Build.SERIAL
        @Suppress("DEPRECATION")
        val fallbackSerial = Build.SERIAL
        if (!fallbackSerial.isNullOrBlank() && fallbackSerial != Build.UNKNOWN) {
            return fallbackSerial
        }

        // 4. Fallback identifier
        return "SUNMI-POS-${Build.MODEL.replace(" ", "-")}"
    }

    private fun checkServiceAvailable(action: String): Boolean {
        return try {
            val intent = android.content.Intent(action).apply {
                `package` = "woyou.aidlservice.jiuiv5"
            }
            val resolveInfo = context.packageManager.queryIntentServices(intent, 0)
            resolveInfo.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun checkScannerAvailable(): Boolean {
        return try {
            val intent = android.content.Intent("com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED")
            val receivers = context.packageManager.queryBroadcastReceivers(intent, 0)
            receivers.isNotEmpty() || Build.MODEL.contains("V2_PRO", ignoreCase = true) || Build.MODEL.contains("V2s", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    private fun getSystemProperty(propName: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java)
            val result = getMethod.invoke(null, propName) as String
            if (result.isNotBlank() && result != "unknown") result else null
        } catch (e: Exception) {
            null
        }
    }
}
