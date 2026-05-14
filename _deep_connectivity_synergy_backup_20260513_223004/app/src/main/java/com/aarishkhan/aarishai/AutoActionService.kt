package com.aarishkhan.aarishai

import android.graphics.RectF

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

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // SMART SWIPE CLASSIFIER
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private val SWIPE_THRESHOLD_PX = 40f

    /**
     * Builds a GestureDescription stroke that is either a tap or a swipe
     * based on whether the path spans > SWIPE_THRESHOLD_PX.
     */
    private fun buildSmartStroke(
        points: List<android.graphics.PointF>,
        durationMs: Long
    ): android.accessibilityservice.GestureDescription.StrokeDescription? {
        if (points.isEmpty()) return null
        val path = android.graphics.Path()
        path.moveTo(points.first().x, points.first().y)
        val isSwipe = points.size > 1 && run {
            val dx = points.last().x - points.first().x
            val dy = points.last().y - points.first().y
            Math.hypot(dx.toDouble(), dy.toDouble()) > SWIPE_THRESHOLD_PX
        }
        if (isSwipe) {
            // Cubic-bezier through all sampled points for natural curve
            for (i in 1 until points.size) {
                path.lineTo(points[i].x, points[i].y)
            }
        }
        return android.accessibilityservice.GestureDescription.StrokeDescription(
            path, 0L, durationMs.coerceAtLeast(60L))
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // MULTI-TAP ENGINE
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Volatile var multiTapCount  = 1       // 1‥10
    @Volatile var multiTapGapMs  = 80L     // ms between taps in a burst

    /**
     * Fires [count] rapid taps at (x, y).
     * Returns immediately; each tap is queued on the coroutine scope.
     */
    fun dispatchMultiTap(x: Float, y: Float, count: Int = multiTapCount,
                         gapMs: Long = multiTapGapMs) {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        scope.launch {
            repeat(count.coerceIn(1, 10)) { i ->
                if (i > 0) kotlinx.coroutines.delay(gapMs)
                val path = android.graphics.Path().apply { moveTo(x, y) }
                val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 60L)
                val gd = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
                dispatchGesture(gd, null, null)
            }
        }
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // HUMAN JITTER ENGINE
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Volatile private var humanJitterEnabled = false
    @Volatile private var humanJitterRadius  = 4f   // px  (0‥20)

    private val _jRng = java.util.Random()

    private fun jx(v: Float) = if (humanJitterEnabled) v + (_jRng.nextFloat() * 2f - 1f) * humanJitterRadius else v
    private fun jy(v: Float) = if (humanJitterEnabled) v + (_jRng.nextFloat() * 2f - 1f) * humanJitterRadius else v

    fun setHumanJitter(on: Boolean, radius: Float = 4f) {
        humanJitterEnabled = on
        humanJitterRadius  = radius.coerceIn(0f, 20f)
    }


    // ═══════════════════════════════════════════════════════════════
    // SNAKE SYNERGY GUARD
    // Prevents replay callbacks from hanging wrong windows / share sheets.
    // ═══════════════════════════════════════════════════════════════
    private var snakeSynergyBlockedUntil = 0L

    private fun snakeSynergyGuardActive(): Boolean {
        return android.os.SystemClock.uptimeMillis() < snakeSynergyBlockedUntil
    }

    private fun snakeMarkTransientSystemUiGuard(event: android.view.accessibility.AccessibilityEvent?) {
        val cls = event?.className?.toString().orEmpty()
        val pkg = event?.packageName?.toString().orEmpty()
        val hit = listOf(
            "ChooserActivity",
            "ResolverActivity",
            "IntentResolver",
            "ShareSheet",
            "ShareActivity"
        ).any { cls.contains(it, true) || pkg.contains(it, true) }

        if (hit) snakeSynergyBlockedUntil = android.os.SystemClock.uptimeMillis() + 1600L
    }


    companion object {
        @Volatile private var instance: AutoActionService? = null

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

        // 🔥 Recording ke time button ki kundali nikalne ke liye
        fun captureTargetSnapshot(
            x: Int,
            y: Int,
            screenW: Float,
            screenH: Float
        ): TargetSnapshot? {
            return instance?.captureTargetSnapshotInternal(x, y, screenW, screenH)
        }

        // AARISH_PRESS_REPLAY_PRO_V2_START
        fun playSingleLiveGestureSafe(gesture: RecordedGesture, onComplete: () -> Unit) {
            val service = instance
            if (service != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                service.fireLiveReplaySafe(gesture, onComplete)
            } else {
                onComplete()
            }
        }

        fun performLiveSystemActionSafe(actionType: Int, onComplete: () -> Unit) {
            val service = instance
            if (service != null) {
                service.performLiveSystemActionInternal(actionType, onComplete)
            } else {
                onComplete()
            }
        }
        // AARISH_PRESS_REPLAY_PRO_V2_END
    }

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
                path.lineTo(
                    (first.x + 1.8f).coerceIn(2f, screenW),
                    (first.y + 1.8f).coerceIn(2f, screenH)
                )
                lastT = max(lastT, 70L)
            }

            val duration = max(55L, lastT).coerceAtMost(600000L)

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
    // AARISH_PRESS_REPLAY_PRO_V2_END

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
    private fun playRecordedGestures(): Boolean {
        initialConfigName = GestureStore.getActiveConfigName(this)
        currentPlayingConfig = initialConfigName

        val gestures = GestureStore.loadConfig(this, currentPlayingConfig)

        if (gestures.isEmpty()) {
            Toast.makeText(this, "Saved Screen Command nahi mila", Toast.LENGTH_SHORT).show()
            return false
        }

        stopPlaybackInternal(showToast = false)
        val runId = playbackRunId.incrementAndGet()
        resetActiveGestures()
        chainVisitedInRun.clear()
        configCycleCounters.clear()
        chainVisitedInRun.add(initialConfigName)
        isPlayingInternal = true
        acquirePlaybackWakeLocks()

        loopCurrentCount = 0
        loopStartTime = android.os.SystemClock.elapsedRealtime()

        Toast.makeText(this, "Workflow start: $currentPlayingConfig", Toast.LENGTH_SHORT).show()

        playSequence(gestures, runId)
        return true
    }

    private fun playSequence(gestures: List<RecordedGesture>, runId: Int) {
        if (!isSamePlaybackRun(runId)) return

        val orderedGestures = gestures.sortedBy { it.delayFromStart }
        val sequenceStartTime = android.os.SystemClock.elapsedRealtime()

        fun finishSequence() {
            if (!isSamePlaybackRun(runId)) return

            val nextOptions = GestureStore.getNextConfigList(this@AutoActionService, currentPlayingConfig)
                .filter { option ->
                    option.isNotBlank() &&
                        !chainVisitedInRun.contains(option) &&
                        GestureStore.hasRecordingForConfig(this@AutoActionService, option)
                }
                .distinct()

            val nextConfig = if (nextOptions.isNotEmpty()) {
                val cycleIndex = (configCycleCounters[currentPlayingConfig] ?: 0).coerceAtLeast(0)
                configCycleCounters[currentPlayingConfig] = cycleIndex + 1
                nextOptions[cycleIndex % nextOptions.size]
            } else {
                null
            }

            if (!nextConfig.isNullOrBlank()) {
                val nextGestures = GestureStore.loadConfig(this@AutoActionService, nextConfig)

                if (nextGestures.isNotEmpty()) {
                    currentPlayingConfig = nextConfig
                    chainVisitedInRun.add(nextConfig)

                    scheduledTasks.forEach { handler.removeCallbacks(it) }
                    scheduledTasks.clear()

                    val nextTask = object : Runnable {
                        override fun run() {
                            scheduledTasks.remove(this)
                            if (isSamePlaybackRun(runId)) {
                                Toast.makeText(this@AutoActionService, "Next: $nextConfig", Toast.LENGTH_SHORT).show()
                                playSequence(nextGestures, runId)
                            }
                        }
                    }

                    scheduledTasks.add(nextTask)
                    handler.postDelayed(nextTask, 500L)
                    return
                }
            }

            loopCurrentCount++

            val mode = GestureStore.getLoopModeForConfig(this@AutoActionService, initialConfigName)
            val value = GestureStore.getLoopValueForConfig(this@AutoActionService, initialConfigName)

            val shouldContinue = when (mode) {
                "ONCE" -> false
                "COUNT" -> loopCurrentCount < value
                "INFINITE" -> true
                "TIME" -> {
                    val elapsedMillis = android.os.SystemClock.elapsedRealtime() - loopStartTime
                    elapsedMillis < (value.toLong().coerceAtLeast(1L) * 60_000L)
                }
                else -> false
            }

            if (shouldContinue && isSamePlaybackRun(runId)) {
                scheduledTasks.forEach { handler.removeCallbacks(it) }
                scheduledTasks.clear()

                currentPlayingConfig = initialConfigName
                chainVisitedInRun.clear()
                chainVisitedInRun.add(initialConfigName)

                val firstGestures = GestureStore.loadConfig(this@AutoActionService, initialConfigName)

                if (firstGestures.isEmpty()) {
                    isPlayingInternal = false
        releasePlaybackWakeLocks()
                    resetActiveGestures()
                    Toast.makeText(this@AutoActionService, "Workflow start config empty hai", Toast.LENGTH_SHORT).show()
                    return
                }

                val loopTask = object : Runnable {
                    override fun run() {
                        scheduledTasks.remove(this)
                        if (isSamePlaybackRun(runId)) {
                            Toast.makeText(this@AutoActionService, "Loop restart", Toast.LENGTH_SHORT).show()
                            playSequence(firstGestures, runId)
                        }
                    }
                }

                scheduledTasks.add(loopTask)
                handler.postDelayed(loopTask, 700L)
            } else if (isCurrentCallbackRun(runId)) {
                isPlayingInternal = false
        releasePlaybackWakeLocks()
                scheduledTasks.forEach { handler.removeCallbacks(it) }
                scheduledTasks.clear()
                resetActiveGestures()
                chainVisitedInRun.clear()
                Toast.makeText(this@AutoActionService, "Workflow complete", Toast.LENGTH_SHORT).show()
            }
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

                    dispatchOneGesture(gesture, runId)

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

    // ==========================================================
    // 🧠 OFFLINE SMART DISPATCH v4: ID + text + desc + context + geometry + ambiguity guard
    // ==========================================================

    private fun dispatchOneGesture(recordedGesture: RecordedGesture, runId: Int) {
        if (!isSamePlaybackRun(runId)) return

        val points = recordedGesture.points
        if (points.isEmpty()) return

        val firstPoint = points.first()

        if (firstPoint.x <= -50f) {
            val token = beginActiveGesture()

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

        val movement = hasRealMovement(points)
        val duration = kotlin.math.max(50L, points.maxOf { it.t.coerceAtLeast(0L) }).coerceAtMost(600000L)
        val match = if (!movement || hasStrongSavedIdentity(recordedGesture) || hasSavedPercentAnchor(recordedGesture)) {
            findBestSmartTarget(recordedGesture)
        } else {
            null
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

    private inline fun <T> safeRead(defaultValue: T, block: () -> T): T {
        return try {
            block()
        } catch (_: IllegalStateException) {
            defaultValue
        } catch (_: Exception) {
            defaultValue
        }
    }

    private fun safeVisible(node: AccessibilityNodeInfo?): Boolean =
        safeRead(false) { node?.isVisibleToUser == true }

    private fun safeEnabled(node: AccessibilityNodeInfo?): Boolean =
        safeRead(false) { node?.isEnabled == true }

    private fun safeClickable(node: AccessibilityNodeInfo?): Boolean =
        safeRead(false) { node?.isClickable == true }

    private fun safeChildCount(node: AccessibilityNodeInfo?): Int =
        safeRead(0) { node?.childCount ?: 0 }

    private fun safeChild(node: AccessibilityNodeInfo?, index: Int): AccessibilityNodeInfo? =
        safeRead(null) { node?.getChild(index) }

    private fun safeParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
        safeRead(null) { node?.parent }

    private fun safeText(node: AccessibilityNodeInfo?): String? =
        safeRead(null) { node?.text?.toString() }

    private fun safeDesc(node: AccessibilityNodeInfo?): String? =
        safeRead(null) { node?.contentDescription?.toString() }

    private fun safeId(node: AccessibilityNodeInfo?): String? =
        safeRead(null) { node?.viewIdResourceName }

    private fun safeClass(node: AccessibilityNodeInfo?): String? =
        safeRead(null) { node?.className?.toString() }

    private fun safePackage(node: AccessibilityNodeInfo?): String? =
        safeRead(null) { node?.packageName?.toString() }

    private fun safeBounds(node: AccessibilityNodeInfo?, out: Rect): Boolean =
        safeRead(false) {
            node?.getBoundsInScreen(out)
            node != null
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

        safeNodeRead(false) { node.isClickable }.let { if (it) flags.add("click") }
        safeNodeRead(false) { node.isLongClickable }.let { if (it) flags.add("long") }
        safeNodeRead(false) { node.isEditable }.let { if (it) flags.add("edit") }
        safeNodeRead(false) { node.isScrollable }.let { if (it) flags.add("scroll") }
        safeNodeRead(false) { node.isCheckable }.let { if (it) flags.add("check") }
        safeNodeRead(false) { node.isChecked }.let { if (it) flags.add("checked") }
        safeNodeRead(false) { node.isEnabled }.let { if (it) flags.add("enabled") }
        safeNodeRead(false) { node.isVisibleToUser }.let { if (it) flags.add("visible") }
        safeNodeRead(false) { node.isFocusable }.let { if (it) flags.add("focus") }

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
        return structFindAtOriginalPoint(gesture)
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

        val roots = collectSmartSearchRoots(fallbackX.toInt(), fallbackY.toInt())
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


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // SUPREME PRECISION ENGINE — ZERO FALSE POSITIVES
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private companion object {
        private const val SUPREME_REJECT_SCORE = -9999
        private const val SUPREME_ACCEPT_SCORE = 9000
    }

    private fun supremeOriginalPoint(gesture: RecordedGesture): Pair<Float, Float> {
        val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
        val first = gesture.points.firstOrNull()

        val x = if (gesture.xPercent > 0f) {
            gesture.xPercent.coerceIn(0f, 1f) * screenW
        } else {
            first?.x ?: 0f
        }

        val y = if (gesture.yPercent > 0f) {
            gesture.yPercent.coerceIn(0f, 1f) * screenH
        } else {
            first?.y ?: 0f
        }

        return x.coerceIn(0f, screenW) to y.coerceIn(0f, screenH)
    }

    private fun supremeText(node: AccessibilityNodeInfo?): String? =
        try { node?.text?.toString()?.trim()?.takeIf { it.isNotBlank() } } catch (_: Exception) { null }

    private fun supremeDesc(node: AccessibilityNodeInfo?): String? =
        try { node?.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() } } catch (_: Exception) { null }

    private fun supremeId(node: AccessibilityNodeInfo?): String? =
        try { node?.viewIdResourceName?.trim()?.takeIf { it.isNotBlank() } } catch (_: Exception) { null }

    private fun supremeClass(node: AccessibilityNodeInfo?): String? =
        try { node?.className?.toString()?.trim()?.takeIf { it.isNotBlank() } } catch (_: Exception) { null }

    private fun supremeVisible(node: AccessibilityNodeInfo?): Boolean =
        try { node?.isVisibleToUser == true } catch (_: Exception) { false }

    private fun supremeEnabled(node: AccessibilityNodeInfo?): Boolean =
        try { node?.isEnabled == true } catch (_: Exception) { false }

    private fun supremeClickable(node: AccessibilityNodeInfo?): Boolean =
        try { node?.isClickable == true } catch (_: Exception) { false }

    private fun supremeChildCount(node: AccessibilityNodeInfo?): Int =
        try { node?.childCount ?: 0 } catch (_: Exception) { 0 }

    private fun supremeChild(node: AccessibilityNodeInfo?, index: Int): AccessibilityNodeInfo? =
        try { node?.getChild(index) } catch (_: Exception) { null }

    private fun supremeParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
        try { node?.parent } catch (_: Exception) { null }

    private fun supremeBounds(node: AccessibilityNodeInfo?, out: Rect): Boolean =
        try {
            node?.getBoundsInScreen(out)
            node != null && out.width() > 0 && out.height() > 0
        } catch (_: Exception) {
            false
        }

    private fun supremeClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var cur = node
        var depth = 0
        while (cur != null && depth < 24) {
            if (supremeClickable(cur)) return cur
            val next = supremeParent(cur)
            if (next === cur) break
            cur = next
            depth++
        }
        return null
    }

    private fun supremeDeepestAt(root: AccessibilityNodeInfo?, x: Int, y: Int): AccessibilityNodeInfo? {
        if (root == null) return null
        val stack = java.util.ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.add(root to 0)

        var best: AccessibilityNodeInfo? = null
        var bestScore = Int.MIN_VALUE

        while (!stack.isEmpty()) {
            val item = stack.removeLast()
            val node = item.first
            val depth = item.second
            val b = Rect()
            if (!supremeBounds(node, b)) continue

            if (b.contains(x, y)) {
                val area = (b.width() * b.height()).coerceAtLeast(1)
                val score =
                    depth * 1000 +
                    if (supremeClickable(node)) 700 else 0 +
                    if (supremeChildCount(node) == 0) 250 else 0 +
                    (250000 / area)

                if (score > bestScore) {
                    bestScore = score
                    best = node
                }

                val c = supremeChildCount(node)
                for (i in 0 until c) {
                    supremeChild(node, i)?.let { stack.add(it to depth + 1) }
                }
            }
        }

        return best
    }

    private fun supremeNorm(s: String?): String =
        s.orEmpty().trim().lowercase().replace(Regex("\\s+"), " ")

    private fun supremeIdTail(s: String?): String =
        s.orEmpty().substringAfterLast("/").trim().lowercase()

    private fun supremeClassSimple(s: String?): String =
        s.orEmpty().substringAfterLast(".").trim().lowercase()

    private fun supremeExactLabelMatch(saved: String?, now: String?): Boolean {
        val a = supremeNorm(saved)
        val b = supremeNorm(now)
        return a.isNotBlank() && b.isNotBlank() && a == b
    }

    private fun supremeIdMatch(saved: String?, now: String?): Boolean {
        val a = saved?.trim().orEmpty()
        val b = now?.trim().orEmpty()
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        return supremeIdTail(a).isNotBlank() && supremeIdTail(a) == supremeIdTail(b)
    }

    private fun supremeClassMatch(saved: String?, now: String?): Boolean {
        val a = supremeClassSimple(saved)
        val b = supremeClassSimple(now)
        if (a.isBlank()) return true
        if (b.isBlank()) return false
        return a == b
    }

    private fun supremePrimaryIdentityOk(node: AccessibilityNodeInfo?, gesture: RecordedGesture): Boolean {
        if (node == null) return false

        val action = supremeClickableParent(node) ?: node

        val nodeText = supremeText(node)
        val nodeDesc = supremeDesc(node)
        val nodeId = supremeId(node)
        val nodeClass = supremeClass(node)

        val actionText = supremeText(action)
        val actionDesc = supremeDesc(action)
        val actionId = supremeId(action)
        val actionClass = supremeClass(action)

        val savedId = gesture.targetId?.trim()
        val savedText = gesture.targetText?.trim()
        val savedDesc = gesture.targetDesc?.trim()
        val savedClass = gesture.targetClass?.trim()

        val hasSavedId = !savedId.isNullOrBlank()
        val hasSavedText = !savedText.isNullOrBlank()
        val hasSavedDesc = !savedDesc.isNullOrBlank()
        val hasPrimary = hasSavedId || hasSavedText || hasSavedDesc

        if (!hasPrimary) return false

        if (hasSavedId && !supremeIdMatch(savedId, nodeId) && !supremeIdMatch(savedId, actionId)) {
            return false
        }

        if (hasSavedText) {
            val textOk =
                supremeExactLabelMatch(savedText, nodeText) ||
                supremeExactLabelMatch(savedText, actionText) ||
                supremeExactLabelMatch(savedText, nodeDesc) ||
                supremeExactLabelMatch(savedText, actionDesc)

            if (!textOk) return false
        }

        if (hasSavedDesc) {
            val descOk =
                supremeExactLabelMatch(savedDesc, nodeDesc) ||
                supremeExactLabelMatch(savedDesc, actionDesc) ||
                supremeExactLabelMatch(savedDesc, nodeText) ||
                supremeExactLabelMatch(savedDesc, actionText)

            if (!descOk) return false
        }

        if (!supremeClassMatch(savedClass, nodeClass) && !supremeClassMatch(savedClass, actionClass)) {
            return false
        }

        return true
    }

    private fun supremeSpatialDrift(bounds: Rect, gesture: RecordedGesture, screenW: Float, screenH: Float): Float {
        val point = supremeOriginalPoint(gesture)
        val x = point.first
        val y = point.second

        if (bounds.contains(x.toInt(), y.toInt())) return 0f

        val dx = kotlin.math.abs(bounds.exactCenterX() - x) / screenW.coerceAtLeast(1f)
        val dy = kotlin.math.abs(bounds.exactCenterY() - y) / screenH.coerceAtLeast(1f)
        return kotlin.math.hypot(dx, dy)
    }

    private fun supremeSpatialOk(bounds: Rect, gesture: RecordedGesture, strictRadius: Float): Boolean {
        val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
        val point = supremeOriginalPoint(gesture)

        if (bounds.contains(point.first.toInt(), point.second.toInt())) return true

        val centerDx = kotlin.math.abs(bounds.exactCenterX() - point.first) / screenW
        val centerDy = kotlin.math.abs(bounds.exactCenterY() - point.second) / screenH
        val euclid = kotlin.math.hypot(centerDx, centerDy)

        return centerDx <= strictRadius && centerDy <= strictRadius && euclid <= (strictRadius * 1.25f)
    }

    private fun supremeScoreFor(node: AccessibilityNodeInfo?, bounds: Rect, gesture: RecordedGesture): Int {
        if (node == null) return SUPREME_REJECT_SCORE
        if (!supremeVisible(node) || !supremeEnabled(node)) return SUPREME_REJECT_SCORE

        val radius = try {
            GestureStore.getSupremePrecisionRadiusPercent(this)
        } catch (_: Exception) {
            0.10f
        }

        val exactRadius = try {
            GestureStore.getSupremeExactRadiusPercent(this)
        } catch (_: Exception) {
            0.05f
        }

        if (!supremeSpatialOk(bounds, gesture, radius)) return SUPREME_REJECT_SCORE
        if (!supremePrimaryIdentityOk(node, gesture)) return SUPREME_REJECT_SCORE

        val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
        val drift = supremeSpatialDrift(bounds, gesture, screenW, screenH)

        if (drift > radius) return SUPREME_REJECT_SCORE

        var score = SUPREME_ACCEPT_SCORE

        if (drift <= exactRadius) score += 2000
        score -= (drift * 10000f).toInt()

        val action = supremeClickableParent(node) ?: node
        if (supremeClickable(action)) score += 300

        if (supremeIdMatch(gesture.targetId, supremeId(node)) || supremeIdMatch(gesture.targetId, supremeId(action))) {
            score += 1200
        }

        if (
            supremeExactLabelMatch(gesture.targetText, supremeText(node)) ||
            supremeExactLabelMatch(gesture.targetText, supremeText(action)) ||
            supremeExactLabelMatch(gesture.targetText, supremeDesc(node)) ||
            supremeExactLabelMatch(gesture.targetText, supremeDesc(action))
        ) {
            score += 900
        }

        if (
            supremeExactLabelMatch(gesture.targetDesc, supremeDesc(node)) ||
            supremeExactLabelMatch(gesture.targetDesc, supremeDesc(action)) ||
            supremeExactLabelMatch(gesture.targetDesc, supremeText(node)) ||
            supremeExactLabelMatch(gesture.targetDesc, supremeText(action))
        ) {
            score += 850
        }

        if (supremeClassMatch(gesture.targetClass, supremeClass(node)) || supremeClassMatch(gesture.targetClass, supremeClass(action))) {
            score += 350
        }

        return score
    }

    private fun supremeFindAtOriginalPoint(gesture: RecordedGesture): SmartMatch? {
        return try {
            val point = supremeOriginalPoint(gesture)
            val x = point.first.toInt()
            val y = point.second.toInt()

            val root = getRealAppRootForPoint(x, y) ?: rootInActiveWindow ?: return null
            val touched = supremeDeepestAt(root, x, y) ?: return null
            val action = supremeClickableParent(touched) ?: touched

            val b = Rect()
            if (!supremeBounds(action, b)) return null

            val score = supremeScoreFor(action, b, gesture)
            if (score >= SUPREME_ACCEPT_SCORE) SmartMatch(action, Rect(b), score + 5000) else null
        } catch (_: Exception) {
            null
        }
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ULTRA-STRICT STRUCTURAL PRECISION ENGINE
    // Text-only matching is rejected. X/Y + Size + Class + Identity required.
    // Keyboard/Input nodes rejected unless original target was keyboard/input.
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private companion object {
        private const val STRUCT_REJECT_SCORE = -9999
        private const val STRUCT_ACCEPT_SCORE = 10000
    }

    private fun structScreenW(): Float =
        resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)

    private fun structScreenH(): Float =
        resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

    private fun structOriginalPoint(gesture: RecordedGesture): Pair<Float, Float> {
        val sw = structScreenW()
        val sh = structScreenH()
        val first = gesture.points.firstOrNull()

        val x = if (gesture.xPercent > 0f) {
            gesture.xPercent.coerceIn(0f, 1f) * sw
        } else {
            first?.x ?: 0f
        }

        val y = if (gesture.yPercent > 0f) {
            gesture.yPercent.coerceIn(0f, 1f) * sh
        } else {
            first?.y ?: 0f
        }

        return x.coerceIn(0f, sw) to y.coerceIn(0f, sh)
    }

    private fun structText(node: AccessibilityNodeInfo?): String? =
        try { node?.text?.toString()?.trim()?.takeIf { it.isNotBlank() } } catch (_: Exception) { null }

    private fun structDesc(node: AccessibilityNodeInfo?): String? =
        try { node?.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() } } catch (_: Exception) { null }

    private fun structId(node: AccessibilityNodeInfo?): String? =
        try { node?.viewIdResourceName?.trim()?.takeIf { it.isNotBlank() } } catch (_: Exception) { null }

    private fun structClass(node: AccessibilityNodeInfo?): String? =
        try { node?.className?.toString()?.trim()?.takeIf { it.isNotBlank() } } catch (_: Exception) { null }

    private fun structPackage(node: AccessibilityNodeInfo?): String? =
        try { node?.packageName?.toString()?.trim()?.takeIf { it.isNotBlank() } } catch (_: Exception) { null }

    private fun structVisible(node: AccessibilityNodeInfo?): Boolean =
        try { node?.isVisibleToUser == true } catch (_: Exception) { false }

    private fun structEnabled(node: AccessibilityNodeInfo?): Boolean =
        try { node?.isEnabled == true } catch (_: Exception) { false }

    private fun structClickable(node: AccessibilityNodeInfo?): Boolean =
        try { node?.isClickable == true } catch (_: Exception) { false }

    private fun structEditable(node: AccessibilityNodeInfo?): Boolean =
        try { node?.isEditable == true } catch (_: Exception) { false }

    private fun structFocusable(node: AccessibilityNodeInfo?): Boolean =
        try { node?.isFocusable == true } catch (_: Exception) { false }

    private fun structChildCount(node: AccessibilityNodeInfo?): Int =
        try { node?.childCount ?: 0 } catch (_: Exception) { 0 }

    private fun structChild(node: AccessibilityNodeInfo?, index: Int): AccessibilityNodeInfo? =
        try { node?.getChild(index) } catch (_: Exception) { null }

    private fun structParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
        try { node?.parent } catch (_: Exception) { null }

    private fun structBounds(node: AccessibilityNodeInfo?, out: Rect): Boolean =
        try {
            node?.getBoundsInScreen(out)
            node != null && out.width() > 0 && out.height() > 0
        } catch (_: Exception) {
            false
        }

    private fun structNorm(s: String?): String =
        s.orEmpty().trim().lowercase().replace(Regex("\\s+"), " ")

    private fun structIdTail(s: String?): String =
        s.orEmpty().substringAfterLast("/").trim().lowercase()

    private fun structClassSimple(s: String?): String =
        s.orEmpty().substringAfterLast(".").trim().lowercase()

    private fun structLabelEquals(a: String?, b: String?): Boolean {
        val x = structNorm(a)
        val y = structNorm(b)
        return x.isNotBlank() && y.isNotBlank() && x == y
    }

    private fun structIdEquals(a: String?, b: String?): Boolean {
        val x = a?.trim().orEmpty()
        val y = b?.trim().orEmpty()
        if (x.isBlank() || y.isBlank()) return false
        if (x == y) return true
        val xt = structIdTail(x)
        val yt = structIdTail(y)
        return xt.isNotBlank() && xt == yt
    }

    private fun structClassEquals(saved: String?, now: String?): Boolean {
        val s = structClassSimple(saved)
        val n = structClassSimple(now)
        if (s.isBlank()) return false
        if (n.isBlank()) return false
        return s == n
    }

    private fun structRoleFamily(cls: String?): String {
        val c = structClassSimple(cls)
        return when {
            c.contains("edit") || c.contains("input") -> "input"
            c.contains("button") -> "button"
            c.contains("imagebutton") -> "button"
            c.contains("checkbox") || c.contains("switch") || c.contains("radio") -> "button"
            c.contains("textview") || c.contains("view") -> "view"
            else -> c
        }
    }

    private fun structSameRole(saved: String?, now: String?): Boolean {
        val s = structRoleFamily(saved)
        val n = structRoleFamily(now)
        return s.isNotBlank() && n.isNotBlank() && s == n
    }

    private fun structIsKeyboardOrInputNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val cls = structClass(node).orEmpty().lowercase()
        val pkg = structPackage(node).orEmpty().lowercase()
        val id = structId(node).orEmpty().lowercase()
        val text = structText(node).orEmpty().lowercase()
        val desc = structDesc(node).orEmpty().lowercase()

        if (structEditable(node)) return true

        val merged = "$cls $pkg $id $text $desc"

        return merged.contains("inputmethod") ||
            merged.contains("keyboard") ||
            merged.contains("latinime") ||
            merged.contains("gboard") ||
            merged.contains("ime") ||
            merged.contains("edittext") ||
            merged.contains("textfield") ||
            merged.contains("textinput") ||
            merged.contains("android.inputmethod")
    }

    private fun structOriginalWasKeyboardOrInput(gesture: RecordedGesture): Boolean {
        val cls = gesture.targetClass.orEmpty().lowercase()
        val id = gesture.targetId.orEmpty().lowercase()
        val text = gesture.targetText.orEmpty().lowercase()
        val desc = gesture.targetDesc.orEmpty().lowercase()
        val role = gesture.targetRoleFlags.orEmpty().lowercase()
        val merged = "$cls $id $text $desc $role"

        return merged.contains("inputmethod") ||
            merged.contains("keyboard") ||
            merged.contains("latinime") ||
            merged.contains("gboard") ||
            merged.contains("ime") ||
            merged.contains("edittext") ||
            merged.contains("textfield") ||
            merged.contains("textinput") ||
            merged.contains("edit")
    }

    private fun structRejectKeyboardIfNeeded(node: AccessibilityNodeInfo?, gesture: RecordedGesture): Boolean {
        if (!GestureStore.isAntiKeyboardGuardEnabled(this)) return false
        if (structOriginalWasKeyboardOrInput(gesture)) return false

        var cur = node
        var depth = 0
        while (cur != null && depth < 10) {
            if (structIsKeyboardOrInputNode(cur)) return true
            val next = structParent(cur)
            if (next === cur) break
            cur = next
            depth++
        }

        return false
    }

    private fun structClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var cur = node
        var depth = 0
        while (cur != null && depth < 24) {
            if (structClickable(cur)) return cur
            val next = structParent(cur)
            if (next === cur) break
            cur = next
            depth++
        }
        return null
    }

    private fun structDeepestAt(root: AccessibilityNodeInfo?, x: Int, y: Int): AccessibilityNodeInfo? {
        if (root == null) return null

        val stack = java.util.ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.add(root to 0)

        var best: AccessibilityNodeInfo? = null
        var bestScore = Int.MIN_VALUE

        while (!stack.isEmpty()) {
            val item = stack.removeLast()
            val node = item.first
            val depth = item.second

            val b = Rect()
            if (!structBounds(node, b)) continue

            if (b.contains(x, y)) {
                val area = (b.width() * b.height()).coerceAtLeast(1)
                val score =
                    depth * 1000 +
                    if (structClickable(node)) 700 else 0 +
                    if (structChildCount(node) == 0) 300 else 0 +
                    (300000 / area)

                if (score > bestScore) {
                    bestScore = score
                    best = node
                }

                val c = structChildCount(node)
                for (i in 0 until c) {
                    structChild(node, i)?.let { stack.add(it to depth + 1) }
                }
            }
        }

        return best
    }

    private fun structSpatialDrift(bounds: Rect, gesture: RecordedGesture): Float {
        val sw = structScreenW()
        val sh = structScreenH()
        val p = structOriginalPoint(gesture)

        if (bounds.contains(p.first.toInt(), p.second.toInt())) return 0f

        val dx = kotlin.math.abs(bounds.exactCenterX() - p.first) / sw
        val dy = kotlin.math.abs(bounds.exactCenterY() - p.second) / sh
        return kotlin.math.hypot(dx, dy)
    }

    private fun structSpatialOk(bounds: Rect, gesture: RecordedGesture, radius: Float): Boolean {
        val sw = structScreenW()
        val sh = structScreenH()
        val p = structOriginalPoint(gesture)

        if (bounds.contains(p.first.toInt(), p.second.toInt())) return true

        val dx = kotlin.math.abs(bounds.exactCenterX() - p.first) / sw
        val dy = kotlin.math.abs(bounds.exactCenterY() - p.second) / sh
        val euclid = kotlin.math.hypot(dx, dy)

        return dx <= radius && dy <= radius && euclid <= radius * 1.25f
    }

    private fun structSizeOk(bounds: Rect, gesture: RecordedGesture, tolerance: Float): Boolean {
        val savedW = gesture.targetWPercent
        val savedH = gesture.targetHPercent

        if (savedW <= 0f || savedH <= 0f) return false

        val nowW = bounds.width().toFloat() / structScreenW()
        val nowH = bounds.height().toFloat() / structScreenH()

        val dw = kotlin.math.abs(nowW - savedW)
        val dh = kotlin.math.abs(nowH - savedH)

        return dw <= tolerance && dh <= tolerance
    }

    private fun structIdentityOk(node: AccessibilityNodeInfo?, gesture: RecordedGesture): Boolean {
        if (node == null) return false

        val action = structClickableParent(node) ?: node

        val savedId = gesture.targetId?.trim()
        val savedText = gesture.targetText?.trim()
        val savedDesc = gesture.targetDesc?.trim()
        val savedClass = gesture.targetClass?.trim()

        val nodeId = structId(node)
        val actionId = structId(action)

        val nodeText = structText(node)
        val actionText = structText(action)

        val nodeDesc = structDesc(node)
        val actionDesc = structDesc(action)

        val nodeClass = structClass(node)
        val actionClass = structClass(action)

        val hasSavedId = !savedId.isNullOrBlank()
        val hasSavedText = !savedText.isNullOrBlank()
        val hasSavedDesc = !savedDesc.isNullOrBlank()
        val hasSavedClass = !savedClass.isNullOrBlank()

        if (!hasSavedId && !hasSavedText && !hasSavedDesc && !hasSavedClass) return false

        if (hasSavedId && !structIdEquals(savedId, nodeId) && !structIdEquals(savedId, actionId)) {
            return false
        }

        if (hasSavedText) {
            val ok =
                structLabelEquals(savedText, nodeText) ||
                structLabelEquals(savedText, actionText) ||
                structLabelEquals(savedText, nodeDesc) ||
                structLabelEquals(savedText, actionDesc)
            if (!ok) return false
        }

        if (hasSavedDesc) {
            val ok =
                structLabelEquals(savedDesc, nodeDesc) ||
                structLabelEquals(savedDesc, actionDesc) ||
                structLabelEquals(savedDesc, nodeText) ||
                structLabelEquals(savedDesc, actionText)
            if (!ok) return false
        }

        if (hasSavedClass) {
            val exactClassOk =
                structClassEquals(savedClass, nodeClass) ||
                structClassEquals(savedClass, actionClass)

            val roleOk =
                structSameRole(savedClass, nodeClass) ||
                structSameRole(savedClass, actionClass)

            if (!exactClassOk && !roleOk) return false
        }

        return true
    }

    private fun structScoreFor(node: AccessibilityNodeInfo?, bounds: Rect, gesture: RecordedGesture): Int {
        if (node == null) return STRUCT_REJECT_SCORE
        if (!structVisible(node) || !structEnabled(node)) return STRUCT_REJECT_SCORE
        if (structRejectKeyboardIfNeeded(node, gesture)) return STRUCT_REJECT_SCORE

        val radius = try {
            GestureStore.getStructuralRadiusPercent(this)
        } catch (_: Exception) {
            0.10f
        }

        val exactRadius = try {
            GestureStore.getStructuralExactRadiusPercent(this)
        } catch (_: Exception) {
            0.05f
        }

        val sizeTolerance = try {
            GestureStore.getStructuralSizeTolerancePercent(this)
        } catch (_: Exception) {
            0.10f
        }

        if (!structSpatialOk(bounds, gesture, radius)) return STRUCT_REJECT_SCORE
        if (!structSizeOk(bounds, gesture, sizeTolerance)) return STRUCT_REJECT_SCORE
        if (!structIdentityOk(node, gesture)) return STRUCT_REJECT_SCORE

        val drift = structSpatialDrift(bounds, gesture)
        if (drift > radius) return STRUCT_REJECT_SCORE

        var score = STRUCT_ACCEPT_SCORE

        if (drift <= exactRadius) score += 3000
        score -= (drift * 16000f).toInt()

        val action = structClickableParent(node) ?: node

        if (structClickable(action)) score += 800
        else if (structFocusable(action)) score -= 500

        if (structIdEquals(gesture.targetId, structId(node)) || structIdEquals(gesture.targetId, structId(action))) {
            score += 2500
        }

        if (
            structLabelEquals(gesture.targetText, structText(node)) ||
            structLabelEquals(gesture.targetText, structText(action)) ||
            structLabelEquals(gesture.targetText, structDesc(node)) ||
            structLabelEquals(gesture.targetText, structDesc(action))
        ) {
            score += 1800
        }

        if (
            structLabelEquals(gesture.targetDesc, structDesc(node)) ||
            structLabelEquals(gesture.targetDesc, structDesc(action)) ||
            structLabelEquals(gesture.targetDesc, structText(node)) ||
            structLabelEquals(gesture.targetDesc, structText(action))
        ) {
            score += 1600
        }

        if (structClassEquals(gesture.targetClass, structClass(node)) || structClassEquals(gesture.targetClass, structClass(action))) {
            score += 1300
        } else if (structSameRole(gesture.targetClass, structClass(node)) || structSameRole(gesture.targetClass, structClass(action))) {
            score += 600
        }

        val savedW = gesture.targetWPercent
        val savedH = gesture.targetHPercent
        if (savedW > 0f && savedH > 0f) {
            val nowW = bounds.width().toFloat() / structScreenW()
            val nowH = bounds.height().toFloat() / structScreenH()
            val sizeDiff = kotlin.math.abs(savedW - nowW) + kotlin.math.abs(savedH - nowH)
            score -= (sizeDiff * 10000f).toInt()
            if (sizeDiff <= 0.04f) score += 1300
        }

        return score
    }

    private fun structFindAtOriginalPoint(gesture: RecordedGesture): SmartMatch? {
        return try {
            val p = structOriginalPoint(gesture)
            val x = p.first.toInt()
            val y = p.second.toInt()

            val root = getRealAppRootForPoint(x, y) ?: rootInActiveWindow ?: return null
            val touched = structDeepestAt(root, x, y) ?: return null
            val action = structClickableParent(touched) ?: touched

            val b = Rect()
            if (!structBounds(action, b)) return null

            val score = structScoreFor(action, b, gesture)
            if (score >= STRUCT_ACCEPT_SCORE) {
                SmartMatch(action, Rect(b), score + 8000)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun structScanNearOriginalPoint(gesture: RecordedGesture): SmartMatch? {
        return try {
            structFindAtOriginalPoint(gesture)?.let { return it }

            val p = structOriginalPoint(gesture)
            val x = p.first.toInt()
            val y = p.second.toInt()
            val root = getRealAppRootForPoint(x, y) ?: rootInActiveWindow ?: return null

            val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
            stack.add(root)

            var best: SmartMatch? = null
            var visited = 0

            while (!stack.isEmpty() && visited < 900) {
                visited++
                val node = stack.removeLast()
                val action = structClickableParent(node) ?: node

                val b = Rect()
                if (structBounds(action, b)) {
                    val score = structScoreFor(action, b, gesture)
                    if (score >= STRUCT_ACCEPT_SCORE && (best == null || score > best!!.score)) {
                        best = SmartMatch(action, Rect(b), score)
                    }
                }

                val c = structChildCount(node)
                for (i in 0 until c) {
                    structChild(node, i)?.let { stack.add(it) }
                }
            }

            best?.takeIf { it.score >= STRUCT_ACCEPT_SCORE }
        } catch (_: Exception) {
            null
        }
    }

    private fun findBestSmartTarget(gesture: RecordedGesture): SmartMatch? {
        return structScanNearOriginalPoint(gesture)
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
            collectNodeTextLimited(safeParent(action)), 30, 340),
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
            structScoreFor(node, bounds, gesture)
        } catch (_: Exception) {
            STRUCT_REJECT_SCORE
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

    private fun smartIdentityConfidence(
        node: AccessibilityNodeInfo?,
        gesture: RecordedGesture
    ): Float {
        return try {
            val action = structClickableParent(node) ?: node ?: return 0f
            val b = Rect()
            if (!structBounds(action, b)) return 0f
            val score = structScoreFor(action, b, gesture)
            if (score >= STRUCT_ACCEPT_SCORE) 1f else 0f
        } catch (_: Exception) {
            0f
        }
    }

    private fun isAmbiguousSmartMatch(
        best: SmartMatch,
        runnerUp: SmartMatch,
        gesture: RecordedGesture
    ): Boolean {
        val bestOk = exactIdentityHit(best.node, gesture)
        val runnerOk = exactIdentityHit(runnerUp.node, gesture)

        if (!bestOk) return true
        if (runnerOk && runnerUp.score >= best.score - 1) return true

        val bestDrift = structSpatialDrift(best.bounds, gesture)
        val runnerDrift = structSpatialDrift(runnerUp.bounds, gesture)

        if (runnerOk && runnerDrift + 0.005f < bestDrift) return true

        return false
    }

    private fun exactIdentityHit(node: AccessibilityNodeInfo?, gesture: RecordedGesture): Boolean {
        return try {
            val action = structClickableParent(node) ?: node ?: return false
            val b = Rect()
            if (!structBounds(action, b)) return false
            structScoreFor(action, b, gesture) >= STRUCT_ACCEPT_SCORE
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
        return STRUCT_ACCEPT_SCORE
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
                    if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue

                    val root = window.root ?: continue
                    val b = Rect()
                    if (!safeBounds(root, b)) continue

                    if (b.contains(tapX, tapY)) addRoot(root, 2)
                }

                for (window in windows) {
                    if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
                    if (window.isActive || window.isFocused) addRoot(window.root, 1)
                }

                for (window in windows) {
                    if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
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
        val safeX = (tapBounds.left + (gesture.insideXPercent.coerceIn(0f, 1f) * tapBounds.width()))
            .coerceIn(2f, screenW)
        val safeY = (tapBounds.top + (gesture.insideYPercent.coerceIn(0f, 1f) * tapBounds.height()))
            .coerceIn(2f, screenH)

        val path = Path().apply {
            moveTo(safeX, safeY)
            lineTo((safeX + 1.2f).coerceIn(2f, screenW), (safeY + 1.2f).coerceIn(2f, screenH))
        }

        val token = beginActiveGesture()
        var watchdog: Runnable? = null

        return try {
            val tap = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, 90L))
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
            return finishNodeClickToast("🎯 Smart Node")
        }

        if (performExactSmartTap(match, gesture, runId, tapBounds)) {
            showTinyToast("🎯 Exact Smart Tap")
            return true
        }

        if (tryClickWithFocus(actionNode)) {
            return finishNodeClickToast("🎯 Smart Click")
        }

        var parent = safeParent(actionNode)
        var depth = 0
        while (parent != null && depth < 10) {
            if ((aarishExactOrVeryStrong(parent, gesture) || safeClickable(parent)) && tryClickWithFocus(parent)) {
                return finishNodeClickToast("🎯 Parent Click")
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
        path.lineTo(
            (safeStartX + 1.5f).coerceIn(2f, screenW),
            (safeStartY + 1.5f).coerceIn(2f, screenH)
        )
    }

    val duration = max(50L, orderedPoints.last().t.coerceAtLeast(0L)).coerceAtMost(600000L)

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
                    if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue

                    val root = window.root ?: continue
                    val pkg = safePackage(root).orEmpty()

                    if (isBadPackage(pkg)) continue

                    val bounds = Rect()
                    if (!safeBounds(root, bounds)) continue

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
        val fallbackPkg = safePackage(fallbackRoot).orEmpty()
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
    val grandParentContext = collectNodeTextLimited(safeParent(clickNode)), 34, 360)

    val primaryText = firstClean(clickText, touchText, clickDesc, touchDesc, idTail(clickId), idTail(touchId))
    val primaryDesc = firstClean(clickDesc, touchDesc, clickText, touchText)

    return TargetSnapshot(
        targetText = primaryText,
        targetDesc = primaryDesc,
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

            while (!stack.isEmpty()) {
                val item = stack.removeLast()
                val node = item.first
                val depth = item.second

                val bounds = Rect()
                if (!safeBounds(node, bounds)) continue

                if (bounds.contains(x, y)) {
                    val area = (bounds.width() * bounds.height()).coerceAtLeast(1)
                    val clickableBonus = if (safeClickable(node)) 800 else 0
                    val leafBonus = if (safeChildCount(node) == 0) 300 else 0
                    val smallAreaBonus = 500000 / area.coerceAtLeast(1)

                    val candidateScore = (depth * 1000) + clickableBonus + leafBonus + smallAreaBonus

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

        
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // AARISHAI V6 REPLAY HELPERS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private fun aarishV6TapPath(x: Float, y: Float, maxX: Float, maxY: Float): android.graphics.Path {
        val sx = x.coerceIn(2f, maxX)
        val sy = y.coerceIn(2f, maxY)
        return android.graphics.Path().apply {
            moveTo(sx, sy)
            lineTo((sx + 1f).coerceIn(2f, maxX), (sy + 1f).coerceIn(2f, maxY))
        }
    }

    private fun aarishV6HapticTick() {
        if (!GestureStore.aarishV6IsVibroEnabled(this)) return
        try {
            val vib = getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                vib?.vibrate(
                    android.os.VibrationEffect.createOneShot(
                        18L,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vib?.vibrate(18L)
            }
        } catch (_: Exception) {
        }
    }

    private fun aarishV6MovementPx(points: List<GesturePoint>): Float {
        val first = points.firstOrNull() ?: return 0f
        val last = points.lastOrNull() ?: return 0f
        val dx = last.x - first.x
        val dy = last.y - first.y
        return kotlin.math.hypot(dx, dy)
    }

    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // AARISHAI V7 SAFE ACTION ENGINE
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private val v7RetryHandler = Handler(Looper.getMainLooper())

    @Volatile private var v7ZoneEnabled = false
    private val v7ZoneRect = RectF(0f, 0f, Float.MAX_VALUE, Float.MAX_VALUE)

    @Volatile private var v7AutoStopOnAppSwitch = false
    @Volatile private var v7LockedPackage = ""

    @Volatile private var v7LongPressEnabled = false
    @Volatile private var v7LongPressMs = 800L

    fun v7SetZoneLock(enabled: Boolean, l: Float = 0f, t: Float = 0f, r: Float = Float.MAX_VALUE, b: Float = Float.MAX_VALUE) {
        v7ZoneEnabled = enabled
        v7ZoneRect.set(l, t, r, b)
    }

    fun v7SetAutoStopOnAppSwitch(enabled: Boolean, lockedPackage: String = "") {
        v7AutoStopOnAppSwitch = enabled
        v7LockedPackage = lockedPackage
    }

    fun v7SetLongPressMode(enabled: Boolean, durationMs: Long = 800L) {
        v7LongPressEnabled = enabled
        v7LongPressMs = durationMs.coerceIn(200L, 5000L)
    }

    private fun v7ZoneAllows(x: Float, y: Float): Boolean {
        return !v7ZoneEnabled || v7ZoneRect.contains(x, y)
    }

    private fun v7TapPath(x: Float, y: Float, maxX: Float, maxY: Float): Path {
        val sx = x.coerceIn(2f, maxX)
        val sy = y.coerceIn(2f, maxY)
        return Path().apply {
            moveTo(sx, sy)
            lineTo((sx + 1f).coerceIn(2f, maxX), (sy + 1f).coerceIn(2f, maxY))
        }
    }

    private fun v7HapticTick() {
        if (!GestureStore.v7IsVibroEnabled(this)) return
        try {
            val vib = getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                vib?.vibrate(
                    android.os.VibrationEffect.createOneShot(
                        18L,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vib?.vibrate(18L)
            }
        } catch (_: Exception) {
        }
    }

    private fun v7MovementPx(points: List<GesturePoint>): Float {
        val first = points.firstOrNull() ?: return 0f
        val last = points.lastOrNull() ?: return 0f
        return kotlin.math.hypot(last.x - first.x, last.y - first.y)
    }

    private fun v7SmartDispatch(
        gd: android.accessibilityservice.GestureDescription,
        runId: Int,
        cb: android.accessibilityservice.AccessibilityService.GestureResultCallback? = null,
        maxRetries: Int = 3,
        attempt: Int = 1
    ): Boolean {
        if (!isSamePlaybackRun(runId)) return false
        val ok = dispatchGesture(gd, cb, null)
        if (!ok && attempt < maxRetries && isSamePlaybackRun(runId)) {
            v7RetryHandler.postDelayed({
                v7SmartDispatch(gd, runId, cb, maxRetries, attempt + 1)
            }, 60L)
        }
        return ok
    }

    fun v7DispatchLongPress(x: Float, y: Float, durationMs: Long = v7LongPressMs) {
        if (!v7ZoneAllows(x, y)) return
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return

        val maxX = (resources.displayMetrics.widthPixels.toFloat() - 2f).coerceAtLeast(2f)
        val maxY = (resources.displayMetrics.heightPixels.toFloat() - 2f).coerceAtLeast(2f)
        val path = v7TapPath(x, y, maxX, maxY)

        val gd = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(
                android.accessibilityservice.GestureDescription.StrokeDescription(
                    path,
                    0L,
                    durationMs.coerceIn(200L, 5000L)
                )
            )
            .build()

        dispatchGesture(gd, null, null)
        GestureStore.v7RecordClick(1)
        v7HapticTick()
    }

    fun v7DispatchPinch(cx: Float, cy: Float, fromSpan: Float, toSpan: Float, durationMs: Long = 350L) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return
        if (!v7ZoneAllows(cx, cy)) return

        val safeDuration = durationMs.coerceIn(100L, 2000L)
        val halfFrom = fromSpan.coerceIn(20f, 2000f) / 2f
        val halfTo = toSpan.coerceIn(20f, 2000f) / 2f

        fun stroke(startX: Float, endX: Float): android.accessibilityservice.GestureDescription.StrokeDescription {
            val p = Path().apply {
                moveTo(startX, cy)
                lineTo(endX, cy)
            }
            return android.accessibilityservice.GestureDescription.StrokeDescription(p, 0L, safeDuration)
        }

        val gd = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(stroke(cx - halfFrom, cx - halfTo))
            .addStroke(stroke(cx + halfFrom, cx + halfTo))
            .build()

        dispatchGesture(gd, null, null)
    }

    private fun v7CheckAutoStop(event: AccessibilityEvent?) {
        if (!v7AutoStopOnAppSwitch) return
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (v7LockedPackage.isBlank()) {
            v7LockedPackage = pkg
            return
        }

        if (pkg != v7LockedPackage && !pkg.startsWith(packageName)) {
            try {
                stopPlayback()
            } catch (_: Exception) {
                try {
                    FloatingControlService.instance?.stopLoop()
                } catch (_: Exception) {
                }
            }
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

        if (!v7ZoneAllows(startX, startY)) {
            onDone(true)
            return
        }

        val screenW = (resources.displayMetrics.widthPixels.toFloat() - 2f).coerceAtLeast(2f)
        val screenH = (resources.displayMetrics.heightPixels.toFloat() - 2f).coerceAtLeast(2f)
        val safeX = startX.coerceIn(2f, screenW)
        val safeY = startY.coerceIn(2f, screenH)

        if (v7LongPressEnabled && !isDoubleTap) {
            v7DispatchLongPress(safeX, safeY, v7LongPressMs)
            onDone(true)
            return
        }

        val movedPx = v7MovementPx(points)
        val isSwipe = points.size > 1 && movedPx > 40f
        val scaledDuration = GestureStore.v7ScaledDuration(this, duration)

        val strokeDuration = when {
            isSwipe -> scaledDuration.coerceIn(120L, 5000L)
            isDoubleTap -> scaledDuration.coerceIn(220L, 1500L)
            else -> scaledDuration.coerceIn(55L, 1200L)
        }

        val callbackDelivered = java.util.concurrent.atomic.AtomicBoolean(false)

        fun finishOnce(value: Boolean) {
            if (callbackDelivered.compareAndSet(false, true)) {
                onDone(value)
            }
        }

        fun dispatchPath(path: Path, strokeMs: Long, done: (Boolean) -> Unit) {
            if (!isSamePlaybackRun(runId)) {
                done(false)
                return
            }

            val gd = try {
                android.accessibilityservice.GestureDescription.Builder()
                    .addStroke(
                        android.accessibilityservice.GestureDescription.StrokeDescription(
                            path,
                            0L,
                            strokeMs
                        )
                    )
                    .build()
            } catch (_: Exception) {
                done(false)
                return
            }

            val cb = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    if (isSamePlaybackRun(runId)) {
                        GestureStore.v7RecordClick(1)
                        v7HapticTick()
                        done(true)
                    } else {
                        done(false)
                    }
                }

                override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    done(false)
                }
            }

            val accepted = v7SmartDispatch(gd, runId, cb)
            if (!accepted) done(false)
        }

        fun buildReplayPath(): Path {
            val path = Path().apply { moveTo(safeX, safeY) }
            var hasLine = false
            val first = points.firstOrNull()

            if (first != null && points.size > 1) {
                for (i in 1 until points.size) {
                    val p = points[i]
                    val shiftedX = startX + (p.x - first.x)
                    val shiftedY = startY + (p.y - first.y)
                    path.lineTo(
                        shiftedX.coerceIn(2f, screenW),
                        shiftedY.coerceIn(2f, screenH)
                    )
                    hasLine = true
                }
            }

            if (!hasLine) {
                path.lineTo(
                    (safeX + 1f).coerceIn(2f, screenW),
                    (safeY + 1f).coerceIn(2f, screenH)
                )
            }

            return path
        }

        if (isDoubleTap) {
            dispatchPath(v7TapPath(safeX, safeY, screenW, screenH), 75L) { firstOk ->
                if (!firstOk || !isSamePlaybackRun(runId)) {
                    finishOnce(false)
                } else {
                    handler.postDelayed({
                        if (!isSamePlaybackRun(runId)) {
                            finishOnce(false)
                        } else {
                            dispatchPath(v7TapPath(safeX, safeY, screenW, screenH), strokeDuration) { secondOk ->
                                finishOnce(secondOk && isSamePlaybackRun(runId))
                            }
                        }
                    }, GestureStore.v7HumaniseDelay(this, 120L))
                }
            }
            return
        }

        val burstCount = if (isSwipe) 1 else GestureStore.v7GetMultiTapCount(this).coerceIn(1, 10)

        if (burstCount > 1) {
            fun runBurst(index: Int) {
                if (!isSamePlaybackRun(runId)) {
                    finishOnce(false)
                    return
                }

                dispatchPath(v7TapPath(safeX, safeY, screenW, screenH), 60L) { ok ->
                    if (!ok || !isSamePlaybackRun(runId)) {
                        finishOnce(false)
                    } else if (index >= burstCount - 1) {
                        finishOnce(true)
                    } else {
                        handler.postDelayed(
                            { runBurst(index + 1) },
                            GestureStore.v7HumaniseDelay(this, GestureStore.v7GetMultiTapGapMs(this))
                        )
                    }
                }
            }
            runBurst(0)
            return
        }

        dispatchPath(buildReplayPath(), strokeDuration) { ok ->
            finishOnce(ok && isSamePlaybackRun(runId))
        }
    }

        private fun isSelectionUiVisibleHybrid(): Boolean {
        return try {
            val roots = mutableListOf<AccessibilityNodeInfo>()

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                try {
                    for (window in windows) {
                        if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
                        val root = try { window.root } catch (_: Exception) { null } ?: continue
                        roots.add(root)
                    }
                } catch (_: Exception) {
                }
            }

            rootInActiveWindow?.let { roots.add(it) }
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

                    if (safeNodeRead(false) {
                            node.textSelectionStart >= 0 &&
                            node.textSelectionEnd >= 0 &&
                            node.textSelectionStart != node.textSelectionEnd
                        }
                    ) return true

                    if (safeNodeRead(false) { node.isSelected }) return true

                    val text = safeText(node)?.lowercase() ?: ""
                    val desc = safeDesc(node)?.lowercase() ?: ""
                    val id = safeId(node)?.lowercase() ?: ""
                    val label = "$text $desc $id"

                    if (label.contains("floating_toolbar") || label.contains("selection")) return true
                    if (safeVisible(node)) {
                        val clickableOrFocusable = safeNodeRead(false) { node.isClickable || node.isFocusable }
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
            if (next === current) return null
            current = next
            depth++
        }

        return null
    }

    private fun showTinyToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }


    // AARISH_SHARE_MENU_LIVE_GUARD_V3_AAS_START
    private fun isAarishSemanticBridgeBlockedPackage(pkg: String): Boolean {
        val p = pkg.trim().lowercase()
        return p.isBlank() ||
            p == packageName.lowercase() ||
            p.contains("inputmethod") ||
            p.contains("keyboard")
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
        val grandParentContext = collectNodeTextLimited(safeParent(clickNode)), 34, 360)

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
        v7CheckAutoStop(event)
        snakeMarkTransientSystemUiGuard(event)
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



    
    // Rescue guard: avoid firing replay into Termux/wrong foreground shell
    private fun rescueForegroundPackage(): String? = try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            windows?.firstOrNull {
                it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused
            }?.root?.packageName?.toString()
        } else {
            rootInActiveWindow?.packageName?.toString()
        }
    } catch (_: Exception) { null }

    private fun rescueSafeToDispatch(): Boolean {
        val pkg = rescueForegroundPackage() ?: return true
        if (pkg.contains("termux", ignoreCase = true)) return false
        return true
    }


    override fun onInterrupt() {
        stopPlaybackInternal(showToast = false)
    }
}
