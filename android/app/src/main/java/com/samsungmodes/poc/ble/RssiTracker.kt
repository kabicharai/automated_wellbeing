package com.samsungmodes.poc.ble

import com.samsungmodes.poc.ble.model.BleRssiSample
import java.util.ArrayDeque
import kotlin.math.roundToInt

/**
 * High-performance, memory-capped rolling RSSI tracker.
 * Maintains chronological samples and calculates instant, moving average, median, min, and max values.
 */
class RssiTracker(
    private val maxCapacity: Int = 1000
) {
    private val samples = ArrayDeque<BleRssiSample>(maxCapacity)
    private val lock = Any()

    enum class HistoryWindow(val seconds: Int, val label: String) {
        WINDOW_5S(5, "5s"),
        WINDOW_15S(15, "15s"),
        WINDOW_30S(30, "30s"),
        WINDOW_60S(60, "60s"),
        WINDOW_300S(300, "5m")
    }

    data class RssiSnapshot(
        val currentRssi: Int?,
        val sampleCount: Int,
        val average: Double?,
        val median: Double?,
        val min: Int?,
        val max: Int?,
        val standardDeviation: Double?,
        val historySamples: List<BleRssiSample>
    )

    fun addSample(rssi: Int, timestamp: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            if (samples.size >= maxCapacity) {
                samples.removeFirst()
            }
            samples.addLast(BleRssiSample(timestampMillis = timestamp, rssi = rssi))
        }
    }

    fun clear() {
        synchronized(lock) {
            samples.clear()
        }
    }

    fun getSnapshot(window: HistoryWindow = HistoryWindow.WINDOW_30S): RssiSnapshot {
        val now = System.currentTimeMillis()
        val windowThreshold = now - (window.seconds * 1000L)

        val windowList: List<BleRssiSample> = synchronized(lock) {
            samples.filter { it.timestampMillis >= windowThreshold }
        }

        if (windowList.isEmpty()) {
            return RssiSnapshot(
                currentRssi = null,
                sampleCount = 0,
                average = null,
                median = null,
                min = null,
                max = null,
                standardDeviation = null,
                historySamples = emptyList()
            )
        }

        val rssiValues = windowList.map { it.rssi }
        val current = rssiValues.lastOrNull()
        val count = rssiValues.size
        val avg = rssiValues.average()
        val min = rssiValues.minOrNull()
        val max = rssiValues.maxOrNull()

        // Calculate median
        val sorted = rssiValues.sorted()
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2].toDouble()
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        }

        // Calculate standard deviation
        val variance = rssiValues.map { (it - avg) * (it - avg) }.average()
        val stdDev = kotlin.math.sqrt(variance)

        return RssiSnapshot(
            currentRssi = current,
            sampleCount = count,
            average = (avg * 10.0).roundToInt() / 10.0,
            median = (median * 10.0).roundToInt() / 10.0,
            min = min,
            max = max,
            standardDeviation = (stdDev * 10.0).roundToInt() / 10.0,
            historySamples = windowList
        )
    }
}
