package com.smartparking.app.hardware.sunmi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

/**
 * Universal Hardware Manager for SUNMI Devices.
 * Handles auto-detection of device model, paper width (58mm vs 80mm),
 * printer service binding via AIDL/SDK, and QR code generation.
 */
class SunmiHardwareManager(private val context: Context) {

    val deviceModel: SunmiDeviceModel = SunmiDeviceModel.detect()
    private var isPrinterBound = false
    private var printerService: Any? = null

    init {
        bindPrinterService()
    }

    private fun bindPrinterService() {
        try {
            // Using reflection / AIDL to bind to SunmiPrinterService
            // Works seamlessly across all Sunmi OS versions
            val innerPrinterClass = Class.forName("com.sunmi.peripheral.printer.InnerPrinterManager")
            val getInstanceMethod = innerPrinterClass.getMethod("getInstance")
            val managerInstance = getInstanceMethod.invoke(null)

            // InnerPrinterCallback listener class
            val callbackClass = Class.forName("com.sunmi.peripheral.printer.InnerPrinterCallback")
            val proxyHandler = java.lang.reflect.Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass)
            ) { _, method, args ->
                when (method.name) {
                    "onConnected" -> {
                        printerService = args?.get(0)
                        isPrinterBound = true
                        initPrinter()
                    }
                    "onDisconnected" -> {
                        printerService = null
                        isPrinterBound = false
                    }
                }
                null
            }

