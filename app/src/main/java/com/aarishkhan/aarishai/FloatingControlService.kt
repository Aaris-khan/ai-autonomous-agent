package com.aarishkhan.aarishai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class FloatingControlService : Service() {

    companion object {
        @Volatile var instance: FloatingControlService? = null

        // Android 14 specialUse foreground-service type = 0x40000000.
        // Literal rakha hai taaki compileSdk mismatch par ServiceInfo constant unresolved na ho.
        private const val FGS_TYPE_SPECIAL_USE_COMPAT = 0x40000000
    }

    fun isRecordingActive(): Boolean = isRecording


    // AARISH_MEMORY_SCOPE_FIX_V2_MEMBER_HELPERS
    fun consumeMemoryCopyArmCode(): Int {
        val code = pendingMemoryCopyCode
        if (code != 0) pendingMemoryCopyCode = 0
        return code
    }

    private fun hideMemoryStripV1() {
        // AARISH_MEMORY_CLIPBOARD_TWOSTEP_V6_HIDE
        memoryStrip?.visibility = View.GONE
        dismissMemoryPopupV5()
    }





    private fun aarishMemoryPopupBgV5(color: Int, stroke: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(17).toFloat()
            setColor(color)
            setStroke(dp(1), stroke)
        }
    }

    private fun aarishMemoryPopupButtonV5(title: String, action: () -> Unit): Button {
        // AARISH_MEMORY_POPUP_BELOW_ICON_V5_BUTTON
        return Button(this).apply {
            text = title
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            textSize = 10.5f
            maxLines = 1
            isSingleLine = true
            isAllCaps = false
            includeFontPadding = false
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(Color.WHITE)
            background = aarishMemoryPopupBgV5(
                Color.rgb(30, 41, 59),
                Color.argb(135, 255, 255, 255)
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(5).toFloat()
                stateListAnimator = null
            }
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(30)).apply {
                setMargins(0, dp(2), 0, dp(2))
            }
        }
    }

    private fun dismissMemoryPopupV5() {
        // AARISH_MEMORY_POPUP_BELOW_ICON_V5_DISMISS
        val popup = memoryPopupView ?: return
        memoryPopupView = null
        try {
            if (popup.parent != null) aarishAccessWmV13().removeViewImmediate(popup)
        } catch (_: Throwable) {
            try {
                if (popup.parent != null) windowManager.removeViewImmediate(popup)
            } catch (_: Throwable) {}
        }
    }


    private fun aarishMemoryTempSlotKeyV6(slot: Int): String {
        return "temp_${slot.coerceIn(1, 6)}"
    }

    private fun aarishClipboardTextV6(): String {
        // AARISH_MEMORY_CLIPBOARD_TWOSTEP_V6_HELPERS
        return try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = cm?.primaryClip ?: return ""
            if (clip.itemCount <= 0) return ""
            clip.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
                .replace("\u0000", " ")
                .replace(Regex("[\\t\\r]+"), " ")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
                .take(50000)
        } catch (_: Throwable) {
            ""
        }
    }

    private fun aarishClearClipboardForMemoryV6() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("AarishAI", ""))
        } catch (_: Throwable) {
        }
    }

    private fun removeClipboardListenerV6() {
        val listener = clipboardListenerV6
        clipboardListenerV6 = null
        if (listener != null) {
            try {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                cm?.removePrimaryClipChangedListener(listener)
            } catch (_: Throwable) {
            }
        }
    }

    private fun cancelClipboardCopyArmV6() {
        pendingClipboardCopySlotV6 = 0
        removeClipboardListenerV6()
    }


    private fun aarishIsValidClipboardMemoryTextV7(value: String?): Boolean {
        // AARISH_MEMORY_CLIPBOARD_V7_CAPTURE_GUARD
        val t = value.orEmpty().trim()
        if (t.isBlank()) return false
        if (t == "AarishAI") return false
        if (t.equals("Copied", ignoreCase = true)) return false
        if (t.equals("Copy", ignoreCase = true)) return false
        return true
    }

    private fun completeClipboardCaptureToSlotV7(slotRaw: Int, source: String): Boolean {
        // AARISH_MEMORY_CLIPBOARD_V7_COMPLETE_CAPTURE
        val slot = slotRaw.coerceIn(1, 6)
        val text = aarishClipboardTextV6()
        if (!aarishIsValidClipboardMemoryTextV7(text)) return false

        getSharedPreferences("aarish_memory_slots_v1", Context.MODE_PRIVATE)
            .edit()
            .putString(aarishMemoryTempSlotKeyV6(slot), text)
            .apply()

        val recorded = if (isRecording && captureView != null) {
            captureView?.addMemoryClipboardCaptureGestureV6(slot) == true
        } else {
            false
        }

        pendingClipboardCopySlotV6 = 0
        removeClipboardListenerV6()
        dismissMemoryPopupV5()

        Toast.makeText(
            this,
            if (recorded) "📥 M$slot saved + recorded" else "📥 M$slot saved",
            Toast.LENGTH_SHORT
        ).show()
        return true
    }

    private fun pollClipboardForMemoryV7(slotRaw: Int, serial: Int, attempt: Int) {
        // AARISH_MEMORY_CLIPBOARD_V7_POLL
        val slot = slotRaw.coerceIn(1, 6)
        if (pendingClipboardCopySlotV6 != slot || pendingClipboardArmSerialV6 != serial) return

        if (completeClipboardCaptureToSlotV7(slot, "poll")) return

        if (attempt >= 160) {
            cancelClipboardCopyArmV6()
            Toast.makeText(this, "M$slot clipboard wait timeout", Toast.LENGTH_SHORT).show()
            return
        }

        handler.postDelayed({
            pollClipboardForMemoryV7(slot, serial, attempt + 1)
        }, 250L)
    }


    private fun onClipboardChangedForMemoryV6() {
        // AARISH_MEMORY_CLIPBOARD_V7_LISTENER_BRIDGE
        val slot = pendingClipboardCopySlotV6.coerceIn(0, 6)
        if (slot <= 0) return
        completeClipboardCaptureToSlotV7(slot, "listener")
    }


    private fun armClipboardCopySlotV6(slotRaw: Int) {
        // AARISH_MEMORY_FOREGROUND_CLIPBOARD_V9_FCS_ARM
        val slot = slotRaw.coerceIn(1, 6)
        if (!isRecording || captureView == null) {
            Toast.makeText(this, "Recording start karo pehle", Toast.LENGTH_SHORT).show()
            return
        }

        cancelClipboardCopyArmV6()
        pendingMemoryCopyCode = 0
        forceNextXyOnlyRecord = false

        // V9 reverse flow:
        // Pehle user normal Android Copy karega, fir C1/C2 select karega.
        // Is tap par foreground Activity clipboard read karke selected slot me save karegi.
        val recorded = captureView?.addMemoryClipboardCaptureGestureV6(slot) == true
        val opened = AutoActionService.aarishMemoryStartForegroundClipboardCaptureV9(
            context = this,
            slot = slot,
            permanent = false
        )

        dismissMemoryPopupV5()
        memoryStrip?.visibility = View.GONE

        Toast.makeText(
            this,
            when {
                !opened -> "Clipboard capture open nahi hua"
                recorded -> "C$slot capture recorded"
                else -> "C$slot capture"
            },
            Toast.LENGTH_SHORT
        ).show()
    }



    private fun memoryPopupBaseParamsV6(width: Int, heightGuess: Int): WindowManager.LayoutParams {
        val loc = IntArray(2)
        try {
            btnMemory.getLocationOnScreen(loc)
        } catch (_: Throwable) {
            loc[0] = resources.displayMetrics.widthPixels - dp(52)
            loc[1] = dp(80)
        }

        val screenW = resources.displayMetrics.widthPixels.coerceAtLeast(dp(120))
        val screenH = resources.displayMetrics.heightPixels.coerceAtLeast(dp(300))
        val iconW = btnMemory.width.takeIf { it > 0 } ?: dp(34)
        val iconH = btnMemory.height.takeIf { it > 0 } ?: dp(34)
        val centerX = loc[0] + iconW / 2

        val x = (centerX - width / 2).coerceIn(dp(2), (screenW - width - dp(2)).coerceAtLeast(dp(2)))
        val yBelow = loc[1] + iconH + dp(6)
        val y = yBelow.coerceIn(dp(34), (screenH - heightGuess - dp(10)).coerceAtLeast(dp(34)))

        return WindowManager.LayoutParams(
            width,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            aarishAccessOverlayTypeV13(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun addMemoryPopupViewV6(popup: LinearLayout, width: Int, heightGuess: Int) {
        dismissMemoryPopupV5()
        memoryStrip?.visibility = View.GONE
        val params = memoryPopupBaseParamsV6(width, heightGuess)
        try {
            aarishAccessWmV13().addView(popup, params)
            memoryPopupView = popup
        } catch (_: Throwable) {
            try {
                windowManager.addView(popup, params)
                memoryPopupView = popup
            } catch (_: Throwable) {
                memoryPopupView = null
                Toast.makeText(this, "Memory menu open nahi hua", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun showMemoryPopupBelowIconV5() {
        // AARISH_MEMORY_CLIPBOARD_TWOSTEP_V6_MODE_MENU
        if (!isRecording || captureView == null) {
            dismissMemoryPopupV5()
            Toast.makeText(this, "Recording start karo pehle", Toast.LENGTH_SHORT).show()
            return
        }

        val popup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = aarishMemoryPopupBgV5(
                Color.argb(242, 15, 23, 42),
                Color.argb(165, 125, 211, 252)
            )
            isClickable = true
            isFocusable = false

            addView(aarishMemoryPopupButtonV5("COPY") { showMemorySlotPopupV6(copyMode = true) }.apply {
                layoutParams = LinearLayout.LayoutParams(dp(74), dp(32)).apply { setMargins(0, dp(2), 0, dp(2)) }
            })
            addView(aarishMemoryPopupButtonV5("PASTE") { showMemorySlotPopupV6(copyMode = false) }.apply {
                layoutParams = LinearLayout.LayoutParams(dp(74), dp(32)).apply { setMargins(0, dp(2), 0, dp(2)) }
            })
        }

        addMemoryPopupViewV6(popup, dp(88), dp(86))
    }

    private fun showMemorySlotPopupV6(copyMode: Boolean) {
        // AARISH_MEMORY_CLIPBOARD_TWOSTEP_V6_SLOT_MENU
        // AARISH_MEMORY_CLIPBOARD_TWOSTEP_V6_SLOT_LABELS: C1 C2 C3 C4 C5 C6 P1 P2 P3 P4 P5 P6
        if (!isRecording || captureView == null) {
            dismissMemoryPopupV5()
            Toast.makeText(this, "Recording start karo pehle", Toast.LENGTH_SHORT).show()
            return
        }

        val popup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = aarishMemoryPopupBgV5(
                Color.argb(242, 15, 23, 42),
                Color.argb(165, 125, 211, 252)
            )
            isClickable = true
            isFocusable = false

            for (slot in 1..6) {
                addView(
                    aarishMemoryPopupButtonV5(if (copyMode) "C$slot" else "P$slot") {
                        if (copyMode) {
                            armClipboardCopySlotV6(slot)
                        } else {
                            recordMemoryPasteV1(slot, false)
                        }
                    }.apply {
                        layoutParams = LinearLayout.LayoutParams(dp(38), dp(30)).apply {
                            setMargins(0, dp(2), 0, dp(2))
                        }
                    }
                )
            }

            addView(aarishMemoryPopupButtonV5("‹") { showMemoryPopupBelowIconV5() }.apply {
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(28)).apply {
                    setMargins(0, dp(3), 0, 0)
                }
            })
        }

        addMemoryPopupViewV6(popup, dp(48), dp(238))
    }



    private fun toggleMemoryStripV1() {
        // AARISH_MEMORY_CLIPBOARD_TWOSTEP_V6_TOGGLE
        memoryStrip?.visibility = View.GONE

        if (memoryPopupView != null) {
            dismissMemoryPopupV5()
            return
        }

        showMemoryPopupBelowIconV5()
    }





    private fun armTempMemoryCopyV1(slot: Int) {
        // AARISH_MEMORY_CLIPBOARD_TWOSTEP_V6_COPY_ARM
        armClipboardCopySlotV6(slot)
    }


    private fun armPermanentMemoryCopyV1() {
        if (!isRecording || captureView == null) {
            Toast.makeText(this, "Recording start karo pehle", Toast.LENGTH_SHORT).show()
            return
        }

        pendingMemoryCopyCode = 101
        forceNextXyOnlyRecord = false
        hideMemoryStripV1()
        Toast.makeText(this, "📌 permanent: ab fixed text par tap karo", Toast.LENGTH_SHORT).show()
    }

    private fun recordMemoryPasteV1(slot: Int, permanent: Boolean) {
        // AARISH_MEMORY_CLIPBOARD_V7_PASTE_SELECT
        if (!isRecording || captureView == null) {
            Toast.makeText(this, "Recording start karo pehle", Toast.LENGTH_SHORT).show()
            return
        }

        cancelClipboardCopyArmV6()

        val safeSlot = slot.coerceIn(1, 6)
        val recorded = captureView?.addMemoryPasteGesture(safeSlot, permanent) == true
        dismissMemoryPopupV5()
        memoryStrip?.visibility = View.GONE

        if (!recorded) {
            Toast.makeText(this, "Memory paste record nahi hua", Toast.LENGTH_SHORT).show()
            return
        }

        // Recording-time live paste preview me slot kabhi clear nahi hoga.
        AutoActionService.aarishMemoryPasteNowV1(
            context = this,
            slot = safeSlot,
            permanent = permanent,
            clearAfter = false
        )

        Toast.makeText(
            this,
            if (permanent) "📌 permanent paste recorded" else "📤 M$safeSlot paste recorded",
            Toast.LENGTH_SHORT
        ).show()
    }






    

    fun consumeNextXyOnlyRecord(): Boolean {
        // AARISH_FORCE_XY_ONLY_CONSUME_V1
        val enabled = forceNextXyOnlyRecord
        if (enabled) forceNextXyOnlyRecord = false
        return enabled
    }

    private fun armNextXyOnlyRecord() {
        // AARISH_FORCE_XY_ONLY_ARM_V1
        if (!isRecording || captureView == null) {
            Toast.makeText(this, "Recording start karo pehle", Toast.LENGTH_SHORT).show()
            return
        }
        forceNextXyOnlyRecord = true
        Toast.makeText(this, "x y coordinates on", Toast.LENGTH_SHORT).show()
    }
fun pokePanelToFront() {
        handler.post {
            if (instance !== this@FloatingControlService) return@post
            if (!::windowManager.isInitialized) return@post

            // Share menu / dialog / chooser window ke baad:
            // Glass first, panel last. Isse DONE/SAVE/CUT buttons glass ke neeche lock nahi honge.
            recoverOverlayStackToFrontNow()
        }
    }


    private lateinit var windowManager: WindowManager
    // AARISH_ACCESSIBILITY_OVERLAY_SAFE_V13_HELPERS
    private var aarishAccessOverlayWmV13: WindowManager? = null

    private fun aarishAccessWmV13(): WindowManager {
        val wm = AutoActionService.getAarishAccessibilityWindowManager()
        if (wm != null) {
            aarishAccessOverlayWmV13 = wm
            return wm
        }
        return aarishAccessOverlayWmV13 ?: windowManager
    }

    private fun aarishAccessOverlayTypeV13(): Int {
        return if (AutoActionService.isAarishAccessibilityServiceReady()) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }
    private var panelView: View? = null
    private var captureView: TouchCaptureView? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var oldPanelX = 30
    private var oldPanelY = 120
    private var panelHiddenForPlayback = false

    private lateinit var label: TextView
    private lateinit var btnStart: Button
    private lateinit var btnApps: Button
    private var aarishDesktopLauncherView: View? = null
    private var aarishDesktopLauncherParams: WindowManager.LayoutParams? = null
    private var btnClear: android.widget.Button? = null
    private lateinit var btnLoop: Button
    private lateinit var btnWorkflow: Button
    private lateinit var btnTools: Button
    private lateinit var btnSystem: Button
    private lateinit var btnAiWait: Button
    private lateinit var btnXyOnly: Button // AARISH_FORCE_XY_ONLY_BUTTON_V1
    private lateinit var btnMemory: Button // AARISH_MEMORY_SLOTS_V1_BUTTON
    private var memoryStrip: LinearLayout? = null // AARISH_MEMORY_SLOTS_V1_STRIP
    private var memoryPopupView: LinearLayout? = null // AARISH_MEMORY_POPUP_BELOW_ICON_V5_FIELD
    private var pendingClipboardCopySlotV6 = 0 // AARISH_MEMORY_CLIPBOARD_TWOSTEP_V6_FIELDS
    private var pendingClipboardArmSerialV6 = 0
    private var clipboardListenerV6: android.content.ClipboardManager.OnPrimaryClipChangedListener? = null
    private lateinit var btnSave: Button
    private lateinit var btnUndo: Button
    private lateinit var btnCut: Button
    private var panelMainRowV13: LinearLayout? = null // AARISH_PANEL_PERMANENT_TWO_ROWS_V13_FIELDS
    private var panelBottomRowV13: LinearLayout? = null
    private var recordingMainRowV12: LinearLayout? = null // AARISH_APP_LAUNCHER_SECOND_ROW_V12_FIELDS
    private var recordingLauncherRowV12: LinearLayout? = null

        private var isRecording = false
    @Volatile private var forceNextXyOnlyRecord = false // AARISH_FORCE_XY_ONLY_STATE_V1
    @Volatile private var pendingMemoryCopyCode = 0 // AARISH_MEMORY_SLOTS_V1_ARM_STATE
    private var pendingDiscardConfirm = false
    private var isSavingRecording = false
    private var unsavedGestures: List<RecordedGesture> = emptyList()
    private val handler = Handler(Looper.getMainLooper())

    // AARISH_ADVANCED_BLINK_STRIKE_FIELDS_V2
    @Volatile private var liveReplayActive = false
    private var liveReplaySerial = 0
    private var lastLiveReplayAt = 0L
    private var lastLiveReplayX = Float.NaN
    private var lastLiveReplayY = Float.NaN
    private val liveReplayQueue = java.util.ArrayDeque<RecordedGesture>()
    private var liveReplayQueueDraining = false
    private var liveReplayFloodToastAt = 0L
    private var lastGhostState: Boolean? = null
    // AARISH_IDLE_TIMER_CANCEL_V1: explicit timer reference for clean cancellation
    private var liveIdleTimerRunnable: Runnable? = null


    private var playbackWatcherRunnable: Runnable? = null
    private var activeConfigDialog: android.app.AlertDialog? = null

    // AARISH_ULTRA_TOUCH_SYSTEM_V2_START

    // AARISH_LIVE_REPLAY_QUEUE_FIX_V1:
    // Fast double-tap miss fix. Short taps wait briefly before pass-through replay,
    // so second tap can still be captured by the glass, then gestures replay in order.
    // AARISH_ULTRA_TOUCH_SYSTEM_V2_END

            override fun onCreate() {
        super.onCreate()
        instance = this
        if (!startForegroundServiceNotification()) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingPanel()
    }

    private fun startForegroundServiceNotification(): Boolean {
        val channelId = "aarishai_floating_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Screen Command Status",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setContentTitle("AarishAI Screen Command")
            .setContentText("Screen Command panel is active")
            .setSmallIcon(R.drawable.ic_stat_aarish)
            .setOngoing(true)
            .build()

        fun startBasicForeground(): Boolean {
            return try {
                startForeground(1, notification)
                true
            } catch (e: Exception) {
                Toast.makeText(this, "Foreground service start nahi hua: ${e.message}", Toast.LENGTH_LONG).show()
                false
            }
        }

        return if (Build.VERSION.SDK_INT >= 34) {
            try {
                startForeground(
                    1,
                    notification,
                    FGS_TYPE_SPECIAL_USE_COMPAT
                )
                true
            } catch (_: Exception) {
                startBasicForeground()
            }
        } else {
            startBasicForeground()
        }
    }


private fun dp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)
}

private fun darken(color: Int, factor: Float): Int {
    return Color.rgb(
        (Color.red(color) * factor).toInt().coerceIn(0, 255),
        (Color.green(color) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color) * factor).toInt().coerceIn(0, 255)
    )
}

private fun stylePanelButton(button: Button, bgColor: Int, fgColor: Int = Color.WHITE, minWdp: Int = 42) {
    val compact = resources.displayMetrics.widthPixels < dp(390)
    val density = resources.displayMetrics.density
    val radius = (if (compact) 14f else 17f) * density
    val targetHeight = if (compact) 34 else 39
    val targetWidth = if (compact) (minWdp - 6).coerceAtLeast(30) else minWdp

    button.minHeight = dp(targetHeight)
    button.minimumHeight = dp(targetHeight)
    button.minWidth = dp(targetWidth)
    button.minimumWidth = dp(targetWidth)
    button.setPadding(dp(if (compact) 4 else 7), dp(1), dp(if (compact) 4 else 7), dp(1))
    button.textSize = if (compact) 9.2f else 10.6f
    button.maxLines = 1
    button.isSingleLine = true
    button.isAllCaps = false
    button.includeFontPadding = false
    button.textAlignment = View.TEXT_ALIGNMENT_CENTER
    button.setTextColor(fgColor)

    (button.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
        lp.setMargins(dp(2), 0, dp(2), 0)
        button.layoutParams = lp
    }

    val normal = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = radius
        setColor(bgColor)
        setStroke(dp(1), Color.argb(104, 255, 255, 255))
    }
    val pressed = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = radius
        setColor(darken(bgColor, 0.78f))
        setStroke(dp(1), Color.argb(170, 255, 255, 255))
    }

    button.background = android.graphics.drawable.StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), pressed)
        addState(intArrayOf(), normal)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        button.elevation = dp(if (compact) 2 else 3).toFloat()
        button.stateListAnimator = null
    }
}


    private fun aarishMemoryRoundBgV3(color: Int, pressedColor: Int): android.graphics.drawable.StateListDrawable {
        // AARISH_MEMORY_ICON_SMALL_V3_BG
        fun oval(c: Int): android.graphics.drawable.GradientDrawable {
            return android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(c)
                setStroke(dp(1), Color.argb(150, 255, 255, 255))
            }
        }

        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), oval(pressedColor))
            addState(intArrayOf(), oval(color))
        }
    }

    private fun aarishMemoryRectBgV3(color: Int, pressedColor: Int): android.graphics.drawable.StateListDrawable {
        fun rect(c: Int): android.graphics.drawable.GradientDrawable {
            return android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(15).toFloat()
                setColor(c)
                setStroke(dp(1), Color.argb(130, 255, 255, 255))
            }
        }

        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rect(pressedColor))
            addState(intArrayOf(), rect(color))
        }
    }


    private fun aarishMemoryRoundBgV4(color: Int, pressedColor: Int): android.graphics.drawable.StateListDrawable {
        fun oval(c: Int): android.graphics.drawable.GradientDrawable {
            return android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(c)
                setStroke(dp(1), Color.argb(150, 255, 255, 255))
            }
        }
        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), oval(pressedColor))
            addState(intArrayOf(), oval(color))
        }
    }

    private fun aarishMemoryDropBgV4(color: Int, pressedColor: Int): android.graphics.drawable.StateListDrawable {
        fun rect(c: Int): android.graphics.drawable.GradientDrawable {
            return android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(15).toFloat()
                setColor(c)
                setStroke(dp(1), Color.argb(140, 255, 255, 255))
            }
        }
        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rect(pressedColor))
            addState(intArrayOf(), rect(color))
        }
    }


    private fun styleMemoryControlsCompactV3() {
        memoryStrip?.visibility = View.GONE // AARISH_MEMORY_POPUP_BELOW_ICON_V5_FORCE_INLINE_GONE

        // AARISH_MEMORY_MENU_VERTICAL_V4_STYLE
        if (::btnMemory.isInitialized) {
            val size = dp(34)
            btnMemory.layoutParams = (btnMemory.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(size, size)).apply {
                width = size
                height = size
                setMargins(dp(2), 0, dp(2), 0)
            }
            btnMemory.minWidth = 0
            btnMemory.minimumWidth = 0
            btnMemory.minHeight = 0
            btnMemory.minimumHeight = 0
            btnMemory.setPadding(0, 0, 0, dp(1))
            btnMemory.text = "📋"
            btnMemory.textSize = 17f
            btnMemory.maxLines = 1
            btnMemory.isSingleLine = true
            btnMemory.isAllCaps = false
            btnMemory.includeFontPadding = false
            btnMemory.gravity = Gravity.CENTER
            btnMemory.textAlignment = View.TEXT_ALIGNMENT_CENTER
            btnMemory.setTextColor(Color.WHITE)
            btnMemory.background = aarishMemoryRoundBgV4(
                Color.rgb(14, 165, 233),
                Color.rgb(3, 105, 161)
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                btnMemory.elevation = dp(4).toFloat()
                btnMemory.stateListAnimator = null
            }
        }

        memoryStrip?.let { strip ->
            strip.orientation = LinearLayout.VERTICAL
            strip.gravity = Gravity.CENTER
            strip.setPadding(dp(3), dp(3), dp(3), dp(3))
            strip.layoutParams = (strip.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(dp(38), LinearLayout.LayoutParams.WRAP_CONTENT)).apply {
                width = dp(38)
                height = LinearLayout.LayoutParams.WRAP_CONTENT
                setMargins(dp(2), 0, dp(2), 0)
            }
            strip.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(Color.argb(238, 15, 23, 42))
                setStroke(dp(1), Color.argb(150, 125, 211, 252))
            }

            for (i in 0 until strip.childCount) {
                val b = strip.getChildAt(i) as? Button ?: continue
                b.layoutParams = (b.layoutParams as? LinearLayout.LayoutParams
                    ?: LinearLayout.LayoutParams(dp(32), dp(30))).apply {
                    width = dp(32)
                    height = dp(30)
                    setMargins(0, dp(1), 0, dp(1))
                }
                b.minWidth = 0
                b.minimumWidth = 0
                b.minHeight = 0
                b.minimumHeight = 0
                b.setPadding(0, 0, 0, 0)
                b.textSize = 10.5f
                b.maxLines = 1
                b.isSingleLine = true
                b.isAllCaps = false
                b.includeFontPadding = false
                b.gravity = Gravity.CENTER
                b.textAlignment = View.TEXT_ALIGNMENT_CENTER
                b.setTextColor(Color.WHITE)
                b.background = aarishMemoryDropBgV4(
                    Color.rgb(30, 41, 59),
                    Color.rgb(51, 65, 85)
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    b.elevation = dp(2).toFloat()
                    b.stateListAnimator = null
                }
            }
        }
    }


    private fun refreshMemoryOverlayLayoutV3() {
        // AARISH_MEMORY_ICON_SMALL_V3_LAYOUT_REFRESH
        styleMemoryControlsCompactV3()
        panelView?.requestLayout()
        val panel = panelView
        val params = panelParams
        if (panel != null && params != null && panel.parent != null) {
            try {
                aarishAccessWmV13().updateViewLayout(panel, params)
            } catch (_: Throwable) {
            }
        }
    }












private fun compactPanelButtonBoxV11(button: Button, widthDp: Int, heightDp: Int = 34, textSp: Float = 10.0f) {
    // AARISH_PANEL_PERMANENT_TWO_ROWS_V13_DISABLE_COMPACT
    // No-op: buttons ko chhota nahi karna.
}

private fun ensureAppLauncherRecordingSlotV11() {
    // AARISH_PANEL_PERMANENT_TWO_ROWS_V13_REDIRECT_OLD_V11
    ensurePanelPermanentTwoRowsV13()
}

private fun ensureRecordingLauncherSecondRowV12() {
    // AARISH_PANEL_PERMANENT_TWO_ROWS_V13_REDIRECT_OLD_V12
    ensurePanelPermanentTwoRowsV13()
}

private fun safeDetachPanelChildV13(view: View?) {
    // AARISH_PANEL_PERMANENT_TWO_ROWS_V13_HELPER
    val v = view ?: return
    try {
        (v.parent as? android.view.ViewGroup)?.removeView(v)
    } catch (_: Throwable) {
    }
}

private fun addPanelChildKeepSizeV13(row: LinearLayout, view: View?) {
    val v = view ?: return
    safeDetachPanelChildV13(v)
    try {
        row.addView(v)
    } catch (_: Throwable) {
    }
}

