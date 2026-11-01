package com.smartparking.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartparking.app.hardware.capability.DeviceCapabilities
import com.smartparking.app.ui.theme.*

@Composable
fun DashboardScreen(
    navController: NavController,
    capabilities: DeviceCapabilities? = null
) {
    Scaffold(
        topBar = {
            DashboardTopBar(
                capabilities = capabilities,
                onInfoClick = { navController.navigate("device_info") }
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
                    totalCapacity = 200,
                    currentlyParked = 83,
                    todayEntries = 42,
                    todayRevenue = "18,450"
                )
            }

            // 3. Quick Action Buttons
            item {
                QuickActionSection(
                    onEntryClick = { navController.navigate("entry") },
                    onExitClick = { navController.navigate("exit") },
                    onSearchClick = { navController.navigate("device_info") },
                    onReportsClick = { navController.navigate("device_info") }
                )
            }

            // 4. Occupancy Bar
            item {
                OccupancyCard(
                    total = 200,
                    occupied = 83,
                    available = 117
                )
            }

            // 5. Recent Activity Header
            item {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // 6. Recent Parked Vehicles List
            items(sampleRecentVehicles) { vehicle ->
                RecentVehicleItem(vehicle = vehicle)
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTopBar(
    capabilities: DeviceCapabilities?,
    onInfoClick: () -> Unit
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
                    text = capabilities?.let { "${it.manufacturer} ${it.model} • SN: ${it.serialNumber}" } ?: "Kathmandu Terminal • SUNMI POS",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
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
        color = if (isSunmi) PrimaryBlue.copy(alpha = 0.08f) else SurfaceVariantLight,
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

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(SuccessGreen.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "● ONLINE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen
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
    todayRevenue: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Total Spaces",
                value = "$totalCapacity",
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
                title = "Today Revenue",
                value = "NPR $todayRevenue",
                icon = "💰",
                color = WarningOrange
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
    onSearchClick: () -> Unit,
    onReportsClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(
                modifier = Modifier.weight(1f),
                label = "Device Info",
                icon = "📱",
                backgroundColor = InfoBlue,
                onClick = onSearchClick
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                label = "App Details",
                icon = "ℹ️",
                backgroundColor = WarningOrange,
                onClick = onReportsClick
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
    val percentage = if (total > 0) (occupied.toFloat() / total.toFloat()) else 0f

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
                    text = "Occupancy Status",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${(percentage * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (percentage > 0.85f) ErrorRed else PrimaryBlue
                )
            }

            LinearProgressIndicator(
                progress = percentage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
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
                    color = SuccessGreen
                )
            }
        }
    }
}

@Composable
fun RecentVehicleItem(vehicle: RecentVehicle) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(vehicle.typeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = vehicle.icon, fontSize = 20.sp)
                }

                Column {
                    Text(
                        text = vehicle.plateNumber,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${vehicle.type} • Entry: ${vehicle.entryTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = vehicle.duration,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = PrimaryBlue
            )
        }
    }
}

data class RecentVehicle(
    val plateNumber: String,
    val type: String,
    val icon: String,
    val typeColor: Color,
    val entryTime: String,
    val duration: String
)

val sampleRecentVehicles = listOf(
    RecentVehicle("BA 12 PA 1234", "Car", "🚗", VehicleCar, "09:42 AM", "2h 18m"),
    RecentVehicle("BA 45 PA 9876", "Motorcycle", "🏍", VehicleMotorcycle, "10:15 AM", "1h 45m"),
    RecentVehicle("GA 01 PA 5544", "Van", "🚐", VehicleVan, "10:30 AM", "1h 30m"),
    RecentVehicle("BA 99 PA 1122", "Car", "🚗", VehicleCar, "11:00 AM", "1h 00m")
)
