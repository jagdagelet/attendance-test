package com.focusguard.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class AppBlockerService : Service() {

    companion object {
        const val CHANNEL_ID = "focus_guard_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.focusguard.app.STOP_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, AppBlockerService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AppBlockerService::class.java)
            context.stopService(intent)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var usageStatsManager: UsageStatsManager? = null
    private var lastQueryTime = 0L
    private var currentForeground: String? = null
    private var lastLockApp: String? = null
    private var lastLockTime = 0L
    private var transitionBackTime = 0L
    private var transitionBackPackage: String? = null
    private lateinit var prefsManager: PrefsManager

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkForeground()
            handler.postDelayed(this, 900L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefsManager = PrefsManager(this)
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefsManager.isClockedIn = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Backdate lastQueryTime by 2 seconds on startup so we catch recent transitions
        lastQueryTime = System.currentTimeMillis() - 2000L

        handler.post(pollRunnable)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        super.onDestroy()
    }

    private fun checkForeground() {
        val usm = usageStatsManager ?: return
        val now = System.currentTimeMillis()

        try {
            val events = usm.queryEvents(lastQueryTime, now)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    currentForeground = event.packageName
                }
            }
        } catch (_: Exception) {
            return
        }

        lastQueryTime = now

        val fg = currentForeground ?: return

        // Don't block ourselves
        if (fg == packageName) return

        // Don't block system UI, launcher, settings
        if (fg.startsWith("com.android.") || fg == "com.google.android.apps.nexuslauncher" ||
            fg == "com.sec.android.app.launcher" || fg == "android" ||
            fg == "com.android.settings") {
            return
        }

        // Check if it's in the blocked list
        val blockedApps = prefsManager.blockedApps
        if (!blockedApps.contains(fg)) return

        // Guard: transition back from our lock overlay (give 3 seconds grace)
        if (fg == transitionBackPackage &&
            (now - transitionBackTime) < 3000) {
            return
        }

        // Guard: lock already showing
        if (LockOverlayActivity.isShowing) return

        // Guard: debounce same package within 1 second
        if (fg == lastLockApp && (now - lastLockTime) < 1000) return

        // Launch lock overlay
        lastLockApp = fg
        lastLockTime = now

        val lockIntent = Intent(this, LockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("blocked_package", fg)
        }
        startActivity(lockIntent)
    }

    /**
     * Called by LockOverlayActivity when user taps "Go Back" to mark a transition.
     * We ignore the blocked package for 3 seconds to prevent re-triggering.
     */
    fun markTransitionBack(packageName: String) {
        transitionBackPackage = packageName
        transitionBackTime = System.currentTimeMillis()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Attendance Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitors apps during attendance session"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AppBlockerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⏰ Attendance Active")
            .setContentText("App blocking is enabled. Tap to open.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_delete, "Clock Out", stopPending)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