private fun ensurePanelPermanentTwoRowsV13() {
    // AARISH_PANEL_PERMANENT_TWO_ROWS_V13
    val root = panelView as? LinearLayout ?: return
    if (!::btnApps.isInitialized) return

    val mainRow = panelMainRowV13 ?: LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, 0)
    }.also { panelMainRowV13 = it }

    val bottomRow = panelBottomRowV13 ?: LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(5), dp(12), dp(2))
    }.also { panelBottomRowV13 = it }

    root.orientation = LinearLayout.VERTICAL
    root.gravity = Gravity.CENTER_VERTICAL

    try { mainRow.removeAllViews() } catch (_: Throwable) {}
    try { bottomRow.removeAllViews() } catch (_: Throwable) {}

    try { addPanelChildKeepSizeV13(mainRow, label) } catch (_: Throwable) {}
    try { addPanelChildKeepSizeV13(mainRow, btnStart) } catch (_: Throwable) {}
    try { addPanelChildKeepSizeV13(mainRow, btnLoop) } catch (_: Throwable) {}
    try { addPanelChildKeepSizeV13(mainRow, btnWorkflow) } catch (_: Throwable) {}
    try { addPanelChildKeepSizeV13(mainRow, btnTools) } catch (_: Throwable) {}
    try { addPanelChildKeepSizeV13(mainRow, btnSystem) } catch (_: Throwable) {}
    try { addPanelChildKeepSizeV13(mainRow, btnAiWait) } catch (_: Throwable) {}
    try { addPanelChildKeepSizeV13(mainRow, btnXyOnly) } catch (_: Throwable) {}
    try { addPanelChildKeepSizeV13(mainRow, btnMemory) } catch (_: Throwable) {}
    try { addPanelChildKeepSizeV13(mainRow, btnSave) } catch (_: Throwable) {}
    try { addPanelChildKeepSizeV13(mainRow, btnUndo) } catch (_: Throwable) {}
    try { addPanelChildKeepSizeV13(mainRow, btnCut) } catch (_: Throwable) {}
    try { btnClear?.let { addPanelChildKeepSizeV13(mainRow, it) } } catch (_: Throwable) {}

    btnApps.text = "📱"
    btnApps.contentDescription = "Premium Desktop App Launcher"
    btnApps.visibility = if (!AutoActionService.isPlaying()) View.VISIBLE else View.GONE
    try { stylePanelButton(btnApps, Color.rgb(37, 99, 235), Color.WHITE, 34) } catch (_: Throwable) {}
    addPanelChildKeepSizeV13(bottomRow, btnApps)

    // Future buttons yahin bottomRow me add honge. AARISH_PANEL_PERMANENT_TWO_ROWS_V13_FUTURE_SLOT

    bottomRow.visibility = View.VISIBLE

    try {
        safeDetachPanelChildV13(mainRow)
        safeDetachPanelChildV13(bottomRow)
        root.removeAllViews()
        root.addView(mainRow)
        root.addView(bottomRow)
    } catch (_: Throwable) {
    }

    try {
        panelView?.requestLayout()
        val panel = panelView
        val params = panelParams
        if (panel != null && params != null && panel.parent != null) {
            aarishAccessWmV13().updateViewLayout(panel, params)
        }
    } catch (_: Throwable) {
    }
}

private fun refreshPanelButtonStyles() {
    if (!::btnStart.isInitialized) return

    val startBg = when (btnStart.text?.toString()) {
        "STOP" -> Color.rgb(239, 68, 68)
        "DONE" -> Color.rgb(245, 158, 11)
        "+ ADD" -> Color.rgb(37, 99, 235)
        "PLAY" -> Color.rgb(16, 185, 129)
        else -> Color.rgb(99, 102, 241)
    }

    stylePanelButton(btnStart, startBg, Color.WHITE, 48)
    if (::btnLoop.isInitialized) stylePanelButton(btnLoop, Color.rgb(6, 182, 212), Color.WHITE, 38)
    if (::btnWorkflow.isInitialized) stylePanelButton(btnWorkflow, Color.rgb(139, 92, 246), Color.WHITE, 40)
    if (::btnTools.isInitialized) stylePanelButton(btnTools, Color.rgb(234, 88, 12), Color.WHITE, 36)
    if (::btnSystem.isInitialized) stylePanelButton(btnSystem, Color.rgb(22, 163, 74), Color.WHITE, 38)
    if (::btnAiWait.isInitialized) stylePanelButton(btnAiWait, Color.rgb(147, 51, 234), Color.WHITE, 34)
    if (::btnXyOnly.isInitialized) stylePanelButton(btnXyOnly, Color.rgb(245, 158, 11), Color.WHITE, 34)
    btnClear?.let { stylePanelButton(it, Color.rgb(30, 41, 59), Color.WHITE, 34) }
    if (::btnSave.isInitialized) stylePanelButton(btnSave, Color.rgb(13, 148, 136), Color.WHITE, 42)
    if (::btnUndo.isInitialized) stylePanelButton(btnUndo, Color.rgb(37, 99, 235), Color.WHITE, 42)
    if (::btnCut.isInitialized) stylePanelButton(btnCut, Color.rgb(225, 29, 72), Color.WHITE, 34)
    styleMemoryControlsCompactV3() // AARISH_MEMORY_ICON_SMALL_V3_REFRESH
    ensurePanelPermanentTwoRowsV13() // AARISH_PANEL_PERMANENT_TWO_ROWS_V13_REFRESH
}

    private fun showFloatingPanel() {
    val compact = resources.displayMetrics.widthPixels < dp(390)
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(if (compact) 6 else 8), dp(7), dp(if (compact) 6 else 8), dp(7))
        background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.rgb(2, 6, 23), Color.rgb(67, 56, 202), Color.rgb(8, 145, 178), Color.rgb(13, 148, 136))
        ).apply {
            cornerRadius = 26f * resources.displayMetrics.density
            setStroke(dp(1), Color.argb(190, 224, 242, 254))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            elevation = 12f * resources.displayMetrics.density
        }
    }

    label = TextView(this).apply {
        text = "📁 " + GestureStore.getActiveConfigName(this@FloatingControlService)
        setTextColor(Color.WHITE)
        textSize = if (compact) 11.5f else 13f
        setPadding(dp(if (compact) 5 else 7), 0, dp(if (compact) 5 else 7), 0)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        maxWidth = dp(if (compact) 64 else 92)
        isClickable = true
        setOnClickListener { showConfigManagerDialog() }
    }

    btnStart = Button(this).apply {
        text = if (GestureStore.hasRecording(this@FloatingControlService)) "PLAY" else "START"
        setOnClickListener { handleStartButton() }
        setOnLongClickListener {
            if (isRecording || AutoActionService.isPlaying() || unsavedGestures.isNotEmpty()) {
                Toast.makeText(
                    this@FloatingControlService,
                    "Recording/Unsaved command ke time clear nahi kar sakte",
                    Toast.LENGTH_SHORT
                ).show()
                true
            } else {
                clearSavedRecordingFromPanel()
                true
            }
        }
    }

    btnLoop = Button(this).apply {
        updateLoopButtonText(this)
        setOnClickListener {
            toggleLoopMode()
            updateLoopButtonText(this)
            refreshPanelButtonStyles()
        }
        visibility = if (GestureStore.hasRecording(this@FloatingControlService)) View.VISIBLE else View.GONE
    }

    btnWorkflow = Button(this).apply {
        text = "🧱 WF"
        setOnClickListener { showWorkflowHubDialog() }
        visibility = if (GestureStore.hasRecording(this@FloatingControlService)) View.VISIBLE else View.GONE
    }

    btnTools = Button(this).apply {
        text = "⚡"
        contentDescription = "Smart Tools"
        setOnClickListener { showSmartToolsDialog() }
        visibility = if (GestureStore.hasRecording(this@FloatingControlService)) View.VISIBLE else View.GONE
    }

    btnSystem = Button(this).apply {
        text = "SYS"
        contentDescription = "Record Back or Recents system action"
        setOnClickListener { showSystemActionRecorderDialog() }
        visibility = View.GONE
    }

    // AARISH_AI_WAIT_BUTTON_V1_PANEL
    btnAiWait = Button(this).apply {
        text = "AI"
        contentDescription = "Record AI Wait step"
        setOnClickListener { recordWaitAiAction() }
        visibility = View.GONE
    }

    btnClear = Button(this).apply {
        text = "CLR"
        visibility = if (GestureStore.hasRecording(this@FloatingControlService)) View.VISIBLE else View.GONE
        setOnClickListener { clearSavedRecordingFromPanel() }
    }

    btnSave = Button(this).apply {
        text = "SAVE"
        visibility = View.GONE
        setOnClickListener { saveRecording() }
    }

    btnUndo = Button(this).apply {
        text = "UNDO"
        visibility = if (GestureStore.hasRecording(this@FloatingControlService)) View.VISIBLE else View.GONE
        setOnClickListener { undoLastStep() }
    }

    btnCut = Button(this).apply {
        text = "CUT"
        setOnClickListener { stopEverythingAndClose() }
    }

    root.addView(label)
    root.addView(btnStart)
    root.addView(btnLoop)
    root.addView(btnWorkflow)
    root.addView(btnTools)
    root.addView(btnSystem)
    btnXyOnly = Button(this).apply {
        text = "XY"
        contentDescription = "Force next recorded step to raw XY coordinates only"
        setOnClickListener { armNextXyOnlyRecord() }
        visibility = View.GONE
    }

    btnMemory = Button(this).apply {
        // AARISH_MEMORY_ICON_SMALL_V3_CREATE
        text = "📋"
        contentDescription = "Memory slots"
        setOnClickListener { toggleMemoryStripV1() }
        setOnLongClickListener {
            armPermanentMemoryCopyV1()
            true
        }
        visibility = View.VISIBLE
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        textSize = 17f
        isAllCaps = false
        includeFontPadding = false
    }

    fun memoryMiniButtonV1(title: String, action: () -> Unit): Button {
        return Button(this).apply {
            // AARISH_MEMORY_ICON_SMALL_V3_MINI_CREATE
            text = title
            minWidth = 0
            minHeight = 0
            minimumWidth = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            textSize = 10.5f
            isAllCaps = false
            includeFontPadding = false
            setOnClickListener { action() }
        }
    }

    memoryStrip = LinearLayout(this).apply {
        // AARISH_MEMORY_SLOTS_V1_PANEL
        // AARISH_MEMORY_MENU_VERTICAL_V4_CREATE
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        visibility = View.GONE
        addView(memoryMiniButtonV1("📥1") { armTempMemoryCopyV1(1) })
        addView(memoryMiniButtonV1("📤1") { recordMemoryPasteV1(1, false) })
        addView(memoryMiniButtonV1("📥2") { armTempMemoryCopyV1(2) })
        addView(memoryMiniButtonV1("📤2") { recordMemoryPasteV1(2, false) })
        addView(memoryMiniButtonV1("📥3") { armTempMemoryCopyV1(3) })
        addView(memoryMiniButtonV1("📤3") { recordMemoryPasteV1(3, false) })
        addView(memoryMiniButtonV1("📌") { recordMemoryPasteV1(1, true) })
    }

    root.addView(btnAiWait)
    root.addView(btnXyOnly)
    root.addView(btnMemory)
    memoryStrip?.let { it.visibility = View.GONE; root.addView(it) } // AARISH_MEMORY_POPUP_BELOW_ICON_V5_INLINE_STRIP_DISABLED
    btnClear?.let { root.addView(it) }
    root.addView(btnSave)
    root.addView(btnUndo)
    root.addView(btnCut)

    updateWorkflowButtonUI()
    styleMemoryControlsCompactV3() // AARISH_MEMORY_MENU_VERTICAL_V4_AFTER_CREATE
    refreshPanelButtonStyles()

    val overlayType = aarishAccessOverlayTypeV13()

    
    // AARISH_PREMIUM_DESKTOP_APP_PANEL_BUTTON_V4_FINAL
    if (!::btnApps.isInitialized) {
        btnApps = Button(this).apply {
            text = "📱"
            contentDescription = "Premium Desktop App Launcher"
            setOnClickListener { showAarishAppLauncher() }
            visibility = if (shouldShowAppLauncherButton()) View.VISIBLE else View.GONE
        }
        try { stylePanelButton(btnApps, Color.rgb(37, 99, 235), Color.WHITE, 34) } catch (_: Throwable) {}
    }
    try {
        if (btnApps.parent == null) root.addView(btnApps)
    } catch (_: Throwable) {}

val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    )

    params.gravity = Gravity.TOP or Gravity.START
    params.x = dp(10)
    params.y = dp(70)
    panelParams = params

    panelView = root
    ensurePanelPermanentTwoRowsV13() // AARISH_PANEL_PERMANENT_TWO_ROWS_V13_AFTER_PANEL_SET
    ensureRecordingLauncherSecondRowV12() // AARISH_APP_LAUNCHER_SECOND_ROW_V12_AFTER_PANEL_SET
    if (!safeAddView(root, params, "Floating panel open nahi hua")) {
        stopSelf()
        return
    }
    makePanelDraggable(label, root, params)
}

private fun toggleLoopMode() {
    val active = GestureStore.getActiveConfigName(this)
    val currentMode = GestureStore.getLoopModeForConfig(this, active)
    val currentValue = GestureStore.getLoopValueForConfig(this, active)

    when (currentMode) {
        "ONCE" -> GestureStore.saveLoopSettingsForConfig(this, active, "COUNT", 10)
        "COUNT" -> GestureStore.saveLoopSettingsForConfig(this, active, "INFINITE", 0)
        "INFINITE" -> GestureStore.saveLoopSettingsForConfig(this, active, "TIME", 5)
        "TIME" -> {
            if (currentValue == 5) GestureStore.saveLoopSettingsForConfig(this, active, "TIME", 10)
            else GestureStore.saveLoopSettingsForConfig(this, active, "ONCE", 1)
        }
        else -> GestureStore.saveLoopSettingsForConfig(this, active, "ONCE", 1)
    }
}
    private fun updateLoopButtonText(btn: Button) {
        val active = GestureStore.getActiveConfigName(this)
        val mode = GestureStore.getLoopModeForConfig(this, active)
        val value = GestureStore.getLoopValueForConfig(this, active)
        btn.text = when (mode) {
            "COUNT" -> "🔁 ${value}x"
            "INFINITE" -> "🔁 ∞"
            "TIME" -> "⏱️ ${value}m"
            else -> "🔁 1x"
        }
    }

    

private fun checkPlaybackStateContinuously() {
    playbackWatcherRunnable?.let { handler.removeCallbacks(it) }

    val watcher = object : Runnable {
        override fun run() {
            if (instance !== this@FloatingControlService) {
                playbackWatcherRunnable = null
                return
            }

            if (!AutoActionService.isPlaying()) {
                if (!isRecording) {
                    val hasSaved = GestureStore.hasRecording(this@FloatingControlService)
                    updateUIState(if (hasSaved) "PLAY" else "START", false, hasSaved, true)
                    restorePanelUI()
                }
                playbackWatcherRunnable = null
                return
            }

            handler.postDelayed(this, 200L)
        }
    }

    playbackWatcherRunnable = watcher
    handler.postDelayed(watcher, 200L)
}

    private fun hidePanelUIForPlayback() {
        cancelClipboardCopyArmV6() // AARISH_MEMORY_CLIPBOARD_TWOSTEP_V6_CLEANUP

    if (panelHiddenForPlayback) return
    panelHiddenForPlayback = true

    if (::btnSave.isInitialized) btnSave.visibility = View.GONE
    if (::btnUndo.isInitialized) btnUndo.visibility = View.GONE
    if (::btnCut.isInitialized) btnCut.visibility = View.GONE
    if (::label.isInitialized) label.visibility = View.GONE
    if (::btnLoop.isInitialized) btnLoop.visibility = View.GONE
    if (::btnWorkflow.isInitialized) btnWorkflow.visibility = View.GONE
    if (::btnTools.isInitialized) btnTools.visibility = View.GONE
    if (::btnSystem.isInitialized) btnSystem.visibility = View.GONE
    if (::btnAiWait.isInitialized) btnAiWait.visibility = View.GONE
    btnClear?.visibility = View.GONE

    val params = panelParams
    val panel = panelView
    if (params != null && panel != null) {
        oldPanelX = params.x
        oldPanelY = params.y
        val maxX = if (panel.width > 0) resources.displayMetrics.widthPixels - panel.width else resources.displayMetrics.widthPixels
        val maxY = if (panel.height > 0) resources.displayMetrics.heightPixels - panel.height else resources.displayMetrics.heightPixels

        params.x = (resources.displayMetrics.widthPixels - dp(118)).coerceIn(0, if (maxX > 0) maxX else resources.displayMetrics.widthPixels)
        params.y = dp(58).coerceIn(0, if (maxY > 0) maxY else resources.displayMetrics.heightPixels)
        safeUpdateView(panel, params)
    }
}

    // BUG #5 FIX: btnLoop tabhi dikhega jab actually recording save ho
        // BUG FIX: Empty recording par SAVE hide rahega
private fun clampPanelToScreen(params: WindowManager.LayoutParams, panel: View) {
    val metrics = resources.displayMetrics
    val maxX = if (panel.width > 0) metrics.widthPixels - panel.width else metrics.widthPixels
    val maxY = if (panel.height > 0) metrics.heightPixels - panel.height else metrics.heightPixels
    params.x = params.x.coerceIn(0, if (maxX > 0) maxX else metrics.widthPixels)
    params.y = params.y.coerceIn(0, if (maxY > 0) maxY else metrics.heightPixels)
}

