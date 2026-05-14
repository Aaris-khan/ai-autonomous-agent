from pathlib import Path
import re, shutil, time, sys

ROOT = Path(".")
FCS = ROOT / "app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt"
AUTO = ROOT / "app/src/main/java/com/aarishkhan/aarishai/AutoActionService.kt"

stamp = time.strftime("%Y%m%d_%H%M%S")

def backup(p: Path):
    if not p.exists():
        raise SystemExit(f"❌ File missing: {p}")
    b = p.with_suffix(p.suffix + f".bak_{stamp}")
    shutil.copy2(p, b)
    print(f"✅ Backup: {b}")

def replace_regex(text, pattern, repl, name):
    new, n = re.subn(pattern, repl, text, count=1, flags=re.S)
    if n == 0:
        raise SystemExit(f"❌ Pattern not found: {name}")
    print(f"✅ Patched: {name}")
    return new

backup(FCS)
backup(AUTO)

# =========================================================
# 1) AutoActionService.kt fixes
# =========================================================
auto = AUTO.read_text()

# Compile killer fix
auto = auto.replace("safeNodeChildCount(", "safeChildCount(")
auto = auto.replace("safeNodeChild(", "safeChild(")

# Add safePackage helper
if "private fun safePackage(node: AccessibilityNodeInfo?)" not in auto:
    auto = auto.replace(
'''    private fun safeClass(node: AccessibilityNodeInfo?): String? =
        try { node?.className?.toString() } catch (_: Exception) { null }''',
'''    private fun safeClass(node: AccessibilityNodeInfo?): String? =
        try { node?.className?.toString() } catch (_: Exception) { null }

    private fun safePackage(node: AccessibilityNodeInfo?): String? =
        try { node?.packageName?.toString() } catch (_: Exception) { null }'''
    )

# getRealAppRootForPoint direct package/bounds safety
auto = auto.replace(
'val pkg = root.packageName?.toString() ?: ""',
'val pkg = safePackage(root).orEmpty()'
)
auto = auto.replace(
'val fallbackPkg = fallbackRoot.packageName?.toString() ?: ""',
'val fallbackPkg = safePackage(fallbackRoot).orEmpty()'
)
auto = auto.replace(
'''                    val bounds = Rect()
                    root.getBoundsInScreen(bounds)

                    if (bounds.width() <= 0 || bounds.height() <= 0) continue''',
'''                    val bounds = Rect()
                    if (!safeBounds(root, bounds)) continue

                    if (bounds.width() <= 0 || bounds.height() <= 0) continue'''
)

new_find_exact = r'''    private fun findExactActionButtonAcrossWindows(
        gesture: RecordedGesture
    ): SmartMatch? {
        return try {
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
                val pkg = safePackage(root).orEmpty()
                if (pkg == packageName) return true
                if (pkg.contains("keyboard", ignoreCase = true)) return true
                if (pkg.contains("inputmethod", ignoreCase = true)) return true
                return false
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                try {
                    for (window in windows) {
                        if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue

                        val root = try { window.root } catch (_: Exception) { null } ?: continue
                        if (skipRoot(root)) continue

                        val b = Rect()
                        if (safeBounds(root, b) && b.width() > 0 && b.height() > 0) {
                            roots.add(root)
                        }
                    }
                } catch (_: Exception) {
                }
            }

            val activeRoot = try { rootInActiveWindow } catch (_: Exception) { null }
            if (activeRoot != null && !skipRoot(activeRoot)) {
                val b = Rect()
                if (safeBounds(activeRoot, b) && b.width() > 0 && b.height() > 0) {
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

                    val node = stack.removeLast()
                    if (!safeVisible(node) || !safeEnabled(node)) continue

                    val text = safeText(node)?.trim()?.lowercase()
                    val desc = safeDesc(node)?.trim()?.lowercase()

                    val exact = text == wantedLower || desc == wantedLower

                    if (exact) {
                        val clickable = findClickableParent(node) ?: node
                        if (safeVisible(clickable) && safeEnabled(clickable)) {
                            val bounds = Rect()
                            if (!safeBounds(clickable, bounds)) continue

                            if (bounds.width() > 0 && bounds.height() > 0) {
                                var score = 500

                                if (safeClickable(clickable)) score += 50

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

                    val count = safeChildCount(node)
                    for (i in 0 until count) {
                        val child = safeChild(node, i)
                        if (child != null) stack.add(child)
                    }
                }
            }

            best
        } catch (_: Exception) {
            null
        }
    }
'''

