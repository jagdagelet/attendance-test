package com.focusguard.app

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefsManager: PrefsManager
    private lateinit var btnClockInOut: Button
    private lateinit var btnManageApps: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvElapsed: TextView
    private lateinit var tvBlockedCount: TextView

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            updateElapsedTime()
            timerHandler.postDelayed(this, 1000L)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Notification permission needed for the service", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefsManager = PrefsManager(this)

        btnClockInOut = findViewById(R.id.btnClockInOut)
        btnManageApps = findViewById(R.id.btnManageApps)
        tvStatus = findViewById(R.id.tvStatus)
        tvElapsed = findViewById(R.id.tvElapsed)
        tvBlockedCount = findViewById(R.id.tvBlockedCount)

        btnClockInOut.setOnClickListener {
            if (prefsManager.isClockedIn) {
                clockOut()
            } else {
                clockIn()
            }
        }

        btnManageApps.setOnClickListener {
            startActivity(Intent(this, BlockedAppsActivity::class.java))
        }

        if (prefsManager.isFirstRun) {
            autoSelectClockApp()
            prefsManager.isFirstRun = false
        }

        updateUI()
    }

    private fun autoSelectClockApp() {
        try {
            val pm = packageManager
            val installedApps = pm.getInstalledApplications(0)
            val clockPackages = setOf(
                "com.google.android.deskclock",
                "com.sec.android.app.clockpackage",
                "com.android.deskclock",
                "com.coloros.alarm",
                "com.oneplus.deskclock",
                "com.xiaomi.clock",
                "com.huawei.deskclock"
            )
            for (app in installedApps) {
                if (clockPackages.contains(app.packageName) ||
                    app.packageName.contains("deskclock", ignoreCase = true)) {
                    prefsManager.addBlockedApp(app.packageName)
                    break
                }
            }
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        if (prefsManager.isClockedIn) {
            timerHandler.post(timerRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        timerHandler.removeCallbacks(timerRunnable)
    }

    private fun clockIn() {
        // Check permissions first
        if (!hasUsageStatsPermission()) {
            showUsageStatsDialog()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            showOverlayDialog()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        if (prefsManager.blockedApps.isEmpty()) {
            Toast.makeText(this, "Please add some apps to block first!", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, BlockedAppsActivity::class.java))
            return
        }

        // Request exemption from battery optimization so OEM killers don't stop the service
        checkBatteryOptimization()

        // All good - clock in
        prefsManager.isClockedIn = true
        prefsManager.clockInTime = System.currentTimeMillis()

        AppBlockerService.start(this)

        timerHandler.post(timerRunnable)
        updateUI()

        Toast.makeText(this, "✅ Clocked in! App blocking is active.", Toast.LENGTH_SHORT).show()
    }

    private fun clockOut() {
        prefsManager.isClockedIn = false

        AppBlockerService.stop(this)

        timerHandler.removeCallbacks(timerRunnable)
        updateUI()

        Toast.makeText(this, "⏹ Clocked out. Apps are unblocked.", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        val isClockedIn = prefsManager.isClockedIn
        val blockedCount = prefsManager.blockedApps.size

        if (isClockedIn) {
            btnClockInOut.text = "CLOCK OUT"
            btnClockInOut.setBackgroundResource(R.drawable.rounded_button_red)
            tvStatus.text = "🟢  CLOCKED IN"
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.green))
            updateElapsedTime()
        } else {
            btnClockInOut.text = "CLOCK IN"
            btnClockInOut.setBackgroundResource(R.drawable.rounded_button)
            tvStatus.text = "⚪  CLOCKED OUT"
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            tvElapsed.text = "00:00:00"
        }

        tvBlockedCount.text = "$blockedCount app${if (blockedCount != 1) "s" else ""} blocked"
    }

    private fun updateElapsedTime() {
        if (!prefsManager.isClockedIn) return
        val elapsed = System.currentTimeMillis() - prefsManager.clockInTime
        val hours = (elapsed / 3600000).toInt()
        val minutes = ((elapsed % 3600000) / 60000).toInt()
        val seconds = ((elapsed % 60000) / 1000).toInt()
        tvElapsed.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun hasUsageStatsPermission(): Boolean = try {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) { false }

    private fun showUsageStatsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Usage Access Required")
            .setMessage(
                "FocusGuard needs Usage Access permission to detect which app is in the foreground.\n\n" +
                "This is used ONLY to check if a blocked app is open - we never read your screen content.\n\n" +
                "Tap OK to open Settings, then find FocusGuard and enable access."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOverlayDialog() {
        AlertDialog.Builder(this)
            .setTitle("Overlay Permission Required")
            .setMessage(
                "FocusGuard needs the \"Display over other apps\" permission to show the block screen over restricted apps.\n\n" +
                "Tap OK to open Settings and enable it for FocusGuard."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
