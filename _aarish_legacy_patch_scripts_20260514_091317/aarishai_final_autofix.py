from pathlib import Path
import re, time, sys

ROOT = Path("app/src/main")
MAIN = ROOT / "java/com/aarishkhan/aarishai/MainActivity.kt"
FLOAT = ROOT / "java/com/aarishkhan/aarishai/FloatingControlService.kt"
AUTO = ROOT / "java/com/aarishkhan/aarishai/AutoActionService.kt"
STORE = ROOT / "java/com/aarishkhan/aarishai/GestureStore.kt"

def backup(p: Path):
    if p.exists():
        p.with_suffix(p.suffix + f".bak_{int(time.time())}").write_text(p.read_text(), encoding="utf-8")

def read(p): return p.read_text(encoding="utf-8")
def write(p, s): p.write_text(s, encoding="utf-8")

def must_replace(s, old, new, name):
    if old not in s:
        print(f"⚠️ SKIP exact block not found: {name}")
        return s
    return s.replace(old, new, 1)

def regex_replace(s, pattern, repl, name, flags=re.S):
    ns, n = re.subn(pattern, repl, s, count=1, flags=flags)
    if n == 0:
        print(f"⚠️ SKIP regex block not found: {name}")
    return ns

for p in [MAIN, FLOAT, AUTO, STORE]:
    if not p.exists():
        print(f"❌ Missing: {p}")
        sys.exit(1)
    backup(p)

# ==========================================================
# 1) MainActivity: save/restore permission flow state + prevent resume loop
# ==========================================================
s = read(MAIN)

s = must_replace(
    s,
    "    private var lastPermissionScreen: String? = null\n",
    "    private var lastPermissionScreen: String? = null\n    private var autoPermissionPromptDone = false\n",
    "MainActivity add autoPermissionPromptDone"
)

s = must_replace(
    s,
    """        setContentView(R.layout.activity_main)

        btnScreenCommand = findViewById(R.id.btnScreenCommand)
""",
    """        setContentView(R.layout.activity_main)

        isWaitingForPermission = savedInstanceState?.getBoolean(KEY_WAITING_PERMISSION, false) ?: false
        lastPermissionLaunchAt = savedInstanceState?.getLong(KEY_LAST_PERMISSION_LAUNCH_AT, 0L) ?: 0L
        notificationPermissionAskedThisSession = savedInstanceState?.getBoolean(KEY_NOTIFICATION_ASKED, false) ?: false
        lastPermissionScreen = savedInstanceState?.getString(KEY_LAST_PERMISSION_SCREEN)
        autoPermissionPromptDone = savedInstanceState?.getBoolean(KEY_AUTO_PERMISSION_PROMPT_DONE, false) ?: false

        btnScreenCommand = findViewById(R.id.btnScreenCommand)
""",
    "MainActivity restore state"
)

s = must_replace(
    s,
    """        btnScreenCommand.setOnClickListener {
            lastPermissionScreen = null
            startScreenCommandSystem(forceOpenSettings = true)
        }
""",
    """        btnScreenCommand.setOnClickListener {
            lastPermissionScreen = null
            autoPermissionPromptDone = false
            startScreenCommandSystem(forceOpenSettings = true)
        }
""",
    "MainActivity reset prompt on button"
)

s = must_replace(
    s,
    """        if (!hasOverlayPermission() || !isAccessibilityServiceEnabled()) {
            startScreenCommandSystem(forceOpenSettings = true)
        }
""",
    """        if ((!hasOverlayPermission() || !isAccessibilityServiceEnabled()) && !autoPermissionPromptDone) {
            autoPermissionPromptDone = true
            startScreenCommandSystem(forceOpenSettings = true)
        }
""",
    "MainActivity prevent permission resume loop"
)

if "override fun onSaveInstanceState" not in s:
    s = must_replace(
        s,
        """    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 5001
    }
""",
        """    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_WAITING_PERMISSION, isWaitingForPermission)
        outState.putLong(KEY_LAST_PERMISSION_LAUNCH_AT, lastPermissionLaunchAt)
        outState.putBoolean(KEY_NOTIFICATION_ASKED, notificationPermissionAskedThisSession)
        outState.putString(KEY_LAST_PERMISSION_SCREEN, lastPermissionScreen)
        outState.putBoolean(KEY_AUTO_PERMISSION_PROMPT_DONE, autoPermissionPromptDone)
    }

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 5001
        private const val KEY_WAITING_PERMISSION = "waiting_permission"
        private const val KEY_LAST_PERMISSION_LAUNCH_AT = "last_permission_launch_at"
        private const val KEY_NOTIFICATION_ASKED = "notification_asked"
        private const val KEY_LAST_PERMISSION_SCREEN = "last_permission_screen"
        private const val KEY_AUTO_PERMISSION_PROMPT_DONE = "auto_permission_prompt_done"
    }
""",
        "MainActivity onSaveInstanceState"
    )

