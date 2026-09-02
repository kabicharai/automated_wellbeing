package com.samsungmodes.poc.ui.ble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.samsungmodes.poc.proximity.model.AutomationEntryAction
import com.samsungmodes.poc.proximity.model.AutomationExitAction
import com.samsungmodes.poc.proximity.model.AutomationRule
import com.samsungmodes.poc.proximity.model.ProximityProfile
import com.samsungmodes.poc.proximity.model.ProximityState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationTab(
    automationState: ProximityAutomationController.AutomationState,
    automationRules: List<AutomationRule>,
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
    onSaveRule: (AutomationRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onToggleRule: (String, Boolean) -> Unit,
    onPause: (Int) -> Unit,
    onResume: () -> Unit,
    onEmergencyStop: () -> Unit,
    onReconcile: () -> Unit,
    onSelectDeviceProfile: (String) -> Unit,
    onResetAllData: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showRuleDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<AutomationRule?>(null) }
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
                        Column(modifier = Modifier.weight(1f)) {
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
                                    else -> "ACTIVE • Multi-Beacon Engine Evaluating Rules"
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

        // --- 3. LIVE PROXIMITY TELEMETRY BANNER ---
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
                            "Live Signal & Proximity Zone",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1
                        )
                        Spacer(Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when (proximityState) {
                                            ProximityState.INSIDE -> Color(0xFFDCFCE7)
                                            ProximityState.OUTSIDE -> Color(0xFFEFF6FF)
                                            ProximityState.UNKNOWN -> Color(0xFFF1F5F9)
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = proximityState.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (proximityState) {
                                        ProximityState.INSIDE -> Color(0xFF15803D)
                                        ProximityState.OUTSIDE -> Color(0xFF1D4ED8)
                                        ProximityState.UNKNOWN -> Color(0xFF64748B)
                                    }
                                )
                            }
                            Text(
                                text = "${filteredRssi.toInt()} dBm",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    if (activeProfile != null) {
                        Text(
                            "Tracked Beacon: ${activeProfile.targetDisplayName} (Enter < ${activeProfile.enterThresholdRssi} dBm, Exit < ${activeProfile.exitThresholdRssi} dBm)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // --- 4. MULTI-DEVICE / MULTI-MODE AUTOMATION RULES HEADER ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Automation Rules",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Multi-beacon triggers with custom entry & exit actions",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        editingRule = null
                        showRuleDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Rule", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // --- 5. AUTOMATION RULES LIST ---
        if (automationRules.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.Rule, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(36.dp))
                        Text("No Automation Rules Configured", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF334155))
                        Text(
                            "Create rules to turn modes ON or OFF automatically when walking near or leaving your BLE tags.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                editingRule = null
                                showRuleDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Create First Rule")
                        }
                    }
                }
            }
        } else {
            items(automationRules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    onToggle = { enabled -> onToggleRule(rule.id, enabled) },
                    onEdit = {
                        editingRule = rule
                        showRuleDialog = true
                    },
                    onDelete = { onDeleteRule(rule.id) }
                )
            }
        }

        // --- 6. MANUAL CONTROLS & OVERRIDES ---
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
                        "Automation Controls & Overrides",
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
                                Text("Resume Automation", fontSize = 12.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onPause(30) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Pause 30m", fontSize = 12.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = onReconcile,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reconcile Now", fontSize = 12.sp)
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
                        Text("EMERGENCY STOP (Disable & Turn Off)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // Reset Data Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Need to reset all configs?", fontSize = 12.sp, color = Color(0xFF64748B))
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

        // --- 7. TELEMETRY STATS ---
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

    // --- RULE BUILDER / EDIT DIALOG ---
    if (showRuleDialog) {
        RuleEditDialog(
            initialRule = editingRule,
            savedDevices = savedDevices,
            onDismiss = { showRuleDialog = false },
            onSave = { rule ->
                onSaveRule(rule)
                showRuleDialog = false
            }
        )
    }

    // --- RESET CONFIRMATION DIALOG ---
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset All Application Data?") },
            text = {
                Text("This will delete all saved BLE beacons, calibration profiles, automation rules, and return the app to initial state.")
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
}

