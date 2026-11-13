package com.focusguard.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Independent kernel-level Watchdog that wakes up periodically (every 3 minutes)
 * to verify the AppBlockerService is running while the user is clocked in.
 * If the service was killed due to memory pressure or app closing, this revives it.
 */
class WatchdogReceiver : BroadcastReceiver() {

    companion object {
        private const val WATCHDOG_INTERVAL_MS = 3 * 60 * 1000L // 3 minutes

        fun schedule(context: Context) {
            val intent = Intent(context, WatchdogReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 101, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val triggerAt = System.currentTimeMillis() + WATCHDOG_INTERVAL_MS

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            } catch (_: SecurityException) {
                // In case exact alarm permission is restricted on Android 14, fallback to inexact
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        }

        fun cancel(context: Context) {
            val intent = Intent(context, WatchdogReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 101, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.cancel(pendingIntent)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = PrefsManager(context)
        if (prefs.isClockedIn) {
            // Ensure the service is running and enforcing the app block
            AppBlockerService.start(context)
            // Schedule the next watchdog heartbeat
            schedule(context)
        }
    }
}
