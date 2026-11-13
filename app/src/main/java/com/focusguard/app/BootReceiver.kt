package com.focusguard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PrefsManager(context)
            if (prefs.isClockedIn) {
                // User was clocked in when phone rebooted, restart the service
                AppBlockerService.start(context)
            }
        }
    }
}
