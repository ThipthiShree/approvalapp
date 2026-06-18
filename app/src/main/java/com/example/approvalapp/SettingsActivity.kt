package com.example.approvalapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.approvalapp.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val NOTIF_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val appPrefs  = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val userPrefs = getSharedPreferences("UserPrefs",   MODE_PRIVATE)

        // ── Back ──
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // ── Show email ──
        val email = appPrefs.getString("logged_in_user", null)
            ?: userPrefs.getString("email", null)
            ?: userPrefs.getString("username", "Unknown")
        binding.tvLoggedInEmail.text = email

        // ── Notifications ──
        binding.switchNotifications.isChecked = isNotifGranted()

        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> {
                        appPrefs.edit().putBoolean("notifications", true).apply()
                        Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show()
                    }
                    isNotifGranted() -> {
                        appPrefs.edit().putBoolean("notifications", true).apply()
                        Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show()
                    }
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        this, Manifest.permission.POST_NOTIFICATIONS
                    ) || !appPrefs.getBoolean("notif_asked_before", false) -> {
                        appPrefs.edit().putBoolean("notif_asked_before", true).apply()
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            NOTIF_PERMISSION_CODE
                        )
                        binding.switchNotifications.isChecked = false
                    }
                    else -> {
                        binding.switchNotifications.isChecked = false
                        Toast.makeText(
                            this,
                            "Enable notifications in App Settings",
                            Toast.LENGTH_LONG
                        ).show()
                        openAppSettings()
                    }
                }
            } else {
                appPrefs.edit().putBoolean("notifications", false).apply()
                Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isNotifGranted()) {
                    Toast.makeText(
                        this,
                        "To fully disable, turn off in App Settings",
                        Toast.LENGTH_LONG
                    ).show()
                    openAppSettings()
                }
            }
        }
    }

    // ── Check real notification permission ──
    private fun isNotifGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // ── Open app system settings ──
    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }

    // ── Permission result ──
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIF_PERMISSION_CODE) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
            getSharedPreferences("AppSettings", MODE_PRIVATE)
                .edit().putBoolean("notifications", granted).apply()
            binding.switchNotifications.isChecked = granted
            Toast.makeText(
                this,
                if (granted) "Notifications enabled"
                else "Permission denied — enable in App Settings",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ── Re-check when returning from system settings ──
    override fun onResume() {
        super.onResume()
        binding.switchNotifications.isChecked = isNotifGranted()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}