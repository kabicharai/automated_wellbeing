package com.samsungmodes.poc.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.samsungmodes.poc.ble.BlePermissionHelper
import com.samsungmodes.poc.ui.ble.AutomationTab
import com.samsungmodes.poc.ui.ble.BleScannerTab
import com.samsungmodes.poc.ui.ble.RssiMonitorTab
import com.samsungmodes.poc.ui.ble.CalibrationTab
import com.samsungmodes.poc.ui.ble.ProximityStateTab
import com.samsungmodes.poc.ui.ble.RoadmapTab

enum class AppTab(val title: String) {
    AUTOMATION("Auto"),
    PROXIMITY("Proximity"),
    CALIBRATION("Calibrate"),
    RSSI_MONITOR("RSSI"),
    BLE_SCANNER("BLE"),
    SAMSUNG_MODES("Modes"),
    ROADMAP("Roadmap")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamsungModesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var selectedTab by remember { mutableStateOf(AppTab.AUTOMATION) }

    // Interactive Runtime Permission Request Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.checkPermissions()
    }

    // Auto-prompt runtime permissions on initial launch if not yet granted
    LaunchedEffect(Unit) {
        if (!state.permissionStatus.allGranted) {
            permissionLauncher.launch(BlePermissionHelper.getRequiredPermissions())
        }
    }

    // Auto scroll logs to bottom when new logs arrive
    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            listState.animateScrollToItem(state.logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Samsung Modes & BLE Proximity",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Phase 4: Proximity Automation & Samsung Modes Dispatcher",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { viewModel.refreshDiagnostics() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Diagnostics")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                AppTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            when (tab) {
                                AppTab.AUTOMATION -> Icon(Icons.Default.SmartToy, contentDescription = null)
                                AppTab.PROXIMITY -> Icon(Icons.Default.Sensors, contentDescription = null)
                                AppTab.CALIBRATION -> Icon(Icons.Default.Tune, contentDescription = null)
                                AppTab.RSSI_MONITOR -> Icon(Icons.Default.Timeline, contentDescription = null)
                                AppTab.BLE_SCANNER -> Icon(Icons.Default.Search, contentDescription = null)
                                AppTab.SAMSUNG_MODES -> Icon(Icons.Default.Settings, contentDescription = null)
                                AppTab.ROADMAP -> Icon(Icons.Default.List, contentDescription = null)
                            }
                        },
                        label = { Text(tab.title, fontSize = 9.sp, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Tab Body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    AppTab.AUTOMATION -> {
                        val proxSnapshot by viewModel.proximityEngine.snapshot.collectAsState()
                        AutomationTab(
                            automationState = state.automationState,
                            proximityState = proxSnapshot.state,
                            filteredRssi = proxSnapshot.filteredRssi,
                            confidencePercent = proxSnapshot.confidencePercent,
                            activeProfile = state.activeProximityProfile,
                            savedProfiles = state.savedProfiles,
                            savedDevices = state.savedDevices,
                            permissionStatus = state.permissionStatus,
                            onRequestPermissions = {
                                permissionLauncher.launch(BlePermissionHelper.getRequiredPermissions())
                            },
                            onOpenSettings = {
                                context.startActivity(BlePermissionHelper.openAppSettingsIntent(context))
                            },
                            onToggleMaster = { viewModel.toggleMasterAutomation(it) },
                            onSetModeUuid = { viewModel.setAutomationTargetMode(it) },
                            onPause = { viewModel.pauseAutomation(it) },
                            onResume = { viewModel.resumeAutomation() },
                            onEmergencyStop = { viewModel.emergencyStopAutomation() },
                            onReconcile = { viewModel.reconcileAutomation() },
                            onSelectDeviceProfile = { viewModel.selectDeviceProfile(it) },
                            onResetAllData = { viewModel.resetAllData() }
                        )
                    }
                    AppTab.BLE_SCANNER -> BleScannerTab(viewModel = viewModel)
                    AppTab.RSSI_MONITOR -> RssiMonitorTab(viewModel = viewModel)
                    AppTab.CALIBRATION -> CalibrationTab(viewModel = viewModel)
                    AppTab.PROXIMITY -> ProximityStateTab(viewModel = viewModel)
                    AppTab.ROADMAP -> RoadmapTab()
                    AppTab.SAMSUNG_MODES -> SamsungModesContent(viewModel = viewModel, state = state)
                }
            }

            // Collapsible Technical Log Drawer (Bottom)
            LogBottomSection(
                logs = state.logs,
                listState = listState,
                onCopy = {
                    val fullLog = state.logs.joinToString("\n") { "[${it.timestamp}] [${it.level}] ${it.message}" }
                    clipboardManager.setText(AnnotatedString(fullLog))
                    Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                onClear = { viewModel.clearLogs() }
            )
        }
    }
}

