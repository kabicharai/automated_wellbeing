package com.samsungmodes.poc.proximity.storage

import android.content.Context
import android.content.SharedPreferences
import com.samsungmodes.poc.ble.model.BleDeviceId
import com.samsungmodes.poc.ble.model.BleDeviceProfile
import com.samsungmodes.poc.ble.model.BleProximityDevice
import com.samsungmodes.poc.proximity.model.AutomationRule
import com.samsungmodes.poc.proximity.model.ProximityProfile
import com.samsungmodes.poc.proximity.model.RssiDistributionMetrics
import com.samsungmodes.poc.proximity.model.RssiFilterType
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Storage Repository providing persistent storage for BLE Devices, Per-Device Calibration Profiles,
 * and Automation Configurations across app sessions and reinstalls (via Android AutoBackup).
 */
class ProximityStorageRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Auto-restore backup from external documents storage if this is a fresh install or empty prefs
        if (getAllSavedDevices().isEmpty() && getAllAutomationRules().isEmpty()) {
            restoreFromExternalStorage()
        }
    }

    companion object {
        private const val PREFS_NAME = "samsung_modes_proximity_store"
        private const val KEY_SAVED_DEVICES = "saved_ble_devices"
        private const val KEY_DEVICE_PROFILES = "device_proximity_profiles"
        private const val KEY_ACTIVE_DEVICE_KEY = "active_tracked_device_key"
        private const val KEY_MASTER_AUTOMATION = "master_automation_enabled"
        private const val KEY_TARGET_MODE_UUID = "target_samsung_mode_uuid"
        private const val KEY_PAUSE_UNTIL_MILLIS = "automation_pause_until_millis"
        private const val KEY_AUTOMATION_RULES = "automation_rules_list"
    }

    // --- Saved BLE Devices Persistence ---

    fun saveBleDevice(device: BleDeviceProfile) {
        val devices = getAllSavedDevices().toMutableMap()
        devices[device.deviceId.primaryKey] = device
        persistSavedDevicesMap(devices)
    }

    fun getAllSavedDevices(): Map<String, BleDeviceProfile> {
        val rawJson = prefs.getString(KEY_SAVED_DEVICES, null) ?: return emptyMap()
        val result = mutableMapOf<String, BleDeviceProfile>()
        try {
            val jsonArray = JSONArray(rawJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val profile = deserializeDeviceProfile(obj)
                if (profile != null) {
                    result[profile.deviceId.primaryKey] = profile
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    fun getSavedDevice(primaryKey: String): BleDeviceProfile? {
        return getAllSavedDevices()[primaryKey]
    }

    fun removeSavedDevice(primaryKey: String) {
        val devices = getAllSavedDevices().toMutableMap()
        devices.remove(primaryKey)
        persistSavedDevicesMap(devices)
    }

    private fun persistSavedDevicesMap(devices: Map<String, BleDeviceProfile>) {
        val jsonArray = JSONArray()
        devices.values.forEach { device ->
            val obj = serializeDeviceProfile(device)
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_DEVICES, jsonArray.toString()).apply()
        syncBackupToExternalStorage()
    }

    // --- Per-Device Calibration Profiles Persistence ---

    fun saveProximityProfile(profile: ProximityProfile) {
        val profiles = getAllProximityProfiles().toMutableMap()
        profiles[profile.targetDeviceId.primaryKey] = profile
        persistProximityProfilesMap(profiles)
    }

    fun getProximityProfileForDevice(deviceKey: String): ProximityProfile? {
        return getAllProximityProfiles()[deviceKey]
    }

    fun getAllProximityProfiles(): Map<String, ProximityProfile> {
        val rawJson = prefs.getString(KEY_DEVICE_PROFILES, null) ?: return emptyMap()
        val result = mutableMapOf<String, ProximityProfile>()
        try {
            val jsonArray = JSONArray(rawJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val profile = deserializeProximityProfile(obj)
                if (profile != null) {
                    result[profile.targetDeviceId.primaryKey] = profile
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    fun removeProximityProfile(deviceKey: String) {
        val profiles = getAllProximityProfiles().toMutableMap()
        profiles.remove(deviceKey)
        persistProximityProfilesMap(profiles)
    }

    private fun persistProximityProfilesMap(profiles: Map<String, ProximityProfile>) {
        val jsonArray = JSONArray()
        profiles.values.forEach { profile ->
            val obj = serializeProximityProfile(profile)
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_DEVICE_PROFILES, jsonArray.toString()).apply()
        syncBackupToExternalStorage()
    }

    // --- Active Device Key ---

    fun getActiveDeviceKey(): String? {
        return prefs.getString(KEY_ACTIVE_DEVICE_KEY, null)
    }

    fun setActiveDeviceKey(key: String?) {
        if (key == null) {
            prefs.edit().remove(KEY_ACTIVE_DEVICE_KEY).apply()
        } else {
            prefs.edit().putString(KEY_ACTIVE_DEVICE_KEY, key).apply()
        }
    }

    // --- Automation Configuration Persistence ---

    fun isMasterAutomationEnabled(): Boolean {
        return prefs.getBoolean(KEY_MASTER_AUTOMATION, false)
    }

    fun setMasterAutomationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER_AUTOMATION, enabled).apply()
    }

    fun getTargetModeUuid(): String {
        return prefs.getString(KEY_TARGET_MODE_UUID, "") ?: ""
    }

    fun setTargetModeUuid(uuid: String) {
        prefs.edit().putString(KEY_TARGET_MODE_UUID, uuid).apply()
    }

    fun getPauseUntilMillis(): Long {
        return prefs.getLong(KEY_PAUSE_UNTIL_MILLIS, 0L)
    }

    fun setPauseUntilMillis(millis: Long) {
        prefs.edit().putLong(KEY_PAUSE_UNTIL_MILLIS, millis).apply()
    }

    // --- Automation Rules Persistence (Multi-Device / Multi-Mode) ---

    fun getAllAutomationRules(): List<AutomationRule> {
        val rawJson = prefs.getString(KEY_AUTOMATION_RULES, null) ?: return emptyList()
        val result = mutableListOf<AutomationRule>()
        try {
            val jsonArray = JSONArray(rawJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                result.add(AutomationRule.fromJson(obj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result.sortedBy { it.priority }
    }

    fun saveAutomationRule(rule: AutomationRule) {
        val rules = getAllAutomationRules().toMutableList()
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index >= 0) {
            rules[index] = rule
        } else {
            rules.add(rule)
        }
        persistAutomationRules(rules)
    }

    fun deleteAutomationRule(ruleId: String) {
        val rules = getAllAutomationRules().filter { it.id != ruleId }
        persistAutomationRules(rules)
    }

    fun persistAutomationRules(rules: List<AutomationRule>) {
        val jsonArray = JSONArray()
        rules.forEach { rule ->
            jsonArray.put(rule.toJson())
        }
        prefs.edit().putString(KEY_AUTOMATION_RULES, jsonArray.toString()).apply()
        syncBackupToExternalStorage()
    }

    // --- Local Backup & Restore (Survives Uninstalls) ---

    fun exportFullBackupJson(): String {
        val root = JSONObject()
        val devicesJson = prefs.getString(KEY_SAVED_DEVICES, "[]") ?: "[]"
        val profilesJson = prefs.getString(KEY_DEVICE_PROFILES, "[]") ?: "[]"
        val rulesJson = prefs.getString(KEY_AUTOMATION_RULES, "[]") ?: "[]"
        
        root.put("version", 2)
        root.put("timestamp", System.currentTimeMillis())
        root.put("saved_devices", JSONArray(devicesJson))
        root.put("device_profiles", JSONArray(profilesJson))
        root.put("automation_rules", JSONArray(rulesJson))
        root.put("active_device_key", getActiveDeviceKey() ?: "")
        root.put("master_automation", isMasterAutomationEnabled())
        root.put("target_mode_uuid", getTargetModeUuid())
        return root.toString(2)
    }

    fun syncBackupToExternalStorage() {
        try {
            val json = exportFullBackupJson()
            LocalExternalConfigStorage.saveBackup(context, json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun restoreFromExternalStorage(): Boolean {
        try {
            val jsonString = LocalExternalConfigStorage.readBackup(context) ?: return false
            return importFullBackupJson(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun importFullBackupJson(jsonContent: String): Boolean {
        return try {
            val root = JSONObject(jsonContent)
            val editor = prefs.edit()

            if (root.has("saved_devices")) {
                editor.putString(KEY_SAVED_DEVICES, root.getJSONArray("saved_devices").toString())
            }
            if (root.has("device_profiles")) {
                editor.putString(KEY_DEVICE_PROFILES, root.getJSONArray("device_profiles").toString())
            }
            if (root.has("automation_rules")) {
                editor.putString(KEY_AUTOMATION_RULES, root.getJSONArray("automation_rules").toString())
            }
            if (root.has("active_device_key")) {
                val key = root.getString("active_device_key")
                if (key.isNotBlank()) {
                    editor.putString(KEY_ACTIVE_DEVICE_KEY, key)
                }
            }
            if (root.has("master_automation")) {
                editor.putBoolean(KEY_MASTER_AUTOMATION, root.getBoolean("master_automation"))
            }
            if (root.has("target_mode_uuid")) {
                editor.putString(KEY_TARGET_MODE_UUID, root.getString("target_mode_uuid"))
            }
            editor.commit()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- Reset All Data ---

    fun resetAllData() {
        prefs.edit().clear().apply()
    }

    // --- JSON Serialization Helpers ---

    private fun serializeDeviceProfile(device: BleDeviceProfile): JSONObject {
        val obj = JSONObject()
        obj.put("id", device.id)
        obj.put("displayName", device.displayName)
        obj.put("deviceType", device.deviceType.name)
        obj.put("primaryKey", device.deviceId.primaryKey)
        obj.put("address", device.deviceId.macAddress ?: "")
        obj.put("advertisedName", device.deviceId.deviceName ?: "")
        obj.put("manufacturerId", device.deviceId.manufacturerId ?: -1)
        obj.put("targetMacAddress", device.targetMacAddress ?: "")
        obj.put("targetManufacturerId", device.targetManufacturerId ?: -1)
        obj.put("notes", device.notes)
        return obj
    }

    private fun deserializeDeviceProfile(obj: JSONObject): BleDeviceProfile? {
        return try {
            val deviceId = BleDeviceId(
                primaryKey = obj.getString("primaryKey"),
                macAddress = obj.optString("address").ifBlank { null },
                deviceName = obj.optString("advertisedName").ifBlank { null },
                manufacturerId = if (obj.has("manufacturerId") && obj.getInt("manufacturerId") != -1) obj.getInt("manufacturerId") else null
            )
            val deviceType = try {
                BleProximityDevice.DeviceType.valueOf(obj.getString("deviceType"))
            } catch (e: Exception) {
                BleProximityDevice.DeviceType.GENERIC_BEACON
            }

            BleDeviceProfile(
                id = obj.optString("id", UUID.randomUUID().toString()),
                displayName = obj.getString("displayName"),
                deviceType = deviceType,
                deviceId = deviceId,
                targetMacAddress = obj.optString("targetMacAddress").ifBlank { null },
                targetManufacturerId = if (obj.has("targetManufacturerId") && obj.getInt("targetManufacturerId") != -1) obj.getInt("targetManufacturerId") else null,
                notes = obj.optString("notes", "")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun serializeProximityProfile(profile: ProximityProfile): JSONObject {
        val obj = JSONObject()
        obj.put("id", profile.id)
        obj.put("profileName", profile.profileName)
        obj.put("targetPrimaryKey", profile.targetDeviceId.primaryKey)
        obj.put("targetDisplayName", profile.targetDisplayName)
        obj.put("enterThresholdRssi", profile.enterThresholdRssi)
        obj.put("exitThresholdRssi", profile.exitThresholdRssi)
        obj.put("enterDurationSeconds", profile.enterDurationSeconds)
        obj.put("exitDurationSeconds", profile.exitDurationSeconds)
        obj.put("filterType", profile.filterType.name)
        obj.put("filterSmoothingParam", profile.filterSmoothingParam)
        obj.put("windowSampleSize", profile.windowSampleSize)
        obj.put("lostDeviceTimeoutSeconds", profile.lostDeviceTimeoutSeconds)
        obj.put("boundSamsungModeUuid", profile.boundSamsungModeUuid)
        obj.put("isEnabled", profile.isEnabled)
        obj.put("createdAtMillis", profile.createdAtMillis)
        return obj
    }

    private fun deserializeProximityProfile(obj: JSONObject): ProximityProfile? {
        return try {
            val deviceId = BleDeviceId(
                primaryKey = obj.getString("targetPrimaryKey"),
                deviceName = obj.optString("targetDisplayName").ifBlank { null }
            )
            val filterType = try {
                RssiFilterType.valueOf(obj.optString("filterType", "EMA"))
            } catch (e: Exception) {
                RssiFilterType.EMA
            }

            ProximityProfile(
                id = obj.optString("id", UUID.randomUUID().toString()),
                profileName = obj.getString("profileName"),
                targetDeviceId = deviceId,
                targetDisplayName = obj.optString("targetDisplayName", "BLE Device"),
                enterThresholdRssi = obj.optInt("enterThresholdRssi", -64),
                exitThresholdRssi = obj.optInt("exitThresholdRssi", -69),
                enterDurationSeconds = obj.optInt("enterDurationSeconds", 5),
                exitDurationSeconds = obj.optInt("exitDurationSeconds", 10),
                filterType = filterType,
                filterSmoothingParam = obj.optDouble("filterSmoothingParam", 0.25),
                windowSampleSize = obj.optInt("windowSampleSize", 15),
                lostDeviceTimeoutSeconds = obj.optInt("lostDeviceTimeoutSeconds", 30),
                boundSamsungModeUuid = obj.optString("boundSamsungModeUuid", ""),
                isEnabled = obj.optBoolean("isEnabled", true),
                createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
