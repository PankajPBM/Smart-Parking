@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartparking.app.ui.screens.license

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartparking.app.license.MachineFingerprint
import com.smartparking.app.license.PbLicenseService
import com.smartparking.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val licenseService = remember { PbLicenseService.getInstance(context) }

    var licenseState by remember { mutableStateOf(licenseService.getLicenseState()) }
    var inputKey by remember { mutableStateOf("") }
    var isActivating by remember { mutableStateOf(false) }
    var activationFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val formattedMachineCode = remember { MachineFingerprint.getFormattedMachineCode(context) }
    val fullHardwareId = remember { MachineFingerprint.getHardwareId(context) }

    fun refreshState() {
        licenseState = licenseService.getLicenseState()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("License & Activation", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshState() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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

            // 1. Current License Status Card
            LicenseStatusCard(licenseState = licenseState)

            // 2. Machine Hardware Fingerprint (Hardware Locked ID)
            MachineIdCard(
                formattedCode = formattedMachineCode,
                fullHash = fullHardwareId,
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Machine Hardware ID", fullHardwareId)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Hardware ID copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            )

            // 3. Online Activation Box
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Activate Pro License",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Enter the license key issued by PB Systems to activate full perpetual or subscription access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = inputKey,
                        onValueChange = { inputKey = it.uppercase() },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. SP-2026-XXXX-XXXX") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(Icons.Default.VpnKey, contentDescription = "Key", tint = PrimaryBlue)
                        }
                    )

                    activationFeedback?.let { (success, msg) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (success) SuccessGreen.copy(alpha = 0.12f) else ErrorRed.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (success) SuccessGreen else ErrorRed
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (inputKey.isNotBlank()) {
                                isActivating = true
                                activationFeedback = null
                                coroutineScope.launch {
                                    val res = licenseService.activateOnline(inputKey)
                                    isActivating = false
                                    activationFeedback = Pair(res.success, res.message)
                                    if (res.success) {
                                        refreshState()
                                        inputKey = ""
                                    }
                                }
                            }
                        },
                        enabled = inputKey.isNotBlank() && !isActivating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        if (isActivating) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CloudDone, contentDescription = null)
                                Text("ACTIVATE ONLINE NOW", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 4. Contact / Purchase / Support Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Need a License or Support?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Contact PB Tech Zone for official licensing keys, renewals, or device migrations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+9779814891277"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Call")
                        }

                        Button(
                            onClick = {
                                val url = "https://wa.me/9779814891277?text=Hi%2C%20I%20need%20a%20license%20for%20Smart%20Parking.%20Hardware%20ID%3A%20$formattedMachineCode"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                        ) {
                            Text("WhatsApp", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun LicenseStatusCard(licenseState: PbLicenseService.LicenseState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (licenseState) {
                is PbLicenseService.LicenseState.ProActive -> SuccessGreen.copy(alpha = 0.12f)
                is PbLicenseService.LicenseState.TrialActive -> PrimaryBlue.copy(alpha = 0.12f)
                is PbLicenseService.LicenseState.Expired -> ErrorRed.copy(alpha = 0.12f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = when (licenseState) {
                            is PbLicenseService.LicenseState.ProActive -> "👑 PRO LICENSE ACTIVE"
                            is PbLicenseService.LicenseState.TrialActive -> "⏳ 1-YEAR FREE TRIAL"
                            is PbLicenseService.LicenseState.Expired -> "🔴 LICENSE EXPIRED"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (licenseState) {
                            is PbLicenseService.LicenseState.ProActive -> SuccessGreen
                            is PbLicenseService.LicenseState.TrialActive -> PrimaryBlue
                            is PbLicenseService.LicenseState.Expired -> ErrorRed
                        }
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when (licenseState) {
                        is PbLicenseService.LicenseState.ProActive -> SuccessGreen
                        is PbLicenseService.LicenseState.TrialActive -> PrimaryBlue
                        is PbLicenseService.LicenseState.Expired -> ErrorRed
                    }
                ) {
                    Text(
                        text = when (licenseState) {
                            is PbLicenseService.LicenseState.ProActive -> "ACTIVE"
                            is PbLicenseService.LicenseState.TrialActive -> "${(licenseState as PbLicenseService.LicenseState.TrialActive).daysRemaining} DAYS"
                            is PbLicenseService.LicenseState.Expired -> "EXPIRED"
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            when (licenseState) {
                is PbLicenseService.LicenseState.ProActive -> {
                    Text(
                        text = "Customer: ${licenseState.customerName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Type: ${licenseState.licenseType} | Key: ${licenseState.licenseKey}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Valid Until: ${licenseState.expiryDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreen,
                        fontWeight = FontWeight.Bold
                    )
                }

                is PbLicenseService.LicenseState.TrialActive -> {
                    Text(
                        text = "Your 1-Year Trial started on ${licenseState.startDate} and is valid until ${licenseState.expiryDate}.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    val progress = (licenseState.daysRemaining.toFloat() / licenseState.totalDays.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = PrimaryBlue,
                        trackColor = PrimaryBlue.copy(alpha = 0.2f)
                    )

                    Text(
                        text = "🔒 Guaranteed Trial: Uninstalling or reinstalling this app preserves your exact trial end date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is PbLicenseService.LicenseState.Expired -> {
                    Text(
                        text = licenseState.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErrorRed
                    )
                    Text(
                        text = "Expired on: ${licenseState.expiryDate}. Please enter a valid Pro license key to resume parking operations.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun MachineIdCard(
    formattedCode: String,
    fullHash: String,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hardware Machine ID",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy ID")
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = formattedCode,
                    modifier = Modifier.padding(14.dp),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "This hardware ID uniquely locks your license and 1-year trial to this physical device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
