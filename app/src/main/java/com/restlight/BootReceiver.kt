package com.restlight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val running = c.getSharedPreferences("restlight", Context.MODE_PRIVATE).getBoolean("running", false)
        if (i.action == Intent.ACTION_BOOT_COMPLETED && running) {
            c.startForegroundService(Intent(c, BreakService::class.java))
        }
    }
}
