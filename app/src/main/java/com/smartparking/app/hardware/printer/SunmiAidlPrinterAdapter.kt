package com.smartparking.app.hardware.printer

import android.content.Context
import com.smartparking.app.hardware.capability.DeviceCapabilities
import com.smartparking.app.hardware.capability.PaperWidth
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.InnerResultCallback
import com.sunmi.peripheral.printer.SunmiPrinterService
import com.sunmi.peripheral.printer.WoyouConsts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Result of a printing operation
 */
sealed class PrintResult {
    data object Success : PrintResult()
    data class Error(val code: Int, val message: String) : PrintResult()
    data object PrinterUnavailable : PrintResult()
    data object OutOfPaper : PrintResult()
    data object Overheated : PrintResult()
}

/**
 * Hardware-agnostic parking printer interface supporting SUNMI V2, V2s, V3, V3 Mix & POS terminals
 */
interface ParkingPrinter {
    fun bind(context: Context)

    suspend fun printEntryTicket(
        siteName: String,
        ticketNo: String,
        vehicleNo: String,
        vehicleType: String,
        entryTime: String,
        zoneName: String,
        operatorName: String,
        rateInfo: String
    ): PrintResult

    suspend fun printExitReceipt(
        siteName: String,
        receiptNo: String,
        ticketNo: String,
        vehicleNo: String,
        vehicleType: String,
        entryTime: String,
        exitTime: String,
        duration: String,
        parkingFee: String,
        discount: String,
        totalPaid: String,
        paymentMethod: String,
        operatorName: String
    ): PrintResult

    suspend fun printMembershipPass(
        siteName: String,
        memberNo: String,
        memberName: String,
        phone: String,
        vehicleNo: String,
        vehicleType: String,
        planType: String,
        validFrom: String,
        validUntil: String,
        amountPaid: String,
        operatorName: String
    ): PrintResult

    suspend fun getPrinterStatus(): PrintResult

    suspend fun printerSelfChecking(): PrintResult

    suspend fun printEscPosSample(): PrintResult
}

/**
 * Universal Official SUNMI Printer SDK Adapter (com.sunmi:printerlibrary:1.0.24)
 *
 * Implements 100% backward and forward compatibility across:
 * - SUNMI V1 / V2 / V2 Pro / V2s (Android 7.1 - Android 11)
 * - SUNMI V3 / V3 Mix / SUNMI OS 4.0+ (Android 13 / Android 14)
 * - SUNMI Desktop POS (T1, T2, T2s, D2, S2, etc.)
 */