@Composable
fun RuleCard(
    rule: AutomationRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (rule.isEnabled) Color.White else Color(0xFFF8FAFC)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (rule.isEnabled) Color(0xFFCBD5E1) else Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: Name & Enabled Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        rule.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (rule.isEnabled) Color(0xFF0F172A) else Color(0xFF94A3B8)
                    )
                    Text(
                        "Beacon: ${rule.deviceDisplayName.ifBlank { "Any BLE Beacon" }}",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF2563EB)
                    )
                )
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            // Action Pills Row: Inside -> Action, Outside -> Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Entry Action Pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when (rule.entryAction) {
                                AutomationEntryAction.TURN_ON -> Color(0xFFDCFCE7)
                                AutomationEntryAction.TURN_OFF -> Color(0xFFFEE2E2)
                                AutomationEntryAction.NONE -> Color(0xFFF1F5F9)
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column {
                        Text("ENTER (INSIDE)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                        Text(
                            rule.entryAction.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when (rule.entryAction) {
                                AutomationEntryAction.TURN_ON -> Color(0xFF15803D)
                                AutomationEntryAction.TURN_OFF -> Color(0xFFB91C1C)
                                AutomationEntryAction.NONE -> Color(0xFF64748B)
                            }
                        )
                    }
                }

                // Exit Action Pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when (rule.exitAction) {
                                AutomationExitAction.TURN_OFF -> Color(0xFFFEE2E2)
                                AutomationExitAction.TURN_ON -> Color(0xFFDCFCE7)
                                AutomationExitAction.RESTORE_PREVIOUS -> Color(0xFFEFF6FF)
                                AutomationExitAction.NONE -> Color(0xFFF1F5F9)
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column {
                        Text("EXIT (OUTSIDE)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                        Text(
                            rule.exitAction.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when (rule.exitAction) {
                                AutomationExitAction.TURN_OFF -> Color(0xFFB91C1C)
                                AutomationExitAction.TURN_ON -> Color(0xFF15803D)
                                AutomationExitAction.RESTORE_PREVIOUS -> Color(0xFF1D4ED8)
                                AutomationExitAction.NONE -> Color(0xFF64748B)
                            }
                        )
                    }
                }
            }

            // Mode Name & UUID Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Mode: ${rule.targetModeName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF334155)
                    )
                    Text(
                        "UUID: ${rule.targetModeUuid.take(18)}...",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF94A3B8)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Rule", tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Rule", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditDialog(
    initialRule: AutomationRule?,
    savedDevices: Map<String, com.samsungmodes.poc.ble.model.BleDeviceProfile>,
    onDismiss: () -> Unit,
    onSave: (AutomationRule) -> Unit
) {
    var name by remember { mutableStateOf(initialRule?.name ?: "Focus Zone Rule") }
    var selectedDeviceKey by remember { mutableStateOf(initialRule?.deviceKey ?: "") }
    var selectedDeviceName by remember {
        mutableStateOf(
            initialRule?.deviceDisplayName
                ?: if (selectedDeviceKey.isNotBlank()) savedDevices[selectedDeviceKey]?.displayName ?: "BLE Beacon" else "Any Beacon"
        )
    }
    var targetModeName by remember { mutableStateOf(initialRule?.targetModeName ?: "Focus Mode") }
    var targetModeUuid by remember { mutableStateOf(initialRule?.targetModeUuid ?: "") }
    var entryAction by remember { mutableStateOf(initialRule?.entryAction ?: AutomationEntryAction.TURN_ON) }
    var exitAction by remember { mutableStateOf(initialRule?.exitAction ?: AutomationExitAction.TURN_OFF) }
    var priority by remember { mutableStateOf(initialRule?.priority ?: 1) }

    val scrollState = rememberScrollState()
    val beaconScrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialRule == null) "Create Automation Rule" else "Edit Automation Rule",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Rule Name Field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Rule Name") },
                    placeholder = { Text("e.g. Work Desk Focus") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // BLE Device Selection
                Text(
                    "Trigger Beacon:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(beaconScrollState),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isAnySelected = selectedDeviceKey.isBlank() || selectedDeviceKey.equals("ANY", ignoreCase = true)
                    FilterChip(
                        selected = isAnySelected,
                        onClick = {
                            selectedDeviceKey = ""
                            selectedDeviceName = "Any Beacon"
                        },
                        label = { Text("Any Beacon", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                        leadingIcon = {
                            Icon(Icons.Default.AllInclusive, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )

                    savedDevices.values.forEach { dev ->
                        val isSelected = dev.deviceId.primaryKey == selectedDeviceKey
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedDeviceKey = dev.deviceId.primaryKey
                                selectedDeviceName = dev.displayName
                            },
                            label = { Text(dev.displayName, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                            leadingIcon = {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }

                // Samsung Mode Name & UUID
                OutlinedTextField(
                    value = targetModeName,
                    onValueChange = { targetModeName = it },
                    label = { Text("Mode Name") },
                    placeholder = { Text("e.g. Focus / Work") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = targetModeUuid,
                    onValueChange = { targetModeUuid = it.trim() },
                    label = { Text("Samsung Mode UUID") },
                    placeholder = { Text("Enter Mode UUID from POC / Modes app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Entry Action (INSIDE trigger)
                Text(
                    "When entering proximity (INSIDE):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = entryAction == AutomationEntryAction.TURN_ON,
                            onClick = { entryAction = AutomationEntryAction.TURN_ON },
                            label = {
                                Text(
                                    "Turn Mode ON",
                                    fontSize = 11.sp,
                                    fontWeight = if (entryAction == AutomationEntryAction.TURN_ON) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = entryAction == AutomationEntryAction.TURN_OFF,
                            onClick = { entryAction = AutomationEntryAction.TURN_OFF },
                            label = {
                                Text(
                                    "Turn Mode OFF",
                                    fontSize = 11.sp,
                                    fontWeight = if (entryAction == AutomationEntryAction.TURN_OFF) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    FilterChip(
                        selected = entryAction == AutomationEntryAction.NONE,
                        onClick = { entryAction = AutomationEntryAction.NONE },
                        label = {
                            Text(
                                "Do Nothing (Ignore Entry)",
                                fontSize = 11.sp,
                                fontWeight = if (entryAction == AutomationEntryAction.NONE) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Exit Action (OUTSIDE trigger)
                Text(
                    "When leaving proximity (OUTSIDE):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = exitAction == AutomationExitAction.TURN_OFF,
                            onClick = { exitAction = AutomationExitAction.TURN_OFF },
                            label = {
                                Text(
                                    "Turn Mode OFF",
                                    fontSize = 11.sp,
                                    fontWeight = if (exitAction == AutomationExitAction.TURN_OFF) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = exitAction == AutomationExitAction.TURN_ON,
                            onClick = { exitAction = AutomationExitAction.TURN_ON },
                            label = {
                                Text(
                                    "Turn Mode ON",
                                    fontSize = 11.sp,
                                    fontWeight = if (exitAction == AutomationExitAction.TURN_ON) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = exitAction == AutomationExitAction.RESTORE_PREVIOUS,
                            onClick = { exitAction = AutomationExitAction.RESTORE_PREVIOUS },
                            label = {
                                Text(
                                    "Restore Previous",
                                    fontSize = 11.sp,
                                    fontWeight = if (exitAction == AutomationExitAction.RESTORE_PREVIOUS) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = exitAction == AutomationExitAction.NONE,
                            onClick = { exitAction = AutomationExitAction.NONE },
                            label = {
                                Text(
                                    "Do Nothing",
                                    fontSize = 11.sp,
                                    fontWeight = if (exitAction == AutomationExitAction.NONE) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rule = (initialRule ?: AutomationRule(
                        id = UUID.randomUUID().toString(),
                        deviceKey = selectedDeviceKey,
                        targetModeUuid = targetModeUuid
                    )).copy(
                        name = name,
                        deviceKey = selectedDeviceKey,
                        deviceDisplayName = selectedDeviceName,
                        targetModeName = targetModeName,
                        targetModeUuid = targetModeUuid,
                        entryAction = entryAction,
                        exitAction = exitAction,
                        priority = priority
                    )
                    onSave(rule)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text("Save Rule")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
