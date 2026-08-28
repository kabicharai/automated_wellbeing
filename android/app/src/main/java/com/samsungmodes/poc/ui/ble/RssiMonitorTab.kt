package com.samsungmodes.poc.ui.ble

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.samsungmodes.poc.ble.RssiTracker
import com.samsungmodes.poc.ui.MainViewModel

@Composable
fun RssiMonitorTab(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val snapshot = state.activeRssiSnapshot
    val target = state.savedProximityDevice ?: state.inspectedDevice?.let { dev ->
        com.samsungmodes.poc.ble.model.BleDeviceProfile(
            id = "preview",
            displayName = dev.name,
            deviceType = com.samsungmodes.poc.ble.model.BleProximityDevice.DeviceType.CUSTOM_BLE,
            deviceId = dev.deviceId
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Target Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "LIVE RSSI TELEMETRY (PHASE 1)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (state.scannerState.isScanning) "SCANNING" else "SCAN PAUSED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.scannerState.isScanning) Color(0xFF2E7D32) else Color(0xFFE65100)
                    )
                }

                Text(
                    text = target?.displayName ?: "No Device Selected",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = if (target != null) "Key: ${target.deviceId.primaryKey}" else "Select a device in the BLE Scanner tab to start real-time tracking.",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. Telemetry Stats Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                label = "CURRENT RSSI",
                value = snapshot.currentRssi?.let { "$it dBm" } ?: "--",
                highlightColor = snapshot.currentRssi?.let {
                    if (it >= -65) Color(0xFF2E7D32) else if (it >= -80) Color(0xFFF57F17) else Color(0xFFC62828)
                } ?: MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "AVG RSSI (WINDOW)",
                value = snapshot.averageRssi?.let { String.format("%.1f dBm", it) } ?: "--",
                highlightColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "MEDIAN (P50)",
                value = snapshot.medianRssi?.let { "$it dBm" } ?: "--",
                highlightColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                label = "STD DEV (σ)",
                value = snapshot.stdDev?.let { String.format("±%.1f dB", it) } ?: "--",
                highlightColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "MIN / MAX",
                value = if (snapshot.minRssi != null && snapshot.maxRssi != null) "${snapshot.minRssi} / ${snapshot.maxRssi}" else "--",
                highlightColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "PACKETS",
                value = "${snapshot.sampleCount} pts",
                highlightColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        // 3. Time-Window Selector Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RssiTracker.HistoryWindow.values().forEach { win ->
                val isSelected = state.selectedRssiWindow == win
                OutlinedButton(
                    onClick = { viewModel.setRssiHistoryWindow(win) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                    colors = if (isSelected) ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    ) else ButtonDefaults.outlinedButtonColors()
                ) {
                    Text(win.label, fontSize = 10.sp, maxLines = 1)
                }
            }
        }

        // 4. Live RSSI Waveform Graph
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("RSSI TIME SERIES WAVEFORM", fontSize = 10.sp, color = Color(0xFF90CAF9), fontWeight = FontWeight.Bold)
                    Text("Range: -100 dBm to -40 dBm", fontSize = 10.sp, color = Color(0xFFB0BEC5))
                }

                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    RssiCanvasGraph(
                        samples = snapshot.samples,
                        minDbm = -100f,
                        maxDbm = -40f,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    highlightColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = highlightColor, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun RssiCanvasGraph(
    samples: List<RssiTracker.RssiSample>,
    minDbm: Float,
    maxDbm: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // Draw horizontal gridlines at -50, -65, -80, -95 dBm
        val gridLevels = listOf(-50f, -65f, -80f, -95f)
        gridLevels.forEach { dbm ->
            val normY = (maxDbm - dbm) / (maxDbm - minDbm)
            val y = normY * height
            drawLine(
                color = Color(0xFF333333),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }

        if (samples.size < 2) return@Canvas

        val minTime = samples.first().timestampMillis
        val maxTime = samples.last().timestampMillis
        val timeSpan = (maxTime - minTime).coerceAtLeast(1L).toFloat()

        val path = Path()
        samples.forEachIndexed { index, sample ->
            val x = ((sample.timestampMillis - minTime) / timeSpan) * width
            val clampedDbm = sample.rssi.toFloat().coerceIn(minDbm, maxDbm)
            val normY = (maxDbm - clampedDbm) / (maxDbm - minDbm)
            val y = normY * height

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        // Draw live signal line
        drawPath(
            path = path,
            color = Color(0xFF64B5F6),
            style = Stroke(width = 3.dp.toPx())
        )

        // Draw point at latest sample
        val latest = samples.last()
        val latestX = width
        val latestY = ((maxDbm - latest.rssi.toFloat().coerceIn(minDbm, maxDbm)) / (maxDbm - minDbm)) * height
        drawCircle(
            color = Color(0xFF42A5F5),
            radius = 5.dp.toPx(),
            center = Offset(latestX, latestY)
        )
    }
}
