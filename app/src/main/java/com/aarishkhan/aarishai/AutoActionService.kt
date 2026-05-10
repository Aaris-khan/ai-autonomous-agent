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

    private var activeGestureCount = java.util.concurrent.atomic.AtomicInteger(0)

    private var loopStartTime = 0L
    private var loopCurrentCount = 0

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
        val gestures = GestureStore.load(this)

        if (gestures.isEmpty()) {
            Toast.makeText(this, "Saved Screen Command nahi mila", Toast.LENGTH_SHORT).show()
            return false
        }

        stopPlaybackInternal(showToast = false)
        isPlayingInternal = true

        loopCurrentCount = 0
        loopStartTime = android.os.SystemClock.elapsedRealtime()

        Toast.makeText(this, "Screen Command start", Toast.LENGTH_SHORT).show()

        playSequence(gestures)
        return true
    }

    private fun playSequence(gestures: List<RecordedGesture>) {
        if (!isPlayingInternal) return

        val orderedGestures = gestures.sortedBy { it.delayFromStart }
        val sequenceStartTime = android.os.SystemClock.elapsedRealtime()

        fun finishSequence() {
            loopCurrentCount++

            val mode = GestureStore.getLoopMode(this)
            val value = GestureStore.getLoopValue(this)

            val shouldContinue = when (mode) {
                "ONCE" -> false
                "COUNT" -> loopCurrentCount < value
                "INFINITE" -> true
                "TIME" -> {
                    val elapsedMillis = android.os.SystemClock.elapsedRealtime() - loopStartTime
                    elapsedMillis < (value * 60 * 1000L)
                }
                else -> false
            }

            if (shouldContinue && isPlayingInternal) {
                scheduledTasks.forEach { handler.removeCallbacks(it) }
                scheduledTasks.clear()
                playSequence(orderedGestures)
            } else {
                isPlayingInternal = false
                scheduledTasks.clear()
                activeGestureCount.set(0)
                Toast.makeText(this, "Screen Command complete", Toast.LENGTH_SHORT).show()
            }
        }

        fun scheduleIndex(index: Int) {
            if (!isPlayingInternal) return

            if (index >= orderedGestures.size) {
                val finishWaiter = object : Runnable {
                    override fun run() {
                        scheduledTasks.remove(this)
                        if (!isPlayingInternal) return

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
                    if (!isPlayingInternal) return

                    // Agar previous gesture abhi bhi system mein active hai, wait karo
                    if (activeGestureCount.get() > 0) {
                        scheduledTasks.add(this)
                        handler.postDelayed(this, 60L)
                        return
                    }

                    dispatchOneGesture(gesture)

                    val waitForGestureFinish = object : Runnable {
                        override fun run() {
                            scheduledTasks.remove(this)
                            if (!isPlayingInternal) return

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

        scheduledTasks.forEach { handler.removeCallbacks(it) }
        scheduledTasks.clear()

        if (activeGestureCount.get() > 0) {
            cancelCurrentRunningGesture()
        }

        activeGestureCount.set(0)

        if (showToast) {
            Toast.makeText(this, "Screen Command stopped", Toast.LENGTH_SHORT).show()
        }
    }

    private fun decrementActiveGestureSafely() {
        while (true) {
            val current = activeGestureCount.get()
            if (current <= 0) return
            if (activeGestureCount.compareAndSet(current, current - 1)) return
        }
    }

    // ==========================================================
    // 🔥 LEVEL 1 SMART DISPATCH
    // ==========================================================
    private fun dispatchOneGesture(recordedGesture: RecordedGesture) {
        if (!isPlayingInternal) return

        val points = recordedGesture.points
        if (points.isEmpty()) return

        val firstPoint = points.first()

        // Volume command system gestures
        if (firstPoint.x <= -50f) {
            activeGestureCount.incrementAndGet()

            val ok = when (firstPoint.x.toInt()) {
                -100 -> performGlobalAction(GLOBAL_ACTION_BACK)
                -200 -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                else -> false
            }

            if (!ok) {
                decrementActiveGestureSafely()
                showTinyToast("System action fail hua")
                return
            }

            // BACK/RECENTS ke baad screen transition settle hone do
            handler.postDelayed({
                decrementActiveGestureSafely()
            }, 450L)

            return
        }

        val movement = hasRealMovement(points)

        // Smart node matching tap/long-press ke liye.
        // Swipe/scroll mein original gesture path preserve karna safe hai.
        val match = if (!movement) findBestSmartTarget(recordedGesture) else null

        val screenW = resources.displayMetrics.widthPixels.toFloat()
        val screenH = resources.displayMetrics.heightPixels.toFloat()

        val fallbackX = if (recordedGesture.xPercent > 0f) {
            recordedGesture.xPercent * screenW
        } else {
            firstPoint.x
        }

        val fallbackY = if (recordedGesture.yPercent > 0f) {
            recordedGesture.yPercent * screenH
        } else {
            firstPoint.y
        }

        val startX = if (match != null && match.bounds.width() > 0) {
            match.bounds.left + (recordedGesture.insideXPercent.coerceIn(0f, 1f) * match.bounds.width())
        } else fallbackX

        val startY = if (match != null && match.bounds.height() > 0) {
            match.bounds.top + (recordedGesture.insideYPercent.coerceIn(0f, 1f) * match.bounds.height())
        } else fallbackY

        val duration = max(50L, points.last().t).coerceAtMost(60000L)

        // Normal tap ho aur strong node mil gaya ho, toh direct ACTION_CLICK try karo.
        if (!movement && duration < 450L && match != null && match.score >= 100 && match.bounds.width() < (resources.displayMetrics.widthPixels * 0.35f) && match.bounds.height() < (resources.displayMetrics.heightPixels * 0.15f)) {
            val clickable = findClickableParent(match.node)
            if (clickable != null) {
                val clicked = try {
                    clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } catch (_: Exception) {
                    false
                }

                if (clicked) {
                    showTinyToast("🎯 Smart Click")
                    activeGestureCount.incrementAndGet()

                    // Smart click ka callback nahi hota, isliye next step se pehle short wait
                    handler.postDelayed({
                        decrementActiveGestureSafely()
                    }, 400L)

                    return
                }
            }
        }

        // 🔥 LEVEL 1 HYBRID SELECTION:
        // Pehle wahi original long press jo user ne record kiya.
        // Agar selection handles/menu nahi aaye, tab double-tap + hold retry.
        if (!movement && duration >= 450L) {
            performHybridSelectionRetry(
                startX = startX,
                startY = startY,
                points = points,
                duration = duration
            )
            return
        }

        // Normal tap/swipe fallback
        performGestureAt(startX, startY, points)
    }

    private data class SmartMatch(
        val node: AccessibilityNodeInfo,
        val bounds: Rect,
        val score: Int
    ) {
        val centerX: Float get() = bounds.exactCenterX()
        val centerY: Float get() = bounds.exactCenterY()
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
        val wantedText = gesture.targetText?.trim()
        val wantedDesc = gesture.targetDesc?.trim()

        val wanted = when {
            isToolbarActionLabel(wantedText) -> wantedText
            isToolbarActionLabel(wantedDesc) -> wantedDesc
            else -> null
        } ?: return null

        val wantedLower = wanted.lowercase()
        val roots = mutableListOf<AccessibilityNodeInfo>()

        fun skipRoot(root: AccessibilityNodeInfo): Boolean {
            val pkg = root.packageName?.toString() ?: ""
            if (pkg == packageName) return true
            if (pkg.contains("keyboard", ignoreCase = true)) return true
            if (pkg.contains("inputmethod", ignoreCase = true)) return true
            return false
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (window in windows) {
                    if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        continue
                    }

                    val root = window.root ?: continue
                    if (skipRoot(root)) continue

                    val b = Rect()
                    root.getBoundsInScreen(b)
                    if (b.width() > 0 && b.height() > 0) {
                        roots.add(root)
                    }
                }
            } catch (_: Exception) {
            }
        }

        val activeRoot = rootInActiveWindow
        if (activeRoot != null && !skipRoot(activeRoot)) {
            val b = Rect()
            activeRoot.getBoundsInScreen(b)
            if (b.width() > 0 && b.height() > 0) {
                roots.add(activeRoot)
            }
        }

        var best: SmartMatch? = null
        var visited = 0

        for (root in roots) {
            val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
            stack.add(root)

            while (!stack.isEmpty() && visited < 1200) {
                visited++

                val node = stack.removeLast() ?: continue

                if (!node.isVisibleToUser || !node.isEnabled) {
                    continue
                }

                val text = node.text?.toString()?.trim()?.lowercase()
                val desc = node.contentDescription?.toString()?.trim()?.lowercase()

                val exact =
                    text == wantedLower ||
                    desc == wantedLower

                if (exact) {
                    val clickable = findClickableParent(node) ?: node
                    if (clickable.isVisibleToUser && clickable.isEnabled) {
                        val bounds = Rect()
                        clickable.getBoundsInScreen(bounds)

                        if (bounds.width() > 0 && bounds.height() > 0) {
                            var score = 500

                            // Agar multiple same action mil jaye, screen ke visible/compact toolbar ko prefer karo.
                            if (clickable.isClickable) score += 50

                            // Old coordinate ko sirf tie-breaker banao, main condition nahi.
                            if (gesture.xPercent > 0f && gesture.yPercent > 0f) {
                                val sw = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
                                val sh = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
                                val cxP = bounds.exactCenterX() / sw
                                val cyP = bounds.exactCenterY() / sh
                                val dx = abs(cxP - gesture.xPercent)
                                val dy = abs(cyP - gesture.yPercent)

                                if (dx < 0.25f && dy < 0.25f) score += 20
                            }

                            if (best == null || score > best!!.score) {
                                best = SmartMatch(clickable, Rect(bounds), score)
                            }
                        }
                    }
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) stack.add(child)
                }
            }
        }

        return best
    }


    private fun findBestSmartTarget(gesture: RecordedGesture): SmartMatch? {
        // 🔥 Toolbar action buttons: Copy/Cut/Paste/Share/Translate
        // Inka order change ho sakta hai, isliye exact text ko sabse pehle dhoondo.
        findExactActionButtonAcrossWindows(gesture)?.let {
            return it
        }

        val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

        val fallbackX = if (gesture.xPercent > 0f) {
            gesture.xPercent * screenW
        } else {
            gesture.points.firstOrNull()?.x ?: 0f
        }

        val fallbackY = if (gesture.yPercent > 0f) {
            gesture.yPercent * screenH
        } else {
            gesture.points.firstOrNull()?.y ?: 0f
        }

        val root = getRealAppRootForPoint(fallbackX.toInt(), fallbackY.toInt()) ?: return null

        var best: SmartMatch? = null
        val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)

        var visitedNodes = 0

        while (!stack.isEmpty() && visitedNodes < 900) {
            visitedNodes++
            val node = stack.removeLast() ?: continue

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            if (bounds.width() > 0 && bounds.height() > 0) {
                val score = scoreNode(node, bounds, gesture)
                if (score > 0) {
                    val targetNode = findClickableParent(node) ?: node
                    if (!targetNode.isVisibleToUser || !targetNode.isEnabled) {
                        continue
                    }

                    val targetBounds = Rect()
                    targetNode.getBoundsInScreen(targetBounds)

                    if (targetBounds.width() > 0 && targetBounds.height() > 0) {
                        var finalScore = score + if (targetNode.isClickable) 12 else 0

                        val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
                        val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

                        // Recording mein button/clickable-parent ka size save hota hai.
                        // Isliye final parent bounds par bhi size score do.
                        if (gesture.targetWPercent > 0f && gesture.targetHPercent > 0f) {
                            val wNow = targetBounds.width() / screenW
                            val hNow = targetBounds.height() / screenH
                            val wDiff = kotlin.math.abs(wNow - gesture.targetWPercent)
                            val hDiff = kotlin.math.abs(hNow - gesture.targetHPercent)

                            if (wDiff < 0.04f && hDiff < 0.04f) finalScore += 25
                            else if (wDiff < 0.08f && hDiff < 0.08f) finalScore += 12
                        }

                        // Duplicate Copy/Send jaise cases mein parent/current bounds ki position bhi use karo.
                        if (gesture.xPercent > 0f && gesture.yPercent > 0f) {
                            val cxP = targetBounds.exactCenterX() / screenW
                            val cyP = targetBounds.exactCenterY() / screenH
                            val dx = kotlin.math.abs(cxP - gesture.xPercent)
                            val dy = kotlin.math.abs(cyP - gesture.yPercent)

                            if (dx < 0.06f && dy < 0.06f) finalScore += 35
                            else if (dx < 0.16f && dy < 0.16f) finalScore += 18
                            else if (dx < 0.30f && dy < 0.30f) finalScore += 8
                        }

                        if (best == null || finalScore > best!!.score) {
                            best = SmartMatch(targetNode, Rect(targetBounds), finalScore)
                        }
                    }
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) stack.add(child)
            }
        }

        val finalBest = best ?: return null
        return if (finalBest.score >= 55) finalBest else null
    }

    private fun scoreNode(
        node: AccessibilityNodeInfo,
        bounds: Rect,
        gesture: RecordedGesture
    ): Int {
        if (!node.isVisibleToUser || !node.isEnabled) return 0

        var score = 0

        val nodeText = node.text?.toString()?.trim()
        val nodeDesc = node.contentDescription?.toString()?.trim()
        val nodeId = node.viewIdResourceName?.trim()
        val nodeClass = node.className?.toString()?.trim()

        // Strong identity
        if (!gesture.targetId.isNullOrBlank() && nodeId == gesture.targetId) {
            score += 100
        }

        val exactDescMatch =
            !gesture.targetDesc.isNullOrBlank() &&
            nodeDesc?.equals(gesture.targetDesc, ignoreCase = true) == true

        if (exactDescMatch) {
            score += 90
        }

        val exactTextMatch =
            !gesture.targetText.isNullOrBlank() &&
            nodeText?.equals(gesture.targetText, ignoreCase = true) == true

        if (exactTextMatch) {
            score += 90
        }

        // Medium identity
        if (!exactTextMatch &&
            !gesture.targetText.isNullOrBlank() &&
            nodeText?.contains(gesture.targetText, ignoreCase = true) == true
        ) {
            score += 45
        }

        if (!exactDescMatch &&
            !gesture.targetDesc.isNullOrBlank() &&
            nodeDesc?.contains(gesture.targetDesc, ignoreCase = true) == true
        ) {
            score += 45
        }

        // Icon/button fingerprint
        if (!gesture.targetClass.isNullOrBlank() && !nodeClass.isNullOrBlank()) {
            val savedSimple = gesture.targetClass.substringAfterLast('.').lowercase()
            val nowSimple = nodeClass.substringAfterLast('.').lowercase()

            if (savedSimple == nowSimple ||
                (savedSimple.contains("button") && nowSimple.contains("button")) ||
                (savedSimple.contains("text") && nowSimple.contains("text")) ||
                (savedSimple.contains("image") && nowSimple.contains("image"))
            ) {
                score += 18
            }
        }

        val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

        val wPercentNow = bounds.width() / screenW
        val hPercentNow = bounds.height() / screenH

        if (gesture.targetWPercent > 0f && gesture.targetHPercent > 0f) {
            val wDiff = abs(wPercentNow - gesture.targetWPercent)
            val hDiff = abs(hPercentNow - gesture.targetHPercent)

            if (wDiff < 0.04f && hDiff < 0.04f) score += 20
            else if (wDiff < 0.08f && hDiff < 0.08f) score += 10
        }

        // Relative position: button thoda shift ho sakta hai, isliye soft score
        if (gesture.xPercent > 0f && gesture.yPercent > 0f) {
            val cxP = bounds.exactCenterX() / screenW
            val cyP = bounds.exactCenterY() / screenH

            val dx = abs(cxP - gesture.xPercent)
            val dy = abs(cyP - gesture.yPercent)

            if (dx < 0.05f && dy < 0.05f) score += 60
            else if (dx < 0.15f && dy < 0.15f) score += 30
            else if (dx < 0.28f && dy < 0.28f) score += 10
            else if (dx > 0.40f || dy > 0.40f) {
                val strongIdentity =
                    (!gesture.targetId.isNullOrBlank() && nodeId == gesture.targetId) ||
                    exactTextMatch ||
                    exactDescMatch

                score -= if (strongIdentity) 10 else 45
            }
        }

        if (node.isClickable) score += 8
        // Parent traversal scoring removed; candidate normalization happens in findBestSmartTarget().

        // Agar kuch bhi identity nahi hai, sirf random coordinate par trust mat karo.
        val hasAnyIdentity =
            !gesture.targetId.isNullOrBlank() ||
            !gesture.targetText.isNullOrBlank() ||
            !gesture.targetDesc.isNullOrBlank() ||
            !gesture.targetClass.isNullOrBlank() ||
            gesture.targetWPercent > 0f

        if (!hasAnyIdentity) return 0

        return score
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

        // High-density phones par tap drift zyada pixels hota hai.
        val slop = 12f * resources.displayMetrics.density
        return maxDx > slop || maxDy > slop
    }

    private fun performGestureAt(
        startX: Float,
        startY: Float,
        points: List<GesturePoint>
    ) {
        if (points.isEmpty()) return

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            Toast.makeText(this, "Gesture playback ke liye Android 7+ required hai", Toast.LENGTH_LONG).show()
            return
        }

        val firstPoint = points.first()

        val screenW = resources.displayMetrics.widthPixels.toFloat() - 1f
        val screenH = resources.displayMetrics.heightPixels.toFloat() - 1f

        val path = Path()
        path.moveTo(
            startX.coerceIn(1f, screenW),
            startY.coerceIn(1f, screenH)
        )

        var movement = false

        if (points.size > 1) {
            for (i in 1 until points.size) {
                val p = points[i]

                if (abs(p.x - firstPoint.x) > 8f || abs(p.y - firstPoint.y) > 8f) {
                    movement = true
                }

                val shiftedX = startX + (p.x - firstPoint.x)
                val shiftedY = startY + (p.y - firstPoint.y)

                path.lineTo(
                    shiftedX.coerceIn(1f, screenW),
                    shiftedY.coerceIn(1f, screenH)
                )
            }
        }

        if (!movement) {
            val safeX = (startX + 2f).coerceIn(2f, screenW)
            val safeY = (startY + 2f).coerceIn(2f, screenH)
            path.lineTo(safeX, safeY)
        }

        val duration = max(50L, points.last().t).coerceAtMost(60000L)

        try {
            val gesture = GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0,
                        duration
                    )
                )
                .build()

            activeGestureCount.incrementAndGet()

            val accepted = dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        decrementActiveGestureSafely()
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        decrementActiveGestureSafely()
                    }
                },
                null
            )

            if (!accepted) {
                decrementActiveGestureSafely()
            }

        } catch (e: Exception) {
            decrementActiveGestureSafely()
            e.printStackTrace()
            Toast.makeText(
                this,
                "Ek gesture invalid tha, skip kar diya",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    private fun getRealAppRoot(): AccessibilityNodeInfo? {
        return getRealAppRootForPoint(null, null)
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

        val bounds = Rect()
        clickNode.getBoundsInScreen(bounds)

        val safeW = screenW.coerceAtLeast(1f)
        val safeH = screenH.coerceAtLeast(1f)

        return TargetSnapshot(
            targetText = clickNode.text?.toString() ?: touchedNode.text?.toString(),
            targetDesc = clickNode.contentDescription?.toString() ?: touchedNode.contentDescription?.toString(),
            targetId = clickNode.viewIdResourceName ?: touchedNode.viewIdResourceName,
            targetClass = clickNode.className?.toString() ?: touchedNode.className?.toString(),
            targetLeft = bounds.left,
            targetTop = bounds.top,
            targetRight = bounds.right,
            targetBottom = bounds.bottom,
            xPercent = x / safeW,
            yPercent = y / safeH,
            targetWPercent = bounds.width() / safeW,
            targetHPercent = bounds.height() / safeH,
            insideXPercent = if (bounds.width() > 0) ((x - bounds.left).toFloat() / bounds.width()).coerceIn(0f, 1f) else 0.5f,
            insideYPercent = if (bounds.height() > 0) ((y - bounds.top).toFloat() / bounds.height()).coerceIn(0f, 1f) else 0.5f
        )
    }

    private fun findDeepestNodeAtCoordinate(
        root: AccessibilityNodeInfo?,
        x: Int,
        y: Int
    ): AccessibilityNodeInfo? {
        if (root == null) return null

        val stack = java.util.ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.add(Pair(root, 0))

        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = Int.MIN_VALUE

        while (!stack.isEmpty()) {
            val item = stack.removeLast()
            val node = item.first
            val depth = item.second

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            if (bounds.contains(x, y)) {
                val area = (bounds.width() * bounds.height()).coerceAtLeast(1)
                val clickableBonus = if (node.isClickable) 800 else 0
                val leafBonus = if (node.childCount == 0) 300 else 0
                val smallAreaBonus = 500000 / area.coerceAtLeast(1)

                // Deep + clickable + leaf + small area = better actual tapped node
                val candidateScore =
                    (depth * 1000) +
                    clickableBonus +
                    leafBonus +
                    smallAreaBonus

                if (candidateScore > bestScore) {
                    bestScore = candidateScore
                    bestNode = node
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        stack.add(Pair(child, depth + 1))
                    }
                }
            }
        }

        return bestNode
    }


    private fun performHybridSelectionRetry(
        startX: Float,
        startY: Float,
        points: List<GesturePoint>,
        duration: Long
    ) {
        if (!isPlayingInternal) return

        // Whole long-press + retry flow ko ek active step ki tarah hold karo,
        // taaki next recorded step jaldi na chale.
        activeGestureCount.incrementAndGet()

        fun finishFlow() {
            decrementActiveGestureSafely()
        }

        fun runDoubleTapRetry() {
            if (!isPlayingInternal) {
                finishFlow()
                return
            }

            showTinyToast("↻ Select miss, double-tap hold")

            dispatchHybridGesturePath(
                startX = startX,
                startY = startY,
                points = points,
                duration = duration,
                isDoubleTap = true
            ) {
                handler.postDelayed({
                    finishFlow()
                }, 850L)
            }
        }

        // First try: exact original recorded long press
        dispatchHybridGesturePath(
            startX = startX,
            startY = startY,
            points = points,
            duration = duration,
            isDoubleTap = false
        ) { completed ->
            if (!isPlayingInternal) {
                finishFlow()
                return@dispatchHybridGesturePath
            }

            // Agar first gesture cancel/fail ho gaya, tab bhi retry karo.
            if (!completed) {
                runDoubleTapRetry()
                return@dispatchHybridGesturePath
            }

            // Android ko blue handles / floating toolbar dikhane ka time do.
            handler.postDelayed({
                if (!isPlayingInternal) {
                    finishFlow()
                    return@postDelayed
                }

                if (isSelectionUiVisibleHybrid()) {
                    // Success: blue handles/menu aa gaya.
                    finishFlow()
                } else {
                    // Fail: ab double-tap + hold retry.
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
        onDone: (Boolean) -> Unit
    ) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            onDone(false)
            return
        }

        val screenW = resources.displayMetrics.widthPixels.toFloat() - 2f
        val screenH = resources.displayMetrics.heightPixels.toFloat() - 2f

        val safeX = startX.coerceIn(2f, screenW)
        val safeY = startY.coerceIn(2f, screenH)
        val holdDuration = duration.coerceAtLeast(650L).coerceAtMost(3500L)

        val builder = GestureDescription.Builder()

        if (isDoubleTap) {
            val path1 = Path().apply {
                moveTo(safeX, safeY)
                lineTo((safeX + 1f).coerceIn(2f, screenW), (safeY + 1f).coerceIn(2f, screenH))
            }

            val path2 = Path().apply {
                moveTo(safeX, safeY)
                lineTo((safeX + 1f).coerceIn(2f, screenW), (safeY + 1f).coerceIn(2f, screenH))
            }

            builder.addStroke(GestureDescription.StrokeDescription(path1, 0L, 90L))
            builder.addStroke(GestureDescription.StrokeDescription(path2, 150L, holdDuration))
        } else {
            val path = Path().apply {
                moveTo(safeX, safeY)
            }

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

            builder.addStroke(GestureDescription.StrokeDescription(path, 0L, holdDuration))
        }

        val gesture = try {
            builder.build()
        } catch (_: Exception) {
            onDone(false)
            return
        }

        val accepted = dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    onDone(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    onDone(false)
                }
            },
            null
        )

        if (!accepted) {
            onDone(false)
        }
    }

    private fun isSelectionUiVisibleHybrid(): Boolean {
        val root = rootInActiveWindow ?: return false
        val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)

        val markers = listOf(
            "copy",
            "select",
            "select all",
            "cut",
            "paste",
            "share",
            "translate",
            "कॉपी",
            "चुनें",
            "सब चुनें",
            "कट",
            "पेस्ट",
            "अनुवाद"
        )

        var visited = 0

        while (!stack.isEmpty() && visited < 900) {
            visited++

            val node = stack.removeLast()

            try {
                if (
                    node.textSelectionStart >= 0 &&
                    node.textSelectionEnd >= 0 &&
                    node.textSelectionStart != node.textSelectionEnd
                ) {
                    return true
                }
            } catch (_: Exception) {
            }

            if (node.isSelected) return true

            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val id = node.viewIdResourceName?.lowercase() ?: ""
            val label = "$text $desc $id"

            // Floating toolbar / Copy menu detection.
            if (label.contains("floating_toolbar")) return true

            // Marker ko zyada safe rakha: visible clickable toolbar-type node par hi accept.
            if (node.isVisibleToUser && (node.isClickable || node.isFocusable)) {
                if (markers.any { label.contains(it) }) return true
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) stack.add(child)
            }
        }

        return false
    }


    private fun findClickableParent(
        node: AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        var current = node
        var depth = 0

        while (current != null && depth < 30) {
            if (current.isClickable) return current
            val next = current.parent
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
            if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val ok = if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
                    fcs.recordSystemAction(1)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                } else {
                    fcs.recordSystemAction(2)
                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                }

                if (!ok) {
                    showTinyToast("Volume system action fail hua")
                }

                return true
            }

            // ACTION_UP consume mat karo, warna kuch devices par key stuck behavior aa sakta hai
            return false
        }

        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        stopPlaybackInternal(showToast = false)
    }
}
