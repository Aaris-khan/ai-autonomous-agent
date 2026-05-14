package com.aarishkhan.aarishai

import android.os.SystemClock

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import android.Manifest

class MainActivity : Activity() {
    private lateinit var btnScreenCommand: Button
    private lateinit var btnExportData: Button
    private lateinit var btnRestoreData: Button

    private var isWaitingForPermission = false
    private var lastPermissionLaunchAt = 0L
    private var notificationPermissionAskedThisSession = false
    private var lastPermissionScreen: String? = null
    private var autoPermissionPromptDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isWaitingForPermission = savedInstanceState?.getBoolean(KEY_WAITING_PERMISSION, false) ?: false
        lastPermissionLaunchAt = 0L
        notificationPermissionAskedThisSession = savedInstanceState?.getBoolean(KEY_NOTIFICATION_ASKED, false) ?: false
        lastPermissionScreen = savedInstanceState?.getString(KEY_LAST_PERMISSION_SCREEN)
        autoPermissionPromptDone = savedInstanceState?.getBoolean(KEY_AUTO_PERMISSION_PROMPT_DONE, false) ?: false

        setContentView(R.layout.activity_main)

        btnScreenCommand = findViewById(R.id.btnScreenCommand)
        btnExportData = findViewById(R.id.btnExportData)
        btnRestoreData = findViewById(R.id.btnRestoreData)

        btnScreenCommand.setOnClickListener {
            autoPermissionPromptDone = false
            lastPermissionScreen = null
            autoPermissionPromptDone = false
            startScreenCommandSystem(forceOpenSettings = true)
        }

        btnExportData.setOnClickListener {
            exportBackupWithPicker()
        }

        btnRestoreData.setOnClickListener {
            restoreBackupWithPicker()
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

            if (returnedFrom == "overlay" && hasOverlayPermission() && !isAccessibilityServiceEnabled()) {
                startScreenCommandSystem(forceOpenSettings = true)
                return
            }

            Toast.makeText(this, "Permission enable karke SCREEN COMMAND dobara dabao", Toast.LENGTH_SHORT).show()
            return
        }

