package com.smartparking.app.domain.tariff

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Repository for parking tariffs.
 * Provides client-configured pricing synced from Cloudflare D1 with instant offline fallback.
 */
class TariffRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pb_parking_tariffs_cache", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var INSTANCE: TariffRepository? = null

        fun getInstance(context: Context): TariffRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = TariffRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Retrieves the active tariff configuration for a vehicle type.
     * Uses cloud-synced client rates if available, otherwise falls back to defaults.
     */
    fun getTariff(vehicleTypeId: String): TariffConfig {
        val normalizedType = vehicleTypeId.trim().uppercase()
        val cachedJson = prefs.getString("tariff_$normalizedType", null)

        if (!cachedJson.isNullOrBlank()) {
            try {
                val json = JSONObject(cachedJson)
                val tiersArray = json.optJSONArray("tiers") ?: JSONArray()
                val tiers = mutableListOf<TariffTier>()

                for (i in 0 until tiersArray.length()) {
                    val t = tiersArray.getJSONObject(i)
                    val fromMins = t.optLong("fromMinutes", 0)
                    val toMins = if (t.isNull("toMinutes")) null else t.optLong("toMinutes")
                    val rate = BigDecimal(t.optDouble("rate", 0.0).toString())
                    val isFixed = t.optBoolean("isFixed", true)
                    tiers.add(TariffTier(fromMins, toMins, rate, isFixed))
                }

                return TariffConfig(
                    id = json.optString("id", "${normalizedType.lowercase()}_tariff"),
                    name = json.optString("vehicleLabel", "$normalizedType Tariff"),
                    vehicleTypeId = normalizedType,
                    gracePeriodMinutes = json.optLong("gracePeriodMinutes", 10),
                    minCharge = BigDecimal(json.optDouble("minCharge", 25.0).toString()),
                    maxDailyCharge = if (json.isNull("maxDailyCharge")) null else BigDecimal(json.optDouble("maxDailyCharge").toString()),
                    lostTicketFee = BigDecimal(json.optDouble("lostTicketFee", 300.0).toString()),
                    tiers = if (tiers.isNotEmpty()) tiers else getDefaultTiers(normalizedType)
                )
            } catch (e: Exception) {
                // Fall back to default on parse error
            }
        }

        return getDefaultTariff(normalizedType)
    }

    /**
     * Saves cloud-synced tariffs into local cache.
     */
    fun saveCloudTariffs(tariffsJsonArray: JSONArray) {
        val editor = prefs.edit()
        for (i in 0 until tariffsJsonArray.length()) {
            val t = tariffsJsonArray.getJSONObject(i)
            val type = t.optString("vehicleTypeId", "").uppercase()
            if (type.isNotBlank()) {
                editor.putString("tariff_$type", t.toString())
            }
        }
        editor.apply()
    }

    private fun getDefaultTariff(type: String): TariffConfig {
        val isBike = type == "MOTORCYCLE"
        val isCar = type == "CAR"
        val isVan = type == "VAN"
        val isBus = type == "BUS"
        val isTruck = type == "TRUCK"

        val label = when {
            isBike -> "Motorcycle"
            isCar -> "Car / Taxi"
            isVan -> "Van / Microbus"
            isBus -> "Bus"
            isTruck -> "Truck"
            else -> type
        }

        val minCharge = when {
            isBike -> BigDecimal("25.00")
            isCar -> BigDecimal("40.00")
            isVan -> BigDecimal("60.00")
            isBus -> BigDecimal("100.00")
            else -> BigDecimal("150.00")
        }

        val maxDaily = when {
            isBike -> BigDecimal("200.00")
            isCar -> BigDecimal("500.00")
            isVan -> BigDecimal("700.00")
            isBus -> BigDecimal("1200.00")
            else -> BigDecimal("1500.00")
        }

        val lostFee = when {
            isBike -> BigDecimal("300.00")
            isCar -> BigDecimal("500.00")
            isVan -> BigDecimal("600.00")
            else -> BigDecimal("800.00")
        }

        return TariffConfig(
            id = "${type.lowercase()}_tariff",
            name = "$label Tariff",
            vehicleTypeId = type,
            gracePeriodMinutes = 10,
            minCharge = minCharge,
            maxDailyCharge = maxDaily,
            lostTicketFee = lostFee,
            tiers = getDefaultTiers(type)
        )
    }

    private fun getDefaultTiers(type: String): List<TariffTier> {
        val isBike = type == "MOTORCYCLE"
        val isCar = type == "CAR"

        val r1 = if (isBike) BigDecimal("25.00") else if (isCar) BigDecimal("50.00") else BigDecimal("70.00")
        val r2 = if (isBike) BigDecimal("40.00") else if (isCar) BigDecimal("80.00") else BigDecimal("120.00")
        val rSub = if (isBike) BigDecimal("15.00") else if (isCar) BigDecimal("30.00") else BigDecimal("40.00")

        return listOf(
            TariffTier(0, 60, r1, isFixed = true),
            TariffTier(60, 120, r2, isFixed = true),
            TariffTier(120, null, rSub, isFixed = false)
        )
    }
}

