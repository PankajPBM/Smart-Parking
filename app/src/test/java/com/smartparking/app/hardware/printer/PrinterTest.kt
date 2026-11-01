package com.smartparking.app.hardware.printer

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Mock/Unit tests for Printer and Hardware Layer
 */
class PrinterTest {

    private lateinit var noOpPrinter: NoOpPrinter

    @Before
    fun setUp() {
        noOpPrinter = NoOpPrinter()
    }

    @Test
    fun `no-op printer returns PrinterUnavailable`() = kotlinx.coroutines.runBlocking {
        val result = noOpPrinter.printEntryTicket(
            siteName = "Test Site",
            ticketNo = "SP-001",
            vehicleNo = "BA 12 PA 1234",
            vehicleType = "CAR",
            entryTime = "09:00 AM",
            zoneName = "Zone A",
            operatorName = "Op 1",
            rateInfo = "50/hr"
        )

        assertEquals(PrintResult.PrinterUnavailable, result)
    }

    @Test
    fun `printer status returns unavailable on fallback`() = kotlinx.coroutines.runBlocking {
        val status = noOpPrinter.getPrinterStatus()
        assertEquals(PrintResult.PrinterUnavailable, status)
    }
}
