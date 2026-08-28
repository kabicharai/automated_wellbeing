package com.samsungmodes.poc.proximity.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.samsungmodes.poc.MainActivity
import com.samsungmodes.poc.proximity.model.ProximityState

/**
 * Android Foreground Service providing 24/7 background BLE scanning & proximity automation.
 * Prevents Samsung One UI / Android OS from throttling or killing the BLE proximity scanner
 * when the screen is turned off or when the app is placed in the background.
 */
class ProximityForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "samsung_modes_proximity_channel"
        const val CHANNEL_NAME = "Proximity & Modes Automation"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SERVICE = "com.samsungmodes.poc.action.START_FOREGROUND"
        const val ACTION_STOP_SERVICE = "com.samsungmodes.poc.action.STOP_FOREGROUND"
        const val ACTION_PAUSE_15M = "com.samsungmodes.poc.action.PAUSE_15M"
        const val ACTION_RESUME = "com.samsungmodes.poc.action.RESUME"

        private var isServiceRunning = false

        fun isRunning(): Boolean = isServiceRunning

        fun start(context: Context) {
            val intent = Intent(context, ProximityForegroundService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ProximityForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.stopService(intent)
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): ProximityForegroundService = this@ProximityForegroundService
    }

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    private var notificationManager: NotificationManager? = null

    // Cached status for notification updates
    private var currentProximityState: ProximityState = ProximityState.UNKNOWN
    private var currentFilteredRssi: Double? = null
    private var targetDeviceName: String = "No Target Beacon"
    private var isAutomationActive: Boolean = true
    private var activeModeUuid: String? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // Acquire partial wake lock to ensure CPU keeps running BLE callbacks smoothly in sleep
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SamsungModes:ProximityForegroundWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // Safe 10-minute timeout refreshed by active scans
            }
        } catch (_: Exception) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
    }

    fun updateStatus(
        state: ProximityState,
        filteredRssi: Double?,
        deviceName: String,
        automationEnabled: Boolean,
        modeUuid: String?
    ) {
        currentProximityState = state
        currentFilteredRssi = filteredRssi
        targetDeviceName = deviceName
        isAutomationActive = automationEnabled
        activeModeUuid = modeUuid

        // Refresh wake lock keep-alive
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.acquire(10 * 60 * 1000L)
            }
        } catch (_: Exception) {
        }

        notificationManager?.notify(NOTIFICATION_ID, buildNotification())
    }

    @SuppressLint("LaunchActivityFromNotification")
    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stateTitle = when (currentProximityState) {
            ProximityState.INSIDE -> "🟢 Inside Proximity Zone"
            ProximityState.OUTSIDE -> "⚪ Outside Proximity Zone"
            ProximityState.UNKNOWN -> "🟡 Searching for Beacon..."
        }

        val rssiStr = currentFilteredRssi?.let { "%.1f dBm".format(it) } ?: "-- dBm"
        val modeStr = if (!activeModeUuid.isNullOrBlank()) "Mode #$activeModeUuid" else "No Mode Bound"
        val autoStr = if (isAutomationActive) "Automation: Active" else "Automation: Paused"

        val bodyText = "$targetDeviceName • $rssiStr • $modeStr ($autoStr)"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Samsung Modes • $stateTitle")
            .setContentText(bodyText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time Samsung Modes BLE Proximity tracking and automation state"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
