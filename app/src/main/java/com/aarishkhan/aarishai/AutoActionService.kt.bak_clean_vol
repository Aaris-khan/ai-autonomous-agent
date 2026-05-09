package com.aarishkhan.aarishai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
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
    }

    private val handler = Handler(Looper.getMainLooper())
        private val scheduledTasks = mutableListOf<Runnable>()
    @Volatile private var isPlayingInternal = false
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

        // BUG #3 FIX: Disk read nahi, RAM mein maujood gestures list se calculate karo
        val totalDuration = gestures.maxOfOrNull { gesture ->
            val gestureDuration = (gesture.points.lastOrNull()?.t ?: 0L).coerceAtMost(60000L)
            gesture.delayFromStart + gestureDuration
        } ?: 0L

                scheduledTasks.add(finishTask)
        // BUG FIX: 500ms ka rukawat hata diya gaya lightning fast clicks ke liye
        handler.postDelayed(finishTask, totalDuration)
    }

        private fun cancelCurrentRunningGesture() {
        // Safe Cancel: isPlayingInternal = false earlier in code already stops the queue.
        // Removed fake (1,1) tap to prevent accidental top-left clicks on apps.
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

    private fun dispatchOneGesture(recordedGesture: RecordedGesture) {
        if (!isPlayingInternal) return
        val points = recordedGesture.points
        if (points.isEmpty()) return
                val firstPoint = points.first()
        val path = Path()
        val screenW = resources.displayMetrics.widthPixels.toFloat() - 1f
        val screenH = resources.displayMetrics.heightPixels.toFloat() - 1f
        
        path.moveTo(firstPoint.x.coerceIn(1f, screenW), firstPoint.y.coerceIn(1f, screenH))
        var hasRealMovement = false

        if (points.size > 1) {
            for (i in 1 until points.size) {
                val p = points[i]
                if (kotlin.math.abs(p.x - firstPoint.x) > 2f ||
                    kotlin.math.abs(p.y - firstPoint.y) > 2f) {
                    hasRealMovement = true
                }
                path.lineTo(p.x.coerceIn(1f, screenW), p.y.coerceIn(1f, screenH))
            }
        }

                // BUG #2 FIX: 0.1f microscopic shift safely clamped
        if (!hasRealMovement) {
            val safeX = (firstPoint.x + 0.1f).coerceIn(1f, screenW)
            val safeY = (firstPoint.y + 0.1f).coerceIn(1f, screenH)
            path.lineTo(safeX, safeY)
        }

        val duration = max(50L, points.last().t).coerceAtMost(60000L)
        try {
            val gesture = GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path, 0, duration
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        stopPlaybackInternal(showToast = false)
    }
}
