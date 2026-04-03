package com.onder1e.usbpdbs

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

object ShizukuRecovery {

    private const val TAG = "ShizukuRecovery"
    const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
    private const val TERMUX_PKG = "com.termux"
    private const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
    private const val TERMUX_BIN = "/data/data/com.termux/files/usr/bin"

    suspend fun run(context: Context, rish: String, log: (String) -> Unit): Boolean {

        val installed = try {
            context.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
            true
        } catch (e: Exception) { false }

        if (!installed) {
            log("Recovery: Shizuku not installed")
            return false
        }

        val wifiManager = context.getSystemService(WifiManager::class.java)
        val wifiWasOff = !wifiManager.isWifiEnabled

        // Step 1 - WiFi on via Termux RUN_COMMAND
        if (wifiWasOff) {
            log("Recovery: enabling WiFi via Termux...")
            val sent = sendTermuxCommand(context, "termux-wifi-enable true")
            if (sent) {
                log("Recovery: WiFi command sent")
                delay(1500) // give WiFi time to come up
            } else {
                log("Recovery: Termux command failed -- ensure Termux is installed and allow-external-apps=true")
            }
        } else {
            log("Recovery: WiFi already on")
        }

        // Step 2 - Launch Shizuku
        log("Recovery: launching Shizuku...")
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PKG)
                ?: throw Exception("no launch intent")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            log("Recovery: failed to launch Shizuku -- ${e.message}")
            if (wifiWasOff) sendTermuxCommand(context, "termux-wifi-enable false")
            return false
        }

        // Step 3 - Wait for binder
        log("Recovery: listening for heartbeat...")
        val binderCame = withTimeoutOrNull(15_000L) {
            suspendCancellableCoroutine { continuation ->
                val listener = Shizuku.OnBinderReceivedListener {
                    if (continuation.isActive) continuation.resume(true)
                }
                Shizuku.addBinderReceivedListenerSticky(listener)
                continuation.invokeOnCancellation {
                    Shizuku.removeBinderReceivedListener(listener)
                }
            }
        }

        if (binderCame != true) {
            log("Recovery: timeout -- Shizuku did not respond")
            if (wifiWasOff) sendTermuxCommand(context, "termux-wifi-enable false")
            return false
        }

        // Step 4 - Binder back, rish available -- dismiss Shizuku UI
        log("Recovery: heartbeat received -- dismissing Shizuku UI...")
        dismissShizukuUI(rish, log)

        // Step 5 - WiFi off if we turned it on
        if (wifiWasOff) {
            log("Recovery: disabling WiFi via Termux...")
            sendTermuxCommand(context, "termux-wifi-enable false")
        }

        log("Recovery: complete")
        return true
    }

    // Send a background command to Termux via RUN_COMMAND intent
    // Requires allow-external-apps=true in ~/.termux/termux.properties
    fun sendTermuxCommand(context: Context, command: String): Boolean {
        return try {
            val termuxInstalled = try {
                context.packageManager.getPackageInfo(TERMUX_PKG, 0)
                true
            } catch (e: Exception) { false }

            if (!termuxInstalled) {
                Log.e(TAG, "Termux not installed")
                return false
            }

            val intent = Intent().apply {
                setClassName(TERMUX_PKG, TERMUX_SERVICE)
                action = "com.termux.RUN_COMMAND"
                putExtra("com.termux.RUN_COMMAND_PATH", "$TERMUX_BIN/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            }
            context.startForegroundService(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "sendTermuxCommand failed: $command -- ${e.message}")
            false
        }
    }

    // Direct WiFi toggle via WRITE_SECURE_SETTINGS as fallback
    fun setWifiEnabled(context: Context, enabled: Boolean): Boolean {
        return try {
            android.provider.Settings.Global.putInt(
                context.contentResolver,
                "wifi_on",
                if (enabled) 1 else 0
            )
            @Suppress("DEPRECATION")
            context.getSystemService(WifiManager::class.java).isWifiEnabled = enabled
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "setWifiEnabled: WRITE_SECURE_SETTINGS not granted")
            false
        } catch (e: Exception) {
            Log.e(TAG, "setWifiEnabled failed: ${e.message}")
            false
        }
    }

    private fun dismissShizukuUI(rish: String, log: (String) -> Unit) {
        try {
            var attempts = 0
            while (attempts < 20) {
                Thread.sleep(500)
                val process = ProcessBuilder("sh", rish, "-c", "am force-stop $SHIZUKU_PKG")
                    .redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
                if (output.isEmpty()) {
                    log("Recovery: Shizuku UI closed after ${attempts + 1} attempt(s)")
                    return
                }
                log("Recovery: dismiss attempt ${attempts + 1} -- $output")
                attempts++
            }
            log("Recovery: could not dismiss Shizuku UI after 20 attempts")
        } catch (e: Exception) {
            log("Recovery: dismiss error -- ${e.message}")
        }
    }

    private fun getForegroundApp(rish: String): String {
        return try {
            val cmd = "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | head -n 1"
            val process = ProcessBuilder("sh", rish, "-c", cmd)
                .redirectErrorStream(true).start()
            val raw = process.inputStream.bufferedReader().readText()
            process.waitFor()
            Regex("""u0 ([^/]+)/""").find(raw)?.groupValues?.get(1)?.trim() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "getForegroundApp failed", e)
            ""
        }
    }

    private fun runCmd(rish: String, cmd: String) {
        try {
            ProcessBuilder("sh", rish, "-c", cmd)
                .redirectErrorStream(true).start().waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "runCmd failed: $cmd", e)
        }
    }
}