auto = replace_regex(
    auto,
    r'    private fun findExactActionButtonAcrossWindows\(\s*gesture: RecordedGesture\s*\): SmartMatch\? \{.*?\n    private fun findBestSmartTarget',
    new_find_exact + "\n\n    private fun findBestSmartTarget",
    "safe findExactActionButtonAcrossWindows()"
)

new_capture = r'''    private fun captureTargetSnapshotInternal(
        x: Int,
        y: Int,
        screenW: Float,
        screenH: Float
    ): TargetSnapshot? {
        return try {
            val root = getRealAppRootForPoint(x, y) ?: return null
            val touchedNode = findDeepestNodeAtCoordinate(root, x, y) ?: return null
            val clickNode = findClickableParent(touchedNode) ?: touchedNode

            val bounds = Rect()
            if (!safeBounds(clickNode, bounds)) return null

            val safeW = screenW.coerceAtLeast(1f)
            val safeH = screenH.coerceAtLeast(1f)

            TargetSnapshot(
                targetText = safeText(clickNode) ?: safeText(touchedNode),
                targetDesc = safeDesc(clickNode) ?: safeDesc(touchedNode),
                targetId = safeId(clickNode) ?: safeId(touchedNode),
                targetClass = safeClass(clickNode) ?: safeClass(touchedNode),

                targetContextText = collectNodeTextLimited(clickNode, 45, 420),
                targetChildText = collectNodeTextLimited(touchedNode, 25, 220),
                targetSiblingText = collectSiblingText(clickNode, 300),
                targetRoleFlags = roleFlagsOf(clickNode),
                targetTreePath = extractTreePathDNA(clickNode),

                targetLeft = bounds.left,
                targetTop = bounds.top,
                targetRight = bounds.right,
                targetBottom = bounds.bottom,
                xPercent = (x / safeW).coerceIn(0f, 1f),
                yPercent = (y / safeH).coerceIn(0f, 1f),
                targetWPercent = (bounds.width() / safeW).coerceIn(0f, 1f),
                targetHPercent = (bounds.height() / safeH).coerceIn(0f, 1f),
                insideXPercent = if (bounds.width() > 0) ((x - bounds.left).toFloat() / bounds.width()).coerceIn(0f, 1f) else 0.5f,
                insideYPercent = if (bounds.height() > 0) ((y - bounds.top).toFloat() / bounds.height()).coerceIn(0f, 1f) else 0.5f
            )
        } catch (_: Exception) {
            null
        }
    }
'''

auto = replace_regex(
    auto,
    r'    private fun captureTargetSnapshotInternal\(\s*x: Int,\s*y: Int,\s*screenW: Float,\s*screenH: Float\s*\): TargetSnapshot\? \{.*?\n    private fun findDeepestNodeAtCoordinate',
    new_capture + "\n\n    private fun findDeepestNodeAtCoordinate",
    "safe captureTargetSnapshotInternal()"
)

AUTO.write_text(auto)

# =========================================================
# 2) FloatingControlService.kt fixes
# =========================================================
fcs = FCS.read_text()

# Panel hidden guard
if "private var panelHiddenForPlayback = false" not in fcs:
    fcs = fcs.replace(
        "private var activeConfigDialog: android.app.AlertDialog? = null",
        "private var activeConfigDialog: android.app.AlertDialog? = null\n    private var panelHiddenForPlayback = false"
    )

new_hide = r'''    private fun hidePanelUIForPlayback() {
        if (panelHiddenForPlayback) return
        panelHiddenForPlayback = true

        btnSave.visibility = View.GONE
        btnUndo.visibility = View.GONE
        btnCut.visibility = View.GONE
        label.visibility = View.GONE
        btnLoop.visibility = View.GONE
        btnClear?.visibility = View.GONE

        val params = panelParams
        val panel = panelView
        if (params != null && panel != null) {
            oldPanelX = params.x
            oldPanelY = params.y

            val density = resources.displayMetrics.density
            val maxX = if (panel.width > 0) resources.displayMetrics.widthPixels - panel.width else resources.displayMetrics.widthPixels
            val maxY = if (panel.height > 0) resources.displayMetrics.heightPixels - panel.height else resources.displayMetrics.heightPixels

            params.x = (resources.displayMetrics.widthPixels - (120 * density).toInt())
                .coerceIn(0, if (maxX > 0) maxX else resources.displayMetrics.widthPixels)
            params.y = (60 * density).toInt()
                .coerceIn(0, if (maxY > 0) maxY else resources.displayMetrics.heightPixels)

            try {
                windowManager.updateViewLayout(panel, params)
            } catch (_: Exception) {
            }
        }
    }
'''

