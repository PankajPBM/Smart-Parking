package com.smartparking.app.license

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import java.util.Locale

/**
 * Generates deterministic, hardware-locked machine fingerprints for Android / SUNMI POS devices.
 * Integrates directly with the PB Systems Licensing Authority.
 */
object MachineFingerprint {

    @SuppressLint("HardwareIds")
    fun getHardwareId(context: Context): String {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "UNKNOWN_ANDROID_ID"

            val serial = getDeviceSerial()
            val model = Build.MODEL ?: "UNKNOWN_MODEL"
            val manufacturer = Build.MANUFACTURER ?: "UNKNOWN_MANUFACTURER"
            val board = Build.BOARD ?: "UNKNOWN_BOARD"
            val fingerprint = Build.FINGERPRINT ?: "UNKNOWN_FINGERPRINT"

            val rawCombined = "PB-SP::$androidId::$serial::$model::$manufacturer::$board::$fingerprint"
            sha256(rawCombined).uppercase(Locale.US)
        } catch (e: Exception) {
            "PB-SP-FALLBACK-" + System.currentTimeMillis().toString(16).uppercase(Locale.US)
        }
    }

    /**
     * Human-readable formatted hardware code for display: e.g. "PB-SP-A1B2-C3D4-E5F6"
     */
    fun getFormattedMachineCode(context: Context): String {
        val hash = getHardwareId(context)
        val chunk1 = hash.take(4)
        val chunk2 = hash.substring(4, 8)
        val chunk3 = hash.substring(8, 12)
        val chunk4 = hash.substring(12, 16)
        return "PB-SP-$chunk1-$chunk2-$chunk3-$chunk4"
    }

    @SuppressLint("HardwareIds")
    private fun getDeviceSerial(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    Build.getSerial()
                } catch (se: SecurityException) {
                    Build.SERIAL ?: "SUNMI_GENERIC"
                }
            } else {
                Build.SERIAL ?: "SUNMI_GENERIC"
            }
        } catch (e: Exception) {
            "SUNMI_GENERIC_SERIAL"
        }
    }

    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

