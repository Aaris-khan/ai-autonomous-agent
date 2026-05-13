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
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scheduledTasks = mutableListOf<Runnable>()

    @Volatile
    private var isPlayingInternal = false

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

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        isPlayingInternal = false
        scheduledTasks.forEach { handler.removeCallbacks(it) }
        scheduledTasks.clear()
        handler.removeCallbacksAndMessages(null)

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
        chainVisitedInRun.add(initialConfigName)
        isPlayingInternal = true

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

            val nextConfig = GestureStore.getNextConfig(this@AutoActionService, currentPlayingConfig)

            if (!nextConfig.isNullOrBlank() && !chainVisitedInRun.contains(nextConfig)) {
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
    playbackRunId.incrementAndGet()

    val oldTasks = scheduledTasks.toList()
    scheduledTasks.clear()
    oldTasks.forEach { task ->
        try { handler.removeCallbacks(task) } catch (_: Exception) {}
    }

    // Safety: is handler par playback ke delayed gestures hi scheduled hote hain,
    // isliye stale ghost callbacks ko yahin hard-cancel kar dete hain.
    handler.removeCallbacksAndMessages(null)
    resetActiveGestures()
    chainVisitedInRun.clear()

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
        val safeTimeout = timeoutMs.coerceIn(900L, 65000L)
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
        val duration = kotlin.math.max(50L, points.maxOf { it.t.coerceAtLeast(0L) }).coerceAtMost(60000L)
        val match = if (!movement) findBestSmartTarget(recordedGesture) else null

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
            performHybridSelectionRetry(
                startX = startX,
                startY = startY,
                points = points,
                duration = duration,
                runId = runId
            )
            return
        }

        performGestureAt(startX, startY, points, runId)
    }

    private data class SmartMatch(
        val node: AccessibilityNodeInfo,
        val bounds: Rect,
        val score: Int
    ) {
        val centerX: Float get() = bounds.exactCenterX()
        val centerY: Float get() = bounds.exactCenterY()
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

    if (gesture.recordedScreenW > 0 && gesture.recordedScreenH > 0) return true

    val hasIdentity =
        !gesture.targetId.isNullOrBlank() ||
        !gesture.targetText.isNullOrBlank() ||
        !gesture.targetDesc.isNullOrBlank() ||
        !gesture.targetContextText.isNullOrBlank() ||
        gesture.targetWPercent > 0f ||
        gesture.targetHPercent > 0f

    return hasIdentity
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

    private fun findBestSmartTarget(gesture: RecordedGesture): SmartMatch? {
        return try {
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
            if (roots.isEmpty()) return null

            var best: SmartMatch? = null
            var secondBest: SmartMatch? = null
            val seenBounds = hashSetOf<String>()

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

                while (!stack.isEmpty() && visitedNodes < 2400) {
                    visitedNodes++
                    val node = stack.removeLast()

                    val count = safeChildCount(node)
                    for (i in count - 1 downTo 0) {
                        val child = safeChild(node, i)
                        if (child != null) stack.add(child)
                    }

                    val bounds = Rect()
                    if (!safeBounds(node, bounds) || bounds.width() <= 0 || bounds.height() <= 0) continue

                    val score = scoreNode(node, bounds, gesture)
                    if (score <= 0) continue

                    val targetNode = findClickableParent(node) ?: node
                    if (!safeVisible(targetNode) || !safeEnabled(targetNode)) continue

                    val targetBounds = Rect()
                    if (!safeBounds(targetNode, targetBounds) ||
                        targetBounds.width() <= 0 ||
                        targetBounds.height() <= 0
                    ) continue

                    val key = "${targetBounds.left},${targetBounds.top},${targetBounds.right},${targetBounds.bottom},${safeClass(targetNode)}"
                    val alreadySeen = !seenBounds.add(key)

                    var finalScore = score

                    if (safeClickable(targetNode)) finalScore += 14

                    if (gesture.targetWPercent > 0f && gesture.targetHPercent > 0f) {
                        val wNow = targetBounds.width() / screenW
                        val hNow = targetBounds.height() / screenH
                        val wDiff = kotlin.math.abs(wNow - gesture.targetWPercent)
                        val hDiff = kotlin.math.abs(hNow - gesture.targetHPercent)

                        if (wDiff < 0.04f && hDiff < 0.04f) finalScore += 28
                        else if (wDiff < 0.09f && hDiff < 0.09f) finalScore += 14
                    }

                    if (hasSavedPercentAnchor(gesture)) {
                        val dist = projectedTapDistance(targetBounds, gesture)
                        if (dist < 0.06f) finalScore += 34
                        else if (dist < 0.18f) finalScore += 17
                        else if (dist < 0.35f) finalScore += 6

                        val centerDx = kotlin.math.abs((targetBounds.exactCenterX() / screenW) - gesture.xPercent)
                        val centerDy = kotlin.math.abs((targetBounds.exactCenterY() / screenH) - gesture.yPercent)
                        if (centerDx < 0.08f && centerDy < 0.08f) finalScore += 9
                    }

                    if (alreadySeen) finalScore -= 2

                    remember(SmartMatch(targetNode, Rect(targetBounds), finalScore.coerceAtLeast(0)))
                }
            }

            val finalBest = best ?: return null
            val threshold = smartMatchThreshold(finalBest, gesture)
            if (finalBest.score < threshold) return null

            val runnerUp = secondBest
            if (runnerUp != null && isAmbiguousSmartMatch(finalBest, runnerUp, gesture)) {
                return null
            }

            finalBest
        } catch (_: Exception) {
            null
        }
    }

    private fun scoreNode(
        node: AccessibilityNodeInfo,
        bounds: Rect,
        gesture: RecordedGesture
    ): Int {
        return try {
            if (!safeVisible(node) || !safeEnabled(node)) return 0

            val actionNode = findClickableParent(node) ?: node

            val hasAnyIdentity =
                !gesture.targetId.isNullOrBlank() ||
                    !gesture.targetText.isNullOrBlank() ||
                    !gesture.targetDesc.isNullOrBlank() ||
                    !gesture.targetClass.isNullOrBlank() ||
                    !gesture.targetContextText.isNullOrBlank() ||
                    !gesture.targetChildText.isNullOrBlank() ||
                    !gesture.targetSiblingText.isNullOrBlank() ||
                    !gesture.targetRoleFlags.isNullOrBlank() ||
                    !gesture.targetTreePath.isNullOrBlank() ||
                    gesture.targetWPercent > 0f ||
                    gesture.targetHPercent > 0f ||
                    hasSavedPercentAnchor(gesture)

            if (!hasAnyIdentity) return 0

            var score = 0

            val nodeText = safeText(node)?.trim()
            val nodeDesc = safeDesc(node)?.trim()
            val nodeId = safeId(node)?.trim()
            val nodeClass = safeClass(node)?.trim()
            val actionText = safeText(actionNode)?.trim()
            val actionDesc = safeDesc(actionNode)?.trim()
            val actionId = safeId(actionNode)?.trim()
            val actionClass = safeClass(actionNode)?.trim()

            val nodeSubtree = collectNodeTextLimited(node, 28, 260)
            val actionContext = collectNodeTextLimited(actionNode, 70, 720)
            val siblingText = collectSiblingText(actionNode, 460)
            // AARISH_SMART_SCORE_V3: larger offline fingerprint window for shuffled UI

            val savedText = gesture.targetText?.trim()
            val savedDesc = gesture.targetDesc?.trim()
            val savedId = gesture.targetId?.trim()

            if (!savedId.isNullOrBlank()) {
                if (nodeId == savedId || actionId == savedId) score += 135
                else {
                    val savedTail = idTail(savedId)
                    if (savedTail.isNotBlank() && (idTail(nodeId) == savedTail || idTail(actionId) == savedTail)) {
                        score += 70
                    }
                }
            }

            if (!savedText.isNullOrBlank()) {
                when {
                    labelsEqual(savedText, nodeText) || labelsEqual(savedText, actionText) -> score += 118
                    labelContains(nodeText, savedText) || labelContains(actionText, savedText) -> score += 72
                    labelContains(nodeSubtree, savedText) || labelContains(actionContext, savedText) -> score += 52
                }

                val textSim = maxOf(
                    tokenSimilarity(savedText, nodeText),
                    tokenSimilarity(savedText, actionText),
                    tokenSimilarity(savedText, nodeSubtree),
                    tokenSimilarity(savedText, actionContext)
                )
                if (textSim >= 0.92f) score += 58
                else if (textSim >= 0.70f) score += 34
                else if (textSim >= 0.50f) score += 14
            }

            if (!savedDesc.isNullOrBlank()) {
                when {
                    labelsEqual(savedDesc, nodeDesc) || labelsEqual(savedDesc, actionDesc) -> score += 112
                    labelContains(nodeDesc, savedDesc) || labelContains(actionDesc, savedDesc) -> score += 66
                    labelContains(actionContext, savedDesc) -> score += 42
                }

                val descSim = maxOf(
                    tokenSimilarity(savedDesc, nodeDesc),
                    tokenSimilarity(savedDesc, actionDesc),
                    tokenSimilarity(savedDesc, actionContext)
                )
                if (descSim >= 0.92f) score += 52
                else if (descSim >= 0.70f) score += 28
                else if (descSim >= 0.50f) score += 12
            }

            val contextSimUltra = tokenSimilarity(gesture.targetContextText, actionContext)
            val childSimUltra = tokenSimilarity(gesture.targetChildText, nodeSubtree)
            val siblingSimUltra = tokenSimilarity(gesture.targetSiblingText, siblingText)
            val roleSimUltra = roleSimilarity(gesture.targetRoleFlags, roleFlagsOf(actionNode))
            val dnaSimUltra = dnaSimilarity(gesture.targetTreePath, extractTreePathDNA(actionNode))

            if (contextSimUltra >= 0.88f) score += 72
            else if (contextSimUltra >= 0.66f) score += 40
            else if (contextSimUltra >= 0.45f) score += 16

            if (childSimUltra >= 0.88f) score += 42
            else if (childSimUltra >= 0.66f) score += 22
            else if (childSimUltra >= 0.45f) score += 9

            if (siblingSimUltra >= 0.88f) score += 34
            else if (siblingSimUltra >= 0.66f) score += 17

            if (roleSimUltra >= 0.80f) score += 20
            else if (roleSimUltra >= 0.55f) score += 8

            if (dnaSimUltra >= 0.94f) score += 62
            else if (dnaSimUltra >= 0.74f) score += 32
            else if (dnaSimUltra >= 0.52f) score += 12

            if (!gesture.targetClass.isNullOrBlank()) {
                val savedSimple = gesture.targetClass.substringAfterLast('.').lowercase()
                val nodeSimple = nodeClass?.substringAfterLast('.')?.lowercase().orEmpty()
                val actionSimple = actionClass?.substringAfterLast('.')?.lowercase().orEmpty()

                if (savedSimple == nodeSimple || savedSimple == actionSimple) {
                    score += 28
                } else if (
                    (savedSimple.contains("button") && (nodeSimple.contains("button") || actionSimple.contains("button"))) ||
                    (savedSimple.contains("text") && (nodeSimple.contains("text") || actionSimple.contains("text"))) ||
                    (savedSimple.contains("image") && (nodeSimple.contains("image") || actionSimple.contains("image"))) ||
                    (savedSimple.contains("edit") && (nodeSimple.contains("edit") || actionSimple.contains("edit")))
                ) {
                    score += 16
                }
            }

            val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
            val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

            if (gesture.targetWPercent > 0f && gesture.targetHPercent > 0f) {
                val wPercentNow = bounds.width() / screenW
                val hPercentNow = bounds.height() / screenH
                val wDiff = abs(wPercentNow - gesture.targetWPercent)
                val hDiff = abs(hPercentNow - gesture.targetHPercent)

                if (wDiff < 0.035f && hDiff < 0.035f) score += 32
                else if (wDiff < 0.085f && hDiff < 0.085f) score += 17
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
                if (overlap >= 0.70f) score += 26
                else if (overlap >= 0.35f) score += 12
            }

            if (hasSavedPercentAnchor(gesture)) {
                val projectedX = (
                    bounds.left + (gesture.insideXPercent.coerceIn(0f, 1f) * bounds.width())
                    ) / screenW
                val projectedY = (
                    bounds.top + (gesture.insideYPercent.coerceIn(0f, 1f) * bounds.height())
                    ) / screenH

                val dx = abs(projectedX - gesture.xPercent.coerceIn(0f, 1f))
                val dy = abs(projectedY - gesture.yPercent.coerceIn(0f, 1f))

                val centerDx = abs((bounds.exactCenterX() / screenW) - gesture.xPercent.coerceIn(0f, 1f))
                val centerDy = abs((bounds.exactCenterY() / screenH) - gesture.yPercent.coerceIn(0f, 1f))

                val strongIdentity =
                    exactIdentityHit(node, gesture) ||
                        exactIdentityHit(actionNode, gesture) ||
                        contextSimUltra >= 0.68f ||
                        childSimUltra >= 0.68f ||
                        siblingSimUltra >= 0.72f ||
                        dnaSimUltra >= 0.82f

                if (dx < 0.045f && dy < 0.045f) score += 58
                else if (dx < 0.15f && dy < 0.15f) score += 31
                else if (dx < 0.31f && dy < 0.31f) score += 11
                else if (centerDx < 0.08f && centerDy < 0.08f) score += 18
                else if (!strongIdentity && (dx > 0.45f || dy > 0.45f) && (centerDx > 0.45f || centerDy > 0.45f)) score -= 54
            }

            if (safeClickable(actionNode)) score += 18
            if (safeClickable(node)) score += 8

            val areaRatio = (bounds.width().toFloat() * bounds.height().toFloat()) / (screenW * screenH).coerceAtLeast(1f)
            if (areaRatio > 0.72f && !exactIdentityHit(node, gesture) && !exactIdentityHit(actionNode, gesture)) score -= 22
            if (bounds.width() < 10 || bounds.height() < 10) score -= 18

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

    private fun isAmbiguousSmartMatch(
        best: SmartMatch,
        runnerUp: SmartMatch,
        gesture: RecordedGesture
    ): Boolean {
        val gap = best.score - runnerUp.score
        if (gap >= 26) return false

        val bestExact = exactIdentityHit(best.node, gesture)
        val runnerExact = exactIdentityHit(runnerUp.node, gesture)

        if (bestExact && !runnerExact) return false

        if (hasSavedPercentAnchor(gesture)) {
            val bestDistance = projectedTapDistance(best.bounds, gesture)
            val runnerDistance = projectedTapDistance(runnerUp.bounds, gesture)

            if (bestDistance + 0.055f < runnerDistance) return false
            if (bestExact && runnerExact && bestDistance + 0.035f < runnerDistance) return false
        }

        return hasStrongSavedIdentity(gesture)
    }

    private fun exactIdentityHit(node: AccessibilityNodeInfo?, gesture: RecordedGesture): Boolean {
        if (node == null) return false

        val nodeText = safeText(node)
        val nodeDesc = safeDesc(node)
        val nodeId = safeId(node)
        val nodeContext = collectNodeTextLimited(node, 24, 260)

        val idOk = !gesture.targetId.isNullOrBlank() &&
            (nodeId == gesture.targetId || idTail(nodeId) == idTail(gesture.targetId))

        val textOk = !gesture.targetText.isNullOrBlank() &&
            (labelsEqual(nodeText, gesture.targetText) || labelContains(nodeContext, gesture.targetText))

        val descOk = !gesture.targetDesc.isNullOrBlank() &&
            (labelsEqual(nodeDesc, gesture.targetDesc) || labelContains(nodeContext, gesture.targetDesc))

        return idOk || textOk || descOk
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
        if (exactIdentityHit(match.node, gesture)) return 66
        if (!hasStrongSavedIdentity(gesture)) return 105
        if (!gesture.targetText.isNullOrBlank() || !gesture.targetDesc.isNullOrBlank() || !gesture.targetId.isNullOrBlank()) return 82
        return 92
    }

    private fun collectSmartSearchRoots(tapX: Int, tapY: Int): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        val seen = hashSetOf<String>()
        val myPackage = packageName

        fun isBadPackage(pkg: String): Boolean {
            return pkg.isBlank() ||
                pkg == myPackage ||
                pkg == "android" ||
                pkg == "com.android.systemui" ||
                pkg.contains("inputmethod", ignoreCase = true) ||
                pkg.contains("keyboard", ignoreCase = true)
        }

        fun addRoot(root: AccessibilityNodeInfo?) {
            if (root == null) return
            val pkg = root.packageName?.toString().orEmpty()
            if (isBadPackage(pkg)) return

            val b = Rect()
            if (!safeBounds(root, b) || b.width() <= 0 || b.height() <= 0) return

            val key = "$pkg:${b.left},${b.top},${b.right},${b.bottom}"
            if (seen.add(key)) roots.add(root)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (window in windows) {
                    if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
                    val root = window.root ?: continue
                    val b = Rect()
                    if (!safeBounds(root, b)) continue
                    if (b.contains(tapX, tapY)) addRoot(root)
                }

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
        return roots
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

private fun performSmartNodeClick(match: SmartMatch, gesture: RecordedGesture, runId: Int): Boolean {
        if (!isSamePlaybackRun(runId)) return false

        val actionNode = findBestActionNodeForClick(match.node, gesture) ?: match.node
        if (!safeVisible(actionNode) || !safeEnabled(actionNode) || !safeClickable(actionNode)) return false

        val trusted =
            match.score >= smartMatchThreshold(match, gesture) ||
                exactIdentityHit(actionNode, gesture) ||
                (match.score >= 78 && hasStrongSavedIdentity(gesture))

        if (!trusted) return false

        fun tryClick(node: AccessibilityNodeInfo?): Boolean {
            if (node == null || !safeVisible(node) || !safeEnabled(node) || !safeClickable(node)) return false
            return try {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } catch (_: Exception) {
                false
            }
        }

        var clicked = tryClick(actionNode)

        if (!clicked) {
            try { actionNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS) } catch (_: Exception) {}
            clicked = tryClick(actionNode)
        }

        if (!clicked) {
            var parent = safeParent(actionNode)
            var depth = 0
            while (!clicked && parent != null && depth < 8) {
                if (exactIdentityHit(parent, gesture) || safeClickable(parent)) {
                    clicked = tryClick(parent)
                }
                val next = safeParent(parent)
                if (next === parent) break
                parent = next
                depth++
            }
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
        handler.postDelayed(doneTask, 380L)
        showTinyToast("🎯 Smart Click")
        return true
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
    runId: Int
) {
    if (points.isEmpty() || !isSamePlaybackRun(runId)) return

    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
        Toast.makeText(this, "Gesture playback ke liye Android 7+ required hai", Toast.LENGTH_LONG).show()
        return
    }

    val orderedPoints = points.sortedBy { it.t.coerceAtLeast(0L) }
    val firstPoint = orderedPoints.first()
    val maxPathPoints = 220
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

    val safeStartX = startX.coerceIn(2f, screenW)
    val safeStartY = startY.coerceIn(2f, screenH)

    val path = Path()
    path.moveTo(safeStartX, safeStartY)

    var movement = false

    if (playbackPoints.size > 1) {
        for (i in 1 until playbackPoints.size) {
            val p = playbackPoints[i]

            if (abs(p.x - firstPoint.x) > 8f || abs(p.y - firstPoint.y) > 8f) {
                movement = true
            }

            val shiftedX = safeStartX + (p.x - firstPoint.x)
            val shiftedY = safeStartY + (p.y - firstPoint.y)

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

    val duration = max(50L, orderedPoints.last().t).coerceAtMost(60000L)
    val token = beginActiveGesture()
    var watchdog: Runnable? = null

    try {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
            .build()

        watchdog = scheduleGestureWatchdog(runId, duration + 2500L, token)

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
                pkg == "android" ||
                pkg == "com.android.systemui" ||
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

        val clickText = safeText(clickNode)
        val touchText = safeText(touchedNode)
        val clickDesc = safeDesc(clickNode)
        val touchDesc = safeDesc(touchedNode)
        val clickId = safeId(clickNode)
        val touchId = safeId(touchedNode)

        val clickContext = collectNodeTextLimited(clickNode, 70, 720)
        val touchContext = collectNodeTextLimited(touchedNode, 40, 420)
        val siblingContext = collectSiblingText(clickNode, 460)
        val parentContext = collectNodeTextLimited(safeParent(clickNode), 45, 460)

        return TargetSnapshot(
            targetText = clickText?.takeIf { it.isNotBlank() } ?: touchText?.takeIf { it.isNotBlank() },
            targetDesc = clickDesc?.takeIf { it.isNotBlank() } ?: touchDesc?.takeIf { it.isNotBlank() },
            targetId = clickId?.takeIf { it.isNotBlank() } ?: touchId?.takeIf { it.isNotBlank() },
            targetClass = safeClass(clickNode)?.takeIf { it.isNotBlank() } ?: safeClass(touchedNode)?.takeIf { it.isNotBlank() },

            targetContextText = listOf(clickContext, touchContext, parentContext)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .take(900),
            targetChildText = touchContext.take(520),
            targetSiblingText = siblingContext.take(520),
            targetRoleFlags = roleFlagsOf(clickNode),
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
        val holdDuration = duration.coerceAtLeast(650L).coerceAtMost(3500L)
        val callbackDelivered = java.util.concurrent.atomic.AtomicBoolean(false)
        val timeoutTask = Runnable {
            if (callbackDelivered.compareAndSet(false, true)) {
                onDone(false)
            }
        }
        handler.postDelayed(timeoutTask, holdDuration + if (isDoubleTap) 3200L else 2400L)

        fun finishOnce(value: Boolean) {
            if (callbackDelivered.compareAndSet(false, true)) {
                handler.removeCallbacks(timeoutTask)
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
                    performGlobalAction(GLOBAL_ACTION_BACK)
                } else {
                    fcs.recordSystemAction(2)
                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                }

                if (!ok) showTinyToast("Volume system action fail hua")
            }

            return true
        }

        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        stopPlaybackInternal(showToast = false)
    }
}
