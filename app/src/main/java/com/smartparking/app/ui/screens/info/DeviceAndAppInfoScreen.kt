package com.smartparking.app.ui.screens.info

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartparking.app.hardware.capability.AppMetadata
import com.smartparking.app.hardware.capability.DeviceCapabilities
import com.smartparking.app.ui.theme.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceAndAppInfoScreen(
    navController: NavController,
    capabilities: DeviceCapabilities,
    appMetadata: AppMetadata = AppMetadata(),
    onTestPrinter: () -> Unit = {}
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device & App Information", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Connected Device Header Banner
            ConnectedDeviceCard(capabilities = capabilities)

            // 2. Hardware Capabilities & Peripherals
            HardwarePeripheralsCard(capabilities = capabilities)

            // 3. Application & Publisher Information (Interactive Links)
            PublisherInfoCard(
                appMetadata = appMetadata,
                onOpenWebsite = { openBrowser(context, appMetadata.websiteUrl) },
                onDialPhone = { dialPhoneNumber(context, appMetadata.supportPhone) },
                onOpenWhatsApp = { openWhatsApp(context, appMetadata.whatsappNumber) }
            )

            // 4. Manage License Button
            Button(
                onClick = { navController.navigate("license") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberWarning)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🔑", fontSize = 20.sp)
                    Text("MANAGE LICENSE & TRIAL", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }

            // 5. Test Hardware Button (Formatted AIDL Test)
            Button(
                onClick = onTestPrinter,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🖨️", fontSize = 18.sp)
                    Text("TEST HARDWARE PRINT (AIDL)", style = MaterialTheme.typography.labelLarge)
                }
            }

            // 6. Direct Firmware Diagnostics & ESC/POS Fallback
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        com.smartparking.app.hardware.printer.UniversalHardwareService.getInstance(context).printSelfCheck {
                            (context as? android.app.Activity)?.runOnUiThread {
                                Toast.makeText(context, "Firmware Self-Check Pattern Sent", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("⚙️ Self-Check", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = {
                        com.smartparking.app.hardware.printer.UniversalHardwareService.getInstance(context).printEscPosSample {
                            (context as? android.app.Activity)?.runOnUiThread {
                                Toast.makeText(context, "Direct ESC/POS Sent to MCU", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("⚡ Raw ESC/POS", style = MaterialTheme.typography.labelMedium)
                }
            }

            // 7. Remote Version Check & OTA Update Trigger
            OutlinedButton(
                onClick = {
                    (context as? androidx.activity.ComponentActivity)?.let { act ->
                        act.lifecycleScope.launch(Dispatchers.IO) {
                            val result = com.smartparking.app.version.AppVersionManager.getInstance(act).checkForUpdates()
                            withContext(Dispatchers.Main) {
                                if (result != null && result.hasUpdate) {
                                    com.smartparking.app.version.AppVersionManager.getInstance(act).showUpdateDialog(act, result)
                                } else {
                                    Toast.makeText(act, "App is up to date (v${com.smartparking.app.BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("🔄 Check for Remote Updates", style = MaterialTheme.typography.labelMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun ConnectedDeviceCard(capabilities: DeviceCapabilities) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CONNECTED POS TERMINAL",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (capabilities.isSunmiDevice) SuccessGreen.copy(alpha = 0.15f) else WarningOrange.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (capabilities.isSunmiDevice) "● SUNMI HARDWARE" else "● GENERIC ANDROID",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (capabilities.isSunmiDevice) SuccessGreen else WarningOrange
                    )
                }
            }

            HorizontalDivider()

            InfoRow(label = "Device Model", value = "${capabilities.manufacturer} ${capabilities.model}")
            InfoRow(label = "Serial Number (SN)", value = capabilities.serialNumber, isHighlight = true)
            InfoRow(label = "Android Version", value = capabilities.androidVersion)
            capabilities.sunmiOsVersion?.let {
                InfoRow(label = "SUNMI OS Version", value = it)
            }
            InfoRow(label = "Thermal Paper Spec", value = "${capabilities.paperWidth.mm}mm (${capabilities.paperWidth.dots} dots / ${capabilities.maxColumns} cols)")
        }
    }
}

@Composable
fun HardwarePeripheralsCard(capabilities: DeviceCapabilities) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "PERIPHERAL STATUS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            StatusIndicatorRow(
                icon = "🧾",
                label = "Integrated Thermal Printer",
                isAvailable = capabilities.hasSunmiPrinterService || capabilities.isSunmiDevice,
                detail = if (capabilities.hasSunmiPrinterService) "AIDL Service Connected" else "Not detected"
            )

            StatusIndicatorRow(
                icon = "▣",
                label = "Hardware Laser Scanner",
                isAvailable = capabilities.hasHardwareScanner,
                detail = if (capabilities.hasHardwareScanner) "Ready for Barcode/QR" else "Camera Scanner Fallback"
            )

            StatusIndicatorRow(
                icon = "📷",
                label = "Digital Camera",
                isAvailable = capabilities.hasCamera,
                detail = if (capabilities.hasCamera) "Available" else "Not available"
            )

            StatusIndicatorRow(
                icon = "📶",
                label = "NFC Reader",
                isAvailable = capabilities.hasNfc,
                detail = if (capabilities.hasNfc) "Supported" else "Not present"
            )
        }
    }
}

@Composable
fun PublisherInfoCard(
    appMetadata: AppMetadata,
    onOpenWebsite: () -> Unit,
    onDialPhone: () -> Unit,
    onOpenWhatsApp: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "APP & PUBLISHER DETAILS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            InfoRow(label = "Application Name", value = appMetadata.appName)
            InfoRow(label = "Version", value = "v${appMetadata.versionName} (Build ${appMetadata.versionCode})")

            // Interactive Publisher Row (Clickable -> Opens https://prakash77.com.np)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenWebsite() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Publisher",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = appMetadata.publisher,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        textDecoration = TextDecoration.Underline
                    )
                    Text("🔗", fontSize = 14.sp)
                }
            }

            // Interactive Website Row (Clickable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenWebsite() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Website",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = appMetadata.websiteUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = PrimaryBlue,
                    textDecoration = TextDecoration.Underline
                )
            }

            // Interactive Contact Phone / WhatsApp Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Direct Developer & Support Contact:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Call Phone Button
                    Button(
                        onClick = onDialPhone,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Call", modifier = Modifier.size(16.dp))
                            Text(
                                text = appMetadata.supportPhoneFormatted,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // WhatsApp Button
                    Button(
                        onClick = onOpenWhatsApp,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("💬", fontSize = 16.sp)
                            Text(
                                text = "WhatsApp",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            InfoRow(label = "Platform", value = appMetadata.targetPlatform)
            InfoRow(label = "Release Period", value = appMetadata.releaseDate)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, isHighlight: Boolean = false, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold || isHighlight) FontWeight.Bold else FontWeight.Medium,
            color = if (isHighlight) PrimaryBlue else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun StatusIndicatorRow(icon: String, label: String, isAvailable: Boolean, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 20.sp)
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isAvailable) SuccessGreen.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isAvailable) "ONLINE" else "OFFLINE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isAvailable) SuccessGreen else Color.Gray
            )
        }
    }
}

// Intent Launch Helpers
private fun openBrowser(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open website", Toast.LENGTH_SHORT).show()
    }
}

private fun dialPhoneNumber(context: Context, phoneNumber: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open phone dialer", Toast.LENGTH_SHORT).show()
    }
}

private fun openWhatsApp(context: Context, whatsappNumber: String) {
    try {
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$whatsappNumber&text=Hello%20PB%20Tech%20Zone,%20I%20have%20an%20inquiry%20regarding%20Smart%20Parking%20POS%20App.")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not launch WhatsApp", Toast.LENGTH_SHORT).show()
    }
}
