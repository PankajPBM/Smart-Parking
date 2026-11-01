package com.smartparking.app.ui.screens.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartparking.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleEntryScreen(
    navController: NavController,
    onGenerateTicket: (plateNumber: String, vehicleType: String, zone: String, customerPhone: String) -> Unit = { _, _, _, _ -> }
) {
    var plateNumber by remember { mutableStateOf("") }
    var selectedVehicleType by remember { mutableStateOf("CAR") }
    var selectedZone by remember { mutableStateOf("Ground Floor - A") }
    var customerPhone by remember { mutableStateOf("") }
    var isPrinting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vehicle Entry", fontWeight = FontWeight.Bold) },
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
            // 1. Plate Number Input (Large touch target)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Vehicle License Plate *",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = plateNumber,
                    onValueChange = { plateNumber = it.uppercase() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    placeholder = { Text("e.g. BA 12 PA 1234") },
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Text
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = BorderLight
                    )
                )
            }

            // 2. Vehicle Type Selector (Large Cards)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Vehicle Type",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    vehicleTypes.forEach { type ->
                        VehicleTypeCard(
                            modifier = Modifier.weight(1f),
                            type = type,
                            isSelected = selectedVehicleType == type.id,
                            onSelect = { selectedVehicleType = type.id }
                        )
                    }
                }
            }

            // 3. Zone Picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Parking Zone",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    zones.forEach { zone ->
                        ZoneChip(
                            label = zone,
                            isSelected = selectedZone == zone,
                            onSelect = { selectedZone = zone }
                        )
                    }
                }
            }

            // 4. Optional Customer Phone (for SMS receipt)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Customer Phone (Optional)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = { customerPhone = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("98XXXXXXXX") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 5. Generate & Auto Print Button
            Button(
                onClick = {
                    if (plateNumber.isNotBlank()) {
                        isPrinting = true
                        onGenerateTicket(plateNumber, selectedVehicleType, selectedZone, customerPhone)
                        navController.popBackStack()
                    }
                },
                enabled = plateNumber.isNotBlank() && !isPrinting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                if (isPrinting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "🖨️", fontSize = 22.sp)
                        Text(
                            text = "GENERATE & PRINT TICKET",
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
fun VehicleTypeCard(
    modifier: Modifier = Modifier,
    type: VehicleTypeOption,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = modifier
            .height(88.dp)
            .clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) null else CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterVertically,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = type.icon, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = type.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ZoneChip(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onSelect() },
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) PrimaryBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isSelected) ButtonDefaults.outlinedButtonBorder else null
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

data class VehicleTypeOption(
    val id: String,
    val label: String,
    val icon: String
)

val vehicleTypes = listOf(
    VehicleTypeOption("CAR", "Car", "🚗"),
    VehicleTypeOption("MOTORCYCLE", "Bike", "🏍"),
    VehicleTypeOption("VAN", "Van", "🚐"),
    VehicleTypeOption("BUS", "Bus", "🚌")
)

val zones = listOf(
    "Ground Floor - A",
    "Basement 1 - B",
    "Outdoor VIP"
)
