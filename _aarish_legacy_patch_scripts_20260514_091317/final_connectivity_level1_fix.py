import re

BASE = "app/src/main/java/com/aarishkhan/aarishai"
AAS = f"{BASE}/AutoActionService.kt"
FCS = f"{BASE}/FloatingControlService.kt"
MAIN = f"{BASE}/MainActivity.kt"

print("🚀 Final Connectivity + Level 1 Hybrid Fix apply ho raha hai...")

# ==========================================================
# 1) MainActivity: notification denied par force-start mat karo
# ==========================================================
with open(MAIN, "r", encoding="utf-8") as f:
    main = f.read()

old_denied = '''            } else {
                Toast.makeText(this, "Notification Denied! Phir bhi service start kar rahe hain...", Toast.LENGTH_SHORT).show()
                try {
                    val serviceIntent = Intent(this, FloatingControlService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    moveTaskToBack(true)
                } catch (e: Exception) {
                    Toast.makeText(this, "Service start nahi hua: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }'''

new_denied = '''            } else {
                Toast.makeText(
                    this,
                    "Notification permission deny hai. Service start karne ke liye permission ON karo.",
                    Toast.LENGTH_LONG
                ).show()
            }'''

main = main.replace(old_denied, new_denied)

with open(MAIN, "w", encoding="utf-8") as f:
    f.write(main)

print("✅ MainActivity.kt fixed")


# ==========================================================
# 2) FloatingControlService fixes
# ==========================================================
with open(FCS, "r", encoding="utf-8") as f:
    fcs = f.read()

# event.action → actionMasked
fcs = fcs.replace(
    "            when (event.action) {",
    "            when (event.actionMasked) {"
)

# extractAndAppendGestures: lastOrNull ki jagah max end time
old_last = '''        val mutable = unsavedGestures.toMutableList()
        val lastGesture = unsavedGestures.lastOrNull()
        val lastGestureEnd = (lastGesture?.delayFromStart ?: 0L) + (lastGesture?.points?.lastOrNull()?.t ?: 0L)'''

new_last = '''        val mutable = unsavedGestures.toMutableList()
        val lastGestureEnd = unsavedGestures.maxOfOrNull { g ->
            g.delayFromStart + (g.points.lastOrNull()?.t ?: 0L)
        } ?: 0L'''

fcs = fcs.replace(old_last, new_last)

# hidePanelUIForPlayback: STOP button ko screen bound me rakho
old_hide = '''            val density = resources.displayMetrics.density
            params.x = resources.displayMetrics.widthPixels - (120 * density).toInt()
            params.y = (60 * density).toInt()
            try {
                windowManager.updateViewLayout(panel, params)
            } catch (_: Exception) {
            }'''

new_hide = '''            val density = resources.displayMetrics.density
            val maxX = if (panel.width > 0) resources.displayMetrics.widthPixels - panel.width else resources.displayMetrics.widthPixels
            val maxY = if (panel.height > 0) resources.displayMetrics.heightPixels - panel.height else resources.displayMetrics.heightPixels

            params.x = (resources.displayMetrics.widthPixels - (120 * density).toInt()).coerceIn(0, if (maxX > 0) maxX else resources.displayMetrics.widthPixels)
            params.y = (60 * density).toInt().coerceIn(0, if (maxY > 0) maxY else resources.displayMetrics.heightPixels)

            try {
                windowManager.updateViewLayout(panel, params)
            } catch (_: Exception) {
            }'''

fcs = fcs.replace(old_hide, new_hide)

with open(FCS, "w", encoding="utf-8") as f:
    f.write(fcs)

print("✅ FloatingControlService.kt fixed")


# ==========================================================
# 3) AutoActionService: system action check + Level 1 hybrid long press
# ==========================================================
with open(AAS, "r", encoding="utf-8") as f:
    aas = f.read()

# System BACK/RECENTS playback return check
old_system = '''        if (firstPoint.x <= -50f) {
            activeGestureCount.incrementAndGet()

            when (firstPoint.x.toInt()) {
                -100 -> performGlobalAction(GLOBAL_ACTION_BACK)
                -200 -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            }

            // System action ka callback nahi hota, isliye transition ke liye short guard delay
            handler.postDelayed({
                decrementActiveGestureSafely()
            }, 450L)

            return
        }'''

new_system = '''        if (firstPoint.x <= -50f) {
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
        }'''

aas = aas.replace(old_system, new_system)

# Long press hybrid hook
old_end = '''        // Tap/long press/swipe sab ke liye shifted path
        performGestureAt(startX, startY, points)
    }'''

new_end = '''        // 🔥 LEVEL 1 HYBRID SELECTION:
        // Pehle original recorded long press.
        // Agar blue handles / Copy toolbar nahi aaye, tab double-tap + hold retry.
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
    }'''

if old_end in aas:
    aas = aas.replace(old_end, new_end)
elif "performHybridSelectionRetry(" not in aas:
    raise SystemExit("❌ dispatchOneGesture end block nahi mila. File version alag hai.")

# Limit node scan in findBestSmartTarget
old_stack = '''        var best: SmartMatch? = null
        val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)

        while (!stack.isEmpty()) {
            val node = stack.removeLast() ?: continue'''

new_stack = '''        var best: SmartMatch? = null
        val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)

        var visitedNodes = 0

        while (!stack.isEmpty() && visitedNodes < 900) {
            visitedNodes++
            val node = stack.removeLast() ?: continue'''

aas = aas.replace(old_stack, new_stack)

# Long press hybrid helpers
helpers = r'''
    private fun performHybridSelectionRetry(
        startX: Float,
        startY: Float,
        points: List<GesturePoint>,
        duration: Long
    ) {
        if (!isPlayingInternal) return

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

            // Agar first long-press fail/cancel ho gaya, tab bhi retry karo.
            if (!completed) {
                runDoubleTapRetry()
                return@dispatchHybridGesturePath
            }

            handler.postDelayed({
                if (!isPlayingInternal) {
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
        val holdDuration = duration.coerceAtLeast(90L).coerceAtMost(3500L)
        val tapDuration = 90L

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
            builder.addStroke(GestureDescription.StrokeDescription(path2, 150L, tapDuration))
        } else {
            val path = Path().apply {
                moveTo(safeX, safeY)
            }

            var hasLine = false
            val first = points.firstOrNull()

            if (first != null && points.size > 1) {
                for (i in 1 until points.size) {
                    val p = points[i]
                    val shiftedX = safeX + (p.x - first.x)
                    val shiftedY = safeY + (p.y - first.y)

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

            if (label.contains("floating_toolbar")) return true

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

'''

if "private fun performHybridSelectionRetry(" not in aas:
    aas = aas.replace(
        "    private fun findClickableParent(",
        helpers + "\n    private fun findClickableParent("
    )

with open(AAS, "w", encoding="utf-8") as f:
    f.write(aas)

print("✅ AutoActionService.kt fixed")

print("")
print("🎯 DONE: Connectivity + Level 1 Hybrid fixes applied.")
