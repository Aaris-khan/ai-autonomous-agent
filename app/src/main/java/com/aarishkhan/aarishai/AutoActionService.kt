package com.aarishkhan.aarishai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max

class AutoActionService : AccessibilityService() {

    companion object {

        // AARISH_BLINK_STRIKE_AAS_BRIDGE_V2
        fun playSingleLiveGestureSafe(gesture: RecordedGesture, onDone: () -> Unit) {
            val svc = instance
            if (svc == null) {
                onDone()
                return
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (instance !== svc) {
                    onDone()
                } else {
                    svc.aarishBlinkStrikeDispatchGesture(gesture, onDone)
                }
            }
        }

        fun performLiveSystemActionSafe(actionType: Int, onDone: () -> Unit) {
            val svc = instance
            if (svc == null) {
                onDone()
                return
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                val ok = try {
                    when (actionType) {
                        1 -> svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                        2 -> svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
                        else -> false
                    }
                } catch (_: Throwable) {
                    false
                }

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    onDone()
                }, if (ok) 180L else 0L)
            }
        }


        private var instance: AutoActionService? = null

        // AARISH_ACCESSIBILITY_OVERLAY_SAFE_V13_BRIDGE
        fun getAarishAccessibilityWindowManager(): android.view.WindowManager? {
            val svc = instance ?: return null
            return try {
                svc.getSystemService(android.content.Context.WINDOW_SERVICE) as? android.view.WindowManager
            } catch (_: Throwable) {
                null
            }
        }

        fun isAarishAccessibilityServiceReady(): Boolean {
            return instance != null
        }

        fun playNow(context: Context): Boolean {
            val service = instance
            return if (service != null) {
                service.playRecordedGestures()
            } else {
                Toast.makeText(
                    context,
                    "Accessibility Service ready nahi hai",
                    Toast.LENGTH_LONG
                ).show()
                false
            }
        }

        fun playMasterNow(context: Context, ownerName: String): Boolean {
            val service = instance
            return if (service != null) {
                service.playRecordedGestures(masterOwner = ownerName)
            } else {
                Toast.makeText(
                    context,
                    "Accessibility Service ready nahi hai",
                    Toast.LENGTH_LONG
                ).show()
                false
            }
        }

        fun stopPlayback(context: Context): Boolean {
            val service = instance
            return if (service != null) {
                service.stopPlaybackInternal()
                true
            } else {
                Toast.makeText(
                    context,
                    "Accessibility Service ready nahi hai",
                    Toast.LENGTH_SHORT
                ).show()
                false
            }
        }

        fun isPlaying(): Boolean {
            return instance?.isPlayingInternal == true
        }


        // AARISH_MEMORY_SLOTS_V1_COMPANION
        private const val AARISH_MEMORY_PREF_V1 = "aarish_memory_slots_v1"

        private fun aarishMemorySlotKeyV1(slot: Int, permanent: Boolean): String {
            val safeSlot = slot.coerceIn(1, 6)
            return if (permanent) "perm_$safeSlot" else "temp_$safeSlot"
        }

        private fun aarishMemoryCleanV1(value: String?): String {
            return value.orEmpty()
                .replace("\u0000", " ")
                .replace("OCR_TEXT_CLICK", " ")
                .replace(Regex("(?i)^OCR\\s*:"), "")
                .replace(Regex("ocr_bounds=[^|\\n]+"), " ")
                .replace("|MAGNETIC:", "\n")
                .replace(Regex("[\\t\\r]+"), " ")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
                .take(20000)
        }

        private fun aarishMemorySnapshotTextV1(snapshot: TargetSnapshot?): String {
            val s = snapshot ?: return ""
            val parts = listOf(
                s.targetText,
                s.targetChildText,
                s.targetDesc,
                s.targetContextText,
                s.targetSiblingText
            )
                .map { aarishMemoryCleanV1(it) }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase(java.util.Locale.US) }

            return parts.joinToString("\n").trim().take(20000)
        }

        fun aarishMemorySaveSnapshotV1(
            context: Context,
            slot: Int,
            permanent: Boolean,
            snapshot: TargetSnapshot?
        ): Boolean {
            val text = aarishMemorySnapshotTextV1(snapshot)
            if (text.isBlank()) {
                Toast.makeText(context, "Memory text nahi mila", Toast.LENGTH_SHORT).show()
                return false
            }

            context.getSharedPreferences(AARISH_MEMORY_PREF_V1, Context.MODE_PRIVATE)
                .edit()
                .putString(aarishMemorySlotKeyV1(slot, permanent), text)
                .apply()

            Toast.makeText(
                context,
                if (permanent) "📌 Permanent memory saved" else "📥 M$slot saved",
                Toast.LENGTH_SHORT
            ).show()
            return true
        }

        fun aarishMemoryPasteNowV1(
            context: Context,
            slot: Int,
            permanent: Boolean,
            clearAfter: Boolean
        ): Boolean {
            val service = instance
            if (service == null) {
                Toast.makeText(context, "Accessibility Service ready nahi hai", Toast.LENGTH_SHORT).show()
                return false
            }

            service.handler.post {
                val success = service.aarishMemoryPasteFromSlotInternalV1(
                    slot = slot,
                    permanent = permanent,
                    clearAfter = clearAfter
                )
                Toast.makeText(
                    context,
                    if (success) "📤 Memory pasted" else "Input field ya memory empty hai",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return true
        }


        fun aarishMemoryStartForegroundClipboardCaptureV9(
            context: Context,
            slot: Int,
            permanent: Boolean = false
        ): Boolean {
            // AARISH_GHOST_CLIPBOARD_SCREEN_V10_COMPANION_INTENT
            val safeSlot = slot.coerceIn(1, 6)
            return try {
                val appContext = context.applicationContext
                val intent = android.content.Intent(appContext, MemoryClipboardCaptureActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NO_HISTORY)
                    putExtra(MemoryClipboardCaptureActivity.EXTRA_SLOT, safeSlot)
                    putExtra(MemoryClipboardCaptureActivity.EXTRA_PERMANENT, permanent)
                }
                appContext.startActivity(intent)
                true
            } catch (_: Throwable) {
                Toast.makeText(context, "Clipboard capture open nahi hua", Toast.LENGTH_SHORT).show()
                false
            }
        }


        // 🔥 Recording ke time button ki kundali nikalne ke liye

        // AARISH_OCR_TEXT_CLICK_V4_COMPANION
        fun captureOcrTextSnapshot(
            x: Int,
            y: Int,
            screenW: Float,
            screenH: Float,
            callback: (TargetSnapshot?) -> Unit
        ): Boolean {
            val service = instance ?: return false
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                service.aarishCaptureOcrTextSnapshotInternal(x, y, screenW, screenH, callback)
            }
            return true
        }

        fun captureTargetSnapshot(
            x: Int,
            y: Int,
            screenW: Float,
            screenH: Float
        ): TargetSnapshot? {
            return instance?.captureTargetSnapshotInternal(x, y, screenW, screenH)
        }

        // AARISH_PRESS_REPLAY_PRO_V2_START
        // AARISH_PRESS_REPLAY_PRO_V2_END
    }

    // AARISH_OCR_TEXT_CLICK_V4_FIELDS
    private data class AarishOcrBox(val text: String, val bounds: android.graphics.Rect)

    private val handler = Handler(Looper.getMainLooper())
    private val scheduledTasks = mutableListOf<Runnable>()

    @Volatile
    private var isPlayingInternal = false


    // AARISH_WAKE_LOCK_ENGINE_V2_FIELDS
    private var aarishCpuWakeLock: android.os.PowerManager.WakeLock? = null
    private var aarishScreenWakeLock: android.os.PowerManager.WakeLock? = null
    private val playbackRunId = java.util.concurrent.atomic.AtomicInteger(0)
    private val activeGestureCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val activeGestureTokenSeq = java.util.concurrent.atomic.AtomicInteger(0)
    private val activeGestureTokens = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()

    private fun isSamePlaybackRun(runId: Int): Boolean {
        return isPlayingInternal && playbackRunId.get() == runId
    }

    private fun isCurrentCallbackRun(runId: Int): Boolean {
        return playbackRunId.get() == runId
    }

    private var loopStartTime = 0L
    private var loopCurrentCount = 0
    private var initialConfigName = ""
    private var currentPlayingConfig = ""
    private val chainVisitedInRun = linkedSetOf<String>()
    private val configCycleCounters = mutableMapOf<String, Int>()

    // AARISH_MASTER_CHAIN_RUNTIME_FIELDS_V2
    private var masterWorkflowOwner = ""
    private var masterWorkflowSteps: List<GestureStore.MasterWorkflowStep> = emptyList()
    private var masterWorkflowIndex = 0
    private var activeMasterStepMode = "ONCE"
    private var activeMasterStepValue = 1
    private var activeMasterStepCycleCount = 0
    private var activeMasterStepStartTime = 0L

    // AARISH_WORKFLOW_PLAYLIST_RUNTIME_FIELDS_V4
    private var workflowOwner = ""
    private var workflowSequence: List<String> = emptyList()
    private var workflowIndex = 0
    private var isMasterPlaybackInternal = false


    // AARISH_WAKE_LOCK_ENGINE_V2_HELPERS
    @android.annotation.SuppressLint("WakelockTimeout")
    private fun acquirePlaybackWakeLocks() {
        try {
            val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager

            if (aarishCpuWakeLock?.isHeld != true) {
                aarishCpuWakeLock = pm.newWakeLock(
                    android.os.PowerManager.PARTIAL_WAKE_LOCK,
                    "AarishAI:PlaybackCpuLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }

            if (aarishScreenWakeLock?.isHeld != true) {
                @Suppress("DEPRECATION")
                val screenLockLevel =
                    android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP

                @Suppress("DEPRECATION")
                aarishScreenWakeLock = pm.newWakeLock(
                    screenLockLevel,
                    "AarishAI:PlaybackScreenLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (_: Exception) {
            // WakeLock fail hone par playback ko crash nahi karna.
        }
    }

    private fun releasePlaybackWakeLocks() {
        try {
            aarishScreenWakeLock?.let { lock ->
                if (lock.isHeld) lock.release()
            }
        } catch (_: Exception) {
        } finally {
            aarishScreenWakeLock = null
        }

        try {
            aarishCpuWakeLock?.let { lock ->
                if (lock.isHeld) lock.release()
            }
        } catch (_: Exception) {
        } finally {
            aarishCpuWakeLock = null
        }
    }



    // AARISH_PRESS_REPLAY_PRO_V2_START
    private fun dispatchLongPressChunksSafe(
        x: Float,
        y: Float,
        totalDuration: Long,
        runId: Int?,
        onDone: (Boolean) -> Unit
    ) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            onDone(false)
            return
        }

        val screenW = (resources.displayMetrics.widthPixels.toFloat() - 2f).coerceAtLeast(2f)
        val screenH = (resources.displayMetrics.heightPixels.toFloat() - 2f).coerceAtLeast(2f)
        val safeX = x.coerceIn(2f, screenW)
        val safeY = y.coerceIn(2f, screenH)
        // AARISH_LONG_PRESS_STOP_RESPONSIVE_V1:
        // Android O+ supports continueStroke(), so smaller chunks make STOP respond fast.
        // Android N keeps 59s chunks because continueStroke is not available there.
        val maxChunk = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) 3500L else 59000L
        var remaining = totalDuration.coerceIn(55L, 600000L)
        var previousStroke: GestureDescription.StrokeDescription? = null
        var finished = false
        var watchdog: Runnable? = null

        fun currentRunOk(): Boolean {
            return runId == null || isSamePlaybackRun(runId)
        }

        fun makePath(): Path {
            return Path().apply {
                moveTo(safeX, safeY)
                lineTo((safeX + 1.6f).coerceIn(2f, screenW), (safeY + 1.6f).coerceIn(2f, screenH))
            }
        }

        fun clearWatchdog() {
            watchdog?.let {
                handler.removeCallbacks(it)
                scheduledTasks.remove(it)
            }
            watchdog = null
        }

        fun finishOnce(ok: Boolean) {
            if (finished) return
            finished = true
            clearWatchdog()
            onDone(ok)
        }

        fun armWatchdog(ms: Long) {
            clearWatchdog()
            val task = Runnable {
                if (!finished) finishOnce(false)
            }
            watchdog = task
            if (runId != null) scheduledTasks.add(task)
            handler.postDelayed(task, (ms + 3200L).coerceAtMost(65000L))
        }

        fun dispatchNext() {
            if (finished) return
            if (!currentRunOk()) {
                finishOnce(false)
                return
            }

            val chunk = remaining.coerceAtMost(maxChunk).coerceAtLeast(55L)
            val moreAfterThis = remaining > chunk

            val stroke = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val path = makePath()
                    val prev = previousStroke
                    if (prev != null) {
                        prev.continueStroke(path, 0L, chunk, moreAfterThis)
                    } else {
                        GestureDescription.StrokeDescription(path, 0L, chunk, moreAfterThis)
                    }
                } else {
                    GestureDescription.StrokeDescription(makePath(), 0L, chunk)
                }
            } catch (_: Exception) {
                finishOnce(false)
                return
            }

            previousStroke = stroke

            val gesture = try {
                GestureDescription.Builder().addStroke(stroke).build()
            } catch (_: Exception) {
                finishOnce(false)
                return
            }

            armWatchdog(chunk)

            val accepted = dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        clearWatchdog()

                        if (!currentRunOk()) {
                            finishOnce(false)
                            return
                        }

                        remaining = (remaining - chunk).coerceAtLeast(0L)

                        if (remaining <= 0L || !moreAfterThis || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
                            finishOnce(true)
                        } else {
                            handler.post { dispatchNext() }
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        finishOnce(false)
                    }
                },
                null
            )

            if (!accepted) finishOnce(false)
        }

        dispatchNext()
    }

    private fun performLiveSystemActionInternal(actionType: Int, onComplete: () -> Unit) {
        handler.post {
            if (isPlayingInternal) {
                onComplete()
                return@post
            }

            val action = when (actionType) {
                1 -> GLOBAL_ACTION_BACK
                2 -> GLOBAL_ACTION_RECENTS
                else -> -1
            }

            if (action == -1) {
                onComplete()
                return@post
            }

            try {
                performGlobalAction(action)
            } catch (_: Exception) {
            }

            handler.postDelayed({ onComplete() }, 430L)
        }
    }

    private fun fireLiveReplaySafe(gesture: RecordedGesture, onComplete: () -> Unit) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            onComplete()
            return
        }

        handler.post {
            if (instance !== this@AutoActionService || isPlayingInternal) {
                onComplete()
                return@post
            }

            val raw = gesture.points
                .filter { !it.x.isNaN() && !it.x.isInfinite() && !it.y.isNaN() && !it.y.isInfinite() }
                .sortedBy { it.t.coerceAtLeast(0L) }
                .take(900)

            if (raw.isEmpty()) {
                onComplete()
                return@post
            }

            val firstRaw = raw.first()

            if (firstRaw.x <= -50f) {
                val systemAction = when (firstRaw.x.toInt()) {
                    -100 -> 1
                    -200 -> 2
                    else -> 0
                }

                if (systemAction > 0) {
                    performLiveSystemActionInternal(systemAction, onComplete)
                } else {
                    onComplete()
                }
                return@post
            }

            val screenW = (resources.displayMetrics.widthPixels.toFloat() - 2f).coerceAtLeast(2f)
            val screenH = (resources.displayMetrics.heightPixels.toFloat() - 2f).coerceAtLeast(2f)
            val baseT = raw.first().t.coerceAtLeast(0L)

            val points = raw.map { point ->
                point.copy(
                    x = point.x.coerceIn(2f, screenW),
                    y = point.y.coerceIn(2f, screenH),
                    t = (point.t.coerceAtLeast(0L) - baseT).coerceAtLeast(0L).coerceAtMost(600000L)
                )
            }

            val first = points.first()
            val path = Path()
            path.moveTo(first.x, first.y)

            var moved = false
            var lastT = 0L

            for (i in 1 until points.size) {
                val p = points[i]
                if (abs(p.x - first.x) > 8f || abs(p.y - first.y) > 8f) {
                    moved = true
                }
                lastT = max(lastT, p.t)
                path.lineTo(p.x, p.y)
            }

            if (!moved) {
                // AARISH_HUMAN_LIKE_LIVE_TAP_V1: real finger style firm tap micro-slip
                path.lineTo(
                    (first.x + 3.8f).coerceIn(2f, screenW),
                    (first.y + 3.8f).coerceIn(2f, screenH)
                )
            }

            val duration = (if (!moved) max(145L, lastT) else max(55L, lastT)).coerceAtMost(600000L) // AARISH_HUMAN_LIKE_LIVE_TAP_DURATION_V2

            if (!moved && duration <= 1200L && aarishTryFileManagerNodeRescueV1(first.x, first.y, null, "File picker clicked")) {
                // AARISH_FILE_MANAGER_RESCUE_LIVE_REPLAY_V1
                handler.post { onComplete() }
                return@post
            }

            if (!moved && duration > 59000L) {
                dispatchLongPressChunksSafe(first.x, first.y, duration, null) {
                    handler.post { onComplete() }
                }
                return@post
            }

            var finished = false
            var watchdog: Runnable? = null

            fun finishOnce() {
                if (finished) return
                finished = true
                watchdog?.let { handler.removeCallbacks(it) }
                handler.post { onComplete() }
            }

            try {
                val safeDuration = duration.coerceAtMost(59000L)
                val desc = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0L, safeDuration))
                    .build()

                watchdog = Runnable { finishOnce() }
                handler.postDelayed(watchdog!!, (safeDuration + 3200L).coerceAtMost(65000L))

                val accepted = dispatchGesture(
                    desc,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            super.onCompleted(gestureDescription)
                            finishOnce()
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            super.onCancelled(gestureDescription)
                            finishOnce()
                        }
                    },
                    null
                )

                if (!accepted) finishOnce()
            } catch (_: Exception) {
                finishOnce()
            }
        }
    }
    // AARIS        // AARISH_PRESS_REPLAY_PRO_V2_END

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        isPlayingInternal = false
        releasePlaybackWakeLocks()
        scheduledTasks.forEach { handler.removeCallbacks(it) }
        scheduledTasks.clear()
        handler.removeCallbacksAndMessages(null)
        resetActiveGestures()
        chainVisitedInRun.clear()
        configCycleCounters.clear()

        if (instance == this) {
            instance = null
        }

        super.onDestroy()
    }
    private fun playRecordedGestures(masterOwner: String? = null): Boolean {
        // AARISH_WORKFLOW_MASTER_CHAIN_PLAYBACK_ENGINE_V4
        val requestedOwner = (masterOwner ?: GestureStore.getActiveConfigName(this)).trim().ifBlank {
            GestureStore.getActiveConfigName(this)
        }

        stopPlaybackInternal(showToast = false)

        initialConfigName = requestedOwner
        masterWorkflowOwner = requestedOwner
        isMasterPlaybackInternal = !masterOwner.isNullOrBlank()
        masterWorkflowSteps = if (isMasterPlaybackInternal) {
            GestureStore.getMasterWorkflow(this, masterWorkflowOwner, 120)
        } else {
            emptyList()
        }

        masterWorkflowIndex = 0
        activeMasterStepCycleCount = 0
        activeMasterStepStartTime = android.os.SystemClock.elapsedRealtime()

        if (isMasterPlaybackInternal && masterWorkflowSteps.isEmpty()) {
            Toast.makeText(this, "Master Chain saved nahi hai", Toast.LENGTH_SHORT).show()
            return false
        }

        val firstOwner = if (isMasterPlaybackInternal) {
            val first = masterWorkflowSteps.first()
            activeMasterStepMode = first.mode
            activeMasterStepValue = first.value.coerceAtLeast(1)
            first.startConfig
        } else {
            requestedOwner
        }

        val firstSequence = GestureStore.getRunnableWorkflowSequence(this, firstOwner, 300)
        if (firstSequence.isEmpty()) {
            Toast.makeText(this, "Saved Screen Command nahi mila", Toast.LENGTH_SHORT).show()
            return false
        }

        workflowOwner = firstOwner
        workflowSequence = firstSequence
        workflowIndex = 0
        currentPlayingConfig = firstSequence.first()

        val gestures = GestureStore.loadConfig(this, currentPlayingConfig)
        if (gestures.isEmpty()) {
            Toast.makeText(this, "Empty config: $currentPlayingConfig", Toast.LENGTH_SHORT).show()
            return false
        }

        val runId = playbackRunId.incrementAndGet()
        resetActiveGestures()
        chainVisitedInRun.clear()
        configCycleCounters.clear()
        chainVisitedInRun.add(currentPlayingConfig)
        isPlayingInternal = true
        acquirePlaybackWakeLocks()

        loopCurrentCount = 0
        loopStartTime = android.os.SystemClock.elapsedRealtime()
        activeMasterStepStartTime = loopStartTime

        val startMsg = if (isMasterPlaybackInternal) {
            "Master Chain start: 1/${masterWorkflowSteps.size}"
        } else if (workflowSequence.size > 1) {
            "Playlist start: ${workflowSequence.size} steps"
        } else {
            "Workflow start: $currentPlayingConfig"
        }

        Toast.makeText(this, startMsg, Toast.LENGTH_SHORT).show()
        playSequence(gestures, runId)
        return true
    }

    private fun playSequence(gestures: List<RecordedGesture>, runId: Int) {
        // AARISH_WORKFLOW_MASTER_CHAIN_SEQUENCE_ENGINE_V4
        if (!isSamePlaybackRun(runId)) return

        val orderedGestures = gestures.sortedBy { it.delayFromStart }
        val sequenceStartTime = android.os.SystemClock.elapsedRealtime()

        fun cleanupDone(message: String) {
            if (!isCurrentCallbackRun(runId)) return

            isPlayingInternal = false
            releasePlaybackWakeLocks()
            scheduledTasks.forEach { handler.removeCallbacks(it) }
            scheduledTasks.clear()
            resetActiveGestures()
            chainVisitedInRun.clear()
            configCycleCounters.clear()

            masterWorkflowSteps = emptyList()
            masterWorkflowIndex = 0
            activeMasterStepCycleCount = 0
            workflowOwner = ""
            workflowSequence = emptyList()
            workflowIndex = 0
            isMasterPlaybackInternal = false

            Toast.makeText(this@AutoActionService, message, Toast.LENGTH_SHORT).show()
        }

        fun scheduleConfig(configName: String, delayMs: Long, label: String) {
            val nextGestures = GestureStore.loadConfig(this@AutoActionService, configName)
            if (nextGestures.isEmpty()) {
                cleanupDone("Empty config: $configName")
                return
            }

            currentPlayingConfig = configName
            chainVisitedInRun.add(configName)

            scheduledTasks.forEach { handler.removeCallbacks(it) }
            scheduledTasks.clear()

            val task = object : Runnable {
                override fun run() {
                    scheduledTasks.remove(this)
                    if (isSamePlaybackRun(runId)) {
                        Toast.makeText(this@AutoActionService, label, Toast.LENGTH_SHORT).show()
                        playSequence(nextGestures, runId)
                    }
                }
            }

            scheduledTasks.add(task)
            handler.postDelayed(task, delayMs.coerceAtLeast(0L))
        }

        fun modeShouldContinue(modeRaw: String, valueRaw: Int, completedCycles: Int, startTime: Long): Boolean {
            val mode = modeRaw.uppercase(java.util.Locale.US)
            val value = valueRaw.coerceAtLeast(1)

            return when (mode) {
                "COUNT" -> completedCycles < value
                "TIME" -> {
                    val elapsed = android.os.SystemClock.elapsedRealtime() - startTime
                    elapsed < value.toLong() * 60_000L
                }
                "INFINITE" -> true
                else -> false
            }
        }

        fun loadPlaylistOwner(ownerName: String): Boolean {
            val sequence = GestureStore.getRunnableWorkflowSequence(this@AutoActionService, ownerName, 300)
            if (sequence.isEmpty()) return false

            workflowOwner = ownerName
            workflowSequence = sequence
            workflowIndex = 0
            currentPlayingConfig = sequence.first()
            chainVisitedInRun.clear()
            configCycleCounters.clear()
            chainVisitedInRun.add(currentPlayingConfig)
            return true
        }

        fun repeatCurrentMasterStep() {
            val step = masterWorkflowSteps.getOrNull(masterWorkflowIndex)
            if (step == null) {
                cleanupDone("Master Chain complete")
                return
            }

            if (!loadPlaylistOwner(step.startConfig)) {
                cleanupDone("Empty chain: ${step.startConfig}")
                return
            }

            scheduleConfig(
                workflowSequence.first(),
                700L,
                "Repeat chain: ${step.startConfig}"
            )
        }

        fun moveToNextMasterStepOrFinish() {
            masterWorkflowIndex++
            val nextStep = masterWorkflowSteps.getOrNull(masterWorkflowIndex)

            if (nextStep == null) {
                cleanupDone("Master Chain complete")
                return
            }

            activeMasterStepMode = nextStep.mode
            activeMasterStepValue = nextStep.value.coerceAtLeast(1)
            activeMasterStepCycleCount = 0
            activeMasterStepStartTime = android.os.SystemClock.elapsedRealtime()

            if (!loadPlaylistOwner(nextStep.startConfig)) {
                cleanupDone("Empty chain: ${nextStep.startConfig}")
                return
            }

            scheduleConfig(
                workflowSequence.first(),
                700L,
                "Master ${masterWorkflowIndex + 1}/${masterWorkflowSteps.size}: ${nextStep.startConfig}"
            )
        }

        fun finishMasterStepOrMoveNext() {
            activeMasterStepCycleCount++

            val repeatThisStep = modeShouldContinue(
                activeMasterStepMode,
                activeMasterStepValue,
                activeMasterStepCycleCount,
                activeMasterStepStartTime
            )

            if (repeatThisStep) {
                repeatCurrentMasterStep()
            } else {
                moveToNextMasterStepOrFinish()
            }
        }

        fun finishSequence() {
            if (!isSamePlaybackRun(runId)) return

            if (workflowSequence.isNotEmpty() && workflowIndex + 1 < workflowSequence.size) {
                workflowIndex++
                val nextConfig = workflowSequence[workflowIndex]
                scheduleConfig(
                    nextConfig,
                    500L,
                    "Next ${workflowIndex + 1}/${workflowSequence.size}: $nextConfig"
                )
                return
            }

            if (isMasterPlaybackInternal) {
                finishMasterStepOrMoveNext()
                return
            }

            loopCurrentCount++

            val mode = GestureStore.getLoopModeForConfig(this@AutoActionService, workflowOwner.ifBlank { initialConfigName })
            val value = GestureStore.getLoopValueForConfig(this@AutoActionService, workflowOwner.ifBlank { initialConfigName }).coerceAtLeast(1)

            val shouldContinue = modeShouldContinue(
                mode,
                value,
                loopCurrentCount,
                loopStartTime
            )

            if (shouldContinue && isSamePlaybackRun(runId)) {
                workflowIndex = 0
                val first = workflowSequence.firstOrNull() ?: initialConfigName
                currentPlayingConfig = first
                chainVisitedInRun.clear()
                configCycleCounters.clear()
                chainVisitedInRun.add(first)
                scheduleConfig(first, 700L, "Playlist repeat")
            } else {
                cleanupDone("Workflow complete")
            }
        }

        fun nextRealGestureAfter(index: Int): RecordedGesture? {
            // AARISH_AI_WAIT_BUTTON_V1_NEXT_REAL
            for (i in (index + 1) until orderedGestures.size) {
                val candidate = orderedGestures[i]
                val first = candidate.points.firstOrNull() ?: continue
                if (first.x > -50f) return candidate
            }
            return null
        }

        fun scheduleIndex(index: Int) {
            if (!isSamePlaybackRun(runId)) return

            if (index >= orderedGestures.size) {
                val finishWaiter = object : Runnable {
                    override fun run() {
                        scheduledTasks.remove(this)
                        if (!isSamePlaybackRun(runId)) return

                        if (activeGestureCount.get() > 0) {
                            scheduledTasks.add(this)
                            handler.postDelayed(this, 150L)
                        } else {
                            finishSequence()
                        }
                    }
                }

                scheduledTasks.add(finishWaiter)
                handler.postDelayed(finishWaiter, 250L)
                return
            }

            val gesture = orderedGestures[index]
            val elapsed = android.os.SystemClock.elapsedRealtime() - sequenceStartTime
            val delay = (gesture.delayFromStart - elapsed).coerceAtLeast(0L)

            val task = object : Runnable {
                override fun run() {
                    scheduledTasks.remove(this)
                    if (!isSamePlaybackRun(runId)) return

                    if (activeGestureCount.get() > 0) {
                        scheduledTasks.add(this)
                        handler.postDelayed(this, 60L)
                        return
                    }

                    dispatchOneGesture(gesture, runId, nextRealGestureAfter(index))

                    val waitForGestureFinish = object : Runnable {
                        override fun run() {
                            scheduledTasks.remove(this)
                            if (!isSamePlaybackRun(runId)) return

                            if (activeGestureCount.get() > 0) {
                                scheduledTasks.add(this)
                                handler.postDelayed(this, 60L)
                            } else {
                                scheduleIndex(index + 1)
                            }
                        }
                    }

                    scheduledTasks.add(waitForGestureFinish)
                    handler.postDelayed(waitForGestureFinish, 60L)
                }
            }

            scheduledTasks.add(task)
            handler.postDelayed(task, delay)
        }

        scheduleIndex(0)
    }

    private fun cancelCurrentRunningGesture() {
        // Fake top-left cancel tap intentionally removed.
    }

