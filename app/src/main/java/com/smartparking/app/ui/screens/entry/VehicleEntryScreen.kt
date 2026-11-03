package com.smartparking.app.ui.screens.entry

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartparking.app.data.local.SmartParkingDatabase
import com.smartparking.app.data.local.entity.MembershipEntity
import com.smartparking.app.data.local.entity.ParkingSessionEntity
import com.smartparking.app.domain.settings.PosSettingsRepository
import com.smartparking.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleEntryScreen(
    navController: NavController,
    onGenerateTicket: (plateNumber: String, vehicleType: String, zone: String, customerPhone: String) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { SmartParkingDatabase.getInstance(context) }
    val posSettingsRepo = remember { PosSettingsRepository.getInstance(context) }

    val enabledVehicleIds = remember { posSettingsRepo.getEnabledVehicleTypes() }
    val activeVehicleTypes = remember(enabledVehicleIds) {
        val all = listOf(
            VehicleTypeOption("CAR", "Car", "🚗"),
            VehicleTypeOption("MOTORCYCLE", "Bike", "🏍"),
            VehicleTypeOption("VAN", "Van", "🚐"),
            VehicleTypeOption("BUS", "Bus", "🚌"),
            VehicleTypeOption("TRUCK", "Truck", "🚚")
        )
        val filtered = all.filter { enabledVehicleIds.contains(it.id) }
        if (filtered.isEmpty()) all.take(2) else filtered
    }

    var plateNumber by remember { mutableStateOf("") }
    var selectedVehicleType by remember {
        mutableStateOf(activeVehicleTypes.firstOrNull { it.id == "CAR" }?.id ?: activeVehicleTypes.first().id)
    }
    var selectedZone by remember { mutableStateOf("Ground Floor - A") }
    var customerPhone by remember { mutableStateOf("") }
    var isPrinting by remember { mutableStateOf(false) }

    // Toggle between Numeric Keypad (default) and Full Character Keyboard
    var isNumericKeyboard by remember { mutableStateOf(true) }

    var matchedMember by remember { mutableStateOf<MembershipEntity?>(null) }

    // Auto-focus and auto-show keyboard
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(250) // Small delay to guarantee Compose layout pass completes
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Ensure selected vehicle type is valid among enabled ones
    LaunchedEffect(activeVehicleTypes) {
        if (activeVehicleTypes.none { it.id == selectedVehicleType }) {
            selectedVehicleType = activeVehicleTypes.first().id
        }
    }

    // Real-time lookup for active membership whenever plate number is entered
    LaunchedEffect(plateNumber) {
        val cleanPlate = plateNumber.trim().uppercase(Locale.US)
        if (cleanPlate.length >= 3) {
            withContext(Dispatchers.IO) {
                val member = database.membershipDao().getActiveMembershipForVehicle(cleanPlate)
                withContext(Dispatchers.Main) {
                    matchedMember = member
                    member?.let {
                        selectedVehicleType = it.vehicleTypeId
                        if (it.phone.isNotBlank() && customerPhone.isBlank()) {
                            customerPhone = it.phone
                        }
                    }
                }
            }
        } else {
            matchedMember = null
        }
    }

    fun submitEntry() {
        if (plateNumber.isNotBlank() && !isPrinting) {
            isPrinting = true
            val cleanPlate = plateNumber.trim().uppercase(Locale.US)
            val nowEpoch = System.currentTimeMillis()
            val ticketNo = "SP-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(nowEpoch)) + "-" + (1000..9999).random()

            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    val session = ParkingSessionEntity(
                        id = UUID.randomUUID().toString(),
                        ticketNumber = ticketNo,
                        siteId = "site_default_01",
                        vehicleNumber = cleanPlate,
                        vehicleTypeId = selectedVehicleType,
                        customerPhone = customerPhone.ifBlank { null },
                        entryTime = nowEpoch,
                        status = "PARKED",
                        notes = matchedMember?.let { "Member Pass: ${it.memberNumber} (${it.fullName})" }
                    )
                    database.parkingSessionDao().insertSession(session)
                }

                onGenerateTicket(cleanPlate, selectedVehicleType, selectedZone, customerPhone)

                withContext(Dispatchers.Main) {
                    isPrinting = false
                    Toast.makeText(context, "Entry Recorded: $ticketNo", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Vehicle Entry", fontWeight = FontWeight.Bold)
                        Text(
                            "Type plate & generate entry ticket",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. Vehicle License Plate (Auto Numeric Keypad + Quick ABC Toggle)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vehicle License Plate *",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Mode Switcher: [ 123 Numeric ] vs [ ABC Text ]
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PrimaryBlue.copy(alpha = 0.12f),
                            modifier = Modifier.clickable {
                                isNumericKeyboard = !isNumericKeyboard
                                keyboardController?.show()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isNumericKeyboard) "🔢 123 (NUMERIC)" else "🔤 ABC (TEXT)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue
                                )
                                Text(
                                    text = "Tap to switch",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = plateNumber,
                        onValueChange = { plateNumber = it.uppercase() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = { Text("e.g. 1234 or BA 12 PA 1234") },
                        label = { Text("Plate Number") },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = PrimaryBlue)
                        },
                        trailingIcon = {
                            if (plateNumber.isNotEmpty()) {
                                IconButton(onClick = { plateNumber = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = if (isNumericKeyboard) KeyboardType.Number else KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                submitEntry()
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = BorderLight
                        )
                    )
                }
            }

            // 2. VIP Membership Found Banner
            matchedMember?.let { member ->
                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = PrimaryBlue.copy(alpha = 0.12f),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PrimaryBlue,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color.White)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "VIP MEMBER PASS DETECTED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrimaryBlue,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SuccessGreen
                                ) {
                                    Text(
                                        text = "FREE PASS",
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "${member.fullName} (${member.memberNumber})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Valid until ${dateFormat.format(Date(member.endDate))} - 100% Zero Fee at Exit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. Vehicle Type Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Vehicle Type",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                val chunkedVehicles = remember(activeVehicleTypes) {
                    if (activeVehicleTypes.size <= 4) listOf(activeVehicleTypes) else activeVehicleTypes.chunked(3)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunkedVehicles.forEach { rowTypes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowTypes.forEach { type ->
                                VehicleTypeCard(
                                    modifier = Modifier.weight(1f),
                                    type = type,
                                    isSelected = selectedVehicleType == type.id,
                                    onSelect = { selectedVehicleType = type.id }
                                )
                            }
                        }
                    }
                }
            }

            // 4. Parking Zone Picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Parking Zone",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    zones.forEach { zone ->
                        ZoneChip(
                            label = zone,
                            isSelected = selectedZone == zone,
                            onSelect = { selectedZone = zone },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 5. Customer Phone (Optional) - Perfectly aligned
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Customer Phone (Optional)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = { customerPhone = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. 98XXXXXXXX") },
                    label = { Text("Mobile Number") },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = PrimaryBlue)
                    },
                    trailingIcon = {
                        if (customerPhone.isNotEmpty()) {
                            IconButton(onClick = { customerPhone = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = BorderLight
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 6. Generate & Auto Print Button
            Button(
                onClick = { submitEntry() },
                enabled = plateNumber.isNotBlank() && !isPrinting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
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
                            text = if (matchedMember != null) "PRINT VIP PASS ENTRY" else "GENERATE & PRINT TICKET",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
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
            .height(86.dp)
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = type.icon, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = type.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ZoneChip(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) PrimaryBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isSelected) ButtonDefaults.outlinedButtonBorder else null
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label.replace(" - ", "\n"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
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
    "Ground - A",
    "Basement - B",
    "VIP Zone"
)
