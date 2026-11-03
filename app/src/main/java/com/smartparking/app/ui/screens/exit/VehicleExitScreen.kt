package com.smartparking.app.ui.screens.exit

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartparking.app.data.local.SmartParkingDatabase
import com.smartparking.app.data.local.entity.MembershipEntity
import com.smartparking.app.domain.settings.PosSettingsRepository
import com.smartparking.app.domain.tariff.TariffEngine
import com.smartparking.app.domain.tariff.TariffRepository
import com.smartparking.app.hardware.sunmi.SunmiScannerManager
import com.smartparking.app.hardware.sunmi.SunmiScannerReceiver
import com.smartparking.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExitVehicleInfo(
    val sessionId: String,
    val ticketNumber: String,
    val vehicleNumber: String,
    val vehicleType: String,
    val entryTimeMillis: Long,
    val entryTime: String,
    val exitTime: String,
    val duration: String,
    val durationMinutes: Long,
    val parkingFee: String,
    val discount: String,
    val totalPaid: String,
    val membership: MembershipEntity? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleExitScreen(
    navController: NavController,
    initialTicketNumber: String = "",
    onProcessExit: (ticketNumber: String, paymentMethod: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { SmartParkingDatabase.getInstance(context) }
    val tariffEngine = remember { TariffEngine() }
    val posSettingsRepo = remember { PosSettingsRepository.getInstance(context) }
    val scannerManager = remember { SunmiScannerManager(context) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val enabledPaymentMethods = remember { posSettingsRepo.getEnabledPaymentMethods() }

    var searchQuery by remember { mutableStateOf(initialTicketNumber) }
    var vehicleFound by remember { mutableStateOf<ExitVehicleInfo?>(null) }
    var selectedPayment by remember {
        mutableStateOf(enabledPaymentMethods.firstOrNull { it != "VIP_PASS" } ?: "CASH")
    }
    var isSearching by remember { mutableStateOf(false) }
    var isCompleting by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    fun lookupVehicle(queryStr: String) {
        val clean = queryStr.trim().uppercase(Locale.US)
        if (clean.isBlank()) return
        isSearching = true
        searchError = null
        keyboardController?.hide()

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                // Search session by ticket number or active vehicle plate
                var session = database.parkingSessionDao().getSessionByTicketNumber(clean)
                if (session == null || session.status != "PARKED") {
                    session = database.parkingSessionDao().getActiveSessionByVehicleNumber(clean)
                }

                if (session == null) {
                    val searchResults = database.parkingSessionDao().searchSessions(clean)
                    session = searchResults.firstOrNull { it.status == "PARKED" }
                }

                withContext(Dispatchers.Main) {
                    isSearching = false
                    if (session != null) {
                        val exitTimeEpoch = System.currentTimeMillis()
                        val durationMinutes = tariffEngine.calculateDuration(session.entryTime, exitTimeEpoch)
                        val durationFormatted = tariffEngine.formatDuration(durationMinutes)

                        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                        val entryTimeFormatted = timeFormat.format(Date(session.entryTime))
                        val exitTimeFormatted = timeFormat.format(Date(exitTimeEpoch))

                        // Check if vehicle has an active membership pass
                        coroutineScope.launch(Dispatchers.IO) {
                            val activeMember = database.membershipDao().getActiveMembershipForVehicle(session.vehicleNumber, exitTimeEpoch)

                            val activeTariff = TariffRepository.getInstance(context).getTariff(session.vehicleTypeId)

                            val calcResult = tariffEngine.calculateFee(
                                durationMinutes = durationMinutes,
                                tariffConfig = activeTariff,
                                isLostTicket = false,
                                memberDiscountPercent = activeMember?.discountPercent,
                                memberInfo = activeMember?.let { "${it.planType} Pass" }
                            )

                            withContext(Dispatchers.Main) {
                                vehicleFound = ExitVehicleInfo(
                                    sessionId = session.id,
                                    ticketNumber = session.ticketNumber,
                                    vehicleNumber = session.vehicleNumber,
                                    vehicleType = session.vehicleTypeId,
                                    entryTimeMillis = session.entryTime,
                                    entryTime = entryTimeFormatted,
                                    exitTime = exitTimeFormatted,
                                    duration = durationFormatted,
                                    durationMinutes = durationMinutes,
                                    parkingFee = calcResult.grossAmount.toPlainString(),
                                    discount = calcResult.discountAmount.toPlainString(),
                                    totalPaid = calcResult.netAmount.toPlainString(),
                                    membership = activeMember
                                )
                                if (activeMember != null) {
                                    selectedPayment = "VIP_PASS"
                                }
                            }
                        }
                    } else {
                        searchError = "No currently parked vehicle found matching \"$clean\"."
                    }
                }
            }
        }
    }

    // 1. Reactive subscription to SUNMI hardware scanner broadcasts
    LaunchedEffect(Unit) {
        SunmiScannerReceiver.scanFlow.collect { result ->
            if (result.data.isNotBlank()) {
                searchQuery = result.data
                lookupVehicle(result.data)
            }
        }
    }

    // 2. Auto-load initial ticket if passed from navigation
    LaunchedEffect(initialTicketNumber) {
        if (initialTicketNumber.isNotBlank()) {
            searchQuery = initialTicketNumber
            lookupVehicle(initialTicketNumber)
        }
    }

    LaunchedEffect(enabledPaymentMethods, vehicleFound) {
        if (vehicleFound?.membership != null) {
            selectedPayment = "VIP_PASS"
        } else if (!enabledPaymentMethods.contains(selectedPayment)) {
            selectedPayment = enabledPaymentMethods.firstOrNull { it != "VIP_PASS" } ?: "CASH"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Vehicle Exit & Billing", fontWeight = FontWeight.Bold)
                        Text(
                            "Scan ticket or search plate",
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
            Spacer(modifier = Modifier.height(2.dp))

            if (vehicleFound == null) {
                // 1. Scan Ticket / QR Section (Hardware Trigger)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Trigger physical laser or camera scanner on SUNMI
                            scannerManager.triggerScan()
                            Toast.makeText(context, "Scanner triggered. Scan ticket QR code.", Toast.LENGTH_SHORT).show()
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.08f)),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = PrimaryBlue.copy(alpha = 0.15f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan QR",
                                    modifier = Modifier.size(36.dp),
                                    tint = PrimaryBlue
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Scan Ticket Barcode / QR",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                        Text(
                            text = "SUNMI hardware scanner listening • Tap to trigger beam",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 2. Manual Search Box - Perfectly Aligned
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
                            text = "Search by Ticket No or License Plate",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it.uppercase() },
                                modifier = Modifier.weight(1f),
                                label = { Text("Ticket or Plate Number") },
                                placeholder = { Text("e.g. SP-2026... or 1234") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryBlue)
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(
                                    onSearch = { lookupVehicle(searchQuery) }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryBlue,
                                    unfocusedBorderColor = BorderLight
                                )
                            )

                            Button(
                                onClick = { lookupVehicle(searchQuery) },
                                modifier = Modifier.height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = searchQuery.isNotBlank() && !isSearching,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                if (isSearching) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                } else {
                                    Text("FIND", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        searchError?.let { err ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ErrorRed.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = err,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ErrorRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            } else {
                // Vehicle Found Display
                val vehicle = vehicleFound!!

                // VIP Member Pass Highlight
                vehicle.membership?.let { member ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = SuccessGreen.copy(alpha = 0.12f),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = SuccessGreen)
                            Column {
                                Text(
                                    text = "VIP MEMBER PASS APPLIED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${member.fullName} - ${member.planType.replace("_", " ")}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "100% Free Parking Exit Waived",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SuccessGreen
                                )
                            }
                        }
                    }
                }

                // Vehicle Details Card
                VehicleExitDetailsCard(vehicle = vehicle)

                // Payment Method Selector
                PaymentMethodSection(
                    selectedMethod = selectedPayment,
                    isMemberPass = vehicle.membership != null,
                    enabledMethods = enabledPaymentMethods,
                    onMethodSelected = { selectedPayment = it }
                )

                // QR Display Card (Shown when QR / Online Payment is selected)
                if (selectedPayment == "QR_ONLINE") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "📱 Scan to Pay NPR ${vehicle.totalPaid}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            val qrBitmap = remember { posSettingsRepo.getQrBitmap() }
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Merchant Payment QR",
                                    modifier = Modifier
                                        .size(190.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .padding(8.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(160.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.QrCodeScanner,
                                            contentDescription = null,
                                            modifier = Modifier.size(54.dp),
                                            tint = PrimaryBlue
                                        )
                                        Text(
                                            text = "Upload QR Photo in Portal",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.DarkGray
                                        )
                                    }
                                }
                            }

                            val qrNote = remember { posSettingsRepo.getQrNote() }
                            Text(
                                text = qrNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            vehicleFound = null
                            searchQuery = ""
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("CANCEL", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            isCompleting = true
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    val session = database.parkingSessionDao().getSessionById(vehicle.sessionId)
                                    session?.let { s ->
                                        val updated = s.copy(
                                            status = "EXITED",
                                            exitTime = System.currentTimeMillis(),
                                            durationMinutes = vehicle.durationMinutes.toInt(),
                                            grossAmount = vehicle.parkingFee,
                                            discountAmount = vehicle.discount,
                                            netAmount = vehicle.totalPaid,
                                            paymentStatus = if (vehicle.totalPaid == "0.00") "WAIVED" else "PAID",
                                            paymentMethod = selectedPayment,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        database.parkingSessionDao().updateSession(updated)
                                    }
                                }

                                onProcessExit(vehicle.ticketNumber, selectedPayment)

                                withContext(Dispatchers.Main) {
                                    isCompleting = false
                                    Toast.makeText(context, "Exit Complete & Receipt Printed!", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                }
                            }
                        },
                        enabled = !isCompleting,
                        modifier = Modifier
                            .weight(2f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (vehicle.membership != null) PrimaryBlue else SuccessGreen
                        )
                    ) {
                        if (isCompleting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Text(
                                    text = if (vehicle.membership != null) "PASS EXIT & RECEIPT" else "PAY & PRINT RECEIPT",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun VehicleExitDetailsCard(vehicle: ExitVehicleInfo) {
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
                        text = vehicle.vehicleNumber,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ticket: ${vehicle.ticketNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PrimaryBlue.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = vehicle.vehicleType,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Entry Time", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(text = vehicle.entryTime, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text(text = "Exit Time", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(text = vehicle.exitTime, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text(text = "Duration", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(text = vehicle.duration, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                }
            }

            HorizontalDivider()

            // Financial Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Gross Parking Fee", style = MaterialTheme.typography.bodyMedium)
                Text(text = "NPR ${vehicle.parkingFee}", style = MaterialTheme.typography.bodyMedium)
            }

            if (vehicle.discount != "0.00") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Member Discount", style = MaterialTheme.typography.bodyMedium, color = SuccessGreen)
                    Text(text = "- NPR ${vehicle.discount}", style = MaterialTheme.typography.bodyMedium, color = SuccessGreen, fontWeight = FontWeight.Bold)
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "TOTAL DUE", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = "NPR ${vehicle.totalPaid}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = if (vehicle.totalPaid == "0.00") SuccessGreen else PrimaryBlue
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentMethodSection(
    selectedMethod: String,
    isMemberPass: Boolean,
    enabledMethods: List<String>,
    onMethodSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Payment Method",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val allMethods = if (isMemberPass) {
                listOf("VIP_PASS" to "👑 Member Pass", "CASH" to "💵 Cash")
            } else {
                listOf("CASH" to "💵 Cash", "QR_ONLINE" to "📱 QR Pay", "CARD" to "💳 Card")
            }

            val activeMethods = allMethods.filter { (id, _) ->
                enabledMethods.contains(id) || (isMemberPass && id == "VIP_PASS")
            }.ifEmpty { listOf("CASH" to "💵 Cash") }

            activeMethods.forEach { (methodId, label) ->
                FilterChip(
                    selected = selectedMethod == methodId,
                    onClick = { onMethodSelected(methodId) },
                    label = { Text(label, fontWeight = if (selectedMethod == methodId) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
