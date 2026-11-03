@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartparking.app.ui.screens.dashboard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartparking.app.data.local.SmartParkingDatabase
import com.smartparking.app.data.local.entity.ParkingSessionEntity
import com.smartparking.app.hardware.capability.DeviceCapabilities
import com.smartparking.app.hardware.sunmi.SunmiScannerReceiver
import com.smartparking.app.license.PbLicenseService
import com.smartparking.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    navController: NavController,
    capabilities: DeviceCapabilities? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { SmartParkingDatabase.getInstance(context) }
    val licenseService = remember { PbLicenseService.getInstance(context) }

    var licenseState by remember { mutableStateOf(licenseService.getLicenseState()) }
    var isSyncing by remember { mutableStateOf(false) }

    // Start of current day timestamp for shift metrics
    val startOfDay = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // Live Room DB flows
    val parkedCount by database.parkingSessionDao().getCurrentlyParkedCountFlow().collectAsState(initial = 0)
    val todayEntries by database.parkingSessionDao().getTodayEntriesCountFlow(startOfDay).collectAsState(initial = 0)
    val todayExits by database.parkingSessionDao().getTodayExitsCountFlow(startOfDay).collectAsState(initial = 0)
    val liveParkedSessions by database.parkingSessionDao().getParkedSessionsFlow().collectAsState(initial = emptyList())

    val totalCapacity = 200
    val availableSpots = (totalCapacity - parkedCount).coerceAtLeast(0)

    // Background sync on launch
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            com.smartparking.app.sync.ParkingSyncService.getInstance(context).syncWithCloud()
        }
    }

    // Auto-navigate to exit when barcode/QR is scanned anywhere from the dashboard
    LaunchedEffect(Unit) {
        SunmiScannerReceiver.scanFlow.collect { result ->
            if (result.data.isNotBlank()) {
                withContext(Dispatchers.Main) {
                    navController.navigate("exit?ticketNo=${result.data}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            DashboardTopBar(
                capabilities = capabilities,
                licenseState = licenseState,
                isSyncing = isSyncing,
                onLicenseClick = { navController.navigate("license") },
                onMembershipClick = { navController.navigate("membership") },
                onReportsClick = { navController.navigate("reports") },
                onInfoClick = { navController.navigate("device_info") },
                onSyncClick = {
                    if (!isSyncing) {
                        isSyncing = true
                        coroutineScope.launch {
                            val res = withContext(Dispatchers.IO) {
                                com.smartparking.app.sync.ParkingSyncService.getInstance(context).syncWithCloud()
                            }
                            isSyncing = false
                            licenseState = licenseService.getLicenseState()
                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. PRIMARY OPERATIONAL ACTIONS (Vehicle Entry & Exit)
            item {
                HeroActionsSection(
                    onEntryClick = { navController.navigate("entry") },
                    onExitClick = { navController.navigate("exit") }
                )
            }

            // 2. PARKING OCCUPANCY & CAPACITY CARD
            item {
                OccupancyCard(
                    total = totalCapacity,
                    occupied = parkedCount,
                    available = availableSpots
                )
            }

            // 3. TODAY'S SHIFT METRICS (3 Non-duplicate clean summary cards)
            item {
                ShiftMetricsRow(
                    parkedCount = parkedCount,
                    todayEntries = todayEntries,
                    todayExits = todayExits
                )
            }

            // 4. CURRENTLY PARKED VEHICLES HEADER
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Currently Parked Vehicles (${liveParkedSessions.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap any vehicle to quickly bill and exit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(onClick = { navController.navigate("reports") }) {
                        Text("All Logs", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 5. PARKED VEHICLES LIST
            if (liveParkedSessions.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "🅿️", fontSize = 36.sp)
                            Text(
                                text = "Parking Lot is Empty",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap \"Vehicle Entry\" to record incoming vehicle and print ticket.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(liveParkedSessions.take(15), key = { it.id }) { session ->
                    LiveParkedVehicleItem(
                        session = session,
                        onExitClick = {
                            navController.navigate("exit?ticketNo=${session.ticketNumber}")
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun DashboardTopBar(
    capabilities: DeviceCapabilities?,
    licenseState: PbLicenseService.LicenseState,
    isSyncing: Boolean,
    onLicenseClick: () -> Unit,
    onMembershipClick: () -> Unit,
    onReportsClick: () -> Unit,
    onInfoClick: () -> Unit,
    onSyncClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Smart Parking",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(SuccessGreen, CircleShape)
                    )
                }
                Text(
                    text = capabilities?.let { "${it.manufacturer} ${it.model} • SN: ${it.serialNumber}" } ?: "SUNMI POS Terminal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            // License Status Pill
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onLicenseClick() },
                color = when (licenseState) {
                    is PbLicenseService.LicenseState.ProActive -> SuccessGreen.copy(alpha = 0.15f)
                    is PbLicenseService.LicenseState.TrialActive -> PrimaryBlue.copy(alpha = 0.15f)
                    is PbLicenseService.LicenseState.Expired -> ErrorRed.copy(alpha = 0.15f)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = when (licenseState) {
                            is PbLicenseService.LicenseState.ProActive -> "👑 PRO"
                            is PbLicenseService.LicenseState.TrialActive -> "⏳ ${(licenseState as? PbLicenseService.LicenseState.TrialActive)?.daysRemaining ?: 7}d Trial"
                            is PbLicenseService.LicenseState.Expired -> "🔴 Expired"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (licenseState) {
                            is PbLicenseService.LicenseState.ProActive -> SuccessGreen
                            is PbLicenseService.LicenseState.TrialActive -> PrimaryBlue
                            is PbLicenseService.LicenseState.Expired -> ErrorRed
                        }
                    )
                }
            }

            // All Secondary Features Arranged Under Single Menu Icon (No Dashboard Clutter or Duplication)
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.width(230.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("VIP Memberships", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Text("👑", fontSize = 18.sp) },
                        onClick = {
                            menuExpanded = false
                            onMembershipClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Reports & History", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Text("📊", fontSize = 18.sp) },
                        onClick = {
                            menuExpanded = false
                            onReportsClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("PB License & Trial", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Text("🔑", fontSize = 18.sp) },
                        onClick = {
                            menuExpanded = false
                            onLicenseClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Terminal Diagnostics", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Text("⚙️", fontSize = 18.sp) },
                        onClick = {
                            menuExpanded = false
                            onInfoClick()
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Sync with Cloud", fontWeight = FontWeight.SemiBold)
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                }
                            }
                        },
                        leadingIcon = { Text("🔄", fontSize = 18.sp) },
                        onClick = {
                            menuExpanded = false
                            onSyncClick()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun HeroActionsSection(
    onEntryClick: () -> Unit,
    onExitClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // VEHICLE ENTRY HERO CARD
        Card(
            modifier = Modifier
                .weight(1f)
                .height(112.dp)
                .clickable { onEntryClick() },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = PrimaryBlue),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(text = "🚗", fontSize = 28.sp)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "NEW IN",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Column {
                    Text(
                        text = "Vehicle Entry",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Print Ticket",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // VEHICLE EXIT HERO CARD
        Card(
            modifier = Modifier
                .weight(1f)
                .height(112.dp)
                .clickable { onExitClick() },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SuccessGreen),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(text = "🚪", fontSize = 28.sp)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "OUT & PAY",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Column {
                    Text(
                        text = "Vehicle Exit",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Scan QR & Bill",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
fun OccupancyCard(
    total: Int,
    occupied: Int,
    available: Int
) {
    val percentage = if (total > 0) (occupied.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val isNearCapacity = percentage >= 0.85f

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Parking Availability",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$available",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isNearCapacity) AmberWarning else SuccessGreen
                        )
                        Text(
                            text = "free spots / $total total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isNearCapacity) ErrorRed.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${(percentage * 100).toInt()}% OCCUPIED",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isNearCapacity) ErrorRed else SuccessGreen
                    )
                }
            }

            LinearProgressIndicator(
                progress = { percentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (isNearCapacity) ErrorRed else PrimaryBlue,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Occupied: $occupied spots",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Available: $available spots",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen
                )
            }
        }
    }
}

@Composable
fun ShiftMetricsRow(
    parkedCount: Int,
    todayEntries: Int,
    todayExits: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ShiftMetricTile(
            modifier = Modifier.weight(1f),
            title = "Parked Now",
            value = "$parkedCount",
            icon = "🚗",
            color = StatusParked
        )
        ShiftMetricTile(
            modifier = Modifier.weight(1f),
            title = "Today In",
            value = "$todayEntries",
            icon = "📥",
            color = PrimaryBlue
        )
        ShiftMetricTile(
            modifier = Modifier.weight(1f),
            title = "Today Out",
            value = "$todayExits",
            icon = "📤",
            color = SuccessGreen
        )
    }
}

@Composable
fun ShiftMetricTile(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = icon, fontSize = 16.sp)
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun LiveParkedVehicleItem(
    session: ParkingSessionEntity,
    onExitClick: () -> Unit
) {
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val elapsedMinutes = ((System.currentTimeMillis() - session.entryTime) / 60000).coerceAtLeast(0)
    val elapsedFormatted = if (elapsedMinutes < 60) "${elapsedMinutes}m" else "${elapsedMinutes / 60}h ${elapsedMinutes % 60}m"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExitClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PrimaryBlue.copy(alpha = 0.12f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = when (session.vehicleTypeId) {
                                "MOTORCYCLE" -> "🏍"
                                "VAN" -> "🚐"
                                "BUS" -> "🚌"
                                "TRUCK" -> "🚚"
                                else -> "🚗"
                            },
                            fontSize = 22.sp
                        )
                    }
                }

                Column {
                    Text(
                        text = session.vehicleNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "In: ${timeFormat.format(Date(session.entryTime))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(text = "•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = elapsedFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }
                }
            }

            Button(
                onClick = onExitClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("EXIT & BILL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
