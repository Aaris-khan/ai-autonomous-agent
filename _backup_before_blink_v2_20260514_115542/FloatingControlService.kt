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
    private var panelView: View? = null
    private var captureView: TouchCaptureView? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var oldPanelX = 30
    private var oldPanelY = 120
    private var panelHiddenForPlayback = false

    private lateinit var label: TextView
    private lateinit var btnStart: Button
    private var btnClear: android.widget.Button? = null
    private lateinit var btnLoop: Button
    private lateinit var btnWorkflow: Button
    private lateinit var btnTools: Button
    private lateinit var btnSystem: Button
    private lateinit var btnSave: Button
    private lateinit var btnUndo: Button
    private lateinit var btnCut: Button

        private var isRecording = false
    private var pendingDiscardConfirm = false
    private var isSavingRecording = false
    private var unsavedGestures: List<RecordedGesture> = emptyList()
    private val handler = Handler(Looper.getMainLooper())

    // AARISH_ADVANCED_BLINK_STRIKE_FIELDS_V1
    @Volatile private var liveReplayActive = false
    private var liveReplaySerial = 0
    private var lastLiveReplayAt = 0L
    private var lastLiveReplayX = Float.NaN
    private var lastLiveReplayY = Float.NaN
    private val liveReplayQueue = java.util.ArrayDeque<RecordedGesture>()
    private var liveReplayQueueDraining = false

    private var playbackWatcherRunnable: Runnable? = null
    private var activeConfigDialog: android.app.AlertDialog? = null

    // AARISH_ULTRA_TOUCH_SYSTEM_V2_START
    @Volatile private var liveReplayActive = false
    private var liveReplaySerial = 0
    private var lastLiveReplayAt = 0L
    private var lastLiveReplayX = Float.NaN
    private var lastLiveReplayY = Float.NaN

    // AARISH_LIVE_REPLAY_QUEUE_FIX_V1:
    // Fast double-tap miss fix. Short taps wait briefly before pass-through replay,
    // so second tap can still be captured by the glass, then gestures replay in order.
    private val liveReplayQueue = java.util.ArrayDeque<RecordedGesture>()
    private var liveReplayQueueDraining = false
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
    btnClear?.let { stylePanelButton(it, Color.rgb(30, 41, 59), Color.WHITE, 34) }
    if (::btnSave.isInitialized) stylePanelButton(btnSave, Color.rgb(13, 148, 136), Color.WHITE, 42)
    if (::btnUndo.isInitialized) stylePanelButton(btnUndo, Color.rgb(37, 99, 235), Color.WHITE, 42)
    if (::btnCut.isInitialized) stylePanelButton(btnCut, Color.rgb(225, 29, 72), Color.WHITE, 34)
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
    btnClear?.let { root.addView(it) }
    root.addView(btnSave)
    root.addView(btnUndo)
    root.addView(btnCut)

    updateWorkflowButtonUI()
    refreshPanelButtonStyles()

    val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

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
        val chain = GestureStore.getWorkflowChain(this, active)
        btnWorkflow.text = if (chain.size > 1) "🧱 ${chain.size}" else "🧱 WF"
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
    val firstX = gesture.points.firstOrNull()?.x ?: return false
    return firstX <= -50f ||
        gesture.targetText == "GLOBAL_BACK" ||
        gesture.targetText == "GLOBAL_RECENTS"
}

private fun localGestureHasRealMovement(gesture: RecordedGesture): Boolean {
    val points = gesture.points
    if (points.size <= 1) return false
    val first = points.first()
    val slop = kotlin.math.max(10f, 6f * resources.displayMetrics.density)
    return points.any { point ->
        abs(point.x - first.x) > slop || abs(point.y - first.y) > slop
    }
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
        if (isRecording || AutoActionService.isPlaying() || unsavedGestures.isNotEmpty()) {
            Toast.makeText(this, "Recording/Play/Unsaved edit ke time workflow change nahi kar sakte", Toast.LENGTH_SHORT).show()
            return
        }

        val active = GestureStore.getActiveConfigName(this)

        val items = arrayOf(
            "🧩 Build Unlimited Workflow: START ➜ NEXT ➜ STOP",
            "🔀 Configure NEXT Cycle",
            "🔁 Loop Settings for Active Config",
            "▶️ Play From Any Config",
            "👀 Preview Active Workflow",
            "🧹 Clear Active Chain Links"
        )

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Workflow Builder • $active")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showSelectStartConfigForWorkflowDialog()
                    1 -> showChooseNextConfigDialog(active)
                    2 -> showLoopChoiceForConfig(active)
                    3 -> showPlayFromAnyConfigDialog()
                    4 -> showWorkflowPreviewDialog()
                    5 -> showClearFullChainDialog()
                }
            }
            .create()

        showOverlayDialogSafely(dialog)
    }


