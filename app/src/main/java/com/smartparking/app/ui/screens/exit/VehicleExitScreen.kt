package com.smartparking.app.ui.screens.exit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
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
import com.smartparking.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleExitScreen(
    navController: NavController,
    onProcessExit: (ticketNumber: String, paymentMethod: String) -> Unit = { _, _ -> }
) {
    var scanMode by remember { mutableStateOf(true) }
    var ticketNumber by remember { mutableStateOf("") }
    var vehicleFound by remember { mutableStateOf<ExitVehicleInfo?>(null) }
    var selectedPayment by remember { mutableStateOf("CASH") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vehicle Exit", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Scan or Manual Entry Mode
            if (vehicleFound == null) {
                ScanTicketSection(
                    onScanClick = {
                        // Trigger SUNMI hardware scanner
                        // Simulating found vehicle
                        vehicleFound = ExitVehicleInfo(
                            ticketNumber = "SP-20260913-001245",
                            vehicleNumber = "BA 12 PA 1234",
                            vehicleType = "CAR",
                            entryTime = "09:42 AM",
                            exitTime = "12:00 PM",
                            duration = "2h 18m",
                            parkingFee = "120.00",
                            discount = "0.00"
                        )
                    },
                    onManualSearch = { query ->
                        // Search by vehicle number
                    }
                )
            } else {
                // 2. Vehicle Found - Show Details
                VehicleExitDetails(vehicle = vehicleFound!!)

                // 3. Payment Method Selection
                PaymentMethodSection(
                    selectedMethod = selectedPayment,
                    onMethodSelected = { selectedPayment = it }
                )

                Spacer(modifier = Modifier.weight(1f))

                // 4. Complete Exit & Print Receipt
                Button(
                    onClick = {
                        onProcessExit(vehicleFound!!.ticketNumber, selectedPayment)
                        navController.popBackStack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "✓", fontSize = 24.sp)
                        Text(
                            text = "COMPLETE EXIT & PRINT",
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScanTicketSection(
    onScanClick: () -> Unit,
    onManualSearch: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Large Scan Button
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan QR",
                    modifier = Modifier.size(64.dp),
                    tint = PrimaryBlue
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Scan Ticket QR Code",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
                Text(
                    text = "Hardware scanner ready",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = onScanClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("SCAN TICKET", style = MaterialTheme.typography.labelLarge)
        }

        HorizontalDivider()

        Text(
            text = "Or search manually",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = "",
            onValueChange = { onManualSearch(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter Vehicle Number or Ticket") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun VehicleExitDetails(vehicle: ExitVehicleInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Vehicle Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = vehicle.vehicleNumber,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${vehicle.vehicleType} • ${vehicle.ticketNumber}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(StatusParked.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🚗", fontSize = 24.sp)
                }
            }

            HorizontalDivider()

            // Parking Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Entry", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(vehicle.entryTime, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Duration", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(vehicle.duration, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Exit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(vehicle.exitTime, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }

            HorizontalDivider()

            // Fee Breakdown
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Parking Fee", style = MaterialTheme.typography.bodyMedium)
                    Text("NPR ${vehicle.parkingFee}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                if (vehicle.discount != "0.00") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Discount", style = MaterialTheme.typography.bodyMedium, color = SuccessGreen)
                        Text("-NPR ${vehicle.discount}", style = MaterialTheme.typography.bodyMedium, color = SuccessGreen)
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("TOTAL PAYABLE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "NPR ${vehicle.parkingFee}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentMethodSection(
    selectedMethod: String,
    onMethodSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Payment Method",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            paymentMethods.forEach { method ->
                PaymentMethodChip(
                    modifier = Modifier.weight(1f),
                    method = method,
                    isSelected = selectedMethod == method.id,
                    onSelect = { onMethodSelected(method.id) }
                )
            }
        }
    }
}

@Composable
fun PaymentMethodChip(
    modifier: Modifier = Modifier,
    method: PaymentMethod,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = modifier
            .height(72.dp),
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) null else CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = method.icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = method.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

data class ExitVehicleInfo(
    val ticketNumber: String,
    val vehicleNumber: String,
    val vehicleType: String,
    val entryTime: String,
    val exitTime: String,
    val duration: String,
    val parkingFee: String,
    val discount: String
)

data class PaymentMethod(
    val id: String,
    val label: String,
    val icon: String
)

val paymentMethods = listOf(
    PaymentMethod("CASH", "Cash", "💵"),
    PaymentMethod("QR", "QR Pay", "📱"),
    PaymentMethod("CARD", "Card", "💳")
)
