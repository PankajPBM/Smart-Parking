package com.smartparking.app

import android.app.Application
import com.smartparking.app.security.SecurityGuard

/**
 * Smart Parking Application Entry Point
 */
class SmartParkingApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Immediate Anti-Tamper & Security Audit before any components initialize
        SecurityGuard.verifyAppIntegrity(this, enforceHardExit = true)
    }
}
