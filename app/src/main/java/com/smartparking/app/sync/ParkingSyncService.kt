package com.smartparking.app.sync

import android.content.Context
import android.os.Build
import com.smartparking.app.data.local.SmartParkingDatabase
import com.smartparking.app.data.local.entity.MembershipEntity
import com.smartparking.app.domain.tariff.TariffRepository
import com.smartparking.app.license.MachineFingerprint
import com.smartparking.app.license.PbLicenseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Real-time synchronization service between Android POS terminal and Cloudflare D1 backend.
 * Pushes local terminal stats & pulls live tariffs and memberships configured by the client.
 */
class ParkingSyncService(private val context: Context) {

    private val db = SmartParkingDatabase.getInstance(context)
    private val tariffRepo = TariffRepository.getInstance(context)

    companion object {
        const val SYNC_URL = "https://pblicense.prakashkushwaha77.com.np/api/v1/parking/sync"

        @Volatile
        private var INSTANCE: ParkingSyncService? = null

        fun getInstance(context: Context): ParkingSyncService {
            return INSTANCE ?: synchronized(this) {
                val instance = ParkingSyncService(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    data class SyncResult(
        val success: Boolean,
        val message: String,
        val tariffsCount: Int = 0,
        val membersCount: Int = 0
    )

    /**
     * Executes bi-directional sync with Cloudflare.
     */
    suspend fun syncWithCloud(): SyncResult = withContext(Dispatchers.IO) {
        val licensePrefs = context.getSharedPreferences("pb_sp_license_vault", Context.MODE_PRIVATE)
        val licenseKey = licensePrefs.getString("activated_license_key", "") ?: ""
        val machineHash = MachineFingerprint.getHardwareId(context)

        // If license key is not yet set, we cannot bind to a tenant in Cloudflare
        if (licenseKey.isBlank()) {
            return@withContext SyncResult(false, "App is running in local trial mode. Activate a PB License to enable Cloud Sync.")
        }

        try {
            // Calculate today's stats from Room DB
            val now = System.currentTimeMillis()
            val startOfDay = getStartOfDayMillis(now)
            val todayEntries: Int = db.parkingSessionDao().getTodayEntriesCount(startOfDay)
            val todayExitsList = db.parkingSessionDao().getTodayExits(startOfDay)
            val todayExitsCount: Int = todayExitsList.size
            val todayRevenue: Double = todayExitsList.sumOf { it.netAmount.toDoubleOrNull() ?: 0.0 }
            val activeParked: Int = db.parkingSessionDao().getCurrentlyParkedCount()

            val url = URL(SYNC_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 12000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            val statsObj = JSONObject()
            statsObj.put("activeParked", activeParked)
            statsObj.put("todayEntries", todayEntries)
            statsObj.put("todayExits", todayExitsCount)
            statsObj.put("todayRevenue", todayRevenue)

            val requestBody = JSONObject().apply {
                put("licenseKey", licenseKey)
                put("machineHash", machineHash)
                put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("appVersion", "1.0.0")
                put("stats", statsObj)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(requestBody.toString())
                it.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }

            val json = JSONObject(responseText)
            val success = json.optBoolean("success", false)
            if (!success) {
                return@withContext SyncResult(false, json.optString("message", "Sync rejected by server."))
            }

            // 1. Update Tariffs
            val tariffsArray = json.optJSONArray("tariffs")
            if (tariffsArray != null) {
                tariffRepo.saveCloudTariffs(tariffsArray)
            }

            // 2. Update Memberships
            val membersArray = json.optJSONArray("memberships")
            var importedMembers = 0
            if (membersArray != null) {
                for (i in 0 until membersArray.length()) {
                    val m = membersArray.getJSONObject(i)
                    val memberNo = m.optString("memberNo", "")
                    val vNo = m.optString("vehicleNo", "")
                    if (memberNo.isNotBlank() && vNo.isNotBlank()) {
                        val validUntilStr = m.optString("validUntil", "")
                        val validUntilEpoch = parseDateToEpoch(validUntilStr) ?: (now + (30L * 86400000L))

                        val entity = MembershipEntity(
                            id = m.optString("id", "${licenseKey}_$memberNo"),
                            memberNumber = memberNo,
                            fullName = m.optString("holderName", "VIP Member"),
                            phone = m.optString("phone", ""),
                            vehicleNumber = vNo,
                            vehicleTypeId = m.optString("vehicleType", "CAR"),
                            planType = m.optString("planType", "MONTHLY"),
                            startDate = now,
                            endDate = validUntilEpoch,
                            discountPercent = m.optInt("discountPercent", 100),
                            feePaid = m.optDouble("amountPaid", 0.0).toString(),
                            isActive = m.optString("status", "ACTIVE") == "ACTIVE"
                        )
                        db.membershipDao().insertMembership(entity)
                        importedMembers++
                    }
                }
            }

            // 3. Update POS Settings & Payments
            val settingsObj = json.optJSONObject("settings")
            if (settingsObj != null) {
                com.smartparking.app.domain.settings.PosSettingsRepository.getInstance(context).saveSettings(settingsObj)
            }

            SyncResult(
                success = true,
                message = "Cloud Sync complete! Rates & VIP passes updated.",
                tariffsCount = tariffsArray?.length() ?: 0,
                membersCount = importedMembers
            )

        } catch (e: Exception) {
            SyncResult(
                success = false,
                message = "Cloud Sync offline: ${e.localizedMessage ?: "No connection"}"
            )
        }
    }

    private fun getStartOfDayMillis(timeMillis: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timeMillis
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun parseDateToEpoch(dateStr: String): Long? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.parse(dateStr.take(10))?.time
        } catch (e: Exception) {
            null
        }
    }
}

