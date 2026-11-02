package com.smartparking.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smartparking.app.data.local.dao.*
import com.smartparking.app.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Database(
    entities = [
        SiteEntity::class,
        ZoneEntity::class,
        VehicleTypeEntity::class,
        TariffEntity::class,
        TariffTierEntity::class,
        DeviceEntity::class,
        UserEntity::class,
        ParkingSessionEntity::class,
        AuditLogEntity::class,
        SyncQueueEntity::class,
        MembershipEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SmartParkingDatabase : RoomDatabase() {

    abstract fun parkingSessionDao(): ParkingSessionDao
    abstract fun tariffDao(): TariffDao
    abstract fun siteDao(): SiteDao
    abstract fun userDao(): UserDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun membershipDao(): MembershipDao

    companion object {
        @Volatile
        private var INSTANCE: SmartParkingDatabase? = null

        fun getInstance(context: Context): SmartParkingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmartParkingDatabase::class.java,
                    "smart_parking.db"
                )
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Seed initial default data for instant POS out-of-the-box readiness
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        seedDefaultData(database)
                    }
                }
            }

            private suspend fun seedDefaultData(db: SmartParkingDatabase) {
                val defaultSiteId = "site_default_01"

                // 1. Default Site
                db.siteDao().insertSite(
                    SiteEntity(
                        id = defaultSiteId,
                        code = "SP-SITE-01",
                        name = "Smart Parking Terminal",
                        address = "Main Boulevard, Kathmandu",
                        currency = "NPR",
                        totalCapacity = 200
                    )
                )

                // 2. Default Zones
                db.siteDao().insertZones(
                    listOf(
                        ZoneEntity(UUID.randomUUID().toString(), defaultSiteId, "Ground Floor - A", "GF-A", 80),
                        ZoneEntity(UUID.randomUUID().toString(), defaultSiteId, "Basement 1 - B", "B1-B", 70),
                        ZoneEntity(UUID.randomUUID().toString(), defaultSiteId, "Outdoor VIP", "VIP", 50)
                    )
                )

                // 3. Default Tariffs (Car and Bike)
                val carTariffId = "tariff_car_default"
                db.tariffDao().insertTariff(
                    TariffEntity(
                        id = carTariffId,
                        siteId = defaultSiteId,
                        name = "Standard Car Tariff",
                        vehicleTypeId = "CAR",
                        gracePeriodMins = 10,
                        minCharge = "50.00",
                        maxDailyCharge = "500.00",
                        lostTicketFee = "300.00",
                        isDefault = true
                    )
                )

                db.tariffDao().insertTariffTiers(
                    listOf(
                        TariffTierEntity(UUID.randomUUID().toString(), carTariffId, 0, 60, "50.00", true),
                        TariffTierEntity(UUID.randomUUID().toString(), carTariffId, 60, 120, "80.00", true),
                        TariffTierEntity(UUID.randomUUID().toString(), carTariffId, 120, 240, "150.00", true),
                        TariffTierEntity(UUID.randomUUID().toString(), carTariffId, 240, null, "30.00", false) // Per additional hour
                    )
                )

                val bikeTariffId = "tariff_bike_default"
                db.tariffDao().insertTariff(
                    TariffEntity(
                        id = bikeTariffId,
                        siteId = defaultSiteId,
                        name = "Standard Motorcycle Tariff",
                        vehicleTypeId = "MOTORCYCLE",
                        gracePeriodMins = 10,
                        minCharge = "25.00",
                        maxDailyCharge = "200.00",
                        lostTicketFee = "150.00",
                        isDefault = false
                    )
                )

                db.tariffDao().insertTariffTiers(
                    listOf(
                        TariffTierEntity(UUID.randomUUID().toString(), bikeTariffId, 0, 60, "25.00", true),
                        TariffTierEntity(UUID.randomUUID().toString(), bikeTariffId, 60, 120, "40.00", true),
                        TariffTierEntity(UUID.randomUUID().toString(), bikeTariffId, 120, null, "15.00", false)
                    )
                )

                // 4. Default Admin & Operator Users (Default PIN: 1234, Admin PIN: 0000)
                db.userDao().insertUser(
                    UserEntity(
                        id = "user_admin_01",
                        siteId = defaultSiteId,
                        username = "admin",
                        fullName = "System Administrator",
                        pinHash = "0000", // Fast PIN for demo
                        role = "SUPER_ADMIN"
                    )
                )

                db.userDao().insertUser(
                    UserEntity(
                        id = "user_op_01",
                        siteId = defaultSiteId,
                        username = "operator1",
                        fullName = "Main Gate Operator",
                        pinHash = "1234",
                        role = "OPERATOR"
                    )
                )

                // 5. Default VIP Member Pass (for out-of-the-box testing)
                val oneYearFromNow = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000L)
                db.membershipDao().insertMembership(
                    MembershipEntity(
                        id = UUID.randomUUID().toString(),
                        memberNumber = "MEM-2026-0001",
                        fullName = "Pankaj Sharma",
                        phone = "9814891277",
                        vehicleNumber = "BA 12 PA 1234",
                        vehicleTypeId = "CAR",
                        planType = "VIP_PASS",
                        startDate = System.currentTimeMillis(),
                        endDate = oneYearFromNow,
                        discountPercent = 100,
                        feePaid = "12000.00",
                        paymentMethod = "QR_ONLINE",
                        notes = "Executive VIP Pass - PB Tech Zone"
                    )
                )
            }
        }
    }
}
