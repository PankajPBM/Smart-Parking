package com.smartparking.app.version

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.smartparking.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Enterprise Remote Version Control & OTA Update Engine for PB Smart Parking.
 * Validates running terminal version against Cloudflare D1 registry, enforces
 * minimum mandatory versions, and orchestrates silent/prompted OTA installations.
 */
class AppVersionManager(private val context: Context) {

    companion object {
        const val VERSION_CHECK_URL = "https://parking.devika77.com.np/api/v1/parking/app/version-check"

        @Volatile
        private var INSTANCE: AppVersionManager? = null

        fun getInstance(context: Context): AppVersionManager {
            return INSTANCE ?: synchronized(this) {
                val instance = AppVersionManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    data class VersionCheckResult(
        val hasUpdate: Boolean,
        val isForced: Boolean,
        val currentVersionName: String,
        val currentVersionCode: Int,
        val latestVersionName: String,
        val latestVersionCode: Int,
        val releaseNotes: String,
        val downloadUrl: String
    )

    /**
     * Checks Cloudflare D1 for the latest authorized terminal release.
     */
    suspend fun checkForUpdates(): VersionCheckResult? = withContext(Dispatchers.IO) {
        try {
            val url = URL(VERSION_CHECK_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode !in 200..299) {
                return@withContext null
            }

            val responseText = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            val json = JSONObject(responseText)
            if (!json.optBoolean("success", false)) {
                return@withContext null
            }

            val latestCode = json.optInt("latestVersionCode", BuildConfig.VERSION_CODE)
            val latestName = json.optString("latestVersionName", BuildConfig.VERSION_NAME)
            val minSupportedCode = json.optInt("minSupportedVersionCode", 1)
            val isForcedFlag = json.optBoolean("isForcedUpdate", false)
            val releaseNotes = json.optString("releaseNotes", "Performance and security updates.")
            val downloadUrl = json.optString("downloadUrl", "https://parking.devika77.com.np/api/v1/parking/download-latest-apk")

            val currentCode = BuildConfig.VERSION_CODE
            val currentName = BuildConfig.VERSION_NAME

            val hasUpdate = latestCode > currentCode
            val isForced = hasUpdate && (isForcedFlag || currentCode < minSupportedCode)

            VersionCheckResult(
                hasUpdate = hasUpdate,
                isForced = isForced,
                currentVersionName = currentName,
                currentVersionCode = currentCode,
                latestVersionName = latestName,
                latestVersionCode = latestCode,
                releaseNotes = releaseNotes,
                downloadUrl = downloadUrl
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Displays a clean update dialog to the operator.
     */
    fun showUpdateDialog(activity: Activity, result: VersionCheckResult) {
        if (activity.isFinishing || activity.isDestroyed) return

        val title = if (result.isForced) {
            "🚨 Mandatory Terminal Update (v${result.latestVersionName})"
        } else {
            "🚀 Update Available: PB Smart Parking v${result.latestVersionName}"
        }

        val message = StringBuilder().apply {
            if (result.isForced) {
                append("A critical version update is required by your administrator before continuing operation.\n\n")
            } else {
                append("A new and improved version of PB Smart Parking is available.\n\n")
            }
            append("• Current Version: v${result.currentVersionName} (Build ${result.currentVersionCode})\n")
            append("• Latest Version: v${result.latestVersionName} (Build ${result.latestVersionCode})\n\n")
            append("Release Highlights:\n")
            append(result.releaseNotes)
        }.toString()

        val builder = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setIcon(com.smartparking.app.R.drawable.ic_parking_logo)
            .setCancelable(!result.isForced)
            .setPositiveButton("⬇️ Update Now") { _, _ ->
                startApkDownload(activity, result.downloadUrl, result.latestVersionName)
            }

        if (result.isForced) {
            builder.setNegativeButton("Exit Application") { _, _ ->
                activity.finishAffinity()
            }
        } else {
            builder.setNegativeButton("Remind Later", null)
        }

        val dialog = builder.create()
        dialog.show()
    }

    /**
     * Downloads the APK file using Android's native DownloadManager and triggers package installer.
     */
    private fun startApkDownload(activity: Activity, downloadUrl: String, targetVersion: String) {
        // Android 8.0+ Unknown App Installation Permission Check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(
                    activity,
                    "Please allow PB Smart Parking to install app updates.",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
                return
            }
        }

        Toast.makeText(activity, "Downloading PB Smart Parking v$targetVersion...", Toast.LENGTH_SHORT).show()

        val fileName = "PB_SmartParking_v$targetVersion.apk"
        val destinationFile = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val downloadManager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("PB Smart Parking Update")
            setDescription("Downloading v$targetVersion...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(destinationFile))
            setMimeType("application/vnd.android.package-archive")
        }

        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    try {
                        ctxt.unregisterReceiver(this)
                    } catch (_: Exception) {}

                    installApk(activity, destinationFile)
                }
            }
        }

        activity.registerReceiver(
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED
        )
    }

    private fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(context, "Downloaded update package not found.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

