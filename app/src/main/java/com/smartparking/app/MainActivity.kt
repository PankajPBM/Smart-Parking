package com.smartparking.app

import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smartparking.app.hardware.capability.AppMetadata
import com.smartparking.app.hardware.capability.DeviceCapabilities
import com.smartparking.app.hardware.printer.UniversalHardwareService
import com.smartparking.app.hardware.sunmi.SunmiScannerManager
import com.smartparking.app.hardware.sunmi.SunmiScannerReceiver
import com.smartparking.app.license.PbLicenseService
import com.smartparking.app.ui.screens.dashboard.DashboardScreen
import com.smartparking.app.ui.screens.entry.VehicleEntryScreen
import com.smartparking.app.ui.screens.exit.VehicleExitScreen
import com.smartparking.app.ui.screens.info.DeviceAndAppInfoScreen
import com.smartparking.app.ui.screens.license.LicenseScreen
import com.smartparking.app.ui.screens.membership.MembershipScreen
import com.smartparking.app.ui.screens.reports.ReportsScreen
import com.smartparking.app.ui.theme.SmartParkingTheme
import com.smartparking.app.sync.ParkingSyncService
import com.smartparking.app.version.AppVersionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var hardwareService: UniversalHardwareService
    private lateinit var licenseService: PbLicenseService
    private lateinit var scannerManager: SunmiScannerManager
    private val scannerReceiver = SunmiScannerReceiver()
    private var isScannerRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 0. Anti-Tamper & Security Audit (Verifies authentic PB certificate, detects hooks/debuggers)
        com.smartparking.app.security.SecurityGuard.verifyAppIntegrity(this, enforceHardExit = true)

        hardwareService = UniversalHardwareService.getInstance(this)
        hardwareService.bind(this)
        licenseService = PbLicenseService.getInstance(this)
        scannerManager = SunmiScannerManager(this)

        // Ensure scanner sounds & feedback are active on SUNMI terminal
        scannerManager.configureScannerSettings(enableBeep = true, enableVibrate = true, enableLight = true)

        // Register dynamic broadcast receiver for hardware scanner
        registerScannerReceiver()

        // Asynchronously check heartbeat, sync with cloud, and verify terminal version
        lifecycleScope.launch(Dispatchers.IO) {
            licenseService.sendHeartbeat()
            ParkingSyncService.getInstance(this@MainActivity).syncWithCloud()
            val updateResult = AppVersionManager.getInstance(this@MainActivity).checkForUpdates()
            if (updateResult != null && updateResult.hasUpdate) {
                withContext(Dispatchers.Main) {
                    AppVersionManager.getInstance(this@MainActivity).showUpdateDialog(this@MainActivity, updateResult)
                }
            }
        }

        val capabilities = hardwareService.capabilities
        val appMetadata = AppMetadata()

        setContent {
            SmartParkingTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmartParkingAppNavigation(
                        capabilities = capabilities,
                        appMetadata = appMetadata,
                        onGenerateTicket = { plateNumber, vehicleType, zone, _ ->
                            val timestamp = SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.getDefault()).format(Date())
                            val ticketNo = "SP-" + System.currentTimeMillis().toString().takeLast(8)

                            hardwareService.autoPrintEntryTicket(
                                siteName = "Smart Parking Terminal",
                                ticketNo = ticketNo,
                                vehicleNo = plateNumber,
                                vehicleType = vehicleType,
                                entryTime = timestamp,
                                zoneName = zone,
                                operatorName = "Gate Operator",
                                rateInfo = "Standard Rate"
                            ) {
                                runOnUiThread {
                                    Toast.makeText(this@MainActivity, "Entry Ticket Printed: $ticketNo", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onProcessExit = { ticketNumber, paymentMethod ->
                            val timestamp = SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.getDefault()).format(Date())
                            val receiptNo = "RCP-" + System.currentTimeMillis().toString().takeLast(8)

                            hardwareService.autoPrintExitReceipt(
                                siteName = "Smart Parking Terminal",
                                receiptNo = receiptNo,
                                ticketNo = ticketNumber,
                                vehicleNo = "BA 12 PA 1234",
                                vehicleType = "CAR",
                                entryTime = "09:42 AM",
                                exitTime = timestamp,
                                duration = "2h 18m",
                                parkingFee = "120.00",
                                discount = "0.00",
                                totalPaid = "120.00",
                                paymentMethod = paymentMethod,
                                operatorName = "Exit Cashier"
                            ) {
                                runOnUiThread {
                                    Toast.makeText(this@MainActivity, "Exit Complete & Receipt Printed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onTestPrinter = {
                            val timestamp = SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.getDefault()).format(Date())
                            hardwareService.autoPrintEntryTicket(
                                siteName = "HARDWARE TEST TERMINAL",
                                ticketNo = "TEST-0001",
                                vehicleNo = "TEST-PLATE",
                                vehicleType = "DIAGNOSTIC",
                                entryTime = timestamp,
                                zoneName = "TEST",
                                operatorName = "System Test",
                                rateInfo = "Model: ${capabilities.manufacturer} ${capabilities.model}"
                            ) {
                                runOnUiThread {
                                    Toast.makeText(this@MainActivity, "Test Print Sent to ${capabilities.model}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun registerScannerReceiver() {
        if (!isScannerRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(SunmiScannerReceiver.ACTION_DATA_CODE_RECEIVED)
                    addAction(SunmiScannerReceiver.ACTION_SUNMI_SCAN_RESULT)
                    addAction(SunmiScannerReceiver.ACTION_GENERIC_SCAN_RESULT)
                    addAction(SunmiScannerReceiver.ACTION_SUNMI_ALT)
                }
                ContextCompat.registerReceiver(
                    this,
                    scannerReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                isScannerRegistered = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun unregisterScannerReceiver() {
        if (isScannerRegistered) {
            try {
                unregisterReceiver(scannerReceiver)
                isScannerRegistered = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-verify integrity on resume (detects debugger attachment or Frida hooks injected during pause)
        com.smartparking.app.security.SecurityGuard.verifyAppIntegrity(this, enforceHardExit = true)
        if (::hardwareService.isInitialized) {
            hardwareService.bind(this)
        }
        registerScannerReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterScannerReceiver()
    }
}

@Composable
fun SmartParkingAppNavigation(
    capabilities: DeviceCapabilities,
    appMetadata: AppMetadata,
    onGenerateTicket: (plateNumber: String, vehicleType: String, zone: String, customerPhone: String) -> Unit,
    onProcessExit: (ticketNumber: String, paymentMethod: String) -> Unit,
    onTestPrinter: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        composable("dashboard") {
            DashboardScreen(
                navController = navController,
                capabilities = capabilities
            )
        }
        composable("entry") {
            VehicleEntryScreen(
                navController = navController,
                onGenerateTicket = onGenerateTicket
            )
        }
        composable("exit") {
            VehicleExitScreen(
                navController = navController,
                initialTicketNumber = "",
                onProcessExit = onProcessExit
            )
        }
        composable(
            route = "exit?ticketNo={ticketNo}",
            arguments = listOf(
                navArgument("ticketNo") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val ticketNo = backStackEntry.arguments?.getString("ticketNo") ?: ""
            VehicleExitScreen(
                navController = navController,
                initialTicketNumber = ticketNo,
                onProcessExit = onProcessExit
            )
        }
        composable("membership") {
            MembershipScreen(
                navController = navController
            )
        }
        composable("license") {
            LicenseScreen(
                navController = navController
            )
        }
        composable("reports") {
            ReportsScreen(
                navController = navController
            )
        }
        composable("device_info") {
            DeviceAndAppInfoScreen(
                navController = navController,
                capabilities = capabilities,
                appMetadata = appMetadata,
                onTestPrinter = onTestPrinter
            )
        }
    }
}