fcs = replace_regex(
    fcs,
    r'    private fun hidePanelUIForPlayback\(\) \{.*?\n    // BUG #5 FIX',
    new_hide + "\n\n    // BUG #5 FIX",
    "panel hidden coordinate guard"
)

# restorePanelUI resets hidden guard
fcs = fcs.replace(
'''    private fun restorePanelUI() {
        btnSave.visibility = if (unsavedGestures.isNotEmpty()) View.VISIBLE else View.GONE''',
'''    private fun restorePanelUI() {
        panelHiddenForPlayback = false
        btnSave.visibility = if (unsavedGestures.isNotEmpty()) View.VISIBLE else View.GONE'''
)

# updateUIState clear button only when saved recording exists
fcs = fcs.replace(
'btnClear?.visibility = if (showOthers) View.VISIBLE else View.GONE',
'btnClear?.visibility = if (showOthers && GestureStore.hasRecording(this) && unsavedGestures.isEmpty()) View.VISIBLE else View.GONE'
)

# Undo hesitation-delay fix
fcs = re.sub(
    r'''if \(unsavedGestures\.isEmpty\(\)\) \{\s*glassHiddenAt = 0L\s*\} else \{\s*glassHiddenAt = android\.os\.SystemClock\.uptimeMillis\(\)\s*\}''',
    'glassHiddenAt = 0L',
    fcs,
    count=1,
    flags=re.S
)

fcs = re.sub(
    r'''unsavedGestures = edited\s*\n\s*glassHiddenAt = android\.os\.SystemClock\.uptimeMillis\(\)''',
    'unsavedGestures = edited\n            glassHiddenAt = 0L',
    fcs,
    count=1,
    flags=re.S
)

# Multi-touch corrupt recording fix
if "private var ignoreCurrentGesture = false" not in fcs:
    fcs = fcs.replace(
        "private var currentSnapshot: TargetSnapshot? = null",
        "private var currentSnapshot: TargetSnapshot? = null\n    private var ignoreCurrentGesture = false"
    )

new_touch = r'''    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.pointerCount > 1 ||
            event.actionMasked == android.view.MotionEvent.ACTION_POINTER_DOWN ||
            event.actionMasked == android.view.MotionEvent.ACTION_POINTER_UP
        ) {
            ignoreCurrentGesture = true
            currentPoints.clear()
            currentSnapshot = null
            return true
        }

        if (ignoreCurrentGesture &&
            event.actionMasked != android.view.MotionEvent.ACTION_DOWN &&
            event.actionMasked != android.view.MotionEvent.ACTION_CANCEL
        ) {
            if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                ignoreCurrentGesture = false
                currentPoints.clear()
                currentSnapshot = null
            }
            return true
        }

        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                ignoreCurrentGesture = false
                currentPoints.clear()
                currentSnapshot = null
                currentGestureDownTime = event.eventTime
                addPoint(event, forceAdd = true)
                currentSnapshot = captureSnapshotFor(event.rawX.toInt(), event.rawY.toInt())
            }

            android.view.MotionEvent.ACTION_MOVE -> {
                if (!ignoreCurrentGesture) {
                    addPoint(event, forceAdd = false)
                }
            }

            android.view.MotionEvent.ACTION_UP -> {
                if (ignoreCurrentGesture) {
                    ignoreCurrentGesture = false
                    currentPoints.clear()
                    currentSnapshot = null
                    return true
                }
                addPoint(event, forceAdd = true)
                saveCurrentGesture()
            }

            android.view.MotionEvent.ACTION_CANCEL -> {
                ignoreCurrentGesture = false
                currentPoints.clear()
                currentSnapshot = null
            }
        }
        return true
    }
'''

fcs = replace_regex(
    fcs,
    r'    override fun onTouchEvent\(event: android\.view\.MotionEvent\): Boolean \{.*?\n        private fun addPoint',
    new_touch + "\n\n        private fun addPoint",
    "multi-touch recording guard"
)

FCS.write_text(fcs)

print("\\n✅ Final autofix apply ho gaya.")
print("🔎 Remaining compile-check:")
print("grep -R \\"safeNodeChild\\" -n app/src/main/java/com/aarishkhan/aarishai || echo \\"✅ safeNodeChild typo gone\\"")
