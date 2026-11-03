package com.smartparking.app.domain.settings

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONObject

/**
 * Manages POS configuration synchronized from the Cloudflare Client Portal:
 * 1. Enabled Vehicle Categories (only selected vehicles shown in mobile app)
 * 2. Accepted Payment Types (only selected payment methods shown at checkout)
 * 3. Custom QR Code Photo (Fonepay / eSewa / UPI image displayed on terminal screen)
 */
class PosSettingsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pb_parking_pos_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ENABLED_VEHICLES = "enabled_vehicles"
        private const val KEY_ENABLED_PAYMENTS = "enabled_payments"
        private const val KEY_QR_IMAGE_DATA = "qr_image_data"
        private const val KEY_QR_NOTE = "qr_note"

        @Volatile
        private var INSTANCE: PosSettingsRepository? = null

        fun getInstance(context: Context): PosSettingsRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = PosSettingsRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    fun saveSettings(settingsObj: JSONObject) {
        val editor = prefs.edit()

        val vehiclesArray = settingsObj.optJSONArray("enabledVehicleTypes")
        if (vehiclesArray != null) {
            val set = mutableSetOf<String>()
            for (i in 0 until vehiclesArray.length()) {
                val v = vehiclesArray.optString(i, "").trim().uppercase()
                if (v.isNotBlank()) set.add(v)
            }
            if (set.isNotEmpty()) {
                editor.putStringSet(KEY_ENABLED_VEHICLES, set)
            }
        }

        val paymentsArray = settingsObj.optJSONArray("enabledPaymentMethods")
        if (paymentsArray != null) {
            val set = mutableSetOf<String>()
            for (i in 0 until paymentsArray.length()) {
                val p = paymentsArray.optString(i, "").trim().uppercase()
                if (p.isNotBlank()) set.add(p)
            }
            if (set.isNotEmpty()) {
                editor.putStringSet(KEY_ENABLED_PAYMENTS, set)
            }
        }

        if (settingsObj.has("qrImageData")) {
            val qrData = settingsObj.optString("qrImageData", "")
            editor.putString(KEY_QR_IMAGE_DATA, qrData)
        }

        if (settingsObj.has("qrNote")) {
            val note = settingsObj.optString("qrNote", "")
            editor.putString(KEY_QR_NOTE, note)
        }

        editor.apply()
    }

    fun getEnabledVehicleTypes(): List<String> {
        val set = prefs.getStringSet(
            KEY_ENABLED_VEHICLES,
            setOf("MOTORCYCLE", "CAR", "VAN", "BUS")
        ) ?: setOf("MOTORCYCLE", "CAR", "VAN", "BUS")
        return if (set.isEmpty()) listOf("CAR") else set.toList()
    }

    fun getEnabledPaymentMethods(): List<String> {
        val set = prefs.getStringSet(
            KEY_ENABLED_PAYMENTS,
            setOf("CASH", "QR_ONLINE", "CARD", "VIP_PASS")
        ) ?: setOf("CASH", "QR_ONLINE", "CARD", "VIP_PASS")
        return if (set.isEmpty()) listOf("CASH") else set.toList()
    }

    fun getQrImageData(): String? {
        val data = prefs.getString(KEY_QR_IMAGE_DATA, null)
        return data?.takeIf { it.isNotBlank() }
    }

    fun getQrNote(): String {
        return prefs.getString(KEY_QR_NOTE, null)?.takeIf { it.isNotBlank() }
            ?: "Scan QR with Fonepay / eSewa / Mobile Banking"
    }

    fun getQrBitmap(): Bitmap? {
        val data = getQrImageData() ?: return null
        return try {
            val cleanBase64 = if (data.contains(",")) {
                data.substringAfter(",")
            } else {
                data
            }
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }
}