write(MAIN, s)

# ==========================================================
# 2) FloatingControlService: saveRecording data-loss guard + restore CLR visibility
# ==========================================================
s = read(FLOAT)

s = must_replace(
    s,
    """        btnLoop.visibility = if (GestureStore.hasRecording(this)) View.VISIBLE else View.GONE

        val params = panelParams
""",
    """        btnLoop.visibility = if (GestureStore.hasRecording(this)) View.VISIBLE else View.GONE
        btnClear?.visibility = if (GestureStore.hasRecording(this) && unsavedGestures.isEmpty()) View.VISIBLE else View.GONE

        val params = panelParams
""",
    "Floating restorePanelUI btnClear visibility"
)

s = must_replace(
    s,
    """        if (isRecording) {
            extractAndAppendGestures()
            isRecording = false
            safeRemoveView(captureView)
            captureView = null
        }
""",
    """        if (isRecording) {
            val liveView = captureView
            if (liveView != null) {
                extractAndAppendGestures()
                safeRemoveView(liveView)
            } else {
                Toast.makeText(this, "⚠️ Recording layer missing thi. Jo saved buffer hai wahi save hoga.", Toast.LENGTH_LONG).show()
            }
            isRecording = false
            captureView = null
        }
""",
    "Floating saveRecording null captureView guard"
)

s = must_replace(
    s,
    """        btnSettings?.visibility = if (showOthers) View.VISIBLE else View.GONE
        btnSpeed?.visibility = if (showOthers) View.VISIBLE else View.GONE
""",
    """        // btnSettings/btnSpeed removed from UI for now.
""",
    "Floating remove dead settings/speed visibility"
)

write(FLOAT, s)

# ==========================================================
# 3) GestureStore: ensure no global loop fallback remains
# ==========================================================
s = read(STORE)

s = must_replace(
    s,
    """        val mode = p.getString(
            loopModeKey(context),
            p.getString(KEY_LOOP_MODE, "ONCE")
        ) ?: "ONCE"
""",
    """        val mode = p.getString(
            loopModeKey(context),
            "ONCE"
        ) ?: "ONCE"
""",
    "GestureStore loop mode fallback"
)

s = must_replace(
    s,
    """"COUNT" -> p.getInt(loopValueKey(context), p.getInt(KEY_LOOP_VALUE, 10)).coerceIn(1, 999)
            "TIME" -> p.getInt(loopValueKey(context), p.getInt(KEY_LOOP_VALUE, 5)).coerceIn(1, 240)
""",
    """"COUNT" -> p.getInt(loopValueKey(context), 10).coerceIn(1, 999)
            "TIME" -> p.getInt(loopValueKey(context), 5).coerceIn(1, 240)
""",
    "GestureStore loop value fallback"
)

write(STORE, s)

# ==========================================================
# 4) AutoActionService: stale node crash guards + double-tap real sequential dispatch + remove screenW shadow
# ==========================================================
s = read(AUTO)

# Add safe node helpers
if "private inline fun <T> safeNodeRead" not in s:
    s = must_replace(
        s,
        """    private data class SmartMatch(
        val node: AccessibilityNodeInfo,
        val bounds: Rect,
        val score: Int
    ) {
        val centerX: Float get() = bounds.exactCenterX()
        val centerY: Float get() = bounds.exactCenterY()
    }
""",
        """    private data class SmartMatch(
        val node: AccessibilityNodeInfo,
        val bounds: Rect,
        val score: Int
    ) {
        val centerX: Float get() = bounds.exactCenterX()
        val centerY: Float get() = bounds.exactCenterY()
    }

    private inline fun <T> safeNodeRead(defaultValue: T, block: () -> T): T {
        return try {
            block()
        } catch (_: IllegalStateException) {
            defaultValue
        } catch (_: Exception) {
            defaultValue
        }
    }

    private fun safeNodeVisible(node: AccessibilityNodeInfo?): Boolean =
        safeNodeRead(false) { node?.isVisibleToUser == true }

    private fun safeNodeEnabled(node: AccessibilityNodeInfo?): Boolean =
        safeNodeRead(false) { node?.isEnabled == true }

    private fun safeNodeClickable(node: AccessibilityNodeInfo?): Boolean =
        safeNodeRead(false) { node?.isClickable == true }

    private fun safeNodeChildCount(node: AccessibilityNodeInfo?): Int =
        safeNodeRead(0) { node?.childCount ?: 0 }

    private fun safeNodeChild(node: AccessibilityNodeInfo?, index: Int): AccessibilityNodeInfo? =
        safeNodeRead(null) { node?.getChild(index) }

    private fun safeNodeParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
        safeNodeRead(null) { node?.parent }

    private fun safeNodeBounds(node: AccessibilityNodeInfo?, out: Rect): Boolean =
        safeNodeRead(false) {
            node?.getBoundsInScreen(out)
            node != null
        }

    private fun safeNodeText(node: AccessibilityNodeInfo?): String? =
        safeNodeRead(null) { node?.text?.toString() }

    private fun safeNodeDesc(node: AccessibilityNodeInfo?): String? =
        safeNodeRead(null) { node?.contentDescription?.toString() }

    private fun safeNodeId(node: AccessibilityNodeInfo?): String? =
        safeNodeRead(null) { node?.viewIdResourceName }

    private fun safeNodeClass(node: AccessibilityNodeInfo?): String? =
        safeNodeRead(null) { node?.className?.toString() }
""",
        "Auto add safe node helpers"
    )

