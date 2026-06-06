package com.aarishkhan.aarishai

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.Toast

class MemoryClipboardCaptureActivity : Activity() {
    companion object {
        const val EXTRA_SLOT = "slot"
        const val EXTRA_PERMANENT = "permanent"
        const val PREF = "aarish_memory_slots_v1"
        const val MARKER = "AARISH_MEMORY_FOREGROUND_CLIPBOARD_V9_ACTIVITY"
        const val GHOST_MARKER = "AARISH_GHOST_CLIPBOARD_SCREEN_V10_ACTIVITY"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var alreadyCaptured = false

    override fun onCreate(savedInstanceState: Bundle?) {
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)
        installGhostWindowV10()
        scheduleCaptureV10(55L)
        scheduleForceCloseV10(650L)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        overridePendingTransition(0, 0)
        if (intent != null) setIntent(intent)
        alreadyCaptured = false
        installGhostWindowV10()
        scheduleCaptureV10(45L)
        scheduleForceCloseV10(650L)
    }

    override fun onResume() {
        super.onResume()
        installGhostWindowV10()
        scheduleCaptureV10(40L)
    }

    override fun onPause() {
        overridePendingTransition(0, 0)
        super.onPause()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    private fun scheduleCaptureV10(delayMs: Long) {
        mainHandler.postDelayed({
            captureClipboardToMemorySlotV10()
        }, delayMs.coerceIn(0L, 250L))
    }

    private fun scheduleForceCloseV10(delayMs: Long) {
        mainHandler.postDelayed({
            finishGhostV10()
        }, delayMs.coerceIn(250L, 1200L))
    }

    private fun installGhostWindowV10() {
        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        } catch (_: Throwable) {
        }

        try {
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.decorView.setBackgroundColor(Color.TRANSPARENT)
            window.decorView.alpha = 0f

            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)

            val params = window.attributes
            params.width = 1
            params.height = 1
            params.alpha = 0f
            params.dimAmount = 0f
            params.gravity = Gravity.TOP or Gravity.START
            params.x = 0
            params.y = 0
            params.windowAnimations = 0
            window.attributes = params

            window.setDimAmount(0f)
            window.setLayout(1, 1)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun cleanTextV10(value: String?): String {
        return value.orEmpty()
            .replace("\u0000", " ")
            .replace(Regex("[\\t\\r]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            .take(50000)
    }

    private fun validTextV10(value: String?): Boolean {
        val t = cleanTextV10(value)
        if (t.isBlank()) return false
        if (t == "AarishAI") return false
        if (t.equals("Copied", ignoreCase = true)) return false
        if (t.equals("Copy", ignoreCase = true)) return false
        if (t.equals("Paste", ignoreCase = true)) return false
        if (t.equals("Clipboard", ignoreCase = true)) return false
        return true
    }

    private fun captureClipboardToMemorySlotV10() {
        if (alreadyCaptured) return
        alreadyCaptured = true

        val slot = intent.getIntExtra(EXTRA_SLOT, 1).coerceIn(1, 6)
        val permanent = intent.getBooleanExtra(EXTRA_PERMANENT, false)

        val text = try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = cm?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                cleanTextV10(clip.getItemAt(0)?.coerceToText(this)?.toString())
            } else {
                ""
            }
        } catch (_: Throwable) {
            ""
        }

        val ok = validTextV10(text)
        val prefs = getSharedPreferences(PREF, Context.MODE_PRIVATE)

        if (ok) {
            val key = if (permanent) "perm_$slot" else "temp_$slot"
            prefs.edit()
                .putString(key, text)
                .putLong("last_capture_v9_ms", android.os.SystemClock.uptimeMillis())
                .putInt("last_capture_v9_slot", slot)
                .putBoolean("last_capture_v9_permanent", permanent)
                .putBoolean("last_capture_v9_ok", true)
                .apply()

            Toast.makeText(
                this,
                if (permanent) "📌 Permanent saved" else "📥 M$slot saved",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            prefs.edit()
                .putLong("last_capture_v9_ms", android.os.SystemClock.uptimeMillis())
                .putInt("last_capture_v9_slot", slot)
                .putBoolean("last_capture_v9_permanent", permanent)
                .putBoolean("last_capture_v9_ok", false)
                .apply()

            Toast.makeText(this, "Clipboard empty: pehle normal Copy karo", Toast.LENGTH_SHORT).show()
        }

        finishGhostV10()
    }

    private fun finishGhostV10() {
        try {
            mainHandler.removeCallbacksAndMessages(null)
        } catch (_: Throwable) {
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask()
            } else {
                finish()
            }
        } catch (_: Throwable) {
            try { finish() } catch (_: Throwable) {}
        }

        try { overridePendingTransition(0, 0) } catch (_: Throwable) {}
    }
}
