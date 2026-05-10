import re

BASE = "app/src/main/java/com/aarishkhan/aarishai"
AAS = f"{BASE}/AutoActionService.kt"
FCS = f"{BASE}/FloatingControlService.kt"

print("🚀 Level 3 SAFE FINAL Patch apply ho raha hai...")

# ==================================================
# 1) FloatingControlService.kt
# ==================================================
with open(FCS, "r", encoding="utf-8") as f:
    fcs = f.read()

# Snapshot field add
if "private var currentSnapshot: TargetSnapshot? = null" not in fcs:
    fcs = fcs.replace(
        "private var currentGestureDownTime = 0L",
        "private var currentGestureDownTime = 0L\n    private var currentSnapshot: TargetSnapshot? = null"
    )

# ACTION_DOWN par snapshot capture
fcs = fcs.replace(
'''            android.view.MotionEvent.ACTION_DOWN -> {
                currentPoints.clear()
                currentGestureDownTime = event.eventTime
                addPoint(event, forceAdd = true)
            }''',
'''            android.view.MotionEvent.ACTION_DOWN -> {
                currentPoints.clear()
                currentGestureDownTime = event.eventTime
                addPoint(event, forceAdd = true)

                // Button/symbol ka snapshot DOWN par lo, screen change hone se pehle
                currentSnapshot = captureSnapshotFor(event.rawX.toInt(), event.rawY.toInt())
            }'''
)

# ACTION_CANCEL par snapshot clear
fcs = fcs.replace(
'''            android.view.MotionEvent.ACTION_CANCEL -> {
                currentPoints.clear()
            }''',
'''            android.view.MotionEvent.ACTION_CANCEL -> {
                currentPoints.clear()
                currentSnapshot = null
            }'''
)

# Helper add before saveCurrentGesture
if "private fun captureSnapshotFor(" not in fcs:
    fcs = fcs.replace(
'''    private fun saveCurrentGesture() {
        if (currentPoints.isEmpty()) return''',
'''    private fun captureSnapshotFor(x: Int, y: Int): TargetSnapshot? {
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = metrics.heightPixels.toFloat().coerceAtLeast(1f)

        return AutoActionService.captureTargetSnapshot(
            x,
            y,
            screenW,
            screenH
        )
    }

    private fun saveCurrentGesture() {
        if (currentPoints.isEmpty()) return'''
    )

# saveCurrentGesture mein ACTION_DOWN snapshot use
fcs = fcs.replace(
'''        val snapshot = AutoActionService.captureTargetSnapshot(
            firstP.x.toInt(),
            firstP.y.toInt(),
            screenW,
            screenH
        )''',
'''        val snapshot = currentSnapshot ?: captureSnapshotFor(
            firstP.x.toInt(),
            firstP.y.toInt()
        )'''
)

# Save ke baad snapshot clear
fcs = fcs.replace(
'''        currentPoints.clear()
    }


    fun addSystemGesture''',
'''        currentPoints.clear()
        currentSnapshot = null
    }


    fun addSystemGesture'''
)

# makePanelDraggable threshold fix
drag_pattern = r'''    private fun makePanelDraggable\(dragHandle: View, root: View, params: WindowManager.LayoutParams\) \{.*?\n    private fun safeAddView'''

new_drag = r'''    private fun makePanelDraggable(dragHandle: View, root: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        val dragSlop = 10f

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    if (!isDragging &&
                        (kotlin.math.abs(dx) > dragSlop || kotlin.math.abs(dy) > dragSlop)
                    ) {
                        isDragging = true
                    }

                    if (isDragging) {
                        val metrics = root.context.resources.displayMetrics
                        val maxX = if (root.width > 0) metrics.widthPixels - root.width else metrics.widthPixels / 2
                        val maxY = if (root.height > 0) metrics.heightPixels - root.height else metrics.heightPixels / 2

                        val newX = initialX + dx.toInt()
                        val newY = initialY + dy.toInt()

                        params.x = newX.coerceIn(0, if (maxX > 0) maxX else metrics.widthPixels)
                        params.y = newY.coerceIn(0, if (maxY > 0) maxY else metrics.heightPixels)

                        safeUpdateView(root, params)
                    }

                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    true
                }

                else -> true
            }
        }
    }

    private fun safeAddView'''

fcs = re.sub(drag_pattern, new_drag, fcs, flags=re.DOTALL)

# bringPanelToFront clean retry
bring_pattern = r'''    private fun bringPanelToFront\(\) \{.*?\n    // BUG #4 FIX'''

new_bring = r'''    private fun bringPanelToFront() {
        val panel = panelView ?: return
        val params = panelParams ?: return

        try {
            windowManager.removeView(panel)
        } catch (_: Exception) {
        }

        val firstAdded = safeAddView(panel, params, "Panel restore error")
        if (!firstAdded) {
            handler.postDelayed({
                val retryAdded = safeAddView(panel, params, "Panel retry error")
                if (!retryAdded) {
                    panelView = null
                    Toast.makeText(
                        this,
                        "Panel wapas nahi aa paya, service band kar rahe hain",
                        Toast.LENGTH_LONG
                    ).show()
                    stopSelf()
                }
            }, 250L)
        }
    }

     

    // BUG #4 FIX'''

