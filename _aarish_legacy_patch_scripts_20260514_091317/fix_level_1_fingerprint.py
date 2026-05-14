import os
import re

BASE = "app/src/main/java/com/aarishkhan/aarishai"
GS = f"{BASE}/GestureStore.kt"
AAS = f"{BASE}/AutoActionService.kt"
FCS = f"{BASE}/FloatingControlService.kt"
ACC = "app/src/main/res/xml/accessibility_service_config.xml"

print("🚀 Level 1 Smart Target Fingerprint Engine apply ho raha hai...")

# ==========================================================
# 1) GestureStore.kt — X/Y ke sath button ki kundali save
# ==========================================================
gesture_store_code = r'''package com.aarishkhan.aarishai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class GesturePoint(
    val x: Float,
    val y: Float,
    val t: Long
)

data class TargetSnapshot(
    val targetText: String? = null,
    val targetDesc: String? = null,
    val targetId: String? = null,
    val targetClass: String? = null,
    val targetLeft: Int = -1,
    val targetTop: Int = -1,
    val targetRight: Int = -1,
    val targetBottom: Int = -1,
    val xPercent: Float = 0f,
    val yPercent: Float = 0f,
    val targetWPercent: Float = 0f,
    val targetHPercent: Float = 0f
)

data class RecordedGesture(
    val delayFromStart: Long,
    val points: List<GesturePoint>,

    // 🔥 Level 1 Smart Fingerprint
    val targetText: String? = null,
    val targetDesc: String? = null,
    val targetId: String? = null,
    val targetClass: String? = null,

    // Recording time bounds
    val targetLeft: Int = -1,
    val targetTop: Int = -1,
    val targetRight: Int = -1,
    val targetBottom: Int = -1,

    // Screen percentage fallback
    val xPercent: Float = 0f,
    val yPercent: Float = 0f,

    // Button size fingerprint
    val targetWPercent: Float = 0f,
    val targetHPercent: Float = 0f
)

object GestureStore {
    private const val PREF_NAME = "screen_command_store"
    private const val KEY_GESTURES = "recorded_gestures"

    private const val KEY_LOOP_MODE = "loop_mode"
    private const val KEY_LOOP_VALUE = "loop_value"

    fun save(context: Context, gestures: List<RecordedGesture>) {
        val mainArray = JSONArray()

        gestures.forEach { gesture ->
            val gestureObject = JSONObject()

            gestureObject.put("delayFromStart", gesture.delayFromStart)

            gestureObject.put("targetText", gesture.targetText ?: "")
            gestureObject.put("targetDesc", gesture.targetDesc ?: "")
            gestureObject.put("targetId", gesture.targetId ?: "")
            gestureObject.put("targetClass", gesture.targetClass ?: "")

            gestureObject.put("targetLeft", gesture.targetLeft)
            gestureObject.put("targetTop", gesture.targetTop)
            gestureObject.put("targetRight", gesture.targetRight)
            gestureObject.put("targetBottom", gesture.targetBottom)

            gestureObject.put("xPercent", gesture.xPercent.toDouble())
            gestureObject.put("yPercent", gesture.yPercent.toDouble())
            gestureObject.put("targetWPercent", gesture.targetWPercent.toDouble())
            gestureObject.put("targetHPercent", gesture.targetHPercent.toDouble())

            val pointsArray = JSONArray()
            gesture.points.forEach { point ->
                val pointObject = JSONObject()
                pointObject.put("x", point.x.toDouble())
                pointObject.put("y", point.y.toDouble())
                pointObject.put("t", point.t)
                pointsArray.put(pointObject)
            }

            gestureObject.put("points", pointsArray)
            mainArray.put(gestureObject)
        }

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GESTURES, mainArray.toString())
            .apply()
    }

    fun load(context: Context): List<RecordedGesture> {
        val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GESTURES, null) ?: return emptyList()

        return try {
            val mainArray = JSONArray(json)
            val result = mutableListOf<RecordedGesture>()

            for (i in 0 until mainArray.length()) {
                val gestureObject = mainArray.getJSONObject(i)

                val delayFromStart = gestureObject.getLong("delayFromStart")

                val targetText = gestureObject.optString("targetText", "").takeIf { it.isNotBlank() }
                val targetDesc = gestureObject.optString("targetDesc", "").takeIf { it.isNotBlank() }
                val targetId = gestureObject.optString("targetId", "").takeIf { it.isNotBlank() }
                val targetClass = gestureObject.optString("targetClass", "").takeIf { it.isNotBlank() }

                val targetLeft = gestureObject.optInt("targetLeft", -1)
                val targetTop = gestureObject.optInt("targetTop", -1)
                val targetRight = gestureObject.optInt("targetRight", -1)
                val targetBottom = gestureObject.optInt("targetBottom", -1)

                val xPercent = gestureObject.optDouble("xPercent", 0.0).toFloat()
                val yPercent = gestureObject.optDouble("yPercent", 0.0).toFloat()
                val targetWPercent = gestureObject.optDouble("targetWPercent", 0.0).toFloat()
                val targetHPercent = gestureObject.optDouble("targetHPercent", 0.0).toFloat()

                val pointsArray = gestureObject.getJSONArray("points")
                val points = mutableListOf<GesturePoint>()

                for (j in 0 until pointsArray.length()) {
                    val pointObject = pointsArray.getJSONObject(j)
                    points.add(
                        GesturePoint(
                            x = pointObject.getDouble("x").toFloat(),
                            y = pointObject.getDouble("y").toFloat(),
                            t = pointObject.getLong("t")
                        )
                    )
                }

                result.add(
                    RecordedGesture(
                        delayFromStart = delayFromStart,
                        points = points,
                        targetText = targetText,
                        targetDesc = targetDesc,
                        targetId = targetId,
                        targetClass = targetClass,
                        targetLeft = targetLeft,
                        targetTop = targetTop,
                        targetRight = targetRight,
                        targetBottom = targetBottom,
                        xPercent = xPercent,
                        yPercent = yPercent,
                        targetWPercent = targetWPercent,
                        targetHPercent = targetHPercent
                    )
                )
            }

            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun hasRecording(context: Context): Boolean {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GESTURES, null)

        return !raw.isNullOrEmpty() && raw != "[]"
    }

    fun totalDuration(context: Context): Long {
        val gestures = load(context)
        return gestures.maxOfOrNull { gesture ->
            val gestureDuration = (gesture.points.lastOrNull()?.t ?: 0L).coerceAtMost(60000L)
            gesture.delayFromStart + gestureDuration
        } ?: 0L
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_GESTURES)
            .putString(KEY_LOOP_MODE, "ONCE")
            .putInt(KEY_LOOP_VALUE, 1)
            .apply()
    }

    fun saveLoopSettings(context: Context, mode: String, value: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOOP_MODE, mode)
            .putInt(KEY_LOOP_VALUE, value)
            .apply()
    }

    fun getLoopMode(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOOP_MODE, "ONCE") ?: "ONCE"
    }

    fun getLoopValue(context: Context): Int {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LOOP_VALUE, 1)
    }
}
'''