private fun restorePanelUI() {
    val shouldRestorePlaybackPosition = panelHiddenForPlayback
    panelHiddenForPlayback = false

    val hasSaved = GestureStore.hasRecording(this)
    if (::btnSave.isInitialized) btnSave.visibility = if (unsavedGestures.isNotEmpty() || isRecording) View.VISIBLE else View.GONE
    if (::btnUndo.isInitialized) btnUndo.visibility = if (hasAnythingToUndo()) View.VISIBLE else View.GONE
    if (::btnCut.isInitialized) btnCut.visibility = View.VISIBLE
    if (::label.isInitialized) label.visibility = View.VISIBLE

    if (::btnLoop.isInitialized) {
        updateLoopButtonText(btnLoop)
        btnLoop.visibility = if (hasSaved && !isRecording) View.VISIBLE else View.GONE
    }

    if (::btnWorkflow.isInitialized) {
        updateWorkflowButtonUI()
        btnWorkflow.visibility = if (hasSaved && !isRecording) View.VISIBLE else View.GONE
    }

    if (::btnTools.isInitialized) {
        btnTools.visibility = if (hasSaved && !isRecording) View.VISIBLE else View.GONE
    }

    if (::btnSystem.isInitialized) {
        btnSystem.visibility = if (isRecording) View.VISIBLE else View.GONE
    }

    if (::btnAiWait.isInitialized) {
        btnAiWait.visibility = if (isRecording) View.VISIBLE else View.GONE
        if (::btnXyOnly.isInitialized) btnXyOnly.visibility = if (isRecording) View.VISIBLE else View.GONE
        if (::btnMemory.isInitialized) btnMemory.visibility = if (AutoActionService.isPlaying()) View.GONE else View.VISIBLE // AARISH_MEMORY_CLIPBOARD_TWOSTEP_V6_PERMANENT_ICON
        if (!isRecording) { memoryStrip?.visibility = View.GONE; dismissMemoryPopupV5() } // AARISH_MEMORY_POPUP_BELOW_ICON_V5_STOP_DISMISS // AARISH_MEMORY_MENU_VERTICAL_V4_STOP_HIDE
    }

    btnClear?.visibility = if (hasSaved && unsavedGestures.isEmpty() && !isRecording) View.VISIBLE else View.GONE
    refreshPanelButtonStyles()

    val params = panelParams
    val panel = panelView
    if (params != null && panel != null) {
        if (shouldRestorePlaybackPosition) {
            params.x = oldPanelX
            params.y = oldPanelY
        }
        clampPanelToScreen(params, panel)
        safeUpdateView(panel, params)
    }
}

    

            private fun bringPanelToFront() {
        val panel = panelView ?: return
        val params = panelParams ?: return

        try {
            windowManager.removeView(panel)
        } catch (_: Exception) {
        }

        val firstAdded = safeAddView(panel, params, "Panel restore error")
        if (!firstAdded) {
            handler.postDelayed({
                if (instance == this@FloatingControlService && panelView === panel) {
                    val retryAdded = safeAddView(panel, params, "Panel retry error")
                    if (!retryAdded) {
                        panelView = null
                        Toast.makeText(
                            this,
                            "Panel wapas nahi aa paya, service band kar rahe hain",
                            Toast.LENGTH_LONG
                        ).show()
                        stopSelf()
                    }
                }
            }, 250L)
        }
    }

     

    // BUG #4 FIX: Loop text update + visibility correctly set on save
     
            
    private fun refreshConfigLabel() {
        if (::label.isInitialized) {
            label.text = "📁 " + GestureStore.getActiveConfigName(this)
        }
    }

    private fun prepareOverlayDialog(dialog: android.app.AlertDialog) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
    }

    private fun showOverlayDialogSafely(dialog: android.app.AlertDialog) {
    try {
        val oldDialog = activeConfigDialog
        if (oldDialog != null && oldDialog !== dialog) {
            try { oldDialog.dismiss() } catch (_: Exception) {}
        }

        activeConfigDialog = dialog

        dialog.setOnDismissListener {
            if (activeConfigDialog === dialog) {
                activeConfigDialog = null
            }
        }

        // Overlay type ko show() se pehle set karna zaroori hai.
        prepareOverlayDialog(dialog)
        dialog.show()
        // AARISH_DIALOG_TYPE_REAPPLY: service dialogs need overlay type even if window is created lazily.
        prepareOverlayDialog(dialog)

        dialog.window?.apply {
            setDimAmount(0.45f)
            setBackgroundDrawable(
                android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(22).toFloat()
                    setColor(android.graphics.Color.rgb(255, 255, 255))
                    setStroke(dp(1), android.graphics.Color.rgb(226, 232, 240))
                }
            )

            val lp = attributes
            val screenW = resources.displayMetrics.widthPixels
            lp.width = kotlin.math.min(screenW - dp(28), dp(440)).coerceAtLeast(dp(286))
            attributes = lp
        }

        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.let {
            stylePanelButton(it, Color.rgb(16, 185, 129), Color.WHITE, 72)
        }
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.let {
            stylePanelButton(it, Color.rgb(71, 85, 105), Color.WHITE, 72)
        }
        dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)?.let {
            stylePanelButton(it, Color.rgb(244, 63, 94), Color.WHITE, 72)
        }
    } catch (e: Exception) {
        try { dialog.dismiss() } catch (_: Exception) {}
        activeConfigDialog = null
        Toast.makeText(this, "Config popup open nahi hua: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

    private fun updateWorkflowButtonUI() {
        if (!::btnWorkflow.isInitialized) return

        val active = GestureStore.getActiveConfigName(this)
        val chain = GestureStore.getRunnableWorkflowSequence(this, active, 300)
        val masterCount = GestureStore.getMasterWorkflow(this, active, 120).size

        btnWorkflow.text = when {
            masterCount > 0 -> "👑$masterCount"
            chain.size > 1 -> "🧱${chain.size}"
            else -> "🧱 WF"
        }
    }


// AARISH_SMART_TOOLS_V1: offline autoclicker power tools without changing full UI.
private fun showSmartToolsDialog() {
    if (isRecording || AutoActionService.isPlaying() || unsavedGestures.isNotEmpty()) {
        Toast.makeText(this, "Recording/Play/Unsaved edit ke time Smart Tools locked hain", Toast.LENGTH_SHORT).show()
        return
    }

    if (!GestureStore.hasRecording(this)) {
        Toast.makeText(this, "Smart Tools ke liye pehle ek macro SAVE karo", Toast.LENGTH_SHORT).show()
        return
    }

    val active = GestureStore.getActiveConfigName(this)
    val items = arrayOf(
        "⚡ Burst Repeat Last Click",
        "⏳ Add Safe Start Delay",
        "🧹 Smooth Long Wait Gaps",
        "🎯 Stabilize Tap Jitter",
        "🚀 Speed Up Macro",
        "🛡️ Add Minimum Tap Gap",
        "🧠 One Tap Optimize Macro",
        "📊 Active Macro Info",
        "🎚️ Smart Tap Accuracy Mode",
        "🧽 Remove Duplicate Taps",
        "✂️ Trim First Idle Wait",
        "🧯 Repair Macro Data",
        "📌 Long Press Last Tap",
        "📐 Re-anchor Tap Percent",
        "🧭 Normalize Swipe Paths",
        "🧲 Edge Safe Clamp",
        "👆 Double Tap Last Tap",
        "🐢 Slow Down Macro"
    )

    val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
        .setTitle("Smart Tools • $active")
        .setItems(items) { _, which ->
            when (which) {
                0 -> showNumberInputDialog(
                    title = "Last click kitni baar extra repeat ho?",
                    hint = "Example: 10",
                    defaultValue = 5,
                    min = 1,
                    max = 100
                ) { repeatCount ->
                    showNumberInputDialog(
                        title = "Repeat gap milliseconds me?",
                        hint = "Example: 120",
                        defaultValue = 120,
                        min = 40,
                        max = 600000
                    ) { gapMs ->
                        addBurstRepeatsToActiveConfig(repeatCount, gapMs.toLong())
                    }
                }

                1 -> showNumberInputDialog(
                    title = "Start se pehle safe delay ms?",
                    hint = "Example: 700",
                    defaultValue = 700,
                    min = 100,
                    max = 10000
                ) { delayMs ->
                    insertSafeStartDelayToActiveConfig(delayMs.toLong())
                }

                2 -> showNumberInputDialog(
                    title = "Maximum wait gap ms?",
                    hint = "Example: 2000",
                    defaultValue = 2000,
                    min = 100,
                    max = 60000
                ) { maxGapMs ->
                    smoothActiveRecordingGaps(maxGapMs.toLong())
                }

                3 -> showNumberInputDialog(
                    title = "Tap duration ms stable kitna rakhein?",
                    hint = "Example: 80",
                    defaultValue = 80,
                    min = 50,
                    max = 350
                ) { durationMs ->
                    stabilizeTapJitterForActiveConfig(durationMs.toLong())
                }

                4 -> showNumberInputDialog(
                    title = "Speed percent? 70 = faster, 100 = same",
                    hint = "Example: 70",
                    defaultValue = 70,
                    min = 20,
                    max = 100
                ) { percent ->
                    speedUpActiveRecording(percent)
                }

                5 -> showNumberInputDialog(
                    title = "Minimum gap between steps ms?",
                    hint = "Example: 180",
                    defaultValue = 180,
                    min = 40,
                    max = 600000
                ) { minGapMs ->
                    addMinimumGapToActiveConfig(minGapMs.toLong())
                }

                6 -> optimizeActiveMacroForReliability()
                7 -> showActiveMacroInfoDialog()
                8 -> showTapAccuracyModeDialog()
                9 -> removeDuplicateTapsFromActiveConfig()
                10 -> showNumberInputDialog(
                    title = "First wait ko maximum kitna rakhein ms?",
                    hint = "Example: 250",
                    defaultValue = 250,
                    min = 0,
                    max = 10000
                ) { maxStartDelay ->
                    trimFirstIdleWaitForActiveConfig(maxStartDelay.toLong())
                }

                11 -> repairActiveMacroData()

                12 -> showNumberInputDialog(
                    title = "Last tap ko long press kitna ms banayein?",
                    hint = "Example: 900",
                    defaultValue = 900,
                    min = 450,
                    max = 600000
                ) { durationMs ->
                    setLastTapLongPress(durationMs.toLong())
                }

                13 -> reanchorActiveMacroToCurrentScreen()
                14 -> normalizeActiveSwipePaths()
                15 -> edgeSafeClampActiveMacro()
                16 -> doubleTapLastTapInActiveConfig()
                17 -> showNumberInputDialog(
                    title = "Macro ko slow kitna karna hai? 150 = 1.5x slow",
                    hint = "Example: 150",
                    defaultValue = 150,
                    min = 110,
                    max = 500
                ) { percent ->
                    slowDownActiveRecording(percent)
                }
            }
        }
        .create()

    showOverlayDialogSafely(dialog)
}

private fun gestureDurationMs(gesture: RecordedGesture): Long {
    return (gesture.points.maxOfOrNull { it.t.coerceAtLeast(0L) } ?: 80L)
        .coerceIn(50L, 600000L)
}

private fun gestureEndMs(gesture: RecordedGesture): Long {
    return gesture.delayFromStart.coerceAtLeast(0L) + gestureDurationMs(gesture)
}

private fun cloneGestureAtDelay(gesture: RecordedGesture, delayMs: Long): RecordedGesture {
    return gesture.copy(
        delayFromStart = delayMs.coerceAtLeast(0L).coerceAtMost(24L * 60L * 60L * 1000L),
        points = gesture.points.map { it.copy(t = it.t.coerceAtLeast(0L).coerceAtMost(600000L)) }
    )
}

private fun finishSmartToolEdit(message: String) {
    unsavedGestures = emptyList()
    glassHiddenAt = 0L
    nextNavigationGapOverride = null
    pendingDiscardConfirm = false

    val hasSaved = GestureStore.hasRecording(this)
    updateUIState(if (hasSaved) "PLAY" else "START", false, hasSaved, true)
    restorePanelUI()
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

private fun addBurstRepeatsToActiveConfig(repeatCount: Int, gapMs: Long) {
    val saved = GestureStore.load(this)
        .filter { it.points.isNotEmpty() }
        .sortedBy { it.delayFromStart.coerceAtLeast(0L) }

    val last = saved.lastOrNull { !isSystemRecordedGesture(it) }
    if (last == null) {
        Toast.makeText(this, "Repeat ke liye saved click nahi mila", Toast.LENGTH_SHORT).show()
        return
    }

    val count = repeatCount.coerceIn(1, 100)
    val gap = gapMs.coerceIn(40L, 5000L)
    val lastDuration = gestureDurationMs(last)
    val extra = mutableListOf<RecordedGesture>()
    var nextDelay = gestureEndMs(last) + gap

    repeat(count) {
        extra.add(cloneGestureAtDelay(last, nextDelay))
        nextDelay += lastDuration + gap
    }

    val merged = (saved + extra)
        .sortedBy { it.delayFromStart.coerceAtLeast(0L) }
        .take(1600)

    if (GestureStore.save(this, merged)) {
        finishSmartToolEdit("⚡ Burst added: last click +$count repeats")
    } else {
        Toast.makeText(this, "Burst save nahi ho paya", Toast.LENGTH_LONG).show()
    }
}

private fun insertSafeStartDelayToActiveConfig(delayMs: Long) {
    val delay = delayMs.coerceIn(100L, 10000L)
    val saved = GestureStore.load(this)
        .filter { it.points.isNotEmpty() }
        .sortedBy { it.delayFromStart.coerceAtLeast(0L) }

    if (saved.isEmpty()) {
        Toast.makeText(this, "Delay ke liye saved macro nahi mila", Toast.LENGTH_SHORT).show()
        return
    }

    val edited = saved.map { gesture ->
        cloneGestureAtDelay(gesture, gesture.delayFromStart.coerceAtLeast(0L) + delay)
    }

    if (GestureStore.save(this, edited)) {
        finishSmartToolEdit("⏳ Start delay added: ${delay}ms")
    } else {
        Toast.makeText(this, "Start delay save nahi hua", Toast.LENGTH_LONG).show()
    }
}

private fun smoothActiveRecordingGaps(maxGapMs: Long) {
    val limit = maxGapMs.coerceIn(100L, 600000L)
    val saved = GestureStore.load(this)
        .filter { it.points.isNotEmpty() }
        .sortedBy { it.delayFromStart.coerceAtLeast(0L) }

    if (saved.isEmpty()) {
        Toast.makeText(this, "Smooth karne ke liye saved macro nahi mila", Toast.LENGTH_SHORT).show()
        return
    }

    var cursor = 0L
    val edited = saved.map { gesture ->
        val rawDelay = gesture.delayFromStart.coerceAtLeast(0L)
        val rawGap = (rawDelay - cursor).coerceAtLeast(0L)
        val newDelay = cursor + rawGap.coerceAtMost(limit)
        val cloned = cloneGestureAtDelay(gesture, newDelay)
        cursor = gestureEndMs(cloned)
        cloned
    }

    if (GestureStore.save(this, edited)) {
        finishSmartToolEdit("🧹 Long waits smooth ho gaye: max ${limit}ms")
    } else {
        Toast.makeText(this, "Smooth timing save nahi hui", Toast.LENGTH_LONG).show()
    }
}

// AARISH_SMART_TOOLS_V2: macro-quality tools that edit saved commands safely offline.
private fun isSystemRecordedGesture(gesture: RecordedGesture): Boolean {
    // AARISH_PREMIUM_DESKTOP_APP_SYSTEM_KIND_V4_FINAL
    val first = gesture.points.firstOrNull() ?: return false
    return first.x.toInt() in setOf(-100, -200, -300, -400)
}

private fun stabilizeTapJitterForActiveConfig(durationMs: Long) {
    val duration = durationMs.coerceIn(50L, 350L)
    val saved = GestureStore.load(this)
        .filter { it.points.isNotEmpty() }
        .sortedBy { it.delayFromStart.coerceAtLeast(0L) }

    if (saved.isEmpty()) {
        Toast.makeText(this, "Stable karne ke liye saved macro nahi mila", Toast.LENGTH_SHORT).show()
        return
    }

    val edited = saved.map { gesture ->
        if (isSystemRecordedGesture(gesture) || localGestureHasRealMovement(gesture) || gestureDurationMs(gesture) >= 450L) {
            gesture
        } else {
            val first = gesture.points.first()
            gesture.copy(
                points = listOf(
                    first.copy(t = 0L),
                    first.copy(t = duration)
                )
            )
        }
    }

    if (GestureStore.save(this, edited)) {
        finishSmartToolEdit("🎯 Tap jitter stable ho gaya: ${duration}ms")
    } else {
        Toast.makeText(this, "Tap stable save nahi hua", Toast.LENGTH_LONG).show()
    }
}

private fun speedUpActiveRecording(speedPercent: Int) {
    val percent = speedPercent.coerceIn(20, 100)
    val factor = percent / 100.0
    val saved = GestureStore.load(this)
        .filter { it.points.isNotEmpty() }
        .sortedBy { it.delayFromStart.coerceAtLeast(0L) }

    if (saved.isEmpty()) {
        Toast.makeText(this, "Speed ke liye saved macro nahi mila", Toast.LENGTH_SHORT).show()
        return
    }

    var oldCursor = 0L
    var newCursor = 0L
    val edited = saved.map { gesture ->
        val rawDelay = gesture.delayFromStart.coerceAtLeast(0L)
        val rawGap = (rawDelay - oldCursor).coerceAtLeast(0L)
        val newDelay = newCursor + (rawGap * factor).toLong().coerceAtLeast(0L)
        val newPoints = gesture.points
            .sortedBy { it.t.coerceAtLeast(0L) }
            .map { point ->
                point.copy(t = (point.t.coerceAtLeast(0L) * factor).toLong().coerceAtLeast(0L).coerceAtMost(600000L))
            }
        val cloned = gesture.copy(delayFromStart = newDelay.coerceAtMost(24L * 60L * 60L * 1000L), points = newPoints)
        oldCursor = gestureEndMs(gesture)
        newCursor = gestureEndMs(cloned)
        cloned
    }

    if (GestureStore.save(this, edited)) {
        finishSmartToolEdit("🚀 Macro speed set: ${percent}% timing")
    } else {
        Toast.makeText(this, "Speed edit save nahi hui", Toast.LENGTH_LONG).show()
    }
}

private fun addMinimumGapToActiveConfig(minGapMs: Long) {
    val gap = minGapMs.coerceIn(40L, 5000L)
    val saved = GestureStore.load(this)
        .filter { it.points.isNotEmpty() }
        .sortedBy { it.delayFromStart.coerceAtLeast(0L) }

    if (saved.isEmpty()) {
        Toast.makeText(this, "Gap ke liye saved macro nahi mila", Toast.LENGTH_SHORT).show()
        return
    }

    var cursor = 0L
    val edited = saved.mapIndexed { index, gesture ->
        val rawDelay = gesture.delayFromStart.coerceAtLeast(0L)
        val safeDelay = if (index == 0) rawDelay else kotlin.math.max(rawDelay, cursor + gap)
        val cloned = cloneGestureAtDelay(gesture, safeDelay)
        cursor = gestureEndMs(cloned)
        cloned
    }

    if (GestureStore.save(this, edited)) {
        finishSmartToolEdit("🛡️ Minimum gap set: ${gap}ms")
    } else {
        Toast.makeText(this, "Minimum gap save nahi hua", Toast.LENGTH_LONG).show()
    }
}

private fun optimizeActiveMacroForReliability() {
    val saved = GestureStore.load(this)
        .filter { it.points.isNotEmpty() }
        .sortedBy { it.delayFromStart.coerceAtLeast(0L) }

    if (saved.isEmpty()) {
        Toast.makeText(this, "Optimize ke liye saved macro nahi mila", Toast.LENGTH_SHORT).show()
        return
    }

    val firstStartDelay = saved.firstOrNull()?.delayFromStart?.coerceAtLeast(0L) ?: 0L
    val startShift = (firstStartDelay - 250L).coerceAtLeast(0L)

    var cursor = 0L
    val edited = saved.mapIndexed { index, gesture ->
        val rawDelay = (gesture.delayFromStart.coerceAtLeast(0L) - startShift).coerceAtLeast(0L)
        val minGap = if (index == 0) 0L else 140L
        val safeDelay = (if (index == 0) rawDelay else kotlin.math.max(rawDelay, cursor + minGap))
            .coerceAtMost(24L * 60L * 60L * 1000L)

        val cleanPoints = gesture.points
            .filter { !it.x.isNaN() && !it.x.isInfinite() && !it.y.isNaN() && !it.y.isInfinite() }
            .sortedBy { it.t.coerceAtLeast(0L) }
            .map { it.copy(t = it.t.coerceAtLeast(0L).coerceAtMost(600000L)) }

        val finalPoints = if (
            !isSystemRecordedGesture(gesture) &&
            cleanPoints.isNotEmpty() &&
            !localGestureHasRealMovement(gesture.copy(points = cleanPoints)) &&
            gestureDurationMs(gesture.copy(points = cleanPoints)) < 450L
        ) {
            val first = cleanPoints.first()
            listOf(first.copy(t = 0L), first.copy(t = 90L))
        } else {
            cleanPoints.ifEmpty { gesture.points }
        }

        val cloned = gesture.copy(delayFromStart = safeDelay, points = finalPoints)
        cursor = gestureEndMs(cloned)
        cloned
    }.take(1600)

    if (GestureStore.save(this, edited)) {
        finishSmartToolEdit("🧠 Macro optimized: stable taps + safe gaps + trim start")
    } else {
        Toast.makeText(this, "Optimize save nahi hua", Toast.LENGTH_LONG).show()
    }
}

private fun showActiveMacroInfoDialog() {
    val active = GestureStore.getActiveConfigName(this)
    val saved = GestureStore.load(this).sortedBy { it.delayFromStart.coerceAtLeast(0L) }
    val durationMs = saved.maxOfOrNull { gestureEndMs(it) } ?: 0L
    val chain = GestureStore.getWorkflowSummary(this, active)
    val mode = GestureStore.getLoopModeForConfig(this, active)
    val loopValue = GestureStore.getLoopValueForConfig(this, active)
    val loopText = when (mode) {
        "COUNT" -> "${loopValue}x"
        "INFINITE" -> "Infinity"
        "TIME" -> "${loopValue} minutes"
        else -> "Once"
    }
    val tapMode = GestureStore.getTapAccuracyModeLabel(this)

    val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
        .setTitle("Macro Info")
        .setMessage(
            "Config: $active\n" +
                "Steps: ${saved.size}\n" +
                "Duration: ${(durationMs + 999L) / 1000L}s\n" +
                "Loop: $loopText\n" +
                "Smart Tap: $tapMode\n" +
                "Workflow: $chain"
        )
        .setPositiveButton("OK", null)
        .create()

    showOverlayDialogSafely(dialog)
}


private fun showTapAccuracyModeDialog() {
    if (isRecording || AutoActionService.isPlaying() || unsavedGestures.isNotEmpty()) {
        Toast.makeText(this, "Recording/Play/Unsaved edit ke time accuracy mode change nahi kar sakte", Toast.LENGTH_SHORT).show()
        return
    }

    val modes = arrayOf("BALANCED", "STRICT", "RELAXED")
    val labels = arrayOf(
        "⚖️ Balanced • recommended",
        "🛡️ Strict • wrong tap se zyada protection",
        "⚡ Relaxed • dynamic UI par zyada fallback"
    )
    val current = GestureStore.getTapAccuracyMode(this)
    val checked = modes.indexOf(current).takeIf { it >= 0 } ?: 0

    val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
        .setTitle("Smart Tap Accuracy")
        .setSingleChoiceItems(labels, checked) { popup, which ->
            GestureStore.saveTapAccuracyMode(this, modes[which])
            Toast.makeText(
                this,
                "Smart Tap: ${GestureStore.getTapAccuracyModeLabel(this)}",
                Toast.LENGTH_SHORT
            ).show()
            popup.dismiss()
        }
        .setNegativeButton("Cancel", null)
        .create()

    showOverlayDialogSafely(dialog)
}


private fun removeDuplicateTapsFromActiveConfig() {
    val saved = GestureStore.load(this)
        .filter { it.points.isNotEmpty() }
        .sortedBy { it.delayFromStart.coerceAtLeast(0L) }

    if (saved.isEmpty()) {
        Toast.makeText(this, "Duplicate clean ke liye saved macro nahi mila", Toast.LENGTH_SHORT).show()
        return
    }

    val slop = kotlin.math.max(8f, 5f * resources.displayMetrics.density)
    val maxDuplicateGap = 120L
    val edited = mutableListOf<RecordedGesture>()
    var removed = 0

    for (gesture in saved) {
        val previous = edited.lastOrNull()
        val duplicateTap = if (previous != null) {
            val gPoint = gesture.points.firstOrNull()
            val pPoint = previous.points.firstOrNull()
            val gap = gesture.delayFromStart.coerceAtLeast(0L) - gestureEndMs(previous)

            gPoint != null &&
                pPoint != null &&
                !isSystemRecordedGesture(gesture) &&
                !isSystemRecordedGesture(previous) &&
                !localGestureHasRealMovement(gesture) &&
                !localGestureHasRealMovement(previous) &&
                abs(gPoint.x - pPoint.x) <= slop &&
                abs(gPoint.y - pPoint.y) <= slop &&
                gap in 0L..maxDuplicateGap
        } else {
            false
        }

        if (duplicateTap) {
            removed++
        } else {
            edited.add(gesture)
        }
    }

    if (removed == 0) {
        Toast.makeText(this, "🧽 Duplicate taps nahi mile", Toast.LENGTH_SHORT).show()
        return
    }

    if (GestureStore.save(this, edited)) {
        finishSmartToolEdit("🧽 Duplicate taps removed: $removed")
    } else {
        Toast.makeText(this, "Duplicate clean save nahi hua", Toast.LENGTH_LONG).show()
    }
}

private fun trimFirstIdleWaitForActiveConfig(maxStartDelayMs: Long) {
    val maxStartDelay = maxStartDelayMs.coerceIn(0L, 10000L)
    val saved = GestureStore.load(this)
        .filter { it.points.isNotEmpty() }
        .sortedBy { it.delayFromStart.coerceAtLeast(0L) }

    if (saved.isEmpty()) {
        Toast.makeText(this, "Trim ke liye saved macro nahi mila", Toast.LENGTH_SHORT).show()
        return
    }

    val firstDelay = saved.first().delayFromStart.coerceAtLeast(0L)
    val shift = (firstDelay - maxStartDelay).coerceAtLeast(0L)

    if (shift <= 0L) {
        Toast.makeText(this, "✂️ First wait already safe hai", Toast.LENGTH_SHORT).show()
        return
    }

    val edited = saved.map { gesture ->
        cloneGestureAtDelay(gesture, gesture.delayFromStart.coerceAtLeast(0L) - shift)
    }

    if (GestureStore.save(this, edited)) {
        finishSmartToolEdit("✂️ First idle wait trimmed: -${shift}ms")
    } else {
        Toast.makeText(this, "First wait trim save nahi hua", Toast.LENGTH_LONG).show()
    }
}


// AARISH_SMART_TOOLS_V3: saved-macro repair + long-press + screen-anchor tools.
private fun cleanToolPoints(gesture: RecordedGesture): List<GesturePoint> {
    if (gesture.points.isEmpty()) return emptyList()
    if (isSystemRecordedGesture(gesture)) {
        return gesture.points
            .sortedBy { it.t.coerceAtLeast(0L) }
            .take(2)
            .map { it.copy(t = it.t.coerceAtLeast(0L).coerceAtMost(1000L)) }
    }

    val screenW = (resources.displayMetrics.widthPixels.toFloat() - 2f).coerceAtLeast(2f)
    val screenH = (resources.displayMetrics.heightPixels.toFloat() - 2f).coerceAtLeast(2f)
    val orderedToolPoints = gesture.points
        .filter { !it.x.isNaN() && !it.x.isInfinite() && !it.y.isNaN() && !it.y.isInfinite() }
        .sortedBy { it.t.coerceAtLeast(0L) }
    val baseT = orderedToolPoints.firstOrNull()?.t?.coerceAtLeast(0L) ?: 0L

    // AARISH_TOOL_POINT_TIME_NORMALIZE_V1: repair/tools ke baad har gesture ka local t 0 se chale.
    return orderedToolPoints.map { point ->
        point.copy(
            x = point.x.coerceIn(2f, screenW),
            y = point.y.coerceIn(2f, screenH),
            t = (point.t.coerceAtLeast(0L) - baseT).coerceAtLeast(0L).coerceAtMost(600000L)
        )
    }
}

private fun repairActiveMacroData() {
    val saved = GestureStore.load(this)
        .filter { it.points.isNotEmpty() }
        .sortedBy { it.delayFromStart.coerceAtLeast(0L) }

    if (saved.isEmpty()) {
        Toast.makeText(this, "Repair ke liye saved macro nahi mila", Toast.LENGTH_SHORT).show()
        return
    }

    var cursor = 0L
    var repaired = 0
    val edited = saved.mapNotNull { gesture ->
        val cleanPoints = cleanToolPoints(gesture)
        if (cleanPoints.isEmpty()) {
            repaired++
            null
        } else {
            val safeDelay = gesture.delayFromStart.coerceAtLeast(cursor).coerceAtMost(24L * 60L * 60L * 1000L)
            val fixed = gesture.copy(delayFromStart = safeDelay, points = cleanPoints)
            if (fixed != gesture) repaired++
            cursor = gestureEndMs(fixed)
            fixed
        }
    }.take(1600)

    if (edited.isEmpty()) {
        Toast.makeText(this, "Repair ke baad valid step nahi bacha", Toast.LENGTH_LONG).show()
        return
    }

    if (GestureStore.save(this, edited)) {
        finishSmartToolEdit("🧯 Macro repaired: $repaired fixes")
    } else {
        Toast.makeText(this, "Repair save nahi hua", Toast.LENGTH_LONG).show()
    }
}

private fun setLastTapLongPress(durationMs: Long) {
    val duration = durationMs.coerceIn(450L, 600000L)
    val saved = GestureStore.load(this)
        .filter { it.points.isNotEmpty() }
        .sortedBy { it.delayFromStart.coerceAtLeast(0L) }
        .toMutableList()

    if (saved.isEmpty()) {
        Toast.makeText(this, "Long press ke liye saved macro nahi mila", Toast.LENGTH_SHORT).show()
        return
    }

    val index = saved.indexOfLast { gesture ->
        !isSystemRecordedGesture(gesture) && !localGestureHasRealMovement(gesture)
    }

    if (index < 0) {
        Toast.makeText(this, "Long press banane ke liye tap step nahi mila", Toast.LENGTH_SHORT).show()
        return
    }

    val gesture = saved[index]
    val first = cleanToolPoints(gesture).firstOrNull() ?: gesture.points.first()
    saved[index] = gesture.copy(points = listOf(first.copy(t = 0L), first.copy(t = duration)))

    if (GestureStore.save(this, saved)) {
        finishSmartToolEdit("📌 Last tap long press: ${duration}ms")
    } else {
        Toast.makeText(this, "Long press save nahi hua", Toast.LENGTH_LONG).show()
    }
}

private fun reanchorActiveMacroToCurrentScreen() {
    val screenW = resources.displayMetrics.widthPixels.coerceAtLeast(1)
    val screenH = resources.displayMetrics.heightPixels.coerceAtLeast(1)
    val saved = GestureStore.load(this)
        .filter { it.points.isNotEmpty() }
        .sortedBy { it.delayFromStart.coerceAtLeast(0L) }

    if (saved.isEmpty()) {
        Toast.makeText(this, "Re-anchor ke liye saved macro nahi mila", Toast.LENGTH_SHORT).show()
        return
    }

    val edited = saved.map { gesture ->
        if (isSystemRecordedGesture(gesture)) {
            gesture
        } else {
            val first = cleanToolPoints(gesture).firstOrNull() ?: gesture.points.first()
            gesture.copy(
                xPercent = (first.x / screenW.toFloat()).coerceIn(0f, 1f),
                yPercent = (first.y / screenH.toFloat()).coerceIn(0f, 1f),
                recordedScreenW = screenW,
                recordedScreenH = screenH
            )
        }
    }

    if (GestureStore.save(this, edited)) {
        finishSmartToolEdit("📐 Tap percent anchors refreshed")
    } else {
        Toast.makeText(this, "Re-anchor save nahi hua", Toast.LENGTH_LONG).show()
    }
}

private fun normalizeActiveSwipePaths() {
    val saved = GestureStore.load(this)
        .filter { it.points.isNotEmpty() }
        .sortedBy { it.delayFromStart.coerceAtLeast(0L) }

    if (saved.isEmpty()) {
        Toast.makeText(this, "Swipe normalize ke liye saved macro nahi mila", Toast.LENGTH_SHORT).show()
        return
    }

    var changed = 0
    val edited = saved.map { gesture ->
        val cleaned = cleanToolPoints(gesture)
        if (cleaned.isEmpty() || !localGestureHasRealMovement(gesture.copy(points = cleaned))) {
            gesture.copy(points = cleaned.ifEmpty { gesture.points })
        } else {
            val maxPoints = 320
            val step = if (cleaned.size > maxPoints) kotlin.math.max(1, cleaned.size / maxPoints) else 1
            val sampled = cleaned
                .filterIndexed { index, _ -> index == 0 || index == cleaned.lastIndex || index % step == 0 }
                .fold(mutableListOf<GesturePoint>()) { acc, point ->
                    val safeT = if (acc.isEmpty()) 0L else point.t.coerceAtLeast(acc.last().t + 12L).coerceAtMost(600000L)
                    acc.add(point.copy(t = safeT))
                    acc
                }
            if (sampled != gesture.points) changed++
            gesture.copy(points = sampled)
        }
    }

    if (GestureStore.save(this, edited)) {
        finishSmartToolEdit("🧭 Swipe paths normalized: $changed")
    } else {
        Toast.makeText(this, "Swipe normalize save nahi hua", Toast.LENGTH_LONG).show()
    }
}

private fun edgeSafeClampActiveMacro() {
    val screenW = resources.displayMetrics.widthPixels.coerceAtLeast(1)
    val screenH = resources.displayMetrics.heightPixels.coerceAtLeast(1)
    val marginX = dp(6).toFloat()
    val topMargin = dp(18).toFloat()
    val bottomMargin = dp(18).toFloat()

    val saved = GestureStore.load(this)
        .filter { it.points.isNotEmpty() }
        .sortedBy { it.delayFromStart.coerceAtLeast(0L) }

    if (saved.isEmpty()) {
        Toast.makeText(this, "Edge clamp ke liye saved macro nahi mila", Toast.LENGTH_SHORT).show()
        return
    }

    var changed = 0
    val maxX = (screenW.toFloat() - marginX).coerceAtLeast(marginX)
    val maxY = (screenH.toFloat() - bottomMargin).coerceAtLeast(topMargin)

    val edited = saved.map { gesture ->
        if (isSystemRecordedGesture(gesture)) {
            gesture
        } else {
            val fixedPoints = gesture.points.map { point ->
                val rawX = if (point.x.isNaN() || point.x.isInfinite()) marginX else point.x
                val rawY = if (point.y.isNaN() || point.y.isInfinite()) topMargin else point.y
                val nx = rawX.coerceIn(marginX, maxX)
                val ny = rawY.coerceIn(topMargin, maxY)
                if (nx != point.x || ny != point.y) changed++
                point.copy(x = nx, y = ny, t = point.t.coerceAtLeast(0L).coerceAtMost(600000L))
            }

            val first = fixedPoints.firstOrNull()
            if (first == null) {
                gesture
            } else {
                gesture.copy(
                    points = fixedPoints,
                    xPercent = (first.x / screenW.toFloat()).coerceIn(0f, 1f),
                    yPercent = (first.y / screenH.toFloat()).coerceIn(0f, 1f),
                    recordedScreenW = screenW,
                    recordedScreenH = screenH
                )
            }
        }
    }

    if (changed == 0) {
        Toast.makeText(this, "🧲 Edge taps already safe hain", Toast.LENGTH_SHORT).show()
        return
    }

    if (GestureStore.save(this, edited)) {
        finishSmartToolEdit("🧲 Edge safe clamp applied: $changed point fixes")
    } else {
        Toast.makeText(this, "Edge clamp save nahi hua", Toast.LENGTH_LONG).show()
    }
}


// AARISH_DOUBLE_SLOW_TOOLS_V1: more offline autoclicker click-types without changing full UI.
private fun doubleTapLastTapInActiveConfig() {
    val saved = GestureStore.load(this)
        .filter { it.points.isNotEmpty() }
        .sortedBy { it.delayFromStart.coerceAtLeast(0L) }

    if (saved.isEmpty()) {
        Toast.makeText(this, "Double tap ke liye saved macro nahi mila", Toast.LENGTH_SHORT).show()
        return
    }

    val lastTap = saved.lastOrNull { gesture ->
        !isSystemRecordedGesture(gesture) &&
            !localGestureHasRealMovement(gesture) &&
            gestureDurationMs(gesture) < 450L
    }

    if (lastTap == null) {
        Toast.makeText(this, "Double tap banane ke liye normal tap step nahi mila", Toast.LENGTH_SHORT).show()
        return
    }

    val first = cleanToolPoints(lastTap).firstOrNull() ?: lastTap.points.first()
    val screenW = resources.displayMetrics.widthPixels.coerceAtLeast(1)
    val screenH = resources.displayMetrics.heightPixels.coerceAtLeast(1)
    val tapDuration = 85L
    val duplicateDelay = ((saved.maxOfOrNull { gestureEndMs(it) } ?: gestureEndMs(lastTap)) + 95L)
        .coerceAtMost(24L * 60L * 60L * 1000L)

    val duplicate = lastTap.copy(
        delayFromStart = duplicateDelay,
        points = listOf(first.copy(t = 0L), first.copy(t = tapDuration)),
        xPercent = (first.x / screenW.toFloat()).coerceIn(0f, 1f),
        yPercent = (first.y / screenH.toFloat()).coerceIn(0f, 1f),
        recordedScreenW = screenW,
        recordedScreenH = screenH
    )

    val edited = (saved + duplicate)
        .sortedBy { it.delayFromStart.coerceAtLeast(0L) }
        .take(1600)

    if (GestureStore.save(this, edited)) {
        finishSmartToolEdit("👆 Double tap added at macro end")
    } else {
        Toast.makeText(this, "Double tap save nahi hua", Toast.LENGTH_LONG).show()
    }
}

private fun slowDownActiveRecording(slowPercent: Int) {
    val percent = slowPercent.coerceIn(110, 500)
    val factor = percent / 100.0
    val saved = GestureStore.load(this)
        .filter { it.points.isNotEmpty() }
        .sortedBy { it.delayFromStart.coerceAtLeast(0L) }

    if (saved.isEmpty()) {
        Toast.makeText(this, "Slow down ke liye saved macro nahi mila", Toast.LENGTH_SHORT).show()
        return
    }

    var oldCursor = 0L
    var newCursor = 0L
    val edited = saved.map { gesture ->
        val rawDelay = gesture.delayFromStart.coerceAtLeast(0L)
        val rawGap = (rawDelay - oldCursor).coerceAtLeast(0L)
        val newDelay = (newCursor + (rawGap * factor).toLong().coerceAtLeast(0L))
            .coerceAtMost(24L * 60L * 60L * 1000L)
        val ordered = gesture.points
            .filter { !it.x.isNaN() && !it.x.isInfinite() && !it.y.isNaN() && !it.y.isInfinite() }
            .sortedBy { it.t.coerceAtLeast(0L) }
        val baseT = ordered.firstOrNull()?.t?.coerceAtLeast(0L) ?: 0L
        val newPoints = ordered.map { point ->
            point.copy(t = (((point.t.coerceAtLeast(0L) - baseT) * factor).toLong()).coerceAtLeast(0L).coerceAtMost(600000L))
        }.ifEmpty { gesture.points }

        val cloned = gesture.copy(delayFromStart = newDelay, points = newPoints)
        oldCursor = gestureEndMs(gesture)
        newCursor = gestureEndMs(cloned)
        cloned
    }

    if (GestureStore.save(this, edited)) {
        finishSmartToolEdit("🐢 Macro slowed: ${percent}% timing")
    } else {
        Toast.makeText(this, "Slow down save nahi hua", Toast.LENGTH_LONG).show()
    }
}

private fun showWorkflowHubDialog() {
        // AARISH_MASTER_CHAIN_WORKFLOW_BUILDER_MENU_V2
        if (isRecording || AutoActionService.isPlaying() || unsavedGestures.isNotEmpty()) {
            Toast.makeText(this, "Recording/Play/Unsaved edit ke time workflow change nahi kar sakte", Toast.LENGTH_SHORT).show()
            return
        }

        val active = GestureStore.getActiveConfigName(this)

        val items = arrayOf(
            "🧩 Build Config Chain",
            "👑 Master Chain Builder",
            "🔀 Configure NEXT Cycle",
            "🔁 Loop Settings",
            "▶️ Play From Any Config",
            "👀 Preview Active Workflow",
            "👑 Preview Master Chain",
            "🧹 Clear Active Chain Links",
            "🧽 Clear Master Chain"
        )

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Workflow Builder • $active")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showSelectStartConfigForWorkflowDialog()
                    1 -> showBuildMasterWorkflowDialog()
                    2 -> showChooseNextConfigDialog(active)
                    3 -> showLoopChoiceForConfig(active)
                    4 -> showPlayFromAnyConfigDialog()
                    5 -> showWorkflowPreviewDialog()
                    6 -> showMasterWorkflowPreviewDialog()
                    7 -> showClearFullChainDialog()
                    8 -> showClearMasterWorkflowDialog()
                }
            }
            .create()

        showOverlayDialogSafely(dialog)
    }



// AARISH_MASTER_CHAIN_UI_INSIDE_WORKFLOW_V4
private fun aarishMasterModeLabel(mode: String, value: Int): String {
    return when (mode.uppercase(java.util.Locale.US)) {
        "COUNT" -> "$value cycles"
        "TIME" -> "$value min"
        "INFINITE" -> "infinite"
        else -> "once"
    }
}


private fun aarishWorkflowDialogContentHeightV1(
    maxScreenPercent: Float = 0.72f,
    minDp: Int = 280,
    maxDp: Int = 560
): Int {
    // AARISH_WORKFLOW_CHAIN_BOUNDED_SCROLL_V1
    val pct = maxScreenPercent.coerceIn(0.30f, 0.90f)
    val screenLimit = (resources.displayMetrics.heightPixels * pct).toInt()
    return screenLimit.coerceIn(dp(minDp), dp(maxDp))
}

private fun aarishWorkflowListHeightV1(
    itemCount: Int,
    rowDp: Int = 52,
    minDp: Int = 104,
    maxDp: Int = 360,
    maxScreenPercent: Float = 0.42f
): Int {
    // AARISH_WORKFLOW_CHAIN_BOUNDED_SCROLL_V1
    val natural = dp((itemCount.coerceAtLeast(1) * rowDp).coerceIn(minDp, maxDp))
    val screenLimit = (resources.displayMetrics.heightPixels * maxScreenPercent.coerceIn(0.20f, 0.70f)).toInt()
    return natural.coerceAtMost(screenLimit.coerceAtLeast(dp(minDp)))
}

private fun aarishWorkflowTextHeightV1(
    text: String,
    minDp: Int = 72,
    maxDp: Int = 210,
    maxScreenPercent: Float = 0.28f
): Int {
    // AARISH_WORKFLOW_CHAIN_BOUNDED_SCROLL_V1
    val estimatedLines = text.lines().sumOf { line ->
        kotlin.math.max(1, (line.length / 34) + 1)
    }.coerceAtLeast(1)
    val natural = dp((estimatedLines * 20 + 24).coerceIn(minDp, maxDp))
    val screenLimit = (resources.displayMetrics.heightPixels * maxScreenPercent.coerceIn(0.12f, 0.45f)).toInt()
    return natural.coerceAtMost(screenLimit.coerceAtLeast(dp(minDp)))
}

private fun aarishAddWorkflowScrollableTextBlockV1(
    parent: android.widget.LinearLayout,
    text: String,
    textSizeSp: Float = 14f,
    textColor: Int = android.graphics.Color.rgb(30, 41, 59),
    minHeightDp: Int = 72,
    maxHeightDp: Int = 210,
    maxScreenPercent: Float = 0.28f,
    bottomMarginDp: Int = 8
) {
    // AARISH_WORKFLOW_CHAIN_BOUNDED_SCROLL_V1
    val textView = android.widget.TextView(this).apply {
        this.text = text
        textSize = textSizeSp
        setTextColor(textColor)
        setPadding(0, 0, dp(6), 0)
        isVerticalScrollBarEnabled = true
        try { movementMethod = android.text.method.ScrollingMovementMethod.getInstance() } catch (_: Throwable) {}
    }

    val scroll = android.widget.ScrollView(this).apply {
        isFillViewport = false
        overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
        isVerticalScrollBarEnabled = true
        addView(textView, -1, -2)
    }

    parent.addView(
        scroll,
        android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            aarishWorkflowTextHeightV1(
                text = text,
                minDp = minHeightDp,
                maxDp = maxHeightDp,
                maxScreenPercent = maxScreenPercent
            )
        ).apply { bottomMargin = dp(bottomMarginDp) }
    )
}

