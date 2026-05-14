import re

BASE = "app/src/main/java/com/aarishkhan/aarishai"
AAS = f"{BASE}/AutoActionService.kt"
FCS = f"{BASE}/FloatingControlService.kt"
GS = f"{BASE}/GestureStore.kt"
MAIN = f"{BASE}/MainActivity.kt"
ACC = "app/src/main/res/xml/accessibility_service_config.xml"

print("🚀 FINAL Level 2 Workflow + Smart Shift Fix apply ho raha hai...")

# ==========================================================
# 1) AutoActionService.kt fixes
# ==========================================================
with open(AAS, "r", encoding="utf-8") as f:
    aas = f.read()

# @Volatile instance
aas = aas.replace(
    "private var instance: AutoActionService? = null",
    "@Volatile private var instance: AutoActionService? = null"
)

# Full playSequence replace: all-at-once scheduling -> safer chained playback
play_pattern = r"    private fun playSequence\(gestures: List<RecordedGesture>\) \{.*?\n    private fun cancelCurrentRunningGesture"
new_play = r'''    private fun playSequence(gestures: List<RecordedGesture>) {
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

    private fun cancelCurrentRunningGesture'''
aas = re.sub(play_pattern, new_play, aas, flags=re.DOTALL)

# findBestSmartTarget iterative BFS/stack scan
best_pattern = r"    private fun findBestSmartTarget\(gesture: RecordedGesture\): SmartMatch\? \{.*?\n    private fun scoreNode"
new_best = r'''    private fun findBestSmartTarget(gesture: RecordedGesture): SmartMatch? {
        val root = getRealAppRoot() ?: return null

        var best: SmartMatch? = null
        val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)

        while (!stack.isEmpty()) {
            val node = stack.removeLast() ?: continue

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            if (bounds.width() > 0 && bounds.height() > 0) {
                val score = scoreNode(node, bounds, gesture)
                if (score > 0 && (best == null || score > best!!.score)) {
                    best = SmartMatch(node, Rect(bounds), score)
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

    private fun scoreNode'''
aas = re.sub(best_pattern, new_best, aas, flags=re.DOTALL)

# getRealAppRoot replace: skip keyboard/system, choose largest/focused app window
root_pattern = r"    private fun getRealAppRoot\(\): AccessibilityNodeInfo\? \{.*?\n    // =========================================================="
new_root = r'''    private fun getRealAppRoot(): AccessibilityNodeInfo? {
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

    // =========================================================='''
aas = re.sub(root_pattern, new_root, aas, flags=re.DOTALL)

# capture snapshot should prefer root containing tap coordinate
aas = aas.replace(
    "val root = getRealAppRoot() ?: return null\n        val touchedNode = findDeepestNodeAtCoordinate(root, x, y) ?: return null",
    "val root = getRealAppRootForPoint(x, y) ?: return null\n        val touchedNode = findDeepestNodeAtCoordinate(root, x, y) ?: return null"
)

# findDeepestNodeAtCoordinate iterative
deep_pattern = r"    private fun findDeepestNodeAtCoordinate\(\s*root: AccessibilityNodeInfo\?,\s*x: Int,\s*y: Int\s*\): AccessibilityNodeInfo\? \{.*?\n    private fun findClickableParent"
new_deep = r'''    private fun findDeepestNodeAtCoordinate(
        root: AccessibilityNodeInfo?,
        x: Int,
        y: Int
    ): AccessibilityNodeInfo? {
        if (root == null) return null

        val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)

        var deepest: AccessibilityNodeInfo? = null

        while (!stack.isEmpty()) {
            val node = stack.removeLast() ?: continue

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            if (bounds.contains(x, y)) {
                deepest = node

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) stack.add(child)
                }
            }
        }

        return deepest
    }

    private fun findClickableParent'''
aas = re.sub(deep_pattern, new_deep, aas, flags=re.DOTALL)

