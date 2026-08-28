package com.samsungmodes.poc.ble.model

import java.util.UUID

/**
 * Stable device identity abstraction.
 * Android 12+ privacy protections and MAC randomization may alter hardware addresses over time.
 * This abstraction evaluates the most reliable combination of:
 * - Manufacturer ID (e.g., 0x0075 for Samsung, 0x004C for Apple)
 * - Specific manufacturer data payload (e.g. SmartTag payload signature)
 * - Advertised Service UUIDs
 * - Service Data bytes
 * - Advertised local name
 * - Device MAC address (when available)
 */
data class BleDeviceId(
    val primaryKey: String,
    val manufacturerId: Int? = null,
    val serviceUuids: List<UUID> = emptyList(),
    val deviceName: String? = null,
    val macAddress: String? = null,
    val identityType: IdentityType = IdentityType.COMBINED_SIGNATURE
) {
    enum class IdentityType {
        MAC_ADDRESS,
        MANUFACTURER_SIGNATURE,
        SERVICE_UUID,
        COMBINED_SIGNATURE
    }

    fun matches(other: BleDeviceId): Boolean {
        // 1. Direct key match
        if (primaryKey == other.primaryKey) return true

        // 2. MAC Address match if available and non-randomized
        if (!macAddress.isNullOrBlank() && macAddress == other.macAddress) return true

        // 3. Manufacturer ID + Service UUID match
        if (manufacturerId != null && manufacturerId == other.manufacturerId) {
            if (serviceUuids.isNotEmpty() && other.serviceUuids.isNotEmpty()) {
                val commonUuids = serviceUuids.intersect(other.serviceUuids.toSet())
                if (commonUuids.isNotEmpty()) return true
            }
            if (!deviceName.isNullOrBlank() && deviceName == other.deviceName) return true
        }

        return false
    }

    companion object {
        fun createFromScan(
            address: String?,
            name: String?,
            manufacturerId: Int?,
            manufacturerData: ByteArray?,
            serviceUuids: List<UUID>
        ): BleDeviceId {
            val keyBuilder = StringBuilder()
            
            if (!address.isNullOrBlank()) {
                keyBuilder.append("addr:").append(address)
            } else if (manufacturerId != null) {
                keyBuilder.append("mfg:0x").append(Integer.toHexString(manufacturerId))
                if (manufacturerData != null && manufacturerData.isNotEmpty()) {
                    val preview = manufacturerData.take(4).joinToString("") { "%02X".format(it) }
                    keyBuilder.append(":").append(preview)
                }
            } else if (serviceUuids.isNotEmpty()) {
                keyBuilder.append("svc:").append(serviceUuids.first().toString().take(8))
            } else {
                keyBuilder.append("name:").append(name ?: "unknown_${System.currentTimeMillis()}")
            }

            return BleDeviceId(
                primaryKey = keyBuilder.toString(),
                manufacturerId = manufacturerId,
                serviceUuids = serviceUuids,
                deviceName = name,
                macAddress = address,
                identityType = when {
                    !address.isNullOrBlank() -> IdentityType.MAC_ADDRESS
                    manufacturerId != null -> IdentityType.MANUFACTURER_SIGNATURE
                    serviceUuids.isNotEmpty() -> IdentityType.SERVICE_UUID
                    else -> IdentityType.COMBINED_SIGNATURE
                }
            )
        }
    }
}
