package com.smartparking.app.hardware.printer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import com.smartparking.app.hardware.capability.DeviceCapabilities
import com.smartparking.app.hardware.capability.PaperWidth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import woyou.aidlservice.jiuiv5.ICallback
import woyou.aidlservice.jiuiv5.IWoyouService
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
 * Enterprise Dual-Engine SUNMI AIDL Adapter
 *
 * Implements full backward & forward compatibility across:
 * - SUNMI V2 / V2s (Android 7.1 - 11, Direct & Buffered streams)
 * - SUNMI V3 / V3 Mix (Android 13 / SUNMI OS 4+, Transaction Buffer mode, strict Binder tokens)
 */
class SunmiAidlPrinterAdapter(
    private val appContext: Context,
    private val capabilities: DeviceCapabilities
) : ParkingPrinter {

    @Volatile
    private var woyouService: IWoyouService? = null
    @Volatile
    private var isBound = false

    /**
     * Non-null reusable callback binder stub.
     * Prevents Android 13 Binder crashes on SUNMI V3 while remaining 100% compatible with SUNMI V2.
     */
    private val noOpCallback = object : ICallback.Stub() {
        override fun onRunResult(isSuccess: Boolean) {}
        override fun onReturnString(result: String?) {}
        override fun onRaiseException(code: Int, msg: String?) {}
        override fun onPrintResult(code: Int, msg: String?) {}
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            woyouService = IWoyouService.Stub.asInterface(service)
            isBound = true
            try {
                woyouService?.printerInit(noOpCallback)
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            woyouService = null
            isBound = false
        }
    }

    init {
        bind(appContext)
    }

    /**
     * High-priority Dual-Context Service Binding with Foreground Token.
     * Binds with BIND_AUTO_CREATE or BIND_ABOVE_CLIENT so Android 13 security manager grants print access.
     */
    override fun bind(context: Context) {
        if (isBound && woyouService != null) return
        try {
            val intent = Intent().apply {
                setPackage("woyou.aidlservice.jiuiv5")
                action = "woyou.aidlservice.jiuiv5.IWoyouService"
            }
            val flags = Context.BIND_AUTO_CREATE or Context.BIND_ABOVE_CLIENT
            context.bindService(intent, serviceConnection, flags)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unbindService(context: Context = appContext) {
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isBound = false
            woyouService = null
        }
    }

    /**
     * Official SDK typography style without fragmented raw byte injection.
     * Protects printhead pipeline on Android 13 SUNMI OS 4 from state machine corruptions.
     */
    private fun setBold(service: IWoyouService, enable: Boolean) {
        try {
            // WoyouConsts.ENABLE_BOLD = 2, ENABLE = 1, DISABLE = 2
            service.setPrinterStyle(2, if (enable) 1 else 2)
        } catch (_: Exception) {
            // Graceful fallback for older V2 firmware that does not support setPrinterStyle
        }
    }

    override suspend fun getPrinterStatus(): PrintResult = withContext(Dispatchers.IO) {
        val service = woyouService ?: return@withContext PrintResult.PrinterUnavailable
        return@withContext try {
            when (service.updatePrinterState()) {
                0 -> PrintResult.Success
                1 -> PrintResult.Error(1, "Printer is busy")
                2 -> PrintResult.OutOfPaper
                3 -> PrintResult.Overheated
                4 -> PrintResult.Error(4, "Printer cover is open")
                else -> PrintResult.Success
            }
        } catch (e: RemoteException) {
            PrintResult.Error(-1, e.message ?: "Remote printer exception")
        }
    }

    /**
     * Runs internal hardware calibration pattern directly on thermal MCU
     */
    override suspend fun printerSelfChecking(): PrintResult = withContext(Dispatchers.IO) {
        val service = woyouService ?: return@withContext PrintResult.PrinterUnavailable
        return@withContext try {
            service.printerSelfChecking(noOpCallback)
            PrintResult.Success
        } catch (e: Exception) {
            PrintResult.Error(-1, e.message ?: "Self check failed")
        }
    }

    /**
     * Standalone ESC/POS stream directly to thermal MCU bypassing Android typography pipeline
     */
    override suspend fun printEscPosSample(): PrintResult = withContext(Dispatchers.IO) {
        val service = woyouService ?: return@withContext PrintResult.PrinterUnavailable
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
        val service = woyouService ?: return@withContext PrintResult.PrinterUnavailable

        return@withContext suspendCancellableCoroutine<PrintResult> { continuation ->
            try {
                val is80mm = capabilities.paperWidth == PaperWidth.WIDTH_80MM
                val divider = if (is80mm) "================================================" else "--------------------------------"

                // 1. Enter transaction buffer and clear previous stale jobs
                service.enterPrinterBuffer(true)

                // 2. Initialize formatting with non-null callback
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

                // 8. CRITICAL FOR V3 & V2: Force printhead to burn the buffered content
                try {
                    service.commitPrinterBuffer()
                } catch (_: Exception) {}

                service.exitPrinterBuffer(true)

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
        val service = woyouService ?: return@withContext PrintResult.PrinterUnavailable

        return@withContext suspendCancellableCoroutine<PrintResult> { continuation ->
            try {
                val is80mm = capabilities.paperWidth == PaperWidth.WIDTH_80MM
                val divider = if (is80mm) "================================================" else "--------------------------------"

                // 1. Enter transaction buffer and clear stale jobs
                service.enterPrinterBuffer(true)

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
                try {
                    service.commitPrinterBuffer()
                } catch (_: Exception) {}

                service.exitPrinterBuffer(true)

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
        val service = woyouService ?: return@withContext PrintResult.PrinterUnavailable

        return@withContext suspendCancellableCoroutine<PrintResult> { continuation ->
            try {
                val is80mm = capabilities.paperWidth == PaperWidth.WIDTH_80MM
                val divider = if (is80mm) "================================================" else "--------------------------------"

                // 1. Enter buffer
                service.enterPrinterBuffer(true)
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
                try {
                    service.commitPrinterBuffer()
                } catch (_: Exception) {}

                service.exitPrinterBuffer(true)

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
