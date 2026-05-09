package com.aarishkhan.aarishai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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

    private lateinit var windowManager: WindowManager
    private var panelView: View? = null
    private var captureView: TouchCaptureView? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var oldPanelX = 30
    private var oldPanelY = 120

    private lateinit var label: TextView
    private lateinit var btnStart: Button
    private var btnSpeed: android.widget.Button? = null
    private var btnSettings: android.widget.Button? = null
    private var btnClear: android.widget.Button? = null
    private lateinit var btnLoop: Button
    private lateinit var btnSave: Button
    private lateinit var btnCut: Button

        private var isRecording = false
    private var pendingDiscardConfirm = false
    private var unsavedGestures: List<RecordedGesture> = emptyList()
    private val handler = Handler(Looper.getMainLooper())

    private var playbackWatcherRunnable: Runnable? = null

            override fun onCreate() {
        super.onCreate()
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
            .setContentText("Macro recorder is ready in background")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    1,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(1, notification)
            }
            true
        } catch (e: Exception) {
            Toast.makeText(this, "Foreground service start nahi hua: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun showFloatingPanel() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14, 10, 14, 10)
            setBackgroundColor(Color.argb(230, 20, 25, 35))
        }

        label = TextView(this).apply {
            text = "AGENT"
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(12, 0, 18, 0)
        }

        btnStart = Button(this).apply {
            text = if (GestureStore.hasRecording(this@FloatingControlService)) "PLAY" else "START"
            setOnClickListener { handleStartButton() }

            // BUG #1 FIX: Recording/Playback ke time clear nahi hoga
                        setOnLongClickListener {
                if (isRecording || AutoActionService.isPlaying() || unsavedGestures.isNotEmpty()) {
                    Toast.makeText(
                        this@FloatingControlService,
                        "Recording/Unsaved command ke time clear nahi kar sakte",
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                } else {
                    GestureStore.clear(this@FloatingControlService)
                    unsavedGestures = emptyList()
                                        btnStart.text = "START"
                    btnStart.setOnClickListener { handleStartButton() }
                    updateLoopButtonText(btnLoop)
                    btnLoop.visibility = View.GONE
                    btnSave.visibility = View.GONE
                    Toast.makeText(
                        this@FloatingControlService,
                        "Old command clear ho gaya",
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
            }
        }

        btnLoop = Button(this).apply {
            updateLoopButtonText(this)
            setOnClickListener {
                toggleLoopMode()
                updateLoopButtonText(this)
            }
            visibility = if (GestureStore.hasRecording(this@FloatingControlService)) View.VISIBLE else View.GONE
        }

                btnSave = Button(this).apply {
            text = "SAVE"
            visibility = View.GONE
            setOnClickListener { saveRecording() }
        }

        btnCut = Button(this).apply {
            text = "CUT"
            setOnClickListener { stopEverythingAndClose() }
        }

        root.addView(label)
        root.addView(btnStart)
        root.addView(btnLoop)
        root.addView(btnSave)
        root.addView(btnCut)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
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
        params.x = 30
        params.y = 120
        panelParams = params

        panelView = root
        if (!safeAddView(root, params, "Floating panel open nahi hua")) {
            stopSelf()
            return
        }
        makePanelDraggable(label, root, params)
    }

    private fun toggleLoopMode() {
        val currentMode = GestureStore.getLoopMode(this)
        val currentValue = GestureStore.getLoopValue(this)
        when (currentMode) {
            "ONCE" -> GestureStore.saveLoopSettings(this, "COUNT", 10)
            "COUNT" -> GestureStore.saveLoopSettings(this, "INFINITE", 0)
            "INFINITE" -> GestureStore.saveLoopSettings(this, "TIME", 5)
            "TIME" -> {
                if (currentValue == 5) GestureStore.saveLoopSettings(this, "TIME", 10)
                else GestureStore.saveLoopSettings(this, "ONCE", 1)
            }
            else -> GestureStore.saveLoopSettings(this, "ONCE", 1)
        }
    }

    private fun updateLoopButtonText(btn: Button) {
        val mode = GestureStore.getLoopMode(this)
        val value = GestureStore.getLoopValue(this)
        btn.text = when (mode) {
            "COUNT" -> "${value}x"
            "INFINITE" -> "∞"
            "TIME" -> "${value}m"
            else -> "1x"
        }
    }

    

       private fun checkPlaybackStateContinuously() {
        playbackWatcherRunnable?.let { handler.removeCallbacks(it) }
                val watcher = object : Runnable {
            override fun run() {
                                if (!AutoActionService.isPlaying()) {
                    if (!isRecording && btnStart.text == "STOP") {
                        btnStart.text = "PLAY"
                        restorePanelUI()
                    }
                    playbackWatcherRunnable = null
                                        return
                }
                handler.postDelayed(this, 200)
            }
        }
        playbackWatcherRunnable = watcher
        handler.postDelayed(watcher, 200)
    }

    private fun hidePanelUIForPlayback() {
        btnSave.visibility = View.GONE
        btnCut.visibility = View.GONE
        label.visibility = View.GONE
        btnLoop.visibility = View.GONE
        val params = panelParams
        val panel = panelView
                if (params != null && panel != null) {
            oldPanelX = params.x
            oldPanelY = params.y
                        val density = resources.displayMetrics.density
            params.x = resources.displayMetrics.widthPixels - (120 * density).toInt()
            params.y = (60 * density).toInt()
            try {
                windowManager.updateViewLayout(panel, params)
            } catch (_: Exception) {
            }
        }
    }

    // BUG #5 FIX: btnLoop tabhi dikhega jab actually recording save ho
        // BUG FIX: Empty recording par SAVE hide rahega
    private fun restorePanelUI() {
        btnSave.visibility = if (unsavedGestures.isNotEmpty()) View.VISIBLE else View.GONE
        btnCut.visibility = View.VISIBLE
        label.visibility = View.VISIBLE
        btnLoop.visibility = if (GestureStore.hasRecording(this)) View.VISIBLE else View.GONE

        val params = panelParams
        val panel = panelView
        if (params != null && panel != null) {
            params.x = oldPanelX
            params.y = oldPanelY
            try {
                windowManager.updateViewLayout(panel, params)
            } catch (_: Exception) {
            }
        }
    }

    

            private fun bringPanelToFront() {
        val panel = panelView ?: return
        val params = panelParams ?: return
        safeRemoveView(panel)
        safeAddView(panel, params, "Panel restore error")
    }

     

    // BUG #4 FIX: Loop text update + visibility correctly set on save
     
        private fun stopEverythingAndClose() {
                                if (unsavedGestures.isNotEmpty() && !pendingDiscardConfirm) {
            pendingDiscardConfirm = true
            Toast.makeText(this, "Unsaved recording hai! Discard karne ke liye dobara CUT dabao.", Toast.LENGTH_LONG).show()
            handler.postDelayed({ pendingDiscardConfirm = false }, 5000)
            return
        }
        pendingDiscardConfirm = false
        unsavedGestures = emptyList()
        if (isRecording) {
            isRecording = false
            safeRemoveView(captureView)
            captureView = null
        }
        if (AutoActionService.isPlaying()) AutoActionService.stopPlayback(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }

    private fun makePanelDraggable(dragHandle: View, root: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                                MotionEvent.ACTION_MOVE -> {
                    val metrics = root.context.resources.displayMetrics
                    val maxX = metrics.widthPixels - root.width
                    val maxY = metrics.heightPixels - root.height
                    
                    val newX = initialX + (event.rawX - initialTouchX).toInt()
                    val newY = initialY + (event.rawY - initialTouchY).toInt()
                    
                    params.x = newX.coerceIn(0, if (maxX > 0) maxX else metrics.widthPixels)
                    params.y = newY.coerceIn(0, if (maxY > 0) maxY else metrics.heightPixels)
                    
                    safeUpdateView(root, params)
                    true
                }
                else -> true
            }
        }
    }

    private fun safeAddView(view: View, params: WindowManager.LayoutParams, errorMessage: String): Boolean {
        return try {
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
            windowManager.removeView(view)
        } catch (_: Exception) {}
    }

        override fun onDestroy() {
        playbackWatcherRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
        safeRemoveView(captureView)
        safeRemoveView(panelView)
        captureView = null
        panelView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null


    private var glassHiddenAt = 0L

    // ✅ SAFE UI STATE FIX: Default parameter added to prevent compile errors
    private fun closeSettingsPanel() {}

    private fun updateUIState(startText: String, showSave: Boolean, showOthers: Boolean, showCut: Boolean = true) {
        btnStart.text = startText
        val sVis = if (showSave) android.view.View.VISIBLE else android.view.View.GONE
        val oVis = if (showOthers) android.view.View.VISIBLE else android.view.View.GONE
        val cVis = if (showCut) android.view.View.VISIBLE else android.view.View.GONE
        
        try { btnSave?.visibility = sVis } catch(e:Exception){}
        try { btnClear?.visibility = oVis } catch(e:Exception){}
        try { btnSettings?.visibility = oVis } catch(e:Exception){}
        try { btnLoop?.visibility = oVis } catch(e:Exception){}
        try { btnSpeed?.visibility = oVis } catch(e:Exception){}
        try { btnCut?.visibility = cVis } catch(e:Exception){}
    }

    // ✅ EXACT NAVIGATION GAP FIX
    private fun extractAndAppendGestures() {
        val view = captureView as? TouchCaptureView ?: return
        val newGestures = view.getRecordedGestures()
        
        if (newGestures.isEmpty()) return
        
        val mutable = unsavedGestures.toMutableList()
        val lastGesture = unsavedGestures.lastOrNull()
        val lastGestureEnd = (lastGesture?.delayFromStart ?: 0L) + (lastGesture?.points?.lastOrNull()?.t ?: 0L)
        
        // Asli Navigation Gap: Naye Glass ke start time mein se Purane HIDE time ko minus karo
        val navigationGap = if (unsavedGestures.isNotEmpty() && glassHiddenAt > 0L) {
            val gap = view.getRecordingStartTime() - glassHiddenAt
            if (gap > 0L) gap else 0L
        } else {
            0L
        }
        
        val timeOffset = if (unsavedGestures.isEmpty()) {
            0L
        } else {
            lastGestureEnd + navigationGap
        }
        
        newGestures.forEach { g -> 
            mutable.add(RecordedGesture(delayFromStart = g.delayFromStart + timeOffset, points = g.points))
        }
        
        unsavedGestures = mutable
    }

    private fun handleStartButton() {
        if (AutoActionService.isPlaying()) {
            AutoActionService.stopPlayback(this)
            updateUIState("PLAY", false, true, true)
            restorePanelUI()
            android.widget.Toast.makeText(this, "Playback stopped", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        if (isRecording) {
            // ✅ ORDER FIXED: Pehle extract karo, fir glass hatao, aur fir HIDE time save karo
            extractAndAppendGestures()
            isRecording = false
            safeRemoveView(captureView)
            captureView = null
            
            glassHiddenAt = android.os.SystemClock.uptimeMillis() // ACTUAL WAIT YAHAN SE START HOGA
            
            updateUIState("+ ADD", true, false, true)
            android.widget.Toast.makeText(this, "Glass hat gaya! Naya page kholo aur + ADD dabao.", android.widget.Toast.LENGTH_LONG).show()
            
        } else if (unsavedGestures.isNotEmpty()) {
            startRecording()
        } else if (GestureStore.hasRecording(this)) {
            try { closeSettingsPanel() } catch(e: Exception) {}
            val started = AutoActionService.playNow(this)
            if (started) {
                updateUIState("STOP", false, false, false) // CUT button hide
                hidePanelUIForPlayback()
                checkPlaybackStateContinuously()
            }
        } else {
            unsavedGestures = emptyList()
            startRecording()
        }
    }

    private fun startRecording() {
        try { closeSettingsPanel() } catch (e: Exception) {}
        isRecording = true
        updateUIState("HIDE", true, false, true)

        val touchLayer = TouchCaptureView(this)
        val overlayType = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            android.view.WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = android.view.Gravity.TOP or android.view.Gravity.START

        if (!safeAddView(touchLayer, params, "Recording layer error")) {
            isRecording = false
            val hasOld = GestureStore.hasRecording(this)
            updateUIState(if (unsavedGestures.isNotEmpty()) "+ ADD" else if (hasOld) "PLAY" else "START", unsavedGestures.isNotEmpty(), hasOld, true)
            return
        }

        captureView = touchLayer
        bringPanelToFront()
        android.widget.Toast.makeText(this, "🟢 Glass ON! Wait aur Tap record honge.", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun saveRecording() {
        if (isRecording) {
            extractAndAppendGestures()
            isRecording = false
            safeRemoveView(captureView)
            captureView = null
        }
        
        glassHiddenAt = 0L // ✅ RESET FIX
        
        if (unsavedGestures.isEmpty()) {
            android.widget.Toast.makeText(this, "Koi recording nahi mili", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        GestureStore.save(this, unsavedGestures)
        unsavedGestures = emptyList()
        updateUIState("PLAY", false, true, true)
        android.widget.Toast.makeText(this, "✅ Poori Memory Save ho gayi!", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        isRecording = false
        safeRemoveView(captureView)
        captureView = null
        glassHiddenAt = 0L // ✅ RESET FIX
        
        if (unsavedGestures.isNotEmpty()) {
            android.widget.Toast.makeText(this, "⚠️ Unsaved multi-page steps cancel ho gaye", android.widget.Toast.LENGTH_SHORT).show()
        }
        unsavedGestures = emptyList()
        val hasOld = GestureStore.hasRecording(this)
        updateUIState(if (hasOld) "PLAY" else "START", false, hasOld, true)
    }

    private fun clearSavedRecordingFromPanel() {
        if (isRecording || AutoActionService.isPlaying()) {
            android.widget.Toast.makeText(this, "Abhi saaf nahi kar sakte", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        GestureStore.clear(this)
        unsavedGestures = emptyList()
        glassHiddenAt = 0L // ✅ RESET FIX
        
        updateUIState("START", false, false, true)
        android.widget.Toast.makeText(this, "🗑️ Purani memory clear ho gayi!", android.widget.Toast.LENGTH_SHORT).show()
    }

}

// BUG #1 FIX: ACTION_DOWN aur ACTION_UP hamesha save honge — long press skip nahi hoga



class TouchCaptureView(context: android.content.Context) : android.view.View(context) {
    private val recordedGestures = mutableListOf<RecordedGesture>()
    private val currentPoints = mutableListOf<GesturePoint>()
    
    private val recordingStartTime = android.os.SystemClock.uptimeMillis()
    private var currentGestureDownTime = 0L

    init {
        isClickable = true
        setBackgroundColor(android.graphics.Color.argb(45, 0, 200, 0)) // 🟢 Green Glass
    }
    
    // ✅ NEW FIX: For exact Gap calculation
    fun getRecordingStartTime(): Long {
        return recordingStartTime
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                currentPoints.clear()
                currentGestureDownTime = event.eventTime
                addPoint(event, forceAdd = true)
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                addPoint(event, forceAdd = false)
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                addPoint(event, forceAdd = true)
                saveCurrentGesture()
            }
        }
        return true
    }

    private fun addPoint(event: android.view.MotionEvent, forceAdd: Boolean = false) {
        val relativeTime = event.eventTime - currentGestureDownTime
        if (currentPoints.isNotEmpty() && !forceAdd) {
            val last = currentPoints.last()
            val samePlace = kotlin.math.abs(last.x - event.rawX) < 2f && kotlin.math.abs(last.y - event.rawY) < 2f
            val tooFast = relativeTime - last.t < 12
            if (samePlace && tooFast) return
        }
        currentPoints.add(GesturePoint(x = event.rawX, y = event.rawY, t = relativeTime))
    }

    private fun saveCurrentGesture() {
        if (currentPoints.isEmpty()) return
        val delayFromStart = currentGestureDownTime - recordingStartTime
        recordedGestures.add(RecordedGesture(delayFromStart = delayFromStart, points = currentPoints.toList()))
        currentPoints.clear()
    }

    fun getRecordedGestures(): List<RecordedGesture> {
        return recordedGestures.toList()
    }
}
