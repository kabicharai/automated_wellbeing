package com.samsungmodes.poc.ui.ble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsungmodes.poc.ble.BlePermissionHelper
import com.samsungmodes.poc.proximity.automation.ProximityAutomationController
import com.samsungmodes.poc.proximity.model.ProximityProfile
import com.samsungmodes.poc.proximity.model.ProximityState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationTab(
    automationState: ProximityAutomationController.AutomationState,
    proximityState: ProximityState,
    filteredRssi: Double,
    confidencePercent: Int,
    activeProfile: ProximityProfile?,
    savedProfiles: Map<String, ProximityProfile>,
    savedDevices: Map<String, com.samsungmodes.poc.ble.model.BleDeviceProfile>,
    permissionStatus: BlePermissionHelper.PermissionStatus,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleMaster: (Boolean) -> Unit,
    onSetModeUuid: (String) -> Unit,
    onPause: (Int) -> Unit,
    onResume: () -> Unit,
    onEmergencyStop: () -> Unit,
    onReconcile: () -> Unit,
    onSelectDeviceProfile: (String) -> Unit,
    onResetAllData: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showUuidEditDialog by remember { mutableStateOf(false) }
    var tempUuid by remember(automationState.targetModeUuid) { mutableStateOf(automationState.targetModeUuid) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. RUNTIME PERMISSIONS BANNER ---
        if (!permissionStatus.allGranted) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFFFB74D), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Permissions Required",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100),
                                fontSize = 15.sp
                            )
                        }
                        Text(
                            "BLE scanning, background proximity monitoring, and accurate RSSI estimation require Bluetooth and Location permissions.",
                            fontSize = 13.sp,
                            color = Color(0xFF5D4037)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onRequestPermissions,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Grant All Permissions", fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = onOpenSettings,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE65100))
                            ) {
                                Text("Settings", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // --- 2. MASTER AUTOMATION SWITCH CARD ---
        item {
            val isEnabled = automationState.masterEnabled
            val isPaused = automationState.isCurrentlyPaused
            val activeBg by animateColorAsState(
                targetValue = when {
                    !isEnabled -> Color(0xFFF1F5F9)
                    isPaused -> Color(0xFFFFFBEB)
                    else -> Color(0xFFF0FDF4)
                },
                label = "automationBg"
            )
            val borderClr = when {
                !isEnabled -> Color(0xFFCBD5E1)
                isPaused -> Color(0xFFFDE68A)
                else -> Color(0xFF86EFAC)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = activeBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, borderClr, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Master Proximity Automation",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = when {
                                    !isEnabled -> "Automation is OFF • No Samsung Modes will be triggered"
                                    isPaused -> "Paused (${automationState.pauseRemainingSeconds}s remaining)"
                                    else -> "ACTIVE • Dispatching Samsung Mode commands automatically"
                                },
                                fontSize = 12.sp,
                                color = when {
                                    !isEnabled -> Color(0xFF64748B)
                                    isPaused -> Color(0xFFB45309)
                                    else -> Color(0xFF15803D)
                                }
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = onToggleMaster,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF16A34A)
                            )
                        )
                    }

                    HorizontalDivider(color = Color(0x15000000))

                    // Status Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (automationState.executionState) {
                                            ProximityAutomationController.ExecutionState.START_SUCCESS,
                                            ProximityAutomationController.ExecutionState.IDLE -> Color(0xFF16A34A)
                                            ProximityAutomationController.ExecutionState.TRIGGERING_START,
                                            ProximityAutomationController.ExecutionState.TRIGGERING_STOP,
                                            ProximityAutomationController.ExecutionState.RETRYING -> Color(0xFFEAB308)
                                            ProximityAutomationController.ExecutionState.PAUSED -> Color(0xFFF59E0B)
                                            ProximityAutomationController.ExecutionState.ERROR -> Color(0xFFDC2626)
                                            ProximityAutomationController.ExecutionState.DISABLED -> Color(0xFF94A3B8)
                                            ProximityAutomationController.ExecutionState.STOP_SUCCESS -> Color(0xFF3B82F6)
                                        }
                                    )
                            )
                            Text(
                                text = automationState.executionState.displayName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B)
                            )
                        }

                        if (automationState.lastActionTimestampMillis > 0) {
                            Text(
                                "Last: ${timeFormat.format(Date(automationState.lastActionTimestampMillis))}",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // --- 3. PROXIMITY -> SAMSUNG MODE PIPELINE ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Proximity → Samsung Mode Pipeline",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF1E293B)
                    )

                    // Pipeline Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Node 1: Beacon
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEFF6FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(activeProfile?.targetDisplayName ?: "SmartTag", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text("${filteredRssi.toInt()} dBm", fontSize = 10.sp, color = Color(0xFF64748B))
                        }

                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))

                        // Node 2: State Engine
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (proximityState) {
                                            ProximityState.INSIDE -> Color(0xFFDCFCE7)
                                            ProximityState.OUTSIDE -> Color(0xFFEFF6FF)
                                            ProximityState.UNKNOWN -> Color(0xFFF1F5F9)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    when (proximityState) {
                                        ProximityState.INSIDE -> "IN"
                                        ProximityState.OUTSIDE -> "OUT"
                                        ProximityState.UNKNOWN -> "?"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (proximityState) {
                                        ProximityState.INSIDE -> Color(0xFF15803D)
                                        ProximityState.OUTSIDE -> Color(0xFF1D4ED8)
                                        ProximityState.UNKNOWN -> Color(0xFF64748B)
                                    }
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(proximityState.name, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text("$confidencePercent% conf", fontSize = 10.sp, color = Color(0xFF64748B))
                        }

                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))

                        // Node 3: Samsung Mode
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (proximityState == ProximityState.INSIDE && automationState.masterEnabled) Color(0xFFEDE9FE) else Color(0xFFF1F5F9)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = if (proximityState == ProximityState.INSIDE && automationState.masterEnabled) Color(0xFF7C3AED) else Color(0xFF64748B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(automationState.targetModeName.ifBlank { "Mode" }, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (proximityState == ProximityState.INSIDE && automationState.masterEnabled) "START (ON)" else "STOP (OFF)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (proximityState == ProximityState.INSIDE && automationState.masterEnabled) Color(0xFF7C3AED) else Color(0xFF64748B)
                            )
                        }
                    }
                }
            }
        }

        // --- 4. TARGET SAMSUNG MODE BINDING ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Target Samsung Mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1E293B)
                        )
                        IconButton(onClick = { showUuidEditDialog = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit UUID", modifier = Modifier.size(16.dp), tint = Color(0xFF2563EB))
                        }
                    }

                    if (automationState.targetModeUuid.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    automationState.targetModeName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    automationState.targetModeUuid,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Text(
                            "No Samsung Mode UUID configured. Tap edit or select a mode to bind.",
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626)
                        )
                    }

                    // Quick Mode Presets
                    Text("Quick Presets / Known Samsung Routines:", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "Bedroom Focus" to "routine-bedroom-focus-01",
                            "Work / Study" to "routine-work-mode-02",
                            "Sleep / Relax" to "routine-sleep-relax-03"
                        ).forEach { (name, uuid) ->
                            SuggestionChip(
                                onClick = { onSetModeUuid(uuid) },
                                label = { Text(name, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        // --- 5. SAFETY & OVERRIDE CONTROLS ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Automation Overrides & Safety",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF1E293B)
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (automationState.isCurrentlyPaused) {
                            Button(
                                onClick = onResume,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Resume", fontSize = 12.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onPause(15) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Pause 15m", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { onPause(60) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Pause 1h", fontSize = 11.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = onReconcile,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reconcile", fontSize = 11.sp)
                        }
                    }

                    // Emergency Stop Button
                    Button(
                        onClick = onEmergencyStop,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("EMERGENCY STOP (Stop Mode & Disable)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- 6. PER-DEVICE SAVED PROFILES & PERSISTENCE ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Per-Device Calibration Profiles (${savedProfiles.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            "Persisted in Storage",
                            fontSize = 11.sp,
                            color = Color(0xFF15803D),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (savedProfiles.isEmpty()) {
                        Text(
                            "No calibrated profiles saved yet. Calibrate a beacon in the Calibration tab to save its profile.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    } else {
                        savedProfiles.values.forEach { profile ->
                            val isSelected = activeProfile?.targetDeviceId?.primaryKey == profile.targetDeviceId.primaryKey
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC))
                                    .border(1.dp, if (isSelected) Color(0xFF93C5FD) else Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                    .clickable { onSelectDeviceProfile(profile.targetDeviceId.primaryKey) }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        profile.profileName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        "Device: ${profile.targetDisplayName} • ENTER: ${profile.enterThresholdRssi} dBm, EXIT: ${profile.exitThresholdRssi} dBm",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                if (isSelected) {
                                    Badge(containerColor = Color(0xFF2563EB)) {
                                        Text("ACTIVE", color = Color.White, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // Reset All Data Option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Need to start fresh?", fontSize = 12.sp, color = Color(0xFF64748B))
                        TextButton(
                            onClick = { showResetDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reset All Saved Data", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // --- 7. AUTOMATION TELEMETRY STATS ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${automationState.totalTransitionsHandled}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text("Evaluated", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${automationState.successfulInvocations}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                        Text("Success", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${automationState.failedInvocations}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                        Text("Failed", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
        }
    }

    // --- RESET CONFIRMATION DIALOG ---
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset All Application Data?") },
            text = {
                Text("This will delete all saved BLE beacons, per-device calibration profiles, thresholds, and automation settings. The app will return to initial default state.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetAllData()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Confirm Reset")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- UUID EDIT DIALOG ---
    if (showUuidEditDialog) {
        AlertDialog(
            onDismissRequest = { showUuidEditDialog = false },
            title = { Text("Configure Target Samsung Mode UUID") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the UUID of the Samsung Mode / Routine to control:", fontSize = 13.sp)
                    OutlinedTextField(
                        value = tempUuid,
                        onValueChange = { tempUuid = it },
                        label = { Text("Mode UUID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSetModeUuid(tempUuid)
                    showUuidEditDialog = false
                }) {
                    Text("Save UUID")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUuidEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
