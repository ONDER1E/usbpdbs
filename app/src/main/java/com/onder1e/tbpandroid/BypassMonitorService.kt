package com.onder1e.tbpandroid

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class BypassMonitorService : Service() {

    companion object {
        const val TAG = "BypassMonitor"
        const val NOTIF_ID = 7421
        const val CHANNEL_ID = "tbp_monitor"
        const val ACTION_PAUSE = "com.onder1e.tbpandroid.PAUSE"
        const val ACTION_RESUME = "com.onder1e.tbpandroid.RESUME"
        const val ACTION_EXIT = "com.onder1e.tbpandroid.EXIT"
        const val ACTION_RECOVER = "com.onder1e.tbpandroid.RECOVER"
        const val ACTION_OPEN_SHIZUKU = "com.onder1e.tbpandroid.OPEN_SHIZUKU"
        const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
    }

    private fun getRishPath() = "${filesDir.absolutePath}/rish"

    private var lastToggleTime = 0L
    private var isPaused = false
    private var lastBattery = -1
    private var lastState = -1
    private var shizukuDead = false
    private var isRecovering = false
    private var recoveryJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        if (!shizukuDead && !isRecovering) {
            log("Shizuku binder available on startup")
            return@OnBinderReceivedListener
        }
        if (!isRecovering) {
            log("Shizuku binder received")
            shizukuDead = false
            if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                log("Shizuku ready")
            } else {
                log("Shizuku binder back but permission not granted")
                showPermissionNeededNotification()
            }
            updateNotification()
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        log("WARNING: Shizuku binder died")
        shizukuDead = true
        isRecovering = false
        showShizukuDiedNotification()
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level == -1 || scale == -1) return
            val battery = (level / scale.toFloat() * 100).toInt()
            lastBattery = battery
            log("Battery: $battery%")
            if (!isPaused && !shizukuDead) checkAndToggle(battery)
            if (!shizukuDead) updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        SettingsManager.init(this)
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Starting...", false))
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        log("--- Monitor started ---")
        log("Enable threshold: ${SettingsManager.enableThreshold}%")
        log("Disable threshold: ${SettingsManager.disableThreshold}%")
        log("Min toggle interval: ${SettingsManager.minStateSeconds}s")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra("command")) {
            "pause" -> {
                isPaused = true
                log("Monitor paused by user")
                updateNotification()
            }
            "resume" -> {
                isPaused = false
                log("Monitor resumed by user")
                updateNotification()
            }
            "recover" -> {
                if (!isRecovering) {
                    log("Manual recovery triggered by user")
                    attemptShizukuRecovery()
                }
            }
            "open_shizuku" -> {
                log("Opening Shizuku for permission grant")
                try {
                    val shizukuIntent = packageManager.getLaunchIntentForPackage(SHIZUKU_PKG)
                    shizukuIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    shizukuIntent?.let { startActivity(it) }
                } catch (e: Exception) {
                    log("Failed to launch Shizuku: ${e.message}")
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        recoveryJob?.cancel()
        log("--- Monitor stopped ---")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun attemptShizukuRecovery() {
        recoveryJob?.cancel()
        isRecovering = true
        showShizukuRecoveringNotification()
        recoveryJob = serviceScope.launch {
            val success = ShizukuRecovery.run(this@BypassMonitorService, getRishPath()) { msg ->
                log(msg)
            }
            isRecovering = false
            if (success) {
                shizukuDead = false
                log("Recovery: service restored successfully")
                updateNotification()
            } else {
                log("Recovery: failed")
                showShizukuDeadNotification()
            }
        }
    }

    private fun checkAndToggle(battery: Int) {
        if (!isShizukuAvailable()) {
            log("WARNING: Shizuku not available")
            return
        }
        val enableThreshold = SettingsManager.enableThreshold
        val disableThreshold = SettingsManager.disableThreshold
        val desired = when {
            battery >= enableThreshold -> 1
            battery <= disableThreshold -> 0
            else -> {
                log("Battery $battery% in hysteresis zone -- no change")
                return
            }
        }
        val now = System.currentTimeMillis()
        val minStateMs = SettingsManager.minStateSeconds * 1000L
        if (now - lastToggleTime < minStateMs) {
            log("Cooldown active -- skipping toggle")
            return
        }
        val current = readPassThrough()
        if (current != desired) {
            log("Changing pass_through $current -> $desired")
            runShizukuCommand("settings put system pass_through $desired")
            lastToggleTime = now
            lastState = desired
            log("pass_through set to $desired - OK")
        } else {
            log("pass_through already $current -- no change")
        }
    }

    private fun readPassThrough(): Int {
        return try {
            runShizukuCommand("settings get system pass_through").trim().toIntOrNull() ?: 0
        } catch (e: Exception) { 0 }
    }

    private fun runShizukuCommand(cmd: String): String {
        return try {
            val process = ProcessBuilder("sh", getRishPath(), "-c", cmd)
                .redirectErrorStream(true).start()
            val result = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            result
        } catch (e: Exception) {
            log("ERROR: Command failed: $cmd -- ${e.message}")
            Log.e(TAG, "Command failed", e)
            ""
        }
    }

    private fun isShizukuAvailable(): Boolean {
        return try {
            !shizukuDead &&
            Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { false }
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        LogBuffer.log(message)
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification("Battery $lastBattery%", lastState == 1))
    }

    // Shown immediately when Shizuku dies -- user taps to start recovery
    private fun showShizukuDiedNotification() {
        log("Shizuku has died -- tap notification to recover")
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Shizuku has died")
            .setContentText("Tap to revive")
            .setOngoing(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(makePendingIntent(ACTION_RECOVER, 3))
            .addAction(0, "Exit", makePendingIntent(ACTION_EXIT, 2))
            .build())
    }

    // Shown while recovery is running
    private fun showShizukuRecoveringNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setContentTitle("Recovering...")
            .setContentText("Listening for heartbeat")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Exit", makePendingIntent(ACTION_EXIT, 2))
            .build())
    }

    // Shown when recovery times out -- tap retries
    private fun showShizukuDeadNotification() {
        log("Shizuku recovery failed -- tap to retry")
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Shizuku recovery failed")
            .setContentText("Tap to retry")
            .setOngoing(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(makePendingIntent(ACTION_RECOVER, 3))
            .addAction(0, "Exit", makePendingIntent(ACTION_EXIT, 2))
            .build())
    }

    private fun showPermissionNeededNotification() {
        log("Shizuku permission not granted -- tap notification to open Shizuku")
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Grant Shizuku Permission")
            .setContentText("Tap to open Shizuku and authorise TBP Android")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(makePendingIntent(ACTION_OPEN_SHIZUKU, 4))
            .addAction(0, "Exit", makePendingIntent(ACTION_EXIT, 2))
            .build())
    }

    private fun makePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(action).apply {
            component = ComponentName(this@BypassMonitorService, ControlReceiver::class.java)
        }
        return PendingIntent.getBroadcast(
            this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun buildNotification(content: String, bypassing: Boolean): android.app.Notification {
        val title = when {
            isPaused    -> "Monitor Paused"
            bypassing   -> "USB PD Bypass Active"
            else        -> "USB PD Bypass Disabled"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (isPaused) builder.addAction(0, "Resume", makePendingIntent(ACTION_RESUME, 1))
        else builder.addAction(0, "Pause", makePendingIntent(ACTION_PAUSE, 0))
        builder.addAction(0, "Exit", makePendingIntent(ACTION_EXIT, 2))
        return builder.build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}