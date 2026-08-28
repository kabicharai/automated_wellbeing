package com.samsungmodes.poc.ble

import com.samsungmodes.poc.ble.model.BleRawAdvertisement
import java.util.UUID

/**
 * Intelligent classifier for Bluetooth Low Energy devices.
 * Accurately distinguishes between Samsung Galaxy SmartTags (Offline Finding 0xFD5A / 0xFD59 / SmartTag 1 & 2)
 * and other Samsung appliances (Smart TVs, Refrigerators, Tablets, Galaxy Watches, Buds, Phones)
 * as well as generic iBeacon / Eddystone / BLE beacons.
 */
object SamsungDeviceClassifier {

    // Samsung Find & SmartThings BLE 16-bit Service UUIDs (Standard Bluetooth SIG assigned & Samsung proprietary)
    val UUID_SAMSUNG_OFFLINE_FINDING_FD5A: UUID = UUID.fromString("0000fd5a-0000-1000-8000-00805f9b34fb")
    val UUID_SAMSUNG_UNREGISTERED_TAG_FD59: UUID = UUID.fromString("0000fd59-0000-1000-8000-00805f9b34fb")
    val UUID_SAMSUNG_SMARTTHINGS_FD5B: UUID = UUID.fromString("0000fd5b-0000-1000-8000-00805f9b34fb")
    val UUID_APPLE_FINDMY_FD6F: UUID = UUID.fromString("0000fd6f-0000-1000-8000-00805f9b34fb")

    const val SAMSUNG_MFG_ID = 0x0075
    const val APPLE_MFG_ID = 0x004C

    enum class BleDeviceCategory(val label: String, val badgeColorHex: Long, val isSmartTag: Boolean) {
        GALAXY_SMARTTAG("Galaxy SmartTag", 0xFF0D47A1, true),
        SMARTTHINGS_FIND_TAG("SmartThings Find (0xFD5A)", 0xFF1565C0, true),
        SMARTTAG_SETUP("SmartTag Setup (0xFD59)", 0xFF00838F, true),
        SAMSUNG_SMART_TV("Samsung Smart TV", 0xFFE65100, false),
        SAMSUNG_GALAXY_WATCH("Galaxy Watch", 0xFF6A1B9A, false),
        SAMSUNG_GALAXY_BUDS("Galaxy Buds", 0xFF00695C, false),
        SAMSUNG_TABLET("Galaxy Tab", 0xFF283593, false),
        SAMSUNG_PHONE("Galaxy Phone", 0xFF37474F, false),
        SAMSUNG_APPLIANCE("Samsung Appliance", 0xFF4E342E, false),
        SAMSUNG_GENERIC("Samsung Device", 0xFF455A64, false),
        APPLE_FIND_MY("Apple Find My / AirTag", 0xFF424242, false),
        GENERIC_BEACON("BLE Beacon / Tracker", 0xFF00838F, false),
        GENERIC_BLE("BLE Device", 0xFF546E7A, false)
    }

    data class Classification(
        val category: BleDeviceCategory,
        val displayName: String,
        val isSmartTag: Boolean,
        val offlineFindingPrivacyId: String? = null,
        val tagStatusDescription: String? = null,
        val subtitle: String = ""
    )