private fun showSelectStartConfigForWorkflowDialog() {
    val configs = GestureStore.getAllConfigNames(this)
        .filter { GestureStore.hasRecordingForConfig(this, it) }
        .distinct()

    if (configs.size < 2) {
        Toast.makeText(this, "Workflow ke liye kam se kam 2 saved configs chahiye", Toast.LENGTH_LONG).show()
        return
    }

    val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
        .setTitle("Step 1: Pehle kaun si config chale?")
        .setItems(configs.toTypedArray()) { _, which ->
            val startConfig = configs[which]

            GestureStore.clearChainFrom(this, startConfig)
            GestureStore.setActiveConfigName(this, startConfig)

            refreshConfigLabel()
            if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
            if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()
            restorePanelUI()

            showSelectNextConfigForWorkflowDialog(
                currentConfig = startConfig,
                stepNumber = 2,
                originalStartConfig = startConfig
            )
        }
        .setCancelable(false)
        .create()

    showOverlayDialogSafely(dialog)
}





    private fun showSelectNextConfigForWorkflowDialog(
    currentConfig: String,
    stepNumber: Int = 2,
    originalStartConfig: String = currentConfig
) {
    val alreadyChosen = GestureStore.getWorkflowChain(this, originalStartConfig, 200)
        .map { it.trim() }
        .toSet()

    val nextConfigs = GestureStore.getAllConfigNames(this)
        .filter { candidate ->
            candidate != currentConfig &&
                !alreadyChosen.contains(candidate) &&
                GestureStore.hasRecordingForConfig(this, candidate)
        }
        .distinct()

    val options = mutableListOf("🛑 STOP CHAINING (Go to Loop Settings)")
    options.addAll(nextConfigs)

    val adapter = object : android.widget.ArrayAdapter<String>(
        this,
        android.R.layout.simple_list_item_1,
        options
    ) {
        override fun getView(
            position: Int,
            convertView: android.view.View?,
            parent: android.view.ViewGroup
        ): android.view.View {
            val row = super.getView(position, convertView, parent)
            row.findViewById<android.widget.TextView>(android.R.id.text1)?.apply {
                textSize = 20f
                setPadding(dp(18), dp(14), dp(18), dp(14))
                if (position == 0) {
                    setTextColor(android.graphics.Color.rgb(220, 38, 38))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                } else {
                    setTextColor(android.graphics.Color.rgb(15, 23, 42))
                    setTypeface(null, android.graphics.Typeface.NORMAL)
                }
                row.setBackgroundColor(android.graphics.Color.rgb(248, 250, 252))
            }
            return row
        }
    }

    val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
        .setTitle("Step $stepNumber: '$currentConfig' ke baad kya chale?")
        .setAdapter(adapter) { _, which ->
            if (which == 0) {
                GestureStore.setNextConfig(this, currentConfig, null)
                GestureStore.setActiveConfigName(this, originalStartConfig)

                refreshConfigLabel()
                if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
                if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()
                restorePanelUI()

                Toast.makeText(this, "Chain complete! Ab loop select karo.", Toast.LENGTH_SHORT).show()
                showLoopChoiceForConfig(originalStartConfig)
                return@setAdapter
            }

            val nextConfig = options[which]

            GestureStore.setNextConfig(this, nextConfig, null)

            if (GestureStore.wouldCreateCycle(this, currentConfig, nextConfig)) {
                Toast.makeText(this, "Cycle ban rahi thi, link block kar di", Toast.LENGTH_LONG).show()
                showSelectNextConfigForWorkflowDialog(currentConfig, stepNumber, originalStartConfig)
                return@setAdapter
            }

            val ok = GestureStore.setNextConfig(this, currentConfig, nextConfig)
            if (ok) {
                GestureStore.setActiveConfigName(this, originalStartConfig)

                refreshConfigLabel()
                if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
                if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()
                restorePanelUI()

                Toast.makeText(this, "Linked: $currentConfig ➜ $nextConfig", Toast.LENGTH_SHORT).show()

                showSelectNextConfigForWorkflowDialog(
                    currentConfig = nextConfig,
                    stepNumber = stepNumber + 1,
                    originalStartConfig = originalStartConfig
                )
            } else {
                Toast.makeText(this, "Link save nahi hui", Toast.LENGTH_LONG).show()
                showSelectNextConfigForWorkflowDialog(currentConfig, stepNumber, originalStartConfig)
            }
        }
        .setCancelable(false)
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
            .setTitle("Clear Full Chain?")
            .setMessage("Isse '$active' se aage jitni linked sequence hai, uske links delete ho jayenge. Recordings delete nahi hongi.")
            .setPositiveButton("Clear Links") { _, _ ->
                val count = GestureStore.clearChainFrom(this, active)
                updateWorkflowButtonUI()
                Toast.makeText(this, "$count workflow links delete ho gaye", Toast.LENGTH_SHORT).show()
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
        return try {
            if (view.parent != null) {
                try {
                    windowManager.removeViewImmediate(view)
                } catch (_: Exception) {
                    try { windowManager.removeView(view) } catch (_: Exception) {}
                }
            }
            windowManager.addView(view, params)
            true
        } catch (e: Exception) {
            Toast.makeText(this, "$errorMessage: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun safeUpdateView(view: View, params: WindowManager.LayoutParams) {
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {}
    }

    private fun safeRemoveView(view: View?) {
        if (view == null) return
        try {
            windowManager.removeViewImmediate(view)
        } catch (_: Exception) {
            try { windowManager.removeView(view) } catch (_: Exception) {}
        }
    }



    // AARISH_ULTRA_TOUCH_SYSTEM_V2_START
H_LIVE_REPLAY_QUEUE_DRAIN_V1
H_ULTRA_TOUCH_SYSTEM_V2_END


    // AARISH_ADVANCED_BLINK_STRIKE_GHOST_ENGINE_V1
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

        // Same callback duplicate block; real fast double-tap ko block nahi karta.
        if (now - lastLiveReplayAt < 28L && dx < 2f && dy < 2f) return true

        lastLiveReplayAt = now
        lastLiveReplayX = first.x
        lastLiveReplayY = first.y
        return false
    }

    private fun aarishSetGlassGhostModeSafe(ghost: Boolean): Boolean {
        if (!::windowManager.isInitialized) return false

        val glass = captureView ?: return false
        val params = glass.layoutParams as? WindowManager.LayoutParams ?: return false

        params.flags = if (ghost) {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }

        glass.alpha = 1f
        glass.setBackgroundColor(if (ghost) Color.TRANSPARENT else Color.argb(24, 0, 200, 0))

        return try {
            windowManager.updateViewLayout(glass, params)
            true
        } catch (_: Throwable) {
            false
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

    private fun drainNextLiveReplaySafe() {
        handler.post {
            if (instance !== this@FloatingControlService || !isRecording || AutoActionService.isPlaying()) {
                liveReplayQueue.clear()
                liveReplayQueueDraining = false
                liveReplayActive = false
                aarishSetGlassGhostModeSafe(false)
                return@post
            }

            val gesture = liveReplayQueue.pollFirst()
            if (gesture == null) {
                liveReplayQueueDraining = false
                liveReplayActive = false
                aarishSetGlassGhostModeSafe(false)
                return@post
            }

            val serial = liveReplaySerial + 1
            liveReplaySerial = serial
            liveReplayActive = true

            if (!aarishSetGlassGhostModeSafe(true)) {
                liveReplayQueue.clear()
                liveReplayQueueDraining = false
                liveReplayActive = false
                return@post
            }

            val duration = liveReplayDurationMs(gesture)
            val watchdogMs = (duration + 4200L).coerceAtMost(610000L)
            var finished = false

            fun finishAndContinue() {
                if (finished) return
                finished = true

                restoreLiveReplayGlassSafe(serial)

                handler.postDelayed({
                    if (instance !== this@FloatingControlService || !isRecording || AutoActionService.isPlaying()) {
                        liveReplayQueue.clear()
                        liveReplayQueueDraining = false
                        liveReplayActive = false
                        aarishSetGlassGhostModeSafe(false)
                        return@postDelayed
                    }

                    if (liveReplayQueue.isNotEmpty()) {
                        drainNextLiveReplaySafe()
                    } else {
                        liveReplayQueueDraining = false
                    }
                }, 78L)
            }

            // Android ko FLAG_NOT_TOUCHABLE settle karne ke liye small delay.
            handler.postDelayed({
                if (serial != liveReplaySerial || instance !== this@FloatingControlService || !isRecording) {
                    finishAndContinue()
                    return@postDelayed
                }

                AutoActionService.playSingleLiveGestureSafe(gesture) {
                    handler.postDelayed({
                        finishAndContinue()
                    }, 55L)
                }
            }, 38L)

            handler.postDelayed({
                if (serial == liveReplaySerial && liveReplayActive) {
                    finishAndContinue()
                }
            }, watchdogMs)
        }
    }

    fun triggerLiveReplaySafe(gesture: RecordedGesture) {
        if (instance !== this@FloatingControlService) return
        if (!isRecording || AutoActionService.isPlaying()) return
        if (gesture.points.isEmpty()) return

        val firstX = gesture.points.firstOrNull()?.x ?: return
        if (firstX <= -50f) return

        if (isLiveReplayBlockedDuplicate(gesture)) return

        handler.post {
            if (instance !== this@FloatingControlService || !isRecording || AutoActionService.isPlaying()) return@post

            if (liveReplayQueue.size >= 96) {
                liveReplayQueue.clear()
                liveReplayQueueDraining = false
                liveReplayActive = false
                aarishSetGlassGhostModeSafe(false)
                Toast.makeText(this, "⚠️ Input flood skip hua. Thoda dheere tap karo.", Toast.LENGTH_SHORT).show()
                return@post
            }

            liveReplayQueue.addLast(gesture)

            if (!liveReplayQueueDraining) {
                liveReplayQueueDraining = true

                val firstDelay = if (!localGestureHasRealMovement(gesture) && liveReplayDurationMs(gesture) <= 240L) {
                    155L
                } else {
                    38L
                }

                handler.postDelayed({
                    drainNextLiveReplaySafe()
                }, firstDelay)
            }
        }
    }

    private fun triggerLiveSystemActionSafe(actionType: Int) {
        if (instance !== this@FloatingControlService) return
        if (!isRecording || AutoActionService.isPlaying()) return

        val serial = liveReplaySerial + 1
        liveReplaySerial = serial
        liveReplayActive = true

        if (!aarishSetGlassGhostModeSafe(true)) return

        handler.postDelayed({
            if (serial != liveReplaySerial || instance !== this@FloatingControlService || !isRecording) {
                restoreLiveReplayGlassSafe(serial)
                return@postDelayed
            }

            AutoActionService.performLiveSystemActionSafe(actionType) {
                handler.postDelayed({
                    restoreLiveReplayGlassSafe(serial)
                }, 80L)
            }
        }, 45L)

        handler.postDelayed({
            if (serial == liveReplaySerial && liveReplayActive) {
                restoreLiveReplayGlassSafe(serial)
            }
        }, 2400L)
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

            if (AutoActionService.isPlaying()) {
                recoverOverlayStackToFrontDebounced(180L)
                return@post
            }

            if (
                isRecording &&
                captureView != null &&
                !liveReplayActive &&
                now > semanticClickMuteUntil
            ) {
                semanticAccessibilityBridgeUntil = kotlin.math.max(
                    semanticAccessibilityBridgeUntil,
                    now + 9000L
                )
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
                pkg.contains("inputmethod") ||
                pkg.contains("keyboard")
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
        overlayRecoverRunnable?.let { handler.removeCallbacks(it) }

        val task = Runnable {
            overlayRecoverRunnable = null
            recoverOverlayStackToFrontNow()
        }

        overlayRecoverRunnable = task
        handler.postDelayed(task, delayMs.coerceIn(40L, 420L))
    }

    private fun recoverOverlayStackToFrontNow() {
        if (instance !== this@FloatingControlService) return
        if (!::windowManager.isInitialized) return

        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastOverlayRecoverAt < 105L) return
        lastOverlayRecoverAt = now

        val glass = captureView
        val glassParams = glass?.layoutParams as? WindowManager.LayoutParams

        if (isRecording && glass != null && glassParams != null) {
            reAddOverlayViewSilently(glass, glassParams)
        }

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
        fun removeQuietly() {
            try {
                if (view.parent != null) {
                    try {
                        windowManager.removeViewImmediate(view)
                    } catch (_: Exception) {
                        try { windowManager.removeView(view) } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {
            }
        }

        fun addOrUpdateQuietly(): Boolean {
            return try {
                if (view.parent == null) {
                    windowManager.addView(view, params)
                } else {
                    windowManager.updateViewLayout(view, params)
                }
                true
            } catch (_: Exception) {
                false
            }
        }

        removeQuietly()

        if (addOrUpdateQuietly()) return

        handler.postDelayed({
            if (instance !== this@FloatingControlService || !::windowManager.isInitialized) return@postDelayed
            if (view === captureView && !isRecording) return@postDelayed
            addOrUpdateQuietly()
        }, 230L)
    }

    // AARISH_SHARE_MENU_LIVE_GUARD_V4_END

        override fun onDestroy() {
        playbackWatcherRunnable?.let { handler.removeCallbacks(it) }
        playbackWatcherRunnable = null
        closeSettingsPanel()
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

    safeRemoveView(captureView)
    captureView = null
    pendingDiscardConfirm = false

    val touchLayer = TouchCaptureView(this)
    val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

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

}

// BUG #1 FIX: ACTION_DOWN aur ACTION_UP hamesha save honge — long press skip nahi hoga



class TouchCaptureView(context: android.content.Context) : android.view.View(context) {

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
    private var multiTouchCanceled = false

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
                addPoint(event, forceAdd = true)

                // Button/symbol ka snapshot DOWN par lo, screen change hone se pehle
                currentSnapshot = captureSnapshotFor(event.rawX.toInt(), event.rawY.toInt())
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                addPoint(event, forceAdd = false)
            }
            android.view.MotionEvent.ACTION_UP -> {
                addPoint(event, forceAdd = true)
                saveCurrentGesture()
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


    private fun saveCurrentGesture() {
        if (currentPoints.isEmpty()) return

        val cleanPoints = normalizePointsForSave(currentPoints)
        if (cleanPoints.isEmpty()) {
            currentPoints.clear()
            currentSnapshot = null
            currentGestureDownTime = 0L
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

        val snapshot = currentSnapshot ?: captureSnapshotFor(
            firstP.x.toInt(),
            firstP.y.toInt()
        )

        val newGesture = RecordedGesture(
            delayFromStart = delayFromStart,
            points = cleanPoints,
            targetText = snapshot?.targetText,
            targetDesc = snapshot?.targetDesc,
            targetId = snapshot?.targetId,
            targetClass = snapshot?.targetClass,
            targetPackage = snapshot?.targetPackage,
            targetContextText = snapshot?.targetContextText,
            targetChildText = snapshot?.targetChildText,
            targetSiblingText = snapshot?.targetSiblingText,
            targetRoleFlags = snapshot?.targetRoleFlags,
            targetTreePath = snapshot?.targetTreePath,
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

        recordedGestures.add(newGesture)

        // Tap, double tap, swipe, long press sab raw gesture ke form me live replay hoga.
        (context as? FloatingControlService)?.triggerLiveReplaySafe(newGesture)

        currentPoints.clear()
        currentSnapshot = null
        currentGestureDownTime = 0L
        multiTouchCanceled = false
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
