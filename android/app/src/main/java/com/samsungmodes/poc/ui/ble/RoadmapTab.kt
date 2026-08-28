package com.samsungmodes.poc.ui.ble

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RoadmapTab(modifier: Modifier = Modifier) {
    val phases = listOf(
        PhaseItem(
            phaseNumber = "PHASE 1",
            title = "BLE Scanner, Discovery & Live RSSI Buffer",
            status = "ACTIVE / IMPLEMENTED",
            isComplete = true,
            highlights = listOf(
                "Android 16 BluetoothLeScanner with BALANCED / LOW_LATENCY modes",
                "Galaxy SmartTag 1, iBeacon, & custom beacon classification",
                "Deterministic BleDeviceId identity (Manufacturer ID 0x0075 / UUID)",
                "Circular RSSI buffer with real-time stats (Avg, Median, Std Dev, Min/Max)"
            )
        ),
        PhaseItem(
            phaseNumber = "PHASE 2",
            title = "Noise Filtering & Distance Estimation",
            status = "PLANNED",
            isComplete = false,
            highlights = listOf(
                "Kalman Filter (1D) & Exponential Moving Average (EMA)",
                "Log-Distance Path Loss model (d = 10 ^ ((TxPower - RSSI) / (10 * n)))",
                "Environmental loss exponent tuning (Office: 2.8, Free space: 2.0)"
            )
        ),
        PhaseItem(
            phaseNumber = "PHASE 3",
            title = "Proximity State Machine & Hysteresis",
            status = "PLANNED",
            isComplete = false,
            highlights = listOf(
                "Dual-threshold hysteresis (Enter <= -72 dBm, Exit >= -82 dBm)",
                "Debounce timers preventing rapid mode flapping",
                "States: UNKNOWN, ENTERING, INSIDE_ZONE, EXITING, OUTSIDE_ZONE, LOST_SIGNAL"
            )
        ),
        PhaseItem(
            phaseNumber = "PHASE 4",
            title = "Bridge & Automation Controller",
            status = "PLANNED",
            isComplete = false,
            highlights = listOf(
                "Decoupled Bridge linking ProximityEngine to SamsungModeController",
                "One UI 8.0 & 8.5 target mode UUID activation on zone transitions",
                "Execution metrics and safety fallbacks"
            )
        ),
        PhaseItem(
            phaseNumber = "PHASE 5",
            title = "Foreground Service & Background Reliability",
            status = "PLANNED",
            isComplete = false,
            highlights = listOf(
                "Android Foreground Service with persistent notification",
                "Doze mode survival and auto-recovery on device reboot",
                "Samsung battery optimization bypass instructions"
            )
        ),
        PhaseItem(
            phaseNumber = "PHASE 6",
            title = "Settings, Profiles & Production Polishing",
            status = "PLANNED",
            isComplete = false,
            highlights = listOf(
                "Multi-beacon management & per-beacon mode UUID mapping",
                "Profile import/export, dark mode & logs export",
                "End-to-end verification checklist"
            )
        )
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "PROJECT BLUEPRINT ARCHITECTURE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Two-System Decoupled Engine",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "System A (BLE Proximity Engine) and System B (Samsung Mode Controller) operate independently and communicate strictly via ProximityAutomationController.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        items(phases.size) { index ->
            val phase = phases[index]
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (phase.isComplete) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            phase.phaseNumber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (phase.isComplete) Color(0xFF2E7D32) else Color(0xFF757575)
                        ) {
                            Text(
                                phase.status,
                                fontSize = 9.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        phase.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        phase.highlights.forEach { h ->
                            Text(
                                "• $h",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class PhaseItem(
    val phaseNumber: String,
    val title: String,
    val status: String,
    val isComplete: Boolean,
    val highlights: List<String>
)
