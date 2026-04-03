package com.onder1e.tbpandroid

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private val mainFragment = MainFragment()
    private val settingsFragment = SettingsFragment()
    private val permissionsFragment = PermissionsFragment()
    private lateinit var bottomNav: BottomNavigationView

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        updateShizukuStatus()
        // Refresh permissions fragment if visible
        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) is PermissionsFragment) {
            permissionsFragment.onResume()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.init(this)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottomNav)
        loadFragment(mainFragment)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_main -> { loadFragment(mainFragment); true }
                R.id.nav_settings -> { loadFragment(settingsFragment); true }
                R.id.nav_permissions -> { loadFragment(permissionsFragment); true }
                else -> false
            }
        }

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        updateShizukuStatus()

        // Handle intent from notification (e.g. tab=permissions)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: android.content.Intent) {
        if (intent.getStringExtra("tab") == "permissions") {
            bottomNav.selectedItemId = R.id.nav_permissions
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun updateShizukuStatus() {
        try {
            val running = Shizuku.pingBinder()
            val granted = Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
            val status = when {
                !running -> "Shizuku: Not running"
                !granted -> "Shizuku: Permission not granted -- tap Start to request"
                else -> "Shizuku: Ready"
            }
            mainFragment.updateShizukuStatus(status)
        } catch (e: Exception) {
            mainFragment.updateShizukuStatus("Shizuku: Not available")
        }
    }

    fun requestShizukuIfNeeded() {
        try {
            if (Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(1001)
            }
        } catch (e: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        updateShizukuStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    }
}