private fun stopPlaybackInternal(showToast: Boolean = true) {
    isPlayingInternal = false
        releasePlaybackWakeLocks()
    playbackRunId.incrementAndGet()

    val oldTasks = scheduledTasks.toList()
    scheduledTasks.clear()
    oldTasks.forEach { task ->
        try { handler.removeCallbacks(task) } catch (_: Exception) {}
    }

    // AARISH_STALE_TASK_FIX_V1: handler ke sab callbacks mat kaato; sirf playback scheduledTasks remove karo.
    // Isse service ke future safe callbacks accidentally cancel nahi hote.
    resetActiveGestures()
    chainVisitedInRun.clear()
    configCycleCounters.clear()
    masterWorkflowSteps = emptyList()
    masterWorkflowIndex = 0
    activeMasterStepCycleCount = 0
    workflowOwner = ""
    workflowSequence = emptyList()
    workflowIndex = 0
    isMasterPlaybackInternal = false

    if (showToast) {
        Toast.makeText(this, "Screen Command stopped", Toast.LENGTH_SHORT).show()
    }
}

    private fun beginActiveGesture(): Int {
        val token = activeGestureTokenSeq.incrementAndGet()
        activeGestureTokens[token] = true
        activeGestureCount.incrementAndGet()
        return token
    }

    private fun finishActiveGesture(token: Int) {
        if (activeGestureTokens.remove(token) == null) return
        while (true) {
            val current = activeGestureCount.get()
            if (current <= 0) return
            if (activeGestureCount.compareAndSet(current, current - 1)) return
        }
    }

    private fun resetActiveGestures() {
        activeGestureTokens.clear()
        activeGestureCount.set(0)
    }

    private fun scheduleGestureWatchdog(runId: Int, timeoutMs: Long, token: Int): Runnable {
        val safeTimeout = timeoutMs.coerceIn(900L, 610000L)
        val task = object : Runnable {
            override fun run() {
                scheduledTasks.remove(this)
                if (isCurrentCallbackRun(runId)) {
                    finishActiveGesture(token)
                }
            }
        }
        scheduledTasks.add(task)
        handler.postDelayed(task, safeTimeout)
        return task
    }


