package com.smartparking.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Debug
import android.os.Process
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.Socket
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.system.exitProcess

/**
 * Enterprise Anti-Tamper & Security Suite for PB Smart Parking.
 * Defends against:
 * 1. APK Repackaging & Cracking (Validates authentic PB Tech Zone signature)
 * 2. Smali Bytecode Modification (Dynamic signature & certificate hash integrity)
 * 3. Dynamic Hooking & Instrumentation (Frida, Xposed, Cydia Substrate)
 * 4. Active Debugger Attachment (ptrace / JDWP anti-debugging)
 * 5. Tampered License Files (HMAC-SHA256 hardware-bound vault verification)
 */
object SecurityGuard {

    // Official PB Tech Zone Release Certificate SHA-256 Fingerprint
    private const val OFFICIAL_RELEASE_SHA256 =
        "EA181379C039107E4309AE3CA86335ACED21E371AF34F4E307E6E201A3B2F1B4"

    // Local Development Debug Keystore SHA-256 Fingerprint (only permitted in BuildConfig.DEBUG)
    private const val OFFICIAL_DEBUG_SHA256 =
        "3DD5A0397EA2E7DD68597C8595C9C2F06566308E2546B176EA53AF43A5A5A3D6"

    private const val HMAC_SALT = "PB_SECURITY_SEAL_V1_2026_SMART_PARKING_KTM"

    data class SecurityStatus(
        val isTampered: Boolean,
        val violationReason: String? = null
    )

    /**
     * Executes a full integrity audit on app startup.
     * Returns SecurityStatus. If tampered, terminates the process if enforceHardExit = true.
     */
    fun verifyAppIntegrity(context: Context, enforceHardExit: Boolean = true): SecurityStatus {
        // 1. Signature Integrity Verification (Anti-Repackage)
        val sigStatus = verifySignature(context)
        if (sigStatus != null) {
            if (enforceHardExit) killProcess("Signature Tamper: $sigStatus")
            return SecurityStatus(isTampered = true, violationReason = sigStatus)
        }

        // 2. Anti-Debugging Check
        if (isDebuggerAttached(context)) {
            val reason = "Unauthorized Debugger or Profiler Attached"
            if (enforceHardExit) killProcess(reason)
            return SecurityStatus(isTampered = true, violationReason = reason)
        }

        // 3. Dynamic Hooking Detection (Frida / Xposed)
        val hookReason = detectDynamicHooking()
        if (hookReason != null) {
            if (enforceHardExit) killProcess(hookReason)
            return SecurityStatus(isTampered = true, violationReason = hookReason)
        }

        // 4. Root / Magisk / Su Binary Detection
        val rootReason = detectRootAccess()
        if (rootReason != null && !com.smartparking.app.BuildConfig.DEBUG) {
            if (enforceHardExit) killProcess(rootReason)
            return SecurityStatus(isTampered = true, violationReason = rootReason)
        }

        return SecurityStatus(isTampered = false)
    }

    /**
     * Validates that the APK signature matches the official PB Tech Zone certificate.
     */
    private fun verifySignature(context: Context): String? {
        try {
            val pm = context.packageManager
            val packageName = context.packageName

            val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = packageInfo.signingInfo
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                packageInfo.signatures
            } ?: return "Missing signature credentials"

            val md = MessageDigest.getInstance("SHA-256")
            for (sig in signatures) {
                val digest = md.digest(sig.toByteArray())
                val sha256 = digest.joinToString("") { "%02X".format(it) }

                if (sha256.equals(OFFICIAL_RELEASE_SHA256, ignoreCase = true)) {
                    return null // Official production certificate validated!
                }

                // Allow debug certificate ONLY if compiled under debug variant
                if (com.smartparking.app.BuildConfig.DEBUG &&
                    sha256.equals(OFFICIAL_DEBUG_SHA256, ignoreCase = true)
                ) {
                    return null // Verified debug build
                }
            }

            return "APK signature mismatch! Binary has been modified or repackaged."
        } catch (e: Exception) {
            return "Signature verification failure: ${e.message}"
        }
    }

    /**
     * Detects unauthorized debuggers attached to the process.
     */
    fun isDebuggerAttached(context: Context): Boolean {
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return true
        val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!com.smartparking.app.BuildConfig.DEBUG && isDebuggable) return true
        return false
    }

    /**
     * Scans for active Frida servers or memory-mapped hooking frameworks.
     */
    private fun detectDynamicHooking(): String? {
        // 1. Scan memory maps for frida or xposed libraries
        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists()) {
                BufferedReader(FileReader(mapsFile)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        val lower = line.lowercase()
                        if (lower.contains("frida-agent") ||
                            lower.contains("frida-gadget") ||
                            lower.contains("xposedbridge") ||
                            lower.contains("libxposed") ||
                            lower.contains("substrate")
                        ) {
                            return "Dynamic instrumentation hook detected in memory: $line"
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Check default Frida local ports (27042, 27043)
        for (port in listOf(27042, 27043)) {
            try {
                Socket("127.0.0.1", port).use {
                    return "Active instrumentation server detected on port $port"
                }
            } catch (_: Exception) {}
        }

        return null
    }

    /**
     * Detects rooted devices, Magisk, and unauthorized su binaries.
     */
    private fun detectRootAccess(): String? {
        val suPaths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/data/adb/magisk"
        )
        for (path in suPaths) {
            try {
                if (File(path).exists()) {
                    return "Root privilege binary detected at: $path"
                }
            } catch (_: Exception) {}
        }

        // Check build tags for test-keys
        val tags = Build.TAGS
        if (tags != null && tags.contains("test-keys")) {
            return "Test-keys custom rooted ROM detected"
        }

        return null
    }

    /**
     * Computes an HMAC-SHA256 integrity seal for stored license data.
     */
    fun computeLicenseHmac(licenseKey: String, machineHash: String, expiryUtc: String): String {
        return try {
            val raw = "$licenseKey|$machineHash|$expiryUtc|$HMAC_SALT"
            val mac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(HMAC_SALT.toByteArray(), "HmacSHA256")
            mac.init(secretKey)
            val bytes = mac.doFinal(raw.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Terminates process immediately when critical tampering is detected.
     */
    private fun killProcess(reason: String) {
        try {
            android.util.Log.e("SecurityGuard", "SECURITY VIOLATION: $reason")
            Process.killProcess(Process.myPid())
            exitProcess(-1)
        } catch (_: Exception) {
            exitProcess(-1)
        }
    }
}

