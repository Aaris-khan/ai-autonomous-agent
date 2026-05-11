package com.aarishkhan.aarishai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var btnScreenCommand: Button
    private var isWaitingForPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnScreenCommand = findViewById(R.id.btnScreenCommand)
                        btnScreenCommand.setOnClickListener {
            startScreenCommandSystem()
        }
        
            }

    override fun onResume() {
        super.onResume()
        if (isWaitingForPermission) {
            // Agar user permission settings se BACK aaya hai
            isWaitingForPermission = false
            if (Settings.canDrawOverlays(this) && isAccessibilityServiceEnabled()) {
                startScreenCommandSystem()
            }
        } else {
            // JAISE HI APP OPEN HOGA (Fresh ya Background se), turant permission check hogi
            if (!Settings.canDrawOverlays(this) || !isAccessibilityServiceEnabled()) {
                startScreenCommandSystem()
            }
        }
    }

    private fun startScreenCommandSystem() {
                if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission ON karo", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            isWaitingForPermission = true
            startActivity(intent)
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Accessibility Service ON karo", Toast.LENGTH_LONG).show()
            isWaitingForPermission = true
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5001)
            return
        }

        if (FloatingControlService.instance != null) {
            moveTaskToBack(true)
            return
        }

        try {
            val serviceIntent = Intent(this, FloatingControlService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            moveTaskToBack(true)
        } catch (e: Exception) {
            Toast.makeText(this, "Service start nahi hua: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
                if (requestCode == 5001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScreenCommandSystem()
            } else {
                Toast.makeText(
                    this,
                    "Notification permission deny hai. Service start karne ke liye permission ON karo.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

        private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = android.content.ComponentName(this, AutoActionService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any { it.equals(expectedService, ignoreCase = true) }
    }
}