private fun aarishWrapWorkflowDialogViewV1(
    content: android.view.View,
    maxScreenPercent: Float = 0.72f,
    minDp: Int = 300,
    maxDp: Int = 560
): android.view.View {
    // AARISH_WORKFLOW_CHAIN_BOUNDED_SCROLL_V1
    content.minimumHeight = aarishWorkflowDialogContentHeightV1(
        maxScreenPercent = maxScreenPercent,
        minDp = minDp,
        maxDp = maxDp
    )
    return content
}


private fun showBuildMasterWorkflowDialog() {
    // AARISH_MASTER_CHAIN_ORCHESTRATOR_V16_HOME
    // AARISH_WORKFLOW_CHAIN_BOUNDED_SCROLL_V1
    val owner = GestureStore.getActiveConfigName(this)
    val steps = GestureStore.getMasterWorkflow(this, owner, 120)
    val summary = GestureStore.getMasterWorkflowSummary(this, owner)

    val box = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(dp(18), dp(12), dp(18), dp(8))
    }

    val infoText = """Master Owner: $owner
Master Steps: ${steps.size}

$summary

Master Chain me sirf saved Build Chains add karo. Har chain ka apna Once / Cycles / Minutes / Infinite mode hoga.""".trimIndent()

    aarishAddWorkflowScrollableTextBlockV1(
        parent = box,
        text = infoText,
        textSizeSp = 15f,
        textColor = android.graphics.Color.rgb(30, 41, 59),
        minHeightDp = 96,
        maxHeightDp = 220,
        maxScreenPercent = 0.28f,
        bottomMarginDp = 10
    )

    fun addBtn(title: String, bg: Int, action: () -> Unit) {
        val btn = android.widget.Button(this).apply {
            text = title
            textSize = 14f
            setAllCaps(false)
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener { action() }
            try {
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(bg)
                }
            } catch (_: Throwable) {}
        }
        box.addView(
            btn,
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply { bottomMargin = dp(8) }
        )
    }

    addBtn("➕ Add Chain", android.graphics.Color.rgb(124, 58, 237)) { showMasterConfigPicker() }
    addBtn("▶ Play Master Chain", android.graphics.Color.rgb(22, 163, 74)) { startMasterWorkflowFromDialog(owner) }
    addBtn("↩ Undo Last Chain", android.graphics.Color.rgb(234, 88, 12)) {
        val ok = GestureStore.removeLastMasterWorkflowStep(this, owner)
        Toast.makeText(this, if (ok) "Last master chain removed" else "No step found", Toast.LENGTH_SHORT).show()
        showBuildMasterWorkflowDialog()
    }
    addBtn("🗑 Delete One Step", android.graphics.Color.rgb(220, 38, 38)) { showDeleteMasterStepDialog() }
    addBtn("👀 Preview Master Chain", android.graphics.Color.rgb(15, 118, 110)) { showMasterWorkflowPreviewDialog() }
    addBtn("🧽 Clear Master Chain", android.graphics.Color.rgb(71, 85, 105)) { showClearMasterWorkflowDialog() }

    val scroll = android.widget.ScrollView(this).apply {
        isFillViewport = false
        overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
        addView(box)
    }

    val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
        .setTitle("👑 Master Chain Builder")
        .setView(aarishWrapWorkflowDialogViewV1(scroll, maxScreenPercent = 0.72f, minDp = 320, maxDp = 560))
        .setNegativeButton("Close", null)
        .create()

    showOverlayDialogSafely(dialog)
}





private fun showMasterConfigPicker() {
    // AARISH_MASTER_CHAIN_ORCHESTRATOR_V16_PICKER
    // AARISH_WORKFLOW_CHAIN_BOUNDED_SCROLL_V1
    val owner = GestureStore.getActiveConfigName(this)

    val chainOwners = GestureStore.getAllConfigNames(this)
        .filter { name ->
            GestureStore.getWorkflowPlaylist(this, name, 300).isNotEmpty()
        }
        .distinct()

    if (chainOwners.isEmpty()) {
        Toast.makeText(this, "Pehle Build Chain me koi chain banao", Toast.LENGTH_LONG).show()
        return
    }

    val current = if (GestureStore.getMasterWorkflow(this, owner, 120).isEmpty()) {
        "Abhi master chain empty hai."
    } else {
        GestureStore.getMasterWorkflowSummary(this, owner)
    }

    val rows = chainOwners.mapIndexed { index, name ->
        val seq = GestureStore.getRunnableWorkflowSequence(this, name, 300)
        "${index + 1}. $name (${seq.size} steps)"
    }

    val box = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(dp(18), dp(10), dp(18), dp(6))
    }

    val infoText = """Owner: $owner

$current

Next chain select karo. Select karte hi Once / Cycles / Minutes / Infinite pucha jayega.""".trimIndent()

    aarishAddWorkflowScrollableTextBlockV1(
        parent = box,
        text = infoText,
        textSizeSp = 14f,
        textColor = android.graphics.Color.rgb(30, 41, 59),
        minHeightDp = 88,
        maxHeightDp = 200,
        maxScreenPercent = 0.25f,
        bottomMarginDp = 8
    )

    val listView = android.widget.ListView(this).apply {
        dividerHeight = 1
        isFastScrollEnabled = true
        adapter = android.widget.ArrayAdapter(
            this@FloatingControlService,
            android.R.layout.simple_list_item_1,
            rows
        )
    }

    val listHeight = aarishWorkflowListHeightV1(
        itemCount = chainOwners.size,
        rowDp = 52,
        minDp = 104,
        maxDp = 310,
        maxScreenPercent = 0.36f
    )
    box.addView(
        listView,
        android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            listHeight
        )
    )

    var dialog: android.app.AlertDialog? = null

    listView.setOnItemClickListener { _, _, position, _ ->
        val selected = chainOwners[position]
        try { dialog?.dismiss() } catch (_: Throwable) {}
        handler.post { showMasterModeDialog(selected) }
    }

    dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
        .setTitle("Add Chain to Master")
        .setView(box)
        .setPositiveButton("Play Master") { _, _ -> startMasterWorkflowFromDialog(owner) }
        .setNeutralButton("Undo Last") { _, _ ->
            val ok = GestureStore.removeLastMasterWorkflowStep(this, owner)
            Toast.makeText(this, if (ok) "Last master chain removed" else "No step found", Toast.LENGTH_SHORT).show()
            handler.post { showMasterConfigPicker() }
        }
        .setNegativeButton("Back", null)
        .create()

    showOverlayDialogSafely(dialog)
}




private fun showMasterModeDialog(chainOwner: String) {
    // AARISH_MASTER_CHAIN_ORCHESTRATOR_V16_MODE
    val labels = arrayOf(
        "▶ Once",
        "🔢 Number of cycles",
        "⏱ Time minutes",
        "♾ Infinite"
    )

    val box = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(dp(18), dp(10), dp(18), dp(6))
    }

    val info = android.widget.TextView(this).apply {
        text = "Mode select karo: $chainOwner"
        textSize = 14f
        setTextColor(android.graphics.Color.rgb(71, 85, 105))
        setPadding(0, 0, 0, dp(8))
    }
    box.addView(info)

    val listView = android.widget.ListView(this).apply {
        dividerHeight = 1
        adapter = android.widget.ArrayAdapter(
            this@FloatingControlService,
            android.R.layout.simple_list_item_1,
            labels
        )
    }

    box.addView(
        listView,
        android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            dp(230)
        )
    )

    var dialog: android.app.AlertDialog? = null

    listView.setOnItemClickListener { _, _, which, _ ->
        try { dialog?.dismiss() } catch (_: Throwable) {}

        when (which) {
            0 -> saveMasterStep(chainOwner, "ONCE", 1)
            1 -> showMasterValueInput(chainOwner, "COUNT")
            2 -> showMasterValueInput(chainOwner, "TIME")
            3 -> saveMasterStep(chainOwner, "INFINITE", 1)
        }
    }

    dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
        .setTitle("Run mode for chain")
        .setView(box)
        .setNegativeButton("Cancel") { _, _ -> handler.post { showMasterConfigPicker() } }
        .create()

    showOverlayDialogSafely(dialog)
}



private fun showMasterValueInput(chainOwner: String, mode: String) {
    // AARISH_MASTER_CHAIN_ORCHESTRATOR_V16_VALUE
    val input = android.widget.EditText(this).apply {
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        hint = if (mode == "COUNT") "Cycles e.g. 3" else "Minutes e.g. 10"
        setText(if (mode == "COUNT") "3" else "10")
        setSelectAllOnFocus(true)
        setPadding(dp(18), dp(12), dp(18), dp(12))
    }

    val title = if (mode == "COUNT") {
        "How many cycles for $chainOwner?"
    } else {
        "How many minutes for $chainOwner?"
    }

    val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
        .setTitle(title)
        .setView(input)
        .setPositiveButton("Add", null)
        .setNegativeButton("Cancel") { _, _ -> handler.post { showMasterConfigPicker() } }
        .create()

    showOverlayDialogSafely(dialog)

    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
        val value = input.text?.toString()?.trim()?.toIntOrNull()
        if (value == null || value !in 1..100000) {
            Toast.makeText(this, "Valid number 1 se 100000 ke beech do", Toast.LENGTH_LONG).show()
            input.requestFocus()
        } else {
            dialog.dismiss()
            saveMasterStep(chainOwner, mode, value)
        }
    }
}



private fun saveMasterStep(chainOwner: String, mode: String, value: Int) {
    // AARISH_MASTER_CHAIN_ORCHESTRATOR_V16_SAVE
    val owner = GestureStore.getActiveConfigName(this)

    val ok = GestureStore.appendMasterWorkflowStep(
        context = this,
        ownerName = owner,
        startConfig = chainOwner,
        mode = mode,
        value = value
    )

    val msg = if (ok) {
        if (mode == "INFINITE") {
            "Added: $chainOwner × infinite. Note: iske baad wali chains tab tak nahi chalengi jab tak STOP/condition na ho."
        } else {
            "Added: $chainOwner × ${aarishMasterModeLabel(mode, value)}"
        }
    } else {
        "Add failed"
    }

    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()

    // Step add hone ke baad turant next chain select karne ke liye picker wapas khulega.
    showMasterConfigPicker()
}




private fun showDeleteMasterStepDialog() {
    // AARISH_MASTER_CHAIN_ORCHESTRATOR_V16_DELETE
    // AARISH_WORKFLOW_CHAIN_BOUNDED_SCROLL_V1
    val owner = GestureStore.getActiveConfigName(this)
    val steps = GestureStore.getMasterWorkflow(this, owner, 120)

    if (steps.isEmpty()) {
        Toast.makeText(this, "No master step", Toast.LENGTH_SHORT).show()
        return
    }

    val rows = steps.mapIndexed { index, step ->
        "${index + 1}. ${step.startConfig} × ${aarishMasterModeLabel(step.mode, step.value)}"
    }

    val box = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(dp(18), dp(10), dp(18), dp(6))
    }

    val hint = android.widget.TextView(this).apply {
        text = "Delete karne ke liye master step tap karo."
        textSize = 14f
        setTextColor(android.graphics.Color.rgb(71, 85, 105))
        setPadding(0, 0, 0, dp(8))
    }
    box.addView(hint)

    val listView = android.widget.ListView(this).apply {
        dividerHeight = 1
        isFastScrollEnabled = true
        adapter = android.widget.ArrayAdapter(
            this@FloatingControlService,
            android.R.layout.simple_list_item_1,
            rows
        )
    }

    val listHeight = aarishWorkflowListHeightV1(
        itemCount = steps.size,
        rowDp = 52,
        minDp = 104,
        maxDp = 390,
        maxScreenPercent = 0.44f
    )
    box.addView(
        listView,
        android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            listHeight
        )
    )

    var dialog: android.app.AlertDialog? = null

    listView.setOnItemClickListener { _, _, which, _ ->
        val ok = GestureStore.removeMasterWorkflowStep(this, owner, which)
        Toast.makeText(this, if (ok) "Master step deleted" else "Delete failed", Toast.LENGTH_SHORT).show()
        try { dialog?.dismiss() } catch (_: Throwable) {}
        handler.post { showBuildMasterWorkflowDialog() }
    }

    dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
        .setTitle("Delete master step")
        .setView(box)
        .setNegativeButton("Cancel", null)
        .create()

    showOverlayDialogSafely(dialog)
}




private fun startMasterWorkflowFromDialog(owner: String) {
    if (AutoActionService.isPlaying()) {
        AutoActionService.stopPlayback(this)
        playbackWatcherRunnable?.let { handler.removeCallbacks(it) }
        playbackWatcherRunnable = null
        val hasOld = GestureStore.hasRecording(this)
        updateUIState(if (hasOld) "PLAY" else "START", false, hasOld, true)
        restorePanelUI()
        Toast.makeText(this, "Playback stopped", Toast.LENGTH_SHORT).show()
        return
    }

    if (GestureStore.getMasterWorkflow(this, owner, 120).isEmpty()) {
        Toast.makeText(this, "Master Chain empty hai", Toast.LENGTH_SHORT).show()
        return
    }

    try { closeSettingsPanel() } catch (_: Exception) {}

    val started = AutoActionService.playMasterNow(this, owner)
    if (started) {
        updateUIState("STOP", false, false, false)
        hidePanelUIForPlayback()
        checkPlaybackStateContinuously()
    } else {
        val hasOld = GestureStore.hasRecording(this)
        updateUIState(if (hasOld) "PLAY" else "START", false, hasOld, true)
        restorePanelUI()
    }
}

private fun showPlaylistFinalRunModeDialog(configName: String) {
    // AARISH_BUILD_CHAIN_LIST_VISIBLE_V12_FINISH
    GestureStore.setActiveConfigName(this, configName)
    refreshConfigLabel()
    if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
    if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()
    restorePanelUI()

    val labels = arrayOf(
        "▶ Once",
        "🔢 Number of cycles",
        "⏱ Time minutes",
        "♾ Infinite"
    )

    val box = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(dp(18), dp(10), dp(18), dp(6))
    }

    val hint = android.widget.TextView(this).apply {
        text = "Is poori chain ko kaise chalana hai?"
        textSize = 14f
        setTextColor(android.graphics.Color.rgb(71, 85, 105))
        setPadding(0, 0, 0, dp(8))
    }
    box.addView(hint)

    val listView = android.widget.ListView(this).apply {
        dividerHeight = 1
        adapter = android.widget.ArrayAdapter(
            this@FloatingControlService,
            android.R.layout.simple_list_item_1,
            labels
        )
    }

    box.addView(
        listView,
        android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            dp(230)
        )
    )

    var dialog: android.app.AlertDialog? = null

    listView.setOnItemClickListener { _, _, which, _ ->
        try { dialog?.dismiss() } catch (_: Throwable) {}

        when (which) {
            0 -> {
                GestureStore.saveLoopSettingsForConfig(this, configName, "ONCE", 1)
                Toast.makeText(this, "Chain ready: once", Toast.LENGTH_SHORT).show()
                showWorkflowPreviewDialog()
            }
            1 -> showPlaylistLoopValueInput(configName, "COUNT")
            2 -> showPlaylistLoopValueInput(configName, "TIME")
            3 -> {
                GestureStore.saveLoopSettingsForConfig(this, configName, "INFINITE", 1)
                Toast.makeText(this, "Chain ready: infinite", Toast.LENGTH_SHORT).show()
                showWorkflowPreviewDialog()
            }
        }
    }

    dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
        .setTitle("Chain repeat mode")
        .setView(box)
        .setNegativeButton("Cancel", null)
        .create()

    showOverlayDialogSafely(dialog)
}





private fun showPlaylistLoopValueInput(configName: String, mode: String) {
    val input = android.widget.EditText(this).apply {
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        hint = if (mode == "COUNT") "Cycles e.g. 5" else "Minutes e.g. 30"
        setText(if (mode == "COUNT") "3" else "10")
        setSelectAllOnFocus(true)
    }

    val title = if (mode == "COUNT") "Playlist cycles" else "Playlist minutes"

    val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
        .setTitle(title)
        .setView(input)
        .setPositiveButton("Save") { _, _ ->
            val value = input.text?.toString()?.trim()?.toIntOrNull()?.coerceIn(1, 100000) ?: 1
            GestureStore.saveLoopSettingsForConfig(this, configName, mode, value)
            Toast.makeText(this, "Playlist ready: ${aarishMasterModeLabel(mode, value)}", Toast.LENGTH_SHORT).show()
            showWorkflowPreviewDialog()
        }
        .setNegativeButton("Cancel", null)
        .create()

    showOverlayDialogSafely(dialog)
}

private fun showMasterWorkflowPreviewDialog() {
    val owner = GestureStore.getActiveConfigName(this)
    val summary = GestureStore.getMasterWorkflowSummary(this, owner)

    val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
        .setTitle("👑 Master Chain Preview")
        .setMessage("""Owner: $owner

$summary""".trimIndent())
        .setPositiveButton("OK", null)
        .create()

    showOverlayDialogSafely(dialog)
}

private fun showClearMasterWorkflowDialog() {
    val owner = GestureStore.getActiveConfigName(this)

    val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
        .setTitle("Clear Master Chain?")
        .setMessage("""Owner: $owner

Sirf master chain clear hogi. Recordings/config playlist delete nahi honge.""".trimIndent())
        .setPositiveButton("Clear") { _, _ ->
            GestureStore.clearMasterWorkflow(this, owner)
            if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()
            Toast.makeText(this, "Master chain cleared", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("Cancel", null)
        .create()

    showOverlayDialogSafely(dialog)
}


private fun showSelectStartConfigForWorkflowDialog() {
    // AARISH_BUILD_CHAIN_LIST_VISIBLE_V12_START
    // AARISH_WORKFLOW_CHAIN_BOUNDED_SCROLL_V1
    val configs = GestureStore.getAllConfigNames(this)
        .filter { GestureStore.hasRecordingForConfig(this, it) }
        .distinct()

    if (configs.isEmpty()) {
        Toast.makeText(this, "Workflow ke liye pehle saved config chahiye", Toast.LENGTH_LONG).show()
        return
    }

    val rows = configs.mapIndexed { index, name ->
        "${index + 1}. $name"
    }

    val box = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(dp(18), dp(10), dp(18), dp(6))
    }

    val hint = android.widget.TextView(this).apply {
        text = "Pehli config select karo. List hide nahi hogi."
        textSize = 14f
        setTextColor(android.graphics.Color.rgb(71, 85, 105))
        setPadding(0, 0, 0, dp(8))
    }
    box.addView(hint)

    val listView = android.widget.ListView(this).apply {
        dividerHeight = 1
        isFastScrollEnabled = true
        adapter = android.widget.ArrayAdapter(
            this@FloatingControlService,
            android.R.layout.simple_list_item_1,
            rows
        )
    }

    val listHeight = aarishWorkflowListHeightV1(
        itemCount = configs.size,
        rowDp = 52,
        minDp = 104,
        maxDp = 390,
        maxScreenPercent = 0.44f
    )
    box.addView(
        listView,
        android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            listHeight
        )
    )

    var dialog: android.app.AlertDialog? = null

    listView.setOnItemClickListener { _, _, position, _ ->
        val startConfig = configs[position]

        GestureStore.clearChainFrom(this, startConfig)
        GestureStore.clearWorkflowPlaylist(this, startConfig)
        GestureStore.setActiveConfigName(this, startConfig)
        GestureStore.appendToWorkflowPlaylist(this, startConfig, startConfig)

        refreshConfigLabel()
        if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
        if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()
        restorePanelUI()

        Toast.makeText(this, "Added: $startConfig", Toast.LENGTH_SHORT).show()

        try { dialog?.dismiss() } catch (_: Throwable) {}

        handler.post {
            showSelectNextConfigForWorkflowDialog(
                currentConfig = startConfig,
                stepNumber = 2,
                originalStartConfig = startConfig
            )
        }
    }

    dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
        .setTitle("Build Chain")
        .setView(box)
        .setNegativeButton("Cancel", null)
        .create()

    showOverlayDialogSafely(dialog)
}







