package com.smartparking.app.license

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Client service for PB Systems Central License Authority.
 * Communicates with Cloudflare Workers + D1 backend at https://pblicense.prakashkushwaha77.com.np.
 */
class PbLicenseService(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pb_sp_license_vault", Context.MODE_PRIVATE)

    val trialManager: PersistentTrialManager = PersistentTrialManager(context)

    companion object {
        const val BASE_URL = "https://pblicense.prakashkushwaha77.com.np"
        const val PRODUCT_CODE = "SMART_PARKING"
        const val PRODUCT_NAME = "Smart Parking (SUNMI POS)"

        private const val PREF_IS_ACTIVATED = "is_license_activated"
        private const val PREF_LICENSE_KEY = "activated_license_key"
        private const val PREF_CUSTOMER_NAME = "activated_customer_name"
        private const val PREF_LICENSE_TYPE = "activated_license_type"
        private const val PREF_EXPIRY_UTC = "activated_expiry_utc"
        private const val PREF_LICENSE_ENVELOPE = "activated_license_envelope"
        private const val PREF_LAST_HEARTBEAT = "last_heartbeat_time"

        @Volatile
        private var INSTANCE: PbLicenseService? = null

        fun getInstance(context: Context): PbLicenseService {
            return INSTANCE ?: synchronized(this) {
                val instance = PbLicenseService(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    sealed class LicenseState {
        data class ProActive(
            val licenseKey: String,
            val customerName: String,
            val licenseType: String,
            val expiryDate: String
        ) : LicenseState()

        data class TrialActive(
            val daysRemaining: Int,
            val expiryDate: String,
            val startDate: String,
            val totalDays: Int = 365
        ) : LicenseState()

        data class Expired(
            val reason: String,
            val expiryDate: String
        ) : LicenseState()
    }

    data class ActivationResult(
        val success: Boolean,
        val message: String,
        val customerName: String? = null,
        val licenseType: String? = null,
        val expiryDate: String? = null
    )

    /**
     * Determines current operational license state (Pro Perpetual/Subscription -> 1-Year Trial -> Expired)
     */
    fun getLicenseState(): LicenseState {
        val isActivated = prefs.getBoolean(PREF_IS_ACTIVATED, false)

        if (isActivated) {
            val key = prefs.getString(PREF_LICENSE_KEY, "") ?: ""
            val customer = prefs.getString(PREF_CUSTOMER_NAME, "Licensed Customer") ?: "Licensed Customer"
            val type = prefs.getString(PREF_LICENSE_TYPE, "PERPETUAL") ?: "PERPETUAL"
            val expiryUtc = prefs.getString(PREF_EXPIRY_UTC, null)

            // Check if pro license has expired
            if (!expiryUtc.isNullOrBlank()) {
                val expiryEpoch = parseUtcDate(expiryUtc)
                if (expiryEpoch != null && System.currentTimeMillis() > expiryEpoch) {
                    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    return LicenseState.Expired(
                        reason = "Pro subscription period has ended",
                        expiryDate = dateFormat.format(Date(expiryEpoch))
                    )
                }
            }

            return LicenseState.ProActive(
                licenseKey = key,
                customerName = customer,
                licenseType = type,
                expiryDate = expiryUtc?.let { formatIsoToReadable(it) } ?: "Lifetime / Perpetual"
            )
        }

        // Check fallback to default 1-Year Trial
        val trialStatus = trialManager.getTrialStatus()
        return if (trialStatus.isTrialActive) {
            LicenseState.TrialActive(
                daysRemaining = trialStatus.daysRemaining,
                expiryDate = trialStatus.expiryDateFormatted,
                startDate = trialStatus.startDateFormatted,
                totalDays = trialStatus.totalDays
            )
        } else {
            val reason = if (trialStatus.isClockTampered) {
                "System clock manipulation detected"
            } else {
                "1-Year Trial period has completed"
            }
            LicenseState.Expired(
                reason = reason,
                expiryDate = trialStatus.expiryDateFormatted
            )
        }
    }

    /**
     * Activates license online using PB License Cloud Authority
     */
    suspend fun activateOnline(licenseKey: String): ActivationResult = withContext(Dispatchers.IO) {
        val cleanKey = licenseKey.trim().uppercase(Locale.US)
        val machineHash = MachineFingerprint.getHardwareId(context)

        if (cleanKey.isBlank()) {
            return@withContext ActivationResult(false, "License key cannot be empty.")
        }

        try {
            val url = URL("$BASE_URL/api/v1/activate-online")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            val requestJson = JSONObject().apply {
                put("licenseKey", cleanKey)
                put("machineHash", machineHash)
                put("hostName", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("clientVersion", "1.0.0")
                put("productCode", PRODUCT_CODE)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }

            val json = JSONObject(responseText)
            val success = json.optBoolean("success", false)
            val message = json.optString("message", "Activation response received.")

            if (success) {
                val customerName = json.optString("customerName", "Valued Customer")
                val licenseType = json.optString("licenseType", "PERPETUAL")
                val expiryUtc = json.optString("expiryUtc", null).takeIf { it != "null" && it.isNotBlank() }
                val licenseEnvelope = json.optString("licenseFileContent", "")

                prefs.edit()
                    .putBoolean(PREF_IS_ACTIVATED, true)
                    .putString(PREF_LICENSE_KEY, cleanKey)
                    .putString(PREF_CUSTOMER_NAME, customerName)
                    .putString(PREF_LICENSE_TYPE, licenseType)
                    .putString(PREF_EXPIRY_UTC, expiryUtc)
                    .putString(PREF_LICENSE_ENVELOPE, licenseEnvelope)
                    .putLong(PREF_LAST_HEARTBEAT, System.currentTimeMillis())
                    .apply()

                val readableExpiry = expiryUtc?.let { formatIsoToReadable(it) } ?: "Lifetime"
                ActivationResult(
                    success = true,
                    message = message,
                    customerName = customerName,
                    licenseType = licenseType,
                    expiryDate = readableExpiry
                )
            } else {
                ActivationResult(false, message)
            }
        } catch (e: Exception) {
            ActivationResult(
                success = false,
                message = "Connection error: ${e.localizedMessage ?: "Unable to reach PB License server. Please check internet connection."}"
            )
        }
    }

    /**
     * Heartbeat check to sync license validity and renewal in background
     */
    suspend fun sendHeartbeat(): Boolean = withContext(Dispatchers.IO) {
        val isActivated = prefs.getBoolean(PREF_IS_ACTIVATED, false)
        val key = prefs.getString(PREF_LICENSE_KEY, null) ?: return@withContext false
        val machineHash = MachineFingerprint.getHardwareId(context)

        try {
            val url = URL("$BASE_URL/api/v1/heartbeat")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val body = JSONObject().apply {
                put("licenseKey", key)
                put("machineHash", machineHash)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            if (conn.responseCode in 200..299) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val valid = json.optBoolean("valid", false)
                if (!valid) {
                    val reason = json.optString("reason", "REVOKED")
                    if (reason == "SUSPENDED" || reason == "MACHINE_MISMATCH") {
                        prefs.edit().putBoolean(PREF_IS_ACTIVATED, false).apply()
                    }
                } else {
                    prefs.edit().putLong(PREF_LAST_HEARTBEAT, System.currentTimeMillis()).apply()
                }
                valid
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Deactivates license on current device (for migration)
     */
    fun clearLicense() {
        prefs.edit().clear().apply()
    }

    private fun parseUtcDate(isoString: String): Long? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            sdf.parse(isoString.take(19))?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun formatIsoToReadable(isoString: String): String {
        return try {
            val epoch = parseUtcDate(isoString) ?: return isoString
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(epoch))
        } catch (e: Exception) {
            isoString
        }
    }
}