fcs = re.sub(bring_pattern, new_bring, fcs, flags=re.DOTALL)

# playNow false state recovery
fcs = fcs.replace(
'''            val started = AutoActionService.playNow(this)
            if (started) {
                updateUIState("STOP", false, false, false) // CUT button hide
                hidePanelUIForPlayback()
                checkPlaybackStateContinuously()
            }''',
'''            val started = AutoActionService.playNow(this)
            if (started) {
                updateUIState("STOP", false, false, false)
                hidePanelUIForPlayback()
                checkPlaybackStateContinuously()
            } else {
                updateUIState("PLAY", false, true, true)
                restorePanelUI()
            }'''
)

# watcher text-state weakness reduce
fcs = fcs.replace(
'''                    if (!isRecording && btnStart.text == "STOP") {
                        btnStart.text = "PLAY"
                        restorePanelUI()
                    }''',
'''                    if (!isRecording) {
                        updateUIState(
                            if (GestureStore.hasRecording(this@FloatingControlService)) "PLAY" else "START",
                            false,
                            true,
                            true
                        )
                        restorePanelUI()
                    }'''
)

with open(FCS, "w", encoding="utf-8") as f:
    f.write(fcs)

print("✅ FloatingControlService.kt patched")


# ==================================================
# 2) AutoActionService.kt
# ==================================================
with open(AAS, "r", encoding="utf-8") as f:
    aas = f.read()

# findBestSmartTarget root should use fallback coordinate
aas = aas.replace(
'''    private fun findBestSmartTarget(gesture: RecordedGesture): SmartMatch? {
        val root = getRealAppRoot() ?: return null''',
'''    private fun findBestSmartTarget(gesture: RecordedGesture): SmartMatch? {
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

        val root = getRealAppRootForPoint(fallbackX.toInt(), fallbackY.toInt()) ?: return null'''
)

# SmartMatch should normalize candidate to clickable parent bounds
old_best_block = '''                val score = scoreNode(node, bounds, gesture)
                if (score > 0 && (best == null || score > best!!.score)) {
                    best = SmartMatch(node, Rect(bounds), score)
                }'''

new_best_block = '''                val score = scoreNode(node, bounds, gesture)
                if (score > 0) {
                    val targetNode = findClickableParent(node) ?: node
                    val targetBounds = Rect()
                    targetNode.getBoundsInScreen(targetBounds)

                    if (targetBounds.width() > 0 && targetBounds.height() > 0) {
                        val finalScore = score + if (targetNode.isClickable) 12 else 0
                        if (best == null || finalScore > best!!.score) {
                            best = SmartMatch(targetNode, Rect(targetBounds), finalScore)
                        }
                    }
                }'''

aas = aas.replace(old_best_block, new_best_block)

# O(n*depth) scoring line remove
aas = aas.replace(
    "        if (findClickableParent(node) != null) score += 8",
    "        // Parent traversal scoring removed; candidate normalization happens in findBestSmartTarget()."
)

# targetClass match safer: simple class name / button-like similarity
old_class = '''        if (!gesture.targetClass.isNullOrBlank() && nodeClass == gesture.targetClass) {
            score += 20
        }'''

new_class = '''        if (!gesture.targetClass.isNullOrBlank() && !nodeClass.isNullOrBlank()) {
            val savedSimple = gesture.targetClass.substringAfterLast('.').lowercase()
            val nowSimple = nodeClass.substringAfterLast('.').lowercase()

            if (savedSimple == nowSimple ||
                (savedSimple.contains("button") && nowSimple.contains("button")) ||
                (savedSimple.contains("text") && nowSimple.contains("text")) ||
                (savedSimple.contains("image") && nowSimple.contains("image"))
            ) {
                score += 18
            }
        }'''

aas = aas.replace(old_class, new_class)

# position penalty softer for strong identity
aas = aas.replace(
'''            else if (dx > 0.40f || dy > 0.40f) {
                val strongIdentity =
                    (!gesture.targetId.isNullOrBlank() && nodeId == gesture.targetId) ||
                    exactTextMatch ||
                    exactDescMatch

                score -= if (strongIdentity) 15 else 50
            }''',
'''            else if (dx > 0.40f || dy > 0.40f) {
                val strongIdentity =
                    (!gesture.targetId.isNullOrBlank() && nodeId == gesture.targetId) ||
                    exactTextMatch ||
                    exactDescMatch

                score -= if (strongIdentity) 10 else 45
            }'''
)

# If old -50 line still exists, patch it too
aas = aas.replace(
'''            else if (dx > 0.40f || dy > 0.40f) score -= 50''',
'''            else if (dx > 0.40f || dy > 0.40f) {
                val strongIdentity =
                    (!gesture.targetId.isNullOrBlank() && nodeId == gesture.targetId) ||
                    exactTextMatch ||
                    exactDescMatch

                score -= if (strongIdentity) 10 else 45
            }'''
)

with open(AAS, "w", encoding="utf-8") as f:
    f.write(aas)

print("✅ AutoActionService.kt patched")

print("")
print("🎯 DONE: Level 3 SAFE FINAL fixes applied.")
print("⚠️ Build ke baad Accessibility service OFF/ON karna.")
print("⚠️ Purani recording clear karke fresh recording banana.")
