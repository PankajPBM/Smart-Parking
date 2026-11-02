@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartparking.app.ui.screens.membership

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartparking.app.data.local.SmartParkingDatabase
import com.smartparking.app.data.local.entity.MembershipEntity
import com.smartparking.app.hardware.printer.UniversalHardwareService
import com.smartparking.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembershipScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { SmartParkingDatabase.getInstance(context) }
    val hardwareService = remember { UniversalHardwareService.getInstance(context) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterTab by remember { mutableStateOf("ALL") } // ALL, ACTIVE, EXPIRING, EXPIRED, VIP
    var showAddDialog by remember { mutableStateOf(false) }
    var memberToRenew by remember { mutableStateOf<MembershipEntity?>(null) }
    var memberToDelete by remember { mutableStateOf<MembershipEntity?>(null) }

    val allMembers by database.membershipDao().getAllMembershipsFlow().collectAsState(initial = emptyList())
    val now = System.currentTimeMillis()

    val filteredMembers = remember(allMembers, searchQuery, selectedFilterTab) {
        allMembers.filter { member ->
            val matchesQuery = searchQuery.isBlank() ||
                    member.fullName.contains(searchQuery, ignoreCase = true) ||
                    member.vehicleNumber.contains(searchQuery, ignoreCase = true) ||
                    member.phone.contains(searchQuery, ignoreCase = true) ||
                    member.memberNumber.contains(searchQuery, ignoreCase = true)

            val isExpired = member.endDate < now || !member.isActive
            val isExpiringSoon = !isExpired && (member.endDate - now) <= (7 * 24 * 3600 * 1000L)

            val matchesTab = when (selectedFilterTab) {
                "ACTIVE" -> !isExpired
                "EXPIRING" -> isExpiringSoon
                "EXPIRED" -> isExpired
                "VIP" -> member.planType == "VIP_PASS"
                else -> true
            }

            matchesQuery && matchesTab
        }
    }

    val totalMembers = allMembers.size
    val activeMembersCount = allMembers.count { it.isActive && it.endDate >= now }
    val expiredMembersCount = allMembers.count { !it.isActive || it.endDate < now }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memberships & Passes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = PrimaryBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Member Pass")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // 1. Metric Overview Counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricChip(
                    modifier = Modifier.weight(1f),
                    title = "Total Passes",
                    value = totalMembers.toString(),
                    color = PrimaryBlue
                )
                MetricChip(
                    modifier = Modifier.weight(1f),
                    title = "Active Passes",
                    value = activeMembersCount.toString(),
                    color = SuccessGreen
                )
                MetricChip(
                    modifier = Modifier.weight(1f),
                    title = "Expired",
                    value = expiredMembersCount.toString(),
                    color = if (expiredMembersCount > 0) ErrorRed else TextSecondary
                )
            }

            // 2. Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by Plate, Name, Phone, or ID...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryBlue) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = BorderLight
                )
            )

            // 3. Filter Tabs
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val tabs = listOf("ALL", "ACTIVE", "EXPIRING", "EXPIRED", "VIP")
                items(tabs) { tab ->
                    val isSelected = selectedFilterTab == tab
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilterTab = tab },
                        label = {
                            Text(
                                text = when (tab) {
                                    "ALL" -> "All ($totalMembers)"
                                    "ACTIVE" -> "Active ($activeMembersCount)"
                                    "EXPIRING" -> "Expiring Soon"
                                    "EXPIRED" -> "Expired ($expiredMembersCount)"
                                    "VIP" -> "VIP Only"
                                    else -> tab
                                },
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue.copy(alpha = 0.15f),
                            selectedLabelColor = PrimaryBlue
                        )
                    )
                }
            }

            // 4. Members List
            if (filteredMembers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "👑", fontSize = 48.sp)
                        Text(
                            text = if (searchQuery.isBlank()) "No member passes found" else "No results matching \"$searchQuery\"",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap the + button below to create a monthly or VIP parking pass.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredMembers, key = { it.id }) { member ->
                        MemberCard(
                            member = member,
                            onPrintCard = {
                                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                hardwareService.autoPrintMembershipPass(
                                    siteName = "Smart Parking Terminal",
                                    memberNo = member.memberNumber,
                                    memberName = member.fullName,
                                    phone = member.phone,
                                    vehicleNo = member.vehicleNumber,
                                    vehicleType = member.vehicleTypeId,
                                    planType = member.planType,
                                    validFrom = dateFormat.format(Date(member.startDate)),
                                    validUntil = dateFormat.format(Date(member.endDate)),
                                    amountPaid = member.feePaid,
                                    operatorName = "Pass Administrator"
                                ) {
                                    coroutineScope.launch {
                                        Toast.makeText(context, "Membership Pass Printed for ${member.fullName}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onCall = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${member.phone}"))
                                context.startActivity(intent)
                            },
                            onRenew = { memberToRenew = member },
                            onDelete = { memberToDelete = member }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    // Add Member Dialog
    if (showAddDialog) {
        AddEditMemberDialog(
            onDismiss = { showAddDialog = false },
            onSave = { newMember, autoPrint ->
                coroutineScope.launch {
                    database.membershipDao().insertMembership(newMember)
                    showAddDialog = false
                    Toast.makeText(context, "Member Pass Added: ${newMember.fullName}", Toast.LENGTH_SHORT).show()

                    if (autoPrint) {
                        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                        hardwareService.autoPrintMembershipPass(
                            siteName = "Smart Parking Terminal",
                            memberNo = newMember.memberNumber,
                            memberName = newMember.fullName,
                            phone = newMember.phone,
                            vehicleNo = newMember.vehicleNumber,
                            vehicleType = newMember.vehicleTypeId,
                            planType = newMember.planType,
                            validFrom = dateFormat.format(Date(newMember.startDate)),
                            validUntil = dateFormat.format(Date(newMember.endDate)),
                            amountPaid = newMember.feePaid,
                            operatorName = "Operator"
                        )
                    }
                }
            }
        )
    }

    // Renew Member Dialog
    memberToRenew?.let { member ->
        RenewMemberDialog(
            member = member,
            onDismiss = { memberToRenew = null },
            onConfirmRenew = { extensionDays, feePaid, paymentMethod ->
                coroutineScope.launch {
                    val baseStart = maxOf(System.currentTimeMillis(), member.endDate)
                    val newEndDate = baseStart + (extensionDays * 24 * 3600 * 1000L)
                    val updated = member.copy(
                        endDate = newEndDate,
                        isActive = true,
                        feePaid = feePaid,
                        paymentMethod = paymentMethod,
                        updatedAt = System.currentTimeMillis()
                    )
                    database.membershipDao().updateMembership(updated)
                    memberToRenew = null
                    Toast.makeText(context, "Pass Renewed for ${member.fullName}!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Delete Confirmation Dialog
    memberToDelete?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToDelete = null },
            title = { Text("Delete Membership Pass?") },
            text = { Text("Are you sure you want to permanently delete pass ${member.memberNumber} for ${member.fullName} (${member.vehicleNumber})?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            database.membershipDao().deleteMembership(member)
                            memberToDelete = null
                            Toast.makeText(context, "Pass deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MemberCard(
    member: MembershipEntity,
    onPrintCard: () -> Unit,
    onCall: () -> Unit,
    onRenew: () -> Unit,
    onDelete: () -> Unit
) {
    val now = System.currentTimeMillis()
    val isExpired = member.endDate < now || !member.isActive
    val daysRemaining = if (!isExpired) {
        ((member.endDate - now) / (24 * 3600 * 1000L)).toInt().coerceAtLeast(0)
    } else 0

    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (member.planType == "VIP_PASS") {
                PrimaryBlue.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Vehicle Plate + Status Badge
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
                        text = when (member.vehicleTypeId) {
                            "MOTORCYCLE" -> "🏍"
                            "VAN" -> "🚐"
                            "BUS" -> "🚌"
                            "TRUCK" -> "🚛"
                            else -> "🚗"
                        },
                        fontSize = 24.sp
                    )
                    Column {
                        Text(
                            text = member.vehicleNumber,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = member.fullName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isExpired) ErrorRed.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isExpired) "EXPIRED" else "$daysRemaining DAYS LEFT",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isExpired) ErrorRed else SuccessGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Details Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Plan", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(
                        text = member.planType.replace("_", " "),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (member.planType == "VIP_PASS") PrimaryBlue else MaterialTheme.colorScheme.onSurface
                    )
                }

                Column {
                    Text(text = "Pass No", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(text = member.memberNumber, style = MaterialTheme.typography.bodyMedium)
                }

                Column {
                    Text(text = "Valid Until", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(
                        text = dateFormat.format(Date(member.endDate)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPrintCard,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Print Card", fontSize = 12.sp)
                }

                Button(
                    onClick = onRenew,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Renew", fontSize = 12.sp)
                }

                IconButton(onClick = onCall, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Phone, contentDescription = "Call", tint = SuccessGreen)
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = ErrorRed)
                }
            }
        }
    }
}

@Composable
fun MetricChip(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMemberDialog(
    onDismiss: () -> Unit,
    onSave: (MembershipEntity, autoPrint: Boolean) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var vehicleNumber by remember { mutableStateOf("") }
    var selectedVehicleType by remember { mutableStateOf("CAR") }
    var selectedPlan by remember { mutableStateOf("MONTHLY") }
    var feePaid by remember { mutableStateOf("1500") }
    var autoPrintCard by remember { mutableStateOf(true) }

    val planDurations = mapOf(
        "MONTHLY" to Pair(30L, "1500"),
        "QUARTERLY" to Pair(90L, "4000"),
        "HALF_YEARLY" to Pair(180L, "7500"),
        "ANNUAL" to Pair(365L, "14000"),
        "VIP_PASS" to Pair(365L, "0")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Issue Membership Pass", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Customer Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = vehicleNumber,
                    onValueChange = { vehicleNumber = it.uppercase() },
                    label = { Text("Vehicle License Plate *") },
                    placeholder = { Text("e.g. BA 2 PA 1234") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Vehicle Type", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val types = listOf("CAR" to "🚗 Car", "MOTORCYCLE" to "🏍 Bike", "VAN" to "🚐 Van")
                    types.forEach { (typeId, label) ->
                        FilterChip(
                            selected = selectedVehicleType == typeId,
                            onClick = { selectedVehicleType = typeId },
                            label = { Text(label) }
                        )
                    }
                }

                Text("Membership Plan", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val plans = listOf("MONTHLY" to "1 Mo", "QUARTERLY" to "3 Mo", "ANNUAL" to "1 Yr", "VIP_PASS" to "VIP")
                    plans.forEach { (planId, label) ->
                        FilterChip(
                            selected = selectedPlan == planId,
                            onClick = {
                                selectedPlan = planId
                                feePaid = planDurations[planId]?.second ?: "0"
                            },
                            label = { Text(label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = feePaid,
                    onValueChange = { feePaid = it },
                    label = { Text("Fee Paid (NPR)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { autoPrintCard = !autoPrintCard }
                ) {
                    Checkbox(checked = autoPrintCard, onCheckedChange = { autoPrintCard = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto-print pass ticket on save", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fullName.isNotBlank() && vehicleNumber.isNotBlank() && phone.isNotBlank()) {
                        val durationDays = planDurations[selectedPlan]?.first ?: 30L
                        val now = System.currentTimeMillis()
                        val endDate = now + (durationDays * 24 * 3600 * 1000L)
                        val memberNumber = "MEM-" + SimpleDateFormat("yyyy", Locale.US).format(Date()) + "-" + (1000..9999).random()

                        val member = MembershipEntity(
                            id = UUID.randomUUID().toString(),
                            memberNumber = memberNumber,
                            fullName = fullName.trim(),
                            phone = phone.trim(),
                            vehicleNumber = vehicleNumber.trim().uppercase(Locale.US),
                            vehicleTypeId = selectedVehicleType,
                            planType = selectedPlan,
                            startDate = now,
                            endDate = endDate,
                            discountPercent = 100, // Free parking pass
                            feePaid = feePaid.ifBlank { "0.00" },
                            paymentMethod = if (selectedPlan == "VIP_PASS") "VIP_WAIVER" else "CASH",
                            isActive = true
                        )
                        onSave(member, autoPrintCard)
                    }
                },
                enabled = fullName.isNotBlank() && vehicleNumber.isNotBlank() && phone.isNotBlank()
            ) {
                Text("Issue Pass")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RenewMemberDialog(
    member: MembershipEntity,
    onDismiss: () -> Unit,
    onConfirmRenew: (days: Long, feePaid: String, paymentMethod: String) -> Unit
) {
    var selectedDays by remember { mutableLongStateOf(30L) }
    var feePaid by remember { mutableStateOf("1500") }
    var paymentMethod by remember { mutableStateOf("CASH") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renew Pass: ${member.fullName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Vehicle: ${member.vehicleNumber} | ID: ${member.memberNumber}")

                Text("Select Renewal Period", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val periods = listOf(30L to "1 Month (Rs 1500)", 90L to "3 Mo (Rs 4000)", 365L to "1 Year (Rs 14000)")
                    periods.forEach { (d, label) ->
                        FilterChip(
                            selected = selectedDays == d,
                            onClick = {
                                selectedDays = d
                                feePaid = when (d) {
                                    30L -> "1500"
                                    90L -> "4000"
                                    else -> "14000"
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = feePaid,
                    onValueChange = { feePaid = it },
                    label = { Text("Fee Paid (NPR)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirmRenew(selectedDays, feePaid, paymentMethod) }) {
                Text("Confirm Renewal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