private fun aarishAiWaitForNextRecordedTarget(
    runId: Int,
    token: Int,
    nextRecordedGesture: RecordedGesture?
) {
    // AARISH_AI_WAIT_OWN_LABEL_MAGNETIC_V8
    val startedAt = android.os.SystemClock.elapsedRealtime()
    val maxWaitMs = 20L * 60L * 1000L
    val pollMs = 1200L
    val restartAsMaster = isMasterPlaybackInternal
    val restartOwner = if (restartAsMaster && masterWorkflowOwner.isNotBlank()) {
        masterWorkflowOwner
    } else {
        workflowOwner.ifBlank { initialConfigName }.ifBlank { GestureStore.getActiveConfigName(this) }
    }

    var finished = false
    var currentTask: Runnable? = null
    var lastToastMs = 0L

    fun clearTask() {
        currentTask?.let {
            try { scheduledTasks.remove(it) } catch (_: Throwable) {}
            try { handler.removeCallbacks(it) } catch (_: Throwable) {}
        }
        currentTask = null
    }

    fun finishWait() {
        if (finished) return
        finished = true
        clearTask()
        if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
    }

    fun restartAfterTimeout() {
        if (finished) return
        finished = true
        clearTask()
        if (!isCurrentCallbackRun(runId)) {
            finishActiveGesture(token)
            return
        }

        showTinyToast("AI WAIT 20min → restart")
        finishActiveGesture(token)

        val owner = restartOwner.trim()
        val masterMode = restartAsMaster
        stopPlaybackInternal(showToast = false)

        handler.postDelayed({
            if (instance !== this@AutoActionService || owner.isBlank()) return@postDelayed
            if (masterMode) playRecordedGestures(masterOwner = owner)
            else {
                GestureStore.setActiveConfigName(this@AutoActionService, owner)
                playRecordedGestures()
            }
        }, 700L)
    }

    fun norm(v: String?): String = v.orEmpty()
        .lowercase(java.util.Locale.US)
        .replace(Regex("[^a-z0-9\\u0600-\\u06FF\\u0750-\\u077F\\u0900-\\u097F]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun useful(v: String): Boolean {
        val bad = setOf("", "view", "text", "button", "image", "layout", "android", "widget", "item")
        return v.length >= 2 && v !in bad
    }

    fun needles(g: RecordedGesture): List<String> = listOf(
        norm(g.targetText),
        norm(g.targetDesc),
        norm(g.targetId?.substringAfterLast("/")?.substringAfterLast(":")?.replace("_", " ")?.replace("-", " "))
    ).filter { useful(it) }.distinct()

    fun ownLabels(node: AccessibilityNodeInfo?): List<String> {
        val n = node ?: return emptyList()
        val action = try { findClickableParent(n) ?: n } catch (_: Throwable) { n }
        val out = mutableListOf<String>()

        fun add(x: AccessibilityNodeInfo?) {
            try { out.add(norm(safeText(x))) } catch (_: Throwable) {}
            try { out.add(norm(safeDesc(x))) } catch (_: Throwable) {}
            try { out.add(norm(safeId(x)?.substringAfterLast("/")?.substringAfterLast(":")?.replace("_", " ")?.replace("-", " "))) } catch (_: Throwable) {}
        }

        add(n)
        add(action)

        val childCount = try { action.childCount.coerceAtMost(6) } catch (_: Throwable) { 0 }
        for (i in 0 until childCount) {
            add(try { action.getChild(i) } catch (_: Throwable) { null })
        }

        return out.filter { it.isNotBlank() }.distinct()
    }

    fun smallTarget(node: AccessibilityNodeInfo?): Boolean {
        val n = try { findClickableParent(node) ?: node } catch (_: Throwable) { node } ?: return false
        if (!safeVisible(n) || !safeEnabled(n)) return false
        val b = Rect()
        if (!safeBounds(n, b) || b.width() <= 0 || b.height() <= 0) return false
        val area = (b.width().toFloat() * b.height().toFloat()) /
            (resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f) *
                resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f))
        return area <= 0.42f
    }

    fun wordHit(label: String, needle: String): Boolean {
        if (label == needle) return true
        if (needle.length <= 5) return label.split(" ").any { it == needle }
        return label.contains(needle) || try { tokenSimilarity(label, needle) >= 0.90f } catch (_: Throwable) { false }
    }

    fun hit(match: SmartMatch?, g: RecordedGesture): Boolean {
        val m = match ?: return false
        if (!smallTarget(m.node)) return false
        val ns = needles(g)
        if (ns.isEmpty()) return false
        return ownLabels(m.node).any { label -> ns.any { n -> wordHit(label, n) } }
    }

    fun targetReady(): Boolean {
        val g = nextRecordedGesture ?: return false
        if (hit(try { findExactActionButtonAcrossWindows(g) } catch (_: Throwable) { null }, g)) return true
        if (hit(try { findSelectorTargetAcrossWindows(g) } catch (_: Throwable) { null }, g)) return true
        if (hit(try { findBestSmartTarget(g) } catch (_: Throwable) { null }, g)) return true
        return false
    }

    val pollTask = object : Runnable {
        override fun run() {
            currentTask = this
            try { scheduledTasks.remove(this) } catch (_: Throwable) {}

            if (finished) return
            if (!isSamePlaybackRun(runId)) {
                finishWait()
                return
            }

            if (targetReady()) {
                showTinyToast("AI WAIT done → target")
                finishWait()
                return
            }

            val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
            if (elapsed >= maxWaitMs) {
                restartAfterTimeout()
                return
            }

            if (elapsed - lastToastMs >= 6000L) {
                showTinyToast("AI WAIT holding ${elapsed / 1000}s")
                lastToastMs = elapsed
            }

            scheduledTasks.add(this)
            handler.postDelayed(this, pollMs)
        }
    }

    currentTask = pollTask
    scheduledTasks.add(pollTask)
    handler.postDelayed(pollTask, pollMs)
    showTinyToast("⏳ AI WAIT 20min magnetic")
}








    // ==========================================================
    // 🧠 OFFLINE SMART DISPATCH v4: ID + text + desc + context + geometry + ambiguity guard
    // ==========================================================


    // AARISH_OCR_TEXT_CLICK_V4_HELPER
    private fun aarishNormOcr(raw: String?): String {
        // AARISH_OCR_MLKIT_CORE_SAFE_V23_FIX1
        // Single punctuation keyboard keys (.,!?@# etc.) ko strip mat karo.
        val s = raw.orEmpty().trim()
        if (s.isEmpty()) return ""
        if (s.length <= 3 && s.all { !it.isLetterOrDigit() && !it.isWhitespace() }) {
            return s
        }

        return s
            .lowercase(java.util.Locale.US)
            .replace(Regex("[^a-z0-9\\u0600-\\u06FF\\u0750-\\u077F\\u0900-\\u097F]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun aarishOcrCanUseScreenshot(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
    }

    private fun aarishBitmapFromScreenshot(
        screenshot: android.accessibilityservice.AccessibilityService.ScreenshotResult
    ): android.graphics.Bitmap? {
        val buffer = try {
            screenshot.hardwareBuffer
        } catch (_: Throwable) {
            return null
        }

        return try {
            val wrapped = android.graphics.Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
            val copy = wrapped?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
            try { wrapped?.recycle() } catch (_: Throwable) {}
            copy
        } catch (_: Throwable) {
            null
        } finally {
            try { buffer.close() } catch (_: Throwable) {}
        }
    }

    private fun aarishOcrBoxesFromResult(result: com.google.mlkit.vision.text.Text): List<AarishOcrBox> {
        // Block/Line ko OCR target mat banao. Sirf element-level word/key boxes use karo.
        // Isse keyboard row "A S D F" ek bada box ban kar Ctrl/Alt/V ko confuse nahi karegi.
        val out = mutableListOf<AarishOcrBox>()

        fun add(text: String?, box: android.graphics.Rect?) {
            val clean = text.orEmpty().trim()
            if (clean.isEmpty() || box == null || box.width() <= 0 || box.height() <= 0) return
            out.add(AarishOcrBox(clean, android.graphics.Rect(box)))
        }

        for (block in result.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    add(element.text, element.boundingBox)
                }
            }
        }

        return out.distinctBy { aarishNormOcr(it.text) + ":" + it.bounds.flattenToString() }
    }



    // Keyboard raw XY lock removed. OCR and Magnetic stay active.



    private fun aarishKeyboardRawMetaV35(g: RecordedGesture): Boolean = false







    private fun aarishPickOcrBoxForTap(
        boxes: List<AarishOcrBox>,
        x: Int,
        y: Int,
        screenW: Float,
        screenH: Float
    ): AarishOcrBox? {
        // AARISH_SMART_GATE_V36_OCR_PICK
        // Keyboard: direct touched box mile to hi OCR save, bagal ka Z/X/Enter-neighbor steal nahi.
        // Non-keyboard: OCR normal; Files/menu/icon ke liye Visual fallback ko block nahi karega.
        if (boxes.isEmpty()) return null

        val px = x.toFloat()
        val py = y.toFloat()
        val density = resources.displayMetrics.density.coerceAtLeast(1f)

        val imeTap = false

        val pad = if (imeTap) {
            (8f * density).toInt().coerceIn(4, 16)
        } else {
            (3f * density).toInt().coerceIn(2, 8)
        }

        fun contains(r: android.graphics.Rect): Boolean {
            return px >= r.left - pad &&
                px <= r.right + pad &&
                py >= r.top - pad &&
                py <= r.bottom + pad
        }

        fun edgeDistance(r: android.graphics.Rect): Float {
            val dx = kotlin.math.max(
                0f,
                kotlin.math.max(r.left.toFloat() - px, px - r.right.toFloat())
            )
            val dy = kotlin.math.max(
                0f,
                kotlin.math.max(r.top.toFloat() - py, py - r.bottom.toFloat())
            )
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }

        fun centerDistance(r: android.graphics.Rect): Float {
            val dx = r.exactCenterX() - px
            val dy = r.exactCenterY() - py
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }

        val direct = boxes
            .filter { contains(it.bounds) }
            .sortedWith(
                compareBy<AarishOcrBox> { edgeDistance(it.bounds) }
                    .thenBy { kotlin.math.abs(it.bounds.exactCenterX() - px) + kotlin.math.abs(it.bounds.exactCenterY() - py) }
                    .thenBy { it.bounds.width() * it.bounds.height() }
            )
            .firstOrNull()

        if (direct != null) return direct

        if (imeTap) return null

        val maxDist = (24f * density).coerceIn(10f, 38f)

        return boxes
            .map { box -> box to edgeDistance(box.bounds) }
            .filter { it.second <= maxDist }
            .sortedWith(
                compareBy<Pair<AarishOcrBox, Float>> { it.second }
                    .thenBy { it.first.bounds.width() * it.first.bounds.height() }
                    .thenBy { centerDistance(it.first.bounds) }
            )
            .firstOrNull()
            ?.first
    }

    private fun aarishOcrLooksLikeKeyboardPackage(pkg: String?): Boolean {
        return false
    }



    private fun aarishBestForegroundPackageForOcr(): String? {
        return try {
            val wins = try { windows } catch (_: Throwable) { null }
            if (wins != null) {
                for (w in wins) {
                    val pkg = try { w.root?.packageName?.toString() } catch (_: Throwable) { null }
                    if (!pkg.isNullOrBlank()) return pkg
                }
            }
            try { rootInActiveWindow?.packageName?.toString() } catch (_: Throwable) { null }
        } catch (_: Throwable) {
            null
        }
    }



    private fun aarishMakeOcrSnapshotFromBox(
        box: AarishOcrBox,
        x: Int,
        y: Int,
        screenW: Float,
        screenH: Float,
        allBoxes: List<AarishOcrBox> = emptyList()
    ): TargetSnapshot {
        // AARISH_SMART_GATE_V36_OCR_SNAPSHOT
        val b = box.bounds
        val sw = screenW.coerceAtLeast(1f)
        val sh = screenH.coerceAtLeast(1f)
        val clean = box.text.trim().take(160)
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val insideX = ((x - b.left).toFloat() / b.width().toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
        val insideY = ((y - b.top).toFloat() / b.height().toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
        val neighborProfile = aarishOcrNeighborProfile(box, allBoxes)

        val rawLockMeta = ""

        return TargetSnapshot(
            targetText = "OCR:$clean",
            targetDesc = "OCR_TEXT_TARGET",
            targetId = "ocr:${aarishNormOcr(clean).take(140)}",
            targetClass = "OCR_TEXT",
            targetPackage = aarishBestForegroundPackageForOcr(),
            targetContextText = "OCR_TEXT_CLICK text=$clean|$neighborProfile$rawLockMeta",
            targetChildText = clean,
            targetSiblingText = "ocr_bounds=${b.left},${b.top},${b.right},${b.bottom}|$neighborProfile$rawLockMeta",
            targetRoleFlags = "OCR_VISIBLE_TEXT",
            targetTreePath = "OCR",
            targetLeft = b.left,
            targetTop = b.top,
            targetRight = b.right,
            targetBottom = b.bottom,
            xPercent = (cx / sw).coerceIn(0f, 1f),
            yPercent = (cy / sh).coerceIn(0f, 1f),
            targetWPercent = (b.width() / sw).coerceIn(0f, 1f),
            targetHPercent = (b.height() / sh).coerceIn(0f, 1f),
            insideXPercent = insideX,
            insideYPercent = insideY,
            recordedScreenW = sw.toInt(),
            recordedScreenH = sh.toInt()
        )
    }

    private fun aarishProcessOcrBoxesFromBitmapV29(
        bitmap: android.graphics.Bitmap,
        onSuccess: (List<AarishOcrBox>) -> Unit,
        onFailure: () -> Unit
    ) {
        // AARISH_OCR_LATIN_DEVANAGARI_V29
        // English/Latin + Hindi/Devanagari OCR ko ek saath try karo.
        // Urdu/Arabic ke liye ML Kit bundled Arabic recognizer available nahi hai.
        val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)

        val recognizers = listOf(
            com.google.mlkit.vision.text.TextRecognition.getClient(
                com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
            ),
            com.google.mlkit.vision.text.TextRecognition.getClient(
                com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions.Builder().build()
            )
        )

        val allBoxes = mutableListOf<AarishOcrBox>()
        val doneCount = java.util.concurrent.atomic.AtomicInteger(0)
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)

        fun closeAll() {
            recognizers.forEach { recognizer ->
                try { recognizer.close() } catch (_: Throwable) {}
            }
        }

        fun finishOne() {
            if (doneCount.incrementAndGet() < recognizers.size) return
            if (!finished.compareAndSet(false, true)) return

            closeAll()

            val merged = allBoxes
                .filter { it.text.trim().isNotEmpty() && it.bounds.width() > 0 && it.bounds.height() > 0 }
                .distinctBy { aarishNormOcr(it.text) + ":" + it.bounds.flattenToString() }

            if (merged.isNotEmpty()) onSuccess(merged) else onFailure()
        }

        recognizers.forEach { recognizer ->
            try {
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        try { allBoxes.addAll(aarishOcrBoxesFromResult(result)) } catch (_: Throwable) {}
                        finishOne()
                    }
                    .addOnFailureListener {
                        finishOne()
                    }
            } catch (_: Throwable) {
                finishOne()
            }
        }
    }

    private fun aarishCaptureOcrTextSnapshotInternal(
        x: Int,
        y: Int,
        screenW: Float,
        screenH: Float,
        callback: (TargetSnapshot?) -> Unit
    ) {
        // AARISH_OCR_LATIN_DEVANAGARI_V29
        // Recording side: Latin + Devanagari OCR dono se word/key boxes nikalo.
        if (!aarishOcrCanUseScreenshot()) {
            showTinyToast("OCR: Android 11+ required")
            callback(null)
            return
        }

        val executor = java.util.concurrent.Executor { runnable -> handler.post(runnable) }
        val callbackDone = java.util.concurrent.atomic.AtomicBoolean(false)
        var timeoutTask: Runnable? = null

        fun safeCallback(snapshot: TargetSnapshot?) {
            if (callbackDone.compareAndSet(false, true)) {
                try { timeoutTask?.let { handler.removeCallbacks(it) } } catch (_: Throwable) {}
                callback(snapshot)
            }
        }

        timeoutTask = Runnable {
            safeCallback(null)
        }

        // AARISH_OCR_DUAL_TIMEOUT_SAFE_V30: dual Latin+Devanagari recognizer ko enough time do.
        handler.postDelayed(timeoutTask!!, 8000L)

        try {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                executor,
                object : android.accessibilityservice.AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(
                        screenshot: android.accessibilityservice.AccessibilityService.ScreenshotResult
                    ) {
                        val bitmap = aarishBitmapFromScreenshot(screenshot)
                        if (bitmap == null) {
                            safeCallback(null)
                            return
                        }

                        aarishProcessOcrBoxesFromBitmapV29(
                            bitmap = bitmap,
                            onSuccess = { boxes ->
                                val picked = aarishPickOcrBoxForTap(
                                    boxes,
                                    x,
                                    y,
                                    screenW,
                                    screenH
                                )
                                val ocrSnapshot = picked?.let { aarishMakeOcrSnapshotFromBox(it, x, y, screenW, screenH, boxes) }
                                safeCallback(ocrSnapshot)
                                try { bitmap.recycle() } catch (_: Throwable) {}
                            },
                            onFailure = {
                                safeCallback(null)
                                try { bitmap.recycle() } catch (_: Throwable) {}
                            }
                        )
                    }

                    override fun onFailure(errorCode: Int) {
                        safeCallback(null)
                    }
                }
            )
        } catch (_: Throwable) {
            safeCallback(null)
        }
    }



    // AARISH_CORE_ONLY_VISUAL_DISABLED
    // OpenCV-style bitmap/template visual logic removed. Magnetic + MLKit OCR + XY fallback remain.

    private data class AarishVisualMatch(
        val x: Float,
        val y: Float,
        val quality: Float
    )

    private fun aarishHasVisualTemplate(g: RecordedGesture): Boolean = false







    private fun aarishHasOcrIdentity(g: RecordedGesture): Boolean {
        return g.targetText.orEmpty().startsWith("OCR:") ||
            g.targetId.orEmpty().startsWith("ocr:") ||
            g.targetContextText.orEmpty().contains("OCR_TEXT_CLICK")
    }

    private fun aarishOcrNeedles(g: RecordedGesture): List<String> {
        // Single letter keys A/B/C/1/V ko replay ke time ignore mat karo.
        return listOf(
            g.targetText.orEmpty().removePrefix("OCR:"),
            g.targetChildText.orEmpty(),
            g.targetId.orEmpty().removePrefix("ocr:")
        ).map { aarishNormOcr(it) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(6)
    }

    private fun aarishOcrTextMatches(live: String, saved: String): Boolean {
        val a = aarishNormOcr(live)
        val b = aarishNormOcr(saved)
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        if (b.length <= 5) return a.split(" ").any { it == b }
        return a.contains(b) || b.contains(a)
    }

    private fun aarishOcrMetaValue(raw: String?, key: String): String {
        // AARISH_OCR_ROW_DELIMITER_SAFE_V24
        val prefix = "$key="
        return aarishOcrMetaDecode(
            raw.orEmpty()
                .split("|")
                .firstOrNull { it.startsWith(prefix) }
                ?.removePrefix(prefix)
                .orEmpty()
        )
    }

    private fun aarishOcrMetaToken(raw: String?): String {
        // AARISH_OCR_ROW_DELIMITER_SAFE_V24
        // Neighbor/meta values me comma, pipe aur unit-separator safely preserve karo.
        return aarishNormOcr(raw)
            .take(60)
            .replace("%", "%25")
            .replace("|", "%7C")
            .replace(",", "%2C")
            .replace("\u241F", "%1F")
    }

    private fun aarishOcrMetaDecode(raw: String): String {
        // AARISH_OCR_ROW_DELIMITER_SAFE_V24
        return raw
            .replace("%1F", "\u241F")
            .replace("%2C", ",")
            .replace("%7C", "|")
            .replace("%25", "%")
    }

    private fun aarishOcrRowParts(raw: String): Set<String> {
        // AARISH_OCR_ROW_DELIMITER_SAFE_V24
        // New rows use \u241F. Old saved rows used comma, so fallback support rakha.
        val parts = if (raw.contains("\u241F")) {
            raw.split("\u241F")
        } else {
            raw.split(",")
        }

        return parts
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun aarishSameBox(a: AarishOcrBox, b: AarishOcrBox): Boolean {
        return a.text == b.text && a.bounds.flattenToString() == b.bounds.flattenToString()
    }

    private fun aarishOcrNeighborProfile(box: AarishOcrBox, boxes: List<AarishOcrBox>): String {
        if (boxes.isEmpty()) {
            return "ocr_left=|ocr_right=|ocr_up=|ocr_down=|ocr_row="
        }

        val b = box.bounds
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val rowTol = kotlin.math.max(8f * density, b.height().toFloat() * 0.75f)
        val colTol = kotlin.math.max(10f * density, b.width().toFloat() * 0.85f)

        fun clean(v: String?): String = aarishOcrMetaToken(v)

        val others = boxes.filterNot { aarishSameBox(it, box) }

        val sameRow = others
            .filter { kotlin.math.abs(it.bounds.exactCenterY() - b.exactCenterY()) <= rowTol }
            .sortedBy { it.bounds.left }

        val left = sameRow
            .filter { it.bounds.exactCenterX() < b.exactCenterX() }
            .maxByOrNull { it.bounds.right }
            ?.text

        val right = sameRow
            .filter { it.bounds.exactCenterX() > b.exactCenterX() }
            .minByOrNull { it.bounds.left }
            ?.text

        val sameCol = others
            .filter { kotlin.math.abs(it.bounds.exactCenterX() - b.exactCenterX()) <= colTol }

        val up = sameCol
            .filter { it.bounds.exactCenterY() < b.exactCenterY() }
            .maxByOrNull { it.bounds.bottom }
            ?.text

        val down = sameCol
            .filter { it.bounds.exactCenterY() > b.exactCenterY() }
            .minByOrNull { it.bounds.top }
            ?.text

        val rowWithSelf = (sameRow + box).sortedBy { it.bounds.left }
        val selfIndex = rowWithSelf.indexOfFirst { aarishSameBox(it, box) }
        val rowSignature = if (selfIndex >= 0) {
            val start = (selfIndex - 2).coerceAtLeast(0)
            val end = (selfIndex + 3).coerceAtMost(rowWithSelf.size)
            rowWithSelf.subList(start, end)
                .joinToString("\u241F") { clean(it.text) }
                .take(180)
        } else {
            ""
        }

        return "ocr_left=${clean(left)}|ocr_right=${clean(right)}|ocr_up=${clean(up)}|ocr_down=${clean(down)}|ocr_row=$rowSignature"
    }

    private fun aarishOcrNeighborScore(savedRaw: String?, liveBox: AarishOcrBox, liveBoxes: List<AarishOcrBox>): Int {
        val saved = savedRaw.orEmpty()
        if (!saved.contains("ocr_left=") && !saved.contains("ocr_row=")) return 0

        val live = aarishOcrNeighborProfile(liveBox, liveBoxes)
        var score = 0

        fun addExactScore(key: String, points: Int) {
            val s = aarishOcrMetaValue(saved, key)
            val l = aarishOcrMetaValue(live, key)
            if (s.isNotEmpty() && l.isNotEmpty() && s == l) score += points
        }

        addExactScore("ocr_left", 4)
        addExactScore("ocr_right", 4)
        addExactScore("ocr_up", 2)
        addExactScore("ocr_down", 2)

        val savedRow = aarishOcrMetaValue(saved, "ocr_row")
        val liveRow = aarishOcrMetaValue(live, "ocr_row")

        if (savedRow.isNotEmpty() && liveRow.isNotEmpty()) {
            if (savedRow == liveRow) {
                score += 8
            } else {
                val savedSet = aarishOcrRowParts(savedRow)
                val liveSet = aarishOcrRowParts(liveRow)
                score += savedSet.intersect(liveSet).size
            }
        }

        return score
    }

    private fun aarishKeyboardDynamicTopPercent(g: RecordedGesture): Float {
        return 0f
    }



    private fun aarishIsKeyboardLikeOcrGesture(g: RecordedGesture): Boolean {
        return false
    }



    private fun aarishFindOcrPlaybackBox(g: RecordedGesture, boxes: List<AarishOcrBox>): AarishOcrBox? {
        val needles = aarishOcrNeedles(g)
            .map { aarishNormOcr(it) }
            .filter { it.isNotBlank() }
            .distinct()

        if (needles.isEmpty() || boxes.isEmpty()) return null

        val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
        val expectedX = if (hasSavedPercentAnchor(g)) g.xPercent.coerceIn(0f, 1f) * screenW else -1f
        val expectedY = if (hasSavedPercentAnchor(g)) g.yPercent.coerceIn(0f, 1f) * screenH else -1f

        val candidates = boxes.filter { box ->
            val live = aarishNormOcr(box.text)
            needles.any { n -> aarishOcrTextMatches(live, n) }
        }

        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        fun textStrength(box: AarishOcrBox): Int {
            val live = aarishNormOcr(box.text)
            var best = 0
            for (n in needles) {
                when {
                    live == n -> best = maxOf(best, 1000)
                    live.split(" ").any { it == n } -> best = maxOf(best, 900)
                    aarishOcrTextMatches(live, n) -> best = maxOf(best, 760)
                }
            }
            return best
        }

        fun distance(box: AarishOcrBox): Float {
            return if (expectedX >= 0f && expectedY >= 0f) {
                kotlin.math.abs(box.bounds.exactCenterX() - expectedX) +
                    kotlin.math.abs(box.bounds.exactCenterY() - expectedY)
            } else {
                0f
            }
        }

        return candidates
            .sortedWith(
                compareByDescending<AarishOcrBox> { box -> textStrength(box) }
                    .thenByDescending { box -> aarishOcrNeighborScore(g.targetSiblingText, box, boxes) }
                    .thenBy { box -> box.bounds.width() * box.bounds.height() }
                    .thenBy { box -> distance(box) }
            )
            .firstOrNull()
    }



    private fun aarishDispatchTapWithToken(
        x: Float,
        y: Float,
        runId: Int,
        token: Int,
        label: String,
        tapDurationMs: Long = 95L,
        postGapMs: Long = 70L
    ) {
        // AARISH_OCR_MLKIT_CORE_SAFE_V23_FIX4
        // Keyboard tap stable bhi rahe aur 230ms/key hard slow-down bhi na ho.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
            return
        }

        val safeTapDuration = tapDurationMs.coerceIn(75L, 140L)
        val safePostGap = postGapMs.coerceIn(45L, 140L)

        val sw = (resources.displayMetrics.widthPixels.toFloat() - 2f).coerceAtLeast(2f)
        val sh = (resources.displayMetrics.heightPixels.toFloat() - 2f).coerceAtLeast(2f)
        val sx = x.coerceIn(2f, sw)
        val sy = y.coerceIn(2f, sh)

        aarishShowVisualClickIndicator(sx, sy) // AARISH_PREMIUM_CLICK_RIPPLE_TAP_TOKEN_V1
        if (aarishTryFileManagerNodeRescueV1(sx, sy, runId, "File picker clicked")) {
            // AARISH_FILE_MANAGER_RESCUE_TAP_TOKEN_V1
            handler.postDelayed({
                if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
            }, safePostGap)
            return
        }

        val path = android.graphics.Path().apply {
            moveTo(sx, sy)
            lineTo((sx + 1.2f).coerceIn(2f, sw), (sy + 1.2f).coerceIn(2f, sh))
        }

        var watchdog: Runnable? = null
        var finished = false

        fun finishStable() {
            if (finished) return
            finished = true
            watchdog?.let {
                handler.removeCallbacks(it)
                scheduledTasks.remove(it)
            }

            handler.postDelayed({
                if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
            }, safePostGap)
        }

        try {
            val tap = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, safeTapDuration))
                .build()

            watchdog = scheduleGestureWatchdog(runId, safeTapDuration + safePostGap + 2600L, token)

            val ok = dispatchGesture(
                tap,
                object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        finishStable()
                    }

                    override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        finishStable()
                    }
                },
                null
            )

            if (ok) showTinyToast(label)
            if (!ok) finishStable()
        } catch (_: Throwable) {
            finishStable()
        }
    }

    private fun aarishFallbackTapForOcrMiss(g: RecordedGesture, runId: Int, token: Int) {
        // AARISH_CORE_ONLY_OCR_MISS_FALLBACK
        // OCR miss -> Magnetic rescue -> XY fallback. Keyboard raw lock and visual rescue removed.
        val first = g.points.firstOrNull()
        if (first == null) {
            if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
            return
        }

        val sw = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val sh = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

        val x = if (hasSavedPercentAnchor(g)) g.xPercent.coerceIn(0f, 1f) * sw else first.x
        val y = if (hasSavedPercentAnchor(g)) g.yPercent.coerceIn(0f, 1f) * sh else first.y

        val hasRealMagnetic =
            (!g.targetId.isNullOrBlank() && !g.targetId!!.startsWith("ocr:")) ||
                (!g.targetDesc.isNullOrBlank() && g.targetDesc != "OCR_TEXT_TARGET") ||
                (!g.targetClass.isNullOrBlank() && g.targetClass != "OCR_TEXT") ||
                (!g.targetTreePath.isNullOrBlank() && g.targetTreePath != "OCR") ||
                (!g.targetRoleFlags.isNullOrBlank() && g.targetRoleFlags != "OCR_VISIBLE_TEXT")

        if (hasRealMagnetic && isSamePlaybackRun(runId)) {
            val match = try { findBestSmartTarget(g) } catch (_: Throwable) { null }
            if (match != null && match.bounds.width() > 0 && match.bounds.height() > 0) {
                val tapX = match.bounds.exactCenterX().coerceIn(2f, (sw - 2f).coerceAtLeast(2f))
                val tapY = match.bounds.exactCenterY().coerceIn(2f, (sh - 2f).coerceAtLeast(2f))
                aarishDispatchTapWithToken(tapX, tapY, runId, token, "Magnet rescue")
                return
            }
        }

        aarishDispatchTapWithToken(
            x.coerceIn(2f, (sw - 2f).coerceAtLeast(2f)),
            y.coerceIn(2f, (sh - 2f).coerceAtLeast(2f)),
            runId,
            token,
            "XY fallback"
        )
    }



    private fun tryOcrTextTargetTap(recordedGesture: RecordedGesture, runId: Int): Boolean {
        // AARISH_OCR_LATIN_DEVANAGARI_V29
        // Playback side: Latin + Devanagari OCR dono se saved word/key dhoondo.
        if (!isSamePlaybackRun(runId)) return true
        if (!aarishHasOcrIdentity(recordedGesture)) return false
        if (!aarishOcrCanUseScreenshot()) return false

        val token = beginActiveGesture()
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        val executor = java.util.concurrent.Executor { runnable -> handler.post(runnable) }
        var watchdog: Runnable? = null

        fun finishWithFallback() {
            if (!done.compareAndSet(false, true)) return
            watchdog?.let {
                handler.removeCallbacks(it)
                scheduledTasks.remove(it)
            }
            aarishFallbackTapForOcrMiss(recordedGesture, runId, token)
        }

        fun finishWithBox(box: AarishOcrBox?) {
            if (!done.compareAndSet(false, true)) return
            watchdog?.let {
                handler.removeCallbacks(it)
                scheduledTasks.remove(it)
            }

            if (box == null) {
                aarishFallbackTapForOcrMiss(recordedGesture, runId, token)
            } else {
                val x = box.bounds.left + recordedGesture.insideXPercent.coerceIn(0f, 1f) * box.bounds.width()
                val y = box.bounds.top + recordedGesture.insideYPercent.coerceIn(0f, 1f) * box.bounds.height()
                val traceText = recordedGesture.targetChildText.orEmpty()
                    .ifBlank { recordedGesture.targetText.orEmpty().removePrefix("OCR:") }
                    .replace("\n", " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(24)
                val traceLabel = if (traceText.isNotBlank()) "OCR click: $traceText" else "OCR click"
                aarishDispatchTapWithToken(x, y, runId, token, traceLabel)
            }
        }

        watchdog = object : Runnable {
            override fun run() {
                scheduledTasks.remove(this)
                if (isSamePlaybackRun(runId)) finishWithFallback()
            }
        }

        scheduledTasks.add(watchdog!!)
        // AARISH_OCR_DUAL_TIMEOUT_SAFE_V30: playback OCR dual recognizer ke liye safe wait.
        handler.postDelayed(watchdog!!, 5500L)

        try {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                executor,
                object : android.accessibilityservice.AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(
                        screenshot: android.accessibilityservice.AccessibilityService.ScreenshotResult
                    ) {
                        val bitmap = aarishBitmapFromScreenshot(screenshot)
                        if (bitmap == null) {
                            finishWithFallback()
                            return
                        }

                        aarishProcessOcrBoxesFromBitmapV29(
                            bitmap = bitmap,
                            onSuccess = { boxes ->
                                finishWithBox(aarishFindOcrPlaybackBox(recordedGesture, boxes))
                                try { bitmap.recycle() } catch (_: Throwable) {}
                            },
                            onFailure = {
                                finishWithFallback()
                                try { bitmap.recycle() } catch (_: Throwable) {}
                            }
                        )
                    }

                    override fun onFailure(errorCode: Int) {
                        finishWithFallback()
                    }
                }
            )
        } catch (_: Throwable) {
            finishWithFallback()
        }

        return true
    }




    private fun aarishMemorySlotKeyV1(slot: Int, permanent: Boolean): String {
        val safeSlot = slot.coerceIn(1, 6)
        return if (permanent) "perm_$safeSlot" else "temp_$safeSlot"
    }

    private fun aarishMemoryPrefsV1(): android.content.SharedPreferences {
        return getSharedPreferences("aarish_memory_slots_v1", android.content.Context.MODE_PRIVATE)
    }

    private fun aarishMemoryCleanTextV1(value: String?): String {
        return value.orEmpty()
            .replace("\u0000", " ")
            .replace("OCR_TEXT_CLICK", " ")
            .replace(Regex("(?i)^OCR\\s*:"), "")
            .replace(Regex("ocr_bounds=[^|\\n]+"), " ")
            .replace("|MAGNETIC:", "\n")
            .replace(Regex("[\\t\\r]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            .take(20000)
    }

    private fun aarishMemoryTextFromGestureFallbackV1(gesture: RecordedGesture): String {
        return listOf(
            gesture.targetText,
            gesture.targetChildText,
            gesture.targetDesc,
            gesture.targetContextText,
            gesture.targetSiblingText
        )
            .map { aarishMemoryCleanTextV1(it) }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(java.util.Locale.US) }
            .joinToString("\n")
            .trim()
            .take(20000)
    }

    private fun aarishMemoryReadNodeDeepTextV1(root: AccessibilityNodeInfo?): String {
        // AARISH_MEMORY_SLOTS_V1_ENGINE
        if (root == null) return ""

        val out = linkedSetOf<String>()
        val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var scanned = 0

        while (!stack.isEmpty() && scanned < 1200) {
            scanned++
            val node = stack.removeLast()

            val t = aarishMemoryCleanTextV1(safeText(node)?.toString())
            val d = aarishMemoryCleanTextV1(safeDesc(node)?.toString())
            if (t.length >= 2) out.add(t)
            if (d.length >= 2) out.add(d)

            val count = safeChildCount(node)
            for (i in 0 until count) {
                val child = safeChild(node, i)
                if (child != null) stack.add(child)
            }
        }

        return out.joinToString("\n").trim().take(20000)
    }

    private fun aarishMemoryRootsV1(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()

        try {
            val active = rootInActiveWindow
            if (active != null) roots.add(active)
        } catch (_: Throwable) {}

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (w in windows) {
                    val root = try { w.root } catch (_: Throwable) { null }
                    if (root != null) roots.add(root)
                }
            } catch (_: Throwable) {}
        }

        return roots.distinctBy { node ->
            val b = Rect()
            if (safeBounds(node, b)) "${b.left},${b.top},${b.right},${b.bottom}" else node.hashCode().toString()
        }
    }

    private fun aarishIsEditableNodeV1(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val cls = safeClass(node).orEmpty().lowercase(java.util.Locale.US)
        val editable = try { node.isEditable } catch (_: Throwable) { false }
        val focused = try { node.isFocused } catch (_: Throwable) { false }
        return safeVisible(node) &&
            safeEnabled(node) &&
            (editable || cls.contains("edittext") || cls.contains("textfield") || cls.contains("textinput")) &&
            (focused || editable || cls.contains("edit"))
    }

    private fun aarishFindFocusedEditableNodeV1(): AccessibilityNodeInfo? {
        for (root in aarishMemoryRootsV1()) {
            val focus = try { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } catch (_: Throwable) { null }
            if (aarishIsEditableNodeV1(focus)) return focus

            var parent = focus
            var guard = 0
            while (parent != null && guard < 8) {
                if (aarishIsEditableNodeV1(parent)) return parent
                parent = safeParent(parent)
                guard++
            }
        }

        for (root in aarishMemoryRootsV1()) {
            val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
            stack.add(root)
            var scanned = 0

            while (!stack.isEmpty() && scanned < 1600) {
                scanned++
                val node = stack.removeLast()
                if (aarishIsEditableNodeV1(node)) return node

                val count = safeChildCount(node)
                for (i in 0 until count) {
                    val child = safeChild(node, i)
                    if (child != null) stack.add(child)
                }
            }
        }

        return null
    }

    private fun aarishMemoryPasteFromSlotInternalV1(
        slot: Int,
        permanent: Boolean,
        clearAfter: Boolean
    ): Boolean {
        val key = aarishMemorySlotKeyV1(slot, permanent)
        val memoryText = aarishMemoryPrefsV1().getString(key, "").orEmpty().trim()
        if (memoryText.isBlank()) return false

        val node = aarishFindFocusedEditableNodeV1() ?: return false
        val oldText = safeText(node)?.toString().orEmpty()
        val finalText = if (oldText.isBlank()) {
            memoryText
        } else {
            oldText.trimEnd() + "\n" + memoryText
        }.take(50000)

        val args = android.os.Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                finalText
            )
        }

        val ok = try {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (_: Throwable) {
            false
        }

        if (ok && clearAfter) {
            aarishMemoryPrefsV1().edit().remove(key).apply()
        }

        return ok
    }

    private fun aarishMemoryFinishVirtualStepV1(runId: Int, token: Int, delayMs: Long = 140L) {
        val done = object : Runnable {
            override fun run() {
                scheduledTasks.remove(this)
                if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
            }
        }
        scheduledTasks.add(done)
        handler.postDelayed(done, delayMs.coerceIn(0L, 1000L))
    }


    private fun aarishMemoryReadClipboardTextV6(): String {
        // AARISH_MEMORY_CLIPBOARD_TWOSTEP_V6_AAS_HELPER
        return try {
            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = cm?.primaryClip ?: return ""
            if (clip.itemCount <= 0) return ""
            aarishMemoryCleanTextV1(clip.getItemAt(0)?.coerceToText(this)?.toString())
        } catch (_: Throwable) {
            ""
        }
    }

    private fun aarishMemoryClearClipboardV6() {
        try {
            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("AarishAI", ""))
        } catch (_: Throwable) {
        }
    }



    private fun aarishMemoryIsValidClipboardTextV7(value: String?): Boolean {
        // AARISH_MEMORY_CLIPBOARD_V7_AAS_GUARD
        val t = value.orEmpty().trim()
        if (t.isBlank()) return false
        if (t == "AarishAI") return false
        if (t.equals("Copied", ignoreCase = true)) return false
        if (t.equals("Copy", ignoreCase = true)) return false
        return true
    }

    private fun aarishMemorySaveClipboardToSlotV7(slotRaw: Int, textRaw: String): Boolean {
        // AARISH_MEMORY_CLIPBOARD_V7_AAS_SAVE
        val slot = slotRaw.coerceIn(1, 6)
        val text = aarishMemoryCleanTextV1(textRaw)
        if (!aarishMemoryIsValidClipboardTextV7(text)) return false

        aarishMemoryPrefsV1()
            .edit()
            .putString(aarishMemorySlotKeyV1(slot, false), text)
            .apply()

        showTinyToast("📥 M$slot clipboard saved")
        return true
    }

    private fun aarishMemoryWaitClipboardToSlotV7(
        slotRaw: Int,
        runId: Int,
        token: Int,
        startMs: Long,
        attempt: Int = 0
    ) {
        // AARISH_MEMORY_CLIPBOARD_V7_AAS_WAIT
        if (!isSamePlaybackRun(runId)) return

        val slot = slotRaw.coerceIn(1, 6)
        val text = aarishMemoryReadClipboardTextV6()

        if (aarishMemorySaveClipboardToSlotV7(slot, text)) {
            aarishMemoryFinishVirtualStepV1(runId, token, 120L)
            return
        }

        val elapsed = android.os.SystemClock.uptimeMillis() - startMs
        if (elapsed >= 8000L || attempt >= 40) {
            showTinyToast("M$slot clipboard empty")
            aarishMemoryFinishVirtualStepV1(runId, token, 80L)
            return
        }

        val retry = object : Runnable {
            override fun run() {
                scheduledTasks.remove(this)
                if (isSamePlaybackRun(runId)) {
                    aarishMemoryWaitClipboardToSlotV7(slot, runId, token, startMs, attempt + 1)
                }
            }
        }
        scheduledTasks.add(retry)
        handler.postDelayed(retry, 200L)
    }



    private fun aarishMemoryIsValidForegroundClipV9(value: String?): Boolean {
        // AARISH_MEMORY_FOREGROUND_CLIPBOARD_V9_HELPER
        val t = aarishMemoryCleanTextV1(value)
        if (t.isBlank()) return false
        if (t == "AarishAI") return false
        if (t.equals("Copied", ignoreCase = true)) return false
        if (t.equals("Copy", ignoreCase = true)) return false
        if (t.equals("Paste", ignoreCase = true)) return false
        return true
    }

    private fun aarishMemoryStartCaptureActivityForPlaybackV9(slotRaw: Int): Boolean {
        // AARISH_GHOST_CLIPBOARD_SCREEN_V10_PLAYBACK_INTENT
        val slot = slotRaw.coerceIn(1, 6)
        return try {
            aarishMemoryPrefsV1()
                .edit()
                .remove(aarishMemorySlotKeyV1(slot, false))
                .putLong("last_capture_v9_ms", 0L)
                .putInt("last_capture_v9_slot", slot)
                .putBoolean("last_capture_v9_ok", false)
                .apply()

            val intent = android.content.Intent(this, MemoryClipboardCaptureActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NO_HISTORY)
                putExtra(MemoryClipboardCaptureActivity.EXTRA_SLOT, slot)
                putExtra(MemoryClipboardCaptureActivity.EXTRA_PERMANENT, false)
            }
            startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }


    private fun aarishMemoryWaitForegroundCaptureV9(
        slotRaw: Int,
        runId: Int,
        token: Int,
        startMs: Long,
        attempt: Int = 0
    ) {
        // AARISH_MEMORY_FOREGROUND_CLIPBOARD_V9_WAIT
        if (!isSamePlaybackRun(runId)) return

        val slot = slotRaw.coerceIn(1, 6)
        val key = aarishMemorySlotKeyV1(slot, false)
        val text = aarishMemoryPrefsV1().getString(key, "").orEmpty()

        if (aarishMemoryIsValidForegroundClipV9(text)) {
            showTinyToast("📥 M$slot saved")
            aarishMemoryFinishVirtualStepV1(runId, token, 140L)
            return
        }

        val elapsed = android.os.SystemClock.uptimeMillis() - startMs
        if (elapsed >= 3500L || attempt >= 24) {
            showTinyToast("M$slot clipboard empty")
            aarishMemoryFinishVirtualStepV1(runId, token, 100L)
            return
        }

        val retry = object : Runnable {
            override fun run() {
                scheduledTasks.remove(this)
                if (isSamePlaybackRun(runId)) {
                    aarishMemoryWaitForegroundCaptureV9(slot, runId, token, startMs, attempt + 1)
                }
            }
        }
        scheduledTasks.add(retry)
        handler.postDelayed(retry, 150L)
    }


    private fun aarishHandleMemoryVirtualStepV1(
        recordedGesture: RecordedGesture,
        runId: Int,
        token: Int
    ): Boolean {
        // AARISH_MEMORY_FOREGROUND_CLIPBOARD_V9_DISPATCH
        val code = recordedGesture.points.firstOrNull()?.x?.toInt() ?: return false

        val prepareClipboardSlot = if (code in -6006..-6001) (-6000 - code).coerceIn(1, 6) else 0
        val clipboardCopySlot = if (code in -6106..-6101) (-6100 - code).coerceIn(1, 6) else 0
        val oldNodeCopySlot = if (code in -5106..-5101) (-5100 - code).coerceIn(1, 6) else 0
        val pasteTempSlot = when {
            code in -6206..-6201 -> (-6200 - code).coerceIn(1, 6)
            code in -5206..-5201 -> (-5200 - code).coerceIn(1, 6)
            else -> 0
        }
        val pastePermSlot = if (code == -5401) 1 else 0

        if (prepareClipboardSlot == 0 && clipboardCopySlot == 0 && oldNodeCopySlot == 0 && pasteTempSlot == 0 && pastePermSlot == 0) return false
        if (!isSamePlaybackRun(runId)) return true

        if (prepareClipboardSlot > 0) {
            // V9 reverse flow me prepare step no-op hai. Clipboard clear nahi karna.
            aarishMemoryFinishVirtualStepV1(runId, token, 30L)
            return true
        }

        if (clipboardCopySlot > 0) {
            val opened = aarishMemoryStartCaptureActivityForPlaybackV9(clipboardCopySlot)
            if (!opened) {
                showTinyToast("Clipboard capture failed")
                aarishMemoryFinishVirtualStepV1(runId, token, 80L)
                return true
            }

            aarishMemoryWaitForegroundCaptureV9(
                slotRaw = clipboardCopySlot,
                runId = runId,
                token = token,
                startMs = android.os.SystemClock.uptimeMillis()
            )
            return true
        }

        if (oldNodeCopySlot > 0) {
            val match = try { findBestSmartTarget(recordedGesture) } catch (_: Throwable) { null }
            val liveText = aarishMemoryReadNodeDeepTextV1(match?.node)
            val fallback = aarishMemoryTextFromGestureFallbackV1(recordedGesture)
            val text = liveText.ifBlank { fallback }

            if (text.isNotBlank()) {
                aarishMemoryPrefsV1()
                    .edit()
                    .putString(aarishMemorySlotKeyV1(oldNodeCopySlot, false), text)
                    .apply()
                showTinyToast("📥 M$oldNodeCopySlot saved")
            } else {
                showTinyToast("M$oldNodeCopySlot text missing")
            }

            aarishMemoryFinishVirtualStepV1(runId, token, 120L)
            return true
        }

        if (pasteTempSlot > 0) {
            val ok = aarishMemoryPasteFromSlotInternalV1(
                slot = pasteTempSlot,
                permanent = false,
                clearAfter = false
            )
            showTinyToast(if (ok) "📤 M$pasteTempSlot pasted" else "M$pasteTempSlot empty/input missing")
            aarishMemoryFinishVirtualStepV1(runId, token, if (ok) 220L else 80L)
            return true
        }

        if (pastePermSlot > 0) {
            val ok = aarishMemoryPasteFromSlotInternalV1(
                slot = pastePermSlot,
                permanent = true,
                clearAfter = false
            )
            showTinyToast(if (ok) "📌 pasted" else "Permanent empty/input missing")
            aarishMemoryFinishVirtualStepV1(runId, token, if (ok) 220L else 80L)
            return true
        }

        return false
    }



    private fun aarishIsForceXyOnlyGesture(g: RecordedGesture): Boolean {
        // AARISH_FORCE_XY_ONLY_PLAYBACK_V1
        val flags = listOf(
            g.targetRoleFlags.orEmpty(),
            g.targetContextText.orEmpty(),
            g.targetTreePath.orEmpty()
        ).joinToString(" ").uppercase(java.util.Locale.US)

        return flags.contains("AARISH_FORCE_XY_ONLY") || flags.contains("XY_ONLY")
    }

    private fun dispatchOneGesture(recordedGesture: RecordedGesture, runId: Int, nextRecordedGesture: RecordedGesture? = null) {
        if (!isSamePlaybackRun(runId)) return

        val points = recordedGesture.points
        if (points.isEmpty()) return

        val firstPoint = points.first()

        if (firstPoint.x <= -50f) {
            val token = beginActiveGesture()

            if (aarishHandleMemoryVirtualStepV1(recordedGesture, runId, token)) {
                // AARISH_MEMORY_SLOTS_V1_DISPATCH
                return
            }

            // AARISH_AI_WAIT_BUTTON_V1_VIRTUAL_STEP
            if (firstPoint.x.toInt() == -400) {
                aarishAiWaitForNextRecordedTarget(runId, token, nextRecordedGesture)
                return
            }


            // AARISH_PREMIUM_DESKTOP_APP_PLAYBACK_V4_FINAL
            if (firstPoint.x.toInt() == -300) {
                val pkg = recordedGesture.targetPackage?.trim().orEmpty()
                val targetId = recordedGesture.targetId?.trim().orEmpty()
                val prefix = "app:$pkg/"
                val activityName = if (targetId.startsWith(prefix)) {
                    targetId.removePrefix(prefix).takeIf { it.isNotBlank() }
                } else {
                    null
                }

                val meta = listOf(
                    recordedGesture.targetText.orEmpty(),
                    recordedGesture.targetDesc.orEmpty(),
                    recordedGesture.targetContextText.orEmpty()
                ).joinToString(" ").uppercase(java.util.Locale.US)

                val mode = if (meta.contains("FLOAT")) "FLOATING" else "FULLSCREEN"
                val ok = aarishDesktopV4LaunchApp(pkg, activityName.orEmpty(), mode)

                if (!ok) {
                    if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
                    showTinyToast("App launch fail")
                    return
                }

                val done = object : Runnable {
                    override fun run() {
                        scheduledTasks.remove(this)
                        if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
                    }
                }

                scheduledTasks.add(done)
                handler.postDelayed(done, if (mode == "FLOATING") 700L else 450L)

                showTinyToast(if (mode == "FLOATING") "🪟 Floating App" else "📱 App Front")
                return
            }

            val ok = when (firstPoint.x.toInt()) {
                -100 -> performGlobalAction(GLOBAL_ACTION_BACK)
                -200 -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                else -> false
            }

            if (!ok) {
                if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
                showTinyToast("System action fail hua")
                return
            }

            val systemDoneTask = object : Runnable {
                override fun run() {
                    scheduledTasks.remove(this)
                    if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
                }
            }
            scheduledTasks.add(systemDoneTask)
            handler.postDelayed(systemDoneTask, 450L)
            return
        }

        if (aarishIsForceXyOnlyGesture(recordedGesture)) {
            // AARISH_FORCE_XY_ONLY_DIRECT_DISPATCH_V1
            showTinyToast("XY playback")
            performGestureAt(firstPoint.x, firstPoint.y, points, runId, recordedGesture)
            return
        }


        val movement = hasRealMovement(points)
        val duration = kotlin.math.max(50L, points.maxOf { it.t.coerceAtLeast(0L) }).coerceAtMost(600000L)

        if (!movement && duration <= 1200L && aarishTryFilesWordNodeClickV2(recordedGesture, runId, "Files click")) {
            // AARISH_UNIVERSAL_FILES_WORD_DISPATCH_V2
            return
        }

        // AARISH_HYBRID_SIMULTAN_V33_PLAYBACK_ORDER
        // Normal text tap ke liye priority: OCR -> Magnetic rescue -> XY last.
        // tryOcrTextTargetTap() ke andar OCR miss par aarishFallbackTapForOcrMiss()
        // chalega, jisme magnetic rescue add kiya gaya hai.
        if (!movement && aarishHasOcrIdentity(recordedGesture)) {
            if (tryOcrTextTargetTap(recordedGesture, runId)) return
        }

        val match = if (!movement || hasStrongSavedIdentity(recordedGesture) || hasSavedPercentAnchor(recordedGesture)) {
            findBestSmartTarget(recordedGesture)
        } else {
            null
        }

        // AARISH_OCR_TEXT_CLICK_V4_PLAYBACK_HOOK
        // Accessibility/magnetic fail hone par OCR se same recorded text dhoondo.
        if (!movement && match == null && aarishHasOcrIdentity(recordedGesture)) {
            if (tryOcrTextTargetTap(recordedGesture, runId)) return
        }

        // AARISH_LATE_TARGET_RETRY_HOOK_V1: target slow ho to raw fallback/strict skip se pehle smart retry.
        if (!movement && match == null &&
            (hasStrongSavedIdentity(recordedGesture) || hasSavedPercentAnchor(recordedGesture) || aarishHasAnyRichIdentity(recordedGesture))
        ) {
            if (trySmartTargetAfterShortSettle(recordedGesture, runId)) return
        }

        if (!movement &&
            match == null &&
            hasStrongSavedIdentity(recordedGesture) &&
            smartTapAccuracyMode() == "STRICT"
        ) {
            showTinyToast("Strict: target missing, tap skip")
            return
        }

        val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

        val orientationMismatch =
            recordedGesture.recordedScreenW > 0 &&
                recordedGesture.recordedScreenH > 0 &&
                ((recordedGesture.recordedScreenW > recordedGesture.recordedScreenH) != (screenW > screenH))

        if (orientationMismatch && (movement || match == null)) {
            showTinyToast("Orientation badal gayi, gesture skip")
            return
        }

        val fallbackX = if (hasSavedPercentAnchor(recordedGesture)) {
            recordedGesture.xPercent.coerceIn(0f, 1f) * screenW
        } else {
            firstPoint.x
        }.coerceIn(2f, (screenW - 2f).coerceAtLeast(2f))

        val fallbackY = if (hasSavedPercentAnchor(recordedGesture)) {
            recordedGesture.yPercent.coerceIn(0f, 1f) * screenH
        } else {
            firstPoint.y
        }.coerceIn(2f, (screenH - 2f).coerceAtLeast(2f))

        val startX = if (match != null && match.bounds.width() > 0) {
            match.bounds.left + (recordedGesture.insideXPercent.coerceIn(0f, 1f) * match.bounds.width())
        } else {
            fallbackX
        }.coerceIn(2f, (screenW - 2f).coerceAtLeast(2f))

        val startY = if (match != null && match.bounds.height() > 0) {
            match.bounds.top + (recordedGesture.insideYPercent.coerceIn(0f, 1f) * match.bounds.height())
        } else {
            fallbackY
        }.coerceIn(2f, (screenH - 2f).coerceAtLeast(2f))

        if (!movement && duration < 450L && match != null) {
            if (performSmartNodeClick(match, recordedGesture, runId)) {
                return
            }
        }

        if (!movement && duration >= 450L) {
            if (match != null && !shouldUseSelectionLongPress(match, recordedGesture)) {
                performGestureAt(startX, startY, points, runId, recordedGesture)
            } else {
                performHybridSelectionRetry(
                    startX = startX,
                    startY = startY,
                    points = points,
                    duration = duration,
                    runId = runId
                )
            }
            return
        }

        showTinyToast(if (match != null) "Magnet gesture" else "XY playback") // AARISH_ENGINE_TRACE_TEXT_TOAST_V25B
        performGestureAt(startX, startY, points, runId, recordedGesture)
    }

    private data class SmartMatch(
        val node: AccessibilityNodeInfo,
        val bounds: Rect,
        val score: Int
    ) {
        val centerX: Float get() = bounds.exactCenterX()
        val centerY: Float get() = bounds.exactCenterY()
    }


    // AARISH_TAP_ACCURACY_MODE_V1: user-selectable offline tap targeting strictness.

    // AARISH_LATE_TARGET_RETRY_V1: slow/dynamic UI par raw coordinate fallback se pehle short smart retry.
    
    // AARISH_PREMIUM_DESKTOP_APP_LAUNCHER_V4_FINAL
    private fun aarishDesktopV4LaunchApp(
        pkg: String,
        activity: String,
        mode: String
    ): Boolean {
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

            val prefs = getSharedPreferences("aarish_desktop_launcher", android.content.Context.MODE_PRIVATE)
            val seq = ((prefs.getInt("float_seq", 0) + 1) % 5)
            prefs.edit().putInt("float_seq", seq).apply()

            val w = (sw * 0.72f).toInt().coerceAtLeast(260)
            val h = (sh * 0.58f).toInt().coerceAtLeast(320)
            val left = ((sw * 0.06f).toInt() + seq * 28).coerceAtMost((sw - 260).coerceAtLeast(0))
            val top = ((sh * 0.08f).toInt() + seq * 36).coerceAtMost((sh - 320).coerceAtLeast(0))

            return android.graphics.Rect(left, top, (left + w).coerceAtMost(sw), (top + h).coerceAtMost(sh))
        }

        fun optionsBundle(): android.os.Bundle? {
            if (android.os.Build.VERSION.SDK_INT < 16) return null

            val opts = android.app.ActivityOptions.makeCustomAnimation(this@AutoActionService, 0, 0)

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

        fun makeIntent(): android.content.Intent? {
            return if (cleanActivity.isNotBlank()) {
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
                }
            }
        }

        fun bringExistingTaskToFront(): Boolean {
            val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
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

        // Desktop/taskbar feel: old task alive hai to fresh launch mat karo.
        if (bringExistingTaskToFront()) return true

        return try {
            val intent = makeIntent() ?: return false
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


private fun trySmartTargetAfterShortSettle(
        recordedGesture: RecordedGesture,
        runId: Int,
        waitMs: Long = 10_000L
    ): Boolean {
        // AARISH_RESTORE_10S_SMART_ICON_WAIT_V1
        // Target/icon late aaye to raw/skip se pehle 10s tak poll karo.
        // Jaise hi accessibility tree me target mile, turant smart click.
        if (!isSamePlaybackRun(runId)) return false

        val points = recordedGesture.points
        if (points.isEmpty()) return false

        val shouldWait = hasStrongSavedIdentity(recordedGesture) ||
            hasSavedPercentAnchor(recordedGesture) ||
            aarishHasAnyRichIdentity(recordedGesture)

        if (!shouldWait) return false

        val token = beginActiveGesture()
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val maxWait = waitMs.coerceIn(1_000L, 15_000L)
        val pollEvery = 160L

        var finished = false
        var currentTask: Runnable? = null

        fun finishOnce() {
            if (finished) return
            finished = true
            currentTask?.let {
                try { scheduledTasks.remove(it) } catch (_: Throwable) {}
                try { handler.removeCallbacks(it) } catch (_: Throwable) {}
            }
            if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
        }

        fun tryClickNow(): Boolean {
            if (!isSamePlaybackRun(runId)) return true

            val orderedPoints = recordedGesture.points
                .filter { !it.x.isNaN() && !it.x.isInfinite() && !it.y.isNaN() && !it.y.isInfinite() }
                .sortedBy { it.t.coerceAtLeast(0L) }

            if (orderedPoints.isEmpty()) return true

            val movement = hasRealMovement(orderedPoints)
            val duration = kotlin.math.max(50L, orderedPoints.maxOf { it.t.coerceAtLeast(0L) })
                .coerceAtMost(600000L)

            val retryMatch = if (!movement || hasStrongSavedIdentity(recordedGesture) || hasSavedPercentAnchor(recordedGesture)) {
                findBestSmartTarget(recordedGesture)
            } else {
                null
            }

            if (retryMatch == null) return false

            val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
            val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

            val orientationMismatch =
                recordedGesture.recordedScreenW > 0 &&
                    recordedGesture.recordedScreenH > 0 &&
                    ((recordedGesture.recordedScreenW > recordedGesture.recordedScreenH) != (screenW > screenH))

            if (orientationMismatch && movement) {
                showTinyToast("Orientation badal gayi, gesture skip")
                return true
            }

            val firstPoint = orderedPoints.first()

            val fallbackX = if (hasSavedPercentAnchor(recordedGesture)) {
                recordedGesture.xPercent.coerceIn(0f, 1f) * screenW
            } else {
                firstPoint.x
            }.coerceIn(2f, (screenW - 2f).coerceAtLeast(2f))

            val fallbackY = if (hasSavedPercentAnchor(recordedGesture)) {
                recordedGesture.yPercent.coerceIn(0f, 1f) * screenH
            } else {
                firstPoint.y
            }.coerceIn(2f, (screenH - 2f).coerceAtLeast(2f))

            val startX = if (retryMatch.bounds.width() > 0) {
                retryMatch.bounds.left + (recordedGesture.insideXPercent.coerceIn(0f, 1f) * retryMatch.bounds.width())
            } else {
                fallbackX
            }.coerceIn(2f, (screenW - 2f).coerceAtLeast(2f))

            val startY = if (retryMatch.bounds.height() > 0) {
                retryMatch.bounds.top + (recordedGesture.insideYPercent.coerceIn(0f, 1f) * retryMatch.bounds.height())
            } else {
                fallbackY
            }.coerceIn(2f, (screenH - 2f).coerceAtLeast(2f))

            if (!movement && duration < 450L) {
                if (performSmartNodeClick(retryMatch, recordedGesture, runId)) return true
                performGestureAt(startX, startY, orderedPoints, runId, recordedGesture)
                return true
            }

            if (!movement && duration >= 450L) {
                if (!shouldUseSelectionLongPress(retryMatch, recordedGesture)) {
                    performGestureAt(startX, startY, orderedPoints, runId, recordedGesture)
                } else {
                    performHybridSelectionRetry(
                        startX = startX,
                        startY = startY,
                        points = orderedPoints,
                        duration = duration,
                        runId = runId
                    )
                }
                return true
            }

            performGestureAt(startX, startY, orderedPoints, runId, recordedGesture)
            return true
        }

        val pollTask = object : Runnable {
            override fun run() {
                currentTask = this
                try { scheduledTasks.remove(this) } catch (_: Throwable) {}

                if (finished) return

                if (!isSamePlaybackRun(runId)) {
                    finishOnce()
                    return
                }

                val clickedOrDone = try {
                    tryClickNow()
                } catch (_: Throwable) {
                    false
                }

                if (clickedOrDone) {
                    finishOnce()
                    return
                }

                val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
                if (elapsed >= maxWait) {
                    showTinyToast("Target 10s me nahi mila")
                    finishOnce()
                    return
                }

                scheduledTasks.add(this)
                handler.postDelayed(this, pollEvery)
            }
        }

        currentTask = pollTask
        scheduledTasks.add(pollTask)
        handler.postDelayed(pollTask, 120L)
        return true
    }


    private fun smartTapAccuracyMode(): String = GestureStore.getTapAccuracyMode(this)

    private fun tuneSmartThreshold(base: Int): Int {
        return when (smartTapAccuracyMode()) {
            "STRICT" -> base + 16
            "RELAXED" -> base - 14
            else -> base
        }.coerceIn(38, 520)
    }

    private fun smartScoreMargin(base: Int): Int {
        return when (smartTapAccuracyMode()) {
            "STRICT" -> base + 8
            "RELAXED" -> base - 6
            else -> base
        }.coerceAtLeast(4)
    }

    private fun smartNodeClickMinConfidence(): Float {
        return when (smartTapAccuracyMode()) {
            "STRICT" -> 0.78f
            "RELAXED" -> 0.60f
            else -> 0.70f
        }
    }

    private fun smartPreferSemanticConfidence(): Float {
        return when (smartTapAccuracyMode()) {
            "STRICT" -> 0.84f
            "RELAXED" -> 0.68f
            else -> 0.78f
        }
    }

    private fun smartClearAmbiguityGap(): Int {
        return when (smartTapAccuracyMode()) {
            "STRICT" -> 42
            "RELAXED" -> 20
            else -> 30
        }
    }

    private fun smartWeakAmbiguityGap(): Int {
        return when (smartTapAccuracyMode()) {
            "STRICT" -> 32
            "RELAXED" -> 16
            else -> 24
        }
    }

    private fun smartFinalAmbiguityGap(): Int {
        return when (smartTapAccuracyMode()) {
            "STRICT" -> 20
            "RELAXED" -> 9
            else -> 14
        }
    }

    private fun smartGeometryClearGap(): Int {
        return when (smartTapAccuracyMode()) {
            "STRICT" -> 34
            "RELAXED" -> 16
            else -> 24
        }
    }



    // AARISH_GEOMETRY_FALLBACK_V7: shifted-icon / no-text button resolver
    private fun geometryMatchThreshold(gesture: RecordedGesture): Int {
        val hasShape = gesture.targetWPercent > 0f && gesture.targetHPercent > 0f
        val hasTree = !gesture.targetTreePath.isNullOrBlank()
        val hasRole = !gesture.targetRoleFlags.isNullOrBlank()
        val hasClass = !gesture.targetClass.isNullOrBlank()

        if (hasStrongSavedIdentity(gesture)) return tuneSmartThreshold(70)
        return tuneSmartThreshold(
            when {
                hasTree && hasShape && hasSavedPercentAnchor(gesture) -> 72
                hasTree && hasSavedPercentAnchor(gesture) -> 82
                hasShape && hasClass && hasSavedPercentAnchor(gesture) -> 88
                hasShape && hasRole && hasSavedPercentAnchor(gesture) -> 94
                hasSavedPercentAnchor(gesture) && (hasShape || hasRole || hasClass) -> 104
                else -> 112
            }
        )
    }

    private fun isGeometryTrusted(match: SmartMatch, gesture: RecordedGesture): Boolean {
        if (!hasSavedPercentAnchor(gesture)) return false
        if (match.score >= geometryMatchThreshold(gesture) + 6) return true
        val strongGeometry =
            (!gesture.targetTreePath.isNullOrBlank() && gesture.targetWPercent > 0f && gesture.targetHPercent > 0f) ||
                (!gesture.targetClass.isNullOrBlank() && gesture.targetWPercent > 0f && gesture.targetHPercent > 0f)
        return strongGeometry && match.score >= geometryMatchThreshold(gesture)
    }

    private fun findGeometrySmartTarget(gesture: RecordedGesture): SmartMatch? {
        if (!hasSavedPercentAnchor(gesture)) return null

        val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
        val fallbackX = (gesture.xPercent.coerceIn(0f, 1f) * screenW).toInt().coerceIn(1, screenW.toInt().coerceAtLeast(1))
        val fallbackY = (gesture.yPercent.coerceIn(0f, 1f) * screenH).toInt().coerceIn(1, screenH.toInt().coerceAtLeast(1))
        val roots = collectSmartSearchRoots(fallbackX, fallbackY)
        if (roots.isEmpty()) return null

        var best: SmartMatch? = null
        var second: SmartMatch? = null
        val seen = hashSetOf<String>()

        fun remember(candidate: SmartMatch) {
            val oldBest = best
            if (oldBest == null || candidate.score > oldBest.score ||
                (candidate.score == oldBest.score && candidate.bounds.width() * candidate.bounds.height() < oldBest.bounds.width() * oldBest.bounds.height())
            ) {
                second = oldBest
                best = candidate
                return
            }

            val oldSecond = second
            if (oldSecond == null || candidate.score > oldSecond.score ||
                (candidate.score == oldSecond.score && candidate.bounds.width() * candidate.bounds.height() < oldSecond.bounds.width() * oldSecond.bounds.height())
            ) {
                second = candidate
            }
        }

        for (root in roots) {
            val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
            stack.add(root)
            var visited = 0

            while (!stack.isEmpty() && visited < 5200) {
                visited++
                val node = stack.removeLast()

                val count = safeChildCount(node)
                for (i in count - 1 downTo 0) {
                    val child = safeChild(node, i)
                    if (child != null) stack.add(child)
                }

                if (!safeVisible(node) || !safeEnabled(node)) continue
                val action = findClickableParent(node) ?: node
                if (!safeVisible(action) || !safeEnabled(action)) continue

                val bounds = Rect()
                if (!safeBounds(action, bounds) || bounds.width() <= 0 || bounds.height() <= 0) continue

                val key = aarishBoundsKey(bounds) + ":" + safeClass(action).orEmpty()
                if (!seen.add(key)) continue

                val score = scoreGeometryTarget(node, action, bounds, gesture)
                if (score <= 0) continue
                remember(SmartMatch(action, Rect(bounds), score))
            }
        }

        val finalBest = best ?: return null
        if (finalBest.score < geometryMatchThreshold(gesture)) return null

        val runner = second
        if (runner != null && isAmbiguousGeometryMatch(finalBest, runner, gesture)) return null

        return finalBest
    }

    private fun scoreGeometryTarget(
        node: AccessibilityNodeInfo,
        action: AccessibilityNodeInfo,
        bounds: Rect,
        gesture: RecordedGesture
    ): Int {
        return try {
            if (!hasSavedPercentAnchor(gesture)) return 0
            if (!safeVisible(action) || !safeEnabled(action)) return 0
            if (bounds.width() < 8 || bounds.height() < 8) return 0

            val areaRatio = aarishAreaRatio(bounds)
            if (areaRatio > 0.86f && !hasStrongSavedIdentity(gesture)) return 0

            val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
            val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
            var score = 0

            val savedClass = gesture.targetClass?.substringAfterLast('.')?.lowercase().orEmpty()
            val nodeClass = safeClass(node)?.substringAfterLast('.')?.lowercase().orEmpty()
            val actionClass = safeClass(action)?.substringAfterLast('.')?.lowercase().orEmpty()

            if (savedClass.isNotBlank()) {
                when {
                    savedClass == actionClass || savedClass == nodeClass -> score += 34
                    savedClass.contains("button") && (actionClass.contains("button") || nodeClass.contains("button")) -> score += 24
                    savedClass.contains("image") && (actionClass.contains("image") || nodeClass.contains("image")) -> score += 18
                    savedClass.contains("text") && (actionClass.contains("text") || nodeClass.contains("text")) -> score += 12
                    savedClass.contains("view") && (actionClass.contains("view") || nodeClass.contains("view")) -> score += 8
                }
            }

            val roleSim = roleSimilarity(gesture.targetRoleFlags, roleFlagsOf(action))
            if (roleSim >= 0.82f) score += 28
            else if (roleSim >= 0.58f) score += 14

            val dna = dnaSimilarity(gesture.targetTreePath, extractTreePathDNA(action))
            if (dna >= 0.94f) score += 64
            else if (dna >= 0.76f) score += 38
            else if (dna >= 0.54f) score += 16

            if (gesture.targetWPercent > 0f && gesture.targetHPercent > 0f) {
                val wDiff = kotlin.math.abs((bounds.width() / screenW) - gesture.targetWPercent.coerceIn(0f, 1f))
                val hDiff = kotlin.math.abs((bounds.height() / screenH) - gesture.targetHPercent.coerceIn(0f, 1f))
                when {
                    wDiff < 0.030f && hDiff < 0.030f -> score += 46
                    wDiff < 0.075f && hDiff < 0.075f -> score += 30
                    wDiff < 0.145f && hDiff < 0.145f -> score += 14
                    wDiff > 0.30f || hDiff > 0.30f -> score -= 24
                }
            }

            if (gesture.targetLeft >= 0 && gesture.targetTop >= 0 &&
                gesture.targetRight > gesture.targetLeft && gesture.targetBottom > gesture.targetTop &&
                gesture.recordedScreenW > 0 && gesture.recordedScreenH > 0
            ) {
                val sx = screenW / gesture.recordedScreenW.toFloat().coerceAtLeast(1f)
                val sy = screenH / gesture.recordedScreenH.toFloat().coerceAtLeast(1f)
                val oldRect = Rect(
                    (gesture.targetLeft * sx).toInt(),
                    (gesture.targetTop * sy).toInt(),
                    (gesture.targetRight * sx).toInt(),
                    (gesture.targetBottom * sy).toInt()
                )
                val overlap = rectOverlapRatio(oldRect, bounds)
                if (overlap >= 0.72f) score += 38
                else if (overlap >= 0.40f) score += 20
            }

            val dist = projectedTapDistance(bounds, gesture)
            val semanticConfidence = smartIdentityConfidence(action, gesture)
            val strongShape = dna >= 0.76f || roleSim >= 0.82f || semanticConfidence >= 0.70f

            when {
                dist < 0.040f -> score += 54
                dist < 0.095f -> score += 38
                dist < 0.19f -> score += 22
                dist < 0.34f -> score += 8
                !strongShape && dist > 0.62f -> score -= 42
                !strongShape && dist > 0.46f -> score -= 22
                strongShape && dist > 0.75f -> score -= 10
            }

            if (semanticConfidence >= 0.86f) score += 46
            else if (semanticConfidence >= 0.70f) score += 24
            else if (semanticConfidence >= 0.54f) score += 10

            score += aarishActionabilityBonus(action)
            if (safeClickable(node)) score += 7
            if (areaRatio in 0.0006f..0.22f) score += 8
            if (areaRatio > 0.50f && !strongShape && semanticConfidence < 0.58f) score -= 22

            score.coerceAtLeast(0)
        } catch (_: Exception) {
            0
        }
    }

    private fun isAmbiguousGeometryMatch(best: SmartMatch, runner: SmartMatch, gesture: RecordedGesture): Boolean {
        val gap = best.score - runner.score
        if (gap >= 24) return false

        val bestDna = dnaSimilarity(gesture.targetTreePath, extractTreePathDNA(best.node))
        val runnerDna = dnaSimilarity(gesture.targetTreePath, extractTreePathDNA(runner.node))
        if (bestDna >= 0.88f && bestDna - runnerDna >= 0.10f && gap >= 8) return false

        val bestDist = projectedTapDistance(best.bounds, gesture)
        val runnerDist = projectedTapDistance(runner.bounds, gesture)
        if (bestDist + 0.070f < runnerDist && gap >= 8) return false

        val bestConfidence = smartIdentityConfidence(best.node, gesture)
        val runnerConfidence = smartIdentityConfidence(runner.node, gesture)
        if (bestConfidence >= 0.76f && bestConfidence - runnerConfidence >= 0.08f && gap >= 8) return false

        return true
    }

    private fun safeVisible(node: AccessibilityNodeInfo?): Boolean =
        try { node?.isVisibleToUser == true } catch (_: Exception) { false }

    private fun safeEnabled(node: AccessibilityNodeInfo?): Boolean =
        try { node?.isEnabled == true } catch (_: Exception) { false }

    private fun safeClickable(node: AccessibilityNodeInfo?): Boolean =
        try {
            node != null &&
                (node.isClickable || ((node.actions and AccessibilityNodeInfo.ACTION_CLICK) != 0))
        } catch (_: Exception) {
            false
        }

    private fun safeChildCount(node: AccessibilityNodeInfo?): Int =
        try { node?.childCount ?: 0 } catch (_: Exception) { 0 }

    private fun safeChild(node: AccessibilityNodeInfo?, index: Int): AccessibilityNodeInfo? =
        try { node?.getChild(index) } catch (_: Exception) { null }

    private fun safeParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
        try { node?.parent } catch (_: Exception) { null }

    private fun safeText(node: AccessibilityNodeInfo?): String? =
        try { node?.text?.toString() } catch (_: Exception) { null }

    private fun safeDesc(node: AccessibilityNodeInfo?): String? =
        try { node?.contentDescription?.toString() } catch (_: Exception) { null }

    private fun safeId(node: AccessibilityNodeInfo?): String? =
        try { node?.viewIdResourceName } catch (_: Exception) { null }

    private fun safeClass(node: AccessibilityNodeInfo?): String? =
        try { node?.className?.toString() } catch (_: Exception) { null }

    private fun safeBounds(node: AccessibilityNodeInfo?, out: Rect): Boolean =
        try {
            node?.getBoundsInScreen(out)
            node != null
        } catch (_: Exception) {
            false
        }

    private fun hasSavedPercentAnchor(gesture: RecordedGesture): Boolean {
    val xOk = !gesture.xPercent.isNaN() && !gesture.xPercent.isInfinite() && gesture.xPercent in 0f..1f
    val yOk = !gesture.yPercent.isNaN() && !gesture.yPercent.isInfinite() && gesture.yPercent in 0f..1f
    if (!xOk || !yOk) return false

    // AARISH_PERCENT_ANCHOR_FIX: identity alone must not turn missing percent into top-left fallback.
    return gesture.recordedScreenW > 0 && gesture.recordedScreenH > 0
}

    private fun normalizeUltraText(value: String?): String {
        val raw = (value ?: "")
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace('_', ' ')
            .replace('-', ' ')
            .replace('.', ' ')
            .replace('/', ' ')
            .replace(':', ' ')

        return raw
            .lowercase()
            .replace(Regex("\\b\\d{2,}\\b"), "#")
            .replace(Regex("[^a-z#\\s\\u0600-\\u06FF\\u0750-\\u077F\\u0900-\\u097F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun tokenSimilarity(saved: String?, current: String?): Float {
        val a = normalizeUltraText(saved)
        val b = normalizeUltraText(current)

        if (a.isBlank() || b.isBlank()) return 0f
        if (a == b) return 1f
        if (a.length >= 2 && b.length >= 2 && (a.contains(b) || b.contains(a))) return 0.92f

        val aTokens = a.split(" ").filter { it.length >= 2 }.distinct()
        val bTokens = b.split(" ").filter { it.length >= 2 }.distinct()

        if (aTokens.isEmpty() || bTokens.isEmpty()) return 0f

        val hits = aTokens.count { token ->
            bTokens.any { other ->
                token == other ||
                    (token.length >= 4 && other.length >= 4 && (token.contains(other) || other.contains(token)))
            }
        }

        val recall = hits.toFloat() / aTokens.size.toFloat()
        val precision = hits.toFloat() / bTokens.size.toFloat()
        val dice = (2f * hits.toFloat()) / (aTokens.size + bTokens.size).toFloat()

        return maxOf(recall, dice, (recall + precision) / 2f).coerceIn(0f, 1f)
    }

    private fun ownLabelOf(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        val text = safeText(node).orEmpty()
        val desc = safeDesc(node).orEmpty()
        val id = safeId(node)?.substringAfterLast("/").orEmpty()

        return listOf(text, desc, id)
            .filter { it.isNotBlank() }
            .joinToString(" | ")
    }

    private fun collectNodeTextLimited(
        node: AccessibilityNodeInfo?,
        maxNodes: Int = 45,
        maxChars: Int = 420
    ): String {
        if (node == null) return ""

        val out = StringBuilder()
        val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
        stack.add(node)

        var visited = 0

        while (!stack.isEmpty() && visited < maxNodes && out.length < maxChars) {
            visited++

            val n = stack.removeLast()
            val label = ownLabelOf(n)

            if (label.isNotBlank()) {
                if (out.isNotEmpty()) out.append(" | ")
                out.append(label)
            }

            val count = safeChildCount(n)
            for (i in 0 until count) {
                val child = safeChild(n, i)
                if (child != null) stack.add(child)
            }
        }

        return out.toString().take(maxChars)
    }

    private fun collectSiblingText(
        node: AccessibilityNodeInfo?,
        maxChars: Int = 300
    ): String {
        val parent = safeParent(node) ?: return ""
        val nodeBounds = Rect()
        val nodeClass = safeClass(node).orEmpty()
        if (!safeBounds(node, nodeBounds)) return ""

        val out = StringBuilder()
        val count = safeChildCount(parent)

        for (i in 0 until count) {
            val child = safeChild(parent, i) ?: continue
            val childBounds = Rect()
            if (!safeBounds(child, childBounds)) continue

            val sameNode = childBounds == nodeBounds && safeClass(child).orEmpty() == nodeClass
            if (sameNode) continue

            val label = collectNodeTextLimited(child, maxNodes = 14, maxChars = 100)
            if (label.isNotBlank()) {
                if (out.isNotEmpty()) out.append(" | ")
                out.append(label)
            }
            if (out.length >= maxChars) break
        }

        return out.toString().take(maxChars)
    }

    private fun roleFlagsOf(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        val flags = mutableListOf<String>()

        try { if (node.isClickable) flags.add("click") } catch (_: Exception) {}
        try { if (node.isLongClickable) flags.add("long") } catch (_: Exception) {}
        try { if (node.isEditable) flags.add("edit") } catch (_: Exception) {}
        try { if (node.isScrollable) flags.add("scroll") } catch (_: Exception) {}
        try { if (node.isCheckable) flags.add("check") } catch (_: Exception) {}
        try { if (node.isChecked) flags.add("checked") } catch (_: Exception) {}
        try { if (node.isEnabled) flags.add("enabled") } catch (_: Exception) {}
        try { if (node.isVisibleToUser) flags.add("visible") } catch (_: Exception) {}
        try { if (node.isFocusable) flags.add("focus") } catch (_: Exception) {}

        return flags.joinToString("|")
    }

    private fun roleSimilarity(savedFlags: String?, currentFlags: String?): Float {
        if (savedFlags.isNullOrBlank() || currentFlags.isNullOrBlank()) return 0f

        val saved = savedFlags.split("|").filter { it.isNotBlank() }
        if (saved.isEmpty()) return 0f

        var hits = 0
        for (f in saved) {
            if (currentFlags.contains(f)) hits++
        }

        return hits.toFloat() / saved.size.toFloat()
    }

    private fun extractTreePathDNA(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        val parts = mutableListOf<String>()
        var current: AccessibilityNodeInfo? = node
        var depth = 0

        while (current != null && depth < 12) {
            val cls = safeClass(current)?.substringAfterLast(".") ?: "N"
            val parent = safeParent(current)
            if (parent === current) break

            var index = -1

            if (parent != null) {
                val currentBounds = Rect()
                safeBounds(current, currentBounds)

                val count = safeChildCount(parent)
                for (i in 0 until count) {
                    val child = safeChild(parent, i) ?: continue
                    val childBounds = Rect()
                    safeBounds(child, childBounds)

                    val sameClass = safeClass(child) == safeClass(current)
                    val sameBounds = childBounds == currentBounds

                    if (sameClass && sameBounds) {
                        index = i
                        break
                    }
                }
            }

            parts.add("$cls[$index]")
            current = parent
            depth++
        }

        return parts.reversed().joinToString("/")
    }

    private fun dnaSimilarity(saved: String?, current: String?): Float {
        if (saved.isNullOrBlank() || current.isNullOrBlank()) return 0f
        if (saved == current) return 1f

        val a = saved.split("/").filter { it.isNotBlank() }
        val b = current.split("/").filter { it.isNotBlank() }

        if (a.isEmpty() || b.isEmpty()) return 0f

        val minLen = minOf(a.size, b.size)
        var leafMatches = 0

        for (i in 1..minLen) {
            if (a[a.size - i] == b[b.size - i]) leafMatches++
        }

        return leafMatches.toFloat() / maxOf(a.size, b.size).toFloat()
    }


    private fun isToolbarActionLabel(label: String?): Boolean {
        val v = label?.trim()?.lowercase() ?: return false

        return v in setOf(
            "copy",
            "cut",
            "paste",
            "select",
            "select all",
            "share",
            "translate",
            "कॉपी",
            "कट",
            "पेस्ट",
            "चुनें",
            "सब चुनें",
            "साझा करें",
            "अनुवाद"
        )
    }

    private fun findExactActionButtonAcrossWindows(
        gesture: RecordedGesture
    ): SmartMatch? {
        val wantedLabels = listOfNotNull(
            gesture.targetText?.trim()?.takeIf { it.isNotBlank() },
            gesture.targetDesc?.trim()?.takeIf { it.isNotBlank() }
        ).filter { isToolbarActionLabel(it) }.distinct()

        if (wantedLabels.isEmpty()) return null

        val roots = mutableListOf<AccessibilityNodeInfo>()
        val seenRootKeys = hashSetOf<String>()

        fun skipRoot(root: AccessibilityNodeInfo): Boolean {
            val pkg = root.packageName?.toString().orEmpty()
            return pkg == packageName ||
                pkg.contains("keyboard", ignoreCase = true) ||
                pkg.contains("inputmethod", ignoreCase = true)
        }

        fun addRoot(root: AccessibilityNodeInfo?) {
            if (root == null || skipRoot(root)) return
            val b = Rect()
            if (!safeBounds(root, b) || b.width() <= 0 || b.height() <= 0) return
            val key = "${root.packageName}:${b.left},${b.top},${b.right},${b.bottom}"
            if (seenRootKeys.add(key)) roots.add(root)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (window in windows) {
if (window.isActive || window.isFocused) addRoot(window.root)
                }
                for (window in windows) {
addRoot(window.root)
                }
            } catch (_: Exception) {
            }
        }

        addRoot(rootInActiveWindow)

        var best: SmartMatch? = null

        fun labelMatches(node: AccessibilityNodeInfo): Boolean {
            val text = safeText(node)
            val desc = safeDesc(node)
            return wantedLabels.any { wanted ->
                labelsEqual(text, wanted) ||
                    labelsEqual(desc, wanted) ||
                    normalizeUltraText(text) == normalizeUltraText(wanted) ||
                    normalizeUltraText(desc) == normalizeUltraText(wanted)
            }
        }

        for (root in roots) {
            val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
            stack.add(root)

            var visited = 0
            while (!stack.isEmpty() && visited < 2200) {
                visited++

                val node = stack.removeLast()

                val count = safeChildCount(node)
                for (i in count - 1 downTo 0) {
                    val child = safeChild(node, i)
                    if (child != null) stack.add(child)
                }

                if (!safeVisible(node) || !safeEnabled(node)) continue
                if (!labelMatches(node)) continue

                val clickable = findClickableParent(node) ?: node
                if (!safeVisible(clickable) || !safeEnabled(clickable)) continue

                val bounds = Rect()
                if (!safeBounds(clickable, bounds) || bounds.width() <= 0 || bounds.height() <= 0) continue

                var score = 540
                if (safeClickable(clickable)) score += 60
                if (exactIdentityHit(clickable, gesture) || exactIdentityHit(node, gesture)) score += 55

                if (hasSavedPercentAnchor(gesture)) {
                    val dist = projectedTapDistance(bounds, gesture)
                    if (dist < 0.08f) score += 32
                    else if (dist < 0.22f) score += 14
                }

                val candidate = SmartMatch(clickable, Rect(bounds), score)
                val oldBest = best
                if (oldBest == null ||
                    candidate.score > oldBest.score ||
                    (candidate.score == oldBest.score &&
                        candidate.bounds.width() * candidate.bounds.height() < oldBest.bounds.width() * oldBest.bounds.height())
                ) {
                    best = candidate
                }
            }
        }

        return best
    }


    // AARISH_SELECTOR_EXACT_V2: resource-id/text/content-desc selector pass before fuzzy scan.
    private fun findSelectorTargetAcrossWindows(gesture: RecordedGesture): SmartMatch? {
        if (!aarishHasPrimaryIdentity(gesture)) return null

        val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
        val fallbackX = if (hasSavedPercentAnchor(gesture)) {
            gesture.xPercent.coerceIn(0f, 1f) * screenW
        } else {
            gesture.points.firstOrNull()?.x ?: (screenW / 2f)
        }
        val fallbackY = if (hasSavedPercentAnchor(gesture)) {
            gesture.yPercent.coerceIn(0f, 1f) * screenH
        } else {
            gesture.points.firstOrNull()?.y ?: (screenH / 2f)
        }

        val roots = aarishSimpleFullTreeMagneticRoots(fallbackX.toInt(), fallbackY.toInt())
        if (roots.isEmpty()) return null

        val savedId = gesture.targetId?.trim().orEmpty()
        val savedTail = idTail(savedId)
        val savedText = gesture.targetText?.trim().orEmpty()
        val savedDesc = gesture.targetDesc?.trim().orEmpty()
        val savedPkg = aarishSavedPackageFromId(gesture)

        fun directSelectorScore(node: AccessibilityNodeInfo): Int {
            var score = 0
            val text = safeText(node)
            val desc = safeDesc(node)
            val id = safeId(node)
            val tail = idTail(id)
            val own = ownLabelOf(node)

            if (savedId.isNotBlank()) {
                when {
                    id == savedId -> score += 260
                    savedTail.isNotBlank() && tail == savedTail -> score += 185
                    savedTail.isNotBlank() && tokenSimilarity(savedTail, own) >= 0.94f -> score += 96
                }
            }

            if (savedText.isNotBlank()) {
                when {
                    labelsEqual(text, savedText) -> score += 230
                    labelsEqual(desc, savedText) -> score += 205
                    labelContains(text, savedText) -> score += 145
                    tokenSimilarity(savedText, own) >= 0.94f -> score += 126
                    tokenSimilarity(savedText, own) >= 0.82f -> score += 72
                }
            }

            if (savedDesc.isNotBlank()) {
                when {
                    labelsEqual(desc, savedDesc) -> score += 230
                    labelsEqual(text, savedDesc) -> score += 198
                    labelContains(desc, savedDesc) -> score += 136
                    tokenSimilarity(savedDesc, own) >= 0.94f -> score += 118
                    tokenSimilarity(savedDesc, own) >= 0.82f -> score += 68
                }
            }

            return score
        }

        var best: SmartMatch? = null
        var second: SmartMatch? = null
        val seen = hashSetOf<String>()

        fun remember(candidate: SmartMatch) {
            val key = aarishBoundsKey(candidate.bounds) + ':' + safeClass(candidate.node).orEmpty()
            if (!seen.add(key)) return

            val oldBest = best
            if (oldBest == null || candidate.score > oldBest.score ||
                (candidate.score == oldBest.score &&
                    candidate.bounds.width() * candidate.bounds.height() < oldBest.bounds.width() * oldBest.bounds.height())
            ) {
                second = oldBest
                best = candidate
                return
            }

            val oldSecond = second
            if (oldSecond == null || candidate.score > oldSecond.score ||
                (candidate.score == oldSecond.score &&
                    candidate.bounds.width() * candidate.bounds.height() < oldSecond.bounds.width() * oldSecond.bounds.height())
            ) {
                second = candidate
            }
        }

        for (root in roots) {
            val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
            stack.add(root)
            var visited = 0

            while (!stack.isEmpty() && visited < 5200) {
                visited++
                val node = stack.removeLast()

                val count = safeChildCount(node)
                for (i in count - 1 downTo 0) {
                    val child = safeChild(node, i)
                    if (child != null) stack.add(child)
                }

                if (!safeVisible(node) || !safeEnabled(node)) continue
                val directScore = directSelectorScore(node)
                if (directScore <= 0) continue

                val actionNode = findClickableParent(node) ?: node
                if (!safeVisible(actionNode) || !safeEnabled(actionNode)) continue

                val bounds = Rect()
                if (!safeBounds(actionNode, bounds) || bounds.width() <= 0 || bounds.height() <= 0) continue
                if (aarishAreaRatio(bounds) > 0.88f && directScore < 220) continue

                var score = directScore
                score += (smartIdentityConfidence(actionNode, gesture) * 95f).toInt()
                score += aarishActionabilityBonus(actionNode)

                val nodePkg = aarishNodePackage(actionNode).ifBlank { aarishNodePackage(node) }
                if (savedPkg.isNotBlank() && nodePkg == savedPkg) score += 28
                else if (savedPkg.isNotBlank() && nodePkg.isNotBlank() && nodePkg != savedPkg) score -= 24

                if (hasSavedPercentAnchor(gesture)) {
                    val dist = projectedTapDistance(bounds, gesture)
                    when {
                        dist < 0.07f -> score += 38
                        dist < 0.18f -> score += 22
                        dist < 0.34f -> score += 8
                        directScore < 220 && dist > 0.62f -> score -= 34
                    }
                }

                remember(SmartMatch(actionNode, Rect(bounds), score.coerceAtLeast(0)))
            }
        }

        val finalBest = best ?: return null
        if (finalBest.score < 245) return null

        val runner = second
        if (runner != null && finalBest.score - runner.score < 18 &&
            !aarishExactOrVeryStrong(finalBest.node, gesture)
        ) {
            return null
        }

        return finalBest
    }

    
    // AARISH_SIMPLE_FULL_TREE_MAGNETIC_V1
    // Simple rule:
    // 1) Pehle old coordinate ke nearby roots
    // 2) Phir full active window root
    // 3) Phir all accessibility window roots
    // Existing score/click logic same rahega.
    private fun aarishSimpleFullTreeMagneticRoots(x: Int, y: Int): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>(12)
        val seen = HashSet<Int>()

        fun add(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val key = System.identityHashCode(node)
            if (seen.add(key)) out.add(node)
        }

        try {
            collectSmartSearchRoots(x, y).forEach { add(it) }
        } catch (_: Throwable) {
        }

        try {
            add(rootInActiveWindow)
        } catch (_: Throwable) {
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                windows?.forEach { w ->
                    try { add(w.root) } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {
        }

        return out
    }

private fun findBestSmartTarget(gesture: RecordedGesture): SmartMatch? {
        return try {
            findSelectorTargetAcrossWindows(gesture)?.let { return it }
            findExactActionButtonAcrossWindows(gesture)?.let { return it }

            val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
            val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

            val fallbackX = if (hasSavedPercentAnchor(gesture)) {
                gesture.xPercent.coerceIn(0f, 1f) * screenW
            } else {
                gesture.points.firstOrNull()?.x ?: 0f
            }

            val fallbackY = if (hasSavedPercentAnchor(gesture)) {
                gesture.yPercent.coerceIn(0f, 1f) * screenH
            } else {
                gesture.points.firstOrNull()?.y ?: 0f
            }

            val roots = collectSmartSearchRoots(fallbackX.toInt(), fallbackY.toInt())
            if (roots.isEmpty()) return findGeometrySmartTarget(gesture)
            if (!aarishHasAnyRichIdentity(gesture)) return findGeometrySmartTarget(gesture)

            val textSeedBounds = aarishCollectTextSeedBounds(roots, gesture)
            var best: SmartMatch? = null
            var secondBest: SmartMatch? = null
            val seenTargets = hashSetOf<String>()
            val savedPkg = aarishSavedPackageFromId(gesture)

            fun remember(candidate: SmartMatch) {
                val oldBest = best
                if (oldBest == null ||
                    candidate.score > oldBest.score ||
                    (candidate.score == oldBest.score &&
                        candidate.bounds.width() * candidate.bounds.height() < oldBest.bounds.width() * oldBest.bounds.height())
                ) {
                    secondBest = oldBest
                    best = candidate
                    return
                }

                val oldSecond = secondBest
                if (oldSecond == null ||
                    candidate.score > oldSecond.score ||
                    (candidate.score == oldSecond.score &&
                        candidate.bounds.width() * candidate.bounds.height() < oldSecond.bounds.width() * oldSecond.bounds.height())
                ) {
                    secondBest = candidate
                }
            }

            for (root in roots) {
                val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
                stack.add(root)

                var visitedNodes = 0

                while (!stack.isEmpty() && visitedNodes < 6200) {
                    visitedNodes++

                    val node = stack.removeLast()

                    val count = safeChildCount(node)
                    for (i in count - 1 downTo 0) {
                        val child = safeChild(node, i)
                        if (child != null) stack.add(child)
                    }

                    val nodeBounds = Rect()
                    if (!safeBounds(node, nodeBounds) ||
                        nodeBounds.width() <= 0 ||
                        nodeBounds.height() <= 0
                    ) continue

                    if (!safeVisible(node) || !safeEnabled(node)) continue

                    var score = scoreNode(node, nodeBounds, gesture)
                    if (score <= 0) continue

                    val actionNode = findClickableParent(node) ?: node
                    if (!safeVisible(actionNode) || !safeEnabled(actionNode)) continue

                    val targetBounds = Rect()
                    if (!safeBounds(actionNode, targetBounds) ||
                        targetBounds.width() <= 0 ||
                        targetBounds.height() <= 0
                    ) continue

                    val targetKey = aarishBoundsKey(targetBounds) + ":" + safeClass(actionNode).orEmpty()
                    val alreadySeen = !seenTargets.add(targetKey)

                    if (textSeedBounds.contains(aarishBoundsKey(targetBounds))) score += 44
                    if (aarishExactOrVeryStrong(actionNode, gesture) ||
                        aarishExactOrVeryStrong(node, gesture)
                    ) score += 60

                    val nodePkg = aarishNodePackage(actionNode).ifBlank { aarishNodePackage(node) }
                    if (savedPkg.isNotBlank() && nodePkg == savedPkg) {
                        score += 24
                    } else if (savedPkg.isNotBlank() && nodePkg.isNotBlank() && nodePkg != savedPkg) {
                        score -= 18
                    }

                    val dist = aarishDistanceToRecorded(targetBounds, gesture)
                    val strong = aarishExactOrVeryStrong(actionNode, gesture) ||
                        smartIdentityConfidence(actionNode, gesture) >= 0.76f

                    if (hasSavedPercentAnchor(gesture)) {
                        when {
                            dist < 0.055f -> score += 42
                            dist < 0.16f -> score += 24
                            dist < 0.31f -> score += 9
                            !strong && dist > 0.58f -> score -= 50
                            strong && dist > 0.72f -> score -= 8
                        }
                    }

                    val areaRatio = aarishAreaRatio(targetBounds)
                    if (areaRatio > 0.80f && !strong) score -= 42
                    else if (areaRatio > 0.48f && smartIdentityConfidence(actionNode, gesture) < 0.65f) score -= 18
                    else if (areaRatio in 0.0008f..0.28f) score += 5

                    score += aarishActionabilityBonus(actionNode)
                    if (alreadySeen) score -= 3

                    remember(SmartMatch(actionNode, Rect(targetBounds), score.coerceAtLeast(0)))
                }
            }

            val finalBest = best ?: return findGeometrySmartTarget(gesture)
            val threshold = smartMatchThreshold(finalBest, gesture)
            if (finalBest.score < threshold) return findGeometrySmartTarget(gesture)

            val runnerUp = secondBest
            if (runnerUp != null && isAmbiguousSmartMatch(finalBest, runnerUp, gesture)) {
                return findGeometrySmartTarget(gesture)
            }

            finalBest
        } catch (_: Exception) {
            null
        }
    }

    // AARISH_AI_SMART_CLICK_V6_HELPERS
    private fun aarishHasPrimaryIdentity(gesture: RecordedGesture): Boolean {
        return !gesture.targetId.isNullOrBlank() ||
            !gesture.targetText.isNullOrBlank() ||
            !gesture.targetDesc.isNullOrBlank()
    }

    private fun aarishHasAnyRichIdentity(gesture: RecordedGesture): Boolean {
        return aarishHasPrimaryIdentity(gesture) ||
            !gesture.targetContextText.isNullOrBlank() ||
            !gesture.targetChildText.isNullOrBlank() ||
            !gesture.targetSiblingText.isNullOrBlank() ||
            !gesture.targetRoleFlags.isNullOrBlank() ||
            !gesture.targetTreePath.isNullOrBlank() ||
            gesture.targetWPercent > 0f ||
            gesture.targetHPercent > 0f ||
            hasSavedPercentAnchor(gesture)
    }

    private fun aarishSavedPackageFromId(gesture: RecordedGesture): String {
        val savedPackage = gesture.targetPackage?.trim()?.lowercase().orEmpty()
        if (savedPackage.isNotBlank()) return savedPackage

        val id = gesture.targetId?.trim().orEmpty()
        val marker = ":id/"
        val at = id.indexOf(marker)
        return if (at > 0) id.substring(0, at).lowercase() else ""
    }

    private fun aarishNodePackage(node: AccessibilityNodeInfo?): String {
        return try { node?.packageName?.toString()?.lowercase().orEmpty() } catch (_: Exception) { "" }
    }

    private fun aarishBoundsKey(bounds: Rect): String {
        return "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
    }

    private fun aarishAreaRatio(bounds: Rect): Float {
        val screenArea = (resources.displayMetrics.widthPixels.toFloat() * resources.displayMetrics.heightPixels.toFloat()).coerceAtLeast(1f)
        return ((bounds.width().coerceAtLeast(0) * bounds.height().coerceAtLeast(0)).toFloat() / screenArea).coerceIn(0f, 9f)
    }

    private fun aarishDistanceToRecorded(bounds: Rect, gesture: RecordedGesture): Float {
        if (!hasSavedPercentAnchor(gesture)) return Float.MAX_VALUE
        val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
        val x = bounds.left + (gesture.insideXPercent.coerceIn(0f, 1f) * bounds.width())
        val y = bounds.top + (gesture.insideYPercent.coerceIn(0f, 1f) * bounds.height())
        return (kotlin.math.abs((x / screenW) - gesture.xPercent.coerceIn(0f, 1f)) +
            kotlin.math.abs((y / screenH) - gesture.yPercent.coerceIn(0f, 1f))).coerceAtLeast(0f)
    }

    private fun aarishHumanSeeds(gesture: RecordedGesture): List<String> {
        val seeds = linkedSetOf<String>()

        fun addRaw(value: String?) {
            val raw = value?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
            if (raw.length in 2..80 && !isToolbarActionLabel(raw)) seeds.add(raw)

            val norm = normalizeUltraText(raw)
            if (norm.length in 2..80) seeds.add(norm)
        }

        addRaw(gesture.targetText)
        addRaw(gesture.targetDesc)
        addRaw(idTail(gesture.targetId))

        gesture.targetContextText
            ?.split("|", "•", ">", "\n")
            ?.map { it.trim() }
            ?.filter { it.length in 3..48 }
            ?.take(8)
            ?.forEach { addRaw(it) }

        return seeds.filter { it.isNotBlank() }.distinct().take(14)
    }

    // AARISH_SELECTOR_SEED_V1: resource-id/text/direct selector seeds before fuzzy scoring.
    private fun aarishCollectTextSeedBounds(
        roots: List<AccessibilityNodeInfo>,
        gesture: RecordedGesture
    ): Set<String> {
        val out = hashSetOf<String>()

        fun remember(node: AccessibilityNodeInfo?) {
            val action = findClickableParent(node) ?: node ?: return
            val b = Rect()
            if (safeVisible(action) &&
                safeEnabled(action) &&
                safeBounds(action, b) &&
                b.width() > 0 &&
                b.height() > 0
            ) {
                out.add(aarishBoundsKey(b))
            }
        }

        val fullId = gesture.targetId?.trim().orEmpty()
        if (fullId.isNotBlank() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            for (root in roots) {
                try {
                    root.findAccessibilityNodeInfosByViewId(fullId).orEmpty()
                        .take(60)
                        .forEach { remember(it) }
                } catch (_: Exception) {
                }
            }
        }

        val wantedTail = idTail(fullId)
        if (wantedTail.isNotBlank()) {
            for (root in roots) {
                try {
                    val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
                    stack.add(root)
                    var visited = 0
                    while (!stack.isEmpty() && visited < 2600) {
                        visited++
                        val node = stack.removeLast()
                        if (idTail(safeId(node)) == wantedTail) remember(node)
                        val count = safeChildCount(node)
                        for (i in count - 1 downTo 0) {
                            val child = safeChild(node, i)
                            if (child != null) stack.add(child)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        val seeds = aarishHumanSeeds(gesture)
        for (root in roots) {
            for (seed in seeds) {
                try {
                    root.findAccessibilityNodeInfosByText(seed).orEmpty()
                        .take(50)
                        .forEach { remember(it) }
                } catch (_: Exception) {
                }
            }
        }

        // Content-description aur resource-id ko findAccessibilityNodeInfosByText()
        // hamesha cover nahi karta, isliye direct selector scan bhi rakha.
        if (seeds.isNotEmpty() || fullId.isNotBlank()) {
            for (root in roots) {
                try {
                    val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
                    stack.add(root)
                    var visited = 0

                    while (!stack.isEmpty() && visited < 3400) {
                        visited++
                        val node = stack.removeLast()

                        val nodeOwn = ownLabelOf(node)
                        val nodeIdTail = idTail(safeId(node))
                        val directHit = seeds.any { seed ->
                            labelsEqual(safeText(node), seed) ||
                                labelsEqual(safeDesc(node), seed) ||
                                labelsEqual(nodeIdTail, seed) ||
                                tokenSimilarity(seed, nodeOwn) >= 0.92f
                        } || (wantedTail.isNotBlank() && nodeIdTail == wantedTail)

                        if (directHit) remember(node)

                        val count = safeChildCount(node)
                        for (i in count - 1 downTo 0) {
                            val child = safeChild(node, i)
                            if (child != null) stack.add(child)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        return out
    }

    private fun aarishSemanticWindow(node: AccessibilityNodeInfo?, maxChars: Int = 1800): String {
        val action = findClickableParent(node) ?: node

        return listOf(
            ownLabelOf(node),
            ownLabelOf(action),
            collectNodeTextLimited(node, 44, 520),
            collectNodeTextLimited(action, 92, 920),
            collectNodeTextLimited(safeParent(action), 44, 520),
            collectNodeTextLimited(safeParent(safeParent(action)), 30, 340),
            collectSiblingText(action, 520)
        ).filter { it.isNotBlank() }
            .joinToString(" | ")
            .take(maxChars)
    }

    private fun aarishPrimaryLabelSimilarity(node: AccessibilityNodeInfo?, gesture: RecordedGesture): Float {
        val action = findClickableParent(node) ?: node
        val merged = aarishSemanticWindow(node, 1600)
        var best = 0f

        val primaryValues = listOf(
            gesture.targetText,
            gesture.targetDesc,
            idTail(gesture.targetId).takeIf { it.isNotBlank() }
        )

        for (saved in primaryValues) {
            if (saved.isNullOrBlank()) continue
            best = maxOf(
                best,
                tokenSimilarity(saved, safeText(node)),
                tokenSimilarity(saved, safeDesc(node)),
                tokenSimilarity(saved, safeText(action)),
                tokenSimilarity(saved, safeDesc(action)),
                tokenSimilarity(saved, idTail(safeId(node))),
                tokenSimilarity(saved, idTail(safeId(action))),
                tokenSimilarity(saved, merged) * 0.88f
            )
        }

        return best.coerceIn(0f, 1f)
    }

    private fun aarishExactOrVeryStrong(node: AccessibilityNodeInfo?, gesture: RecordedGesture): Boolean {
        if (node == null) return false
        if (exactIdentityHit(node, gesture)) return true

        val action = findClickableParent(node) ?: node
        if (exactIdentityHit(action, gesture)) return true

        return aarishPrimaryLabelSimilarity(node, gesture) >= 0.94f ||
            smartIdentityConfidence(action, gesture) >= 0.90f
    }

    private fun aarishActionabilityBonus(node: AccessibilityNodeInfo?): Int {
        if (node == null) return 0

        var score = 0
        try { if (node.isClickable) score += 22 } catch (_: Exception) {}
        try { if (node.isLongClickable) score += 6 } catch (_: Exception) {}
        try { if (node.isFocusable) score += 5 } catch (_: Exception) {}
        try { if (node.isCheckable) score += 9 } catch (_: Exception) {}
        try { if ((node.actions and AccessibilityNodeInfo.ACTION_CLICK) != 0) score += 14 } catch (_: Exception) {}

        return score
    }

    private fun scoreNode(
        node: AccessibilityNodeInfo,
        bounds: Rect,
        gesture: RecordedGesture
    ): Int {
        return try {
            if (!safeVisible(node) || !safeEnabled(node)) return 0
            if (!aarishHasAnyRichIdentity(gesture)) return 0

            val actionNode = findClickableParent(node) ?: node

            val nodeText = safeText(node)?.trim()
            val nodeDesc = safeDesc(node)?.trim()
            val nodeId = safeId(node)?.trim()
            val nodeClass = safeClass(node)?.trim()
            val actionText = safeText(actionNode)?.trim()
            val actionDesc = safeDesc(actionNode)?.trim()
            val actionId = safeId(actionNode)?.trim()
            val actionClass = safeClass(actionNode)?.trim()

            val nodeSubtree = collectNodeTextLimited(node, 36, 420)
            val semanticWindow = aarishSemanticWindow(node, 1800)
            val actionContext = collectNodeTextLimited(actionNode, 96, 960)
            val siblingText = collectSiblingText(actionNode, 560)
            val parentText = collectNodeTextLimited(safeParent(actionNode), 46, 520)

            var score = 0
            var primaryHits = 0

            val savedId = gesture.targetId?.trim()
            if (!savedId.isNullOrBlank()) {
                val savedTail = idTail(savedId)
                when {
                    nodeId == savedId || actionId == savedId -> {
                        score += 190
                        primaryHits++
                    }
                    savedTail.isNotBlank() &&
                        (idTail(nodeId) == savedTail || idTail(actionId) == savedTail) -> {
                        score += 108
                        primaryHits++
                    }
                    tokenSimilarity(savedTail, semanticWindow) >= 0.88f -> score += 34
                }
            }

            val savedText = gesture.targetText?.trim()
            if (!savedText.isNullOrBlank()) {
                val textSim = maxOf(
                    tokenSimilarity(savedText, nodeText),
                    tokenSimilarity(savedText, actionText),
                    tokenSimilarity(savedText, nodeDesc) * 0.94f,
                    tokenSimilarity(savedText, actionDesc) * 0.94f,
                    tokenSimilarity(savedText, nodeSubtree) * 0.90f,
                    tokenSimilarity(savedText, actionContext) * 0.86f,
                    tokenSimilarity(savedText, semanticWindow) * 0.82f
                )

                when {
                    labelsEqual(savedText, nodeText) || labelsEqual(savedText, actionText) -> {
                        score += 148
                        primaryHits++
                    }
                    labelsEqual(savedText, nodeDesc) || labelsEqual(savedText, actionDesc) -> {
                        score += 126
                        primaryHits++
                    }
                    labelContains(nodeText, savedText) || labelContains(actionText, savedText) -> {
                        score += 92
                        primaryHits++
                    }
                    labelContains(nodeSubtree, savedText) || labelContains(actionContext, savedText) -> score += 58
                }

                if (textSim >= 0.94f) {
                    score += 72
                    primaryHits++
                } else if (textSim >= 0.78f) {
                    score += 42
                } else if (textSim >= 0.58f) {
                    score += 18
                }
            }

            val savedDesc = gesture.targetDesc?.trim()
            if (!savedDesc.isNullOrBlank()) {
                val descSim = maxOf(
                    tokenSimilarity(savedDesc, nodeDesc),
                    tokenSimilarity(savedDesc, actionDesc),
                    tokenSimilarity(savedDesc, nodeText) * 0.92f,
                    tokenSimilarity(savedDesc, actionText) * 0.92f,
                    tokenSimilarity(savedDesc, semanticWindow) * 0.82f
                )

                when {
                    labelsEqual(savedDesc, nodeDesc) || labelsEqual(savedDesc, actionDesc) -> {
                        score += 142
                        primaryHits++
                    }
                    labelsEqual(savedDesc, nodeText) || labelsEqual(savedDesc, actionText) -> {
                        score += 116
                        primaryHits++
                    }
                    labelContains(nodeDesc, savedDesc) || labelContains(actionDesc, savedDesc) -> {
                        score += 82
                        primaryHits++
                    }
                    labelContains(semanticWindow, savedDesc) -> score += 46
                }

                if (descSim >= 0.94f) {
                    score += 66
                    primaryHits++
                } else if (descSim >= 0.76f) {
                    score += 36
                } else if (descSim >= 0.56f) {
                    score += 15
                }
            }

            val contextSim = maxOf(
                tokenSimilarity(gesture.targetContextText, actionContext),
                tokenSimilarity(gesture.targetContextText, semanticWindow),
                tokenSimilarity(gesture.targetContextText, parentText) * 0.92f
            )

            val childSim = maxOf(
                tokenSimilarity(gesture.targetChildText, nodeSubtree),
                tokenSimilarity(gesture.targetChildText, actionContext) * 0.86f
            )

            val siblingSim = maxOf(
                tokenSimilarity(gesture.targetSiblingText, siblingText),
                tokenSimilarity(gesture.targetSiblingText, semanticWindow) * 0.72f
            )

            val roleSim = roleSimilarity(gesture.targetRoleFlags, roleFlagsOf(actionNode))
            val dnaSim = dnaSimilarity(gesture.targetTreePath, extractTreePathDNA(actionNode))
            val confidence = smartIdentityConfidence(actionNode, gesture)

            if (confidence >= 0.92f) score += 88
            else if (confidence >= 0.80f) score += 58
            else if (confidence >= 0.66f) score += 28
            else if (confidence >= 0.52f) score += 11

            if (contextSim >= 0.88f) score += 74
            else if (contextSim >= 0.66f) score += 40
            else if (contextSim >= 0.45f) score += 16

            if (childSim >= 0.88f) score += 44
            else if (childSim >= 0.66f) score += 24
            else if (childSim >= 0.45f) score += 10

            if (siblingSim >= 0.86f) score += 38
            else if (siblingSim >= 0.64f) score += 20
            else if (siblingSim >= 0.46f) score += 7

            if (roleSim >= 0.80f) score += 24
            else if (roleSim >= 0.55f) score += 10

            if (dnaSim >= 0.94f) score += 64
            else if (dnaSim >= 0.74f) score += 34
            else if (dnaSim >= 0.52f) score += 13

            if (!gesture.targetClass.isNullOrBlank()) {
                val savedSimple = gesture.targetClass.substringAfterLast('.').lowercase()
                val nodeSimple = nodeClass?.substringAfterLast('.')?.lowercase().orEmpty()
                val actionSimple = actionClass?.substringAfterLast('.')?.lowercase().orEmpty()

                if (savedSimple == nodeSimple || savedSimple == actionSimple) {
                    score += 32
                } else if (
                    (savedSimple.contains("button") && (nodeSimple.contains("button") || actionSimple.contains("button"))) ||
                    (savedSimple.contains("text") && (nodeSimple.contains("text") || actionSimple.contains("text"))) ||
                    (savedSimple.contains("image") && (nodeSimple.contains("image") || actionSimple.contains("image"))) ||
                    (savedSimple.contains("edit") && (nodeSimple.contains("edit") || actionSimple.contains("edit"))) ||
                    (savedSimple.contains("view") && (nodeSimple.contains("view") || actionSimple.contains("view")))
                ) {
                    score += 17
                }
            }

            val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
            val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

            if (gesture.targetWPercent > 0f && gesture.targetHPercent > 0f) {
                val wDiff = abs((bounds.width() / screenW) - gesture.targetWPercent)
                val hDiff = abs((bounds.height() / screenH) - gesture.targetHPercent)

                if (wDiff < 0.035f && hDiff < 0.035f) score += 34
                else if (wDiff < 0.085f && hDiff < 0.085f) score += 18
                else if (wDiff < 0.16f && hDiff < 0.16f) score += 7
            }

            if (gesture.targetLeft >= 0 &&
                gesture.targetTop >= 0 &&
                gesture.targetRight > gesture.targetLeft &&
                gesture.targetBottom > gesture.targetTop &&
                gesture.recordedScreenW > 0 &&
                gesture.recordedScreenH > 0
            ) {
                val sx = screenW / gesture.recordedScreenW.toFloat().coerceAtLeast(1f)
                val sy = screenH / gesture.recordedScreenH.toFloat().coerceAtLeast(1f)

                val oldRect = Rect(
                    (gesture.targetLeft * sx).toInt(),
                    (gesture.targetTop * sy).toInt(),
                    (gesture.targetRight * sx).toInt(),
                    (gesture.targetBottom * sy).toInt()
                )

                val overlap = rectOverlapRatio(oldRect, bounds)
                if (overlap >= 0.72f) score += 30
                else if (overlap >= 0.38f) score += 14
            }

            if (hasSavedPercentAnchor(gesture)) {
                val dist = aarishDistanceToRecorded(bounds, gesture)
                val strong = primaryHits > 0 ||
                    confidence >= 0.74f ||
                    contextSim >= 0.70f ||
                    dnaSim >= 0.82f

                when {
                    dist < 0.045f -> score += 58
                    dist < 0.15f -> score += 31
                    dist < 0.31f -> score += 11
                    !strong && dist > 0.46f -> score -= 54
                    strong && dist > 0.70f -> score -= 10
                }
            }

            score += aarishActionabilityBonus(actionNode)
            if (safeClickable(node)) score += 8

            val areaRatio = aarishAreaRatio(bounds)
            val hasStrongSemantic = primaryHits > 0 ||
                confidence >= 0.74f ||
                contextSim >= 0.70f ||
                childSim >= 0.70f ||
                siblingSim >= 0.75f

            if (areaRatio > 0.72f && !hasStrongSemantic) score -= 36
            if (areaRatio > 0.42f && confidence < 0.62f && primaryHits == 0) score -= 18
            if (bounds.width() < 10 || bounds.height() < 10) score -= 20

            score.coerceAtLeast(0)
        } catch (_: Exception) {
            0
        }
    }


    // ==========================================================
    // 🧠 OFFLINE SMART CLICK v2: text/id/desc/context/DNA based matching
    // ==========================================================
    private fun idTail(value: String?): String {
        return value?.substringAfterLast("/")?.trim()?.lowercase().orEmpty()
    }

    private fun labelsEqual(a: String?, b: String?): Boolean {
        val aa = a?.trim().orEmpty()
        val bb = b?.trim().orEmpty()
        if (aa.isBlank() || bb.isBlank()) return false
        return aa.equals(bb, ignoreCase = true) || normalizeUltraText(aa) == normalizeUltraText(bb)
    }

    private fun labelContains(container: String?, needle: String?): Boolean {
        val c = normalizeUltraText(container)
        val n = normalizeUltraText(needle)
        if (c.isBlank() || n.isBlank()) return false
        return c == n || c.contains(n) || n.contains(c)
    }

    private fun rectOverlapRatio(a: Rect, b: Rect): Float {
        val left = kotlin.math.max(a.left, b.left)
        val top = kotlin.math.max(a.top, b.top)
        val right = kotlin.math.min(a.right, b.right)
        val bottom = kotlin.math.min(a.bottom, b.bottom)
        val iw = (right - left).coerceAtLeast(0)
        val ih = (bottom - top).coerceAtLeast(0)
        val intersection = iw * ih
        val smaller = kotlin.math.min(
            (a.width() * a.height()).coerceAtLeast(1),
            (b.width() * b.height()).coerceAtLeast(1)
        )
        return intersection.toFloat() / smaller.toFloat()
    }


    private fun projectedTapDistance(bounds: Rect, gesture: RecordedGesture): Float {
        if (!hasSavedPercentAnchor(gesture) || bounds.width() <= 0 || bounds.height() <= 0) {
            return Float.MAX_VALUE
        }

        val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

        val projectedX = (
            bounds.left + (gesture.insideXPercent.coerceIn(0f, 1f) * bounds.width())
            ) / screenW
        val projectedY = (
            bounds.top + (gesture.insideYPercent.coerceIn(0f, 1f) * bounds.height())
            ) / screenH

        return (
            kotlin.math.abs(projectedX - gesture.xPercent.coerceIn(0f, 1f)) +
                kotlin.math.abs(projectedY - gesture.yPercent.coerceIn(0f, 1f))
            ).coerceAtLeast(0f)
    }

    private fun smartIdentityConfidence(node: AccessibilityNodeInfo?, gesture: RecordedGesture): Float {
    if (node == null) return 0f

    return try {
        val actionNode = findClickableParent(node) ?: node

        val nodeText = safeText(node)
        val nodeDesc = safeDesc(node)
        val nodeId = safeId(node)
        val actionText = safeText(actionNode)
        val actionDesc = safeDesc(actionNode)
        val actionId = safeId(actionNode)

        val nodeOwn = ownLabelOf(node)
        val actionOwn = ownLabelOf(actionNode)
        val nodeContext = collectNodeTextLimited(node, 36, 420)
        val actionContext = collectNodeTextLimited(actionNode, 72, 760)
        val parentContext = collectNodeTextLimited(safeParent(actionNode), 28, 360)
        val siblingContext = collectSiblingText(actionNode, 420)
        val mergedContext = listOf(nodeOwn, actionOwn, nodeContext, actionContext, parentContext, siblingContext)
            .filter { it.isNotBlank() }
            .joinToString(" | ")
            .take(1400)

        var best = 0f

        val savedPackage = gesture.targetPackage?.trim()?.lowercase().orEmpty()
        if (savedPackage.isNotBlank()) {
            val nodePackage = aarishNodePackage(node)
            val actionPackage = aarishNodePackage(actionNode)
            if (nodePackage == savedPackage || actionPackage == savedPackage) {
                best = maxOf(best, 0.36f)
            }
        }

        val savedId = gesture.targetId?.trim()
        if (!savedId.isNullOrBlank()) {
            val savedTail = idTail(savedId)
            val nodeTail = idTail(nodeId)
            val actionTail = idTail(actionId)

            if (nodeId == savedId || actionId == savedId) best = maxOf(best, 1f)
            else if (savedTail.isNotBlank() && (savedTail == nodeTail || savedTail == actionTail)) best = maxOf(best, 0.88f)
            else best = maxOf(best, tokenSimilarity(savedTail, mergedContext) * 0.72f)
        }

        val savedText = gesture.targetText?.trim()
        if (!savedText.isNullOrBlank()) {
            best = maxOf(
                best,
                tokenSimilarity(savedText, nodeText),
                tokenSimilarity(savedText, actionText),
                tokenSimilarity(savedText, nodeDesc) * 0.94f,
                tokenSimilarity(savedText, actionDesc) * 0.94f,
                tokenSimilarity(savedText, mergedContext) * 0.86f
            )
        }

        val savedDesc = gesture.targetDesc?.trim()
        if (!savedDesc.isNullOrBlank()) {
            best = maxOf(
                best,
                tokenSimilarity(savedDesc, nodeDesc),
                tokenSimilarity(savedDesc, actionDesc),
                tokenSimilarity(savedDesc, nodeText) * 0.92f,
                tokenSimilarity(savedDesc, actionText) * 0.92f,
                tokenSimilarity(savedDesc, mergedContext) * 0.84f
            )
        }

        best = maxOf(best, tokenSimilarity(gesture.targetContextText, mergedContext) * 0.82f)
        best = maxOf(best, tokenSimilarity(gesture.targetChildText, nodeContext) * 0.72f)
        best = maxOf(best, tokenSimilarity(gesture.targetSiblingText, siblingContext) * 0.68f)
        best = maxOf(best, roleSimilarity(gesture.targetRoleFlags, roleFlagsOf(actionNode)) * 0.58f)
        best = maxOf(best, dnaSimilarity(gesture.targetTreePath, extractTreePathDNA(actionNode)) * 0.74f)

        if (best >= 0.58f && hasSavedPercentAnchor(gesture)) {
            val b = Rect()
            if (safeBounds(actionNode, b)) {
                val dist = projectedTapDistance(b, gesture)
                if (dist < 0.08f) best += 0.10f
                else if (dist < 0.18f) best += 0.05f
            }
        }

        best.coerceIn(0f, 1f)
    } catch (_: Exception) {
        0f
    }
}

    private fun isAmbiguousSmartMatch(
        best: SmartMatch,
        runnerUp: SmartMatch,
        gesture: RecordedGesture
    ): Boolean {
        val gap = best.score - runnerUp.score
        if (gap >= 30) return false

        val bestExact = aarishExactOrVeryStrong(best.node, gesture)
        val runnerExact = aarishExactOrVeryStrong(runnerUp.node, gesture)

        if (bestExact && !runnerExact) return false
        if (!bestExact && runnerExact && gap < 42) return true

        val bestConfidence = smartIdentityConfidence(best.node, gesture)
        val runnerConfidence = smartIdentityConfidence(runnerUp.node, gesture)
        val confidenceGap = bestConfidence - runnerConfidence

        if (bestConfidence >= 0.90f && confidenceGap >= 0.08f) return false
        if (bestConfidence >= 0.78f && confidenceGap >= 0.05f && gap >= 12) return false

        if (hasSavedPercentAnchor(gesture)) {
            val bestDistance = projectedTapDistance(best.bounds, gesture)
            val runnerDistance = projectedTapDistance(runnerUp.bounds, gesture)

            if (bestDistance + 0.09f < runnerDistance && gap >= 8) return false
            if (runnerDistance + 0.06f < bestDistance && gap < 22 && !bestExact) return true
        }

        val bothWeak = bestConfidence < 0.68f &&
            runnerConfidence < 0.68f &&
            !bestExact &&
            !runnerExact

        if (bothWeak && gap < 24) return true

        val bestLabel = aarishPrimaryLabelSimilarity(best.node, gesture)
        val runnerLabel = aarishPrimaryLabelSimilarity(runnerUp.node, gesture)

        if (bestLabel >= 0.90f && bestLabel - runnerLabel >= 0.09f) return false

        return gap < 14 && kotlin.math.abs(bestConfidence - runnerConfidence) < 0.06f
    }

    private fun exactIdentityHit(node: AccessibilityNodeInfo?, gesture: RecordedGesture): Boolean {
    if (node == null) return false

    return try {
        val actionNode = findClickableParent(node) ?: node

        val nodeText = safeText(node)
        val nodeDesc = safeDesc(node)
        val nodeId = safeId(node)
        val actionText = safeText(actionNode)
        val actionDesc = safeDesc(actionNode)
        val actionId = safeId(actionNode)

        val nodeContext = collectNodeTextLimited(node, 26, 340)
        val actionContext = collectNodeTextLimited(actionNode, 60, 680)
        val merged = listOf(ownLabelOf(node), ownLabelOf(actionNode), nodeContext, actionContext)
            .filter { it.isNotBlank() }
            .joinToString(" | ")

        val savedPackage = gesture.targetPackage?.trim()?.lowercase().orEmpty()
        val packageOk = savedPackage.isBlank() || aarishNodePackage(actionNode) == savedPackage || aarishNodePackage(node) == savedPackage

        val savedId = gesture.targetId?.trim()
        val idOk = packageOk && !savedId.isNullOrBlank() &&
            (
                nodeId == savedId ||
                    actionId == savedId ||
                    (idTail(savedId).isNotBlank() && (idTail(nodeId) == idTail(savedId) || idTail(actionId) == idTail(savedId)))
                )

        val savedText = gesture.targetText?.trim()
        val textOk = !savedText.isNullOrBlank() &&
            (
                labelsEqual(nodeText, savedText) ||
                    labelsEqual(actionText, savedText) ||
                    labelsEqual(nodeDesc, savedText) ||
                    labelsEqual(actionDesc, savedText) ||
                    labelContains(merged, savedText) ||
                    tokenSimilarity(savedText, merged) >= 0.93f
                )

        val savedDesc = gesture.targetDesc?.trim()
        val descOk = !savedDesc.isNullOrBlank() &&
            (
                labelsEqual(nodeDesc, savedDesc) ||
                    labelsEqual(actionDesc, savedDesc) ||
                    labelsEqual(nodeText, savedDesc) ||
                    labelsEqual(actionText, savedDesc) ||
                    labelContains(merged, savedDesc) ||
                    tokenSimilarity(savedDesc, merged) >= 0.93f
                )

        idOk || textOk || descOk
    } catch (_: Exception) {
        false
    }
}

    private fun hasStrongSavedIdentity(gesture: RecordedGesture): Boolean {
        return !gesture.targetId.isNullOrBlank() ||
            !gesture.targetText.isNullOrBlank() ||
            !gesture.targetDesc.isNullOrBlank() ||
            !gesture.targetContextText.isNullOrBlank() ||
            !gesture.targetChildText.isNullOrBlank() ||
            !gesture.targetSiblingText.isNullOrBlank() ||
            !gesture.targetTreePath.isNullOrBlank()
    }

    private fun smartMatchThreshold(match: SmartMatch, gesture: RecordedGesture): Int {
        if (aarishExactOrVeryStrong(match.node, gesture)) return tuneSmartThreshold(54)

        val confidence = smartIdentityConfidence(match.node, gesture)
        val primarySim = aarishPrimaryLabelSimilarity(match.node, gesture)
        val hasPrimary = aarishHasPrimaryIdentity(gesture)

        if (confidence >= 0.90f || primarySim >= 0.94f) return tuneSmartThreshold(58)
        if (confidence >= 0.80f || primarySim >= 0.84f) return tuneSmartThreshold(66)
        if (confidence >= 0.70f || primarySim >= 0.74f) return tuneSmartThreshold(76)

        if (!hasStrongSavedIdentity(gesture)) return geometryMatchThreshold(gesture)
        if (hasPrimary) return tuneSmartThreshold(82)

        return tuneSmartThreshold(92)
    }

    private fun collectSmartSearchRoots(tapX: Int, tapY: Int): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        val seen = hashSetOf<String>()
        val myPackage = packageName

        fun isBadPackage(pkg: String): Boolean {
            return pkg.isBlank() ||
                pkg == myPackage ||
                pkg.contains("inputmethod", ignoreCase = true) ||
                pkg.contains("keyboard", ignoreCase = true)
        }

        fun addRoot(root: AccessibilityNodeInfo?, priority: Int = 0) {
            if (root == null) return

            val pkg = root.packageName?.toString().orEmpty()
            if (isBadPackage(pkg)) return

            val b = Rect()
            if (!safeBounds(root, b) || b.width() <= 0 || b.height() <= 0) return

            val key = "$pkg:${b.left},${b.top},${b.right},${b.bottom}"
            if (seen.add(key)) {
                if (priority > 0) roots.add(0, root) else roots.add(root)
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (window in windows) {
val root = window.root ?: continue
                    val b = Rect()
                    if (!safeBounds(root, b)) continue

                    if (b.contains(tapX, tapY)) addRoot(root, 2)
                }

                for (window in windows) {
if (window.isActive || window.isFocused) addRoot(window.root, 1)
                }

                for (window in windows) {
addRoot(window.root, 0)
                }
            } catch (_: Exception) {
            }
        }

        addRoot(rootInActiveWindow, 1)

        return roots.take(12)
    }

    private fun findBestActionNodeForClick(node: AccessibilityNodeInfo?, gesture: RecordedGesture): AccessibilityNodeInfo? {
        var current = node
        var depth = 0
        var firstClickable: AccessibilityNodeInfo? = null

        while (current != null && depth < 30) {
            if (safeVisible(current) && safeEnabled(current) && safeClickable(current)) {
                if (firstClickable == null) firstClickable = current
                if (exactIdentityHit(current, gesture)) return current
            }

            val next = safeParent(current)
            if (next == current) break
            current = next
            depth++
        }

        return firstClickable
    }

    private fun performExactSmartTap(match: SmartMatch, gesture: RecordedGesture, runId: Int, tapBounds: Rect = match.bounds): Boolean {
        if (!isSamePlaybackRun(runId)) return false
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return false
        if (tapBounds.width() <= 0 || tapBounds.height() <= 0) return false

        val screenW = (resources.displayMetrics.widthPixels.toFloat() - 2f).coerceAtLeast(2f)
        val screenH = (resources.displayMetrics.heightPixels.toFloat() - 2f).coerceAtLeast(2f)
        val safeX = (tapBounds.left.toFloat() + (gesture.insideXPercent.coerceIn(0f, 1f) * tapBounds.width().toFloat()))
            .coerceIn(2f, screenW)
        val safeY = (tapBounds.top.toFloat() + (gesture.insideYPercent.coerceIn(0f, 1f) * tapBounds.height().toFloat()))
            .coerceIn(2f, screenH)
        aarishShowVisualClickIndicator(safeX, safeY) // AARISH_PREMIUM_CLICK_RIPPLE_EXACT_TAP_V1

        if (aarishTryFileManagerNodeRescueV1(safeX, safeY, runId, "File picker clicked")) {
            // AARISH_FILE_MANAGER_RESCUE_EXACT_TAP_V1
            return true
        }

        val path = Path().apply {
            moveTo(safeX, safeY)
            lineTo((safeX + 1.2f).coerceIn(2f, screenW), (safeY + 1.2f).coerceIn(2f, screenH))
        }

        val token = beginActiveGesture()
        var watchdog: Runnable? = null

        return try {
            val tap = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, 115L))
                .build()

            watchdog = scheduleGestureWatchdog(runId, 2600L, token)

            val accepted = dispatchGesture(
                tap,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        watchdog?.let {
                            handler.removeCallbacks(it)
                            scheduledTasks.remove(it)
                        }
                        if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        watchdog?.let {
                            handler.removeCallbacks(it)
                            scheduledTasks.remove(it)
                        }
                        if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
                    }
                },
                null
            )

            if (!accepted) {
                watchdog?.let {
                    handler.removeCallbacks(it)
                    scheduledTasks.remove(it)
                }
                if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
            }

            accepted
        } catch (_: Exception) {
            watchdog?.let {
                handler.removeCallbacks(it)
                scheduledTasks.remove(it)
            }
            if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
            false
        }
    }

    // AARISH_EXACT_INSIDE_TAP_V1: wide controls/seekers/off-center taps ko node-click ke center bias se bachao.
    private fun hasIntentionalInsideOffset(gesture: RecordedGesture): Boolean {
        val insideX = gesture.insideXPercent.takeIf { !it.isNaN() && !it.isInfinite() } ?: 0.5f
        val insideY = gesture.insideYPercent.takeIf { !it.isNaN() && !it.isInfinite() } ?: 0.5f
        val offCenter = abs(insideX - 0.5f) > 0.18f || abs(insideY - 0.5f) > 0.18f
        if (!offCenter) return false

        val cls = gesture.targetClass.orEmpty().lowercase()
        val role = gesture.targetRoleFlags.orEmpty().lowercase()
        val id = gesture.targetId.orEmpty().lowercase()
        val text = listOfNotNull(
            gesture.targetText,
            gesture.targetDesc,
            gesture.targetContextText,
            gesture.targetChildText,
            gesture.targetSiblingText
        ).joinToString(" ").lowercase()

        val wideOrTall = gesture.targetWPercent >= 0.18f || gesture.targetHPercent >= 0.10f
        val exactControl = cls.contains("seek") || cls.contains("slider") || cls.contains("progress") ||
            role.contains("scroll") || role.contains("seek") || role.contains("slider") ||
            id.contains("seek") || id.contains("slider") || id.contains("progress") ||
            text.contains("seek") || text.contains("slider") || text.contains("progress")

        return wideOrTall || exactControl
    }

    private fun performSmartNodeClick(match: SmartMatch, gesture: RecordedGesture, runId: Int): Boolean {
        if (!isSamePlaybackRun(runId)) return false

        val actionNode = findBestActionNodeForClick(match.node, gesture) ?: match.node
        if (!safeVisible(actionNode) || !safeEnabled(actionNode)) return false

        val actionBounds = Rect()
        val tapBounds = if (safeBounds(actionNode, actionBounds) && actionBounds.width() > 0 && actionBounds.height() > 0) {
            Rect(actionBounds)
        } else {
            Rect(match.bounds)
        }

        val threshold = smartMatchThreshold(match, gesture)
        val confidence = smartIdentityConfidence(actionNode, gesture)
        val exact = aarishExactOrVeryStrong(actionNode, gesture) || exactIdentityHit(actionNode, gesture)
        val geometryTrusted = isGeometryTrusted(match.copy(bounds = tapBounds), gesture)

        val trusted =
            match.score >= threshold ||
                exact ||
                confidence >= smartNodeClickMinConfidence() ||
                geometryTrusted ||
                (match.score >= tuneSmartThreshold(78) && hasStrongSavedIdentity(gesture)) ||
                (match.score >= tuneSmartThreshold(230) && aarishHasPrimaryIdentity(gesture))

        if (!trusted) return false

        fun tryClick(node: AccessibilityNodeInfo?): Boolean {
            if (node == null || !safeVisible(node) || !safeEnabled(node) || !safeClickable(node)) return false
            return try {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } catch (_: Exception) {
                false
            }
        }

        fun tryClickWithFocus(node: AccessibilityNodeInfo?): Boolean {
            if (tryClick(node)) return true
            try { node?.performAction(AccessibilityNodeInfo.ACTION_FOCUS) } catch (_: Exception) {}
            return tryClick(node)
        }

        fun finishNodeClickToast(label: String): Boolean {
            val token = beginActiveGesture()
            val doneTask = object : Runnable {
                override fun run() {
                    scheduledTasks.remove(this)
                    if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
                }
            }
            scheduledTasks.add(doneTask)
            handler.postDelayed(doneTask, 360L)
            aarishShowVisualClickIndicator(tapBounds.exactCenterX(), tapBounds.exactCenterY()) // AARISH_PREMIUM_CLICK_RIPPLE_NODE_CLICK_V1
            showTinyToast(label)
            return true
        }

        val areaRatio = aarishAreaRatio(tapBounds)
        val preferSemanticClick =
            !hasIntentionalInsideOffset(gesture) &&
                safeClickable(actionNode) &&
                areaRatio <= 0.70f &&
                (exact ||
                    confidence >= smartPreferSemanticConfidence() ||
                    match.score >= threshold + smartScoreMargin(20) ||
                    match.score >= tuneSmartThreshold(260))

        if (preferSemanticClick && tryClickWithFocus(actionNode)) {
            return finishNodeClickToast("Magnet node") // AARISH_ENGINE_TRACE_TEXT_TOAST_V25B
        }

        if (performExactSmartTap(match, gesture, runId, tapBounds)) {
            showTinyToast("Magnet exact") // AARISH_ENGINE_TRACE_TEXT_TOAST_V25B
            return true
        }

        if (tryClickWithFocus(actionNode)) {
            return finishNodeClickToast("Magnet click") // AARISH_ENGINE_TRACE_TEXT_TOAST_V25B
        }

        var parent = safeParent(actionNode)
        var depth = 0
        while (parent != null && depth < 10) {
            if ((aarishExactOrVeryStrong(parent, gesture) || safeClickable(parent)) && tryClickWithFocus(parent)) {
                return finishNodeClickToast("Magnet parent") // AARISH_ENGINE_TRACE_TEXT_TOAST_V25B
            }

            val next = safeParent(parent)
            if (next === parent) break
            parent = next
            depth++
        }

        return false
    }

private fun shouldUseSelectionLongPress(match: SmartMatch?, gesture: RecordedGesture): Boolean {
        val node = match?.node ?: return true
        val cls = safeClass(node)?.lowercase().orEmpty()
        val id = safeId(node)?.lowercase().orEmpty()
        val label = listOfNotNull(
            safeText(node),
            safeDesc(node),
            gesture.targetText,
            gesture.targetDesc,
            gesture.targetContextText
        ).joinToString(" ").lowercase()

        val editable = cls.contains("edittext") ||
            id.contains("input") ||
            id.contains("edit") ||
            id.contains("message") ||
            label.contains("type a message") ||
            label.contains("search")

        val selectionHint = label.contains("copy") ||
            label.contains("select") ||
            label.contains("text") ||
            label.contains("message") ||
            label.contains("कॉपी") ||
            label.contains("चुन") ||
            label.contains("نسخ")

        val hardAction = cls.contains("button") ||
            cls.contains("imagebutton") ||
            cls.contains("checkbox") ||
            cls.contains("switch") ||
            cls.contains("tab") ||
            (safeClickable(node) && !cls.contains("textview") && !cls.contains("edittext"))

        return editable || selectionHint || (!hardAction && cls.contains("textview") && label.length > 10)
    }

private fun hasRealMovement(points: List<GesturePoint>): Boolean {
        if (points.size <= 1) return false

        val first = points.first()
        var maxDx = 0f
        var maxDy = 0f

        for (i in 1 until points.size) {
            val p = points[i]
            maxDx = kotlin.math.max(maxDx, abs(p.x - first.x))
            maxDy = kotlin.math.max(maxDy, abs(p.y - first.y))
        }

        // Tap jitter ignore, small drag/scroll preserve.
        val slop = kotlin.math.max(10f, 6f * resources.displayMetrics.density)
        return maxDx > slop || maxDy > slop
    }


private fun performGestureAt(
    startX: Float,
    startY: Float,
    points: List<GesturePoint>,
    runId: Int,
    recordedGesture: RecordedGesture? = null
) {
    if (points.isEmpty() || !isSamePlaybackRun(runId)) return

    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
        Toast.makeText(this, "Gesture playback ke liye Android 7+ required hai", Toast.LENGTH_LONG).show()
        return
    }

    val orderedPoints = points
        .filter { !it.x.isNaN() && !it.x.isInfinite() && !it.y.isNaN() && !it.y.isInfinite() }
        .sortedBy { it.t.coerceAtLeast(0L) }
        .ifEmpty { return }

    val firstPoint = orderedPoints.first()
    val maxPathPoints = 260
    val step = if (orderedPoints.size > maxPathPoints) {
        kotlin.math.max(1, orderedPoints.size / maxPathPoints)
    } else {
        1
    }

    val playbackPoints = if (step <= 1) {
        orderedPoints
    } else {
        orderedPoints.filterIndexed { index, _ ->
            index == 0 || index == orderedPoints.lastIndex || index % step == 0
        }
    }

    val screenW = (resources.displayMetrics.widthPixels.toFloat() - 2f).coerceAtLeast(2f)
    val screenH = (resources.displayMetrics.heightPixels.toFloat() - 2f).coerceAtLeast(2f)
    val scaleX = recordedGesture
        ?.takeIf { it.recordedScreenW > 0 }
        ?.let { screenW / it.recordedScreenW.toFloat().coerceAtLeast(1f) }
        ?.coerceIn(0.35f, 3.0f) ?: 1f
    val scaleY = recordedGesture
        ?.takeIf { it.recordedScreenH > 0 }
        ?.let { screenH / it.recordedScreenH.toFloat().coerceAtLeast(1f) }
        ?.coerceIn(0.35f, 3.0f) ?: 1f

    val safeStartX = startX.coerceIn(2f, screenW)
    val safeStartY = startY.coerceIn(2f, screenH)

    aarishShowVisualClickIndicator(safeStartX, safeStartY) // AARISH_PREMIUM_CLICK_RIPPLE_GESTURE_AT_V1
    val path = Path()
    path.moveTo(safeStartX, safeStartY)

    var movement = false

    if (playbackPoints.size > 1) {
        for (i in 1 until playbackPoints.size) {
            val p = playbackPoints[i]
            val shiftedX = safeStartX + ((p.x - firstPoint.x) * scaleX)
            val shiftedY = safeStartY + ((p.y - firstPoint.y) * scaleY)

            if (abs(shiftedX - safeStartX) > 8f || abs(shiftedY - safeStartY) > 8f) {
                movement = true
            }

            path.lineTo(
                shiftedX.coerceIn(2f, screenW),
                shiftedY.coerceIn(2f, screenH)
            )
        }
    }

    if (!movement) {
        // AARISH_HUMAN_LIKE_TAP_V1: real finger style firm tap micro-slip
        path.lineTo(
            (safeStartX + 3.8f).coerceIn(2f, screenW),
            (safeStartY + 3.8f).coerceIn(2f, screenH)
        )
    }

    val rawDuration = orderedPoints.last().t.coerceAtLeast(0L)
    val duration = (if (!movement) max(145L, rawDuration) else max(50L, rawDuration)).coerceAtMost(600000L) // AARISH_HUMAN_LIKE_TAP_V1

    if (!movement && duration <= 1200L && aarishTryFileManagerNodeRescueV1(safeStartX, safeStartY, runId, "File picker clicked")) {
        // AARISH_FILE_MANAGER_RESCUE_GESTURE_AT_V1
        return
    }

    if (!movement && duration > 59000L) {
        val token = beginActiveGesture()
        dispatchLongPressChunksSafe(safeStartX, safeStartY, duration, runId) {
            if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
        }
        return
    }

    val token = beginActiveGesture()
    var watchdog: Runnable? = null

    try {
        val safeDuration = duration.coerceAtMost(59000L)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, safeDuration))
            .build()

        watchdog = scheduleGestureWatchdog(runId, safeDuration + 3200L, token)

        val accepted = dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    watchdog?.let {
                        handler.removeCallbacks(it)
                        scheduledTasks.remove(it)
                    }
                    if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    watchdog?.let {
                        handler.removeCallbacks(it)
                        scheduledTasks.remove(it)
                    }
                    if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
                }
            },
            null
        )

        if (!accepted && isCurrentCallbackRun(runId)) {
            watchdog?.let {
                handler.removeCallbacks(it)
                scheduledTasks.remove(it)
            }
            finishActiveGesture(token)
        }

    } catch (_: Exception) {
        watchdog?.let {
            handler.removeCallbacks(it)
            scheduledTasks.remove(it)
        }
        if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
        Toast.makeText(this, "Ek gesture invalid tha, skip kar diya", Toast.LENGTH_SHORT).show()
    }
}


    private fun getRealAppRootForPoint(tapX: Int?, tapY: Int?): AccessibilityNodeInfo? {
        val myPackage = packageName
        var bestRoot: AccessibilityNodeInfo? = null
        var bestScore = Int.MIN_VALUE

        fun isBadPackage(pkg: String): Boolean {
            return pkg == myPackage ||
                pkg.contains("inputmethod", ignoreCase = true) ||
                pkg.contains("keyboard", ignoreCase = true)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (window in windows) {
val root = window.root ?: continue
                    val pkg = root.packageName?.toString() ?: ""

                    if (isBadPackage(pkg)) continue

                    val bounds = Rect()
                    root.getBoundsInScreen(bounds)

                    if (bounds.width() <= 0 || bounds.height() <= 0) continue

                    if (tapX != null && tapY != null && !bounds.contains(tapX, tapY)) {
                        continue
                    }

                    val area = bounds.width() * bounds.height()
                    val activeBonus = if (window.isActive || window.isFocused) 1_000_000 else 0
                    val score = area + activeBonus

                    if (score > bestScore) {
                        bestScore = score
                        bestRoot = root
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (bestRoot != null) return bestRoot

        val fallbackRoot = rootInActiveWindow ?: return null
        val fallbackPkg = fallbackRoot.packageName?.toString() ?: ""
        return if (isBadPackage(fallbackPkg)) null else fallbackRoot
    }

    // ==========================================================
    // 🔥 RECORDING TIME SNAPSHOT
    // ==========================================================
private fun captureTargetSnapshotInternal(
    x: Int,
    y: Int,
    screenW: Float,
    screenH: Float
): TargetSnapshot? {
    val root = getRealAppRootForPoint(x, y) ?: return null
    val touchedNode = findDeepestNodeAtCoordinate(root, x, y) ?: return null
    val clickNode = findClickableParent(touchedNode) ?: touchedNode

    val clickBounds = Rect()
    if (!safeBounds(clickNode, clickBounds) || clickBounds.width() <= 0 || clickBounds.height() <= 0) return null

    val safeW = screenW.coerceAtLeast(1f)
    val safeH = screenH.coerceAtLeast(1f)

    fun clean(value: String?): String? {
        return value?.replace(Regex("\\s+"), " ")?.trim()?.take(180)?.takeIf { it.isNotBlank() }
    }

    fun firstClean(vararg values: String?): String? {
        for (v in values) {
            val cleaned = clean(v)
            if (!cleaned.isNullOrBlank()) return cleaned
        }
        return null
    }

    val clickText = clean(safeText(clickNode))
    val touchText = clean(safeText(touchedNode))
    val clickDesc = clean(safeDesc(clickNode))
    val touchDesc = clean(safeDesc(touchedNode))
    val clickId = clean(safeId(clickNode))
    val touchId = clean(safeId(touchedNode))

    val clickOwn = ownLabelOf(clickNode)
    val touchOwn = ownLabelOf(touchedNode)
    val clickContext = collectNodeTextLimited(clickNode, 84, 860)
    val touchContext = collectNodeTextLimited(touchedNode, 52, 560)
    val siblingContext = listOf(
        collectSiblingText(touchedNode, 420),
        collectSiblingText(clickNode, 620)
    )
        .filter { it.isNotBlank() }
        .joinToString(" | ")
        .take(760)
    val parentContext = collectNodeTextLimited(safeParent(clickNode), 50, 560)
    val grandParentContext = collectNodeTextLimited(safeParent(safeParent(clickNode)), 34, 360)

    val primaryText = firstClean(clickText, touchText, clickDesc, touchDesc, idTail(clickId), idTail(touchId))
    val primaryDesc = firstClean(clickDesc, touchDesc, clickText, touchText)

    val aarishFilesWordPrimaryV2 = aarishFindFilesWordNearTapV2(
        root = root,
        touchedNode = touchedNode,
        clickNode = clickNode,
        tapX = x,
        tapY = y,
        screenW = safeW,
        screenH = safeH
    ) // AARISH_UNIVERSAL_FILES_WORD_RECORD_PRIMARY_V2
    val aarishPrimaryTextV2 = aarishFilesWordPrimaryV2 ?: primaryText
    val aarishPrimaryDescV2 = aarishFilesWordPrimaryV2 ?: primaryDesc

    return TargetSnapshot(
        targetText = aarishPrimaryTextV2,
        targetDesc = aarishPrimaryDescV2,
        targetId = firstClean(clickId, touchId),
        targetClass = firstClean(safeClass(clickNode), safeClass(touchedNode)),
        targetPackage = firstClean(aarishNodePackage(clickNode), aarishNodePackage(touchedNode)),

        targetContextText = listOf(clickOwn, touchOwn, clickContext, touchContext, parentContext, grandParentContext)
            .filter { it.isNotBlank() }
            .joinToString(" | ")
            .take(1100),
        targetChildText = listOf(touchContext, clickContext)
            .filter { it.isNotBlank() }
            .joinToString(" | ")
            .take(700),
        targetSiblingText = siblingContext.take(700),
        targetRoleFlags = listOf(
            roleFlagsOf(touchedNode),
            roleFlagsOf(clickNode)
        )
            .filter { it.isNotBlank() }
            .joinToString("|"),
        targetTreePath = extractTreePathDNA(clickNode),

        targetLeft = clickBounds.left,
        targetTop = clickBounds.top,
        targetRight = clickBounds.right,
        targetBottom = clickBounds.bottom,
        xPercent = (x / safeW).coerceIn(0f, 1f),
        yPercent = (y / safeH).coerceIn(0f, 1f),
        targetWPercent = (clickBounds.width() / safeW).coerceIn(0f, 1f),
        targetHPercent = (clickBounds.height() / safeH).coerceIn(0f, 1f),
        insideXPercent = if (clickBounds.width() > 0) ((x - clickBounds.left).toFloat() / clickBounds.width()).coerceIn(0f, 1f) else 0.5f,
        insideYPercent = if (clickBounds.height() > 0) ((y - clickBounds.top).toFloat() / clickBounds.height()).coerceIn(0f, 1f) else 0.5f,
        recordedScreenW = safeW.toInt(),
        recordedScreenH = safeH.toInt()
    )
}

    private fun findDeepestNodeAtCoordinate(
        root: AccessibilityNodeInfo?,
        x: Int,
        y: Int
    ): AccessibilityNodeInfo? {
        if (root == null) return null

        return try {
            val stack = java.util.ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
            stack.add(Pair(root, 0))

            var bestNode: AccessibilityNodeInfo? = null
            var bestScore = Int.MIN_VALUE
            var visited = 0

            while (!stack.isEmpty() && visited < 2500) {
                visited++
                val item = stack.removeLast()
                val node = item.first
                val depth = item.second

                val bounds = Rect()
                if (!safeBounds(node, bounds) || !bounds.contains(x, y)) continue

                val area = (bounds.width() * bounds.height()).coerceAtLeast(1)
                val clickableParent = findClickableParent(node)
                val hasLabel = !safeText(node).isNullOrBlank() || !safeDesc(node).isNullOrBlank() || !safeId(node).isNullOrBlank()
                val clickableBonus = if (safeClickable(node)) 900 else if (clickableParent != null) 650 else 0
                val leafBonus = if (safeChildCount(node) == 0) 260 else 0
                val labelBonus = if (hasLabel) 360 else 0
                val smallAreaBonus = (700000 / area).coerceIn(0, 900)

                val candidateScore =
                    (depth * 1000) +
                        clickableBonus +
                        leafBonus +
                        labelBonus +
                        smallAreaBonus

                if (candidateScore > bestScore) {
                    bestScore = candidateScore
                    bestNode = node
                }

                val count = safeChildCount(node)
                for (i in 0 until count) {
                    val child = safeChild(node, i)
                    if (child != null) stack.add(Pair(child, depth + 1))
                }
            }

            bestNode
        } catch (_: Exception) {
            null
        }
    }

    private fun performHybridSelectionRetry(
        startX: Float,
        startY: Float,
        points: List<GesturePoint>,
        duration: Long,
        runId: Int
    ) {
        if (!isSamePlaybackRun(runId)) return

        val token = beginActiveGesture()

        fun finishFlow() {
            if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
        }

        fun runDoubleTapRetry() {
            if (!isSamePlaybackRun(runId)) {
                finishFlow()
                return
            }

            showTinyToast("↻ Select miss, double-tap hold")

            dispatchHybridGesturePath(
                startX = startX,
                startY = startY,
                points = points,
                duration = duration,
                isDoubleTap = true,
                runId = runId
            ) {
                handler.postDelayed({
                    finishFlow()
                }, 850L)
            }
        }

        dispatchHybridGesturePath(
            startX = startX,
            startY = startY,
            points = points,
            duration = duration,
            isDoubleTap = false,
            runId = runId
        ) { completed ->
            if (!isSamePlaybackRun(runId)) {
                finishFlow()
                return@dispatchHybridGesturePath
            }

            if (!completed) {
                runDoubleTapRetry()
                return@dispatchHybridGesturePath
            }

            handler.postDelayed({
                if (!isSamePlaybackRun(runId)) {
                    finishFlow()
                    return@postDelayed
                }

                if (isSelectionUiVisibleHybrid()) {
                    finishFlow()
                } else {
                    runDoubleTapRetry()
                }
            }, 850L)
        }
    }

        private fun dispatchHybridGesturePath(
        startX: Float,
        startY: Float,
        points: List<GesturePoint>,
        duration: Long,
        isDoubleTap: Boolean,
        runId: Int,
        onDone: (Boolean) -> Unit
    ) {
        if (!isSamePlaybackRun(runId)) {
            onDone(false)
            return
        }

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            onDone(false)
            return
        }

        val screenW = (resources.displayMetrics.widthPixels.toFloat() - 2f).coerceAtLeast(2f)
        val screenH = (resources.displayMetrics.heightPixels.toFloat() - 2f).coerceAtLeast(2f)
        val safeX = startX.coerceIn(2f, screenW)
        val safeY = startY.coerceIn(2f, screenH)
        val holdDuration = duration.coerceAtLeast(650L).coerceAtMost(600000L)
        aarishShowVisualClickIndicator(safeX, safeY) // AARISH_PREMIUM_CLICK_RIPPLE_HYBRID_PATH_V1
        val callbackDelivered = java.util.concurrent.atomic.AtomicBoolean(false)
        // AARISH_HYBRID_TIMEOUT_TRACK_FIX_V1:
        // Track timeout in scheduledTasks so STOP/playback cancel removes stale callbacks too.
        val timeoutTask = object : Runnable {
            override fun run() {
                scheduledTasks.remove(this)
                if (callbackDelivered.compareAndSet(false, true)) {
                    onDone(false)
                }
            }
        }
        scheduledTasks.add(timeoutTask)
        handler.postDelayed(timeoutTask, holdDuration + if (isDoubleTap) 3200L else 2400L)

        fun finishOnce(value: Boolean) {
            if (callbackDelivered.compareAndSet(false, true)) {
                handler.removeCallbacks(timeoutTask)
                scheduledTasks.remove(timeoutTask)
                onDone(value)
            }
        }

        fun tinyPath(): Path {
            return Path().apply {
                moveTo(safeX, safeY)
                lineTo((safeX + 1f).coerceIn(2f, screenW), (safeY + 1f).coerceIn(2f, screenH))
            }
        }

        fun dispatchBuilder(builder: GestureDescription.Builder, done: (Boolean) -> Unit) {
            val gesture = try {
                builder.build()
            } catch (_: Exception) {
                done(false)
                return
            }

            val accepted = dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        done(isCurrentCallbackRun(runId))
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        done(false)
                    }
                },
                null
            )

            if (!accepted) done(false)
        }

        if (isDoubleTap) {
            val firstTap = GestureDescription.Builder().apply {
                addStroke(GestureDescription.StrokeDescription(tinyPath(), 0L, 90L))
            }

            dispatchBuilder(firstTap) { firstOk ->
                if (!firstOk || !isSamePlaybackRun(runId)) {
                    finishOnce(false)
                } else {
                    handler.postDelayed({
                        if (!isSamePlaybackRun(runId)) {
                            finishOnce(false)
                        } else {
                            val secondHold = GestureDescription.Builder().apply {
                                addStroke(GestureDescription.StrokeDescription(tinyPath(), 0L, holdDuration))
                            }
                            dispatchBuilder(secondHold) { secondOk ->
                                finishOnce(secondOk && isCurrentCallbackRun(runId))
                            }
                        }
                    }, 120L)
                }
            }
            return
        }

        val path = Path().apply { moveTo(safeX, safeY) }
        var hasLine = false
        val first = points.firstOrNull()
        if (first != null && points.size > 1) {
            for (i in 1 until points.size) {
                val p = points[i]
                val shiftedX = startX + (p.x - first.x)
                val shiftedY = startY + (p.y - first.y)
                path.lineTo(shiftedX.coerceIn(2f, screenW), shiftedY.coerceIn(2f, screenH))
                hasLine = true
            }
        }

        if (!hasLine) {
            path.lineTo((safeX + 1f).coerceIn(2f, screenW), (safeY + 1f).coerceIn(2f, screenH))
        }

        val normalBuilder = GestureDescription.Builder().apply {
            addStroke(GestureDescription.StrokeDescription(path, 0L, holdDuration))
        }

        dispatchBuilder(normalBuilder) { completed ->
            finishOnce(completed && isCurrentCallbackRun(runId))
        }
    }

    private fun isSelectionUiVisibleHybrid(): Boolean {
        return try {
            val roots = mutableListOf<AccessibilityNodeInfo>()

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                try {
                    for (window in windows) {
val root = window.root ?: continue
                        roots.add(root)
                    }
                } catch (_: Exception) {
                }
            }

            rootInActiveWindow?.let { active ->
                val activeBounds = Rect()
                if (safeBounds(active, activeBounds)) {
                    val alreadyAdded = roots.any { root ->
                        val rootBounds = Rect()
                        safeBounds(root, rootBounds) && rootBounds == activeBounds
                    }
                    if (!alreadyAdded) roots.add(active)
                }
            }
            if (roots.isEmpty()) return false

            val markers = listOf(
                "copy", "select", "select all", "cut", "paste", "share", "translate",
                "कॉपी", "चुनें", "सब चुनें", "कट", "पेस्ट", "अनुवाद",
                "نسخ", "چنیں", "سب منتخب", "قص", "چسپاں", "ترجمہ"
            )

            var visited = 0
            for (root in roots) {
                val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
                stack.add(root)

                while (!stack.isEmpty() && visited < 1600) {
                    visited++
                    val node = stack.removeLast()

                    try {
                        if (node.textSelectionStart >= 0 &&
                            node.textSelectionEnd >= 0 &&
                            node.textSelectionStart != node.textSelectionEnd
                        ) return true
                    } catch (_: Exception) {
                    }

                    try {
                        if (node.isSelected) return true
                    } catch (_: Exception) {
                    }

                    val text = safeText(node)?.lowercase() ?: ""
                    val desc = safeDesc(node)?.lowercase() ?: ""
                    val id = safeId(node)?.lowercase() ?: ""
                    val label = "$text $desc $id"

                    if (label.contains("floating_toolbar") || label.contains("selection")) return true
                    if (safeVisible(node)) {
                        val clickableOrFocusable = try { node.isClickable || node.isFocusable } catch (_: Exception) { false }
                        if (clickableOrFocusable && markers.any { label.contains(it) }) return true
                    }

                    val count = safeChildCount(node)
                    for (i in 0 until count) {
                        val child = safeChild(node, i)
                        if (child != null) stack.add(child)
                    }
                }
            }

            false
        } catch (_: Exception) {
            false
        }
    }

    private fun findClickableParent(
        node: AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        var current = node
        var depth = 0

        while (current != null && depth < 30) {
            if (safeClickable(current)) return current
            val next = safeParent(current)
            if (next == current) return null
            current = next
            depth++
        }

        return null
    }




    private fun aarishFilesWordNormV2(value: String?): String {
        // AARISH_UNIVERSAL_FILES_WORD_V2
        return value.orEmpty()
            .removePrefix("OCR:")
            .replace(Regex("[^a-zA-Z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(java.util.Locale.US)
    }

    private fun aarishIsFilesWordV2(value: String?): Boolean {
        // AARISH_UNIVERSAL_FILES_WORD_V2
        val n = aarishFilesWordNormV2(value)
        return n == "file" || n == "files" || n == "my files"
    }

    private fun aarishFilesWordDisplayV2(value: String?): String? {
        // AARISH_UNIVERSAL_FILES_WORD_V2
        return when (aarishFilesWordNormV2(value)) {
            "file" -> "File"
            "files" -> "Files"
            "my files" -> "My Files"
            else -> null
        }
    }

    private fun aarishNodeFilesLabelV2(node: AccessibilityNodeInfo?): String? {
        // AARISH_UNIVERSAL_FILES_WORD_V2
        if (node == null) return null

        val direct = listOf(
            safeText(node),
            safeDesc(node),
            ownLabelOf(node),
            idTail(safeId(node))
        )

        for (v in direct) {
            val d = aarishFilesWordDisplayV2(v)
            if (!d.isNullOrBlank()) return d
        }

        return null
    }

    private fun aarishFindFilesWordNearTapV2(
        root: AccessibilityNodeInfo?,
        touchedNode: AccessibilityNodeInfo?,
        clickNode: AccessibilityNodeInfo?,
        tapX: Int,
        tapY: Int,
        screenW: Float,
        screenH: Float
    ): String? {
        // AARISH_UNIVERSAL_FILES_WORD_RECORD_V2
        val safeW = screenW.coerceAtLeast(1f)
        val safeH = screenH.coerceAtLeast(1f)
        val density = try { resources.displayMetrics.density.coerceAtLeast(1f) } catch (_: Throwable) { 2f }

        val seeds = mutableListOf<AccessibilityNodeInfo>()
        touchedNode?.let { seeds.add(it) }
        clickNode?.let { seeds.add(it) }
        root?.let { seeds.add(it) }

        var bestLabel: String? = null
        var bestScore = Int.MIN_VALUE
        var scanned = 0

        fun remember(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || !safeVisible(node) || !safeEnabled(node)) return

            val label = aarishNodeFilesLabelV2(node) ?: return

            val b = Rect()
            if (!safeBounds(node, b) || b.width() <= 0 || b.height() <= 0) return

            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val dx = kotlin.math.abs(cx - tapX.toFloat())
            val dy = kotlin.math.abs(cy - tapY.toFloat())

            val nearSameRow = dy <= (44f * density)
            val nearTap = (dx + dy) <= (230f * density)
            val contains = b.contains(tapX, tapY)

            if (!contains && !nearSameRow && !nearTap) return

            val area = b.width().coerceAtLeast(1) * b.height().coerceAtLeast(1)
            val screenArea = (safeW * safeH).coerceAtLeast(1f)
            val areaRatio = (area.toFloat() / screenArea).coerceIn(0f, 1f)
            if (areaRatio > 0.40f) return

            var score = 10000
            score -= (dx * 0.85f).toInt()
            score -= (dy * 3.2f).toInt()
            score -= (areaRatio * 800f).toInt()
            score += depth * 8
            if (contains) score += 900
            if (safeClickable(node)) score += 120
            if (label == "Files") score += 60
            if (label == "My Files") score += 40

            if (score > bestScore) {
                bestScore = score
                bestLabel = label
            }
        }

        try {
            for (seed in seeds) {
                val stack = java.util.ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
                stack.add(Pair(seed, 0))

                while (!stack.isEmpty() && scanned < 5200) {
                    val item = stack.removeLast()
                    val node = item.first
                    val depth = item.second
                    scanned++

                    remember(node, depth)

                    val count = safeChildCount(node)
                    if (count > 0) {
                        for (i in 0 until count) {
                            val child = safeChild(node, i)
                            if (child != null) stack.add(Pair(child, depth + 1))
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        return bestLabel
    }

    private fun aarishGestureAsksFilesWordV2(g: RecordedGesture): Boolean {
        // AARISH_UNIVERSAL_FILES_WORD_V2
        val direct = listOf(
            g.targetText,
            g.targetDesc,
            idTail(g.targetId),
            g.targetClass
        )

        if (direct.any { aarishIsFilesWordV2(it) }) return true

        val child = g.targetChildText.orEmpty()
            .removePrefix("OCR:")
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return child.length <= 30 && aarishIsFilesWordV2(child)
    }

    private fun aarishTryFilesWordNodeClickV2(
        gesture: RecordedGesture,
        runId: Int,
        label: String = "Files click"
    ): Boolean {
        // AARISH_UNIVERSAL_FILES_WORD_CLICK_V2
        if (!isSamePlaybackRun(runId)) return false
        if (!aarishGestureAsksFilesWordV2(gesture)) return false

        val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

        val anchorX = if (hasSavedPercentAnchor(gesture)) {
            gesture.xPercent.coerceIn(0f, 1f) * screenW
        } else {
            gesture.points.firstOrNull()?.x ?: (screenW / 2f)
        }.coerceIn(2f, (screenW - 2f).coerceAtLeast(2f))

        val anchorY = if (hasSavedPercentAnchor(gesture)) {
            gesture.yPercent.coerceIn(0f, 1f) * screenH
        } else {
            gesture.points.firstOrNull()?.y ?: (screenH / 2f)
        }.coerceIn(2f, (screenH - 2f).coerceAtLeast(2f))

        val roots = collectSmartSearchRoots(anchorX.toInt(), anchorY.toInt())
        if (roots.isEmpty()) return false

        var bestNode: AccessibilityNodeInfo? = null
        var bestBounds = Rect()
        var bestScore = Int.MIN_VALUE
        var scanned = 0

        fun remember(labelNode: AccessibilityNodeInfo?, depth: Int) {
            if (labelNode == null || !safeVisible(labelNode) || !safeEnabled(labelNode)) return

            val label = aarishNodeFilesLabelV2(labelNode) ?: return
            val actionNode = findClickableParent(labelNode) ?: labelNode

            if (!safeVisible(actionNode) || !safeEnabled(actionNode)) return

            val b = Rect()
            if (!safeBounds(actionNode, b) || b.width() <= 0 || b.height() <= 0) return

            val area = b.width().coerceAtLeast(1) * b.height().coerceAtLeast(1)
            val screenArea = (screenW * screenH).coerceAtLeast(1f)
            val areaRatio = (area.toFloat() / screenArea).coerceIn(0f, 1f)
            if (areaRatio > 0.55f) return

            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val dx = kotlin.math.abs(cx - anchorX)
            val dy = kotlin.math.abs(cy - anchorY)
            val dist = dx + (dy * 2.8f)

            var score = 12000
            score -= dist.toInt()
            score -= (areaRatio * 950f).toInt()
            score -= (area / 9000)
            score += depth * 10
            if (safeClickable(actionNode)) score += 260
            if (b.contains(anchorX.toInt(), anchorY.toInt())) score += 700
            if (label == "Files") score += 160
            if (label == "My Files") score += 120

            if (score > bestScore) {
                bestScore = score
                bestNode = actionNode
                bestBounds = Rect(b)
            }
        }

        try {
            for (root in roots) {
                val stack = java.util.ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
                stack.add(Pair(root, 0))

                while (!stack.isEmpty() && scanned < 6200) {
                    val item = stack.removeLast()
                    val node = item.first
                    val depth = item.second
                    scanned++

                    val count = safeChildCount(node)
                    for (i in count - 1 downTo 0) {
                        val child = safeChild(node, i)
                        if (child != null) stack.add(Pair(child, depth + 1))
                    }

                    remember(node, depth)
                }
            }
        } catch (_: Throwable) {}

        val node = bestNode ?: return false

        val clicked = try {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                node.performAction(AccessibilityNodeInfo.ACTION_SELECT) ||
                run {
                    try { node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) } catch (_: Throwable) {}
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
        } catch (_: Throwable) {
            false
        }

        if (!clicked) return false

        val token = beginActiveGesture()
        val doneTask = object : Runnable {
            override fun run() {
                scheduledTasks.remove(this)
                if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
            }
        }

        scheduledTasks.add(doneTask)
        handler.postDelayed(doneTask, 430L)

        aarishShowVisualClickIndicator(bestBounds.exactCenterX(), bestBounds.exactCenterY())

        try {
            android.util.Log.d(
                "AarishAI_FilesWord",
                "Files universal ACTION_CLICK score=$bestScore scanned=$scanned bounds=$bestBounds"
            )
        } catch (_: Throwable) {}

        showTinyToast(label)
        return true
    }

    private fun aarishFileManagerPackageV1(pkg: String?): Boolean {
        // AARISH_FILE_MANAGER_RESCUE_V1
        val p = pkg.orEmpty().trim().lowercase(java.util.Locale.US)
        if (p.isBlank()) return false

        return p.contains("documentsui") ||
            p.contains("providers.media") ||
            p.contains("media.module") ||
            p.contains("permissioncontroller") ||
            p.contains("packageinstaller") ||
            p.contains("systemui") ||
            p.contains("filemanager") ||
            p.contains(".files") ||
            p.endsWith(".files") ||
            p.contains("documents") ||
            p.contains("storage")
    }

    private fun aarishFileNodeActionCandidateV1(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        // AARISH_FILE_MANAGER_RESCUE_V1
        var cur = node
        var guard = 0
        while (cur != null && guard < 10) {
            if (safeVisible(cur) && safeEnabled(cur) && safeClickable(cur)) {
                return cur
            }
            cur = safeParent(cur)
            guard++
        }
        return null
    }

    private fun aarishTryFileManagerNodeRescueV1(
        x: Float,
        y: Float,
        runId: Int?,
        label: String = "File picker clicked"
    ): Boolean {
        // AARISH_FILE_MANAGER_RESCUE_V1
        if (runId != null && !isSamePlaybackRun(runId)) return false

        val targetX = x.toInt()
        val targetY = y.toInt()
        val roots = mutableListOf<AccessibilityNodeInfo>()

        try {
            rootInActiveWindow?.let { roots.add(it) }
        } catch (_: Throwable) {}

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (w in windows) {
                    val root = try { w.root } catch (_: Throwable) { null }
                    if (root != null && roots.none { it === root }) {
                        roots.add(root)
                    }
                }
            } catch (_: Throwable) {}
        }

        if (roots.isEmpty()) return false

        var fileUiFound = false
        for (root in roots) {
            val pkg = try { root.packageName?.toString() } catch (_: Throwable) { null }
            if (aarishFileManagerPackageV1(pkg)) {
                fileUiFound = true
                break
            }
        }

        if (!fileUiFound) return false

        var bestNode: AccessibilityNodeInfo? = null
        var bestArea = Int.MAX_VALUE
        var bestDepth = -1
        var scanned = 0

        fun remember(candidate: AccessibilityNodeInfo?, depth: Int) {
            if (candidate == null || !safeVisible(candidate) || !safeEnabled(candidate) || !safeClickable(candidate)) return

            val b = Rect()
            if (!safeBounds(candidate, b)) return
            if (b.width() <= 0 || b.height() <= 0) return

            val area = (b.width().coerceAtLeast(1) * b.height().coerceAtLeast(1))
            val better =
                bestNode == null ||
                    area < bestArea ||
                    (area == bestArea && depth > bestDepth)

            if (better) {
                bestNode = candidate
                bestArea = area
                bestDepth = depth
            }
        }

        try {
            for (root in roots) {
                val stack = java.util.ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
                stack.add(Pair(root, 0))

                while (!stack.isEmpty() && scanned < 2600) {
                    val item = stack.removeLast()
                    val node = item.first
                    val depth = item.second
                    scanned++

                    val bounds = Rect()
                    val boundsOk = safeBounds(node, bounds)
                    val containsPoint = boundsOk &&
                        bounds.width() > 0 &&
                        bounds.height() > 0 &&
                        bounds.contains(targetX, targetY)

                    if (containsPoint) {
                        remember(aarishFileNodeActionCandidateV1(node), depth)
                    }

                    val count = safeChildCount(node)
                    if (count > 0) {
                        for (i in 0 until count) {
                            val child = safeChild(node, i)
                            if (child != null) {
                                stack.add(Pair(child, depth + 1))
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        val node = bestNode ?: return false

        val clicked = try {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                node.performAction(AccessibilityNodeInfo.ACTION_SELECT)
        } catch (_: Throwable) {
            false
        }

        if (clicked) {
            try { android.util.Log.d("AarishAI_FileRescue", "Node rescue click x=$targetX y=$targetY area=$bestArea depth=$bestDepth scanned=$scanned") } catch (_: Throwable) {}
            showTinyToast(label)
            return true
        }

        return false
    }

    private fun aarishShowVisualClickIndicator(x: Float, y: Float) {
        // AARISH_PREMIUM_CLICK_RIPPLE_V1
        // AARISH_PREMIUM_CLICK_RIPPLE_RED_GREEN_SKIN_V1
        handler.post {
            val wm = try {
                getSystemService(android.content.Context.WINDOW_SERVICE) as? android.view.WindowManager
            } catch (_: Throwable) {
                null
            } ?: return@post

            val density = try {
                resources.displayMetrics.density.coerceAtLeast(1f)
            } catch (_: Throwable) {
                2f
            }

            val size = (92f * density).toInt().coerceAtLeast(64)
            val strokeBase = (2.4f * density).coerceAtLeast(2f)

            val rippleView = object : android.view.View(this@AutoActionService) {
                private val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                private val ringPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                private val dotPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                private var progress = 0f

                fun setProgressValue(v: Float) {
                    progress = v.coerceIn(0f, 1f)
                    invalidate()
                }

                override fun onDraw(canvas: android.graphics.Canvas) {
                    super.onDraw(canvas)

                    val cx = width / 2f
                    val cy = height / 2f
                    val ease = 1f - ((1f - progress) * (1f - progress))
                    val alpha = ((1f - progress) * 255f).toInt().coerceIn(0, 255)

                    val radius = (7f * density) + (34f * density * ease)
                    val innerRadius = (4.5f * density) + (5f * density * (1f - progress))

                    glowPaint.shader = android.graphics.RadialGradient(
                        cx,
                        cy,
                        radius.coerceAtLeast(1f),
                        intArrayOf(
                            android.graphics.Color.argb((125 * (1f - progress)).toInt().coerceIn(0, 125), 34, 197, 94),
                            android.graphics.Color.argb((96 * (1f - progress)).toInt().coerceIn(0, 96), 132, 204, 22),
                            android.graphics.Color.argb((82 * (1f - progress)).toInt().coerceIn(0, 82), 239, 68, 68),
                            android.graphics.Color.argb(0, 239, 68, 68)
                        ),
                        floatArrayOf(0f, 0.34f, 0.72f, 1f),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(cx, cy, radius, glowPaint)
                    glowPaint.shader = null

                    ringPaint.style = android.graphics.Paint.Style.STROKE
                    ringPaint.strokeWidth = strokeBase * (1f + 0.35f * (1f - progress))
                    ringPaint.color = android.graphics.Color.argb(alpha, 34, 197, 94)
                    canvas.drawCircle(cx, cy, radius * 0.58f, ringPaint)

                    ringPaint.strokeWidth = strokeBase * 0.9f
                    ringPaint.color = android.graphics.Color.argb((alpha * 0.86f).toInt().coerceIn(0, 255), 239, 68, 68)
                    canvas.drawCircle(cx, cy, radius * 0.82f, ringPaint)

                    ringPaint.strokeWidth = strokeBase * 0.55f
                    ringPaint.color = android.graphics.Color.argb((alpha * 0.65f).toInt().coerceIn(0, 255), 250, 204, 21)
                    canvas.drawCircle(cx, cy, radius * 1.02f, ringPaint)

                    dotPaint.style = android.graphics.Paint.Style.FILL
                    dotPaint.color = android.graphics.Color.argb((225 * (1f - progress)).toInt().coerceIn(0, 225), 74, 222, 128)
                    canvas.drawCircle(cx, cy, innerRadius, dotPaint)

                    dotPaint.color = android.graphics.Color.argb((120 * (1f - progress)).toInt().coerceIn(0, 120), 248, 113, 113)
                    canvas.drawCircle(cx + (2.2f * density), cy - (2.2f * density), innerRadius * 0.58f, dotPaint)
                }
            }.apply {
                alpha = 1f
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                isClickable = false
                isFocusable = false
            }

            val params = android.view.WindowManager.LayoutParams(
                size,
                size,
                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                this.x = (x - size / 2f).toInt()
                this.y = (y - size / 2f).toInt()
            }

            try {
                wm.addView(rippleView, params)

                val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 430L
                    interpolator = android.view.animation.DecelerateInterpolator(1.65f)
                    addUpdateListener { anim ->
                        val v = (anim.animatedValue as? Float) ?: 1f
                        rippleView.setProgressValue(v)
                    }
                    addListener(object : android.animation.Animator.AnimatorListener {
                        override fun onAnimationStart(animation: android.animation.Animator) {}
                        override fun onAnimationRepeat(animation: android.animation.Animator) {}
                        override fun onAnimationCancel(animation: android.animation.Animator) {
                            try {
                                if (rippleView.parent != null) wm.removeViewImmediate(rippleView)
                            } catch (_: Throwable) {}
                        }
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            try {
                                if (rippleView.parent != null) wm.removeViewImmediate(rippleView)
                            } catch (_: Throwable) {}
                        }
                    })
                }

                animator.start()
            } catch (_: Throwable) {
                try {
                    if (rippleView.parent != null) wm.removeViewImmediate(rippleView)
                } catch (_: Throwable) {}
            }
        }
    }


    private fun showTinyToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }


    // AARISH_SHARE_MENU_LIVE_GUARD_V3_AAS_START
    private fun isAarishSemanticBridgeBlockedPackage(pkg: String): Boolean {
        val p = pkg.trim().lowercase()
        return p.isBlank() || p == packageName.lowercase()
    }

    private fun cleanAarishSnapshotText(value: String?): String? {
        return value
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(220)
            ?.takeIf { it.isNotBlank() }
    }

    private fun firstCleanAarishSnapshot(vararg values: String?): String? {
        for (value in values) {
            val cleaned = cleanAarishSnapshotText(value)
            if (!cleaned.isNullOrBlank()) return cleaned
        }
        return null
    }


    private fun buildAarishSemanticSnapshotFromEvent(event: AccessibilityEvent): TargetSnapshot? {
        val eventPackage = cleanAarishSnapshotText(event.packageName?.toString()?.lowercase())
        if (eventPackage != null && isAarishSemanticBridgeBlockedPackage(eventPackage)) return null

        val touchedNode = try {
            event.source
        } catch (_: Exception) {
            null
        } ?: return null

        val clickNode = findClickableParent(touchedNode) ?: touchedNode
        if (!safeVisible(clickNode) || !safeEnabled(clickNode)) return null

        val clickBounds = Rect()
        if (!safeBounds(clickNode, clickBounds) || clickBounds.width() <= 0 || clickBounds.height() <= 0) {
            return null
        }

        val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
        val screenArea = (screenW * screenH).coerceAtLeast(1f)
        val areaRatio = ((clickBounds.width().coerceAtLeast(0) * clickBounds.height().coerceAtLeast(0)).toFloat() / screenArea)
            .coerceIn(0f, 9f)

        val clickText = cleanAarishSnapshotText(safeText(clickNode))
        val touchText = cleanAarishSnapshotText(safeText(touchedNode))
        val clickDesc = cleanAarishSnapshotText(safeDesc(clickNode))
        val touchDesc = cleanAarishSnapshotText(safeDesc(touchedNode))
        val clickId = cleanAarishSnapshotText(safeId(clickNode))
        val touchId = cleanAarishSnapshotText(safeId(touchedNode))

        if (
            areaRatio > 0.86f &&
            clickText.isNullOrBlank() &&
            touchText.isNullOrBlank() &&
            clickDesc.isNullOrBlank() &&
            touchDesc.isNullOrBlank() &&
            clickId.isNullOrBlank() &&
            touchId.isNullOrBlank()
        ) {
            return null
        }

        val centerX = clickBounds.exactCenterX().coerceIn(1f, screenW)
        val centerY = clickBounds.exactCenterY().coerceIn(1f, screenH)

        val clickOwn = ownLabelOf(clickNode)
        val touchOwn = ownLabelOf(touchedNode)
        val clickContext = collectNodeTextLimited(clickNode, 84, 860)
        val touchContext = collectNodeTextLimited(touchedNode, 52, 560)
        val siblingContext = listOf(
            collectSiblingText(touchedNode, 420),
            collectSiblingText(clickNode, 620)
        )
            .filter { it.isNotBlank() }
            .joinToString(" | ")
            .take(760)
        val parentContext = collectNodeTextLimited(safeParent(clickNode), 50, 560)
        val grandParentContext = collectNodeTextLimited(safeParent(safeParent(clickNode)), 34, 360)

        return TargetSnapshot(
            targetText = firstCleanAarishSnapshot(clickText, touchText, clickDesc, touchDesc, idTail(clickId), idTail(touchId)),
            targetDesc = firstCleanAarishSnapshot(clickDesc, touchDesc, clickText, touchText),
            targetId = firstCleanAarishSnapshot(clickId, touchId),
            targetClass = firstCleanAarishSnapshot(safeClass(clickNode), safeClass(touchedNode)),
            targetPackage = firstCleanAarishSnapshot(aarishNodePackage(clickNode), aarishNodePackage(touchedNode), eventPackage),

            targetContextText = listOf(clickOwn, touchOwn, clickContext, touchContext, parentContext, grandParentContext)
                .filter { it.isNotBlank() }
                .joinToString(" | ")
                .take(1100),
            targetChildText = listOf(touchContext, clickContext)
                .filter { it.isNotBlank() }
                .joinToString(" | ")
                .take(700),
            targetSiblingText = siblingContext.take(700),
            targetRoleFlags = listOf(
                roleFlagsOf(touchedNode),
                roleFlagsOf(clickNode)
            )
                .filter { it.isNotBlank() }
                .joinToString("|")
                .take(360),
            targetTreePath = extractTreePathDNA(clickNode),

            targetLeft = clickBounds.left,
            targetTop = clickBounds.top,
            targetRight = clickBounds.right,
            targetBottom = clickBounds.bottom,
            xPercent = (centerX / screenW).coerceIn(0f, 1f),
            yPercent = (centerY / screenH).coerceIn(0f, 1f),
            targetWPercent = (clickBounds.width() / screenW).coerceIn(0f, 1f),
            targetHPercent = (clickBounds.height() / screenH).coerceIn(0f, 1f),
            insideXPercent = 0.5f,
            insideYPercent = 0.5f,
            recordedScreenW = screenW.toInt(),
            recordedScreenH = screenH.toInt()
        )
    }

    // AARISH_SHARE_MENU_LIVE_GUARD_V3_AAS_END

        override fun onKeyEvent(event: android.view.KeyEvent): Boolean {
        val fcs = FloatingControlService.instance
        val isVolumeKey =
            event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP ||
            event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN

        if (fcs != null && fcs.isRecordingActive() && isVolumeKey) {
            // ACTION_DOWN + ACTION_UP dono consume karo,
            // warna Android ka volume slider pop-up aa sakta hai.
            if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val ok = if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
                    fcs.recordSystemAction(1)
                    true
                } else {
                    fcs.recordSystemAction(2)
                    true
                }

                if (!ok) showTinyToast("Volume system action fail hua")
            }

            return true
        }

        return super.onKeyEvent(event)
    }



    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val type = event.eventType
        val floating = FloatingControlService.instance

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            floating?.notifyExternalWindowChangedFromAccessibility()
            return
        }

        if (type != AccessibilityEvent.TYPE_VIEW_CLICKED) return

        val service = floating ?: return
        if (!service.shouldRecordAccessibilitySemanticClick()) return

        val pkg = event.packageName?.toString()?.lowercase().orEmpty()
        if (isAarishSemanticBridgeBlockedPackage(pkg)) return

        val snapshot = buildAarishSemanticSnapshotFromEvent(event) ?: return
        service.recordAccessibilitySemanticClickFromSnapshot(snapshot)
    }



    override fun onInterrupt() {
        stopPlaybackInternal(showToast = false)
    }


    // AARISH_ADVANCED_BLINK_STRIKE_GHOST_ENGINE_V1
    // Floating glass remove/add nahi hota. FCS glass ko ghost banata hai,
    // yeh service same gesture ko behind-app par dispatch karti hai.


    // AARISH_ADVANCED_BLINK_STRIKE_GHOST_ENGINE_V2

    private fun aarishBlinkStrikeDispatchGesture(gesture: RecordedGesture, onDone: () -> Unit) {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)

        fun finishOnce() {
            if (finished.compareAndSet(false, true)) {
                main.post { onDone() }
            }
        }

        try {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
                finishOnce()
                return
            }

            val raw0 = gesture.points
                .filter { p ->
                    !p.x.isNaN() && !p.x.isInfinite() &&
                        !p.y.isNaN() && !p.y.isInfinite()
                }
                .sortedBy { it.t.coerceAtLeast(0L) }

            if (raw0.isEmpty()) {
                finishOnce()
                return
            }

            val first = raw0.first()

            // Virtual system actions.
            if (first.x <= -50f) {
                val ok = when (first.x.toInt()) {
                    -100 -> performGlobalAction(GLOBAL_ACTION_BACK)
                    -200 -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                    else -> false
                }
                main.postDelayed({ finishOnce() }, if (ok) 180L else 0L)
                return
            }

            val raw = if (raw0.size <= 180) {
                raw0
            } else {
                val step = kotlin.math.ceil(raw0.size / 180.0).toInt().coerceAtLeast(1)
                raw0.filterIndexed { index, _ ->
                    index == 0 || index == raw0.lastIndex || index % step == 0
                }
            }

            val maxX = (resources.displayMetrics.widthPixels.toFloat() - 2f).coerceAtLeast(2f)
            val maxY = (resources.displayMetrics.heightPixels.toFloat() - 2f).coerceAtLeast(2f)

            val startX = first.x.coerceIn(2f, maxX)
            val startY = first.y.coerceIn(2f, maxY)

            val path = android.graphics.Path()
            path.moveTo(startX, startY)

            var movement = false
            for (i in 1 until raw.size) {
                val p = raw[i]
                val dx = p.x - first.x
                val dy = p.y - first.y

                if (kotlin.math.abs(dx) > 7f || kotlin.math.abs(dy) > 7f) {
                    movement = true
                }

                path.lineTo(
                    (startX + dx).coerceIn(2f, maxX),
                    (startY + dy).coerceIn(2f, maxY)
                )
            }

            if (!movement) {
                // AARISH_HUMAN_LIKE_BLINK_TAP_V1: real finger style firm tap micro-slip
                path.lineTo(
                    (startX + 3.8f).coerceIn(2f, maxX),
                    (startY + 3.8f).coerceIn(2f, maxY)
                )
            }

            val startT = raw0.first().t.coerceAtLeast(0L)
            val endT = raw0.last().t.coerceAtLeast(startT)
            val rawTapDuration = (endT - startT).coerceAtLeast(0L)
            val duration = (if (!movement) kotlin.math.max(145L, rawTapDuration) else kotlin.math.max(55L, rawTapDuration)).coerceAtMost(600000L) // AARISH_HUMAN_LIKE_BLINK_TAP_V1

            // Long tap ko chunks me dispatch karo.
            if (!movement && duration <= 1200L && aarishTryFileManagerNodeRescueV1(startX, startY, null, "File picker clicked")) {
                // AARISH_FILE_MANAGER_RESCUE_BLINK_TAP_V1
                finishOnce()
                return
            }

            if (!movement && duration > 59000L) {
                dispatchLongPressChunksSafe(startX, startY, duration, null) {
                    finishOnce()
                }
                return
            }

            // SMART FIX:
            // Moving/swipe live replay me 59000ms se zyada duration direct StrokeDescription
            // me dena unsafe hai. Clamp karo, warna live replay skip/crash/stuck ho sakta hai.
            val safeDuration = duration.coerceAtMost(59000L)

            val desc = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(
                    android.accessibilityservice.GestureDescription.StrokeDescription(
                        path,
                        0L,
                        safeDuration
                    )
                )
                .build()

            val accepted = dispatchGesture(
                desc,
                object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        finishOnce()
                    }

                    override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        finishOnce()
                    }
                },
                null
            )

            if (!accepted) {
                finishOnce()
                return
            }

            main.postDelayed({
                finishOnce()
            }, (safeDuration + 1650L).coerceAtMost(610000L))
        } catch (_: Throwable) {
            finishOnce()
        }
    }

}

