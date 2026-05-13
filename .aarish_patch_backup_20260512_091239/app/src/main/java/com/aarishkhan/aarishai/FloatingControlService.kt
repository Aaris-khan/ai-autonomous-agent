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
    private lateinit var btnSave: Button
    private lateinit var btnUndo: Button
    private lateinit var btnCut: Button

        private var isRecording = false
    private var pendingDiscardConfirm = false
    private var isSavingRecording = false
    private var unsavedGestures: List<RecordedGesture> = emptyList()
    private val handler = Handler(Looper.getMainLooper())

    private var playbackWatcherRunnable: Runnable? = null
    private var activeConfigDialog: android.app.AlertDialog? = null

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

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
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
    val radius = (if (compact) 14f else 16f) * resources.displayMetrics.density
    val targetHeight = if (compact) 34 else 38
    val targetWidth = if (compact) (minWdp - 5).coerceAtLeast(30) else minWdp

    button.minHeight = dp(targetHeight)
    button.minimumHeight = dp(targetHeight)
    button.minWidth = dp(targetWidth)
    button.minimumWidth = dp(targetWidth)
    button.setPadding(dp(if (compact) 4 else 6), dp(1), dp(if (compact) 4 else 6), dp(1))
    button.textSize = if (compact) 9.2f else 10.4f
    button.maxLines = 1
    button.isSingleLine = true
    button.isAllCaps = false
    button.includeFontPadding = false
    button.textAlignment = View.TEXT_ALIGNMENT_CENTER
    button.setTextColor(fgColor)

    val normal = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = radius
        setColor(bgColor)
        setStroke(dp(1), Color.argb(92, 255, 255, 255))
    }
    val pressed = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = radius
        setColor(darken(bgColor, 0.80f))
        setStroke(dp(1), Color.argb(150, 255, 255, 255))
    }

    button.background = android.graphics.drawable.StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), pressed)
        addState(intArrayOf(), normal)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        button.elevation = dp(2).toFloat()
        button.stateListAnimator = null
    }
}

