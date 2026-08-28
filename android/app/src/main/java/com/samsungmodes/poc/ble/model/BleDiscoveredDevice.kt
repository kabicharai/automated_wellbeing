package com.samsungmodes.poc.ble.model

/**
 * Real-time state of a device discovered during BLE scanning.
 */
data class BleDiscoveredDevice(
    val deviceId: BleDeviceId,
    val name: String,
    val address: String,
    val currentRssi: Int,
    val firstSeenMillis: Long = System.currentTimeMillis(),
    val lastSeenMillis: Long = System.currentTimeMillis(),
    val totalSamples: Int = 1,
    val advertisement: BleRawAdvertisement = BleRawAdvertisement(),
    val isSmartTagCandidate: Boolean = false
) {
    val isRecentlyActive: Boolean
        get() = (System.currentTimeMillis() - lastSeenMillis) < 8000L

    val formattedAddress: String
        get() = if (address.isNotBlank()) address else "Randomized / Protected"

    val signalStrengthCategory: String
        get() = when {
            currentRssi >= -60 -> "Strong"
            currentRssi >= -75 -> "Moderate"
            currentRssi >= -85 -> "Weak"
            else -> "Very Weak"
        }
}
