package com.samsungmodes.poc.proximity.model

import com.samsungmodes.poc.ble.model.BleDeviceId
import com.samsungmodes.poc.ble.model.BleDeviceProfile
import java.util.UUID

/**
 * Smoothing filters available for raw RSSI sample processing.
 */
enum class RssiFilterType(val displayName: String, val description: String) {
    EMA("Exponential Moving Average", "Weighted toward recent samples with alpha smoothing factor"),
    RUNNING_MEDIAN("Running Median Window", "Resistant to extreme transient multipath spikes and noise"),
    KALMAN("1D Kalman Filter", "Optimal dynamic state estimation filtering process and measurement variance"),
    MOVING_AVERAGE("Simple Moving Average", "Unweighted mean over recent sliding window")
}

/**
 * Statistical distribution metrics derived from calibration samples.
 */
data class RssiDistributionMetrics(
    val sampleCount: Int,
    val durationSeconds: Int,
    val minRssi: Int,
    val maxRssi: Int,
    val meanRssi: Double,
    val medianRssi: Double,
    val standardDeviation: Double,
    val p10: Double,
    val p25: Double,
    val p75: Double,
    val p90: Double,
    val sampleHistory: List<Int> = emptyList()
) {
    val spreadIqr: Double
        get() = p75 - p25

    companion object {
        fun calculateFromSamples(samples: List<Int>, durationSec: Int = 30): RssiDistributionMetrics {
            if (samples.isEmpty()) {
                return RssiDistributionMetrics(
                    sampleCount = 0,
                    durationSeconds = durationSec,
                    minRssi = 0,
                    maxRssi = 0,
                    meanRssi = 0.0,
                    medianRssi = 0.0,
                    standardDeviation = 0.0,
                    p10 = 0.0,
                    p25 = 0.0,
                    p75 = 0.0,
                    p90 = 0.0
                )
            }

            val sorted = samples.sorted()
            val count = sorted.size
            val min = sorted.first()
            val max = sorted.last()
            val mean = samples.average()

            val variance = samples.map { (it - mean) * (it - mean) }.average()
            val stdDev = Math.sqrt(variance)

            fun percentile(p: Double): Double {
                if (count == 1) return sorted[0].toDouble()
                val rank = p * (count - 1)
                val lowerIndex = rank.toInt()
                val upperIndex = (lowerIndex + 1).coerceAtMost(count - 1)
                val weight = rank - lowerIndex
                return sorted[lowerIndex] * (1.0 - weight) + sorted[upperIndex] * weight
            }

            return RssiDistributionMetrics(
                sampleCount = count,
                durationSeconds = durationSec,
                minRssi = min,
                maxRssi = max,
                meanRssi = mean,
                medianRssi = percentile(0.50),
                standardDeviation = stdDev,
                p10 = percentile(0.10),
                p25 = percentile(0.25),
                p75 = percentile(0.75),
                p90 = percentile(0.90),
                sampleHistory = samples
            )
        }
    }
}

/**
 * Quality assessment of RSSI distribution separation between Inside and Outside.
 */
enum class SeparationQuality(val label: String, val colorHex: Long, val advice: String) {
    EXCELLENT("EXCELLENT SEPARATION", 0xFF2E7D32, "Strong distinct signal boundary. Highly reliable proximity detection."),
    GOOD("GOOD SEPARATION", 0xFF388E3C, "Clear signal margin between zones. Reliable for automated mode switching."),
    MODERATE("MODERATE SEPARATION", 0xFFF57F17, "Slight distribution overlap. Hysteresis and temporal smoothing are recommended."),
    POOR("POOR SEPARATION / OVERLAP", 0xFFD32F2F, "Substantial overlap between Inside and Outside. Consider repositioning the beacon or adjusting environment.")
}

/**
 * Computed boundary thresholds with hysteresis recommendations.
 */
data class ThresholdCalculationResult(
    val suggestedEnterThreshold: Int, // e.g. -64 dBm (stronger signal)
    val suggestedExitThreshold: Int,  // e.g. -69 dBm (weaker signal)
    val medianSeparationDb: Double,
    val quality: SeparationQuality,
    val overlapPercentage: Double,
    val summaryNotes: String
)

/**
 * Complete calibrated Proximity Profile ready for persistence and automation.
 */
data class ProximityProfile(
    val id: String = UUID.randomUUID().toString(),
    val profileName: String = "Bedroom Focus",
    val targetDeviceId: BleDeviceId,
    val targetDisplayName: String = "Smart Tag",
    val insideMetrics: RssiDistributionMetrics? = null,
    val outsideMetrics: RssiDistributionMetrics? = null,
    val enterThresholdRssi: Int = -64,
    val exitThresholdRssi: Int = -69,
    val enterDurationSeconds: Int = 5,
    val exitDurationSeconds: Int = 10,
    val filterType: RssiFilterType = RssiFilterType.EMA,
    val filterSmoothingParam: Double = 0.25, // Alpha for EMA or measurement variance for Kalman
    val windowSampleSize: Int = 15,
    val lostDeviceTimeoutSeconds: Int = 8,
    val boundSamsungModeUuid: String = "",
    val isEnabled: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)
