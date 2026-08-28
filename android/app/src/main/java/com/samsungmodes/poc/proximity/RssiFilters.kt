package com.samsungmodes.poc.proximity

import com.samsungmodes.poc.proximity.model.RssiFilterType
import java.util.ArrayDeque

/**
 * Filter implementations for smoothing fluctuating raw BLE RSSI readings.
 * Ensures the Proximity State Machine receives clean, anti-flapping estimates.
 */
interface RssiFilter {
    fun addSample(rawRssi: Int): Double
    fun getCurrentFilteredValue(): Double?
    fun reset()
}

/**
 * Exponential Moving Average (EMA) filter.
 * Snappy response while smoothing high-frequency noise.
 * Formula: S_t = alpha * Y_t + (1 - alpha) * S_{t-1}
 */
class EmaRssiFilter(private val alpha: Double = 0.25) : RssiFilter {
    private var currentFiltered: Double? = null

    override fun addSample(rawRssi: Int): Double {
        val prev = currentFiltered
        val next = if (prev == null) {
            rawRssi.toDouble()
        } else {
            alpha * rawRssi + (1.0 - alpha) * prev
        }
        currentFiltered = next
        return next
    }

    override fun getCurrentFilteredValue(): Double? = currentFiltered

    override fun reset() {
        currentFiltered = null
    }
}

/**
 * Running Median Filter.
 * Highly robust against severe single-sample multipath drops and spike artifacts.
 */
class RunningMedianRssiFilter(private val windowSize: Int = 11) : RssiFilter {
    private val buffer = ArrayDeque<Int>(windowSize)

    override fun addSample(rawRssi: Int): Double {
        if (buffer.size >= windowSize) {
            buffer.removeFirst()
        }
        buffer.addLast(rawRssi)

        val sorted = buffer.sorted()
        val size = sorted.size
        val median = if (size % 2 == 1) {
            sorted[size / 2].toDouble()
        } else {
            (sorted[(size / 2) - 1] + sorted[size / 2]) / 2.0
        }
        return median
    }

    override fun getCurrentFilteredValue(): Double? {
        if (buffer.isEmpty()) return null
        val sorted = buffer.sorted()
        val size = sorted.size
        return if (size % 2 == 1) {
            sorted[size / 2].toDouble()
        } else {
            (sorted[(size / 2) - 1] + sorted[size / 2]) / 2.0
        }
    }

    override fun reset() {
        buffer.clear()
    }
}

/**
 * 1D Kalman Filter for dynamic BLE RSSI tracking.
 * Adapts estimation variance based on process noise (Q) and measurement noise (R).
 */
class KalmanRssiFilter(
    private val processNoiseQ: Double = 0.125,
    private val measurementNoiseR: Double = 3.5
) : RssiFilter {

    private var xEst: Double? = null // State estimate (RSSI in dBm)
    private var pEst: Double = 1.0   // Estimate error covariance

    override fun addSample(rawRssi: Int): Double {
        val currentX = xEst
        if (currentX == null) {
            xEst = rawRssi.toDouble()
            pEst = 1.0
            return rawRssi.toDouble()
        }

        // 1. Prediction update
        val pTemp = pEst + processNoiseQ

        // 2. Measurement update (Kalman Gain)
        val kGain = pTemp / (pTemp + measurementNoiseR)
        val nextX = currentX + kGain * (rawRssi - currentX)
        pEst = (1.0 - kGain) * pTemp
        xEst = nextX

        return nextX
    }

    override fun getCurrentFilteredValue(): Double? = xEst

    override fun reset() {
        xEst = null
        pEst = 1.0
    }
}

/**
 * Factory creating the configured filter instance.
 */
object RssiFilterFactory {
    fun create(type: RssiFilterType, smoothingParam: Double = 0.25, windowSize: Int = 11): RssiFilter {
        return when (type) {
            RssiFilterType.EMA -> EmaRssiFilter(alpha = smoothingParam.coerceIn(0.05, 0.95))
            RssiFilterType.RUNNING_MEDIAN -> RunningMedianRssiFilter(windowSize = windowSize.coerceIn(3, 31))
            RssiFilterType.KALMAN -> KalmanRssiFilter(processNoiseQ = 0.1, measurementNoiseR = (smoothingParam * 10.0).coerceAtLeast(1.0))
            RssiFilterType.MOVING_AVERAGE -> object : RssiFilter {
                private val buf = ArrayDeque<Int>(windowSize)
                override fun addSample(rawRssi: Int): Double {
                    if (buf.size >= windowSize) buf.removeFirst()
                    buf.addLast(rawRssi)
                    return buf.average()
                }
                override fun getCurrentFilteredValue(): Double? = if (buf.isEmpty()) null else buf.average()
                override fun reset() = buf.clear()
            }
        }
    }
}
