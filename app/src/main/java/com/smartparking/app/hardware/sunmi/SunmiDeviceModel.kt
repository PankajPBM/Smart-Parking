package com.smartparking.app.hardware.sunmi

import android.os.Build

/**
 * Enumerates supported SUNMI POS device models and their hardware capabilities.
 */
enum class SunmiDeviceModel(
    val modelCode: String,
    val displayName: String,
    val paperWidthMm: Int,
    val dotsPerLine: Int,
    val hasHardwareScanner: Boolean,
    val hasNfc: Boolean,
    val isHandheld: Boolean
) {
    // Handheld Series (58mm)
    V1("V1", "SUNMI V1", 58, 384, false, false, true),
    V1S("V1s", "SUNMI V1s", 58, 384, false, true, true),
    V2("V2", "SUNMI V2", 58, 384, false, false, true),
    V2_PRO("V2 PRO", "SUNMI V2 Pro", 58, 384, true, true, true),
    V2S("V2s", "SUNMI V2s", 58, 384, true, true, true),
    P1("P1", "SUNMI P1", 58, 384, false, true, true),
    P2("P2", "SUNMI P2", 58, 384, true, true, true),
    P2_PRO("P2 PRO", "SUNMI P2 Pro", 58, 384, true, true, true),

    // Handheld Series (80mm)
    V2S_PLUS("V2s Plus", "SUNMI V2s Plus", 80, 576, true, true, true),

    // Desktop Series (80mm)
    T1("T1", "SUNMI T1", 80, 576, false, false, false),
    T2("T2", "SUNMI T2", 80, 576, false, true, false),
    T2_MINI("T2mini", "SUNMI T2 Mini", 80, 576, false, true, false),
    T2S("T2s", "SUNMI T2s", 80, 576, false, true, false),
    T3_PRO("T3 PRO", "SUNMI T3 Pro", 80, 576, false, true, false),
    D2("D2", "SUNMI D2", 58, 384, false, false, false),
    D2S("D2s", "SUNMI D2s", 58, 384, false, false, false),

    // Generic / Fallback
    UNKNOWN("UNKNOWN", "Generic Android Device", 58, 384, false, false, true);

    companion object {
        fun detect(): SunmiDeviceModel {
            val model = Build.MODEL ?: ""
            val brand = Build.BRAND ?: ""
            val manufacturer = Build.MANUFACTURER ?: ""

            val isSunmi = brand.contains("SUNMI", ignoreCase = true) ||
                    manufacturer.contains("SUNMI", ignoreCase = true) ||
                    model.contains("SUNMI", ignoreCase = true)

            if (!isSunmi) {
                return UNKNOWN
            }

            return entries.firstOrNull { device ->
                model.contains(device.modelCode, ignoreCase = true)
            } ?: if (model.contains("PLUS", ignoreCase = true) || model.contains("80", ignoreCase = true)) {
                V2S_PLUS
            } else {
                V2S // Modern default SUNMI handheld
            }
        }
    }
}
