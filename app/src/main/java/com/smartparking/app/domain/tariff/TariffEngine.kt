package com.smartparking.app.domain.tariff

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil

/**
 * Smart Tariff Calculation Engine
 *
 * Handles dynamic pricing based on:
 * - Grace periods (free parking for initial minutes)
 * - Tiered hourly rates
 * - Vehicle type differentiation
 * - Daily maximum caps
 * - Lost ticket penalties
 */
class TariffEngine {

    /**
     * Calculates parking fee based on tariff configuration and duration
     */
    fun calculateFee(
        durationMinutes: Long,
        tariffConfig: TariffConfig,
        isLostTicket: Boolean = false,
        memberDiscountPercent: Int? = null,
        memberInfo: String? = null
    ): TariffCalculationResult {

        // Lost ticket penalty
        if (isLostTicket) {
            return TariffCalculationResult(
                durationMinutes = 0,
                grossAmount = tariffConfig.lostTicketFee,
                discountAmount = BigDecimal.ZERO,
                netAmount = tariffConfig.lostTicketFee,
                breakdown = "Lost Ticket Penalty: ${formatCurrency(tariffConfig.lostTicketFee)}"
            )
        }

        // Grace period check
        if (durationMinutes <= tariffConfig.gracePeriodMinutes) {
            return TariffCalculationResult(
                durationMinutes = durationMinutes,
                grossAmount = BigDecimal.ZERO,
                discountAmount = BigDecimal.ZERO,
                netAmount = BigDecimal.ZERO,
                breakdown = "Free (Within ${tariffConfig.gracePeriodMinutes} min grace period)"
            )
        }

        // Apply tiered pricing
        var totalFee = BigDecimal.ZERO
        val breakdownParts = mutableListOf<String>()

        for (tier in tariffConfig.tiers.sortedBy { it.fromMinutes }) {
            val tierStart = tier.fromMinutes
            val tierEnd = tier.toMinutes ?: Long.MAX_VALUE

            if (durationMinutes <= tierStart) {
                break
            }

            val applicableMinutes = minOf(durationMinutes, tierEnd) - tierStart
            if (applicableMinutes <= 0) continue

            val tierFee = if (tier.isFixed) {
                tier.rate
            } else {
                // Hourly rate calculation (round up partial hours)
                val hours = ceil(applicableMinutes / 60.0).toLong()
                tier.rate.multiply(BigDecimal(hours))
            }

            totalFee = totalFee.add(tierFee)

            val tierLabel = if (tier.toMinutes != null) {
                "${tier.fromMinutes / 60}h - ${tier.toMinutes / 60}h"
            } else {
                "Beyond ${tier.fromMinutes / 60}h"
            }
            breakdownParts.add("$tierLabel: ${formatCurrency(tierFee)}")
        }

        // Apply minimum charge
        if (totalFee < tariffConfig.minCharge) {
            totalFee = tariffConfig.minCharge
            breakdownParts.add("(Minimum charge applied)")
        }

        // Apply daily maximum cap
        if (tariffConfig.maxDailyCharge != null && totalFee > tariffConfig.maxDailyCharge) {
            breakdownParts.add("Daily cap reached: ${formatCurrency(tariffConfig.maxDailyCharge)}")
            totalFee = tariffConfig.maxDailyCharge
        }

        val gross = totalFee.setScale(2, RoundingMode.HALF_UP)
        var discount = BigDecimal.ZERO
        var net = gross

        if (memberDiscountPercent != null && memberDiscountPercent > 0) {
            val pct = memberDiscountPercent.coerceIn(0, 100)
            discount = gross.multiply(BigDecimal(pct)).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
            net = gross.subtract(discount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
            val infoTag = memberInfo?.let { " ($it)" } ?: ""
            breakdownParts.add("Member Pass Discount -$pct%$infoTag: -${formatCurrency(discount)}")
        }

        return TariffCalculationResult(
            durationMinutes = durationMinutes,
            grossAmount = gross,
            discountAmount = discount,
            netAmount = net,
            breakdown = breakdownParts.joinToString(" + ")
        )
    }

    /**
     * Calculates duration in minutes from entry to exit timestamps
     */
    fun calculateDuration(entryTimeMillis: Long, exitTimeMillis: Long): Long {
        val durationMillis = exitTimeMillis - entryTimeMillis
        return (durationMillis / 60000).coerceAtLeast(0)
    }

    /**
     * Formats duration as human-readable string (e.g., "2h 15m")
     */
    fun formatDuration(minutes: Long): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    private fun formatCurrency(amount: BigDecimal): String {
        return "NPR ${amount.setScale(2, RoundingMode.HALF_UP)}"
    }
}

/**
 * Tariff configuration for a specific vehicle type at a site
 */
data class TariffConfig(
    val id: String,
    val name: String,
    val vehicleTypeId: String,
    val gracePeriodMinutes: Long,
    val minCharge: BigDecimal,
    val maxDailyCharge: BigDecimal?,
    val lostTicketFee: BigDecimal = BigDecimal("300.00"),
    val tiers: List<TariffTier>
)

/**
 * Individual pricing tier (time bracket with associated rate)
 */
data class TariffTier(
    val fromMinutes: Long,
    val toMinutes: Long?, // null = infinity
    val rate: BigDecimal,
    val isFixed: Boolean // true = flat rate, false = hourly multiplier
)

/**
 * Result of tariff calculation
 */
data class TariffCalculationResult(
    val durationMinutes: Long,
    val grossAmount: BigDecimal,
    val discountAmount: BigDecimal,
    val netAmount: BigDecimal,
    val breakdown: String
)