# long press drift fix: hasRealMovement threshold bigger
move_pattern = r"    private fun hasRealMovement\(points: List<GesturePoint>\): Boolean \{.*?\n    private fun performGestureAt"
new_move = r'''    private fun hasRealMovement(points: List<GesturePoint>): Boolean {
        if (points.size <= 1) return false

        val first = points.first()
        var maxDx = 0f
        var maxDy = 0f

        for (i in 1 until points.size) {
            val p = points[i]
            maxDx = kotlin.math.max(maxDx, abs(p.x - first.x))
            maxDy = kotlin.math.max(maxDy, abs(p.y - first.y))
        }

        // 8px tak natural finger drift maan lo, swipe nahi
        return maxDx > 8f || maxDy > 8f
    }

    private fun performGestureAt'''
aas = re.sub(move_pattern, new_move, aas, flags=re.DOTALL)

# performGestureAt movement threshold also 8px
aas = aas.replace(
    "if (abs(p.x - firstPoint.x) > 2f || abs(p.y - firstPoint.y) > 2f) {",
    "if (abs(p.x - firstPoint.x) > 8f || abs(p.y - firstPoint.y) > 8f) {"
)

# Direct ACTION_CLICK stricter: only simple button-ish nodes
aas = aas.replace(
    "if (!movement && duration < 650L && match != null && match.score >= 90 && match.bounds.width() < (resources.displayMetrics.widthPixels * 0.55f)) {",
    "if (!movement && duration < 450L && match != null && match.score >= 100 && match.bounds.width() < (resources.displayMetrics.widthPixels * 0.35f) && match.bounds.height() < (resources.displayMetrics.heightPixels * 0.15f)) {"
)

with open(AAS, "w", encoding="utf-8") as f:
    f.write(aas)

print("✅ AutoActionService.kt fixed")


# ==========================================================
# 2) GestureStore.kt fixes
# ==========================================================
with open(GS, "r", encoding="utf-8") as f:
    gs = f.read()

# save returns Boolean
gs = gs.replace(
    "fun save(context: Context, gestures: List<RecordedGesture>) {",
    "fun save(context: Context, gestures: List<RecordedGesture>): Boolean {"
)

gs = gs.replace(
    "        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)\n            .edit()\n            .putString(KEY_GESTURES, mainArray.toString())\n            .commit()",
    "        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)\n            .edit()\n            .putString(KEY_GESTURES, mainArray.toString())\n            .commit()"
)

# hasRecording lightweight parse
has_pattern = r"    fun hasRecording\(context: Context\): Boolean \{.*?\n    fun totalDuration"
new_has = r'''    fun hasRecording(context: Context): Boolean {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GESTURES, null) ?: return false

        return try {
            JSONArray(raw).length() > 0
        } catch (_: Exception) {
            false
        }
    }

    fun totalDuration'''
gs = re.sub(has_pattern, new_has, gs, flags=re.DOTALL)

# load failure at least log
gs = gs.replace(
    "        } catch (e: Exception) {\n            emptyList()\n        }",
    "        } catch (e: Exception) {\n            android.util.Log.e(\"GestureStore\", \"Recording load failed\", e)\n            emptyList()\n        }"
)

with open(GS, "w", encoding="utf-8") as f:
    f.write(gs)

print("✅ GestureStore.kt fixed")


# ==========================================================
# 3) FloatingControlService.kt fixes
# ==========================================================
with open(FCS, "r", encoding="utf-8") as f:
    fcs = f.read()

# saveRecording should check save result
old_save_call = '''        GestureStore.save(this, unsavedGestures)
        unsavedGestures = emptyList()
        updateUIState("PLAY", false, true, true)
        android.widget.Toast.makeText(this, "✅ Poori Memory Save ho gayi!", android.widget.Toast.LENGTH_SHORT).show()'''