class SunmiAidlPrinterAdapter(
    private val appContext: Context,
    private val capabilities: DeviceCapabilities
) : ParkingPrinter {

    @Volatile
    private var sunmiService: SunmiPrinterService? = null
    @Volatile
    private var isBound = false

    /**
     * Non-null reusable callback binder stub.
     * Prevents Android 13 Binder NPE exceptions on SUNMI V3 while remaining 100% compatible with SUNMI V2.
     */
    private val noOpCallback = object : InnerResultCallback() {
        override fun onRunResult(isSuccess: Boolean) {}
        override fun onReturnString(result: String?) {}
        override fun onRaiseException(code: Int, msg: String?) {}
        override fun onPrintResult(code: Int, msg: String?) {}
    }

    private val innerPrinterCallback = object : InnerPrinterCallback() {
        override fun onConnected(service: SunmiPrinterService?) {
            sunmiService = service
            isBound = true
            try {
                service?.printerInit(noOpCallback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onDisconnected() {
            sunmiService = null
            isBound = false
        }
    }

    init {
        bind(appContext)
    }

    /**
     * Official SDK binding through InnerPrinterManager.
     * Resolves all OS-level intent filters, permissions, and security tokens automatically.
     */
    override fun bind(context: Context) {
        if (isBound && sunmiService != null) return
        try {
            val bindSuccess = InnerPrinterManager.getInstance().bindService(context, innerPrinterCallback)
            if (!bindSuccess && context != appContext) {
                InnerPrinterManager.getInstance().bindService(appContext, innerPrinterCallback)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unbindService(context: Context = appContext) {
        if (isBound) {
            try {
                InnerPrinterManager.getInstance().unBindService(context, innerPrinterCallback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isBound = false
            sunmiService = null
        }
    }

    /**
     * Official SDK typography engine style (WoyouConsts).
     * Prevents printhead pipeline state corruptions across SUNMI OS 3 & OS 4.
     */
    private fun setBold(service: SunmiPrinterService, enable: Boolean) {
        try {
            val style = if (enable) WoyouConsts.ENABLE else WoyouConsts.DISABLE
            service.setPrinterStyle(WoyouConsts.ENABLE_BOLD, style)
        } catch (_: Exception) {
            // Graceful fallback for older firmware
        }
    }

    override suspend fun getPrinterStatus(): PrintResult = withContext(Dispatchers.IO) {
        val service = sunmiService ?: return@withContext PrintResult.PrinterUnavailable
        return@withContext try {
            when (service.updatePrinterState()) {
                0, 1 -> PrintResult.Success
                2 -> PrintResult.Error(2, "Printer is initializing")
                3 -> PrintResult.Error(3, "Printer communication error")
                4 -> PrintResult.OutOfPaper
                5 -> PrintResult.Overheated
                6 -> PrintResult.Error(6, "Printer cover open")
                else -> PrintResult.Success
            }
        } catch (e: Exception) {
            PrintResult.Error(-1, e.message ?: "Printer status exception")
        }
    }

    /**
     * Runs internal hardware calibration pattern directly on thermal MCU.
     * Works on both V2 and V3 hardware.
     */
    override suspend fun printerSelfChecking(): PrintResult = withContext(Dispatchers.IO) {
        val service = sunmiService ?: return@withContext PrintResult.PrinterUnavailable
        return@withContext try {
            service.printerSelfChecking(noOpCallback)
            PrintResult.Success
        } catch (e: Exception) {
            PrintResult.Error(-1, e.message ?: "Self check failed")
        }
    }

    /**
     * Standalone ESC/POS stream directly to thermal MCU bypassing Android typography pipeline.
     */
    override suspend fun printEscPosSample(): PrintResult = withContext(Dispatchers.IO) {
        val service = sunmiService ?: return@withContext PrintResult.PrinterUnavailable
        return@withContext try {
            val escPos = byteArrayOf(
                0x1B, 0x40, // ESC @ (Init)
                0x1B, 0x61, 0x01, // ESC a 1 (Center)
                0x1B, 0x21, 0x30, // ESC ! 0x30 (Double H/W)
                0x50, 0x42, 0x20, 0x53, 0x4D, 0x41, 0x52, 0x54, 0x20, 0x50, 0x41, 0x52, 0x4B, 0x49, 0x4E, 0x47, 0x0A,
                0x1B, 0x21, 0x00, // ESC ! 0x00 (Normal)
                0x53, 0x55, 0x4E, 0x4D, 0x49, 0x20, 0x56, 0x32, 0x2F, 0x56, 0x33, 0x20, 0x54, 0x65, 0x73, 0x74, 0x0A,
                0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x0A,
                0x0A, 0x0A, 0x0A, 0x0A
            )
            service.sendRAWData(escPos, noOpCallback)
            PrintResult.Success
        } catch (e: Exception) {
            PrintResult.Error(-1, e.message ?: "ESC/POS print failed")
        }
    }

    override suspend fun printEntryTicket(
        siteName: String,
        ticketNo: String,
        vehicleNo: String,
        vehicleType: String,
        entryTime: String,
        zoneName: String,
        operatorName: String,
        rateInfo: String
    ): PrintResult = withContext(Dispatchers.IO) {
        val service = sunmiService ?: return@withContext PrintResult.PrinterUnavailable

        return@withContext suspendCancellableCoroutine<PrintResult> { continuation ->
            try {
                val is80mm = capabilities.paperWidth == PaperWidth.WIDTH_80MM
                val divider = if (is80mm) "================================================" else "--------------------------------"

                // 1. Transaction buffer management: wipe any stuck jobs
                try { service.enterPrinterBuffer(true) } catch (_: Exception) {}

                // 2. Initialize printer formatting
                service.printerInit(noOpCallback)

                // 3. Header
                service.setAlignment(1, noOpCallback) // Center
                service.setFontSize(if (is80mm) 32f else 26f, noOpCallback)
                setBold(service, true)
                service.printText("PB SMART PARKING\n", noOpCallback)
                setBold(service, false)

                service.setFontSize(if (is80mm) 22f else 18f, noOpCallback)
                service.printText("$siteName\n", noOpCallback)
                service.printText("ENTRY PASS\n", noOpCallback)
                service.printText("$divider\n", noOpCallback)

                // 4. Ticket Details
                service.setAlignment(0, noOpCallback) // Left
                service.setFontSize(if (is80mm) 24f else 20f, noOpCallback)
                setBold(service, true)
                service.printText("TICKET : $ticketNo\n", noOpCallback)
                service.printText("VEHICLE: $vehicleNo\n", noOpCallback)
                setBold(service, false)

                service.printText("TYPE   : $vehicleType\n", noOpCallback)
                service.printText("ZONE   : $zoneName\n", noOpCallback)
                service.printText("ENTRY  : $entryTime\n", noOpCallback)
                service.printText("OPERATOR: $operatorName\n", noOpCallback)
                service.printText("$divider\n", noOpCallback)

                // 5. Tariff info
                service.setFontSize(16f, noOpCallback)
                service.printText("Rate: $rateInfo\n", noOpCallback)
                service.printText("$divider\n", noOpCallback)

                // 6. QR Code
                service.setAlignment(1, noOpCallback) // Center
                service.printQRCode("TICKET:$ticketNo", if (is80mm) 8 else 6, 2, noOpCallback)
                service.printText("\nScan QR at Exit Counter\n", noOpCallback)
                service.printText("Lost Ticket Fee Applies\n", noOpCallback)
                service.printText("$divider\n", noOpCallback)

                // 7. Feed paper
                service.lineWrap(if (is80mm) 4 else 4, noOpCallback)
                if (is80mm) {
                    try { service.cutPaper(noOpCallback) } catch (_: Exception) {}
                }

                // 8. CRITICAL FOR V3 & V2 UNIVERSALITY:
                // commitPrinterBuffer forces V3 hardware to burn buffered content.
                // Wrapped in try-catch so V2 executes seamlessly.
                try { service.commitPrinterBuffer() } catch (_: Exception) {}
                try { service.exitPrinterBuffer(true) } catch (_: Exception) {}

                if (continuation.isActive) {
                    continuation.resume(PrintResult.Success)
                }

            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(PrintResult.Error(-1, e.message ?: "Exception occurred during entry printing"))
                }
            }
        }
    }

    override suspend fun printExitReceipt(
        siteName: String,
        receiptNo: String,
        ticketNo: String,
        vehicleNo: String,
        vehicleType: String,
        entryTime: String,
        exitTime: String,
        duration: String,
        parkingFee: String,
        discount: String,
        totalPaid: String,
        paymentMethod: String,
        operatorName: String
    ): PrintResult = withContext(Dispatchers.IO) {
        val service = sunmiService ?: return@withContext PrintResult.PrinterUnavailable

        return@withContext suspendCancellableCoroutine<PrintResult> { continuation ->
            try {
                val is80mm = capabilities.paperWidth == PaperWidth.WIDTH_80MM
                val divider = if (is80mm) "================================================" else "--------------------------------"

                // 1. Transaction buffer management
                try { service.enterPrinterBuffer(true) } catch (_: Exception) {}

                // 2. Initialize formatting
                service.printerInit(noOpCallback)

                // 3. Header
                service.setAlignment(1, noOpCallback)
                service.setFontSize(if (is80mm) 32f else 26f, noOpCallback)
                setBold(service, true)
                service.printText("PB SMART PARKING\n", noOpCallback)
                setBold(service, false)

                service.setFontSize(18f, noOpCallback)
                service.printText("$siteName\n", noOpCallback)
                service.printText("OFFICIAL RECEIPT\n", noOpCallback)
                service.printText("Receipt #: $receiptNo\n", noOpCallback)
                service.printText("$divider\n", noOpCallback)

                // 4. Details
                service.setAlignment(0, noOpCallback)
                service.setFontSize(20f, noOpCallback)
                service.printText("Ticket   : $ticketNo\n", noOpCallback)
                setBold(service, true)
                service.printText("Vehicle  : $vehicleNo ($vehicleType)\n", noOpCallback)
                setBold(service, false)
                service.printText("Entry    : $entryTime\n", noOpCallback)
                service.printText("Exit     : $exitTime\n", noOpCallback)
                service.printText("Duration : $duration\n", noOpCallback)
                service.printText("$divider\n", noOpCallback)

                // 5. Payment details
                service.setAlignment(2, noOpCallback) // Right
                service.printText("Parking Fee : NPR $parkingFee\n", noOpCallback)
                if (discount.isNotEmpty() && discount != "0.00") {
                    service.printText("Discount    : -NPR $discount\n", noOpCallback)
                }
                setBold(service, true)
                service.setFontSize(24f, noOpCallback)
                service.printText("TOTAL PAID  : NPR $totalPaid\n", noOpCallback)
                setBold(service, false)
                service.setFontSize(18f, noOpCallback)
                service.printText("Payment Mode: $paymentMethod\n", noOpCallback)
                service.printText("$divider\n", noOpCallback)

                // 6. Footer
                service.setAlignment(1, noOpCallback)
                service.printText("Cashier: $operatorName\n", noOpCallback)
                service.printText("Thank you for parking with us!\n", noOpCallback)
                service.printText("Drive Safely\n", noOpCallback)

                // 7. Feed and cut
                service.lineWrap(if (is80mm) 4 else 4, noOpCallback)
                if (is80mm) {
                    try { service.cutPaper(noOpCallback) } catch (_: Exception) {}
                }

                // 8. Commit and exit buffer
                try { service.commitPrinterBuffer() } catch (_: Exception) {}
                try { service.exitPrinterBuffer(true) } catch (_: Exception) {}

                if (continuation.isActive) {
                    continuation.resume(PrintResult.Success)
                }

            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(PrintResult.Error(-1, e.message ?: "Exception in receipt printing"))
                }
            }
        }
    }

    override suspend fun printMembershipPass(
        siteName: String,
        memberNo: String,
        memberName: String,
        phone: String,
        vehicleNo: String,
        vehicleType: String,
        planType: String,
        validFrom: String,
        validUntil: String,
        amountPaid: String,
        operatorName: String
    ): PrintResult = withContext(Dispatchers.IO) {
        val service = sunmiService ?: return@withContext PrintResult.PrinterUnavailable

        return@withContext suspendCancellableCoroutine<PrintResult> { continuation ->
            try {
                val is80mm = capabilities.paperWidth == PaperWidth.WIDTH_80MM
                val divider = if (is80mm) "================================================" else "--------------------------------"

                // 1. Enter buffer
                try { service.enterPrinterBuffer(true) } catch (_: Exception) {}
                service.printerInit(noOpCallback)

                // 2. Header
                service.setAlignment(1, noOpCallback)
                service.setFontSize(if (is80mm) 32f else 26f, noOpCallback)
                setBold(service, true)
                service.printText("VIP MEMBERSHIP PASS\n", noOpCallback)
                setBold(service, false)

                service.setFontSize(18f, noOpCallback)
                service.printText("$siteName\n", noOpCallback)
                service.printText("PB SMART PARKING\n", noOpCallback)
                service.printText("$divider\n", noOpCallback)

                // 3. Member QR code
                service.setAlignment(1, noOpCallback)
                service.printQRCode(memberNo, 6, 2, noOpCallback)
                service.printText("\n", noOpCallback)

                // 4. Details
                service.setAlignment(0, noOpCallback)
                service.setFontSize(20f, noOpCallback)
                setBold(service, true)
                service.printText("PASS NO : $memberNo\n", noOpCallback)
                service.printText("MEMBER  : $memberName\n", noOpCallback)
                service.printText("VEHICLE : $vehicleNo\n", noOpCallback)
                setBold(service, false)

                service.printText("TYPE    : $vehicleType\n", noOpCallback)
                service.printText("PHONE   : $phone\n", noOpCallback)
                service.printText("PLAN    : $planType\n", noOpCallback)
                service.printText("VALID   : $validFrom\n", noOpCallback)
                setBold(service, true)
                service.printText("EXPIRES : $validUntil\n", noOpCallback)
                setBold(service, false)
                service.printText("$divider\n", noOpCallback)

                // 5. Fee Info
                service.setAlignment(2, noOpCallback)
                service.printText("FEE PAID : NPR $amountPaid\n", noOpCallback)
                service.printText("$divider\n", noOpCallback)

                // 6. Footer
                service.setAlignment(1, noOpCallback)
                service.printText("Issued By: $operatorName\n", noOpCallback)
                service.printText("Scan QR at gate for instant zero-fee access\n", noOpCallback)

                service.lineWrap(if (is80mm) 4 else 4, noOpCallback)
                if (is80mm) {
                    try { service.cutPaper(noOpCallback) } catch (_: Exception) {}
                }

                // 7. Commit & exit buffer
                try { service.commitPrinterBuffer() } catch (_: Exception) {}
                try { service.exitPrinterBuffer(true) } catch (_: Exception) {}

                if (continuation.isActive) {
                    continuation.resume(PrintResult.Success)
                }

            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(PrintResult.Error(-1, e.message ?: "Exception printing member pass"))
                }
            }
        }
    }
}
