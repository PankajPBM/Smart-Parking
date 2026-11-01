package com.smartparking.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Smart Parking Application Entry Point
 * Initializes Hilt Dependency Injection
 */
@HiltAndroidApp
class SmartParkingApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Application initialization handled by Hilt
    }
}
