package com.samsungmodes.poc.ble.model

/**
 * Interface representing a hardware-agnostic BLE Proximity Device.
 * Allows seamless extension from Samsung SmartTag 1 to generic iBeacon, Eddystone, or custom beacons.
 */
interface BleProximityDevice {
    val id: String
    val displayName: String
    val deviceType: DeviceType
    val deviceId: BleDeviceId

    enum class DeviceType {
        SAMSUNG_SMARTTAG_1,
        GENERIC_BEACON,
        IBEACON,
        EDDYSTONE,
        CUSTOM_BLE
    }
}

/**
 * Concrete saved profile for a BLE device used in proximity monitoring.
 */
data class BleDeviceProfile(
    override val id: String,
    override val displayName: String,
    override val deviceType: BleProximityDevice.DeviceType,
    override val deviceId: BleDeviceId,
    val targetMacAddress: String? = null,
    val targetManufacturerId: Int? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val notes: String = ""
) : BleProximityDevice
