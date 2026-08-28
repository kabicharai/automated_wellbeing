package com.samsungmodes.poc.ble.model

import java.util.UUID

/**
 * Raw decoded BLE advertisement packet.
 * Used for diagnostic inspection of Samsung SmartTags and generic beacons.
 */
data class BleRawAdvertisement(
    val advertiseFlags: Int = -1,
    val txPowerLevel: Int? = null,
    val manufacturerDataMap: Map<Int, ByteArray> = emptyMap(),
    val serviceUuids: List<UUID> = emptyList(),
    val serviceDataMap: Map<UUID, ByteArray> = emptyMap(),
    val rawBytesHex: String = "",
    val isConnectable: Boolean = false,
    val primaryPhy: Int = 1, // LE 1M
    val secondaryPhy: Int = 0,
    val periodicAdvertisingInterval: Int = 0,
    val timestampNanos: Long = 0L
) {
    /**
     * Checks if this advertisement contains Samsung Electronics Manufacturer ID (0x0075 / 117).
     */
    val isSamsungManufacturer: Boolean
        get() = manufacturerDataMap.containsKey(0x0075)

    /**
     * Inspects manufacturer payload formatted as a hexadecimal string.
     */
    fun getManufacturerHex(manufacturerId: Int): String? {
        return manufacturerDataMap[manufacturerId]?.joinToString(" ") { "%02X".format(it) }
    }

    /**
     * Formats all manufacturer data entries as human-readable key-value pairs.
     */
    fun getFormattedManufacturerList(): List<Pair<String, String>> {
        return manufacturerDataMap.map { (id, data) ->
            val mfgName = when (id) {
                0x0075 -> "Samsung Electronics (0x0075)"
                0x004C -> "Apple Inc. (0x004C)"
                0x0006 -> "Microsoft (0x0006)"
                0x00E0 -> "Google LLC (0x00E0)"
                0x0157 -> "Anhui Huami (0x0157)"
                0x000D -> "Texas Instruments (0x000D)"
                0x0059 -> "Nordic Semiconductor (0x0059)"
                else -> "Vendor ID 0x%04X (%d)".format(id, id)
            }
            val hex = data.joinToString(" ") { "%02X".format(it) }
            mfgName to hex
        }
    }
}
