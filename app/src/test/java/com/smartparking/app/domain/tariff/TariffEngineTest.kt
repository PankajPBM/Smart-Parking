package com.smartparking.app.domain.tariff

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * Unit tests for TariffEngine
 * Tests grace periods, tiered rates, daily caps, lost ticket penalties
 */
class TariffEngineTest {

    private lateinit var tariffEngine: TariffEngine
    private lateinit var sampleCarTariff: TariffConfig
    private lateinit var sampleBikeTariff: TariffConfig

    @Before
    fun setUp() {
        tariffEngine = TariffEngine()

        // Setup sample car tariff
        sampleCarTariff = TariffConfig(
            id = "tariff_car_test",
            name = "Test Car Tariff",
            vehicleTypeId = "CAR",
            gracePeriodMinutes = 10,
            minCharge = BigDecimal("50.00"),
            maxDailyCharge = BigDecimal("500.00"),
            lostTicketFee = BigDecimal("300.00"),
            tiers = listOf(
                TariffTier(0, 60, BigDecimal("50.00"), isFixed = true),       // 0-1h: 50
                TariffTier(60, 120, BigDecimal("80.00"), isFixed = true),     // 1-2h: 80
                TariffTier(120, 240, BigDecimal("150.00"), isFixed = true),   // 2-4h: 150
                TariffTier(240, null, BigDecimal("30.00"), isFixed = false)   // 4h+: 30/hr
            )
        )

        // Setup sample bike tariff
        sampleBikeTariff = TariffConfig(
            id = "tariff_bike_test",
            name = "Test Bike Tariff",
            vehicleTypeId = "MOTORCYCLE",
            gracePeriodMinutes = 10,
            minCharge = BigDecimal("25.00"),
            maxDailyCharge = BigDecimal("200.00"),
            lostTicketFee = BigDecimal("150.00"),
            tiers = listOf(
                TariffTier(0, 60, BigDecimal("25.00"), isFixed = true),
                TariffTier(60, 120, BigDecimal("40.00"), isFixed = true),
                TariffTier(120, null, BigDecimal("15.00"), isFixed = false)
            )
        )
    }

    @Test
    fun `grace period test - free within 10 minutes`() {
        val result = tariffEngine.calculateFee(
            durationMinutes = 8,
            tariffConfig = sampleCarTariff
        )

        assertEquals(BigDecimal.ZERO, result.netAmount)
        assertTrue(result.breakdown.contains("grace period"))
    }

    @Test
    fun `first hour test - flat rate applied`() {
        val result = tariffEngine.calculateFee(
            durationMinutes = 45,
            tariffConfig = sampleCarTariff
        )

        assertEquals(BigDecimal("50.00"), result.netAmount)
    }

    @Test
    fun `second hour tier test`() {
        val result = tariffEngine.calculateFee(
            durationMinutes = 90, // 1h 30m
            tariffConfig = sampleCarTariff
        )

        // First hour (50) + Second hour (80) = 130
        assertEquals(BigDecimal("130.00"), result.netAmount)
    }

    @Test
    fun `long duration test - hourly increments applied`() {
        val result = tariffEngine.calculateFee(
            durationMinutes = 300, // 5 hours
            tariffConfig = sampleCarTariff
        )

        // Tier 1 (50) + Tier 2 (80) + Tier 3 (150) + 1 hour beyond 4h (30) = 310
        assertEquals(BigDecimal("310.00"), result.netAmount)
    }

    @Test
    fun `daily maximum cap test`() {
        val result = tariffEngine.calculateFee(
            durationMinutes = 1440, // 24 hours
            tariffConfig = sampleCarTariff
        )

        // Should cap at maxDailyCharge (500)
        assertEquals(BigDecimal("500.00"), result.netAmount)
        assertTrue(result.breakdown.contains("Daily cap"))
    }

    @Test
    fun `lost ticket penalty test`() {
        val result = tariffEngine.calculateFee(
            durationMinutes = 0,
            tariffConfig = sampleCarTariff,
            isLostTicket = true
        )

        assertEquals(BigDecimal("300.00"), result.netAmount)
        assertTrue(result.breakdown.contains("Lost Ticket"))
    }

    @Test
    fun `duration formatting test`() {
        assertEquals("2h 15m", tariffEngine.formatDuration(135))
        assertEquals("3h", tariffEngine.formatDuration(180))
        assertEquals("45m", tariffEngine.formatDuration(45))
    }
}
