package com.onder1e.usbpdbs

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import rikka.shizuku.Shizuku
import java.io.File

class PermissionsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_permissions, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPermissions(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { setupPermissions(it) }
    }

    private fun setupPermissions(view: View) {
        val ctx = context ?: return

        // --- Shizuku ---
        val shizukuStatus = view.findViewById<TextView>(R.id.tvShizukuPermStatus)
        val shizukuBtn = view.findViewById<Button>(R.id.btnGrantShizuku)
        val shizukuInstalled = isPackageInstalled("moe.shizuku.privileged.api")
        val shizukuRunning = try { Shizuku.pingBinder() } catch (e: Exception) { false }
        val shizukuGranted = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { false }

        shizukuStatus.text = when {
            !shizukuInstalled -> "Shizuku: Not installed"
            !shizukuRunning   -> "Shizuku: Not running"
            !shizukuGranted   -> "Shizuku: Permission not granted"
            else              -> "Shizuku: Granted"
        }
        shizukuStatus.setTextColor(if (shizukuGranted) 0xFF00CC00.toInt() else 0xFFCC0000.toInt())
        shizukuBtn.text = when {
            !shizukuInstalled -> "Install Shizuku"
            !shizukuRunning   -> "Open Shizuku"
            !shizukuGranted   -> "Grant Permission"
            else              -> "Granted"
        }
        shizukuBtn.isEnabled = !shizukuGranted || !shizukuRunning || !shizukuInstalled
        shizukuBtn.setOnClickListener {
            when {
                !shizukuInstalled -> startActivity(
                    Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=moe.shizuku.privileged.api"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                !shizukuRunning -> ctx.packageManager
                    .getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.let { startActivity(it) }
                !shizukuGranted -> try {
                    Shizuku.requestPermission(1001)
                } catch (e: Exception) {
                    LogBuffer.log("Failed to request Shizuku permission: ${e.message}")
                }
            }
        }

        // --- Notifications ---
        val notifStatus = view.findViewById<TextView>(R.id.tvNotifPermStatus)
        val notifBtn = view.findViewById<Button>(R.id.btnGrantNotif)
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true

        notifStatus.text = if (notifGranted) "Notifications: Granted" else "Notifications: Not granted"
        notifStatus.setTextColor(if (notifGranted) 0xFF00CC00.toInt() else 0xFFCC0000.toInt())
        notifBtn.text = if (notifGranted) "Granted" else "Grant Permission"
        notifBtn.isEnabled = !notifGranted
        notifBtn.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2001)
            }
        }

        // --- Display over other apps ---
        val overlayStatus = view.findViewById<TextView>(R.id.tvOverlayPermStatus)
        val overlayBtn = view.findViewById<Button>(R.id.btnGrantOverlay)
        val overlayGranted = Settings.canDrawOverlays(ctx)

        overlayStatus.text = if (overlayGranted) "Display over apps: Granted" else "Display over apps: Not granted"
        overlayStatus.setTextColor(if (overlayGranted) 0xFF00CC00.toInt() else 0xFFCC0000.toInt())
        overlayBtn.text = if (overlayGranted) "Granted" else "Grant Permission"
        overlayBtn.isEnabled = !overlayGranted
        overlayBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        // --- Battery optimisation ---
        val batteryStatus = view.findViewById<TextView>(R.id.tvBatteryPermStatus)
        val batteryBtn = view.findViewById<Button>(R.id.btnGrantBattery)
        val pm = ctx.getSystemService(android.os.PowerManager::class.java)
        val batteryOptIgnored = pm.isIgnoringBatteryOptimizations(ctx.packageName)

        batteryStatus.text = if (batteryOptIgnored)
            "Battery Optimisation: Unrestricted"
        else
            "Battery Optimisation: Restricted"
        batteryStatus.setTextColor(if (batteryOptIgnored) 0xFF00CC00.toInt() else 0xFFCC0000.toInt())
        batteryBtn.text = if (batteryOptIgnored) "Unrestricted" else "Set Unrestricted"
        batteryBtn.isEnabled = !batteryOptIgnored
        batteryBtn.setOnClickListener {
            AlertDialog.Builder(ctx)
                .setTitle("Battery Optimisation")
                .setMessage("This will open Battery settings. Set TBP Android to Unrestricted.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // --- Write Secure Settings ---
        val secureStatus = view.findViewById<TextView>(R.id.tvSecureSettingsStatus)
        val secureBtn = view.findViewById<Button>(R.id.btnGrantSecureSettings)
        val hasSecureSettings = ctx.checkSelfPermission(
            "android.permission.WRITE_SECURE_SETTINGS"
        ) == PackageManager.PERMISSION_GRANTED

        secureStatus.text = if (hasSecureSettings)
            "Write Secure Settings: Granted (WiFi toggle enabled)"
        else
            "Write Secure Settings: Not granted (WiFi toggle disabled in recovery)"
        secureStatus.setTextColor(if (hasSecureSettings) 0xFF00CC00.toInt() else 0xFFCC0000.toInt())
        secureBtn.text = if (hasSecureSettings) "Granted" else "Grant via Shizuku"
        secureBtn.isEnabled = !hasSecureSettings && shizukuGranted
        secureBtn.setOnClickListener {
            try {
                val rish = File(ctx.filesDir, "rish").absolutePath
                val process = ProcessBuilder(
                    "sh", rish, "-c",
                    "pm grant com.onder1e.usbpdbs android.permission.WRITE_SECURE_SETTINGS"
                ).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                LogBuffer.log("Grant WRITE_SECURE_SETTINGS: ${output.ifBlank { "OK" }}")
                Toast.makeText(ctx, "Write Secure Settings granted", Toast.LENGTH_SHORT).show()
                setupPermissions(view)
            } catch (e: Exception) {
                Toast.makeText(ctx, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // --- WiFi section removed from here ---

        // --- Rish Setup ---
        val rishStatus = view.findViewById<TextView>(R.id.tvRishStatus)
        val rishBtn = view.findViewById<Button>(R.id.btnCopyRish)
        val rishFile = File(ctx.filesDir, "rish")
        val rishDexFile = File(ctx.filesDir, "rish_shizuku.dex")
        val rishReady = rishFile.exists() && rishDexFile.exists()

        rishStatus.text = if (rishReady) "Rish: Ready" else "Rish: Not set up"
        rishStatus.setTextColor(if (rishReady) 0xFF00CC00.toInt() else 0xFFCC0000.toInt())
        rishBtn.text = if (rishReady) "Re-copy Rish" else "Copy Rish from /sdcard"
        rishBtn.setOnClickListener {
            try {
                val destRish = File(ctx.filesDir, "rish").absolutePath
                val destDex = File(ctx.filesDir, "rish_shizuku.dex").absolutePath

                val setupCmd = "mkdir -p ${ctx.filesDir.absolutePath} && " +
                        "cp /sdcard/rish /data/local/tmp/rish && " +
                        "cp /sdcard/rish_shizuku.dex /data/local/tmp/rish_shizuku.dex && " +
                        "chmod 666 /data/local/tmp/rish* && " +
                        "cp /data/local/tmp/rish $destRish && " +
                        "cp /data/local/tmp/rish_shizuku.dex $destDex && " +
                        "chmod 755 $destRish && " +
                        "chmod 644 $destDex && " +
                        "rm /data/local/tmp/rish /data/local/tmp/rish_shizuku.dex"

                val process = ProcessBuilder("sh", "-c", setupCmd).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()

                if (rishFile.exists() && rishDexFile.exists()) {
                    Toast.makeText(ctx, "Rish setup successful", Toast.LENGTH_SHORT).show()
                    setupPermissions(view)
                } else {
                    Toast.makeText(ctx, "Setup failed: $output", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return try {
            requireContext().packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: Exception) { false }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        view?.let { setupPermissions(it) }
    }
}