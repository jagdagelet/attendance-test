package com.focusguard.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class BlockedAppsActivity : AppCompatActivity() {

    private lateinit var prefsManager: PrefsManager
    private lateinit var adapter: AppListAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchBar: EditText
    private lateinit var tvSelectedCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_apps)

        prefsManager = PrefsManager(this)

        recyclerView = findViewById(R.id.recyclerApps)
        searchBar = findViewById(R.id.searchBar)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)

        val blockedSet = prefsManager.blockedApps.toMutableSet()

        recyclerView.layoutManager = LinearLayoutManager(this)

        // Load installed user apps
        val apps = loadInstalledApps()

        adapter = AppListAdapter(apps, blockedSet) { packageName, isBlocked ->
            if (isBlocked) {
                prefsManager.addBlockedApp(packageName)
            } else {
                prefsManager.removeBlockedApp(packageName)
            }
            updateSelectedCount()
        }

        recyclerView.adapter = adapter
        updateSelectedCount()

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun loadInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val installedApps = pm.getInstalledApplications(0)

        return installedApps
            .filter { app ->
                // Only show user-installed apps (not system apps), but also include some common ones
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            }
            .filter { app ->
                // Don't show ourselves
                app.packageName != packageName
            }
            .filter { app ->
                // Must be launchable
                pm.getLaunchIntentForPackage(app.packageName) != null
            }
            .map { app ->
                AppInfo(
                    packageName = app.packageName,
                    appName = pm.getApplicationLabel(app).toString(),
                    icon = pm.getApplicationIcon(app)
                )
            }
            .sortedWith(compareByDescending<AppInfo> {
                // Show blocked apps first
                prefsManager.blockedApps.contains(it.packageName)
            }.thenBy {
                it.appName.lowercase()
            })
    }

    private fun updateSelectedCount() {
        val count = prefsManager.blockedApps.size
        tvSelectedCount.text = "$count app${if (count != 1) "s" else ""} selected"
    }
}
