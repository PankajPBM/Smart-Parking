package com.smartparking.app.di

import android.content.Context
import com.smartparking.app.data.local.SmartParkingDatabase
import com.smartparking.app.data.local.dao.*
import com.smartparking.app.hardware.sunmi.SunmiHardwareManager
import com.smartparking.app.hardware.sunmi.SunmiScannerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt Dependency Injection Module
 * Provides singleton instances of database, DAOs, and hardware managers
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SmartParkingDatabase {
        return SmartParkingDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideParkingSessionDao(database: SmartParkingDatabase): ParkingSessionDao {
        return database.parkingSessionDao()
    }

    @Provides
    @Singleton
    fun provideTariffDao(database: SmartParkingDatabase): TariffDao {
        return database.tariffDao()
    }

    @Provides
    @Singleton
    fun provideSiteDao(database: SmartParkingDatabase): SiteDao {
        return database.siteDao()
    }

    @Provides
    @Singleton
    fun provideUserDao(database: SmartParkingDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    @Singleton
    fun provideSyncQueueDao(database: SmartParkingDatabase): SyncQueueDao {
        return database.syncQueueDao()
    }

    @Provides
    @Singleton
    fun provideAuditLogDao(database: SmartParkingDatabase): AuditLogDao {
        return database.auditLogDao()
    }

    @Provides
    @Singleton
    fun provideSunmiHardwareManager(@ApplicationContext context: Context): SunmiHardwareManager {
        return SunmiHardwareManager(context)
    }

    @Provides
    @Singleton
    fun provideSunmiScannerManager(@ApplicationContext context: Context): SunmiScannerManager {
        return SunmiScannerManager(context)
    }
}
