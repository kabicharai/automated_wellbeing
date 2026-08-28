package com.samsungmodes.poc.proximity.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AutomationEntryAction(val displayName: String, val description: String) {
    TURN_ON("Turn Mode ON", "Starts the selected Samsung Mode upon entering proximity"),
    TURN_OFF("Turn Mode OFF", "Stops the selected Samsung Mode (Exclusion Zone / Safe Zone)"),
    NONE("Do Nothing", "Ignores entry transition (exit only rule)")
}

enum class AutomationExitAction(val displayName: String, val description: String) {
    TURN_OFF("Turn Mode OFF", "Stops the selected Samsung Mode upon leaving proximity"),
    TURN_ON("Turn Mode ON", "Starts the selected Samsung Mode upon leaving proximity"),
    RESTORE_PREVIOUS("Restore Previous Mode", "Restores the phone mode that was active before entering"),
    NONE("Do Nothing", "Ignores exit transition (entry only rule)")
}

/**
 * Multi-Device Proximity Automation Rule.
 * Binds a specific BLE Device/Beacon to a Samsung Mode with explicit entry & exit triggers.
 */
data class AutomationRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Proximity Rule",
    val deviceKey: String,
    val deviceDisplayName: String = "BLE Beacon",
    val targetModeUuid: String,
    val targetModeName: String = "Selected Mode",
    val entryAction: AutomationEntryAction = AutomationEntryAction.TURN_ON,
    val exitAction: AutomationExitAction = AutomationExitAction.TURN_OFF,
    val priority: Int = 1, // 1 = highest priority
    val isEnabled: Boolean = true,
    val timeConstraintEnabled: Boolean = false,
    val timeStart: String = "00:00", // "HH:mm"
    val timeEnd: String = "23:59",   // "HH:mm"
    val daysOfWeek: List<String> = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"),
    val notes: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("deviceKey", deviceKey)
        obj.put("deviceDisplayName", deviceDisplayName)
        obj.put("targetModeUuid", targetModeUuid)
        obj.put("targetModeName", targetModeName)
        obj.put("entryAction", entryAction.name)
        obj.put("exitAction", exitAction.name)
        obj.put("priority", priority)
        obj.put("isEnabled", isEnabled)
        obj.put("timeConstraintEnabled", timeConstraintEnabled)
        obj.put("timeStart", timeStart)
        obj.put("timeEnd", timeEnd)
        
        val daysArray = JSONArray()
        daysOfWeek.forEach { daysArray.put(it) }
        obj.put("daysOfWeek", daysArray)

        obj.put("notes", notes)
        obj.put("createdAtMillis", createdAtMillis)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): AutomationRule {
            val daysList = mutableListOf<String>()
            if (obj.has("daysOfWeek")) {
                val arr = obj.getJSONArray("daysOfWeek")
                for (i in 0 until arr.length()) {
                    daysList.add(arr.getString(i))
                }
            } else {
                daysList.addAll(listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"))
            }

            return AutomationRule(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "Proximity Rule"),
                deviceKey = obj.optString("deviceKey", ""),
                deviceDisplayName = obj.optString("deviceDisplayName", "BLE Beacon"),
                targetModeUuid = obj.optString("targetModeUuid", ""),
                targetModeName = obj.optString("targetModeName", "Selected Mode"),
                entryAction = try {
                    AutomationEntryAction.valueOf(obj.optString("entryAction", AutomationEntryAction.TURN_ON.name))
                } catch (e: Exception) {
                    AutomationEntryAction.TURN_ON
                },
                exitAction = try {
                    AutomationExitAction.valueOf(obj.optString("exitAction", AutomationExitAction.TURN_OFF.name))
                } catch (e: Exception) {
                    AutomationExitAction.TURN_OFF
                },
                priority = obj.optInt("priority", 1),
                isEnabled = obj.optBoolean("isEnabled", true),
                timeConstraintEnabled = obj.optBoolean("timeConstraintEnabled", false),
                timeStart = obj.optString("timeStart", "00:00"),
                timeEnd = obj.optString("timeEnd", "23:59"),
                daysOfWeek = daysList,
                notes = obj.optString("notes", ""),
                createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis())
            )
        }
    }
}
