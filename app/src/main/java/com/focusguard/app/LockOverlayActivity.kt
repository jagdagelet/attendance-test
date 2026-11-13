package com.focusguard.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView

class LockOverlayActivity : AppCompatActivity() {

    companion object {
        @Volatile
        var isShowing = false
    }

    private var blockedPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isShowing = true

        // Keep screen on and show over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContentView(R.layout.activity_lock_overlay)

        blockedPackage = intent.getStringExtra("blocked_package")

        // Try to get the app name
        val appName = try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(blockedPackage ?: "", 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            blockedPackage ?: "This app"
        }

        findViewById<TextView>(R.id.blockedAppName).text = appName
        findViewById<TextView>(R.id.blockedMessage).text =
            "This app is blocked during your attendance session."

        findViewById<Button>(R.id.btnGoBack).setOnClickListener {
            goHome()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Update blocked package if we get a new intent (singleInstance)
        blockedPackage = intent?.getStringExtra("blocked_package")
        val appName = try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(blockedPackage ?: "", 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            blockedPackage ?: "This app"
        }
        findViewById<TextView>(R.id.blockedAppName).text = appName
    }

    private fun goHome() {
        // Go to home screen
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    override fun onBackPressed() {
        // Don't allow back to the blocked app, go home instead
        goHome()
    }

    override fun onDestroy() {
        isShowing = false
        super.onDestroy()
    }
}
