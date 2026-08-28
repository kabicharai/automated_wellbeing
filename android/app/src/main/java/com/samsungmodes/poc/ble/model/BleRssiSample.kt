package com.samsungmodes.poc.ble.model

/**
 * Individual RSSI measurement sample with timestamp.
 */
data class BleRssiSample(
    val timestampMillis: Long,
    val rssi: Int
)