with open(GS, "w", encoding="utf-8") as f:
    f.write(gesture_store_code)

print("✅ GestureStore.kt upgraded")


# ==========================================================
# 2) AutoActionService.kt — Smart match + shifted target click
# ==========================================================
auto_action_code = r'''package com.aarishkhan.aarishai

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

        val startX = match?.centerX ?: fallbackX
        val startY = match?.centerY ?: fallbackY

        val movement = hasRealMovement(points)
        val duration = max(50L, points.last().t).coerceAtMost(60000L)

        // Normal tap ho aur strong node mil gaya ho, toh direct ACTION_CLICK try karo.
        if (!movement && duration < 650L && match != null && match.score >= 80) {
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
        val root = rootInActiveWindow ?: return null

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

        if (!gesture.targetText.isNullOrBlank() &&
            nodeText?.equals(gesture.targetText, ignoreCase = true) == true
        ) {
            score += 90
        }

        // Medium identity
        if (!gesture.targetText.isNullOrBlank() &&
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

            if (dx < 0.12f && dy < 0.12f) score += 20
            else if (dx < 0.22f && dy < 0.22f) score += 10
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

    // ==========================================================
    // 🔥 RECORDING TIME SNAPSHOT
    // ==========================================================
    private fun captureTargetSnapshotInternal(
        x: Int,
        y: Int,
        screenW: Float,
        screenH: Float
    ): TargetSnapshot? {
        val root = rootInActiveWindow ?: return null
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
            targetHPercent = bounds.height() / safeH
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
'''