            val bindMethod = innerPrinterClass.getMethod("bindService", Context::class.java, callbackClass)
            bindMethod.invoke(managerInstance, context, proxyHandler)
        } catch (e: Exception) {
            // Fallback for non-Sunmi or older Sunmi service architecture
            isPrinterBound = false
        }
    }

    private fun initPrinter() {
        try {
            printerService?.let { service ->
                val initMethod = service.javaClass.getMethod("printerInit", Class.forName("com.sunmi.peripheral.printer.InnerResultCallback"))
                initMethod.invoke(service, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Prints a formatted Vehicle Parking Entry Ticket
     */
    fun printEntryTicket(
        siteName: String,
        ticketNo: String,
        vehicleNo: String,
        vehicleType: String,
        entryTime: String,
        zoneName: String,
        operatorName: String,
        rateInfo: String
    ): Boolean {
        val qrBitmap = generateQrBitmap("TICKET:$ticketNo", if (deviceModel.paperWidthMm == 80) 360 else 240)

        return try {
            val divider = if (deviceModel.paperWidthMm == 80) {
                "================================================"
            } else {
                "--------------------------------"
            }

            // 1. Header (Centered, Bold, Large)
            setAlignment(1)
            setFontSize(if (deviceModel.paperWidthMm == 80) 32f else 26f)
            setBold(true)
            printText("SMART PARKING\n")
            setBold(false)

            setFontSize(if (deviceModel.paperWidthMm == 80) 22f else 18f)
            printText("$siteName\n")
            printText("ENTRY PASS\n")
            printText("$divider\n")

            // 2. Ticket Details (Left aligned)
            setAlignment(0)
            setFontSize(if (deviceModel.paperWidthMm == 80) 24f else 20f)
            setBold(true)
            printText("TICKET : $ticketNo\n")
            printText("VEHICLE: $vehicleNo\n")
            setBold(false)

            printText("TYPE   : $vehicleType\n")
            printText("ZONE   : $zoneName\n")
            printText("ENTRY  : $entryTime\n")
            printText("OPERATOR: $operatorName\n")
            printText("$divider\n")

            // 3. Tariff summary
            setFontSize(16f)
            printText("Rate: $rateInfo\n")
            printText("$divider\n")

            // 4. QR Code
            setAlignment(1)
            qrBitmap?.let { printBitmap(it) }
            printText("\nScan QR at Exit Counter\n")
            printText("Lost Ticket Fee Applies\n")
            printText("$divider\n\n\n")

            // 5. Feed and auto cut if available
            lineWrap(3)
            cutPaper()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Prints a formatted Vehicle Parking Exit & Tax Receipt
     */
    fun printExitReceipt(
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
    ): Boolean {
        return try {
            val divider = if (deviceModel.paperWidthMm == 80) {
                "================================================"
            } else {
                "--------------------------------"
            }

            // 1. Header
            setAlignment(1)
            setFontSize(if (deviceModel.paperWidthMm == 80) 32f else 26f)
            setBold(true)
            printText("SMART PARKING\n")
            setBold(false)

            setFontSize(18f)
            printText("$siteName\n")
            printText("OFFICIAL RECEIPT\n")
            printText("Receipt #: $receiptNo\n")
            printText("$divider\n")

            // 2. Session details
            setAlignment(0)
            setFontSize(20f)
            printText("Ticket   : $ticketNo\n")
            setBold(true)
            printText("Vehicle  : $vehicleNo ($vehicleType)\n")
            setBold(false)
            printText("Entry    : $entryTime\n")
            printText("Exit     : $exitTime\n")
            setBold(true)
            printText("Duration : $duration\n")
            setBold(false)
            printText("$divider\n")

            // 3. Financial breakdown
            setAlignment(2) // Right aligned
            printText("Parking Fee : $parkingFee\n")
            if (discount.isNotEmpty() && discount != "0.00") {
                printText("Discount    : -$discount\n")
            }
            setBold(true)
            setFontSize(24f)
            printText("TOTAL PAID  : $totalPaid\n")
            setBold(false)
            setFontSize(18f)
            printText("Payment Mode: $paymentMethod\n")
            setAlignment(0)
            printText("$divider\n")

            // 4. Footer
            setAlignment(1)
            printText("Cashier: $operatorName\n")
            printText("Thank you for parking with us!\n")
            printText("Drive Safely\n\n\n")

            lineWrap(3)
            cutPaper()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Helper SUNMI Low-Level Commands
    private fun setAlignment(align: Int) {
        // 0: Left, 1: Center, 2: Right
        try {
            printerService?.let { service ->
                val method = service.javaClass.getMethod("setAlignment", Int::class.javaPrimitiveType, Class.forName("com.sunmi.peripheral.printer.InnerResultCallback"))
                method.invoke(service, align, null)
            }
        } catch (e: Exception) { /* ignored */ }
    }

    private fun setFontSize(size: Float) {
        try {
            printerService?.let { service ->
                val method = service.javaClass.getMethod("setFontSize", Float::class.javaPrimitiveType, Class.forName("com.sunmi.peripheral.printer.InnerResultCallback"))
                method.invoke(service, size, null)
            }
        } catch (e: Exception) { /* ignored */ }
    }

    private fun setBold(isBold: Boolean) {
        try {
            val boldCode = if (isBold) byteArrayOf(0x1B, 0x45, 0x01) else byteArrayOf(0x1B, 0x45, 0x00)
            printerService?.let { service ->
                val method = service.javaClass.getMethod("sendRAWAndNoPrintData", ByteArray::class.java, Class.forName("com.sunmi.peripheral.printer.InnerResultCallback"))
                method.invoke(service, boldCode, null)
            }
        } catch (e: Exception) { /* ignored */ }
    }

    private fun printText(text: String) {
        try {
            printerService?.let { service ->
                val method = service.javaClass.getMethod("printText", String::class.java, Class.forName("com.sunmi.peripheral.printer.InnerResultCallback"))
                method.invoke(service, text, null)
            }
        } catch (e: Exception) { /* ignored */ }
    }

    private fun printBitmap(bitmap: Bitmap) {
        try {
            printerService?.let { service ->
                val method = service.javaClass.getMethod("printBitmap", Bitmap::class.java, Class.forName("com.sunmi.peripheral.printer.InnerResultCallback"))
                method.invoke(service, bitmap, null)
            }
        } catch (e: Exception) { /* ignored */ }
    }

    private fun lineWrap(lines: Int) {
        try {
            printerService?.let { service ->
                val method = service.javaClass.getMethod("lineWrap", Int::class.javaPrimitiveType, Class.forName("com.sunmi.peripheral.printer.InnerResultCallback"))
                method.invoke(service, lines, null)
            }
        } catch (e: Exception) { /* ignored */ }
    }

    private fun cutPaper() {
        try {
            printerService?.let { service ->
                val method = service.javaClass.getMethod("cutPaper", Class.forName("com.sunmi.peripheral.printer.InnerResultCallback"))
                method.invoke(service, null)
            }
        } catch (e: Exception) { /* ignored on non-cutter models */ }
    }

    /**
     * Generates a crisp monochrome QR Code Bitmap for thermal printing
     */
    fun generateQrBitmap(content: String, size: Int): Bitmap? {
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                put(EncodeHintType.MARGIN, 1)
            }
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }

            Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
