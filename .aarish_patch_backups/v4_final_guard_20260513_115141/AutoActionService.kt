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


    // AARISH_TAP_ACCURACY_MODE_V1: user-selectable offline tap targeting strictness.

    // AARISH_LATE_TARGET_RETRY_V1: slow/dynamic UI par raw coordinate fallback se pehle short smart retry.
    private fun trySmartTargetAfterShortSettle(
        recordedGesture: RecordedGesture,
        runId: Int,
        waitMs: Long = 190L
    ): Boolean {
        if (!isSamePlaybackRun(runId)) return false
        val points = recordedGesture.points
        if (points.isEmpty()) return false

        val shouldWait = hasStrongSavedIdentity(recordedGesture) ||
            hasSavedPercentAnchor(recordedGesture) ||
            aarishHasAnyRichIdentity(recordedGesture)

        if (!shouldWait) return false

        val token = beginActiveGesture()
        val task = object : Runnable {
            override fun run() {
                scheduledTasks.remove(this)
                try {
                    if (!isSamePlaybackRun(runId)) return

                    val orderedPoints = recordedGesture.points
                        .filter { !it.x.isNaN() && !it.x.isInfinite() && !it.y.isNaN() && !it.y.isInfinite() }
                        .sortedBy { it.t.coerceAtLeast(0L) }

                    if (orderedPoints.isEmpty()) return

                    val movement = hasRealMovement(orderedPoints)
                    val duration = kotlin.math.max(50L, orderedPoints.maxOf { it.t.coerceAtLeast(0L) })
                        .coerceAtMost(600000L)
                    val retryMatch = if (!movement || hasStrongSavedIdentity(recordedGesture) || hasSavedPercentAnchor(recordedGesture)) {
                        findBestSmartTarget(recordedGesture)
                    } else {
                        null
                    }

                    val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
                    val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

                    val orientationMismatch =
                        recordedGesture.recordedScreenW > 0 &&
                            recordedGesture.recordedScreenH > 0 &&
                            ((recordedGesture.recordedScreenW > recordedGesture.recordedScreenH) != (screenW > screenH))

                    if (orientationMismatch && (movement || retryMatch == null)) {
                        showTinyToast("Orientation badal gayi, gesture skip")
                        return
                    }

                    if (!movement && retryMatch == null && hasStrongSavedIdentity(recordedGesture) && smartTapAccuracyMode() == "STRICT") {
                        showTinyToast("Strict: target missing, tap skip")
                        return
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

                    val startX = if (retryMatch != null && retryMatch.bounds.width() > 0) {
                        retryMatch.bounds.left + (recordedGesture.insideXPercent.coerceIn(0f, 1f) * retryMatch.bounds.width())
                    } else {
                        fallbackX
                    }.coerceIn(2f, (screenW - 2f).coerceAtLeast(2f))

                    val startY = if (retryMatch != null && retryMatch.bounds.height() > 0) {
                        retryMatch.bounds.top + (recordedGesture.insideYPercent.coerceIn(0f, 1f) * retryMatch.bounds.height())
                    } else {
                        fallbackY
                    }.coerceIn(2f, (screenH - 2f).coerceAtLeast(2f))

                    if (!movement && duration < 450L && retryMatch != null) {
                        if (performSmartNodeClick(retryMatch, recordedGesture, runId)) return
                    }

                    if (!movement && duration >= 450L) {
                        if (retryMatch != null && !shouldUseSelectionLongPress(retryMatch, recordedGesture)) {
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
                        return
                    }

                    performGestureAt(startX, startY, orderedPoints, runId, recordedGesture)
                } finally {
                    if (isCurrentCallbackRun(runId)) finishActiveGesture(token)
                }
            }
        }

        scheduledTasks.add(task)
        handler.postDelayed(task, waitMs.coerceIn(80L, 420L))
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
                    if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
                    if (window.isActive || window.isFocused) addRoot(window.root)
                }
                for (window in windows) {
                    if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
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
                        if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
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
        val centerX = clickBounds.exactCenterX().coerceIn(1f, screenW)
        val centerY = clickBounds.exactCenterY().coerceIn(1f, screenH)

        val clickText = cleanAarishSnapshotText(safeText(clickNode))
        val touchText = cleanAarishSnapshotText(safeText(touchedNode))
        val clickDesc = cleanAarishSnapshotText(safeDesc(clickNode))
        val touchDesc = cleanAarishSnapshotText(safeDesc(touchedNode))
        val clickId = cleanAarishSnapshotText(safeId(clickNode))
        val touchId = cleanAarishSnapshotText(safeId(touchedNode))

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
        }

        // Jab Android Share Menu / chooser / dialog glass ke upar aa jaye aur user ka real click
        // direct system window par chala jaye, tab bhi macro me semantic click save ho.
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val service = floating ?: return
            if (!service.shouldRecordAccessibilitySemanticClick()) return

            val pkg = event.packageName?.toString()?.lowercase().orEmpty()
            if (isAarishSemanticBridgeBlockedPackage(pkg)) return

            val snapshot = buildAarishSemanticSnapshotFromEvent(event) ?: return
            service.recordAccessibilitySemanticClickFromSnapshot(snapshot)
        }
    }


    override fun onInterrupt() {
        stopPlaybackInternal(showToast = false)
    }
}