        if ((!hasOverlayPermission() || !isAccessibilityServiceEnabled()) && !autoPermissionPromptDone) {
            autoPermissionPromptDone = true
            startScreenCommandSystem(forceOpenSettings = true)
        }
    }


    private fun exportBackupWithPicker() {
        try {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "AarishAI_Backup.json")
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_EXPORT_BACKUP)
        } catch (e: Exception) {
            Toast.makeText(this, "Export picker open nahi hua: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun restoreBackupWithPicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_IMPORT_BACKUP)
        } catch (e: Exception) {
            Toast.makeText(this, "Restore picker open nahi hua: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Using Activity result for compatibility with simple Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        when (requestCode) {
            REQUEST_EXPORT_BACKUP -> writeBackupToUri(uri)
            REQUEST_IMPORT_BACKUP -> readBackupFromUri(uri)
        }
    }

    private fun writeBackupToUri(uri: Uri) {
        try {
            val backup = buildBackupJson().toString(2)
            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(backup.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: error("Output stream null")

            Toast.makeText(this, "✅ Backup save ho gaya", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Backup fail: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun readBackupFromUri(uri: Uri) {
        try {
            val jsonText = contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: error("Input stream null")

            restoreBackupJson(JSONObject(jsonText))
            Toast.makeText(this, "✅ Restore complete! App restart karo.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Restore fail: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildBackupJson(): JSONObject {
        val prefs = getSharedPreferences(PREF_SCREEN_COMMAND_STORE, Context.MODE_PRIVATE)
        val root = JSONObject()
        val data = JSONObject()

        root.put("format", "AarishAI_Backup_v2")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("packageName", packageName)

        for ((key, value) in prefs.all) {
            val item = JSONObject()
            when (value) {
                is String -> {
                    item.put("type", "String")
                    item.put("value", value)
                }
                is Int -> {
                    item.put("type", "Int")
                    item.put("value", value)
                }
                is Long -> {
                    item.put("type", "Long")
                    item.put("value", value)
                }
                is Float -> {
                    item.put("type", "Float")
                    item.put("value", value.toDouble())
                }
                is Boolean -> {
                    item.put("type", "Boolean")
                    item.put("value", value)
                }
                is Set<*> -> {
                    item.put("type", "StringSet")
                    val arr = JSONArray()
                    value.filterIsInstance<String>().forEach { arr.put(it) }
                    item.put("value", arr)
                }
                else -> continue
            }
            data.put(key, item)
        }

        root.put("prefs", data)
        return root
    }

    


    

    



    private fun isAarishBackupKey(key: String): Boolean {
        return key == "recorded_gestures" ||
            key == "active_config" ||
            key == "config_list_json" ||
            key == "tap_accuracy_mode" ||
            key.startsWith("config_") ||
            key.startsWith("next_config_") ||
            key.startsWith("loop_mode") ||
            key.startsWith("loop_value")
    }

    private fun restoreBackupJson(root: JSONObject) {
        val typedPrefs = root.optJSONObject("prefs")
        val restoreItems = mutableListOf<Triple<String, String, Any?>>()

        if (typedPrefs != null) {
            val format = root.optString("format", "")

            if (format.isNotBlank() && !format.startsWith("AarishAI_Backup_")) {
                error("Ye AarishAI backup file nahi lag rahi")
            }

            val keys = typedPrefs.keys()
            while (keys.hasNext()) {
                val key = keys.next()

                // Wrong/random JSON se app data destroy na ho.
                if (!isAarishBackupKey(key)) continue

                val item = typedPrefs.optJSONObject(key) ?: continue
                when (val type = item.optString("type")) {
                    "String" -> restoreItems.add(Triple(key, type, item.optString("value", "")))
                    "Int" -> restoreItems.add(Triple(key, type, item.optInt("value", 0)))
                    "Long" -> restoreItems.add(Triple(key, type, item.optLong("value", 0L)))
                    "Float" -> restoreItems.add(Triple(key, type, item.optDouble("value", 0.0).toFloat()))
                    "Boolean" -> restoreItems.add(Triple(key, type, item.optBoolean("value", false)))
                    "StringSet" -> {
                        val arr = item.optJSONArray("value") ?: JSONArray()
                        val set = linkedSetOf<String>()
                        for (i in 0 until arr.length()) {
                            val value = arr.optString(i, "").trim()
                            if (value.isNotBlank()) set.add(value)
                        }
                        restoreItems.add(Triple(key, type, set.toSet()))
                    }
                }
            }
        } else {
            // Legacy old backup support.
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()

                if (key in setOf("format", "exportedAt", "packageName")) continue
                if (!isAarishBackupKey(key)) continue

                when (val value = root.opt(key)) {
                    is String -> restoreItems.add(Triple(key, "String", value))
                    is Int -> restoreItems.add(Triple(key, "Int", value))
                    is Long -> restoreItems.add(Triple(key, "Long", value))
                    is Double -> restoreItems.add(Triple(key, "Float", value.toFloat()))
                    is Boolean -> restoreItems.add(Triple(key, "Boolean", value))
                }
            }
        }

        if (restoreItems.isEmpty()) {
            error("Valid AarishAI backup data nahi mila")
        }

        val hasMacroOrConfigData = restoreItems.any { item ->
            item.first == "recorded_gestures" ||
                item.first == "active_config" ||
                item.first == "config_list_json" ||
                item.first.startsWith("config_")
        }

        if (!hasMacroOrConfigData) {
            error("Backup me macro/config data nahi mila")
        }

        val editor = getSharedPreferences(PREF_SCREEN_COMMAND_STORE, Context.MODE_PRIVATE).edit()
        editor.clear()

        for ((key, type, value) in restoreItems) {
            when (type) {
                "String" -> editor.putString(key, value as? String ?: "")
                "Int" -> editor.putInt(key, value as? Int ?: 0)
                "Long" -> editor.putLong(key, value as? Long ?: 0L)
                "Float" -> editor.putFloat(key, value as? Float ?: 0f)
                "Boolean" -> editor.putBoolean(key, value as? Boolean ?: false)
                "StringSet" -> {
                    val safeSet = (value as? Set<*>)
                        ?.filterIsInstance<String>()
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        ?.toSet()
                        ?: emptySet()
                    editor.putStringSet(key, safeSet)
                }
            }
        }

        if (!editor.commit()) {
            error("SharedPreferences commit fail")
        }

        // Restore ke baad config list corrupt/duplicate ho to auto-repair.
        GestureStore.getAllConfigNames(this)
    }


    private fun startScreenCommandSystem(forceOpenSettings: Boolean) {
        if (!hasOverlayPermission()) {
            if (forceOpenSettings && canLaunchPermissionScreenSafely()) {
                Toast.makeText(this, "Overlay permission ON karo", Toast.LENGTH_LONG).show()
                isWaitingForPermission = true
                lastPermissionScreen = "overlay"
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            if (forceOpenSettings && canLaunchPermissionScreenSafely()) {
                Toast.makeText(this, "Accessibility Service ON karo", Toast.LENGTH_LONG).show()
                isWaitingForPermission = true
                lastPermissionScreen = "accessibility"
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !notificationPermissionAskedThisSession
        ) {
            notificationPermissionAskedThisSession = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATION_PERMISSION)
            return
        }

        autoPermissionPromptDone = false

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
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
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

    private fun canLaunchPermissionScreenSafely(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPermissionLaunchAt < 1200L) return false
        lastPermissionLaunchAt = now
        return true
    }


    private fun canLaunchPermissionScreenSafely(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPermissionLaunchAt < 1200L) return false
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
                Toast.makeText(this, "Notification permission deny hai. Service phir bhi start ho sakti hai.", Toast.LENGTH_LONG).show()
            }
            startScreenCommandSystem(forceOpenSettings = false)
        }
    }


    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = ComponentName(this, AutoActionService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        for (service in splitter) {
            if (service.equals(expectedService, ignoreCase = true)) return true
        }
        return false
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_WAITING_PERMISSION, isWaitingForPermission)
        outState.putLong(KEY_LAST_PERMISSION_LAUNCH_AT, 0L)
        outState.putBoolean(KEY_NOTIFICATION_ASKED, notificationPermissionAskedThisSession)
        outState.putString(KEY_LAST_PERMISSION_SCREEN, lastPermissionScreen)
        outState.putBoolean(KEY_AUTO_PERMISSION_PROMPT_DONE, autoPermissionPromptDone)
    }


    companion object {
        private const val PREF_SCREEN_COMMAND_STORE = "screen_command_store"

        private const val REQUEST_NOTIFICATION_PERMISSION = 5001
        private const val REQUEST_EXPORT_BACKUP = 7001
        private const val REQUEST_IMPORT_BACKUP = 7002

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