private fun showSelectNextConfigForWorkflowDialog(
    currentConfig: String,
    stepNumber: Int = 2,
    originalStartConfig: String = currentConfig
) {
    // AARISH_BUILD_CHAIN_LIST_VISIBLE_V12_NEXT
    // AARISH_WORKFLOW_CHAIN_BOUNDED_SCROLL_V1
    val owner = originalStartConfig
    val configs = GestureStore.getAllConfigNames(this)
        .filter { GestureStore.hasRecordingForConfig(this, it) }
        .distinct()

    if (configs.isEmpty()) {
        Toast.makeText(this, "Saved config nahi mili", Toast.LENGTH_LONG).show()
        return
    }

    val steps = GestureStore.getWorkflowPlaylist(this, owner, 300)
    val summary = if (steps.isEmpty()) "Empty" else steps.joinToString(" ➜ ")

    val rows = configs.mapIndexed { index, name ->
        "${index + 1}. $name"
    }

    val box = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(dp(18), dp(10), dp(18), dp(6))
    }

    val chainText = "Current Chain:\n$summary\n\nTap config name to add again. Same config repeat allowed."
    aarishAddWorkflowScrollableTextBlockV1(
        parent = box,
        text = chainText,
        textSizeSp = 14f,
        textColor = android.graphics.Color.rgb(30, 41, 59),
        minHeightDp = 88,
        maxHeightDp = 190,
        maxScreenPercent = 0.24f,
        bottomMarginDp = 8
    )

    val listView = android.widget.ListView(this).apply {
        dividerHeight = 1
        isFastScrollEnabled = true
        adapter = android.widget.ArrayAdapter(
            this@FloatingControlService,
            android.R.layout.simple_list_item_1,
            rows
        )
    }

    val listHeight = aarishWorkflowListHeightV1(
        itemCount = configs.size,
        rowDp = 52,
        minDp = 104,
        maxDp = 320,
        maxScreenPercent = 0.36f
    )
    box.addView(
        listView,
        android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            listHeight
        )
    )

    var dialog: android.app.AlertDialog? = null

    listView.setOnItemClickListener { _, _, position, _ ->
        val selected = configs[position]
        GestureStore.appendToWorkflowPlaylist(this, owner, selected)

        Toast.makeText(this, "Added: $selected", Toast.LENGTH_SHORT).show()
        if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()

        try { dialog?.dismiss() } catch (_: Throwable) {}

        handler.post {
            showSelectNextConfigForWorkflowDialog(
                currentConfig = selected,
                stepNumber = stepNumber + 1,
                originalStartConfig = owner
            )
        }
    }

    dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
        .setTitle("Build Chain")
        .setView(box)
        .setPositiveButton("Finish") { _, _ ->
            val finalSteps = GestureStore.getWorkflowPlaylist(this, owner, 300)
            if (finalSteps.isEmpty()) {
                Toast.makeText(this, "Chain empty hai", Toast.LENGTH_SHORT).show()
                handler.post { showSelectStartConfigForWorkflowDialog() }
            } else {
                handler.post { showPlaylistFinalRunModeDialog(owner) }
            }
        }
        .setNeutralButton("Undo Last") { _, _ ->
            val ok = GestureStore.removeLastWorkflowPlaylistStep(this, owner)
            Toast.makeText(this, if (ok) "Last step removed" else "No step found", Toast.LENGTH_SHORT).show()

            val left = GestureStore.getWorkflowPlaylist(this, owner, 300)
            handler.post {
                if (left.isEmpty()) {
                    showSelectStartConfigForWorkflowDialog()
                } else {
                    showSelectNextConfigForWorkflowDialog(
                        currentConfig = left.last(),
                        stepNumber = left.size + 1,
                        originalStartConfig = owner
                    )
                }
            }
        }
        .setNegativeButton("Clear") { _, _ ->
            GestureStore.clearWorkflowPlaylist(this, owner)
            Toast.makeText(this, "Chain cleared", Toast.LENGTH_SHORT).show()
            handler.post { showSelectStartConfigForWorkflowDialog() }
        }
        .create()

    showOverlayDialogSafely(dialog)
}









    private fun showLoopChoiceForConfig(configName: String) {
        val items = arrayOf(
            "▶️ Once",
            "🔢 Number of Cycles",
            "⏱️ Minutes",
            "♾️ Infinity Loop"
        )

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Step 3: '$configName' workflow ko kaise loop karna hai?")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        GestureStore.saveLoopSettingsForConfig(this, configName, "ONCE", 1)
                        Toast.makeText(this, "Ready: $configName • Once", Toast.LENGTH_SHORT).show()
                    }

                    1 -> showNumberInputDialog(
                        title = "Kitne cycles chalana hai?",
                        hint = "Example: 5",
                        defaultValue = GestureStore.getLoopValueForConfig(this, configName).takeIf { it > 1 } ?: 10,
                        min = 1,
                        max = 9999
                    ) { value ->
                        GestureStore.saveLoopSettingsForConfig(this, configName, "COUNT", value)
                        Toast.makeText(this, "Ready: $configName • $value cycles", Toast.LENGTH_SHORT).show()
                        if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
                        if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()
                    }

                    2 -> showNumberInputDialog(
                        title = "Kitne minutes chalana hai?",
                        hint = "Example: 15",
                        defaultValue = GestureStore.getLoopValueForConfig(this, configName).takeIf { it > 1 } ?: 5,
                        min = 1,
                        max = 1440
                    ) { value ->
                        GestureStore.saveLoopSettingsForConfig(this, configName, "TIME", value)
                        Toast.makeText(this, "Ready: $configName • $value minutes", Toast.LENGTH_SHORT).show()
                        if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
                        if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()
                    }

                    3 -> {
                        GestureStore.saveLoopSettingsForConfig(this, configName, "INFINITE", 0)
                        Toast.makeText(this, "Ready: $configName • Infinity Loop", Toast.LENGTH_SHORT).show()
                    }
                }

                if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
                if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()
                restorePanelUI()
            }
            .create()

        showOverlayDialogSafely(dialog)
    }

    private fun showPlayFromAnyConfigDialog() {
        val configs = GestureStore.getAllConfigNames(this)
            .filter { GestureStore.hasRecordingForConfig(this, it) }
            .distinct()

        if (configs.isEmpty()) {
            Toast.makeText(this, "Play ke liye koi saved config nahi mili", Toast.LENGTH_LONG).show()
            return
        }

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Kaun si config se workflow start karna hai?")
            .setItems(configs.toTypedArray()) { _, which ->
                val selected = configs[which]
                GestureStore.setActiveConfigName(this, selected)
                refreshConfigLabel()
                if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
                if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()
                restorePanelUI()
                handleStartButton()
            }
            .create()

        showOverlayDialogSafely(dialog)
    }


    private fun showChooseNextConfigDialog(currentConfig: String) {
        val candidates = GestureStore.getAllConfigNames(this)
            .filter { configName ->
                configName != currentConfig &&
                    GestureStore.hasRecordingForConfig(this, configName)
            }
            .distinct()

        if (candidates.isEmpty()) {
            Toast.makeText(this, "NEXT Cycle ke liye koi aur saved config nahi mili", Toast.LENGTH_LONG).show()
            return
        }

        val currentList = GestureStore.getNextConfigList(this, currentConfig)
        val checkedItems = BooleanArray(candidates.size) { index ->
            currentList.contains(candidates[index])
        }

        val currentText = if (currentList.isEmpty()) {
            "Abhi koi NEXT cycle set nahi hai."
        } else {
            "Current cycle:\n" + currentList.joinToString(" ➜ ")
        }

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Configure NEXT Cycle • $currentConfig")
            .setMessage(
                "$currentText\n\n" +
                    "Jitni configs tick karoge, playback har loop me unko rotate karega.\n\n" +
                    "Example: 1-2-3 ➜ [A/B/C] ➜ 5-10"
            )
            .setMultiChoiceItems(candidates.toTypedArray(), checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Save Cycle") { _, _ ->
                val selected = candidates.filterIndexed { index, _ -> checkedItems[index] }
                val valid = selected.filterNot { next ->
                    GestureStore.wouldCreateCycle(this, currentConfig, next)
                }

                if (selected.size != valid.size) {
                    Toast.makeText(this, "Kuch cycle/circular links skip kar diye", Toast.LENGTH_LONG).show()
                }

                val ok = GestureStore.setNextConfigList(this, currentConfig, valid)

                Toast.makeText(
                    this,
                    if (ok) {
                        if (valid.isEmpty()) "NEXT Cycle clear ho gaya"
                        else "NEXT Cycle saved: ${valid.size} config"
                    } else {
                        "NEXT Cycle save nahi hua"
                    },
                    Toast.LENGTH_LONG
                ).show()

                updateWorkflowButtonUI()
                restorePanelUI()
            }
            .setNeutralButton("Clear") { _, _ ->
                val ok = GestureStore.setNextConfigList(this, currentConfig, emptyList())
                Toast.makeText(
                    this,
                    if (ok) "NEXT Cycle clear ho gaya" else "Clear save nahi hua",
                    Toast.LENGTH_SHORT
                ).show()
                updateWorkflowButtonUI()
                restorePanelUI()
            }
            .setNegativeButton("Cancel", null)
            .create()

        showOverlayDialogSafely(dialog)
    }

