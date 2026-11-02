@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartparking.app.ui.screens.dashboard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartparking.app.data.local.SmartParkingDatabase
import com.smartparking.app.data.local.entity.ParkingSessionEntity
import com.smartparking.app.hardware.capability.DeviceCapabilities
import com.smartparking.app.license.PbLicenseService
import com.smartparking.app.ui.theme.*
import kotlinx.coroutines.launch
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
    val database = remember { SmartParkingDatabase.getInstance(context) }
    val licenseService = remember { PbLicenseService.getInstance(context) }

    var licenseState by remember { mutableStateOf(licenseService.getLicenseState()) }

    // Start of current day timestamp
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
    val activeMembersCount by database.membershipDao().getActiveMembersCountFlow().collectAsState(initial = 0)

    val totalCapacity = 200
    val availableSpots = (totalCapacity - parkedCount).coerceAtLeast(0)
    val coroutineScope = rememberCoroutineScope()

    // Real-time Cloud Sync on launch
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.smartparking.app.sync.ParkingSyncService.getInstance(context).syncWithCloud()
        }
    }

    Scaffold(
        topBar = {
            DashboardTopBar(
                capabilities = capabilities,
                licenseState = licenseState,
                onLicenseClick = { navController.navigate("license") },
                onInfoClick = { navController.navigate("device_info") },
                onSyncClick = {
                    coroutineScope.launch {
                        Toast.makeText(context, "Syncing with PB Cloud...", Toast.LENGTH_SHORT).show()
                        val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.smartparking.app.sync.ParkingSyncService.getInstance(context).syncWithCloud()
                        }
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
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
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 1. Live Device Status Banner
            capabilities?.let { caps ->
                item {
                    DeviceStatusBanner(
                        model = "${caps.manufacturer} ${caps.model}",
                        serialNumber = caps.serialNumber,
                        isSunmi = caps.isSunmiDevice,
                        onBannerClick = { navController.navigate("device_info") }
                    )
                }
            }

            // 2. Live Statistics Overview (Cards)
            item {
                StatisticsOverview(
                    totalCapacity = totalCapacity,
                    currentlyParked = parkedCount,
                    todayEntries = todayEntries,
                    activeMembers = activeMembersCount
                )
            }

            // 3. Quick Action Buttons (6 Aligned Actions)
            item {
                QuickActionSection(
                    onEntryClick = { navController.navigate("entry") },
                    onExitClick = { navController.navigate("exit") },
                    onMembershipClick = { navController.navigate("membership") },
                    onLicenseClick = { navController.navigate("license") },
                    onReportsClick = { navController.navigate("reports") },
                    onDeviceClick = { navController.navigate("device_info") }
                )
            }

            // 4. Live Occupancy Bar
            item {
                OccupancyCard(
                    total = totalCapacity,
                    occupied = parkedCount,
                    available = availableSpots
                )
            }

            // 5. Recent Activity Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Currently Parked Vehicles (${liveParkedSessions.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = { navController.navigate("reports") }) {
                        Text("View All Logs")
                    }
                }
            }

            // 6. Parked Vehicles List
            if (liveParkedSessions.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = "🅿️", fontSize = 32.sp)
                            Text(
                                text = "No vehicles currently parked",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Tap \"Vehicle Entry\" to record and print ticket for incoming vehicles.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(liveParkedSessions.take(10), key = { it.id }) { session ->
                    LiveParkedVehicleItem(
                        session = session,
                        onExitClick = { navController.navigate("exit") }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTopBar(
    capabilities: DeviceCapabilities?,
    licenseState: PbLicenseService.LicenseState,
    onLicenseClick: () -> Unit,
    onInfoClick: () -> Unit,
    onSyncClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Smart Parking",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = capabilities?.let { "${it.manufacturer} ${it.model} • SN: ${it.serialNumber}" } ?: "Terminal • SUNMI POS",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            // Manual Cloud Sync button
            IconButton(onClick = onSyncClick) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync with Cloud",
                    tint = PrimaryBlue
                )
            }

            // License status badge pill (clickable)
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
                            is PbLicenseService.LicenseState.TrialActive -> "⏳ ${(licenseState as PbLicenseService.LicenseState.TrialActive).daysRemaining}d Trial"
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

            IconButton(onClick = onInfoClick) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Device & App Info",
                    tint = PrimaryBlue
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun DeviceStatusBanner(
    model: String,
    serialNumber: String,
    isSunmi: Boolean,
    onBannerClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onBannerClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isSunmi) PrimaryBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant,
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "📱", fontSize = 18.sp)
                Column {
                    Text(
                        text = model,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "SN: $serialNumber",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SuccessGreen.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "HARDWARE READY",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = SuccessGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun StatisticsOverview(
    totalCapacity: Int,
    currentlyParked: Int,
    todayEntries: Int,
    activeMembers: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Total Capacity",
                value = "$totalCapacity spots",
                icon = "🅿️",
                color = PrimaryBlue
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Parked Now",
                value = "$currentlyParked",
                icon = "🚗",
                color = StatusParked
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Today Entries",
                value = "$todayEntries",
                icon = "🚪",
                color = SuccessGreen
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Active Passes",
                value = "$activeMembers VIPs",
                icon = "👑",
                color = AmberWarning
            )
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = icon, fontSize = 20.sp)
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun QuickActionSection(
    onEntryClick: () -> Unit,
    onExitClick: () -> Unit,
    onMembershipClick: () -> Unit,
    onLicenseClick: () -> Unit,
    onReportsClick: () -> Unit,
    onDeviceClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionButton(
                modifier = Modifier.weight(1f),
                label = "Vehicle Entry",
                icon = "🚗",
                backgroundColor = PrimaryBlue,
                onClick = onEntryClick
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                label = "Vehicle Exit",
                icon = "🚪",
                backgroundColor = SuccessGreen,
                onClick = onExitClick
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionButton(
                modifier = Modifier.weight(1f),
                label = "Memberships",
                icon = "👑",
                backgroundColor = AmberWarning,
                onClick = onMembershipClick
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                label = "PB License",
                icon = "🔑",
                backgroundColor = PrimaryBlue,
                onClick = onLicenseClick
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionButton(
                modifier = Modifier.weight(1f),
                label = "History & Logs",
                icon = "📊",
                backgroundColor = InfoBlue,
                onClick = onReportsClick
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                label = "Device Info",
                icon = "⚙️",
                backgroundColor = DarkSlate,
                onClick = onDeviceClick
            )
        }
    }
}

@Composable
fun ActionButton(
    modifier: Modifier = Modifier,
    label: String,
    icon: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = icon, fontSize = 20.sp)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
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
                    text = "Parking Occupancy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${(percentage * 100).toInt()}% Full",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (percentage > 0.85f) ErrorRed else PrimaryBlue
                )
            }

            LinearProgressIndicator(
                progress = { percentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = if (percentage > 0.85f) ErrorRed else PrimaryBlue,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Occupied: $occupied",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Available: $available",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen
                )
            }
        }
    }
}

@Composable
fun LiveParkedVehicleItem(
    session: ParkingSessionEntity,
    onExitClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
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
                Text(
                    text = when (session.vehicleTypeId) {
                        "MOTORCYCLE" -> "🏍"
                        "VAN" -> "🚐"
                        "BUS" -> "🚌"
                        else -> "🚗"
                    },
                    fontSize = 24.sp
                )
                Column {
                    Text(
                        text = session.vehicleNumber,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Entered: ${dateFormat.format(Date(session.entryTime))} • ${session.ticketNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onExitClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Exit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
