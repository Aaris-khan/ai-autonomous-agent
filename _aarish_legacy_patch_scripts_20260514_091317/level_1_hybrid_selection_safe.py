import re

BASE = "app/src/main/java/com/aarishkhan/aarishai"
AAS = f"{BASE}/AutoActionService.kt"

print("🚀 Level 1 Hybrid Selection Safe apply ho raha hai...")

with open(AAS, "r", encoding="utf-8") as f:
    aas = f.read()

# ==========================================================
# 1) Hook at end of dispatchOneGesture
# ==========================================================
old_end = '''        // Tap/long press/swipe sab ke liye shifted path
        performGestureAt(startX, startY, points)
    }'''

new_end = '''        // 🔥 LEVEL 1 HYBRID SELECTION:
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
    }'''

if old_end not in aas:
    if "performHybridSelectionRetry(" in aas:
        print("ℹ️ Hybrid hook already present.")
    else:
        raise SystemExit("❌ dispatchOneGesture end block nahi mila. File version check karo.")
else:
    aas = aas.replace(old_end, new_end)

# ==========================================================
# 2) Inject unique helper functions
# ==========================================================
helpers = r'''
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

'''

if "private fun performHybridSelectionRetry(" not in aas:
    aas = aas.replace(
        "    private fun findClickableParent(",
        helpers + "\n    private fun findClickableParent("
    )
else:
    print("ℹ️ Hybrid helper already present.")

with open(AAS, "w", encoding="utf-8") as f:
    f.write(aas)

print("✅ DONE: Level 1 Hybrid Selection Safe applied.")
