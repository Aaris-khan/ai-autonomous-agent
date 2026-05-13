package com.aarishkhan.aarishai

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var btnScreenCommand: Button
    private var isWaitingForPermission = false
    private var lastPermissionLaunchAt = 0L
    private var notificationPermissionAskedThisSession = false
    private var lastPermissionScreen: String? = null
    private var autoPermissionPromptDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isWaitingForPermission = savedInstanceState?.getBoolean(KEY_WAITING_PERMISSION, false) ?: false
        lastPermissionLaunchAt = savedInstanceState?.getLong(KEY_LAST_PERMISSION_LAUNCH_AT, 0L) ?: 0L
        notificationPermissionAskedThisSession = savedInstanceState?.getBoolean(KEY_NOTIFICATION_ASKED, false) ?: false
        lastPermissionScreen = savedInstanceState?.getString(KEY_LAST_PERMISSION_SCREEN)
        autoPermissionPromptDone = savedInstanceState?.getBoolean(KEY_AUTO_PERMISSION_PROMPT_DONE, false) ?: false

        setContentView(R.layout.activity_main)

        btnScreenCommand = findViewById(R.id.btnScreenCommand)
        btnScreenCommand.setOnClickListener {
            lastPermissionScreen = null
            autoPermissionPromptDone = false
            startScreenCommandSystem(forceOpenSettings = true)
        }
    }

    override fun onResume() {
        super.onResume()

        if (isWaitingForPermission) {
            isWaitingForPermission = false
            val returnedFrom = lastPermissionScreen
            lastPermissionScreen = null

            if (hasOverlayPermission() && isAccessibilityServiceEnabled()) {
                autoPermissionPromptDone = false
                startScreenCommandSystem(forceOpenSettings = false)
                return
            }

            if (returnedFrom == PERMISSION_SCREEN_OVERLAY && hasOverlayPermission() && !isAccessibilityServiceEnabled()) {
                lastPermissionLaunchAt = 0L
                startScreenCommandSystem(forceOpenSettings = true)
                return
            }

            Toast.makeText(
                this,
                "Permission enable karke SCREEN COMMAND dobara dabao",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if ((!hasOverlayPermission() || !isAccessibilityServiceEnabled()) && !autoPermissionPromptDone) {
            autoPermissionPromptDone = true
            startScreenCommandSystem(forceOpenSettings = true)
        }
    }

    private fun startScreenCommandSystem(forceOpenSettings: Boolean) {
        if (isFinishing || isDestroyed) return

        if (!hasOverlayPermission()) {
            if (forceOpenSettings && canLaunchPermissionScreenSafely()) {
                openOverlayPermissionScreen()
            }
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            if (forceOpenSettings && canLaunchPermissionScreenSafely()) {
                openAccessibilityPermissionScreen()
            }
            return
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(POST_NOTIFICATIONS_PERMISSION) != PackageManager.PERMISSION_GRANTED &&
            !notificationPermissionAskedThisSession
        ) {
            notificationPermissionAskedThisSession = true
            requestPermissions(arrayOf(POST_NOTIFICATIONS_PERMISSION), REQUEST_NOTIFICATION_PERMISSION)
            return
        }

        autoPermissionPromptDone = false

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

    private fun openOverlayPermissionScreen() {
        Toast.makeText(this, "Overlay permission ON karo", Toast.LENGTH_LONG).show()
        isWaitingForPermission = true
        lastPermissionScreen = PERMISSION_SCREEN_OVERLAY
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            isWaitingForPermission = false
            lastPermissionScreen = null
            Toast.makeText(this, "Overlay settings open nahi hui: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openAccessibilityPermissionScreen() {
        Toast.makeText(this, "Accessibility Service ON karo", Toast.LENGTH_LONG).show()
        isWaitingForPermission = true
        lastPermissionScreen = PERMISSION_SCREEN_ACCESSIBILITY
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            isWaitingForPermission = false
            lastPermissionScreen = null
            Toast.makeText(this, "Accessibility settings open nahi hui: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun canLaunchPermissionScreenSafely(minGapMs: Long = 1200L): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastPermissionLaunchAt < minGapMs) return false
        lastPermissionLaunchAt = now
        return true
    }

    private fun hasOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                    this,
                    "Notification permission deny hai. Service phir bhi start ho sakti hai.",
                    Toast.LENGTH_LONG
                ).show()
            }
            startScreenCommandSystem(forceOpenSettings = false)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, AutoActionService::class.java)
        val expectedFull = expected.flattenToString()
        val expectedShort = expected.flattenToShortString()

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)

        for (service in splitter) {
            val item = service.trim()
            if (item.equals(expectedFull, ignoreCase = true) ||
                item.equals(expectedShort, ignoreCase = true)
            ) {
                return true
            }

            val enabled = ComponentName.unflattenFromString(item)
            if (enabled != null &&
                enabled.packageName == expected.packageName &&
                enabled.className == expected.className
            ) {
                return true
            }
        }

        return false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_WAITING_PERMISSION, isWaitingForPermission)
        outState.putLong(KEY_LAST_PERMISSION_LAUNCH_AT, lastPermissionLaunchAt)
        outState.putBoolean(KEY_NOTIFICATION_ASKED, notificationPermissionAskedThisSession)
        outState.putString(KEY_LAST_PERMISSION_SCREEN, lastPermissionScreen)
        outState.putBoolean(KEY_AUTO_PERMISSION_PROMPT_DONE, autoPermissionPromptDone)
    }

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 5001
        private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"
        private const val PERMISSION_SCREEN_OVERLAY = "overlay"
        private const val PERMISSION_SCREEN_ACCESSIBILITY = "accessibility"
        private const val KEY_WAITING_PERMISSION = "waiting_permission"
        private const val KEY_LAST_PERMISSION_LAUNCH_AT = "last_permission_launch_at"
        private const val KEY_NOTIFICATION_ASKED = "notification_asked"
        private const val KEY_LAST_PERMISSION_SCREEN = "last_permission_screen"
        private const val KEY_AUTO_PERMISSION_PROMPT_DONE = "auto_permission_prompt_done"
    }
}
