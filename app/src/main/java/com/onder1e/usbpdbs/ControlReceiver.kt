package com.onder1e.usbpdbs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, BypassMonitorService::class.java)
        when (intent.action) {
            BypassMonitorService.ACTION_PAUSE -> {
                serviceIntent.putExtra("command", "pause")
                context.startService(serviceIntent)
            }
            BypassMonitorService.ACTION_RESUME -> {
                serviceIntent.putExtra("command", "resume")
                context.startService(serviceIntent)
            }
            BypassMonitorService.ACTION_EXIT -> {
                context.stopService(serviceIntent)
            }
            BypassMonitorService.ACTION_RECOVER -> {
                serviceIntent.putExtra("command", "recover")
                context.startService(serviceIntent)
            }
            BypassMonitorService.ACTION_OPEN_SHIZUKU -> {
                serviceIntent.putExtra("command", "open_shizuku")
                context.startService(serviceIntent)
            }
        }
    }
}