@Composable
private fun SamsungModesContent(
    viewModel: MainViewModel,
    state: MainUiState
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Device Info Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "DEVICE & ENVIRONMENT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    InfoRow(label = "DEVICE", value = state.deviceModel.ifEmpty { "Samsung Galaxy S23" })
                    InfoRow(label = "ANDROID", value = state.androidVersion.ifEmpty { "16 (SDK 36)" })
                    InfoRow(label = "ONE UI", value = state.oneUiVersion.ifEmpty { "Detecting..." })
                    InfoRow(label = "MODES & ROUTINES", value = "${state.routinesVersionName} (${state.routinesVersionCode})")
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("BACKEND", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (state.isSupported) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        ) {
                            Text(
                                text = state.selectedBackendName.ifEmpty { "Probing..." },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Backend quick-selector buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isV8 = state.selectedBackendName.contains("V8", ignoreCase = true) && !state.selectedBackendName.contains("V8.5") && !state.selectedBackendName.contains("Combined")
                        OutlinedButton(
                            onClick = { viewModel.selectBackend("V8") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            colors = if (isV8) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text("V8 Provider", fontSize = 10.sp, maxLines = 1)
                        }

                        val isV85 = state.selectedBackendName.contains("V8.5", ignoreCase = true)
                        OutlinedButton(
                            onClick = { viewModel.selectBackend("V85") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            colors = if (isV85) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text("V8.5 Shortcut", fontSize = 10.sp, maxLines = 1)
                        }

                        val isCombined = state.selectedBackendName.contains("Combined", ignoreCase = true)
                        OutlinedButton(
                            onClick = { viewModel.selectBackend("COMBINED") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            colors = if (isCombined) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text("Combined", fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        // 2. Mode UUID Input & Action Buttons
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "MODE UUID",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = state.modeUuid,
                        onValueChange = { viewModel.onUuidChanged(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. 550e8400-e29b-41d4-a716-446655440000") },
                        singleLine = true,
                        enabled = !state.isActionInProgress
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.startMode() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            enabled = !state.isActionInProgress
                        ) {
                            Text("START", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.stopMode() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            enabled = !state.isActionInProgress
                        ) {
                            Text("STOP", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.toggleMode() },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isActionInProgress
                        ) {
                            Text("TOGGLE")
                        }
                    }
                }
            }
        }

        // 3. Current Mode & Test Actions
        item {
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
                                "CURRENT MODE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (state.currentMode?.isModeActive == true)
                                    "Active: ${state.currentMode?.activeModeUuid}"
                                else
                                    "Not active / Not exposed",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Button(
                            onClick = { viewModel.readCurrentMode() },
                            enabled = !state.isActionInProgress
                        ) {
                            Text("READ CURRENT")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Button(
                        onClick = { viewModel.runFullTest() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        enabled = !state.isActionInProgress
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("RUN FULL TEST (7-STEP VERIFICATION)", fontWeight = FontWeight.Bold)
                    }

                    // Test Outcome Display if applicable
                    when (val testState = state.testState) {
                        is FullTestState.Running -> {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = testState.stepDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is FullTestState.Completed -> {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (testState.outcome) {
                                    "PASS" -> Color(0xFFE8F5E9)
                                    "PARTIAL PASS" -> Color(0xFFFFF3E0)
                                    else -> Color(0xFFFFEBEE)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "RESULT: ${testState.outcome}",
                                        fontWeight = FontWeight.Bold,
                                        color = when (testState.outcome) {
                                            "PASS" -> Color(0xFF2E7D32)
                                            "PARTIAL PASS" -> Color(0xFFE65100)
                                            else -> Color(0xFFC62828)
                                        }
                                    )
                                    Text(
                                        text = testState.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                        FullTestState.Idle -> {}
                    }
                }
            }
        }

        // 4. Diagnostics Section
        item {
            val rep = state.report
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "DIAGNOSTICS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    DiagRow("Modes package", if (rep?.packageInstalled == true) "FOUND" else "NOT FOUND", rep?.packageInstalled == true)
                    DiagRow("Shortcut activity", if (rep?.shortcutActivityFound == true) "FOUND" else "NOT FOUND", rep?.shortcutActivityFound == true)
                    DiagRow("Shortcut activity exported", if (rep?.shortcutActivityExported == true) "YES" else "NO", rep?.shortcutActivityExported == true)
                    DiagRow("Legacy provider", if (rep?.legacyProviderFound == true) "FOUND" else "NOT FOUND", rep?.legacyProviderFound == true)
                    DiagRow("Legacy provider accessible", if (rep?.legacyProviderAccessible == true) "YES" else "NO", rep?.legacyProviderAccessible == true)
                    DiagRow("Selected backend", state.selectedBackendName, state.isSupported)
                }
            }
        }
    }
}

@Composable
private fun LogBottomSection(
    logs: List<UiLogEntry>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onCopy: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "SYSTEM LOGS",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onCopy,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("COPY", fontSize = 10.sp, color = Color(0xFF90CAF9))
                }

                TextButton(
                    onClick = onClear,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("CLEAR", fontSize = 10.sp, color = Color(0xFFEF9A9A))
                }
            }
        }

        HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 2.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(logs) { entry ->
                val logColor = when (entry.level) {
                    "SUCCESS" -> Color(0xFF81C784)
                    "WARN" -> Color(0xFFFFB74D)
                    "ERROR" -> Color(0xFFE57373)
                    "BLE" -> Color(0xFF64B5F6)
                    "ACTION" -> Color(0xFF4FC3F7)
                    "TEST" -> Color(0xFFBA68C8)
                    else -> Color(0xFFE0E0E0)
                }
                Text(
                    text = "[${entry.timestamp}] [${entry.level}] ${entry.message}",
                    color = logColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(vertical = 0.5.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DiagRow(label: String, value: String, isPositive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (isPositive) Color(0xFF2E7D32) else Color(0xFFC62828)
        )
    }
}
