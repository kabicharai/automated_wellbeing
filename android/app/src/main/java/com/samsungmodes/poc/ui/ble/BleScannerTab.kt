package com.samsungmodes.poc.ui.ble

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsungmodes.poc.ble.BleScanner
import com.samsungmodes.poc.ble.model.BleDiscoveredDevice
import com.samsungmodes.poc.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleScannerTab(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val scanState = state.scannerState
    var filterText by remember { mutableStateOf("") }
    var filterOnlySmartTags by remember { mutableStateOf(false) }
    var deviceToInspect by remember { mutableStateOf<BleDiscoveredDevice?>(null) }

    val filteredDevices = remember(scanState.discoveredDevices, filterText, filterOnlySmartTags) {
        scanState.discoveredDevices.filter { dev ->
            val matchesQuery = dev.name.contains(filterText, ignoreCase = true) ||
                    dev.address.contains(filterText, ignoreCase = true) ||
                    dev.categoryLabel.contains(filterText, ignoreCase = true) ||
                    dev.deviceId.primaryKey.contains(filterText, ignoreCase = true)
            if (!matchesQuery) return@filter false
            if (filterOnlySmartTags && !dev.isSmartTagCandidate) return@filter false
            true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Scan Control Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "BLE SCANNER (PHASE 1)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            if (scanState.isScanning) "Status: SCANNING ACTIVE (${scanState.discoveredDevices.size} found)" else "Status: IDLE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (scanState.isScanning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (scanState.isScanning) {
                        Button(
                            onClick = { viewModel.stopBleScan() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("STOP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startBleScan() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("START SCAN", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Scan Mode Preferences (Balanced / Low Latency / Battery Saver)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BleScanner.ScanModePreference.values().forEach { mode ->
                        val isSelected = scanState.scanMode == mode
                        OutlinedButton(
                            onClick = { viewModel.setScanMode(mode) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                            colors = if (isSelected) ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            ) else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text(
                                when (mode) {
                                    BleScanner.ScanModePreference.BALANCED -> "Balanced"
                                    BleScanner.ScanModePreference.LOW_LATENCY -> "Low Latency"
                                    BleScanner.ScanModePreference.LOW_POWER -> "Battery Saver"
                                },
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // 2. Active Proximity Target Badge (if saved)
        state.savedProximityDevice?.let { saved ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1565C0),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "ACTIVE PROXIMITY TARGET",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFBBDEFB)
                        )
                        Text(
                            saved.displayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        saved.deviceType.name,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        modifier = Modifier
                            .background(Color(0xFF0D47A1), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // 3. Search & Filter Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = filterText,
                onValueChange = { filterText = it },
                placeholder = { Text("Filter (SmartTag, TV, MAC...)", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            OutlinedIconToggleButton(
                checked = filterOnlySmartTags,
                onCheckedChange = { filterOnlySmartTags = it },
                modifier = Modifier.height(48.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (filterOnlySmartTags) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                    Text("Only SmartTags", fontSize = 11.sp, fontWeight = if (filterOnlySmartTags) FontWeight.Bold else FontWeight.Normal)
                }
            }

            IconButton(onClick = { viewModel.clearDiscoveredDevices() }) {
                Icon(Icons.Default.Delete, contentDescription = "Clear List", tint = MaterialTheme.colorScheme.error)
            }
        }

        // 4. Discovered Devices List
        if (filteredDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (scanState.isScanning) "Searching for BLE beacons and SmartTags..." else "Press 'START SCAN' to discover nearby BLE devices.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (filterOnlySmartTags) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Filtering strictly for SmartTags (0xFD5A / 0xFD59 / SmartTag 1/2)",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredDevices, key = { it.deviceId.primaryKey }) { dev ->
                    val isTarget = state.savedProximityDevice?.deviceId?.primaryKey == dev.deviceId.primaryKey
                    val isInspected = state.inspectedDevice?.deviceId?.primaryKey == dev.deviceId.primaryKey

                    DeviceCardItem(
                        device = dev,
                        isTarget = isTarget,
                        isInspected = isInspected,
                        onInspect = {
                            viewModel.inspectDevice(dev)
                            deviceToInspect = dev
                        },
                        onSetTarget = { viewModel.saveAsProximityDevice(dev) }
                    )
                }
            }
        }
    }

    // Diagnostic Detail Modal
    deviceToInspect?.let { dev ->
        AlertDialog(
            onDismissRequest = { deviceToInspect = null },
            title = {
                Column {
                    Text(dev.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(dev.categoryLabel, fontSize = 11.sp, color = Color(dev.categoryBadgeColor), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("MAC Address: ${dev.formattedAddress}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text("Current RSSI: ${dev.currentRssi} dBm", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Packets Received: ${dev.totalSamples}", fontSize = 12.sp)

                    if (!dev.offlineFindingPrivacyId.isNullOrBlank()) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFE3F2FD), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(8.dp)) {
                                Text("Offline Finding Privacy ID:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
                                Text(dev.offlineFindingPrivacyId, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                if (!dev.tagStatus.isNullOrBlank()) {
                                    Text("Status: ${dev.tagStatus}", fontSize = 11.sp, color = Color(0xFF1565C0))
                                }
                            }
                        }
                    }

                    if (dev.advertisement.serviceUuids.isNotEmpty()) {
                        Text("Service UUIDs:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        dev.advertisement.serviceUuids.forEach { uuid ->
                            Text(uuid.toString(), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    if (dev.advertisement.manufacturerDataMap.isNotEmpty()) {
                        Text("Manufacturer Specific Data:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        dev.advertisement.getFormattedManufacturerList().forEach { (mfg, hex) ->
                            Text("$mfg:\n$hex", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveAsProximityDevice(dev)
                    deviceToInspect = null
                }) {
                    Text("Set as Target")
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToInspect = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun DeviceCardItem(
    device: BleDiscoveredDevice,
    isTarget: Boolean,
    isInspected: Boolean,
    onInspect: () -> Unit,
    onSetTarget: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onInspect() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTarget) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        border = if (isInspected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else null
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        device.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1
                    )

                    // Device Category Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(device.categoryBadgeColor)
                    ) {
                        Text(
                            if (device.isSmartTagCandidate) "SmartTag" else device.categoryLabel,
                            fontSize = 9.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }

                    if (isTarget) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF2E7D32)
                        ) {
                            Text(
                                "TARGET",
                                fontSize = 9.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // RSSI badge
                Text(
                    "${device.currentRssi} dBm",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = when {
                        device.currentRssi >= -65 -> Color(0xFF2E7D32)
                        device.currentRssi >= -80 -> Color(0xFFF57F17)
                        else -> Color(0xFFC62828)
                    }
                )
            }

            // Subtitle / Privacy ID info
            if (!device.offlineFindingPrivacyId.isNullOrBlank()) {
                Text(
                    "Find ID: ${device.offlineFindingPrivacyId.take(12)}... • ${device.tagStatus ?: "Offline Finding"}",
                    fontSize = 10.sp,
                    color = Color(0xFF0D47A1),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            } else if (device.subtitle.isNotBlank()) {
                Text(
                    device.subtitle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // MAC & samples info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${device.formattedAddress} • ${device.totalSamples} pkts",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    device.signalStrengthCategory,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onInspect,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Inspect", fontSize = 11.sp)
                }

                Button(
                    onClick = onSetTarget,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    colors = if (isTarget) ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    else ButtonDefaults.buttonColors()
                ) {
                    Text(if (isTarget) "Target Active" else "Set Target", fontSize = 11.sp)
                }
            }
        }
    }
}
