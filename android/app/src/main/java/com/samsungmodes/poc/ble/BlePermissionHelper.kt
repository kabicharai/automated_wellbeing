package com.samsungmodes.poc.ble

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Helper to manage dynamic runtime permissions required for BLE Proximity scanning,
 * background services, and Samsung Mode automation.
 */
object BlePermissionHelper {

    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()

        // Bluetooth permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        // Location is required for BLE beacon discovery across Android versions
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // Notification permission for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permissions.toTypedArray()
    }

    fun hasAllPermissions(context: Context): Boolean {
        return getRequiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    data class PermissionStatus(
        val allGranted: Boolean,
        val hasBluetoothScan: Boolean,
        val hasBluetoothConnect: Boolean,
        val hasFineLocation: Boolean,
        val hasCoarseLocation: Boolean,
        val hasNotification: Boolean,
        val missingPermissions: List<String>
    )

    fun checkPermissionStatus(context: Context): PermissionStatus {
        val missing = mutableListOf<String>()

        val btScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            if (!granted) missing.add(Manifest.permission.BLUETOOTH_SCAN)
            granted
        } else {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
            if (!granted) missing.add(Manifest.permission.BLUETOOTH)
            granted
        }

        val btConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (!granted) missing.add(Manifest.permission.BLUETOOTH_CONNECT)
            granted
        } else {
            true
        }

        val fineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineLoc) missing.add(Manifest.permission.ACCESS_FINE_LOCATION)

        val coarseLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!coarseLoc) missing.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) missing.add(Manifest.permission.POST_NOTIFICATIONS)
            granted
        } else {
            true
        }

        return PermissionStatus(
            allGranted = missing.isEmpty(),
            hasBluetoothScan = btScan,
            hasBluetoothConnect = btConnect,
            hasFineLocation = fineLoc,
            hasCoarseLocation = coarseLoc,
            hasNotification = notif,
            missingPermissions = missing
        )
    }

    fun openAppSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