    fun classify(
        rawName: String?,
        rawAddress: String?,
        rawAd: BleRawAdvertisement
    ): Classification {
        val name = rawName?.trim() ?: ""

        // 1. Check for Samsung Find / SmartTag 16-bit Service UUIDs (0xFD5A, 0xFD59, 0xFD5B)
        val hasFd5a = rawAd.serviceUuids.any { matchesShortUuid(it, 0xFD5A) } ||
                rawAd.serviceDataMap.keys.any { matchesShortUuid(it, 0xFD5A) }

        val hasFd59 = rawAd.serviceUuids.any { matchesShortUuid(it, 0xFD59) } ||
                rawAd.serviceDataMap.keys.any { matchesShortUuid(it, 0xFD59) }

        val hasFd5b = rawAd.serviceUuids.any { matchesShortUuid(it, 0xFD5B) } ||
                rawAd.serviceDataMap.keys.any { matchesShortUuid(it, 0xFD5B) }

        // Extract 0xFD5A Offline Finding Service Data Payload if available
        var privacyIdHex: String? = null
        var tagStatus: String? = null

        val fd5aData = rawAd.serviceDataMap.entries.firstOrNull { matchesShortUuid(it.key, 0xFD5A) }?.value
        if (fd5aData != null && fd5aData.isNotEmpty()) {
            if (fd5aData.size >= 12) {
                // Bytes 4..11 contain the 8-byte rolling Privacy ID in Samsung Offline Finding protocol
                val pidBytes = fd5aData.sliceArray(4 until 12.coerceAtMost(fd5aData.size))
                privacyIdHex = pidBytes.joinToString("") { "%02X".format(it) }
            } else if (fd5aData.size >= 4) {
                privacyIdHex = fd5aData.take(4).toByteArray().joinToString("") { "%02X".format(it) }
            }

            val statusByte = fd5aData[0].toInt() and 0xFF
            val isLostMode = (statusByte and 0x20) != 0
            val isConnectedState = (statusByte and 0x10) != 0
            tagStatus = when {
                isLostMode -> "Lost Mode Broadcast"
                isConnectedState -> "Paired / Active"
                else -> "Offline Finding Beacon"
            }
        }

        // 2. Explicit SmartTag local name matching (e.g. Galaxy SmartTag, SmartTag, EI-T5300, EI-T7300, EI-T5600)
        val isExplicitSmartTagName = name.contains("SmartTag", ignoreCase = true) ||
                name.contains("Smart Tag", ignoreCase = true) ||
                name.contains("Galaxy Tag", ignoreCase = true) ||
                name.contains("EI-T5300", ignoreCase = true) || // SmartTag 1
                name.contains("EI-T7300", ignoreCase = true) || // SmartTag+ (UWB)
                name.contains("EI-T5600", ignoreCase = true) || // SmartTag 2
                name.contains("SmartThings Tag", ignoreCase = true)

        // 3. If explicit SmartTag or Offline Finding Service UUIDs present -> Definite SmartTag!
        if (isExplicitSmartTagName) {
            val resolved = if (name.isNotBlank()) name else "Samsung Galaxy SmartTag"
            return Classification(
                category = BleDeviceCategory.GALAXY_SMARTTAG,
                displayName = resolved,
                isSmartTag = true,
                offlineFindingPrivacyId = privacyIdHex,
                tagStatusDescription = tagStatus ?: "Galaxy SmartTag Active",
                subtitle = "Hardware: Samsung Galaxy SmartTag"
            )
        }

        if (hasFd5a) {
            val pidLabel = privacyIdHex?.let { " [ID: ${it.take(8)}]" } ?: ""
            val resolved = if (name.isNotBlank() && !name.startsWith("BLE", ignoreCase = true)) {
                name
            } else {
                "Samsung SmartTag (Find 0xFD5A)$pidLabel"
            }
            return Classification(
                category = BleDeviceCategory.SMARTTHINGS_FIND_TAG,
                displayName = resolved,
                isSmartTag = true,
                offlineFindingPrivacyId = privacyIdHex,
                tagStatusDescription = tagStatus ?: "SmartThings Offline Finding",
                subtitle = "Samsung Offline Finding Protocol (0xFD5A)"
            )
        }

        if (hasFd59) {
            val resolved = if (name.isNotBlank()) name else "Samsung SmartTag (Unregistered Setup)"
            return Classification(
                category = BleDeviceCategory.SMARTTAG_SETUP,
                displayName = resolved,
                isSmartTag = true,
                tagStatusDescription = "Unregistered / Pairing Mode (0xFD59)",
                subtitle = "SmartTag Setup Beacon"
            )
        }

        if (hasFd5b) {
            val resolved = if (name.isNotBlank()) name else "Samsung SmartThings Finder Beacon"
            return Classification(
                category = BleDeviceCategory.SMARTTHINGS_FIND_TAG,
                displayName = resolved,
                isSmartTag = true,
                tagStatusDescription = "SmartThings UWB / Finder Beacon",
                subtitle = "SmartThings Protocol (0xFD5B)"
            )
        }

        // 4. Non-SmartTag Samsung devices differentiation (TVs, Refrigerators, Tablets, Watches, Buds, Phones)
        val mfg0075 = rawAd.manufacturerDataMap[SAMSUNG_MFG_ID]
        val isSamsung = rawAd.isSamsungManufacturer || name.contains("Samsung", ignoreCase = true)

        // 4A. Samsung Smart TVs
        if (name.contains("[TV]", ignoreCase = true) ||
            name.contains("Samsung TV", ignoreCase = true) ||
            name.contains("The Frame", ignoreCase = true) ||
            name.contains("The Serif", ignoreCase = true) ||
            name.contains("Neo QLED", ignoreCase = true) ||
            name.contains("OLED TV", ignoreCase = true) ||
            (name.startsWith("QN", ignoreCase = true) && name.length in 5..12) ||
            (name.startsWith("UE", ignoreCase = true) && name.length in 5..12) ||
            (name.startsWith("QE", ignoreCase = true) && name.length in 5..12)
        ) {
            return Classification(
                category = BleDeviceCategory.SAMSUNG_SMART_TV,
                displayName = if (name.isNotBlank()) name else "Samsung Smart TV",
                isSmartTag = false,
                subtitle = "Home Display / Smart TV"
            )
        }

        // 4B. Samsung Galaxy Watch
        if (name.contains("Galaxy Watch", ignoreCase = true) ||
            name.contains("Watch4", ignoreCase = true) ||
            name.contains("Watch5", ignoreCase = true) ||
            name.contains("Watch6", ignoreCase = true) ||
            name.contains("Watch7", ignoreCase = true) ||
            name.contains("Watch Ultra", ignoreCase = true) ||
            name.contains("Gear S", ignoreCase = true) ||
            name.startsWith("SM-R8", ignoreCase = true) ||
            name.startsWith("SM-R9", ignoreCase = true)
        ) {
            return Classification(
                category = BleDeviceCategory.SAMSUNG_GALAXY_WATCH,
                displayName = if (name.isNotBlank()) name else "Samsung Galaxy Watch",
                isSmartTag = false,
                subtitle = "Wearable / Galaxy Watch"
            )
        }

        // 4C. Samsung Galaxy Buds
        if (name.contains("Galaxy Buds", ignoreCase = true) ||
            name.contains("Buds2", ignoreCase = true) ||
            name.contains("Buds Pro", ignoreCase = true) ||
            name.contains("Buds FE", ignoreCase = true) ||
            name.contains("Buds3", ignoreCase = true) ||
            name.contains("Buds Live", ignoreCase = true) ||
            name.startsWith("SM-R1", ignoreCase = true) ||
            name.startsWith("SM-R5", ignoreCase = true)
        ) {
            return Classification(
                category = BleDeviceCategory.SAMSUNG_GALAXY_BUDS,
                displayName = if (name.isNotBlank()) name else "Samsung Galaxy Buds",
                isSmartTag = false,
                subtitle = "Audio / Galaxy Buds"
            )
        }

        // 4D. Samsung Galaxy Tablets
        if (name.contains("Galaxy Tab", ignoreCase = true) ||
            name.contains("Tab S", ignoreCase = true) ||
            name.contains("Tab A", ignoreCase = true) ||
            name.startsWith("SM-T", ignoreCase = true) ||
            name.startsWith("SM-X", ignoreCase = true)
        ) {
            return Classification(
                category = BleDeviceCategory.SAMSUNG_TABLET,
                displayName = if (name.isNotBlank()) name else "Samsung Galaxy Tablet",
                isSmartTag = false,
                subtitle = "Mobile / Galaxy Tablet"
            )
        }

        // 4E. Samsung Home Appliances (Refrigerator, Washer, Dryer, etc.)
        if (name.contains("Refrigerator", ignoreCase = true) ||
            name.contains("Fridge", ignoreCase = true) ||
            name.contains("Family Hub", ignoreCase = true) ||
            name.contains("Washer", ignoreCase = true) ||
            name.contains("Dryer", ignoreCase = true) ||
            name.contains("AirDresser", ignoreCase = true) ||
            name.contains("Dishwasher", ignoreCase = true) ||
            name.contains("Oven", ignoreCase = true) ||
            name.contains("Jet Bot", ignoreCase = true) ||
            name.contains("Robot Vacuum", ignoreCase = true) ||
            name.contains("[Ref]", ignoreCase = true) ||
            name.contains("[Washer]", ignoreCase = true) ||
            name.contains("[Dryer]", ignoreCase = true) ||
            name.contains("[AC]", ignoreCase = true) ||
            name.contains("[Oven]", ignoreCase = true) ||
            name.contains("SmartThings Home", ignoreCase = true)
        ) {
            return Classification(
                category = BleDeviceCategory.SAMSUNG_APPLIANCE,
                displayName = if (name.isNotBlank()) name else "Samsung Smart Appliance",
                isSmartTag = false,
                subtitle = "Home Appliance / SmartThings"
            )
        }

        // 4F. Samsung Galaxy Phones
        if (name.contains("Galaxy S", ignoreCase = true) ||
            name.contains("Galaxy Z", ignoreCase = true) ||
            name.contains("Galaxy A", ignoreCase = true) ||
            name.contains("Galaxy Note", ignoreCase = true) ||
            name.startsWith("SM-S", ignoreCase = true) ||
            name.startsWith("SM-F", ignoreCase = true) ||
            name.startsWith("SM-G", ignoreCase = true) ||
            name.startsWith("SM-A", ignoreCase = true)
        ) {
            return Classification(
                category = BleDeviceCategory.SAMSUNG_PHONE,
                displayName = if (name.isNotBlank()) name else "Samsung Galaxy Phone",
                isSmartTag = false,
                subtitle = "Mobile / Galaxy Phone"
            )
        }

        // 4G. Check Samsung Manufacturer Data (0x0075) Subtype Byte
        if (mfg0075 != null && mfg0075.isNotEmpty()) {
            val subtype = mfg0075[0].toInt() and 0xFF
            when (subtype) {
                0x42, 0x1B -> {
                    // SmartTag / SmartThings Find Tag subtype in Samsung BLE payload
                    return Classification(
                        category = BleDeviceCategory.GALAXY_SMARTTAG,
                        displayName = if (name.isNotBlank()) name else "Samsung Galaxy SmartTag",
                        isSmartTag = true,
                        tagStatusDescription = "Samsung Tag Payload (0x42)",
                        subtitle = "Samsung SmartThings Tag"
                    )
                }
                0x08, 0x05 -> {
                    return Classification(
                        category = BleDeviceCategory.SAMSUNG_SMART_TV,
                        displayName = if (name.isNotBlank()) name else "Samsung Smart TV",
                        isSmartTag = false,
                        subtitle = "Samsung Display Device (0x08)"
                    )
                }
                0x03 -> {
                    return Classification(
                        category = BleDeviceCategory.SAMSUNG_GALAXY_WATCH,
                        displayName = if (name.isNotBlank()) name else "Samsung Galaxy Watch",
                        isSmartTag = false,
                        subtitle = "Samsung Wearable (0x03)"
                    )
                }
                0x04 -> {
                    return Classification(
                        category = BleDeviceCategory.SAMSUNG_GALAXY_BUDS,
                        displayName = if (name.isNotBlank()) name else "Samsung Galaxy Buds",
                        isSmartTag = false,
                        subtitle = "Samsung Audio (0x04)"
                    )
                }
                0x02 -> {
                    return Classification(
                        category = BleDeviceCategory.SAMSUNG_TABLET,
                        displayName = if (name.isNotBlank()) name else "Samsung Galaxy Tablet",
                        isSmartTag = false,
                        subtitle = "Samsung Tablet (0x02)"
                    )
                }
                0x01 -> {
                    return Classification(
                        category = BleDeviceCategory.SAMSUNG_PHONE,
                        displayName = if (name.isNotBlank()) name else "Samsung Galaxy Phone",
                        isSmartTag = false,
                        subtitle = "Samsung Mobile (0x01)"
                    )
                }
            }

            if (isSamsung) {
                return Classification(
                    category = BleDeviceCategory.SAMSUNG_GENERIC,
                    displayName = if (name.isNotBlank()) name else "Samsung Electronics Device",
                    isSmartTag = false,
                    subtitle = "Samsung Electronics (0x0075)"
                )
            }
        }

        // 5. Apple Find My / AirTag (0x004C, 0xFD6F)
        val hasAppleMfg = rawAd.manufacturerDataMap.containsKey(APPLE_MFG_ID)
        val hasAppleFindMy = rawAd.serviceUuids.any { matchesShortUuid(it, 0xFD6F) }
        if (hasAppleMfg || hasAppleFindMy || name.contains("AirTag", ignoreCase = true)) {
            return Classification(
                category = BleDeviceCategory.APPLE_FIND_MY,
                displayName = if (name.isNotBlank()) name else "Apple Device / AirTag",
                isSmartTag = false,
                subtitle = "Apple Find My Ecosystem"
            )
        }

        // 6. Generic Beacons vs Standard BLE
        if (rawAd.serviceUuids.isNotEmpty() || rawAd.serviceDataMap.isNotEmpty() || rawAd.manufacturerDataMap.isNotEmpty()) {
            return Classification(
                category = BleDeviceCategory.GENERIC_BEACON,
                displayName = if (name.isNotBlank()) name else "BLE Beacon / Peripheral",
                isSmartTag = false,
                subtitle = if (rawAd.serviceUuids.isNotEmpty()) "Service: ${rawAd.serviceUuids.first().toString().take(8)}..." else "BLE Peripheral"
            )
        }

        return Classification(
            category = BleDeviceCategory.GENERIC_BLE,
            displayName = if (name.isNotBlank()) name else "BLE Device (${rawAddress?.takeLast(5) ?: "Unresolved"})",
            isSmartTag = false,
            subtitle = "Standard BLE Device"
        )
    }

    private fun matchesShortUuid(uuid: UUID, short16Bit: Int): Boolean {
        val mostSig = uuid.mostSignificantBits
        val extracted16 = ((mostSig ushr 32) and 0xFFFFL).toInt()
        val hexShort = "%04x".format(short16Bit).lowercase()
        return extracted16 == short16Bit || uuid.toString().lowercase().startsWith("0000$hexShort")
    }
}