with open(AAS, "w", encoding="utf-8") as f:
    f.write(auto_action_code)

print("✅ AutoActionService.kt upgraded")


# ==========================================================
# 3) FloatingControlService.kt — TouchCaptureView ko smart save
# ==========================================================
with open(FCS, "r", encoding="utf-8") as f:
    fcs = f.read()

old_save = r'''    private fun saveCurrentGesture() {
        if (currentPoints.isEmpty()) return
        val delayFromStart = currentGestureDownTime - recordingStartTime
        recordedGestures.add(RecordedGesture(delayFromStart = delayFromStart, points = currentPoints.toList()))
        currentPoints.clear()
    }'''

new_save = r'''    private fun saveCurrentGesture() {
        if (currentPoints.isEmpty()) return

        val delayFromStart = currentGestureDownTime - recordingStartTime
        val firstP = currentPoints.first()

        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = metrics.heightPixels.toFloat().coerceAtLeast(1f)

        val snapshot = AutoActionService.captureTargetSnapshot(
            firstP.x.toInt(),
            firstP.y.toInt(),
            screenW,
            screenH
        )

        recordedGestures.add(
            RecordedGesture(
                delayFromStart = delayFromStart,
                points = currentPoints.toList(),

                targetText = snapshot?.targetText,
                targetDesc = snapshot?.targetDesc,
                targetId = snapshot?.targetId,
                targetClass = snapshot?.targetClass,

                targetLeft = snapshot?.targetLeft ?: -1,
                targetTop = snapshot?.targetTop ?: -1,
                targetRight = snapshot?.targetRight ?: -1,
                targetBottom = snapshot?.targetBottom ?: -1,

                xPercent = snapshot?.xPercent ?: (firstP.x / screenW),
                yPercent = snapshot?.yPercent ?: (firstP.y / screenH),

                targetWPercent = snapshot?.targetWPercent ?: 0f,
                targetHPercent = snapshot?.targetHPercent ?: 0f
            )
        )

        currentPoints.clear()
    }'''

if old_save not in fcs:
    raise SystemExit("❌ TouchCaptureView saveCurrentGesture block nahi mila.")
fcs = fcs.replace(old_save, new_save)

# Multi-page + ADD ke time fingerprint lost na ho
fcs = fcs.replace(
    "mutable.add(RecordedGesture(delayFromStart = g.delayFromStart + timeOffset, points = g.points))",
    "mutable.add(g.copy(delayFromStart = g.delayFromStart + timeOffset))"
)

with open(FCS, "w", encoding="utf-8") as f:
    f.write(fcs)

print("✅ FloatingControlService.kt patched")


# ==========================================================
# 4) Accessibility config — screen content read ON
# ==========================================================
with open(ACC, "r", encoding="utf-8") as f:
    acc = f.read()

acc = acc.replace(
    'android:canRetrieveWindowContent="false"',
    'android:canRetrieveWindowContent="true"'
)

acc = re.sub(
    r'android:accessibilityFlags="[^"]*"',
    'android:accessibilityFlags="flagRequestFilterKeyEvents|flagReportViewIds|flagRetrieveInteractiveWindows"',
    acc
)

with open(ACC, "w", encoding="utf-8") as f:
    f.write(acc)

print("✅ accessibility_service_config.xml fixed")

print("")
print("🎯 DONE: Level 1 Smart Target Fingerprint Engine applied.")
print("⚠️ Important: Purani recording clear karke fresh recording banao, tabhi button fingerprint save hoga.")