new_save_call = '''        val saved = GestureStore.save(this, unsavedGestures)
        if (!saved) {
            android.widget.Toast.makeText(this, "❌ Memory save fail ho gayi, dobara SAVE dabao", android.widget.Toast.LENGTH_LONG).show()
            updateUIState("+ ADD", true, false, true)
            return
        }

        unsavedGestures = emptyList()
        updateUIState("PLAY", false, true, true)
        android.widget.Toast.makeText(this, "✅ Poori Memory Save ho gayi!", android.widget.Toast_SHORT).show()'''

# typo-safe: use Toast.LENGTH_SHORT constant correctly
new_save_call = new_save_call.replace("android.widget.Toast_SHORT", "android.widget.Toast.LENGTH_SHORT")

fcs = fcs.replace(old_save_call, new_save_call)

# panel drag off-screen fix
fcs = fcs.replace(
    "val maxX = metrics.widthPixels - root.width\n                    val maxY = metrics.heightPixels - root.height",
    "val maxX = if (root.width > 0) metrics.widthPixels - root.width else metrics.widthPixels / 2\n                    val maxY = if (root.height > 0) metrics.heightPixels - root.height else metrics.heightPixels / 2"
)

# bringPanelToFront retry once before stopping service
old_bring = '''    private fun bringPanelToFront() {
        val panel = panelView ?: return
        val params = panelParams ?: return

        try {
            windowManager.removeView(panel)
        } catch (_: Exception) {
        }

        val added = safeAddView(panel, params, "Panel restore error")
        if (!added) {
            panelView = null
            Toast.makeText(this, "Panel wapas nahi aa paya, service band kar rahe hain", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }'''

new_bring = '''    private fun bringPanelToFront() {
        val panel = panelView ?: return
        val params = panelParams ?: return

        try {
            windowManager.removeView(panel)
        } catch (_: Exception) {
        }

        var added = safeAddView(panel, params, "Panel restore error")
        if (!added) {
            handler.postDelayed({
                added = safeAddView(panel, params, "Panel retry error")
                if (!added) {
                    panelView = null
                    Toast.makeText(this, "Panel wapas nahi aa paya, service band kar rahe hain", Toast.LENGTH_LONG).show()
                    stopSelf()
                }
            }, 250L)
        }
    }'''

fcs = fcs.replace(old_bring, new_bring)

with open(FCS, "w", encoding="utf-8") as f:
    f.write(fcs)

print("✅ FloatingControlService.kt fixed")


# ==========================================================
# 4) MainActivity.kt: avoid duplicate service start
# ==========================================================
with open(MAIN, "r", encoding="utf-8") as f:
    main = f.read()

if "FloatingControlService.instance != null" not in main:
    main = main.replace(
        '''        try {
            val serviceIntent = Intent(this, FloatingControlService::class.java)''',
        '''        if (FloatingControlService.instance != null) {
            moveTaskToBack(true)
            return
        }

        try {
            val serviceIntent = Intent(this, FloatingControlService::class.java)'''
    )

with open(MAIN, "w", encoding="utf-8") as f:
    f.write(main)

print("✅ MainActivity.kt fixed")


# ==========================================================
# 5) Accessibility config: reduce useless event spam
# ==========================================================
with open(ACC, "r", encoding="utf-8") as f:
    acc = f.read()

acc = acc.replace(
    'android:accessibilityEventTypes="typeAllMask"',
    'android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewClicked|typeViewScrolled"'
)

acc = acc.replace(
    'android:notificationTimeout="100"',
    'android:notificationTimeout="200"'
)

with open(ACC, "w", encoding="utf-8") as f:
    f.write(acc)

print("✅ accessibility_service_config.xml fixed")

print("")
print("🎯 FINAL DONE: Smart shift workflow, root selection, playback chain, movement threshold, and save verification fixed.")
print("⚠️ Build ke baad Accessibility service OFF karke ON karna.")
print("⚠️ Purani recording clear karke fresh recording banao.")
