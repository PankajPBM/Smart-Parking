package com.smartparking.app.hardware.printer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
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
 * Clean hardware-agnostic parking printer interface
 */
interface ParkingPrinter {
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
}

/**
 * Official SUNMI AIDL-backed Parking Printer Adapter
 */
class SunmiAidlPrinterAdapter(
    private val context: Context,
    private val capabilities: DeviceCapabilities
) : ParkingPrinter {

    private var woyouService: IWoyouService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            woyouService = IWoyouService.Stub.asInterface(service)
            isBound = true
            try {
                woyouService?.printerInit(null)
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
        bindService()
    }

    private fun bindService() {
        try {
            val intent = Intent().apply {
                setPackage("woyou.aidlservice.jiuiv5")
                action = "woyou.aidlservice.jiuiv5.IWoyouService"
            }
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unbindService() {
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isBound = false
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

                // Enter transaction buffer mode for fast atomic printing
                service.enterPrinterBuffer(true)

                // Header
                service.setAlignment(1, null) // Center
                service.setFontSize(if (is80mm) 32f else 26f, null)
                service.sendRAWData(byteArrayOf(0x1B, 0x45, 0x01), null) // Bold on
                service.printText("SMART PARKING\n", null)
                service.sendRAWData(byteArrayOf(0x1B, 0x45, 0x00), null) // Bold off

                service.setFontSize(if (is80mm) 22f else 18f, null)
                service.printText("$siteName\n", null)
                service.printText("ENTRY PASS\n", null)
                service.printText("$divider\n", null)

                // Details
                service.setAlignment(0, null) // Left
                service.setFontSize(if (is80mm) 24f else 20f, null)
                service.sendRAWData(byteArrayOf(0x1B, 0x45, 0x01), null)
                service.printText("TICKET : $ticketNo\n", null)
                service.printText("VEHICLE: $vehicleNo\n", null)
                service.sendRAWData(byteArrayOf(0x1B, 0x45, 0x00), null)

                service.printText("TYPE   : $vehicleType\n", null)
                service.printText("ZONE   : $zoneName\n", null)
                service.printText("ENTRY  : $entryTime\n", null)
                service.printText("OPERATOR: $operatorName\n", null)
                service.printText("$divider\n", null)

                // Tariff info
                service.setFontSize(16f, null)
                service.printText("Rate: $rateInfo\n", null)
                service.printText("$divider\n", null)

                // QR Code
                service.setAlignment(1, null) // Center
                service.printQRCode("TICKET:$ticketNo", if (is80mm) 8 else 6, 2, null)
                service.printText("\nScan QR at Exit Counter\n", null)
                service.printText("Lost Ticket Fee Applies\n", null)
                service.printText("$divider\n\n", null)

                // Feed and cut
                service.lineWrap(3, null)
                if (is80mm) {
                    service.cutPaper(null)
                }

                // Execute print buffer
                service.exitPrinterBuffer(true)
                if (continuation.isActive) {
                    continuation.resume(PrintResult.Success)
                }

            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(PrintResult.Error(-1, e.message ?: "Exception occurred during printing"))
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

                service.enterPrinterBuffer(true)

                // Header
                service.setAlignment(1, null)
                service.setFontSize(if (is80mm) 32f else 26f, null)
                service.sendRAWData(byteArrayOf(0x1B, 0x45, 0x01), null)
                service.printText("SMART PARKING\n", null)
                service.sendRAWData(byteArrayOf(0x1B, 0x45, 0x00), null)

                service.setFontSize(18f, null)
                service.printText("$siteName\n", null)
                service.printText("OFFICIAL RECEIPT\n", null)
                service.printText("Receipt #: $receiptNo\n", null)
                service.printText("$divider\n", null)

                // Body
                service.setAlignment(0, null)
                service.setFontSize(20f, null)
                service.printText("Ticket   : $ticketNo\n", null)
                service.sendRAWData(byteArrayOf(0x1B, 0x45, 0x01), null)
                service.printText("Vehicle  : $vehicleNo ($vehicleType)\n", null)
                service.sendRAWData(byteArrayOf(0x1B, 0x45, 0x00), null)
                service.printText("Entry    : $entryTime\n", null)
                service.printText("Exit     : $exitTime\n", null)
                service.printText("Duration : $duration\n", null)
                service.printText("$divider\n", null)

                // Amounts
                service.setAlignment(2, null) // Right
                service.printText("Parking Fee : $parkingFee\n", null)
                if (discount.isNotEmpty() && discount != "0.00") {
                    service.printText("Discount    : -$discount\n", null)
                }
                service.sendRAWData(byteArrayOf(0x1B, 0x45, 0x01), null)
                service.setFontSize(24f, null)
                service.printText("TOTAL PAID  : $totalPaid\n", null)
                service.sendRAWData(byteArrayOf(0x1B, 0x45, 0x00), null)
                service.setFontSize(18f, null)
                service.printText("Payment Mode: $paymentMethod\n", null)
                service.printText("$divider\n", null)

                // Footer
                service.setAlignment(1, null)
                service.printText("Cashier: $operatorName\n", null)
                service.printText("Thank you for parking with us!\n", null)
                service.printText("Drive Safely\n\n", null)

                service.lineWrap(3, null)
                if (is80mm) {
                    service.cutPaper(null)
                }

                // Execute print buffer
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

                service.enterPrinterBuffer(true)

                // Header
                service.setAlignment(1, null)
                service.setFontSize(if (is80mm) 32f else 26f, null)
                service.sendRAWData(byteArrayOf(0x1B, 0x45, 0x01), null)
                service.printText("VIP MEMBERSHIP PASS\n", null)
                service.sendRAWData(byteArrayOf(0x1B, 0x45, 0x00), null)

                service.setFontSize(18f, null)
                service.printText("$siteName\n", null)
                service.printText("PB SMART PARKING\n", null)
                service.printText("$divider\n", null)

                // Member QR code for fast entry/exit scanning
                service.setAlignment(1, null)
                service.printQRCode(memberNo, 6, 2, null)
                service.printText("\n", null)

                // Details
                service.setAlignment(0, null)
                service.setFontSize(20f, null)
                service.sendRAWData(byteArrayOf(0x1B, 0x45, 0x01), null)
                service.printText("PASS NO : $memberNo\n", null)
                service.printText("MEMBER  : $memberName\n", null)
                service.printText("VEHICLE : $vehicleNo\n", null)
                service.sendRAWData(byteArrayOf(0x1B, 0x45, 0x00), null)

                service.printText("TYPE    : $vehicleType\n", null)
                service.printText("PHONE   : $phone\n", null)
                service.printText("PLAN    : $planType\n", null)
                service.printText("VALID   : $validFrom\n", null)
                service.sendRAWData(byteArrayOf(0x1B, 0x45, 0x01), null)
                service.printText("EXPIRES : $validUntil\n", null)
                service.sendRAWData(byteArrayOf(0x1B, 0x45, 0x00), null)
                service.printText("$divider\n", null)

                // Fee Info
                service.setAlignment(2, null)
                service.printText("FEE PAID : NPR $amountPaid\n", null)
                service.printText("$divider\n", null)

                // Footer
                service.setAlignment(1, null)
                service.printText("Issued By: $operatorName\n", null)
                service.printText("Scan QR at gate for instant zero-fee access\n", null)
                service.printText("PB Tech Zone - 9814891277\n\n", null)

                service.lineWrap(3, null)
                if (is80mm) {
                    service.cutPaper(null)
                }

                // Execute print buffer
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