private fun showLoopSettingsDialog() {
        val active = GestureStore.getActiveConfigName(this)
        showLoopChoiceForConfig(active)
    }


    private fun showNumberInputDialog(
        title: String,
        hint: String,
        defaultValue: Int,
        min: Int,
        max: Int,
        onOk: (Int) -> Unit
    ) {
        val input = android.widget.EditText(this).apply {
            setSingleLine(true)
            this.hint = hint
            setText(defaultValue.coerceIn(min, max).toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            selectAll()
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        showOverlayDialogSafely(dialog)

        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val value = input.text?.toString()?.trim()?.toIntOrNull()
            if (value == null || value !in min..max) {
                Toast.makeText(this, "Valid number $min se $max ke beech do", Toast.LENGTH_LONG).show()
                input.requestFocus()
            } else {
                onOk(value)
                dialog.dismiss()
            }
        }
    }

    private fun showWorkflowPreviewDialog() {
        val active = GestureStore.getActiveConfigName(this)
        val chain = GestureStore.getWorkflowSummary(this, active)
        val mode = GestureStore.getLoopModeForConfig(this, active)
        val value = GestureStore.getLoopValueForConfig(this, active)

        val loopText = when (mode) {
            "COUNT" -> "$value cycles"
            "INFINITE" -> "Infinity"
            "TIME" -> "$value minutes"
            else -> "Once"
        }

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Workflow Preview")
            .setMessage("Chain:\n$chain\n\nLoop:\n$loopText\n\nPLAY dabane par yahi poora workflow chalega.")
            .setPositiveButton("OK", null)
            .create()

        showOverlayDialogSafely(dialog)
    }

    private fun showClearFullChainDialog() {
        val active = GestureStore.getActiveConfigName(this)

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Clear Active Chain Playlist?")
            .setMessage("""Isse '$active' ki saved chain playlist/NEXT links delete honge.
Recordings aur Master Chain delete nahi honge.""".trimIndent())
            .setPositiveButton("Clear Chain") { _, _ ->
                val playlistCount = GestureStore.getWorkflowPlaylist(this, active, 300).size
                val legacyCount = GestureStore.clearChainFrom(this, active)
                GestureStore.clearWorkflowPlaylist(this, active)
                updateWorkflowButtonUI()
                Toast.makeText(this, "${playlistCount + legacyCount} chain links clear ho gaye", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()

        showOverlayDialogSafely(dialog)
    }
    private fun showConfigManagerDialog() {
        if (isRecording || AutoActionService.isPlaying() || unsavedGestures.isNotEmpty()) {
            Toast.makeText(this, "Recording/Play/Unsaved edit ke time config change nahi kar sakte", Toast.LENGTH_SHORT).show()
            return
        }

        val savedConfigs = GestureStore.getAllConfigNames(this).toMutableList()
        val active = GestureStore.getActiveConfigName(this)
        val items = (savedConfigs + listOf(
            "🧱 Workflow Builder",
            "🔁 Loop Settings",
            "➕ Create New Config"
        )).toTypedArray()

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Macro Configs • $active")
            .setItems(items) { _, which ->
                when {
                    which < savedConfigs.size -> {
                        val selected = savedConfigs[which]
                        GestureStore.setActiveConfigName(this, selected)
                        refreshConfigLabel()
                        if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
                        if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()

                        unsavedGestures = emptyList()
                        glassHiddenAt = 0L
                        nextNavigationGapOverride = null
                        pendingDiscardConfirm = false

                        val hasOld = GestureStore.hasRecording(this)
                        updateUIState(if (hasOld) "PLAY" else "START", false, hasOld, true)
                        restorePanelUI()

                        Toast.makeText(this, "Switched: $selected", Toast.LENGTH_SHORT).show()
                    }

                    which == savedConfigs.size -> showWorkflowHubDialog()
                    which == savedConfigs.size + 1 -> showLoopSettingsDialog()
                    which == savedConfigs.size + 2 -> showCreateConfigDialog()
                }
            }
            .setNeutralButton("Delete Active Config") { _, _ ->
                val current = GestureStore.getActiveConfigName(this)
                val deleted = GestureStore.deleteConfig(this, current)
                unsavedGestures = emptyList()
                glassHiddenAt = 0L
                nextNavigationGapOverride = null
                pendingDiscardConfirm = false
                refreshConfigLabel()
                if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
                if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()
                val hasOld = GestureStore.hasRecording(this)
                updateUIState(if (hasOld) "PLAY" else "START", false, hasOld, true)
                restorePanelUI()
                Toast.makeText(
                    this,
                    if (deleted) "Deleted: $current" else "Default Config delete nahi ho sakta",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .create()

        showOverlayDialogSafely(dialog)
    }

    private fun showCreateConfigDialog() {
        if (isRecording || AutoActionService.isPlaying() || unsavedGestures.isNotEmpty()) {
            Toast.makeText(this, "Pehle current recording/save complete karo", Toast.LENGTH_SHORT).show()
            return
        }

        val input = android.widget.EditText(this).apply {
            hint = "New Config Name"
            setSingleLine(true)
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Create Config Block")
            .setView(input)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create()

        showOverlayDialogSafely(dialog)

        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val name = input.text?.toString()
                ?.replace(Regex("[\r\n\t]+"), " ")
                ?.replace(Regex("\\s+"), " ")
                ?.replace(Regex("[^\\p{L}\\p{N} _.-]+"), "")
                ?.trim()
                ?.take(40)
                .orEmpty()

            if (name.isBlank()) {
                Toast.makeText(this, "Config name empty nahi ho sakta", Toast.LENGTH_SHORT).show()
                input.requestFocus()
                return@setOnClickListener
            }

            GestureStore.setActiveConfigName(this, name)
            refreshConfigLabel()
            if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
            if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()

            unsavedGestures = emptyList()
            glassHiddenAt = 0L
            nextNavigationGapOverride = null
            pendingDiscardConfirm = false

            updateUIState("START", false, false, true)
            restorePanelUI()

            Toast.makeText(this, "Ready: $name", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }


    private fun stopEverythingAndClose() {
        cancelClipboardCopyArmV6() // AARISH_MEMORY_CLIPBOARD_TWOSTEP_V6_CLEANUP

        val liveGestures = if (isRecording) captureView?.getRecordedGestures().orEmpty() else emptyList()
        val hasLiveRecording = liveGestures.isNotEmpty()

        if ((unsavedGestures.isNotEmpty() || hasLiveRecording) && !pendingDiscardConfirm) {
            pendingDiscardConfirm = true
            Toast.makeText(this, "Unsaved recording hai! Discard karne ke liye dobara CUT dabao.", Toast.LENGTH_LONG).show()
            // AARISH_DISCARD_CONFIRM_GUARD_V1: stale delayed callback destroyed service par state change na kare.
        handler.postDelayed({
            if (instance === this@FloatingControlService) pendingDiscardConfirm = false
        }, 5000L)
            return
        }

        pendingDiscardConfirm = false
        glassHiddenAt = 0L
        nextNavigationGapOverride = null
        unsavedGestures = emptyList()
        isRecording = false
        // AARISH_LIVE_REPLAY_QUEUE_CLEAR_V1
        liveReplayQueue.clear()
        liveReplayQueueDraining = false
        liveReplayActive = false
        liveReplaySerial++
        playbackWatcherRunnable?.let { handler.removeCallbacks(it) }
        playbackWatcherRunnable = null
        // AARISH_V2_REMOVEVIEW_CLEANUP
        aarishResetLiveReplayStateSafe(forceSolid = false)
        safeRemoveView(captureView)
        captureView = null
        if (AutoActionService.isPlaying()) AutoActionService.stopPlayback(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun makePanelDraggable(dragHandle: View, root: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        val dragSlop = dp(10).toFloat()

        dragHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    if (!isDragging &&
                        (kotlin.math.abs(dx) > dragSlop || kotlin.math.abs(dy) > dragSlop)
                    ) {
                        isDragging = true
                    }

                    if (isDragging) {
                        val metrics = root.context.resources.displayMetrics
                        val maxX = if (root.width > 0) metrics.widthPixels - root.width else metrics.widthPixels / 2
                        val maxY = if (root.height > 0) metrics.heightPixels - root.height else metrics.heightPixels / 2

                        val newX = initialX + dx.toInt()
                        val newY = initialY + dy.toInt()

                        params.x = newX.coerceIn(0, if (maxX > 0) maxX else metrics.widthPixels)
                        params.y = newY.coerceIn(0, if (maxY > 0) maxY else metrics.heightPixels)

                        safeUpdateView(root, params)
                    }

                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        dragHandle.performClick()
                    }
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    true
                }

                else -> true
            }
        }
    }

private fun safeAddView(view: View, params: WindowManager.LayoutParams, errorMessage: String): Boolean {
    // AARISH_ACCESSIBILITY_OVERLAY_SAFE_V13_SAFE_ADD
    val wm = aarishAccessWmV13()
    return try {
        if (view.parent != null) {
            try {
                wm.removeViewImmediate(view)
            } catch (_: Exception) {
                try { wm.removeView(view) } catch (_: Exception) {}
            }
        }
        wm.addView(view, params)
        true
    } catch (e: Exception) {
        Toast.makeText(this, "$errorMessage: ${e.message}", Toast.LENGTH_LONG).show()
        false
    }
}



private fun safeUpdateView(view: View, params: WindowManager.LayoutParams) {
    // AARISH_ACCESSIBILITY_OVERLAY_SAFE_V13_SAFE_UPDATE
    try {
        aarishAccessWmV13().updateViewLayout(view, params)
    } catch (_: Exception) {}
}



private fun safeRemoveView(view: View?) {
    // AARISH_ACCESSIBILITY_OVERLAY_SAFE_V13_SAFE_REMOVE
    if (view == null) return
    val wm = aarishAccessWmV13()
    try {
        wm.removeViewImmediate(view)
    } catch (_: Exception) {
        try { wm.removeView(view) } catch (_: Exception) {}
    }
}





    // AARISH_ULTRA_TOUCH_SYSTEM_V2_START
// AARISH_LIVE_REPLAY_QUEUE_DRAIN_V1
// AARISH_ULTRA_TOUCH_SYSTEM_V2_END
    // AARISH_ADVANCED_BLINK_STRIKE_GHOST_ENGINE_V1

// AARISH_PREMIUM_DESKTOP_APP_LAUNCHER_UI_V4_FINAL
private fun shouldShowAppLauncherButton(): Boolean {
    return !AutoActionService.isPlaying()
}

private fun updateAppLauncherVisibility() {
    if (::btnApps.isInitialized) {
        btnApps.visibility = if (shouldShowAppLauncherButton()) View.VISIBLE else View.GONE
    }
}

private fun closeAarishDesktopLauncher() {
    // AARISH_LAUNCHER_ABOVE_GLASS_ZFIX_V1
    val v = aarishDesktopLauncherView
    aarishDesktopLauncherView = null
    aarishDesktopLauncherParams = null

    if (v != null) {
        try {
            val wm = if (::windowManager.isInitialized) aarishAccessWmV13() else null
            if (wm != null && v.parent != null) {
                try {
                    wm.removeViewImmediate(v)
                } catch (_: Throwable) {
                    try { wm.removeView(v) } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {
            try {
                if (::windowManager.isInitialized && v.parent != null) {
                    windowManager.removeViewImmediate(v)
                }
            } catch (_: Throwable) {
                try {
                    if (::windowManager.isInitialized && v.parent != null) {
                        windowManager.removeView(v)
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    if (isRecording && captureView != null && !AutoActionService.isPlaying()) {
        handler.postDelayed({
            if (instance === this@FloatingControlService &&
                isRecording &&
                captureView != null &&
                aarishDesktopLauncherView == null &&
                !AutoActionService.isPlaying()
            ) {
                aarishSetGlassGhostModeSafe(false)
            }
        }, 80L)
    }
}

private fun aarishDesktopV4LaunchFromFloat(pkg: String, activity: String, mode: String): Boolean {
    val cleanPkg = pkg.trim()
    val cleanActivity = activity.trim()
    val cleanMode = mode.trim().uppercase(java.util.Locale.US)
    val floating = cleanMode.contains("FLOAT")

    if (cleanPkg.isBlank()) return false

    fun launchBounds(): android.graphics.Rect {
        val dm = resources.displayMetrics
        val sw = dm.widthPixels.coerceAtLeast(1)
        val sh = dm.heightPixels.coerceAtLeast(1)

        if (!floating) {
            return android.graphics.Rect(0, 0, sw, sh)
        }

        val prefs = getSharedPreferences("aarish_desktop_launcher", Context.MODE_PRIVATE)
        val seq = ((prefs.getInt("float_seq", 0) + 1) % 5)
        prefs.edit().putInt("float_seq", seq).apply()

        val w = (sw * 0.72f).toInt().coerceAtLeast(dp(260))
        val h = (sh * 0.58f).toInt().coerceAtLeast(dp(320))
        val left = ((sw * 0.06f).toInt() + seq * dp(28)).coerceAtMost((sw - dp(260)).coerceAtLeast(0))
        val top = ((sh * 0.08f).toInt() + seq * dp(36)).coerceAtMost((sh - dp(320)).coerceAtLeast(0))

        return android.graphics.Rect(left, top, (left + w).coerceAtMost(sw), (top + h).coerceAtMost(sh))
    }

    fun optionsBundle(): android.os.Bundle? {
        if (android.os.Build.VERSION.SDK_INT < 16) return null

        val opts = android.app.ActivityOptions.makeCustomAnimation(this, 0, 0)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                val m = android.app.ActivityOptions::class.java.getDeclaredMethod(
                    "setLaunchWindowingMode",
                    java.lang.Integer.TYPE
                )
                m.isAccessible = true
                m.invoke(opts, if (floating) 5 else 1)
            } catch (_: Throwable) {
            }

            try {
                opts.setLaunchBounds(launchBounds())
            } catch (_: Throwable) {
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            try {
                val m = android.app.ActivityOptions::class.java.getDeclaredMethod(
                    "setPendingIntentBackgroundActivityStartMode",
                    java.lang.Integer.TYPE
                )
                m.isAccessible = true
                val allowed = android.app.ActivityOptions::class.java
                    .getField("MODE_BACKGROUND_ACTIVITY_START_ALLOWED")
                    .getInt(null)
                m.invoke(opts, allowed)
            } catch (_: Throwable) {
            }
        }

        return opts.toBundle()
    }

    fun bringExistingTaskToFront(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            ?: return false

        return try {
            @Suppress("DEPRECATION")
            val recent = am.getRecentTasks(
                60,
                android.app.ActivityManager.RECENT_IGNORE_UNAVAILABLE
            )

            val task = recent.firstOrNull { info ->
                info.baseIntent?.component?.packageName == cleanPkg ||
                    info.origActivity?.packageName == cleanPkg
            } ?: return false

            try {
                am.moveTaskToFront(task.id, 0, optionsBundle())
            } catch (_: Throwable) {
                am.moveTaskToFront(task.id, 0)
            }

            true
        } catch (_: Throwable) {
            false
        }
    }

    if (bringExistingTaskToFront()) return true

    return try {
        val intent = if (cleanActivity.isNotBlank()) {
            android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                setClassName(cleanPkg, cleanActivity)
                addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
        } else {
            packageManager.getLaunchIntentForPackage(cleanPkg)?.apply {
                addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            } ?: return false
        }

        val bundle = optionsBundle()
        if (bundle != null) startActivity(intent, bundle) else startActivity(intent)
        true
    } catch (_: Throwable) {
        try {
            val fallback = packageManager.getLaunchIntentForPackage(cleanPkg) ?: return false
            fallback.addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            startActivity(fallback)
            true
        } catch (_: Throwable) {
            false
        }
    }
}

private fun launchAppFromAarishDesktopLauncher(
    appLabel: String,
    pkg: String,
    activity: String,
    mode: String
) {
    closeAarishDesktopLauncher()

    val cleanMode = if (mode.uppercase(java.util.Locale.US).contains("FLOAT")) {
        "FLOATING"
    } else {
        "FULLSCREEN"
    }

    val wasRecording = isRecording && captureView != null
    if (!wasRecording) {
        startRecording()
    }

    handler.postDelayed({
        if (instance !== this@FloatingControlService) return@postDelayed

        val recorded = if (isRecording && captureView != null) {
            captureView?.addAppLaunchGesture(appLabel, pkg, activity, cleanMode) == true
        } else {
            false
        }

        if (recorded) {
            updateUIState("DONE", true, false, true)
            restorePanelUI()
            updateAppLauncherVisibility()
        }

        val ok = aarishDesktopV4LaunchFromFloat(pkg, activity, cleanMode)

        Toast.makeText(
            this,
            when {
                ok && recorded && cleanMode == "FLOATING" -> "✅ Recorded + floating: $appLabel"
                ok && recorded -> "✅ Recorded + front: $appLabel"
                ok && cleanMode == "FLOATING" -> "🪟 Floating: $appLabel"
                ok -> "📱 Front: $appLabel"
                else -> "❌ App launch fail"
            },
            Toast.LENGTH_SHORT
        ).show()
    }, 120L)
}


// AARISH_PREMIUM_LAUNCHER_UIUX_V5_HELPERS
private fun aarishLauncherPrefs(): android.content.SharedPreferences {
    return getSharedPreferences("aarish_premium_launcher_ui", Context.MODE_PRIVATE)
}

private fun aarishPinnedApps(): MutableSet<String> {
    return aarishLauncherPrefs()
        .getStringSet("pinned_apps_v1", emptySet())
        ?.toMutableSet() ?: mutableSetOf()
}

private fun aarishIsPinnedApp(pkg: String): Boolean {
    return aarishPinnedApps().contains(pkg)
}

private fun aarishTogglePinnedApp(pkg: String): Boolean {
    val set = aarishPinnedApps()
    val nowPinned = if (set.contains(pkg)) {
        set.remove(pkg)
        false
    } else {
        set.add(pkg)
        true
    }
    aarishLauncherPrefs().edit().putStringSet("pinned_apps_v1", set).apply()
    return nowPinned
}

private fun aarishRoundBg(
    color: Int,
    radiusDp: Int = 18,
    strokeColor: Int = Color.TRANSPARENT,
    strokeDp: Int = 0
): android.graphics.drawable.GradientDrawable {
    return android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
        if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
    }
}

private fun aarishCircleBg(
    color: Int,
    strokeColor: Int = Color.TRANSPARENT,
    strokeDp: Int = 0
): android.graphics.drawable.GradientDrawable {
    return android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(color)
        if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
    }
}

private fun aarishTinyLabel(textValue: String, colorValue: Int): TextView {
    return TextView(this).apply {
        text = textValue
        setTextColor(colorValue)
        textSize = 11f
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
}


private fun renderAarishDesktopModeChoice(
    titleText: TextView,
    search: android.widget.EditText,
    listBox: LinearLayout,
    appLabel: String,
    pkg: String,
    activity: String
) {
    // AARISH_PREMIUM_LAUNCHER_UIUX_V5_MODE_CHOICE
    titleText.text = appLabel
    search.visibility = View.GONE
    listBox.removeAllViews()

    val pm = packageManager

    val rootCard = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = aarishRoundBg(
            Color.argb(245, 15, 23, 42),
            26,
            Color.argb(90, 96, 165, 250),
            1
        )
    }

    val top = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    val icon = android.widget.ImageView(this).apply {
        try {
            setImageDrawable(pm.getApplicationIcon(pkg))
        } catch (_: Throwable) {
            setImageResource(android.R.drawable.sym_def_app_icon)
        }
        background = aarishRoundBg(Color.argb(255, 30, 41, 59), 18)
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    val textCol = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), 0, 0, 0)
    }

    val name = TextView(this).apply {
        text = appLabel
        setTextColor(Color.WHITE)
        textSize = 18f
        setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    val sub = aarishTinyLabel("Choose launch style", Color.argb(185, 203, 213, 225))

    textCol.addView(name)
    textCol.addView(sub)

    top.addView(icon, LinearLayout.LayoutParams(dp(54), dp(54)))
    top.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    rootCard.addView(top)

    val modeRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(0, dp(18), 0, dp(6))
    }

    fun modeButton(symbol: String, mini: String, bg: Int, onTap: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = aarishRoundBg(bg, 24, Color.argb(80, 255, 255, 255), 1)
            setPadding(dp(10), dp(12), dp(10), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { onTap() }

            val s = TextView(this@FloatingControlService).apply {
                text = symbol
                textSize = 27f
                gravity = Gravity.CENTER
            }

            val m = TextView(this@FloatingControlService).apply {
                text = mini
                textSize = 11f
                setTextColor(Color.argb(210, 241, 245, 249))
                gravity = Gravity.CENTER
                setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            }

            addView(s, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)))
            addView(m, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    val floating = modeButton(
        "🪟",
        "FLOAT",
        Color.argb(255, 88, 28, 135)
    ) {
        launchAppFromAarishDesktopLauncher(appLabel, pkg, activity, "FLOATING")
    }

    val full = modeButton(
        "▣",
        "FULL",
        Color.argb(255, 14, 116, 144)
    ) {
        launchAppFromAarishDesktopLauncher(appLabel, pkg, activity, "FULLSCREEN")
    }

    modeRow.addView(floating, LinearLayout.LayoutParams(0, dp(88), 1f).apply { rightMargin = dp(8) })
    modeRow.addView(full, LinearLayout.LayoutParams(0, dp(88), 1f).apply { leftMargin = dp(8) })

    rootCard.addView(modeRow)

    val bottom = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(8), 0, 0)
    }

    val back = TextView(this).apply {
        text = "← Apps"
        setTextColor(Color.argb(235, 219, 234, 254))
        textSize = 14f
        setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        gravity = Gravity.CENTER
        background = aarishRoundBg(Color.argb(120, 30, 41, 59), 16)
        setPadding(dp(14), dp(9), dp(14), dp(9))
        isClickable = true
        isFocusable = true
        setOnClickListener { showAarishAppLauncher() }
    }

    val hint = aarishTinyLabel("Tap icon only • no long text", Color.argb(145, 148, 163, 184)).apply {
        gravity = Gravity.RIGHT
    }

    bottom.addView(back)
    bottom.addView(hint, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    rootCard.addView(bottom)

    listBox.addView(
        rootCard,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = dp(2)
            rightMargin = dp(2)
            topMargin = dp(10)
        }
    )
}

private fun showAarishAppLauncher() {
    // AARISH_PREMIUM_LAUNCHER_UIUX_V5_SHOW
    if (!shouldShowAppLauncherButton()) {
        Toast.makeText(this, "App Launcher abhi available nahi", Toast.LENGTH_SHORT).show()
        return
    }

    closeAarishDesktopLauncher()

    if (!::windowManager.isInitialized) {
        Toast.makeText(this, "WindowManager ready nahi", Toast.LENGTH_SHORT).show()
        return
    }

    val pm = packageManager
    val baseIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
    }

    data class AppItem(
        val label: String,
        val pkg: String,
        val activity: String,
        val icon: android.graphics.drawable.Drawable?
    )

    val apps = try {
        pm.queryIntentActivities(baseIntent, 0)
            .mapNotNull { info ->
                val ai = info.activityInfo ?: return@mapNotNull null
                val p = ai.packageName ?: return@mapNotNull null
                val a = ai.name ?: return@mapNotNull null
                if (p == packageName) return@mapNotNull null

                val label = info.loadLabel(pm)
                    ?.toString()
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    .orEmpty()

                if (label.isBlank()) return@mapNotNull null

                AppItem(
                    label = label,
                    pkg = p,
                    activity = a,
                    icon = try { info.loadIcon(pm) } catch (_: Throwable) { null }
                )
            }
            .distinctBy { "${it.pkg}/${it.activity}" }
            .sortedWith(
                compareByDescending<AppItem> { aarishIsPinnedApp(it.pkg) }
                    .thenBy { it.label.lowercase(java.util.Locale.US) }
            )
            .take(280)
    } catch (_: Throwable) {
        emptyList()
    }

    if (apps.isEmpty()) {
        Toast.makeText(this, "App list nahi mili", Toast.LENGTH_SHORT).show()
        return
    }

    val dm = resources.displayMetrics
    val width = (dm.widthPixels * 0.92f).toInt().coerceAtLeast(dp(292))
    val height = (dm.heightPixels * 0.76f).toInt().coerceAtLeast(dp(390))

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(14))
        background = aarishRoundBg(
            Color.argb(248, 8, 13, 24),
            30,
            Color.argb(85, 99, 102, 241),
            1
        )
    }

    val titleRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(2), 0, dp(2), dp(8))
    }

    val orb = TextView(this).apply {
        text = "⌘"
        textSize = 22f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = aarishCircleBg(Color.argb(255, 37, 99, 235), Color.argb(120, 147, 197, 253), 1)
    }

    val titleCol = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), 0, 0, 0)
    }

    val title = TextView(this).apply {
        text = "Launcher"
        setTextColor(Color.WHITE)
        textSize = 19f
        setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        letterSpacing = 0.04f
    }

    val subtitle = aarishTinyLabel("Pinned apps stay on top", Color.argb(170, 203, 213, 225))

    titleCol.addView(title)
    titleCol.addView(subtitle)

    val close = TextView(this).apply {
        text = "×"
        textSize = 30f
        gravity = Gravity.CENTER
        setTextColor(Color.argb(235, 226, 232, 240))
        background = aarishRoundBg(Color.argb(155, 30, 41, 59), 18)
        isClickable = true
        isFocusable = true
        setOnClickListener { closeAarishDesktopLauncher() }
    }

    titleRow.addView(orb, LinearLayout.LayoutParams(dp(44), dp(44)))
    titleRow.addView(titleCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    titleRow.addView(close, LinearLayout.LayoutParams(dp(50), dp(44)))
    root.addView(titleRow)

    val search = android.widget.EditText(this).apply {
        hint = "Search apps"
        setSingleLine(true)
        textSize = 15f
        setTextColor(Color.WHITE)
        setHintTextColor(Color.argb(150, 203, 213, 225))
        setPadding(dp(14), 0, dp(14), 0)
        background = aarishRoundBg(
            Color.argb(220, 15, 23, 42),
            18,
            Color.argb(80, 96, 165, 250),
            1
        )
    }

    root.addView(
        search,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(48)
        ).apply {
            topMargin = dp(2)
            bottomMargin = dp(10)
        }
    )

    val scroll = android.widget.ScrollView(this).apply {
        isFillViewport = false
        isClickable = true
        isFocusable = true
    }

    val listBox = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(2), 0, dp(8))
    }

    scroll.addView(listBox, -1, -2)

    root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

    fun addSection(label: String) {
        val s = TextView(this).apply {
            text = label
            setTextColor(Color.argb(210, 147, 197, 253))
            textSize = 11f
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            letterSpacing = 0.12f
            setPadding(dp(4), dp(9), dp(4), dp(6))
        }
        listBox.addView(s)
    }

    fun addAppRow(app: AppItem) {
        val pinned = aarishIsPinnedApp(app.pkg)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(8), dp(8))
            background = aarishRoundBg(
                if (pinned) Color.argb(238, 30, 41, 59) else Color.argb(225, 15, 23, 42),
                20,
                if (pinned) Color.argb(120, 250, 204, 21) else Color.argb(45, 148, 163, 184),
                1
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                renderAarishDesktopModeChoice(title, search, listBox, app.label, app.pkg, app.activity)
            }
        }

        val icon = android.widget.ImageView(this).apply {
            if (app.icon != null) setImageDrawable(app.icon) else setImageResource(android.R.drawable.sym_def_app_icon)
            background = aarishRoundBg(Color.argb(255, 241, 245, 249), 14)
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }

        val name = TextView(this).apply {
            text = app.label
            setTextColor(Color.argb(245, 248, 250, 252))
            textSize = 15f
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val pkgSmall = aarishTinyLabel(app.pkg, Color.argb(125, 203, 213, 225))

        textCol.addView(name)
        textCol.addView(pkgSmall)

        val pin = TextView(this).apply {
            text = if (pinned) "★" else "☆"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(if (pinned) Color.argb(255, 250, 204, 21) else Color.argb(190, 148, 163, 184))
            background = aarishCircleBg(Color.argb(90, 30, 41, 59))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val nowPinned = aarishTogglePinnedApp(app.pkg)
                Toast.makeText(
                    this@FloatingControlService,
                    if (nowPinned) "Pinned: ${app.label}" else "Unpinned: ${app.label}",
                    Toast.LENGTH_SHORT
                ).show()
                showAarishAppLauncher()
            }
        }

        row.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
        row.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(pin, LinearLayout.LayoutParams(dp(42), dp(42)))

        listBox.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(68)
            ).apply {
                bottomMargin = dp(8)
            }
        )
    }

    fun render(filter: String) {
        listBox.removeAllViews()

        val q = filter.trim().lowercase(java.util.Locale.US)
        val filtered = if (q.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.lowercase(java.util.Locale.US).contains(q) ||
                    it.pkg.lowercase(java.util.Locale.US).contains(q)
            }
        }

        val pinned = filtered.filter { aarishIsPinnedApp(it.pkg) }
        val normal = filtered.filterNot { aarishIsPinnedApp(it.pkg) }

        if (pinned.isNotEmpty()) {
            addSection("PINNED")
            pinned.forEach { addAppRow(it) }
        }

        addSection(if (pinned.isNotEmpty()) "ALL APPS" else "APPS")
        normal.take(160).forEach { addAppRow(it) }

        if (filtered.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No app found"
                setTextColor(Color.LTGRAY)
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, dp(34), 0, dp(34))
            }
            listBox.addView(empty, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    search.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            render(s?.toString().orEmpty())
        }
        override fun afterTextChanged(s: android.text.Editable?) {}
    })

    render("")

    // AARISH_LAUNCHER_ABOVE_GLASS_ZFIX_V1
    // Launcher ko same overlay stack me rakho jisme glass/panel hain.
    // Isse app list glass card/control panel ke neeche dabegi nahi.
    val launcherWm = aarishAccessWmV13()
    val type = aarishAccessOverlayTypeV13()

    if (isRecording && captureView != null && !AutoActionService.isPlaying()) {
        aarishSetGlassGhostModeSafe(true)
    }

    val params = WindowManager.LayoutParams(
        width,
        height,
        type,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        x = 0
        y = dp(50)
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }

    aarishDesktopLauncherView = root
    aarishDesktopLauncherParams = params

    try {
        launcherWm.addView(root, params)
        root.bringToFront()
        search.requestFocus()

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        handler.postDelayed({
            imm?.showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 100L)
    } catch (e: Throwable) {
        aarishDesktopLauncherView = null
        aarishDesktopLauncherParams = null

        if (isRecording && captureView != null && !AutoActionService.isPlaying()) {
            aarishSetGlassGhostModeSafe(false)
        }

        Toast.makeText(this, "Launcher open fail: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}



private fun recordWaitAiAction() {
    // AARISH_AI_WAIT_BUTTON_V1_RECORD
    if (!isRecording) {
        Toast.makeText(this, "AI WAIT sirf recording ke time add hota hai", Toast.LENGTH_SHORT).show()
        return
    }

    val view = captureView
    if (view == null) {
        Toast.makeText(this, "Recording glass ready nahi hai", Toast.LENGTH_SHORT).show()
        return
    }

    val added = view.addWaitAiGesture()
    if (!added) {
        Toast.makeText(this, "AI WAIT step add nahi hua", Toast.LENGTH_SHORT).show()
        return
    }

    pendingDiscardConfirm = false
    updateUIState("DONE", true, false, true)
    restorePanelUI()
    Toast.makeText(this, "⏳ AI WAIT recorded: next recorded target ka wait karega", Toast.LENGTH_LONG).show()
}

private fun showSystemActionRecorderDialog() {
        if (!isRecording) {
            Toast.makeText(this, "SYS sirf Glass ON recording ke time use karo", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Record System Action")
            .setItems(arrayOf("🔙 BACK", "🗂️ RECENTS")) { _, which ->
                when (which) {
                    0 -> recordSystemAction(1)
                    1 -> recordSystemAction(2)
                }
                updateUIState("DONE", true, false, true)
            }
            .setNegativeButton("Cancel", null)
            .create()

        showOverlayDialogSafely(dialog)
    }

    fun recordSystemAction(actionType: Int) {
        if (!isRecording) return
        val view = captureView
        if (view == null) {
            android.widget.Toast.makeText(this, "Recording glass ready nahi hai", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val msg = when (actionType) {
            1 -> "🔙 BACK recorded"
            2 -> "🗂️ RECENTS recorded"
            else -> {
                android.widget.Toast.makeText(this, "Unknown system action skip hua", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
        }

        view.addSystemGesture(actionType)
        triggerLiveSystemActionSafe(actionType)
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    // AARISH_SHARE_MENU_LIVE_GUARD_V4_START
    // V4:
    // 1) Live replay ke synthetic click ko semantic recorder se mute.
    // 2) Share/dialog/window change par overlay debounce recover.
    // 3) Playback ke time panel hidden-state disturb nahi hota.
    @Volatile private var semanticAccessibilityBridgeUntil = 0L
    @Volatile private var semanticClickMuteUntil = 0L
    private var lastSemanticAccessibilityClickAt = 0L
    private var lastSemanticAccessibilityClickKey = ""
    private var lastOverlayRecoverAt = 0L
    private var overlayRecoverRunnable: Runnable? = null


fun notifyExternalWindowChangedFromAccessibility() {
    handler.post {
        if (instance !== this@FloatingControlService) return@post
        if (!::windowManager.isInitialized) return@post

        val now = android.os.SystemClock.uptimeMillis()

        val replayBusy = isRecording &&
            captureView != null &&
            (liveReplayActive || liveReplayQueueDraining)

        if (replayBusy) {
            semanticClickMuteUntil = kotlin.math.max(semanticClickMuteUntil, now + 1800L)
            aarishSetGlassGhostModeSafe(true)
            return@post
        }

        if (isRecording && captureView != null && !AutoActionService.isPlaying()) {
            if (lastGhostState == true && !liveReplayActive && !liveReplayQueueDraining) {
                aarishSetGlassGhostModeSafe(false)
            }

            if (now > semanticClickMuteUntil) {
                semanticAccessibilityBridgeUntil = kotlin.math.max(
                    semanticAccessibilityBridgeUntil,
                    now + 9000L
                )
            }
            return@post
        }

        if (AutoActionService.isPlaying()) {
            recoverOverlayStackToFrontDebounced(180L)
            return@post
        }

        recoverOverlayStackToFrontDebounced(170L)
    }
}









    fun shouldRecordAccessibilitySemanticClick(): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        return isRecording &&
            captureView != null &&
            now <= semanticAccessibilityBridgeUntil &&
            now > semanticClickMuteUntil &&
            !liveReplayActive &&
            !AutoActionService.isPlaying()
    }



    fun recordAccessibilitySemanticClickFromSnapshot(snapshot: TargetSnapshot) {
        handler.post {
            if (instance !== this@FloatingControlService) return@post
            if (!shouldRecordAccessibilitySemanticClick()) return@post

            val now = android.os.SystemClock.uptimeMillis()
            if (now <= semanticClickMuteUntil) return@post

            val pkg = snapshot.targetPackage.orEmpty().trim().lowercase()
            if (
                pkg.isBlank() ||
                pkg == packageName.lowercase() ||
                false
            ) {
                return@post
            }

            val key = semanticSnapshotKey(snapshot)
            if (key == lastSemanticAccessibilityClickKey && now - lastSemanticAccessibilityClickAt < 900L) {
                return@post
            }

            val oldAt = lastSemanticAccessibilityClickAt
            val added = captureView?.addAccessibilitySnapshotGesture(snapshot) == true
            if (!added) return@post

            lastSemanticAccessibilityClickAt = now
            lastSemanticAccessibilityClickKey = key
            semanticAccessibilityBridgeUntil = now + 7500L
            pendingDiscardConfirm = false

            updateUIState("DONE", true, false, true)
            restorePanelUI()

            if (now - oldAt > 1200L) {
                Toast.makeText(this, "🧩 Share/Dialog click record ho gaya", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun semanticSnapshotKey(snapshot: TargetSnapshot): String {
        return listOf(
            snapshot.targetPackage.orEmpty(),
            snapshot.targetId.orEmpty(),
            snapshot.targetText.orEmpty(),
            snapshot.targetDesc.orEmpty(),
            "${snapshot.targetLeft},${snapshot.targetTop},${snapshot.targetRight},${snapshot.targetBottom}"
        ).joinToString("|")
    }

private fun recoverOverlayStackToFrontDebounced(delayMs: Long = 170L) {
        // Recording mode me glass/card stable rakho.
        // Queue waiting ke dauran ghost mat karo. Ghost sirf actual replay drain me.
        if (isRecording && captureView != null && !AutoActionService.isPlaying()) {
            if (liveReplayActive || liveReplayQueueDraining) {
                aarishSetGlassGhostModeSafe(true)
            } else if (lastGhostState == true) {
                aarishSetGlassGhostModeSafe(false)
            }
            return
        }

        overlayRecoverRunnable?.let { handler.removeCallbacks(it) }

        val task = Runnable {
            overlayRecoverRunnable = null

            if (isRecording && captureView != null && !AutoActionService.isPlaying()) {
                if (liveReplayActive || liveReplayQueueDraining) {
                    aarishSetGlassGhostModeSafe(true)
                } else if (lastGhostState == true) {
                    aarishSetGlassGhostModeSafe(false)
                }
                return@Runnable
            }

            recoverOverlayStackToFrontNow()
        }

        overlayRecoverRunnable = task
        handler.postDelayed(task, delayMs.coerceIn(40L, 420L))
    }

    private fun recoverOverlayStackToFrontNow() {
        if (instance !== this@FloatingControlService) return
        if (!::windowManager.isInitialized) return

        // Recording ke dauran remove/add bilkul nahi.
        // Remove/add hi blink ka common reason hota hai.
        if (isRecording && captureView != null && !AutoActionService.isPlaying()) {
            if (liveReplayActive || liveReplayQueueDraining) {
                aarishSetGlassGhostModeSafe(true)
            } else if (lastGhostState == true) {
                aarishSetGlassGhostModeSafe(false)
            }
            return
        }

        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastOverlayRecoverAt < 1500L) return
        lastOverlayRecoverAt = now

        val panel = panelView
        val params = panelParams

        if (panel == null) {
            showFloatingPanel()
            return
        }

        if (params != null) {
            if (AutoActionService.isPlaying()) {
                hidePanelUIForPlayback()
            } else {
                restorePanelUI()
            }
            reAddOverlayViewSilently(panel, params)
        }
    }




private fun reAddOverlayViewSilently(view: View, params: WindowManager.LayoutParams) {
    // AARISH_NO_PANEL_FLICKER_FINAL_V1:
    // AARISH_ACCESSIBILITY_OVERLAY_SAFE_V13_READD
    fun addOrUpdateQuietly(): Boolean {
        val wm = aarishAccessWmV13()
        return try {
            if (view.parent == null) {
                wm.addView(view, params)
            } else {
                wm.updateViewLayout(view, params)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    if (addOrUpdateQuietly()) return

    val wm = aarishAccessWmV13()
    try {
        if (view.parent != null) {
            try {
                wm.removeViewImmediate(view)
            } catch (_: Exception) {
                try { wm.removeView(view) } catch (_: Exception) {}
            }
        }
    } catch (_: Exception) {}

    handler.postDelayed({
        if (instance !== this@FloatingControlService || !::windowManager.isInitialized) return@postDelayed
        if (view === captureView && !isRecording) return@postDelayed
        addOrUpdateQuietly()
    }, 230L)
}



    // AARISH_SHARE_MENU_LIVE_GUARD_V4_END

        override fun onDestroy() {
        cancelClipboardCopyArmV6() // AARISH_MEMORY_CLIPBOARD_TWOSTEP_V6_CLEANUP

        closeAarishDesktopLauncher() // AARISH_PREMIUM_DESKTOP_APP_DESTROY_CLEANUP_V4_FINAL
        playbackWatcherRunnable?.let { handler.removeCallbacks(it) }
        playbackWatcherRunnable = null
        closeSettingsPanel()
        // AARISH_V2_HANDLER_CLEANUP
        aarishResetLiveReplayStateSafe()
        handler.removeCallbacksAndMessages(null)
        isRecording = false
        liveReplayActive = false
        liveReplaySerial++
        // AARISH_LIVE_REPLAY_QUEUE_DESTROY_CLEAR_V1
        liveReplayQueue.clear()
        liveReplayQueueDraining = false
        pendingDiscardConfirm = false
        glassHiddenAt = 0L
        nextNavigationGapOverride = null
        // AARISH_V2_REMOVEVIEW_CLEANUP
        aarishResetLiveReplayStateSafe()
        safeRemoveView(captureView)
        safeRemoveView(panelView)
        captureView = null
        panelView = null
        if (AutoActionService.isPlaying()) AutoActionService.stopPlayback(this)
        if (instance == this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }


    private var glassHiddenAt = 0L
    private var nextNavigationGapOverride: Long? = null

    // ✅ SAFE UI STATE FIX: Default parameter added to prevent compile errors
    private fun closeSettingsPanel() {
        try {
            activeConfigDialog?.dismiss()
        } catch (_: Exception) {
        }
        activeConfigDialog = null
    }


    private fun hasAnythingToUndo(): Boolean {
        if (AutoActionService.isPlaying()) return false
        if (isRecording && captureView?.hasRecordedSomething() == true) return true
        if (unsavedGestures.isNotEmpty()) return true
        return GestureStore.hasRecording(this)
    }
private fun updateUIState(startText: String, showSave: Boolean, showOthers: Boolean, showCut: Boolean = true) {
    if (::btnStart.isInitialized) btnStart.text = startText

    val hasSavedRecording = GestureStore.hasRecording(this)
    val canShowSave = showSave && (isRecording || unsavedGestures.isNotEmpty())

    if (::btnSave.isInitialized) btnSave.visibility = if (canShowSave) View.VISIBLE else View.GONE
    if (::btnUndo.isInitialized) btnUndo.visibility = if (hasAnythingToUndo()) View.VISIBLE else View.GONE

    if (::btnLoop.isInitialized) {
        updateLoopButtonText(btnLoop)
        btnLoop.visibility = if (showOthers && hasSavedRecording && !isRecording) View.VISIBLE else View.GONE
    }

    if (::btnWorkflow.isInitialized) {
        updateWorkflowButtonUI()
        btnWorkflow.visibility = if (showOthers && hasSavedRecording && !isRecording) View.VISIBLE else View.GONE
    }

    if (::btnTools.isInitialized) {
        btnTools.visibility = if (showOthers && hasSavedRecording && !isRecording) View.VISIBLE else View.GONE
    }

    if (::btnSystem.isInitialized) {
        btnSystem.visibility = if (isRecording) View.VISIBLE else View.GONE
    }

    if (::btnAiWait.isInitialized) {
        btnAiWait.visibility = if (isRecording) View.VISIBLE else View.GONE
        if (::btnXyOnly.isInitialized) btnXyOnly.visibility = if (isRecording) View.VISIBLE else View.GONE
    }

    if (::btnCut.isInitialized) btnCut.visibility = if (showCut) View.VISIBLE else View.GONE
    btnClear?.visibility = if (showOthers && hasSavedRecording && unsavedGestures.isEmpty() && !isRecording) View.VISIBLE else View.GONE

    refreshPanelButtonStyles()
}

    // ✅ EXACT NAVIGATION GAP FIX
private fun extractAndAppendGestures() {
    val view = captureView ?: return
    val newGestures = view.getRecordedGestures()
        .filter { it.points.isNotEmpty() }
        .sortedBy { it.delayFromStart }

    if (newGestures.isEmpty()) return

    val lastGestureEnd = unsavedGestures.maxOfOrNull { gestureEndMs(it) } ?: 0L

    val rawNavigationGap = if (unsavedGestures.isNotEmpty() && glassHiddenAt > 0L) {
        view.getRecordingStartTime() - glassHiddenAt
    } else {
        0L
    }

    val navigationGap = (nextNavigationGapOverride ?: rawNavigationGap)
        .coerceAtLeast(0L)
        .coerceAtMost(120000L)

    val timeOffset = if (unsavedGestures.isEmpty()) 0L else lastGestureEnd + navigationGap

    unsavedGestures = (unsavedGestures + newGestures.map { g ->
        g.copy(delayFromStart = (g.delayFromStart + timeOffset).coerceAtLeast(0L))
    }).sortedBy { it.delayFromStart }

    nextNavigationGapOverride = null
}

        private fun handleStartButton() {
        pendingDiscardConfirm = false

        if (AutoActionService.isPlaying()) {
            AutoActionService.stopPlayback(this)
            playbackWatcherRunnable?.let { handler.removeCallbacks(it) }
            playbackWatcherRunnable = null
            val hasOld = GestureStore.hasRecording(this)
            updateUIState(if (hasOld) "PLAY" else "START", false, hasOld, true)
            restorePanelUI()
            Toast.makeText(this, "Playback stopped", Toast.LENGTH_SHORT).show()
            return
        }

        if (isRecording) {
            extractAndAppendGestures()
            isRecording = false
            // AARISH_V2_REMOVEVIEW_CLEANUP
            // forceSolid=false: view remove hone wali hai, alpha restore ki zaroorat nahi
            aarishResetLiveReplayStateSafe(forceSolid = false)
            safeRemoveView(captureView)
            captureView = null
            glassHiddenAt = android.os.SystemClock.uptimeMillis()
            nextNavigationGapOverride = null

            val hasOld = GestureStore.hasRecording(this)
            val hasUnsaved = unsavedGestures.isNotEmpty()
            val nextText = if (hasUnsaved) "+ ADD" else if (hasOld) "PLAY" else "START"

            updateUIState(nextText, hasUnsaved, hasOld, true)

            if (hasUnsaved) {
                Toast.makeText(this, "✅ Recording segment DONE. SAVE dabao ya +ADD se aur steps jodo.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Koi step record nahi hua", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (unsavedGestures.isNotEmpty()) {
            startRecording()
            return
        }

        if (GestureStore.hasRecording(this)) {
            try { closeSettingsPanel() } catch (_: Exception) {}
            val started = AutoActionService.playNow(this)
            if (started) {
                updateUIState("STOP", false, false, false)
                hidePanelUIForPlayback()
                checkPlaybackStateContinuously()
            } else {
                updateUIState("PLAY", false, true, true)
                restorePanelUI()
            }
            return
        }

        unsavedGestures = emptyList()
        glassHiddenAt = 0L
        nextNavigationGapOverride = null
        startRecording()
    }


private fun startRecording() {
    try { closeSettingsPanel() } catch (_: Exception) {}

    if (isRecording) return

    // AARISH_V2_REMOVEVIEW_CLEANUP
    // forceSolid=false: glass turant remove hogi, alpha restore waste hai
    aarishResetLiveReplayStateSafe(forceSolid = false)
    safeRemoveView(captureView)
    captureView = null
    pendingDiscardConfirm = false

    val touchLayer = TouchCaptureView(this)
    val overlayType = aarishAccessOverlayTypeV13()

    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        overlayType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
        PixelFormat.TRANSLUCENT
    )
    params.gravity = Gravity.TOP or Gravity.START

    if (!safeAddView(touchLayer, params, "Recording layer error")) {
        isRecording = false
        captureView = null
        val hasOld = GestureStore.hasRecording(this)
        updateUIState(
            if (unsavedGestures.isNotEmpty()) "+ ADD" else if (hasOld) "PLAY" else "START",
            unsavedGestures.isNotEmpty(),
            hasOld,
            true
        )
        restorePanelUI()
        return
    }

    captureView = touchLayer
    lastGhostState = false
    isRecording = true
    updateUIState("DONE", true, false, true)
    bringPanelToFront()
    Toast.makeText(
        this,
        "🟢 LIVE Glass ON! Tap/swipe/double-tap/long-press record + live fire. Share/Dialog fallback bhi ON hai.",
        Toast.LENGTH_SHORT
    ).show()
}



    private fun undoLastStep() {
        if (AutoActionService.isPlaying()) {
            Toast.makeText(this, "Pehle playback STOP/complete hone do", Toast.LENGTH_SHORT).show()
            return
        }

        // Case 1: Glass ON hai, live recording ke andar last tap delete karo
        if (isRecording) {
            val removed = captureView?.removeLastGesture() == true
            if (removed) {
                Toast.makeText(this, "↩️ Last live step delete ho gaya", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Undo ke liye abhi koi live step nahi hai", Toast.LENGTH_SHORT).show()
            }
            updateUIState("DONE", true, false, true)
            return
        }

        // Case 2: SAVE se pehle unsaved buffer ke andar last step delete karo
        if (unsavedGestures.isNotEmpty()) {
            val sortedUnsaved = unsavedGestures.sortedBy { it.delayFromStart }
            val removedGesture = sortedUnsaved.lastOrNull()
            val remainingGestures = sortedUnsaved.dropLast(1)
            val previousEnd = remainingGestures.maxOfOrNull { gestureEndMs(it) } ?: 0L
            val preservedGap = ((removedGesture?.delayFromStart ?: previousEnd) - previousEnd)
                .coerceAtLeast(0L)
                .coerceAtMost(120000L)

            unsavedGestures = remainingGestures

            if (unsavedGestures.isEmpty()) {
                glassHiddenAt = 0L
                nextNavigationGapOverride = null
            } else {
                // Undo Time-Freeze Fix:
                // Purana gap preserve rahega + naya wait time bhi live add hota rahega.
                glassHiddenAt = android.os.SystemClock.uptimeMillis() - preservedGap
                nextNavigationGapOverride = null
            }

            val hasOld = GestureStore.hasRecording(this)
            val nextText = if (unsavedGestures.isNotEmpty()) "+ ADD" else if (hasOld) "PLAY" else "START"

            updateUIState(nextText, unsavedGestures.isNotEmpty(), hasOld, true)
            restorePanelUI()
            Toast.makeText(this, "↩️ Last unsaved step delete ho gaya", Toast.LENGTH_SHORT).show()
            return
        }

        // Case 3: SAVE + PLAY ke baad bhi saved memory se last step delete karo
        val savedGestures = GestureStore.load(this).sortedBy { it.delayFromStart }

        if (savedGestures.isNotEmpty()) {
            val edited = savedGestures.dropLast(1)

            if (edited.isEmpty()) {
                GestureStore.clear(this)
                unsavedGestures = emptyList()
                glassHiddenAt = 0L
                nextNavigationGapOverride = null
                updateUIState("START", false, false, true)
                restorePanelUI()
                Toast.makeText(this, "↩️ Last step delete hua. Ab memory empty hai.", Toast.LENGTH_SHORT).show()
                return
            }

            val ok = GestureStore.save(this, edited)
            if (!ok) {
                Toast.makeText(this, "❌ Undo save nahi ho paya", Toast.LENGTH_LONG).show()
                return
            }

            unsavedGestures = emptyList()
            glassHiddenAt = 0L
            nextNavigationGapOverride = null

            updateUIState("PLAY", false, true, true)
            restorePanelUI()
            Toast.makeText(this, "↩️ Saved memory ka last step delete ho gaya.", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Undo karne ke liye kuch nahi hai", Toast.LENGTH_SHORT).show()
    }

private fun saveRecording() {
    if (isSavingRecording) {
        Toast.makeText(this, "Save already chal raha hai", Toast.LENGTH_SHORT).show()
        return
    }
    isSavingRecording = true
    try {
    if (isRecording) {
        val liveView = captureView
        if (liveView != null) {
            extractAndAppendGestures()
            // AARISH_V2_REMOVEVIEW_CLEANUP
            aarishResetLiveReplayStateSafe(forceSolid = false)
            safeRemoveView(liveView)
        } else {
            Toast.makeText(this, "⚠️ Recording layer missing thi. Jo saved buffer hai wahi save hoga.", Toast.LENGTH_LONG).show()
        }
        isRecording = false
        captureView = null
    }

    glassHiddenAt = 0L
    nextNavigationGapOverride = null
    pendingDiscardConfirm = false

    var lastEnd = 0L
    val cleanGestures = unsavedGestures
        .filter { it.points.isNotEmpty() }
        .sortedBy { it.delayFromStart.coerceAtLeast(0L) }
        .mapNotNull { gesture ->
            // AARISH_SAVE_POINT_TIME_NORMALIZE_V1: SAVE ke waqt edited/imported points ka local t 0 se normalize karo.
            val orderedPointsForSave = gesture.points
                .filter { !it.x.isNaN() && !it.x.isInfinite() && !it.y.isNaN() && !it.y.isInfinite() }
                .sortedBy { it.t.coerceAtLeast(0L) }
            val baseT = orderedPointsForSave.firstOrNull()?.t?.coerceAtLeast(0L) ?: 0L
            val cleanPoints = orderedPointsForSave
                .map { it.copy(t = (it.t.coerceAtLeast(0L) - baseT).coerceAtLeast(0L).coerceAtMost(600000L)) }

            if (cleanPoints.isEmpty()) {
                null
            } else {
                val safeDelay = gesture.delayFromStart.coerceAtLeast(lastEnd).coerceAtMost(24L * 60L * 60L * 1000L)
                lastEnd = (safeDelay + (cleanPoints.lastOrNull()?.t ?: 0L)).coerceAtLeast(safeDelay)
                gesture.copy(delayFromStart = safeDelay, points = cleanPoints)
            }
        }

    if (cleanGestures.isEmpty()) {
        unsavedGestures = emptyList()
        val hasOld = GestureStore.hasRecording(this)
        updateUIState(if (hasOld) "PLAY" else "START", false, hasOld, true)
        restorePanelUI()
        Toast.makeText(this, "Koi valid recording nahi mili", Toast.LENGTH_SHORT).show()
        return
    }

    val saved = GestureStore.save(this, cleanGestures)
    if (!saved) {
        Toast.makeText(this, "❌ Memory save fail ho gayi, dobara SAVE dabao", Toast.LENGTH_LONG).show()
        updateUIState("+ ADD", true, false, true)
        restorePanelUI()
        return
    }

    unsavedGestures = emptyList()
    updateUIState("PLAY", false, true, true)
    restorePanelUI()
    Toast.makeText(this, "✅ Poori Memory Save ho gayi!", Toast.LENGTH_SHORT).show()
    } finally {
        isSavingRecording = false
    }
}
private fun clearSavedRecordingFromPanel() {
    if (isRecording || AutoActionService.isPlaying() || unsavedGestures.isNotEmpty()) {
        Toast.makeText(this, "Abhi saaf nahi kar sakte", Toast.LENGTH_SHORT).show()
        return
    }
    GestureStore.clear(this)
    unsavedGestures = emptyList()
    glassHiddenAt = 0L
    nextNavigationGapOverride = null
    pendingDiscardConfirm = false
    updateUIState("START", false, false, true)
    restorePanelUI()
    Toast.makeText(this, "🗑️ Active config ki memory clear ho gayi!", Toast.LENGTH_SHORT).show()
}


    // AARISH_ADVANCED_BLINK_STRIKE_GHOST_ENGINE_V2
    private fun localGestureHasRealMovement(gesture: RecordedGesture): Boolean {
        val pts = gesture.points
        if (pts.size < 2) return false
        val first = pts.first()
        return pts.any { p ->
            kotlin.math.abs(p.x - first.x) > 7f || kotlin.math.abs(p.y - first.y) > 7f
        }
    }

    private fun liveReplayDurationMs(gesture: RecordedGesture): Long {
        val points = gesture.points
            .filter { !it.x.isNaN() && !it.x.isInfinite() && !it.y.isNaN() && !it.y.isInfinite() }
            .sortedBy { it.t.coerceAtLeast(0L) }

        if (points.isEmpty()) return 80L

        val start = points.first().t.coerceAtLeast(0L)
        val end = points.last().t.coerceAtLeast(start)
        return (end - start).coerceIn(55L, 600000L)
    }

    private fun isLiveReplayBlockedDuplicate(gesture: RecordedGesture): Boolean {
        val first = gesture.points.firstOrNull() ?: return true
        val now = android.os.SystemClock.uptimeMillis()

        val dx = if (lastLiveReplayX.isNaN()) 99999f else kotlin.math.abs(first.x - lastLiveReplayX)
        val dy = if (lastLiveReplayY.isNaN()) 99999f else kotlin.math.abs(first.y - lastLiveReplayY)

        if (now - lastLiveReplayAt < 24L && dx < 2f && dy < 2f) return true

        lastLiveReplayAt = now
        lastLiveReplayX = first.x
        lastLiveReplayY = first.y
        return false
    }


    // AARISH_HIDE_AFTER_UP_NO_BLINK_FINAL_V3
    // ghost=true  -> replay ke time Glass + Panel touch-pass-through; visual same.
    // ghost=false -> replay complete ke baad Glass + Panel normal touch capture.


private fun aarishResetLiveReplayStateSafe(forceSolid: Boolean = true) {
    liveReplaySerial++
    liveReplayActive = false
    liveReplayQueue.clear()
    liveReplayQueueDraining = false
    lastLiveReplayAt = 0L
    lastLiveReplayX = Float.NaN
    lastLiveReplayY = Float.NaN

    // Pending idle replay timer ko hard-cancel karo.
    liveIdleTimerRunnable?.let { handler.removeCallbacks(it) }
    liveIdleTimerRunnable = null

    // IMPORTANT:
    // Naya glass/panel aane par purana cached ghost state valid nahi hota.
    // Isko null nahi kiya to code soch sakta hai "already ghost/solid hai",
    // aur actual WindowManager flags update nahi honge.
    lastGhostState = null

    val notTouchable = android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    val notFocusable = android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

    fun makePanelTouchableSilently() {
        try {
            val panel = panelView ?: return
            val lp = (panel.layoutParams as? android.view.WindowManager.LayoutParams) ?: panelParams ?: return
            lp.flags = (lp.flags and notTouchable.inv()) or notFocusable
            panelParams = lp

            if (::windowManager.isInitialized && panel.parent != null) {
                aarishAccessWmV13().updateViewLayout(panel, lp)
            }
        } catch (_: Throwable) {
        }
    }

    fun makeGlassTouchableSilently() {
        try {
            val glass = captureView ?: return
            val lp = glass.layoutParams as? android.view.WindowManager.LayoutParams ?: return
            glass.alpha = 1f
            lp.flags = (lp.flags and notTouchable.inv()) or notFocusable

            if (::windowManager.isInitialized && glass.parent != null) {
                aarishAccessWmV13().updateViewLayout(glass, lp)
            }
        } catch (_: Throwable) {
        }
    }

    if (forceSolid) {
        // Normal restore: glass + panel dono touchable.
        aarishSetGlassGhostModeSafe(false)
    } else {
        // View remove hone wali ho tab bhi panel ko touchable zaroor restore karo,
        // warna DONE/SAVE/CUT next session me dead ho sakte hain.
        makeGlassTouchableSilently()
        makePanelTouchableSilently()
    }
}


    private fun restoreLiveReplayGlassSafe(serial: Int) {
        handler.post {
            if (serial != liveReplaySerial) return@post
            liveReplayActive = false
            if (instance !== this@FloatingControlService) return@post
            if (!isRecording) return@post
            aarishSetGlassGhostModeSafe(false)
        }
    }








    // AARISH_SYSTEM_ACTION_ZERO_BLINK_FIX_V1
    private fun triggerLiveSystemActionSafe(actionType: Int) {
        if (instance !== this@FloatingControlService) return
        if (!isRecording || AutoActionService.isPlaying()) return

        // System BACK/RECENTS real touch nahi hain, isliye glass ko ghost karne ki zaroorat nahi.
        // liveReplaySerial ko bhi increment nahi karte, warna pending 550ms touch batch cancel ho sakta hai.
        semanticClickMuteUntil = android.os.SystemClock.uptimeMillis() + 2600L

        handler.postDelayed({
            if (instance !== this@FloatingControlService || !isRecording || AutoActionService.isPlaying()) {
                return@postDelayed
            }

            AutoActionService.performLiveSystemActionSafe(actionType) {
                semanticClickMuteUntil = android.os.SystemClock.uptimeMillis() + 900L
            }
        }, 40L)
    }
// AARISH_550MS_IDLE_BATCH_ENGINE_V1

    // AARISH_550MS_IDLE_BATCH_ENGINE_V2_FIX
private fun aarishSetGlassGhostModeSafe(ghost: Boolean): Boolean {
    if (!::windowManager.isInitialized) return false

    val glass = captureView ?: return false
    val glassParams = glass.layoutParams as? android.view.WindowManager.LayoutParams ?: return false

    val panel = panelView
    val panelLp = (panel?.layoutParams as? android.view.WindowManager.LayoutParams) ?: panelParams

    val notTouchable = android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    val notFocusable = android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

    val glassAlreadyGhost = (glassParams.flags and notTouchable) != 0
    val panelAlreadyGhost = if (panel != null && panelLp != null && panel.parent != null) {
        (panelLp.flags and notTouchable) != 0
    } else {
        ghost
    }

    // Cached state tabhi trust karo jab real WindowManager flags bhi same hon.
    if (lastGhostState == ghost && glassAlreadyGhost == ghost && panelAlreadyGhost == ghost) {
        return true
    }

    return try {
        // NO-BLINK RULE:
        // Glass ko alpha 0 mat karo. Visual green glass stable rahega.
        // Replay ke time sirf FLAG_NOT_TOUCHABLE lagta hai, isliye injected gesture
        // behind-app tak pass hota hai without remove/add blink.
        glass.alpha = 1f

        glassParams.flags = if (ghost) {
            glassParams.flags or notTouchable or notFocusable
        } else {
            (glassParams.flags and notTouchable.inv()) or notFocusable
        }

        if (glass.parent != null) {
            aarishAccessWmV13().updateViewLayout(glass, glassParams)
        }

        if (panel != null && panelLp != null) {
            panelLp.flags = if (ghost) {
                panelLp.flags or notTouchable or notFocusable
            } else {
                (panelLp.flags and notTouchable.inv()) or notFocusable
            }

            panelParams = panelLp

            if (panel.parent != null) {
                aarishAccessWmV13().updateViewLayout(panel, panelLp)
            }
        }

        lastGhostState = ghost
        true
    } catch (_: Throwable) {
        lastGhostState = null
        false
    }
}

private fun drainNextLiveReplaySafe() {
    handler.post {
        if (instance !== this@FloatingControlService || !isRecording || AutoActionService.isPlaying()) {
            aarishResetLiveReplayStateSafe()
            return@post
        }
        if (liveReplayQueue.isEmpty()) {
            liveReplayQueueDraining = false
            liveReplayActive = false
            aarishSetGlassGhostModeSafe(false)
            return@post
        }

        val serial = liveReplaySerial
        liveReplayQueueDraining = true
        liveReplayActive = true
        semanticClickMuteUntil = android.os.SystemClock.uptimeMillis() + 2200L

        if (!aarishSetGlassGhostModeSafe(true)) {
            aarishResetLiveReplayStateSafe(forceSolid = false)
            return@post
        }

        fun restoreAfterQueueDone() {
            if (serial != liveReplaySerial || instance !== this@FloatingControlService) return
            liveReplayQueueDraining = false
            liveReplayActive = false
            handler.postDelayed({
                if (serial == liveReplaySerial &&
                    instance === this@FloatingControlService &&
                    isRecording &&
                    !AutoActionService.isPlaying() &&
                    !liveReplayActive &&
                    !liveReplayQueueDraining
                ) {
                    aarishSetGlassGhostModeSafe(false)
                }
            }, 80L)
        }

        fun fireNextQueuedGesture() {
            if (serial != liveReplaySerial || instance !== this@FloatingControlService ||
                !isRecording || AutoActionService.isPlaying()) {
                aarishResetLiveReplayStateSafe()
                return
            }
            val gesture = liveReplayQueue.pollFirst()
            if (gesture == null) {
                restoreAfterQueueDone()
                return
            }
            val duration = liveReplayDurationMs(gesture)
            val watchdogMs = (duration + 4500L).coerceAtMost(610000L)
            var finished = false

            fun finishAndContinue() {
                if (finished) return
                finished = true
                if (serial != liveReplaySerial || instance !== this@FloatingControlService ||
                    !isRecording || AutoActionService.isPlaying()) {
                    aarishResetLiveReplayStateSafe()
                    return
                }
                if (liveReplayQueue.isNotEmpty()) {
                    handler.postDelayed({ fireNextQueuedGesture() }, 55L)
                } else {
                    restoreAfterQueueDone()
                }
            }

            semanticClickMuteUntil = android.os.SystemClock.uptimeMillis() + duration + 1700L
            AutoActionService.playSingleLiveGestureSafe(gesture) {
                handler.postDelayed({ finishAndContinue() }, 70L)
            }

            handler.postDelayed({
                if (serial == liveReplaySerial && liveReplayActive && !finished) {
                    finishAndContinue()
                }
            }, watchdogMs)
        }

        // Give WindowManager time to apply ghost flags before firing
        handler.postDelayed({ fireNextQueuedGesture() }, 120L)
    }
}

fun triggerLiveReplaySafe(gesture: RecordedGesture) {
    if (instance !== this@FloatingControlService) return
    if (!isRecording || AutoActionService.isPlaying()) return
    if (gesture.points.isEmpty()) return

    val firstX = gesture.points.firstOrNull()?.x ?: return
    if (firstX <= -50f) {
        val actionType = when (firstX.toInt()) {
            -100 -> 1
            -200 -> 2
            else -> 0
        }
        if (actionType != 0) triggerLiveSystemActionSafe(actionType)
        return
    }

    if (isLiveReplayBlockedDuplicate(gesture)) return

    handler.post {
        if (instance !== this@FloatingControlService || !isRecording || AutoActionService.isPlaying()) return@post

        // Queue overflow par app ko freeze nahi hone dena.
        if (liveReplayQueue.size >= 64) {
            while (liveReplayQueue.size > 32) {
                liveReplayQueue.pollFirst()
            }

            val now = android.os.SystemClock.uptimeMillis()
            if (now - liveReplayFloodToastAt > 2500L) {
                liveReplayFloodToastAt = now
                Toast.makeText(this, "Too many live taps, old pending taps trimmed", Toast.LENGTH_SHORT).show()
            }
        }

        liveReplayQueue.addLast(gesture)

        // Agar replay already chal raha hai to same drain baaki queue ko bhi fire karega.
        if (liveReplayActive || liveReplayQueueDraining) return@post

        // Idle wait ke dauran glass SOLID/touchable rahe.
        // Purani ghost state bachi ho to turant normal karo.
        if (lastGhostState == true) {
            aarishSetGlassGhostModeSafe(false)
        }

        // Fast double-tap / triple-tap miss na ho:
        // har new tap purana idle timer cancel karega.
        liveIdleTimerRunnable?.let { handler.removeCallbacks(it) }
        liveIdleTimerRunnable = null

        liveReplaySerial++
        val serial = liveReplaySerial

        val idleTask = Runnable {
            liveIdleTimerRunnable = null

            if (serial != liveReplaySerial ||
                instance !== this@FloatingControlService ||
                !isRecording ||
                AutoActionService.isPlaying()
            ) {
                return@Runnable
            }

            if (liveReplayQueue.isNotEmpty() && !liveReplayActive && !liveReplayQueueDraining) {
                drainNextLiveReplaySafe()
            }
        }

        liveIdleTimerRunnable = idleTask
        handler.postDelayed(idleTask, 550L)
    }
}


}

// BUG #1 FIX: ACTION_DOWN aur ACTION_UP hamesha save honge — long press skip nahi hoga



class TouchCaptureView(private val owner: FloatingControlService) : android.view.View(owner) {

    // AARISH_GESTURE_UNIVERSAL_CAPTURE_V2_START
    companion object {
        private const val MAX_GESTURE_DURATION_MS = 600000L
        private const val MAX_POINTS_PER_GESTURE = 900
    }
    // AARISH_GESTURE_UNIVERSAL_CAPTURE_V2_END
    private val recordedGestures = mutableListOf<RecordedGesture>()
    private val currentPoints = mutableListOf<GesturePoint>()
    
    private val recordingStartTime = android.os.SystemClock.uptimeMillis()
    private var currentGestureDownTime = 0L
    private var currentSnapshot: TargetSnapshot? = null
    private var currentGestureForceXyOnly = false // AARISH_FORCE_XY_ONLY_CAPTURE_V1
    private var currentMemoryCopyCode = 0 // AARISH_MEMORY_SLOTS_V1_TOUCH_STATE
    private var multiTouchCanceled = false
    // AARISH_OCR_TEXT_CLICK_V4_FIELDS
    private val ocrSaveHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var ocrSavePending = false
    private var ocrSaveSerial = 0

    // AARISH_OCR_NON_BLOCKING_RECORD_V5_FIELDS
    private var ocrActiveSerial = 0
    private var ocrPrefetchSnapshot: TargetSnapshot? = null
    private var ocrPrefetchDownTime = 0L
    private var ocrPendingGestureIndex = -1
    private var ocrPendingGestureSerial = 0

    // AARISH_ENGINE_TRACE_TEXT_TOAST_V25B
    private fun aarishTraceTextToast(msg: String) {
        android.widget.Toast.makeText(owner, msg.take(64), android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun aarishTraceCleanText(raw: String?): String {
        return raw.orEmpty()
            .removePrefix("OCR:")
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(24)
    }

    private fun aarishTraceIsOcr(g: RecordedGesture): Boolean {
        return g.targetText.orEmpty().startsWith("OCR:") ||
            g.targetId.orEmpty().startsWith("ocr:") ||
            g.targetContextText.orEmpty().contains("OCR_TEXT_CLICK")
    }

    private fun aarishTraceOcrLabel(raw: String?): String {
        val t = aarishTraceCleanText(raw)
        return if (t.isNotBlank()) "OCR: $t" else "OCR recorded"
    }

    private fun aarishTraceRecordLabel(g: RecordedGesture): String {
        if (aarishTraceIsOcr(g)) {
            val t = aarishTraceCleanText(g.targetChildText ?: g.targetText)
            return if (t.isNotBlank()) "OCR: $t" else "OCR recorded"
        }

        val hasMagnetIdentity =
            !g.targetText.isNullOrBlank() ||
            !g.targetDesc.isNullOrBlank() ||
            !g.targetId.isNullOrBlank() ||
            !g.targetContextText.isNullOrBlank()

        return if (hasMagnetIdentity) "Magnet recorded" else "XY recorded"
    }

    init {
        isClickable = true
        setBackgroundColor(android.graphics.Color.argb(26, 0, 200, 0)) // 🟢 Green Glass
    }
    
    // ✅ NEW FIX: For exact Gap calculation
    fun getRecordingStartTime(): Long {
        return recordingStartTime
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        // Multi-touch/pinch/two-finger accidental input ko record mat karo.
        // ACTION_POINTER_DOWN ke baad final ACTION_UP ko bhi block karo,
        // warna ek ghost tap save ho sakta hai.
        if (event.pointerCount > 1 ||
            event.actionMasked == android.view.MotionEvent.ACTION_POINTER_DOWN ||
            event.actionMasked == android.view.MotionEvent.ACTION_POINTER_UP
        ) {
            currentPoints.clear()
            currentSnapshot = null
            multiTouchCanceled = true
            return true
        }

        if (multiTouchCanceled) {
            if (event.actionMasked == android.view.MotionEvent.ACTION_UP ||
                event.actionMasked == android.view.MotionEvent.ACTION_CANCEL
            ) {
                multiTouchCanceled = false
                currentGestureDownTime = 0L
            }
            currentPoints.clear()
            currentSnapshot = null
            return true
        }

        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                currentPoints.clear()
                currentGestureDownTime = event.eventTime
                currentGestureForceXyOnly = owner.consumeNextXyOnlyRecord()
                currentMemoryCopyCode = owner.consumeMemoryCopyArmCode()
                if (currentMemoryCopyCode != 0) currentGestureForceXyOnly = false
                addPoint(event, forceAdd = true)

                // Button/symbol ka snapshot DOWN par lo, screen change hone se pehle
                currentSnapshot = if (currentGestureForceXyOnly) null else captureSnapshotFor(event.rawX.toInt(), event.rawY.toInt())

                // AARISH_OCR_NON_BLOCKING_RECORD_V5_DOWN_PREFETCH
                // OCR ko pehle hi start karo, ACTION_UP par wait/block nahi karna.
                if (!currentGestureForceXyOnly && currentMemoryCopyCode == 0) startOcrPrefetchForCurrentTap(event.rawX.toInt(), event.rawY.toInt())
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                addPoint(event, forceAdd = false)
            }
            android.view.MotionEvent.ACTION_UP -> {
                addPoint(event, forceAdd = true)
                if (currentMemoryCopyCode != 0) {
                    saveMemoryCopyGestureV1()
                    return true
                }
                // AARISH_OCR_TEXT_CLICK_V4_ACTION_UP
                if (currentGestureForceXyOnly) { saveCurrentGesture() } else if (!trySaveCurrentGestureWithOcr(event.rawX.toInt(), event.rawY.toInt())) {
                    saveCurrentGesture()
                }
            }
            android.view.MotionEvent.ACTION_CANCEL -> {
                currentPoints.clear()
                currentSnapshot = null
                currentGestureDownTime = 0L
                multiTouchCanceled = false
            }
        }
        return true
    }


private fun addPoint(event: android.view.MotionEvent, forceAdd: Boolean = false) {
    val downTime = currentGestureDownTime.takeIf { it > 0L } ?: event.eventTime
    val relativeTime = (event.eventTime - downTime).coerceAtLeast(0L).coerceAtMost(MAX_GESTURE_DURATION_MS)
    val x = event.rawX
    val y = event.rawY

    if (currentPoints.isNotEmpty()) {
        val last = currentPoints.last()
        val samePlace = kotlin.math.abs(last.x - x) < 1.4f && kotlin.math.abs(last.y - y) < 1.4f
        val sameTime = relativeTime == last.t
        val tooFast = relativeTime - last.t < 8L

        if (!forceAdd && samePlace && tooFast) return
        if (forceAdd && samePlace && sameTime && currentPoints.size > 1) return
    }

    if (!forceAdd && currentPoints.size >= MAX_POINTS_PER_GESTURE) {
        val last = currentPoints.lastOrNull()
        if (last != null && relativeTime - last.t < 700L) return
    }

    currentPoints.add(GesturePoint(x = x, y = y, t = relativeTime))
}

private fun normalizePointsForSave(points: List<GesturePoint>): List<GesturePoint> {
    val ordered = points
        .filter { !it.x.isNaN() && !it.x.isInfinite() && !it.y.isNaN() && !it.y.isInfinite() }
        .sortedBy { it.t.coerceAtLeast(0L) }

    if (ordered.isEmpty()) return emptyList()

    val baseT = ordered.first().t.coerceAtLeast(0L)
    val normalized = ordered.map { point ->
        point.copy(
            t = (point.t.coerceAtLeast(0L) - baseT).coerceAtLeast(0L).coerceAtMost(MAX_GESTURE_DURATION_MS)
        )
    }

    if (normalized.size <= MAX_POINTS_PER_GESTURE) return normalized

    val result = mutableListOf<GesturePoint>()
    val lastIndex = normalized.lastIndex
    var lastPicked = -1

    for (i in 0 until MAX_POINTS_PER_GESTURE) {
        val idx = ((i.toLong() * lastIndex.toLong()) / (MAX_POINTS_PER_GESTURE - 1).toLong()).toInt().coerceIn(0, lastIndex)
        if (idx != lastPicked) {
            result.add(normalized[idx])
            lastPicked = idx
        }
    }

    if (lastPicked != lastIndex) {
        result.add(normalized.last())
    }

    return result
}


    private fun captureSnapshotFor(x: Int, y: Int): TargetSnapshot? {
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = metrics.heightPixels.toFloat().coerceAtLeast(1f)

        return AutoActionService.captureTargetSnapshot(
            x,
            y,
            screenW,
            screenH
        )
    }



    private fun snapshotHasUsefulIdentity(snapshot: TargetSnapshot?): Boolean {
        // AARISH_OCR_ALWAYS_PRIORITY_RECORD_V28
        // Is function ko ab OCR gate/blocker ke liye use nahi karna.
        // Sirf real text/desc ko useful identity maan sakta hai; resource-id/child/context OCR ko block nahi karenge.
        val s = snapshot ?: return false
        val bad = setOf("", "view", "text", "button", "image", "layout", "android", "widget", "item")

        fun norm(value: String?): String {
            return value.orEmpty()
                .trim()
                .lowercase(java.util.Locale.US)
                .substringAfterLast("/")
                .substringAfterLast(":")
                .replace("_", " ")
                .replace("-", " ")
                .replace(Regex("[^a-z0-9\\u0600-\\u06FF\\u0750-\\u077F\\u0900-\\u097F]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        val text = norm(s.targetText)
        val desc = norm(s.targetDesc)

        return listOf(text, desc).any { it.length >= 2 && it !in bad }
    }


    private fun gestureHasUsefulIdentityForOcrV5(gesture: RecordedGesture): Boolean {
        // AARISH_OCR_ALWAYS_PRIORITY_RECORD_V28
        // Normal accessibility/magnetic identity OCR ko block nahi karegi.
        // Sirf already-OCR gesture ko OCR identity maana jayega.
        return gesture.targetText.orEmpty().startsWith("OCR:") ||
            gesture.targetId.orEmpty().startsWith("ocr:") ||
            gesture.targetContextText.orEmpty().contains("OCR_TEXT_CLICK")
    }

    private fun applyOcrSnapshotToGestureV5(
        gesture: RecordedGesture,
        snapshot: TargetSnapshot
    ): RecordedGesture {
        // AARISH_CORE_ONLY_OCR_MERGE
        // Magnetic identity safe, OCR snapshot merge only. Visual/OpenCV storage removed.

        fun realId(v: String?): Boolean = !v.isNullOrBlank() && !v.startsWith("ocr:")
        fun realDesc(v: String?): Boolean = !v.isNullOrBlank() && v != "OCR_TEXT_TARGET"
        fun realClass(v: String?): Boolean = !v.isNullOrBlank() && v != "OCR_TEXT"
        fun realTree(v: String?): Boolean = !v.isNullOrBlank() && v != "OCR"
        fun realRole(v: String?): Boolean = !v.isNullOrBlank() && v != "OCR_VISIBLE_TEXT"
        fun realSibling(v: String?): Boolean = !v.isNullOrBlank() && !v.startsWith("ocr_bounds=")
        fun clean(v: String?): String? = v?.takeIf { it.isNotBlank() }

        val snapContext = snapshot.targetContextText.orEmpty()
        val magneticContext = gesture.targetContextText.orEmpty()
        val mergedContext = buildString {
            if (snapContext.isNotBlank()) append(snapContext)
            if (magneticContext.isNotBlank() && !magneticContext.contains("OCR_TEXT_CLICK")) {
                if (isNotEmpty()) append("|MAGNETIC:")
                append(magneticContext)
            }
        }.ifBlank { clean(snapshot.targetContextText) ?: gesture.targetContextText }

        return gesture.copy(
            targetText = clean(snapshot.targetText) ?: gesture.targetText,
            targetContextText = mergedContext,
            targetChildText = clean(snapshot.targetChildText) ?: gesture.targetChildText,

            targetDesc = if (realDesc(gesture.targetDesc)) gesture.targetDesc else snapshot.targetDesc,
            targetId = if (realId(gesture.targetId)) gesture.targetId else snapshot.targetId,
            targetClass = if (realClass(gesture.targetClass)) gesture.targetClass else snapshot.targetClass,
            targetPackage = clean(gesture.targetPackage) ?: snapshot.targetPackage,
            targetSiblingText = if (realSibling(gesture.targetSiblingText)) gesture.targetSiblingText else snapshot.targetSiblingText,
            targetRoleFlags = if (realRole(gesture.targetRoleFlags)) gesture.targetRoleFlags else snapshot.targetRoleFlags,
            targetTreePath = if (realTree(gesture.targetTreePath)) gesture.targetTreePath else snapshot.targetTreePath,

            targetLeft = snapshot.targetLeft.takeIf { it >= 0 } ?: gesture.targetLeft,
            targetTop = snapshot.targetTop.takeIf { it >= 0 } ?: gesture.targetTop,
            targetRight = snapshot.targetRight.takeIf { it >= 0 } ?: gesture.targetRight,
            targetBottom = snapshot.targetBottom.takeIf { it >= 0 } ?: gesture.targetBottom,
            xPercent = snapshot.xPercent.takeIf { it > 0f } ?: gesture.xPercent,
            yPercent = snapshot.yPercent.takeIf { it > 0f } ?: gesture.yPercent,
            targetWPercent = snapshot.targetWPercent.takeIf { it > 0f } ?: gesture.targetWPercent,
            targetHPercent = snapshot.targetHPercent.takeIf { it > 0f } ?: gesture.targetHPercent,
            insideXPercent = snapshot.insideXPercent.takeIf { it in 0f..1f } ?: gesture.insideXPercent,
            insideYPercent = snapshot.insideYPercent.takeIf { it in 0f..1f } ?: gesture.insideYPercent,
            recordedScreenW = snapshot.recordedScreenW.takeIf { it > 0 } ?: gesture.recordedScreenW,
            recordedScreenH = snapshot.recordedScreenH.takeIf { it > 0 } ?: gesture.recordedScreenH
        )
    }



    private fun clearOcrPendingGestureV5(serial: Int) {
        if (ocrPendingGestureSerial == serial) {
            ocrPendingGestureIndex = -1
            ocrPendingGestureSerial = 0
        }
    }

    private fun startOcrPrefetchForCurrentTap(x: Int, y: Int) {
        // AARISH_OCR_ALWAYS_PRIORITY_RECORD_V28
        // OCR hamesha start hoga. Accessibility/magnetic identity milne par bhi OCR block nahi hoga.
        // Agar OCR ko tapped word/key mila, woh saved gesture ko overwrite karega.
        // Agar OCR fail/null hua, purana magnetic/XY fallback safe rahega.
        if (currentPoints.isEmpty()) return
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return

        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = metrics.heightPixels.toFloat().coerceAtLeast(1f)

        ocrSaveSerial++
        val serial = ocrSaveSerial
        ocrActiveSerial = serial
        ocrSavePending = true
        ocrPrefetchSnapshot = null
        ocrPrefetchDownTime = currentGestureDownTime
        ocrPendingGestureIndex = -1
        ocrPendingGestureSerial = 0

        val started = AutoActionService.captureOcrTextSnapshot(x, y, screenW, screenH) { ocrSnapshot ->
            ocrSaveHandler.post {
                val isActiveTap = serial == ocrActiveSerial
                val isPendingSavedGesture = serial == ocrPendingGestureSerial && ocrPendingGestureIndex >= 0

                if (!isActiveTap && !isPendingSavedGesture) return@post

                if (isActiveTap) {
                    ocrSavePending = false
                    if (ocrSnapshot != null) {
                        ocrPrefetchSnapshot = ocrSnapshot
                    }
                }

                if (isPendingSavedGesture) {
                    if (ocrSnapshot != null) {
                        val idx = ocrPendingGestureIndex
                        val old = recordedGestures.getOrNull(idx)
                        if (old != null) {
                            recordedGestures[idx] = applyOcrSnapshotToGestureV5(old, ocrSnapshot)
                            aarishTraceTextToast(aarishTraceOcrLabel(ocrSnapshot.targetText))
                        }
                    }
                    clearOcrPendingGestureV5(serial)
                }
            }
        }

        if (!started) {
            ocrSavePending = false
            ocrPrefetchSnapshot = null
            ocrActiveSerial = 0
            return
        }

        ocrSaveHandler.postDelayed({
            // AARISH_OCR_PENDING_TIMEOUT_SAFE_V31
            // AutoActionService OCR recording timeout 8000ms hai,
            // isliye pending gesture ko 5000ms par clear mat karo.
            if (serial == ocrActiveSerial) {
                ocrSavePending = false
            }
            if (serial == ocrPendingGestureSerial) {
                clearOcrPendingGestureV5(serial)
            }
        }, 9000L)
    }

    private fun applyReadyOcrSnapshotIfAnyV5(gesture: RecordedGesture): RecordedGesture {
        // AARISH_OCR_ALWAYS_PRIORITY_RECORD_V28
        // Agar same tap ka OCR snapshot ready hai, to hamesha OCR ko priority do.
        val snap = ocrPrefetchSnapshot ?: return gesture
        if (ocrPrefetchDownTime <= 0L || ocrPrefetchDownTime != currentGestureDownTime) return gesture

        ocrPrefetchSnapshot = null
        ocrSavePending = false

        aarishTraceTextToast(aarishTraceOcrLabel(snap.targetText))
        return applyOcrSnapshotToGestureV5(gesture, snap)
    }

    private fun rememberPendingOcrForLastSavedGestureV5(savedGesture: RecordedGesture) {
        // AARISH_OCR_ALWAYS_PRIORITY_RECORD_V28
        // Gesture save ho gaya aur OCR abhi in-flight hai to baad me OCR result attach/overwrite hoga.
        // OCR null/fail hua to saved magnetic/XY gesture same rahega.
        if (!ocrSavePending || ocrActiveSerial <= 0) return
        if (recordedGestures.isEmpty()) return

        ocrPendingGestureIndex = recordedGestures.lastIndex
        ocrPendingGestureSerial = ocrActiveSerial
    }


    private fun trySaveCurrentGestureWithOcr(x: Int, y: Int): Boolean {
        // AARISH_CORE_ONLY_OCR_NO_MAGNET_OVERWRITE
        // OCR ready ho bhi jaye to currentSnapshot replace nahi karna.
        // Pehle magnetic gesture banega, phir applyReadyOcrSnapshotIfAnyV5() OCR merge karega.
        if (currentPoints.isEmpty()) return false

        val snap = ocrPrefetchSnapshot
        if (snap != null && ocrPrefetchDownTime == currentGestureDownTime) {
            ocrSavePending = false
            // currentSnapshot = snap intentionally removed.
            // ocrPrefetchSnapshot ko applyReadyOcrSnapshotIfAnyV5 consume karega.
        }

        return false
    }




    private fun saveCurrentGesture() {
        if (currentPoints.isEmpty()) return

        val cleanPoints = normalizePointsForSave(currentPoints)
        if (cleanPoints.isEmpty()) {
            currentPoints.clear()
            currentSnapshot = null
            currentGestureDownTime = 0L
            currentGestureForceXyOnly = false
            multiTouchCanceled = false
            return
        }

        val rawDelay = (currentGestureDownTime - recordingStartTime).coerceAtLeast(0L)
        val previousEnd = recordedGestures.maxOfOrNull { gesture ->
            gesture.delayFromStart.coerceAtLeast(0L) +
                (gesture.points.maxOfOrNull { it.t.coerceAtLeast(0L) } ?: 0L)
        } ?: 0L

        val delayFromStart = if (recordedGestures.isEmpty()) {
            rawDelay
        } else {
            kotlin.math.max(rawDelay, previousEnd + 35L)
        }.coerceAtMost(24L * 60L * 60L * 1000L)

        val firstP = cleanPoints.first()

        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = metrics.heightPixels.toFloat().coerceAtLeast(1f)

        val forceXyOnly = currentGestureForceXyOnly // AARISH_FORCE_XY_ONLY_SAVE_V1

        val snapshot = if (forceXyOnly) {
            null
        } else {
            currentSnapshot ?: captureSnapshotFor(
                firstP.x.toInt(),
                firstP.y.toInt()
            )
        }

        val newGesture = RecordedGesture(
            delayFromStart = delayFromStart,
            points = cleanPoints,
            targetText = if (forceXyOnly) null else snapshot?.targetText,
            targetDesc = if (forceXyOnly) null else snapshot?.targetDesc,
            targetId = if (forceXyOnly) null else snapshot?.targetId,
            targetClass = if (forceXyOnly) null else snapshot?.targetClass,
            targetPackage = if (forceXyOnly) null else snapshot?.targetPackage,
            targetContextText = if (forceXyOnly) null else snapshot?.targetContextText,
            targetChildText = if (forceXyOnly) null else snapshot?.targetChildText,
            targetSiblingText = if (forceXyOnly) null else snapshot?.targetSiblingText,
            targetRoleFlags = if (forceXyOnly) "AARISH_FORCE_XY_ONLY" else snapshot?.targetRoleFlags,
            targetTreePath = if (forceXyOnly) null else snapshot?.targetTreePath,
            targetLeft = snapshot?.targetLeft ?: -1,
            targetTop = snapshot?.targetTop ?: -1,
            targetRight = snapshot?.targetRight ?: -1,
            targetBottom = snapshot?.targetBottom ?: -1,
            xPercent = snapshot?.xPercent ?: (firstP.x / screenW).coerceIn(0f, 1f),
            yPercent = snapshot?.yPercent ?: (firstP.y / screenH).coerceIn(0f, 1f),
            targetWPercent = snapshot?.targetWPercent ?: 0f,
            targetHPercent = snapshot?.targetHPercent ?: 0f,
            insideXPercent = snapshot?.insideXPercent ?: 0.5f,
            insideYPercent = snapshot?.insideYPercent ?: 0.5f,
            recordedScreenW = snapshot?.recordedScreenW ?: metrics.widthPixels,
            recordedScreenH = snapshot?.recordedScreenH ?: metrics.heightPixels
        )

        // AARISH_OCR_NON_BLOCKING_RECORD_V5_FINAL_GESTURE
        val finalGesture = if (forceXyOnly) newGesture else applyReadyOcrSnapshotIfAnyV5(newGesture)
        if (forceXyOnly) {
            aarishTraceTextToast("XY recorded")
        } else if (!aarishTraceIsOcr(finalGesture)) {
            aarishTraceTextToast(aarishTraceRecordLabel(finalGesture)) // AARISH_ENGINE_TRACE_TEXT_TOAST_V25B
        }
        recordedGestures.add(finalGesture)
        if (!forceXyOnly) rememberPendingOcrForLastSavedGestureV5(finalGesture)

        // Tap, double tap, swipe, long press sab raw gesture ke form me live replay hoga.
        owner.triggerLiveReplaySafe(finalGesture)

        currentPoints.clear()
        currentSnapshot = null
        currentGestureDownTime = 0L
        currentGestureForceXyOnly = false
        currentMemoryCopyCode = 0
        multiTouchCanceled = false
    }





// AARISH_PREMIUM_DESKTOP_APP_RECORD_STEP_V4_FINAL
fun addAppLaunchGesture(
    appLabel: String,
    packageNameToLaunch: String,
    activityName: String,
    mode: String
): Boolean {
    currentPoints.clear()
    currentSnapshot = null
    currentGestureDownTime = 0L
    multiTouchCanceled = false

    val safeLabel = appLabel.trim().ifBlank { packageNameToLaunch.trim() }
    val safePkg = packageNameToLaunch.trim()
    val safeActivity = activityName.trim()
    val safeMode = if (mode.uppercase(java.util.Locale.US).contains("FLOAT")) "FLOATING" else "FULLSCREEN"

    if (safePkg.isBlank() || safeActivity.isBlank()) return false

    val now = android.os.SystemClock.uptimeMillis()
    val rawDelay = (now - recordingStartTime).coerceAtLeast(0L)
    val previousEnd = recordedGestures.maxOfOrNull { gesture ->
        gesture.delayFromStart.coerceAtLeast(0L) +
            (gesture.points.maxOfOrNull { it.t.coerceAtLeast(0L) } ?: 0L)
    } ?: 0L

    val delay = kotlin.math.max(rawDelay, previousEnd + 180L)

    recordedGestures.add(
        RecordedGesture(
            delayFromStart = delay,
            points = listOf(
                GesturePoint(-300f, -300f, 0L),
                GesturePoint(-300f, -300f, 120L)
            ),
            targetText = "APP_LAUNCH_$safeMode",
            targetDesc = safeLabel,
            targetId = "app:$safePkg/$safeActivity",
            targetPackage = safePkg,
            targetContextText = "Launch app: $safeLabel | mode=$safeMode"
        )
    )

    return true
}


fun addWaitAiGesture(): Boolean {
    // AARISH_AI_WAIT_BUTTON_V1_CAPTURE
    currentPoints.clear()
    currentSnapshot = null
    currentGestureDownTime = 0L
    multiTouchCanceled = false

    val now = android.os.SystemClock.uptimeMillis()
    val rawDelay = (now - recordingStartTime).coerceAtLeast(0L)
    val previousEnd = recordedGestures.maxOfOrNull { gesture ->
        gesture.delayFromStart.coerceAtLeast(0L) +
            (gesture.points.maxOfOrNull { it.t.coerceAtLeast(0L) } ?: 0L)
    } ?: 0L

    val delay = kotlin.math.max(rawDelay, previousEnd + 220L)
        .coerceAtMost(24L * 60L * 60L * 1000L)

    recordedGestures.add(
        RecordedGesture(
            delayFromStart = delay,
            points = listOf(
                GesturePoint(-400f, -400f, 0L),
                GesturePoint(-400f, -400f, 90L)
            ),
            targetText = "WAIT_NEXT_TARGET_AI_GATE",
            targetDesc = "AI Wait: waits for next recorded target up to 20 minutes",
            targetId = "ai_wait:next_recorded_target",
            targetContextText = "AI Wait: wait until next recorded target appears, then continue"
        )
    )

    return true
}

    private fun nextVirtualDelayFromStartV1(): Long {
        val now = android.os.SystemClock.uptimeMillis()
        val rawDelay = (now - recordingStartTime).coerceAtLeast(0L)
        val previousEnd = recordedGestures.maxOfOrNull { gesture ->
            gesture.delayFromStart.coerceAtLeast(0L) +
                (gesture.points.maxOfOrNull { it.t.coerceAtLeast(0L) } ?: 0L)
        } ?: 0L

        return if (recordedGestures.isEmpty()) {
            rawDelay
        } else {
            kotlin.math.max(rawDelay, previousEnd + 45L)
        }.coerceAtMost(24L * 60L * 60L * 1000L)
    }

    private fun saveMemoryCopyGestureV1() {
        // AARISH_MEMORY_SLOTS_V1_TOUCH_FUNCS
        if (currentPoints.isEmpty()) {
            currentMemoryCopyCode = 0
            return
        }

        val code = currentMemoryCopyCode
        val permanent = code >= 100
        val slot = (if (permanent) code - 100 else code).coerceIn(1, 3)
        val firstP = currentPoints.first()
        val metrics = resources.displayMetrics
        val snapshot = currentSnapshot ?: captureSnapshotFor(firstP.x.toInt(), firstP.y.toInt())

        val saved = AutoActionService.aarishMemorySaveSnapshotV1(
            context = owner,
            slot = slot,
            permanent = permanent,
            snapshot = snapshot
        )

        if (saved && !permanent) {
            recordedGestures.add(
                RecordedGesture(
                    delayFromStart = nextVirtualDelayFromStartV1(),
                    points = listOf(
                        GesturePoint(x = (-5100 - slot).toFloat(), y = -5100f, t = 0L),
                        GesturePoint(x = (-5100 - slot).toFloat(), y = -5100f, t = 80L)
                    ),
                    targetText = snapshot?.targetText,
                    targetDesc = snapshot?.targetDesc,
                    targetId = snapshot?.targetId,
                    targetClass = snapshot?.targetClass,
                    targetPackage = snapshot?.targetPackage,
                    targetContextText = snapshot?.targetContextText,
                    targetChildText = snapshot?.targetChildText,
                    targetSiblingText = snapshot?.targetSiblingText,
                    targetRoleFlags = listOfNotNull(snapshot?.targetRoleFlags, "AARISH_MEMORY_COPY_TEMP_SLOT_$slot").joinToString("|"),
                    targetTreePath = snapshot?.targetTreePath,
                    targetLeft = snapshot?.targetLeft ?: -1,
                    targetTop = snapshot?.targetTop ?: -1,
                    targetRight = snapshot?.targetRight ?: -1,
                    targetBottom = snapshot?.targetBottom ?: -1,
                    xPercent = snapshot?.xPercent ?: (firstP.x / metrics.widthPixels.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f),
                    yPercent = snapshot?.yPercent ?: (firstP.y / metrics.heightPixels.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f),
                    targetWPercent = snapshot?.targetWPercent ?: 0f,
                    targetHPercent = snapshot?.targetHPercent ?: 0f,
                    insideXPercent = snapshot?.insideXPercent ?: 0.5f,
                    insideYPercent = snapshot?.insideYPercent ?: 0.5f,
                    recordedScreenW = snapshot?.recordedScreenW ?: metrics.widthPixels,
                    recordedScreenH = snapshot?.recordedScreenH ?: metrics.heightPixels
                )
            )
            aarishTraceTextToast("📥 M$slot copy recorded")
        } else if (saved) {
            aarishTraceTextToast("📌 permanent saved")
        }

        currentPoints.clear()
        currentSnapshot = null
        currentGestureDownTime = 0L
        currentGestureForceXyOnly = false
        currentMemoryCopyCode = 0
        multiTouchCanceled = false
    }

    fun addMemoryPasteGesture(slotRaw: Int, permanent: Boolean): Boolean {
        // AARISH_MEMORY_CLIPBOARD_TWOSTEP_V6_PASTE_RECORD
        currentPoints.clear()
        currentSnapshot = null
        currentGestureDownTime = 0L
        currentGestureForceXyOnly = false
        currentMemoryCopyCode = 0
        multiTouchCanceled = false

        val slot = slotRaw.coerceIn(1, 6)
        val codeX = if (permanent) -5401f else (-6200 - slot).toFloat()
        val flags = if (permanent) "AARISH_MEMORY_PASTE_PERM_SLOT_$slot" else "AARISH_MEMORY_PASTE_TEMP_SLOT_$slot"

        recordedGestures.add(
            RecordedGesture(
                delayFromStart = nextVirtualDelayFromStartV1(),
                points = listOf(
                    GesturePoint(x = codeX, y = -6200f, t = 0L),
                    GesturePoint(x = codeX, y = -6200f, t = 80L)
                ),
                targetText = if (permanent) "MEMORY_PERMANENT_$slot" else "MEMORY_TEMP_$slot",
                targetDesc = if (permanent) "Paste permanent memory slot $slot" else "Paste temporary memory slot $slot",
                targetId = null,
                targetClass = "AARISH_MEMORY_SLOT",
                targetPackage = owner.packageName,
                targetContextText = flags,
                targetChildText = null,
                targetSiblingText = null,
                targetRoleFlags = flags,
                targetTreePath = null,
                targetLeft = -1,
                targetTop = -1,
                targetRight = -1,
                targetBottom = -1,
                xPercent = 0f,
                yPercent = 0f,
                targetWPercent = 0f,
                targetHPercent = 0f,
                insideXPercent = 0.5f,
                insideYPercent = 0.5f,
                recordedScreenW = resources.displayMetrics.widthPixels,
                recordedScreenH = resources.displayMetrics.heightPixels
            )
        )

        aarishTraceTextToast(if (permanent) "📌 paste recorded" else "📤 M$slot paste recorded")
        return true
    }

    fun addMemoryClipboardPrepareGestureV6(slotRaw: Int): Boolean {
        // AARISH_MEMORY_CLIPBOARD_TWOSTEP_V6_PREP_RECORD
        currentPoints.clear()
        currentSnapshot = null
        currentGestureDownTime = 0L
        currentGestureForceXyOnly = false
        currentMemoryCopyCode = 0
        multiTouchCanceled = false

        val slot = slotRaw.coerceIn(1, 6)
        val codeX = (-6000 - slot).toFloat()

        recordedGestures.add(
            RecordedGesture(
                delayFromStart = nextVirtualDelayFromStartV1(),
                points = listOf(
                    GesturePoint(x = codeX, y = -6000f, t = 0L),
                    GesturePoint(x = codeX, y = -6000f, t = 50L)
                ),
                targetText = "MEMORY_CLIPBOARD_PREP_$slot",
                targetDesc = "Prepare clipboard for memory slot $slot",
                targetId = null,
                targetClass = "AARISH_MEMORY_SLOT",
                targetPackage = owner.packageName,
                targetContextText = "AARISH_MEMORY_CLIPBOARD_PREP_SLOT_$slot",
                targetChildText = null,
                targetSiblingText = null,
                targetRoleFlags = "AARISH_MEMORY_CLIPBOARD_PREP_SLOT_$slot",
                targetTreePath = null,
                targetLeft = -1,
                targetTop = -1,
                targetRight = -1,
                targetBottom = -1,
                xPercent = 0f,
                yPercent = 0f,
                targetWPercent = 0f,
                targetHPercent = 0f,
                insideXPercent = 0.5f,
                insideYPercent = 0.5f,
                recordedScreenW = resources.displayMetrics.widthPixels,
                recordedScreenH = resources.displayMetrics.heightPixels
            )
        )
        return true
    }

    fun addMemoryClipboardCaptureGestureV6(slotRaw: Int): Boolean {
        // AARISH_MEMORY_CLIPBOARD_TWOSTEP_V6_CAPTURE_RECORD
        currentPoints.clear()
        currentSnapshot = null
        currentGestureDownTime = 0L
        currentGestureForceXyOnly = false
        currentMemoryCopyCode = 0
        multiTouchCanceled = false

        val slot = slotRaw.coerceIn(1, 6)
        val codeX = (-6100 - slot).toFloat()

        recordedGestures.add(
            RecordedGesture(
                delayFromStart = nextVirtualDelayFromStartV1(),
                points = listOf(
                    GesturePoint(x = codeX, y = -6100f, t = 0L),
                    GesturePoint(x = codeX, y = -6100f, t = 70L)
                ),
                targetText = "MEMORY_CLIPBOARD_CAPTURE_$slot",
                targetDesc = "Capture clipboard to memory slot $slot",
                targetId = null,
                targetClass = "AARISH_MEMORY_SLOT",
                targetPackage = owner.packageName,
                targetContextText = "AARISH_MEMORY_CLIPBOARD_CAPTURE_SLOT_$slot",
                targetChildText = null,
                targetSiblingText = null,
                targetRoleFlags = "AARISH_MEMORY_CLIPBOARD_CAPTURE_SLOT_$slot",
                targetTreePath = null,
                targetLeft = -1,
                targetTop = -1,
                targetRight = -1,
                targetBottom = -1,
                xPercent = 0f,
                yPercent = 0f,
                targetWPercent = 0f,
                targetHPercent = 0f,
                insideXPercent = 0.5f,
                insideYPercent = 0.5f,
                recordedScreenW = resources.displayMetrics.widthPixels,
                recordedScreenH = resources.displayMetrics.heightPixels
            )
        )

        aarishTraceTextToast("📥 M$slot clipboard recorded")
        return true
    }




fun addSystemGesture(actionType: Int) {
    currentPoints.clear()
    currentSnapshot = null
    currentGestureDownTime = 0L
    multiTouchCanceled = false

    val dummyCoord = when (actionType) {
        1 -> -100f
        2 -> -200f
        else -> return
    }

    val now = android.os.SystemClock.uptimeMillis()
    val rawDelay = (now - recordingStartTime).coerceAtLeast(0L)
    val previousEnd = recordedGestures.maxOfOrNull { gesture ->
        gesture.delayFromStart.coerceAtLeast(0L) +
            (gesture.points.maxOfOrNull { it.t.coerceAtLeast(0L) } ?: 0L)
    } ?: 0L
    val delay = kotlin.math.max(rawDelay, previousEnd + 140L).coerceAtMost(24L * 60L * 60L * 1000L)

    val points = listOf(
        GesturePoint(dummyCoord, dummyCoord, 0L),
        GesturePoint(dummyCoord, dummyCoord, 90L)
    )

    recordedGestures.add(
        RecordedGesture(
            delayFromStart = delay,
            points = points,
            targetText = if (actionType == 1) "GLOBAL_BACK" else "GLOBAL_RECENTS",
            targetDesc = if (actionType == 1) "System Back" else "System Recents"
        )
    )
}


// AARISH_TOUCH_CAPTURE_SEMANTIC_BRIDGE_V3_START
fun addAccessibilitySnapshotGesture(snapshot: TargetSnapshot): Boolean {
    currentPoints.clear()
    currentSnapshot = null
    currentGestureDownTime = 0L
    multiTouchCanceled = false

    fun safePercent(value: Float, fallback: Float): Float {
        return if (value.isNaN() || value.isInfinite()) fallback else value.coerceIn(0f, 1f)
    }

    val metrics = resources.displayMetrics
    val screenW = metrics.widthPixels.toFloat().coerceAtLeast(1f)
    val screenH = metrics.heightPixels.toFloat().coerceAtLeast(1f)

    val hasBounds = snapshot.targetRight > snapshot.targetLeft &&
        snapshot.targetBottom > snapshot.targetTop

    val rawX = if (hasBounds) {
        snapshot.targetLeft.toFloat() +
            (safePercent(snapshot.insideXPercent, 0.5f) * (snapshot.targetRight - snapshot.targetLeft).toFloat())
    } else {
        safePercent(snapshot.xPercent, 0.5f) * screenW
    }

    val rawY = if (hasBounds) {
        snapshot.targetTop.toFloat() +
            (safePercent(snapshot.insideYPercent, 0.5f) * (snapshot.targetBottom - snapshot.targetTop).toFloat())
    } else {
        safePercent(snapshot.yPercent, 0.5f) * screenH
    }

    val x = rawX.coerceIn(2f, (screenW - 2f).coerceAtLeast(2f))
    val y = rawY.coerceIn(2f, (screenH - 2f).coerceAtLeast(2f))

    val now = android.os.SystemClock.uptimeMillis()
    val rawDelay = (now - recordingStartTime).coerceAtLeast(0L)
    val previousEnd = recordedGestures.maxOfOrNull { gesture ->
        gesture.delayFromStart.coerceAtLeast(0L) +
            (gesture.points.maxOfOrNull { it.t.coerceAtLeast(0L) } ?: 0L)
    } ?: 0L

    val delay = kotlin.math.max(rawDelay, previousEnd + 90L)
        .coerceAtMost(24L * 60L * 60L * 1000L)

    val last = recordedGestures.lastOrNull()
    if (last != null && delay - previousEnd <= 280L) {
        val lastPoint = last.points.firstOrNull()
        val near = lastPoint != null &&
            kotlin.math.abs(lastPoint.x - x) < 22f &&
            kotlin.math.abs(lastPoint.y - y) < 22f

        val samePrimary =
            (!snapshot.targetId.isNullOrBlank() && snapshot.targetId == last.targetId) ||
                (!snapshot.targetText.isNullOrBlank() && snapshot.targetText == last.targetText) ||
                (!snapshot.targetDesc.isNullOrBlank() && snapshot.targetDesc == last.targetDesc)

        if (near || samePrimary) return false
    }

    val points = listOf(
        GesturePoint(x = x, y = y, t = 0L),
        GesturePoint(x = x, y = y, t = 90L)
    )

    recordedGestures.add(
        RecordedGesture(
            delayFromStart = delay,
            points = points,
            targetText = snapshot.targetText,
            targetDesc = snapshot.targetDesc,
            targetId = snapshot.targetId,
            targetClass = snapshot.targetClass,
            targetPackage = snapshot.targetPackage,
            targetContextText = snapshot.targetContextText,
            targetChildText = snapshot.targetChildText,
            targetSiblingText = snapshot.targetSiblingText,
            targetRoleFlags = snapshot.targetRoleFlags,
            targetTreePath = snapshot.targetTreePath,
            targetLeft = snapshot.targetLeft,
            targetTop = snapshot.targetTop,
            targetRight = snapshot.targetRight,
            targetBottom = snapshot.targetBottom,
            xPercent = safePercent(snapshot.xPercent, x / screenW),
            yPercent = safePercent(snapshot.yPercent, y / screenH),
            targetWPercent = safePercent(snapshot.targetWPercent, 0f),
            targetHPercent = safePercent(snapshot.targetHPercent, 0f),
            insideXPercent = safePercent(snapshot.insideXPercent, 0.5f),
            insideYPercent = safePercent(snapshot.insideYPercent, 0.5f),
            recordedScreenW = snapshot.recordedScreenW.takeIf { it > 0 } ?: metrics.widthPixels,
            recordedScreenH = snapshot.recordedScreenH.takeIf { it > 0 } ?: metrics.heightPixels
        )
    )

    aarishTraceTextToast("Magnet recorded") // AARISH_ENGINE_TRACE_TEXT_TOAST_V25B
    return true
}
// AARISH_TOUCH_CAPTURE_SEMANTIC_BRIDGE_V3_END

    fun hasRecordedSomething(): Boolean {
        return currentPoints.isNotEmpty() || recordedGestures.isNotEmpty()
    }

    fun removeLastGesture(): Boolean {
        if (currentPoints.isNotEmpty()) {
            currentPoints.clear()
            currentSnapshot = null
            return true
        }

        if (recordedGestures.isNotEmpty()) {
            recordedGestures.removeAt(recordedGestures.lastIndex)
            currentSnapshot = null
            return true
        }

        return false
    }

    fun getRecordedGestures(): List<RecordedGesture> {
        return recordedGestures.sortedBy { it.delayFromStart }
    }
}