# Replace ownLabelOf
s = regex_replace(
    s,
    r"    private fun ownLabelOf\(node: AccessibilityNodeInfo\?\): String \{.*?\n    \}\n\n    private fun collectNodeTextLimited",
    """    private fun ownLabelOf(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        val text = safeNodeText(node).orEmpty()
        val desc = safeNodeDesc(node).orEmpty()
        val id = safeNodeId(node)?.substringAfterLast("/").orEmpty()

        return listOf(text, desc, id)
            .filter { it.isNotBlank() }
            .joinToString(" | ")
    }

    private fun collectNodeTextLimited""",
    "Auto ownLabelOf safe"
)

# Replace collectNodeTextLimited
s = regex_replace(
    s,
    r"    private fun collectNodeTextLimited\(\n        node: AccessibilityNodeInfo\?,\n        maxNodes: Int = 45,\n        maxChars: Int = 420\n    \): String \{.*?\n    \}\n\n        private fun collectSiblingText",
    """    private fun collectNodeTextLimited(
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

            val count = safeNodeChildCount(n)
            for (i in 0 until count) {
                val child = safeNodeChild(n, i)
                if (child != null) stack.add(child)
            }
        }

        return out.toString().take(maxChars)
    }

        private fun collectSiblingText""",
    "Auto collectNodeTextLimited safe"
)

# Replace collectSiblingText
s = regex_replace(
    s,
    r"        private fun collectSiblingText\(\n        node: AccessibilityNodeInfo\?,\n        maxChars: Int = 300\n    \): String \{.*?\n    \}\n\n    private fun roleFlagsOf",
    """        private fun collectSiblingText(
        node: AccessibilityNodeInfo?,
        maxChars: Int = 300
    ): String {
        val parent = safeNodeParent(node) ?: return ""
        val nodeBounds = Rect()
        val nodeClass = safeNodeClass(node).orEmpty()
        if (!safeNodeBounds(node, nodeBounds)) return ""

        val out = StringBuilder()

        val count = safeNodeChildCount(parent)
        for (i in 0 until count) {
            val child = safeNodeChild(parent, i) ?: continue
            val childBounds = Rect()
            if (!safeNodeBounds(child, childBounds)) continue

            val sameNode = childBounds == nodeBounds && safeNodeClass(child).orEmpty() == nodeClass
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

    private fun roleFlagsOf""",
    "Auto collectSiblingText safe"
)

# Replace roleFlagsOf
s = regex_replace(
    s,
    r"    private fun roleFlagsOf\(node: AccessibilityNodeInfo\?\): String \{.*?\n    \}\n\n    private fun roleSimilarity",
    """    private fun roleFlagsOf(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        val flags = mutableListOf<String>()

        if (safeNodeRead(false) { node.isClickable }) flags.add("click")
        if (safeNodeRead(false) { node.isLongClickable }) flags.add("long")
        if (safeNodeRead(false) { node.isEditable }) flags.add("edit")
        if (safeNodeRead(false) { node.isScrollable }) flags.add("scroll")
        if (safeNodeRead(false) { node.isCheckable }) flags.add("check")
        if (safeNodeRead(false) { node.isChecked }) flags.add("checked")
        if (safeNodeRead(false) { node.isEnabled }) flags.add("enabled")
        if (safeNodeRead(false) { node.isVisibleToUser }) flags.add("visible")
        if (safeNodeRead(false) { node.isFocusable }) flags.add("focus")

        return flags.joinToString("|")
    }

    private fun roleSimilarity""",
    "Auto roleFlagsOf safe"
)

# Replace findClickableParent
s = regex_replace(
    s,
    r"    private fun findClickableParent\(\n        node: AccessibilityNodeInfo\?\n    \): AccessibilityNodeInfo\? \{.*?\n    \}\n\n    private fun showTinyToast",
    """    private fun findClickableParent(
        node: AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        var current = node
        var depth = 0

        while (current != null && depth < 30) {
            if (safeNodeClickable(current)) return current
            val next = safeNodeParent(current)
            if (next == current) return null
            current = next
            depth++
        }

        return null
    }

    private fun showTinyToast""",
    "Auto findClickableParent safe"
)

