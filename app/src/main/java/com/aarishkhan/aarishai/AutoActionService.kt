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
        private var instance: AutoActionService? = null

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

        gestures.forEach { gesture ->
            val task = object : Runnable {
                override fun run() {
                    scheduledTasks.remove(this)
                    if (isPlayingInternal) {
                        dispatchOneGesture(gesture)
                    }
                }
            }

            scheduledTasks.add(task)
            handler.postDelayed(task, gesture.delayFromStart)
        }

        val finishTask = Runnable {
            loopCurrentCount++

            val mode = GestureStore.getLoopMode(this)
            val value = GestureStore.getLoopValue(this)

            var shouldContinue = false

            when (mode) {
                "ONCE" -> shouldContinue = false
                "COUNT" -> shouldContinue = loopCurrentCount < value
                "INFINITE" -> shouldContinue = true
                "TIME" -> {
                    val elapsedMillis = android.os.SystemClock.elapsedRealtime() - loopStartTime
                    shouldContinue = elapsedMillis < (value * 60 * 1000L)
                }
            }

            if (shouldContinue && isPlayingInternal) {
                scheduledTasks.forEach { handler.removeCallbacks(it) }
                scheduledTasks.clear()
                playSequence(gestures)
            } else {
                isPlayingInternal = false
                scheduledTasks.clear()
                activeGestureCount.set(0)
                Toast.makeText(this, "Screen Command complete", Toast.LENGTH_SHORT).show()
            }
        }

        val totalDuration = gestures.maxOfOrNull { gesture ->
            val gestureDuration = (gesture.points.lastOrNull()?.t ?: 0L).coerceAtMost(60000L)
            gesture.delayFromStart + gestureDuration
        } ?: 0L

        scheduledTasks.add(finishTask)
        handler.postDelayed(finishTask, totalDuration)
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
            when (firstPoint.x.toInt()) {
                -100 -> performGlobalAction(GLOBAL_ACTION_BACK)
                -200 -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
            return
        }

        val match = findBestSmartTarget(recordedGesture)

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

        val movement = hasRealMovement(points)
        val duration = max(50L, points.last().t).coerceAtMost(60000L)

        // Normal tap ho aur strong node mil gaya ho, toh direct ACTION_CLICK try karo.
        if (!movement && duration < 650L && match != null && match.score >= 90 && match.bounds.width() < (resources.displayMetrics.widthPixels * 0.55f)) {
            val clickable = findClickableParent(match.node)
            if (clickable != null) {
                val clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    showTinyToast("🎯 Smart Click")
                    return
                }
            }
        }

        // Tap/long press/swipe sab ke liye shifted path
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

    private fun findBestSmartTarget(gesture: RecordedGesture): SmartMatch? {
        val root = getRealAppRoot() ?: return null

        var best: SmartMatch? = null

        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            if (bounds.width() > 0 && bounds.height() > 0) {
                val score = scoreNode(node, bounds, gesture)
                if (score > 0 && (best == null || score > best!!.score)) {
                    best = SmartMatch(node, Rect(bounds), score)
                }
            }

            for (i in 0 until node.childCount) {
                visit(node.getChild(i))
            }
        }

        visit(root)

        val finalBest = best ?: return null

        // Safety gate:
        // 80+ = strong text/id/desc match
        // 55+ = icon/class/size/position match
        return if (finalBest.score >= 55) finalBest else null
    }

    private fun scoreNode(
        node: AccessibilityNodeInfo,
        bounds: Rect,
        gesture: RecordedGesture
    ): Int {
        var score = 0

        val nodeText = node.text?.toString()?.trim()
        val nodeDesc = node.contentDescription?.toString()?.trim()
        val nodeId = node.viewIdResourceName?.trim()
        val nodeClass = node.className?.toString()?.trim()

        // Strong identity
        if (!gesture.targetId.isNullOrBlank() && nodeId == gesture.targetId) {
            score += 100
        }

        if (!gesture.targetDesc.isNullOrBlank() &&
            nodeDesc?.equals(gesture.targetDesc, ignoreCase = true) == true
        ) {
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

        if (!gesture.targetDesc.isNullOrBlank() &&
            nodeDesc?.contains(gesture.targetDesc, ignoreCase = true) == true
        ) {
            score += 45
        }

        // Icon/button fingerprint
        if (!gesture.targetClass.isNullOrBlank() && nodeClass == gesture.targetClass) {
            score += 20
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
            else if (dx > 0.40f || dy > 0.40f) score -= 50
        }

        if (node.isClickable) score += 8
        if (findClickableParent(node) != null) score += 8

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

        for (i in 1 until points.size) {
            val p = points[i]
            if (abs(p.x - first.x) > 2f || abs(p.y - first.y) > 2f) {
                return true
            }
        }

        return false
    }

    private fun performGestureAt(
        startX: Float,
        startY: Float,
        points: List<GesturePoint>
    ) {
        if (points.isEmpty()) return

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

                if (abs(p.x - firstPoint.x) > 2f || abs(p.y - firstPoint.y) > 2f) {
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
            val safeX = (startX + 0.1f).coerceIn(1f, screenW)
            val safeY = (startY + 0.1f).coerceIn(1f, screenH)
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
        val myPackage = packageName

        try {
            for (window in windows) {
                val root = window.root ?: continue
                val pkg = root.packageName?.toString() ?: ""

                // Apne AarishAI overlay/panel ko skip karo
                if (pkg == myPackage) continue

                val bounds = Rect()
                root.getBoundsInScreen(bounds)

                if (bounds.width() > 0 && bounds.height() > 0) {
                    return root
                }
            }
        } catch (_: Exception) {
        }

        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString() ?: ""
        return if (pkg == myPackage) null else root
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
        val root = getRealAppRoot() ?: return null
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

        val bounds = Rect()
        root.getBoundsInScreen(bounds)

        if (!bounds.contains(x, y)) return null

        var deepest: AccessibilityNodeInfo = root

        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            val match = findDeepestNodeAtCoordinate(child, x, y)
            if (match != null) {
                deepest = match
            }
        }

        return deepest
    }

    private fun findClickableParent(
        node: AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        var current = node

        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
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
                if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
                    fcs.recordSystemAction(1)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                } else if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
                    fcs.recordSystemAction(2)
                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                }
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
