package com.smartparking.app.license

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Manages the persistent 1-Year Trial for Smart Parking.
 * Guarantees that across multiple app uninstalls and reinstalls, the original trial
 * start date and expiry date are preserved without resetting.
 */
class PersistentTrialManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pb_sp_trial_vault", Context.MODE_PRIVATE)

    companion object {
        const val TRIAL_DURATION_DAYS = 365L
        private const val TRIAL_DURATION_MILLIS = TRIAL_DURATION_DAYS * 24 * 60 * 60 * 1000L
        private const val HMAC_KEY_SEED = "PB-TECH-ZONE-SMART-PARKING-TRIAL-V2026"

        private const val PREF_TRIAL_START = "trial_start_epoch"
        private const val PREF_TRIAL_EXPIRY = "trial_expiry_epoch"
        private const val PREF_LAST_SEEN_CLOCK = "trial_last_seen_clock"
        private const val PREF_CLOCK_TAMPERED = "trial_clock_tampered"

        private const val FILE_NAME_PRIMARY = ".pb_smart_parking_trial.dat"
        private const val FILE_NAME_BACKUP = ".pb_sp_trial_cache.dat"
    }

    data class TrialStatus(
        val isTrialActive: Boolean,
        val isExpired: Boolean,
        val isClockTampered: Boolean,
        val daysRemaining: Int,
        val startDateFormatted: String,
        val expiryDateFormatted: String,
        val totalDays: Int = 365
    )

    init {
        ensureTrialInitialized()
    }

    /**
     * Initializes or recovers the trial record across uninstalls/reinstalls.
     */
    @Synchronized
    fun ensureTrialInitialized() {
        val machineHash = MachineFingerprint.getHardwareId(context)
        val now = System.currentTimeMillis()

        // 1. Try reading from external persistent storage (persists across uninstall/reinstall)
        val externalRecord = readFromExternalStorage(machineHash)

        // 2. Try reading from internal SharedPreferences
        val prefStart = prefs.getLong(PREF_TRIAL_START, 0L)
        val prefExpiry = prefs.getLong(PREF_TRIAL_EXPIRY, 0L)

        val resolvedStart: Long
        val resolvedExpiry: Long

        when {
            // Case A: External storage has a valid record -> Always trust the earliest historical start
            externalRecord != null -> {
                resolvedStart = if (prefStart in 1 until externalRecord.first) prefStart else externalRecord.first
                resolvedExpiry = resolvedStart + TRIAL_DURATION_MILLIS

                // Sync internal prefs if missing or newer
                prefs.edit()
                    .putLong(PREF_TRIAL_START, resolvedStart)
                    .putLong(PREF_TRIAL_EXPIRY, resolvedExpiry)
                    .apply()
            }

            // Case B: SharedPreferences has record, external was missing (first reinstall or external wiped)
            prefStart > 0L && prefExpiry > 0L -> {
                resolvedStart = prefStart
                resolvedExpiry = prefExpiry
                writeToExternalStorage(machineHash, resolvedStart, resolvedExpiry)
            }

            // Case C: Brand new first installation on this physical hardware
            else -> {
                resolvedStart = now
                resolvedExpiry = now + TRIAL_DURATION_MILLIS

                prefs.edit()
                    .putLong(PREF_TRIAL_START, resolvedStart)
                    .putLong(PREF_TRIAL_EXPIRY, resolvedExpiry)
                    .putLong(PREF_LAST_SEEN_CLOCK, now)
                    .apply()

                writeToExternalStorage(machineHash, resolvedStart, resolvedExpiry)
            }
        }

        // Clock rollback detection
        val lastClock = prefs.getLong(PREF_LAST_SEEN_CLOCK, now)
        if (now < lastClock - (2 * 3600 * 1000L)) { // If clock jumped back by >2 hours
            prefs.edit().putBoolean(PREF_CLOCK_TAMPERED, true).apply()
        } else {
            prefs.edit().putLong(PREF_LAST_SEEN_CLOCK, now).apply()
        }
    }

    /**
     * Returns current trial status and remaining days.
     */
    fun getTrialStatus(): TrialStatus {
        val now = System.currentTimeMillis()
        val startEpoch = prefs.getLong(PREF_TRIAL_START, now)
        val expiryEpoch = prefs.getLong(PREF_TRIAL_EXPIRY, now + TRIAL_DURATION_MILLIS)
        val isClockTampered = prefs.getBoolean(PREF_CLOCK_TAMPERED, false)

        val millisRemaining = expiryEpoch - now
        val daysRemaining = if (millisRemaining > 0) {
            ((millisRemaining + (24 * 3600 * 1000L - 1)) / (24 * 3600 * 1000L)).toInt().coerceAtMost(365)
        } else {
            0
        }

        val isExpired = daysRemaining <= 0 || isClockTampered

        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        return TrialStatus(
            isTrialActive = !isExpired,
            isExpired = isExpired,
            isClockTampered = isClockTampered,
            daysRemaining = daysRemaining,
            startDateFormatted = dateFormat.format(Date(startEpoch)),
            expiryDateFormatted = dateFormat.format(Date(expiryEpoch)),
            totalDays = 365
        )
    }

    /**
     * Reads trial data from external public storage files.
     * Returns Pair(startEpoch, expiryEpoch) or null if invalid / not found.
     */
    private fun readFromExternalStorage(machineHash: String): Pair<Long, Long>? {
        val targetFiles = listOfNotNull(
            getPublicFile(Environment.DIRECTORY_DOCUMENTS, FILE_NAME_PRIMARY),
            getPublicFile(Environment.DIRECTORY_DOWNLOADS, FILE_NAME_BACKUP),
            File(context.filesDir.parentFile, FILE_NAME_PRIMARY)
        )

        for (file in targetFiles) {
            try {
                if (file.exists() && file.canRead()) {
                    val content = file.readText(Charsets.UTF_8).trim()
                    if (content.isNotBlank()) {
                        val json = JSONObject(content)
                        val fileHash = json.optString("machineHash")
                        val start = json.optLong("startEpoch", 0L)
                        val expiry = json.optLong("expiryEpoch", 0L)
                        val signature = json.optString("signature")

                        if (start > 0 && expiry > start && fileHash == machineHash) {
                            val expectedSig = computeHmac(machineHash, start, expiry)
                            if (signature == expectedSig) {
                                return Pair(start, expiry)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore corrupted file and continue checking backups
            }
        }
        return null
    }

    /**
     * Writes signed trial record to external public storage so it survives app uninstall.
     */
    private fun writeToExternalStorage(machineHash: String, startEpoch: Long, expiryEpoch: Long) {
        val signature = computeHmac(machineHash, startEpoch, expiryEpoch)
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val json = JSONObject().apply {
            put("product", "Smart Parking")
            put("machineHash", machineHash)
            put("startEpoch", startEpoch)
            put("expiryEpoch", expiryEpoch)
            put("trialDays", TRIAL_DURATION_DAYS)
            put("startedAtUtc", isoFormat.format(Date(startEpoch)))
            put("expiresAtUtc", isoFormat.format(Date(expiryEpoch)))
            put("signature", signature)
            put("issuer", "PB Systems Central Authority")
        }

        val content = json.toString(2)

        val targetFiles = listOfNotNull(
            getPublicFile(Environment.DIRECTORY_DOCUMENTS, FILE_NAME_PRIMARY),
            getPublicFile(Environment.DIRECTORY_DOWNLOADS, FILE_NAME_BACKUP)
        )

        for (file in targetFiles) {
            try {
                file.parentFile?.mkdirs()
                file.writeText(content, Charsets.UTF_8)
            } catch (e: Exception) {
                // Ignore storage permission restrictions on older/newer APIs
            }
        }
    }

    private fun getPublicFile(directoryType: String, fileName: String): File? {
        return try {
            val dir = Environment.getExternalStoragePublicDirectory(directoryType)
            File(dir, fileName)
        } catch (e: Exception) {
            null
        }
    }

    private fun computeHmac(machineHash: String, start: Long, expiry: Long): String {
        return try {
            val raw = "$machineHash::$start::$expiry::$HMAC_KEY_SEED"
            val mac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(HMAC_KEY_SEED.toByteArray(Charsets.UTF_8), "HmacSHA256")
            mac.init(secretKey)
            val bytes = mac.doFinal(raw.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            MachineFingerprint.sha256("$machineHash:$start:$expiry")
        }
    }
}