# Replace findDeepestNodeAtCoordinate
s = regex_replace(
    s,
    r"    private fun findDeepestNodeAtCoordinate\(\n        root: AccessibilityNodeInfo\?,\n        x: Int,\n        y: Int\n    \): AccessibilityNodeInfo\? \{.*?\n    \}\n\n\n    private fun performHybridSelectionRetry",
    """    private fun findDeepestNodeAtCoordinate(
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
            if (!safeNodeBounds(node, bounds)) continue

            if (bounds.contains(x, y)) {
                val area = (bounds.width() * bounds.height()).coerceAtLeast(1)
                val clickableBonus = if (safeNodeClickable(node)) 800 else 0
                val leafBonus = if (safeNodeChildCount(node) == 0) 300 else 0
                val smallAreaBonus = 500000 / area.coerceAtLeast(1)

                val candidateScore =
                    (depth * 1000) +
                    clickableBonus +
                    leafBonus +
                    smallAreaBonus

                if (candidateScore > bestScore) {
                    bestScore = candidateScore
                    bestNode = node
                }

                val count = safeNodeChildCount(node)
                for (i in 0 until count) {
                    val child = safeNodeChild(node, i)
                    if (child != null) {
                        stack.add(Pair(child, depth + 1))
                    }
                }
            }
        }

        return bestNode
    }


    private fun performHybridSelectionRetry""",
    "Auto findDeepestNodeAtCoordinate safe"
)

# Replace dispatchHybridGesturePath with sequential double-tap
s = regex_replace(
    s,
    r"        private fun dispatchHybridGesturePath\(\n        startX: Float,\n        startY: Float,\n        points: List<GesturePoint>,\n        duration: Long,\n        isDoubleTap: Boolean,\n        runId: Int,\n        onDone: \(Boolean\) -> Unit\n    \) \{.*?\n    \}\n\n        private fun isSelectionUiVisibleHybrid",
    """        private fun dispatchHybridGesturePath(
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
        val holdDuration = duration.coerceAtLeast(90L).coerceAtMost(3500L)
        val tapDuration = 90L
        val callbackDelivered = java.util.concurrent.atomic.AtomicBoolean(false)

        fun finishOnce(value: Boolean) {
            if (callbackDelivered.compareAndSet(false, true)) {
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
                                addStroke(GestureDescription.StrokeDescription(tinyPath(), 0L, tapDuration))
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
                val shiftedX = safeX + (p.x - first.x)
                val shiftedY = safeY + (p.y - first.y)
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

        private fun isSelectionUiVisibleHybrid""",
    "Auto sequential double tap"
)

# Remove inner screenW/screenH shadow block inside findBestSmartTarget
s = s.replace(
"""                        val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
                        val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

                        // Recording mein button/clickable-parent ka size save hota hai.
""",
"""                        // Recording mein button/clickable-parent ka size save hota hai.
""",
1)

# Basic safe replacements in common traversal loops
s = s.replace("node.childCount", "safeNodeChildCount(node)")
s = s.replace("n.childCount", "safeNodeChildCount(n)")
s = s.replace("parent.childCount", "safeNodeChildCount(parent)")
s = s.replace("node.getChild(i)", "safeNodeChild(node, i)")
s = s.replace("n.getChild(i)", "safeNodeChild(n, i)")
s = s.replace("parent.getChild(i)", "safeNodeChild(parent, i)")
s = s.replace("current.parent", "safeNodeParent(current)")
s = s.replace("node.parent", "safeNodeParent(node)")

# Fix accidental double replacement if any
s = s.replace("safeNodeChildCount(safeNodeChildCount(node))", "safeNodeChildCount(node)")
s = s.replace("safeNodeChildCount(safeNodeChildCount(n))", "safeNodeChildCount(n)")
s = s.replace("safeNodeChildCount(safeNodeChildCount(parent))", "safeNodeChildCount(parent)")
s = s.replace("safeNodeChild(safeNodeChild(node, i), i)", "safeNodeChild(node, i)")
s = s.replace("safeNodeChild(safeNodeChild(n, i), i)", "safeNodeChild(n, i)")
s = s.replace("safeNodeChild(safeNodeChild(parent, i), i)", "safeNodeChild(parent, i)")
s = s.replace("safeNodeParent(safeNodeParent(current))", "safeNodeParent(current)")
s = s.replace("safeNodeParent(safeNodeParent(node))", "safeNodeParent(node)")

write(AUTO, s)

print("✅ AarishAI final autofix applied.")
print("➡️ Ab run karo: ./gradlew assembleDebug")
