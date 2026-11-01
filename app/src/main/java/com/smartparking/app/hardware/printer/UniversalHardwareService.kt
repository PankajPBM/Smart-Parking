package com.smartparking.app.hardware.printer

import android.content.Context
import com.smartparking.app.data.local.SmartParkingDatabase
import com.smartparking.app.data.local.entity.SyncQueueEntity
import com.smartparking.app.hardware.capability.DeviceCapabilities
import com.smartparking.app.hardware.capability.DeviceCapabilityDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Universal Hardware Service Coordinator
 *
 * Coordinates dynamic hardware detection, resilient print execution,
 * offline print job queuing, and automatic retry without blocking transactions.
 */
class UniversalHardwareService(private val context: Context) {

    val capabilities: DeviceCapabilities = DeviceCapabilityDetector(context).detectCapabilities()
    val printer: ParkingPrinter = if (capabilities.hasSunmiPrinterService || capabilities.isSunmiDevice) {
        SunmiAidlPrinterAdapter(context, capabilities)
    } else {
        NoOpPrinter()
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val database = SmartParkingDatabase.getInstance(context)

    /**
     * Executes automatic ticket printing with offline-safe retry queuing
     */
    fun autoPrintEntryTicket(
        siteName: String,
        ticketNo: String,
        vehicleNo: String,
        vehicleType: String,
        entryTime: String,
        zoneName: String,
        operatorName: String,
        rateInfo: String,
        onResult: (PrintResult) -> Unit = {}
    ) {
        scope.launch {
            val result = printer.printEntryTicket(
                siteName = siteName,
                ticketNo = ticketNo,
                vehicleNo = vehicleNo,
                vehicleType = vehicleType,
                entryTime = entryTime,
                zoneName = zoneName,
                operatorName = operatorName,
                rateInfo = rateInfo
            )

            if (result !is PrintResult.Success) {
                // Queue print job in local database for subsequent retry
                database.syncQueueDao().enqueue(
                    SyncQueueEntity(
                        eventType = "PRINT_ENTRY_TICKET",
                        payloadJson = """{"ticketNo":"$ticketNo","vehicleNo":"$vehicleNo","site":"$siteName"}"""
                    )
                )
            }

            onResult(result)
        }
    }

    /**
     * Executes automatic exit receipt printing with offline-safe retry queuing
     */
    fun autoPrintExitReceipt(
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
        operatorName: String,
        onResult: (PrintResult) -> Unit = {}
    ) {
        scope.launch {
            val result = printer.printExitReceipt(
                siteName = siteName,
                receiptNo = receiptNo,
                ticketNo = ticketNo,
                vehicleNo = vehicleNo,
                vehicleType = vehicleType,
                entryTime = entryTime,
                exitTime = exitTime,
                duration = duration,
                parkingFee = parkingFee,
                discount = discount,
                totalPaid = totalPaid,
                paymentMethod = paymentMethod,
                operatorName = operatorName
            )

            if (result !is PrintResult.Success) {
                database.syncQueueDao().enqueue(
                    SyncQueueEntity(
                        eventType = "PRINT_EXIT_RECEIPT",
                        payloadJson = """{"receiptNo":"$receiptNo","ticketNo":"$ticketNo","totalPaid":"$totalPaid"}"""
                    )
                )
            }

            onResult(result)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: UniversalHardwareService? = null

        fun getInstance(context: Context): UniversalHardwareService {
            return INSTANCE ?: synchronized(this) {
                val instance = UniversalHardwareService(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Fallback no-op printer for non-printer hardware
 */
class NoOpPrinter : ParkingPrinter {
    override suspend fun printEntryTicket(
        siteName: String, ticketNo: String, vehicleNo: String, vehicleType: String,
        entryTime: String, zoneName: String, operatorName: String, rateInfo: String
    ): PrintResult = PrintResult.PrinterUnavailable

    override suspend fun printExitReceipt(
        siteName: String, receiptNo: String, ticketNo: String, vehicleNo: String,
        vehicleType: String, entryTime: String, exitTime: String, duration: String,
        parkingFee: String, discount: String, totalPaid: String, paymentMethod: String,
        operatorName: String
    ): PrintResult = PrintResult.PrinterUnavailable

    override suspend fun getPrinterStatus(): PrintResult = PrintResult.PrinterUnavailable
}
