package com.samsungmodes.poc.ui.ble

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsungmodes.poc.proximity.CalibrationEngine
import com.samsungmodes.poc.proximity.model.RssiDistributionMetrics
import com.samsungmodes.poc.ui.MainViewModel

@Composable
fun CalibrationTab(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val calState by viewModel.calibrationEngine.state.collectAsState()
    val savedTarget = state.savedProximityDevice

    var selectedDurationSec by remember { mutableStateOf(30) }
    var customEnterThreshold by remember { mutableStateOf<Int?>(null) }
    var customExitThreshold by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Calibration Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "PHASE 2: DUAL-ZONE CALIBRATION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Empirical RSSI Profile Calibration",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        "Collect 30s samples in OUTSIDE and INSIDE zones to calculate optimal hysteresis thresholds with zero guess-work.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Target Beacon Selector (Multi-Device Support)
                    if (state.savedDevices.isNotEmpty()) {
                        Text(
                            "SELECT BEACON TO CALIBRATE:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.savedDevices.values.forEach { dev ->
                                val isSelected = (savedTarget?.deviceId?.primaryKey == dev.deviceId.primaryKey)
                                val hasCal = state.savedProfiles.containsKey(dev.deviceId.primaryKey)
                                val prof = state.savedProfiles[dev.deviceId.primaryKey]

                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectDeviceProfile(dev.deviceId.primaryKey) },
                                    label = {
                                        Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                            Text(
                                                dev.displayName,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                if (hasCal && prof != null) "Calibrated (${prof.enterThresholdRssi} / ${prof.exitThresholdRssi} dBm)" else "Uncalibrated",
                                                fontSize = 9.sp,
                                                color = if (hasCal) Color(0xFF2E7D32) else Color(0xFFE65100)
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (hasCal) Icons.Default.CheckCircle else Icons.Default.Info,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = if (hasCal) Color(0xFF2E7D32) else Color(0xFFE65100)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Target Device Status
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (savedTarget != null) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "ACTIVE CALIBRATION TARGET",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (savedTarget != null) Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )
                                    Text(
                                        savedTarget?.displayName ?: "No target saved (Go to BLE Scanner tab first)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (savedTarget != null) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                                    )
                                }
                                if (savedTarget != null) {
                                    Text(
                                        savedTarget.formattedTarget(),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }

                            val activeProf = state.savedProfiles[savedTarget?.deviceId?.primaryKey]
                            if (activeProf != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        "Current Saved Profile: Enter ≥ ${activeProf.enterThresholdRssi} dBm • Exit ≤ ${activeProf.exitThresholdRssi} dBm",
                                        fontSize = 10.sp,
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Local Offline Backup / Restore Buttons (Survives Uninstalls)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.exportConfigBackup() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("EXPORT BACKUP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.restoreConfigBackup() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("RESTORE BACKUP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. Step 1 — Outside Calibration Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (calState.step) {
                        CalibrationEngine.Step.RECORDING_OUTSIDE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        CalibrationEngine.Step.OUTSIDE_COMPLETE, CalibrationEngine.Step.RECORDING_INSIDE, CalibrationEngine.Step.CALIBRATION_READY -> Color(0xFFF1F8E9)
                        else -> MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (calState.outsideMetrics != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    "1",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Text("STEP 1: OUTSIDE CALIBRATION", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        if (calState.outsideMetrics != null) {
                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF2E7D32)) {
                                Text("COMPLETED", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }

                    Text(
                        "Go to the OUTSIDE location (e.g. hallway, living room). Hold phone normally.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (calState.step == CalibrationEngine.Step.RECORDING_OUTSIDE) {
                        LinearProgressIndicator(
                            progress = { (selectedDurationSec - calState.countdownSecondsRemaining).toFloat() / selectedDurationSec },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Recording: ${calState.outsideSamples.size} samples", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${calState.countdownSecondsRemaining}s remaining", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    calState.outsideMetrics?.let { m ->
                        DistributionSummaryView(metrics = m, zoneLabel = "Outside (Far)")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (calState.step == CalibrationEngine.Step.RECORDING_OUTSIDE) {
                            Button(
                                onClick = { viewModel.calibrationEngine.cancelCalibration() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Cancel", fontSize = 11.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (savedTarget != null) {
                                        viewModel.startOutsideCalibration(savedTarget.deviceId.primaryKey, savedTarget.displayName, selectedDurationSec)
                                    }
                                },
                                enabled = savedTarget != null && calState.step != CalibrationEngine.Step.RECORDING_INSIDE
                            ) {
                                Text(if (calState.outsideMetrics == null) "START OUTSIDE (30s)" else "RE-CALIBRATE OUTSIDE", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // 3. Step 2 — Inside Calibration Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (calState.step) {
                        CalibrationEngine.Step.RECORDING_INSIDE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        CalibrationEngine.Step.CALIBRATION_READY -> Color(0xFFE8F5E9)
                        else -> MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (calState.insideMetrics != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    "2",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Text("STEP 2: INSIDE CALIBRATION", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        if (calState.insideMetrics != null) {
                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF2E7D32)) {
                                Text("COMPLETED", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }

                    Text(
                        "Go to the INSIDE location (e.g. desk, bed). Hold or place phone normally.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (calState.step == CalibrationEngine.Step.RECORDING_INSIDE) {
                        LinearProgressIndicator(
                            progress = { (selectedDurationSec - calState.countdownSecondsRemaining).toFloat() / selectedDurationSec },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Recording: ${calState.insideSamples.size} samples", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${calState.countdownSecondsRemaining}s remaining", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    calState.insideMetrics?.let { m ->
                        DistributionSummaryView(metrics = m, zoneLabel = "Inside (Close)")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (calState.step == CalibrationEngine.Step.RECORDING_INSIDE) {
                            Button(
                                onClick = { viewModel.calibrationEngine.cancelCalibration() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Cancel", fontSize = 11.sp)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.startInsideCalibration(selectedDurationSec) },
                                enabled = calState.outsideMetrics != null && calState.step != CalibrationEngine.Step.RECORDING_OUTSIDE
                            ) {
                                Text(if (calState.insideMetrics == null) "START INSIDE (30s)" else "RE-CALIBRATE INSIDE", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // 4. Threshold & Separation Analysis Card
        calState.calculationResult?.let { res ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(res.quality.colorHex))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SEPARATION & THRESHOLD ANALYSIS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Surface(shape = RoundedCornerShape(4.dp), color = Color(res.quality.colorHex)) {
                                Text(
                                    res.quality.label,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(res.summaryNotes, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)

                        // Visual Comparison Bars
                        DualZoneDistributionCanvas(
                            outside = calState.outsideMetrics,
                            inside = calState.insideMetrics,
                            enterThreshold = customEnterThreshold ?: res.suggestedEnterThreshold,
                            exitThreshold = customExitThreshold ?: res.suggestedExitThreshold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        )

                        // Thresholds Summary Box
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE8F5E9),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("SUGGESTED ENTER", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    Text("${res.suggestedEnterThreshold} dBm", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                                    Text("Hysteresis high", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFFEBEE),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("SUGGESTED EXIT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                                    Text("${res.suggestedExitThreshold} dBm", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C))
                                    Text("Hysteresis low", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE3F2FD),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("DELTA SPREAD", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                                    Text("${"%.1f".format(res.medianSeparationDb)} dB", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
                                    Text("Median gap", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        // Apply to Proximity Profile
                        Button(
                            onClick = {
                                calState.calibratedProfile?.let { prof ->
                                    viewModel.saveCalibratedProfile(
                                        prof.copy(
                                            enterThresholdRssi = customEnterThreshold ?: res.suggestedEnterThreshold,
                                            exitThresholdRssi = customExitThreshold ?: res.suggestedExitThreshold
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("SAVE CALIBRATED PROXIMITY PROFILE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DistributionSummaryView(metrics: RssiDistributionMetrics, zoneLabel: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(zoneLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("${metrics.sampleCount} samples (${metrics.durationSeconds}s)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Median: ${"%.1f".format(metrics.medianRssi)} dBm", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("Mean: ${"%.1f".format(metrics.meanRssi)} dBm", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("StdDev: ±${"%.1f".format(metrics.standardDeviation)} dB", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("p10: ${"%.0f".format(metrics.p10)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("p25: ${"%.0f".format(metrics.p25)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("p75: ${"%.0f".format(metrics.p75)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("p90: ${"%.0f".format(metrics.p90)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DualZoneDistributionCanvas(
    outside: RssiDistributionMetrics?,
    inside: RssiDistributionMetrics?,
    enterThreshold: Int,
    exitThreshold: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.background(Color(0xFF1E293B), RoundedCornerShape(8.dp))) {
        val w = size.width
        val h = size.height
        val minRssi = -100f
        val maxRssi = -40f
        val span = maxRssi - minRssi

        fun xForRssi(rssi: Float): Float {
            return ((rssi - minRssi) / span) * w
        }

        // Draw grid lines
        for (rssi in -90..-40 step 10) {
            val x = xForRssi(rssi.toFloat())
            drawLine(
                color = Color(0xFF334155),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1f
            )
        }

        // Outside Distribution (Red span)
        if (outside != null) {
            val xMin = xForRssi(outside.p10.toFloat())
            val xMax = xForRssi(outside.p90.toFloat())
            val xMed = xForRssi(outside.medianRssi.toFloat())

            drawRect(
                color = Color(0xFFEF4444).copy(alpha = 0.35f),
                topLeft = Offset(xMin, h * 0.55f),
                size = androidx.compose.ui.geometry.Size(xMax - xMin, h * 0.35f)
            )
            drawLine(
                color = Color(0xFFEF4444),
                start = Offset(xMed, h * 0.5f),
                end = Offset(xMed, h * 0.95f),
                strokeWidth = 3f
            )
        }

        // Inside Distribution (Green span)
        if (inside != null) {
            val xMin = xForRssi(inside.p10.toFloat())
            val xMax = xForRssi(inside.p90.toFloat())
            val xMed = xForRssi(inside.medianRssi.toFloat())

            drawRect(
                color = Color(0xFF10B981).copy(alpha = 0.35f),
                topLeft = Offset(xMin, h * 0.1f),
                size = androidx.compose.ui.geometry.Size(xMax - xMin, h * 0.35f)
            )
            drawLine(
                color = Color(0xFF10B981),
                start = Offset(xMed, h * 0.05f),
                end = Offset(xMed, h * 0.5f),
                strokeWidth = 3f
            )
        }

        // ENTER Threshold vertical line (Yellow/Green)
        val xEnter = xForRssi(enterThreshold.toFloat())
        drawLine(
            color = Color(0xFFFBBF24),
            start = Offset(xEnter, 0f),
            end = Offset(xEnter, h),
            strokeWidth = 2f
        )

        // EXIT Threshold vertical line (Orange)
        val xExit = xForRssi(exitThreshold.toFloat())
        drawLine(
            color = Color(0xFFFB923C),
            start = Offset(xExit, 0f),
            end = Offset(xExit, h),
            strokeWidth = 2f
        )
    }
}

private fun com.samsungmodes.poc.ble.model.BleDeviceProfile.formattedTarget(): String {
    return targetMacAddress ?: deviceId.primaryKey
}
