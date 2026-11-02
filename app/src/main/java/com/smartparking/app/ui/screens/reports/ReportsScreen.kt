@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartparking.app.ui.screens.reports

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartparking.app.data.local.SmartParkingDatabase
import com.smartparking.app.data.local.entity.ParkingSessionEntity
import com.smartparking.app.hardware.printer.UniversalHardwareService
import com.smartparking.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { SmartParkingDatabase.getInstance(context) }
    val hardwareService = remember { UniversalHardwareService.getInstance(context) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, PARKED, EXITED

    val allSessions by database.parkingSessionDao().getAllSessionsPaged(100, 0).collectAsState(initial = emptyList())

    val filteredSessions = remember(allSessions, searchQuery, selectedFilter) {
        allSessions.filter { session ->
            val matchesQuery = searchQuery.isBlank() ||
                    session.vehicleNumber.contains(searchQuery, ignoreCase = true) ||
                    session.ticketNumber.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "PARKED" -> session.status == "PARKED"
                "EXITED" -> session.status == "EXITED"
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    val currentlyParked = allSessions.count { it.status == "PARKED" }
    val totalExited = allSessions.count { it.status == "EXITED" }
    val totalRevenue = allSessions
        .filter { it.status == "EXITED" }
        .sumOf { it.netAmount.toDoubleOrNull() ?: 0.0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parking History & Reports", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // Summary Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Parked Now",
                    value = currentlyParked.toString(),
                    color = PrimaryBlue
                )
                MetricSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Exited Total",
                    value = totalExited.toString(),
                    color = SuccessGreen
                )
                MetricSummaryCard(
                    modifier = Modifier.weight(1.2f),
                    title = "Revenue (NPR)",
                    value = "%.0f".format(totalRevenue),
                    color = AmberWarning
                )
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by Plate or Ticket Number...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryBlue) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp)
            )

            // Filter Tabs
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filters = listOf("ALL", "PARKED", "EXITED")
                items(filters) { f ->
                    val isSelected = selectedFilter == f
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = f },
                        label = {
                            Text(
                                text = when (f) {
                                    "ALL" -> "All Sessions (${allSessions.size})"
                                    "PARKED" -> "Parked ($currentlyParked)"
                                    "EXITED" -> "Completed ($totalExited)"
                                    else -> f
                                },
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Sessions List
            if (filteredSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "No parking records recorded yet." else "No records matching \"$searchQuery\".",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredSessions, key = { it.id }) { session ->
                        SessionHistoryItem(
                            session = session,
                            onReprint = {
                                val dateFormat = SimpleDateFormat("dd MMM hh:mm a", Locale.getDefault())
                                if (session.status == "PARKED") {
                                    hardwareService.autoPrintEntryTicket(
                                        siteName = "Smart Parking Terminal",
                                        ticketNo = session.ticketNumber,
                                        vehicleNo = session.vehicleNumber,
                                        vehicleType = session.vehicleTypeId,
                                        entryTime = dateFormat.format(Date(session.entryTime)),
                                        zoneName = "Zone A",
                                        operatorName = "Operator",
                                        rateInfo = "Standard Rate"
                                    ) {
                                        coroutineScope.launch {
                                            Toast.makeText(context, "Ticket Reprinted: ${session.ticketNumber}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    val exitTimeStr = session.exitTime?.let { dateFormat.format(Date(it)) } ?: "N/A"
                                    hardwareService.autoPrintExitReceipt(
                                        siteName = "Smart Parking Terminal",
                                        receiptNo = "RCP-" + session.ticketNumber.takeLast(8),
                                        ticketNo = session.ticketNumber,
                                        vehicleNo = session.vehicleNumber,
                                        vehicleType = session.vehicleTypeId,
                                        entryTime = dateFormat.format(Date(session.entryTime)),
                                        exitTime = exitTimeStr,
                                        duration = "${session.durationMinutes ?: 0}m",
                                        parkingFee = session.grossAmount,
                                        discount = session.discountAmount,
                                        totalPaid = session.netAmount,
                                        paymentMethod = session.paymentMethod ?: "CASH",
                                        operatorName = "Exit Cashier"
                                    ) {
                                        coroutineScope.launch {
                                            Toast.makeText(context, "Receipt Reprinted: ${session.ticketNumber}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
fun SessionHistoryItem(
    session: ParkingSessionEntity,
    onReprint: () -> Unit
) {
    val isParked = session.status == "PARKED"
    val dateFormat = SimpleDateFormat("dd MMM hh:mm a", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = session.vehicleNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isParked) PrimaryBlue.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = session.status,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isParked) PrimaryBlue else SuccessGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ticket: ${session.ticketNumber} | ${session.vehicleTypeId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Entry: ${dateFormat.format(Date(session.entryTime))}" +
                            if (session.exitTime != null) " | Exit: ${dateFormat.format(Date(session.exitTime))}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (session.notes != null) {
                    Text(
                        text = "⭐ ${session.notes}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuccessGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!isParked) {
                    Text(
                        text = "NPR ${session.netAmount}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                }

                IconButton(onClick = onReprint, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Print, contentDescription = "Reprint", tint = PrimaryBlue)
                }
            }
        }
    }
}

@Composable
fun MetricSummaryCard(
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
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