private fun refreshPanelButtonStyles() {
    if (!::btnStart.isInitialized) return

    val startBg = when (btnStart.text?.toString()) {
        "STOP" -> Color.rgb(248, 113, 113)
        "HIDE" -> Color.rgb(251, 191, 36)
        "+ ADD" -> Color.rgb(59, 130, 246)
        "PLAY" -> Color.rgb(34, 197, 94)
        else -> Color.rgb(99, 102, 241)
    }

    stylePanelButton(btnStart, startBg, Color.WHITE, 48)
    if (::btnLoop.isInitialized) stylePanelButton(btnLoop, Color.rgb(14, 165, 233), Color.WHITE, 38)
    if (::btnWorkflow.isInitialized) stylePanelButton(btnWorkflow, Color.rgb(124, 58, 237), Color.WHITE, 40)
    btnClear?.let { stylePanelButton(it, Color.rgb(71, 85, 105), Color.WHITE, 34) }
    if (::btnSave.isInitialized) stylePanelButton(btnSave, Color.rgb(16, 185, 129), Color.WHITE, 42)
    if (::btnUndo.isInitialized) stylePanelButton(btnUndo, Color.rgb(20, 184, 166), Color.WHITE, 42)
    if (::btnCut.isInitialized) stylePanelButton(btnCut, Color.rgb(244, 63, 94), Color.WHITE, 34)
}

    private fun showFloatingPanel() {
    val compact = resources.displayMetrics.widthPixels < dp(390)
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(if (compact) 6 else 8), dp(7), dp(if (compact) 6 else 8), dp(7))
        background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.rgb(3, 7, 18), Color.rgb(15, 23, 42), Color.rgb(30, 64, 175))
        ).apply {
            cornerRadius = 23f * resources.displayMetrics.density
            setStroke(dp(1), Color.argb(132, 226, 232, 240))
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
    btnClear?.visibility = View.GONE

    val params = panelParams
    val panel = panelView
    if (params != null && panel != null) {
        oldPanelX = params.x
        oldPanelY = params.y
        val density = resources.displayMetrics.density
        val maxX = if (panel.width > 0) resources.displayMetrics.widthPixels - panel.width else resources.displayMetrics.widthPixels
        val maxY = if (panel.height > 0) resources.displayMetrics.heightPixels - panel.height else resources.displayMetrics.heightPixels

        params.x = (resources.displayMetrics.widthPixels - (118 * density).toInt()).coerceIn(0, if (maxX > 0) maxX else resources.displayMetrics.widthPixels)
        params.y = (54 * density).toInt().coerceIn(0, if (maxY > 0) maxY else resources.displayMetrics.heightPixels)
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

    btnClear?.visibility = if (hasSaved && unsavedGestures.isEmpty() && !isRecording) View.VISIBLE else View.GONE
    refreshPanelButtonStyles()

    val params = panelParams
    val panel = panelView
    if (params != null && panel != null) {
        params.x = oldPanelX
        params.y = oldPanelY
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
        closeSettingsPanel()
        activeConfigDialog = dialog

        dialog.setOnDismissListener {
            if (activeConfigDialog === dialog) {
                activeConfigDialog = null
            }
        }

        prepareOverlayDialog(dialog)
        dialog.show()
        prepareOverlayDialog(dialog)

        dialog.window?.apply {
            setDimAmount(0.20f)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

            val lp = attributes
            val screenW = resources.displayMetrics.widthPixels
            lp.width = kotlin.math.min(screenW - dp(32), dp(430)).coerceAtLeast(dp(280))
            attributes = lp
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

private fun showWorkflowHubDialog() {
        if (isRecording || AutoActionService.isPlaying() || unsavedGestures.isNotEmpty()) {
            Toast.makeText(this, "Recording/Play/Unsaved edit ke time workflow change nahi kar sakte", Toast.LENGTH_SHORT).show()
            return
        }

        val active = GestureStore.getActiveConfigName(this)

        val items = arrayOf(
            "🧩 Build Unlimited Workflow: START ➜ NEXT ➜ STOP",
            "➕ Change NEXT for Active Config",
            "🔁 Loop Settings for Active Config",
            "▶️ Play From Any Config",
            "👀 Preview Active Workflow",
            "🧹 Clear Active Chain Links"
        )

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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

    val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        .setTitle("Step 1: Pehle kaun si config chale?")
        .setItems(configs.toTypedArray()) { _, which ->
            val startConfig = configs[which]

            GestureStore.setNextConfig(this, startConfig, null)
            GestureStore.setActiveConfigName(this, startConfig)

            refreshConfigLabel()
            if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
            if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()

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
                    setTextColor(android.graphics.Color.rgb(17, 24, 39))
                    setTypeface(null, android.graphics.Typeface.NORMAL)
                }
                row.setBackgroundColor(android.graphics.Color.rgb(248, 250, 252))
            }
            return row
        }
    }

    val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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
            .filter { it != currentConfig && GestureStore.hasRecordingForConfig(this, it) }
            .distinct()

        if (candidates.isEmpty()) {
            Toast.makeText(this, "Link ke liye koi saved config nahi mili", Toast.LENGTH_LONG).show()
            return
        }

        val options = mutableListOf("🚫 Stop Here / Remove Link")
        options.addAll(candidates)

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("After '$currentConfig' run:")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    GestureStore.setNextConfig(this, currentConfig, null)
                    Toast.makeText(this, "Sequence yahin rukegi", Toast.LENGTH_SHORT).show()
                } else {
                    val next = options[which]
                    if (GestureStore.wouldCreateCycle(this, currentConfig, next)) {
                        Toast.makeText(this, "Cycle ban rahi thi, isliye link block kar di", Toast.LENGTH_LONG).show()
                    } else {
                        val ok = GestureStore.setNextConfig(this, currentConfig, next)
                        Toast.makeText(
                            this,
                            if (ok) "Linked: $currentConfig ➜ $next" else "Link save nahi hui",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                updateWorkflowButtonUI()
            }
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

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Workflow Preview")
            .setMessage("Chain:\n$chain\n\nLoop:\n$loopText\n\nPLAY dabane par yahi poora workflow chalega.")
            .setPositiveButton("OK", null)
            .create()

        showOverlayDialogSafely(dialog)
    }

    private fun showClearFullChainDialog() {
        val active = GestureStore.getActiveConfigName(this)

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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
            handler.postDelayed({ pendingDiscardConfirm = false }, 5000L)
            return
        }

        pendingDiscardConfirm = false
        glassHiddenAt = 0L
        nextNavigationGapOverride = null
        unsavedGestures = emptyList()
        isRecording = false
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

    fun recordSystemAction(actionType: Int) {
        if (!isRecording) return
        val view = captureView
        if (view == null) {
            android.widget.Toast.makeText(this, "Recording glass ready nahi hai", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        view.addSystemGesture(actionType)
        val msg = if (actionType == 1) { "🔙 BACK recorded" } else { "🗂️ RECENTS recorded" }
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
        override fun onDestroy() {
        playbackWatcherRunnable?.let { handler.removeCallbacks(it) }
        playbackWatcherRunnable = null
        closeSettingsPanel()
        handler.removeCallbacksAndMessages(null)
        isRecording = false
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

    val lastGestureEnd = unsavedGestures.maxOfOrNull { g ->
        g.delayFromStart.coerceAtLeast(0L) + (g.points.lastOrNull()?.t ?: 0L).coerceAtLeast(0L)
    } ?: 0L

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
                Toast.makeText(this, "Glass hat gaya! Naya page kholo aur + ADD dabao.", Toast.LENGTH_LONG).show()
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
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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
    updateUIState("HIDE", true, false, true)
    bringPanelToFront()
    Toast.makeText(this, "🟢 Glass ON! Wait aur Tap record honge.", Toast.LENGTH_SHORT).show()
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
            updateUIState("HIDE", true, false, true)
            return
        }

        // Case 2: SAVE se pehle unsaved buffer ke andar last step delete karo
        if (unsavedGestures.isNotEmpty()) {
            val sortedUnsaved = unsavedGestures.sortedBy { it.delayFromStart }
            val removedGesture = sortedUnsaved.lastOrNull()
            val remainingGestures = sortedUnsaved.dropLast(1)
            val previousEnd = remainingGestures.maxOfOrNull { g ->
                g.delayFromStart.coerceAtLeast(0L) + (g.points.lastOrNull()?.t ?: 0L).coerceAtLeast(0L)
            } ?: 0L
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
            val cleanPoints = gesture.points
                .filter { !it.x.isNaN() && !it.x.isInfinite() && !it.y.isNaN() && !it.y.isInfinite() }
                .sortedBy { it.t.coerceAtLeast(0L) }
                .map { it.copy(t = it.t.coerceAtLeast(0L).coerceAtMost(60000L)) }

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
    val relativeTime = (event.eventTime - downTime).coerceAtLeast(0L).coerceAtMost(60000L)

    if (currentPoints.isNotEmpty()) {
        val last = currentPoints.last()
        val samePlace = kotlin.math.abs(last.x - event.rawX) < 1.5f && kotlin.math.abs(last.y - event.rawY) < 1.5f
        val sameTime = relativeTime == last.t
        val tooFast = relativeTime - last.t < 10L

        if (!forceAdd && samePlace && tooFast) return
        if (forceAdd && samePlace && sameTime && currentPoints.size > 1) return
    }

    currentPoints.add(GesturePoint(x = event.rawX, y = event.rawY, t = relativeTime))
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

        val delayFromStart = (currentGestureDownTime - recordingStartTime).coerceAtLeast(0L)
        val firstP = currentPoints.first()

        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = metrics.heightPixels.toFloat().coerceAtLeast(1f)

        val snapshot = currentSnapshot ?: captureSnapshotFor(
            firstP.x.toInt(),
            firstP.y.toInt()
        )

        recordedGestures.add(
            RecordedGesture(
                delayFromStart = delayFromStart,
                points = currentPoints.sortedBy { it.t }.toList(),
                targetText = snapshot?.targetText,
                targetDesc = snapshot?.targetDesc,
                targetId = snapshot?.targetId,
                targetClass = snapshot?.targetClass,
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
        )

        currentPoints.clear()
        currentSnapshot = null
        currentGestureDownTime = 0L
        multiTouchCanceled = false
    }


fun addSystemGesture(actionType: Int) {
    currentPoints.clear()
    currentSnapshot = null
    currentGestureDownTime = 0L

    val dummyCoord = when (actionType) {
        1 -> -100f
        2 -> -200f
        else -> return
    }

    val delay = (android.os.SystemClock.uptimeMillis() - recordingStartTime).coerceAtLeast(0L)
    val points = listOf(GesturePoint(dummyCoord, dummyCoord, 100L))
    recordedGestures.add(
        RecordedGesture(delayFromStart = delay, points = points)
    )
}
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
