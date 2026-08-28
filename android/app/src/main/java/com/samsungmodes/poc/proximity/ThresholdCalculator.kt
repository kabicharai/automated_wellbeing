package com.samsungmodes.poc.proximity

import com.samsungmodes.poc.proximity.model.RssiDistributionMetrics
import com.samsungmodes.poc.proximity.model.SeparationQuality
import com.samsungmodes.poc.proximity.model.ThresholdCalculationResult
import kotlin.math.roundToInt

/**
 * Intelligent threshold calculator analyzing empirical Inside and Outside RSSI distributions.
 * Recommends optimal ENTER / EXIT thresholds with safe hysteresis margins.
 */
object ThresholdCalculator {

    /**
     * Calculates candidate thresholds using empirical percentiles and distribution boundaries.
     *
     * Inside: Closer to beacon -> Stronger RSSI (e.g. -50 to -65 dBm)
     * Outside: Farther from beacon -> Weaker RSSI (e.g. -70 to -90 dBm)
     */
    fun calculate(
        inside: RssiDistributionMetrics,
        outside: RssiDistributionMetrics
    ): ThresholdCalculationResult {
        if (inside.sampleCount == 0 || outside.sampleCount == 0) {
            return ThresholdCalculationResult(
                suggestedEnterThreshold = -64,
                suggestedExitThreshold = -69,
                medianSeparationDb = 0.0,
                quality = SeparationQuality.POOR,
                overlapPercentage = 100.0,
                summaryNotes = "Insufficient calibration samples. Please run both Inside and Outside steps."
            )
        }

        val insideMedian = inside.medianRssi
        val outsideMedian = outside.medianRssi
        val separationDb = insideMedian - outsideMedian

        // Determine overlap: checking whether Outside's high percentiles reach Inside's low percentiles
        val insideP25 = inside.p25
        val insideP10 = inside.p10
        val outsideP75 = outside.p75
        val outsideP90 = outside.p90

        // Quality assessment
        val quality = when {
            separationDb >= 14.0 && outsideP90 < insideP10 -> SeparationQuality.EXCELLENT
            separationDb >= 8.0 && outsideP75 < insideP25 -> SeparationQuality.GOOD
            separationDb >= 4.0 -> SeparationQuality.MODERATE
            else -> SeparationQuality.POOR
        }

        // Calculate overlap estimate
        val overlapCount = outside.sampleHistory.count { it >= insideP25 } +
                inside.sampleHistory.count { it <= outsideP75 }
        val totalCount = (inside.sampleCount + outside.sampleCount).coerceAtLeast(1)
        val overlapPct = ((overlapCount.toDouble() / totalCount) * 100.0).coerceIn(0.0, 100.0)

        // Threshold boundary computation
        // ENTER threshold (require high confidence entering inside):
        // Positioned between inside p25 and outside p75, weighted slightly toward inside
        val enterRaw = if (insideP25 > outsideP75) {
            (insideP25 * 0.6 + outsideP75 * 0.4)
        } else {
            // Overlapping distributions: fallback to midpoint between medians with safety bias
            (insideMedian * 0.45 + outsideMedian * 0.55)
        }

        // EXIT threshold (require high confidence leaving outside):
        // Positioned lower than ENTER to enforce a mandatory 4-6 dBm hysteresis deadband
        val naturalHysteresis = (separationDb * 0.35).coerceIn(4.0, 7.0)
        val exitRaw = enterRaw - naturalHysteresis

        val suggestedEnter = enterRaw.roundToInt()
        val suggestedExit = exitRaw.roundToInt()

        val summary = when (quality) {
            SeparationQuality.EXCELLENT -> "Strong ${"%.1f".format(separationDb)} dB separation with negligible overlap. High accuracy expected."
            SeparationQuality.GOOD -> "Clear ${"%.1f".format(separationDb)} dB separation. Hysteresis (${suggestedEnter - suggestedExit} dB) effectively suppresses edge fluttering."
            SeparationQuality.MODERATE -> "Moderate ${"%.1f".format(separationDb)} dB separation with slight overlap (${"%.0f".format(overlapPct)}%). Candidate duration timers will filter transient noise."
            SeparationQuality.POOR -> "Weak ${"%.1f".format(separationDb)} dB separation. Beacon signal may be attenuated by walls or distance is too close between test spots."
        }

        return ThresholdCalculationResult(
            suggestedEnterThreshold = suggestedEnter,
            suggestedExitThreshold = suggestedExit,
            medianSeparationDb = separationDb,
            quality = quality,
            overlapPercentage = overlapPct,
            summaryNotes = summary
        )
    }
}
