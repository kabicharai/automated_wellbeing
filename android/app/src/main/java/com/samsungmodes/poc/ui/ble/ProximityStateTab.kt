package com.samsungmodes.poc.ui.ble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsungmodes.poc.proximity.model.CandidateStatus
import com.samsungmodes.poc.proximity.model.ProximityState
import com.samsungmodes.poc.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProximityStateTab(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val proxSnapshot by viewModel.proximityEngine.snapshot.collectAsState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Header Banner & State Overview Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "PHASE 3: PROXIMITY ENGINE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Anti-Flapping State Machine",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        // Active State Badge
                        val stateColor = when (proxSnapshot.state) {
                            ProximityState.INSIDE -> Color(0xFF2E7D32)
                            ProximityState.OUTSIDE -> Color(0xFFC62828)
                            ProximityState.UNKNOWN -> Color(0xFF546E7A)
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = stateColor
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = when (proxSnapshot.state) {
                                        ProximityState.INSIDE -> Icons.Default.CheckCircle
                                        ProximityState.OUTSIDE -> Icons.Default.Cancel
                                        ProximityState.UNKNOWN -> Icons.Default.HelpOutline
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    proxSnapshot.state.label,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Candidate Confirmation Warning Banner
                    AnimatedVisibility(visible = proxSnapshot.candidateStatus != CandidateStatus.NONE) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (proxSnapshot.candidateStatus == CandidateStatus.ENTERING) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (proxSnapshot.candidateStatus == CandidateStatus.ENTERING) Color(0xFF2E7D32) else Color(0xFFC62828)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        if (proxSnapshot.candidateStatus == CandidateStatus.ENTERING) "VERIFYING ENTER CANDIDATE..." else "VERIFYING EXIT CANDIDATE...",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (proxSnapshot.candidateStatus == CandidateStatus.ENTERING) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                                    )
                                    Text(
                                        "${proxSnapshot.candidateElapsedSeconds}s / ${proxSnapshot.candidateTotalSeconds}s",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (proxSnapshot.candidateStatus == CandidateStatus.ENTERING) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { proxSnapshot.candidateProgressPercent },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (proxSnapshot.candidateStatus == CandidateStatus.ENTERING) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. 3-State Machine Architecture Diagram Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("STATE TRANSITION FLOW", fontWeight = FontWeight.Bold, fontSize = 12.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // OUTSIDE Node
                        StateNode(
                            title = "OUTSIDE",
                            subtitle = "< ${proxSnapshot.exitThreshold} dBm",
                            isActive = proxSnapshot.state == ProximityState.OUTSIDE,
                            activeColor = Color(0xFFC62828),
                            isCandidate = proxSnapshot.candidateStatus == CandidateStatus.EXITING
                        )

                        // Center Bridge (Hysteresis & Candidate)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "ENTER: ≥ ${proxSnapshot.enterThreshold} dBm (${proxSnapshot.enterDurationSeconds}s)",
                                fontSize = 9.sp,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold
                            )
                            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Text(
                                "EXIT: ≤ ${proxSnapshot.exitThreshold} dBm (${proxSnapshot.exitDurationSeconds}s)",
                                fontSize = 9.sp,
                                color = Color(0xFFC62828),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // INSIDE Node
                        StateNode(
                            title = "INSIDE",
                            subtitle = "≥ ${proxSnapshot.enterThreshold} dBm",
                            isActive = proxSnapshot.state == ProximityState.INSIDE,
                            activeColor = Color(0xFF2E7D32),
                            isCandidate = proxSnapshot.candidateStatus == CandidateStatus.ENTERING
                        )
                    }
                }
            }
        }

        // 3. Live Needle & Hysteresis Gauge Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("HYSTERESIS & SIGNAL GAUGE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                        Text(
                            "Confidence: ${proxSnapshot.confidencePercent}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (proxSnapshot.confidencePercent > 60) Color(0xFF4ADE80) else Color(0xFFFACC15)
                        )
                    }

                    // Needle Gauge Canvas
                    ProximityGaugeCanvas(
                        filteredRssi = proxSnapshot.currentFilteredRssi,
                        rawRssi = proxSnapshot.currentRawRssi,
                        enterThreshold = proxSnapshot.enterThreshold,
                        exitThreshold = proxSnapshot.exitThreshold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                    )

                    // Gauge Readout Values
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("FILTERED RSSI", fontSize = 9.sp, color = Color(0xFF94A3B8))
                            Text(
                                proxSnapshot.currentFilteredRssi?.let { "${"%.1f".format(it)} dBm" } ?: "--",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF38BDF8)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("HYSTERESIS DEADBAND", fontSize = 9.sp, color = Color(0xFF94A3B8))
                            Text(
                                "${proxSnapshot.exitThreshold} to ${proxSnapshot.enterThreshold} dBm",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFCBD5E1)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("RAW LAST RSSI", fontSize = 9.sp, color = Color(0xFF94A3B8))
                            Text(
                                proxSnapshot.currentRawRssi?.let { "$it dBm" } ?: "--",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }

        // 4. State Machine Event Log
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TRANSITION & CANDIDATE TIMELINE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        IconButton(onClick = { viewModel.proximityEngine.reset() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset Engine", modifier = Modifier.size(16.dp))
                        }
                    }

                    if (proxSnapshot.recentEvents.isEmpty()) {
                        Text("No transitions recorded yet. Feed RSSI samples to trigger.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            proxSnapshot.recentEvents.take(8).forEach { ev ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            timeFormat.format(Date(ev.timestampMillis)),
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "${ev.fromState} → ${ev.toState}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (ev.toState == ProximityState.INSIDE) Color(0xFF2E7D32) else if (ev.toState == ProximityState.OUTSIDE) Color(0xFFC62828) else Color(0xFF546E7A)
                                        )
                                        Text(
                                            ev.reason,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StateNode(
    title: String,
    subtitle: String,
    isActive: Boolean,
    activeColor: Color,
    isCandidate: Boolean
) {
    val bgColor by animateColorAsState(if (isActive) activeColor else Color(0xFFE2E8F0), label = "bg")
    val textColor = if (isActive) Color.White else Color(0xFF475569)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = if (isCandidate) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFF59E0B)) else null,
        modifier = Modifier.width(100.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = textColor)
            Text(subtitle, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = textColor.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun ProximityGaugeCanvas(
    filteredRssi: Double?,
    rawRssi: Int?,
    enterThreshold: Int,
    exitThreshold: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val minRssi = -100f
        val maxRssi = -40f
        val span = maxRssi - minRssi

        fun xForRssi(rssi: Float): Float {
            return ((rssi - minRssi) / span) * w
        }

        // Zone 1: OUTSIDE band (Red)
        val xExit = xForRssi(exitThreshold.toFloat())
        drawRect(
            color = Color(0xFFEF4444).copy(alpha = 0.25f),
            topLeft = Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(xExit, h)
        )

        // Zone 2: HYSTERESIS DEADBAND (Slate / Neutral)
        val xEnter = xForRssi(enterThreshold.toFloat())
        drawRect(
            color = Color(0xFF64748B).copy(alpha = 0.25f),
            topLeft = Offset(xExit, 0f),
            size = androidx.compose.ui.geometry.Size(xEnter - xExit, h)
        )

        // Zone 3: INSIDE band (Green)
        drawRect(
            color = Color(0xFF10B981).copy(alpha = 0.25f),
            topLeft = Offset(xEnter, 0f),
            size = androidx.compose.ui.geometry.Size(w - xEnter, h)
        )

        // EXIT Threshold Line
        drawLine(
            color = Color(0xFFEF4444),
            start = Offset(xExit, 0f),
            end = Offset(xExit, h),
            strokeWidth = 2f
        )

        // ENTER Threshold Line
        drawLine(
            color = Color(0xFF10B981),
            start = Offset(xEnter, 0f),
            end = Offset(xEnter, h),
            strokeWidth = 2f
        )

        // Filtered RSSI Needle (Cyan)
        if (filteredRssi != null) {
            val xFiltered = xForRssi(filteredRssi.toFloat()).coerceIn(0f, w)
            drawLine(
                color = Color(0xFF38BDF8),
                start = Offset(xFiltered, 0f),
                end = Offset(xFiltered, h),
                strokeWidth = 4f
            )
            drawCircle(
                color = Color(0xFF38BDF8),
                radius = 6f,
                center = Offset(xFiltered, h / 2f)
            )
        }
    }
}
