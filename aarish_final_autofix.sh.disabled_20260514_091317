#!/bin/bash
set -e

AUTO="app/src/main/java/com/aarishkhan/aarishai/AutoActionService.kt"
FLOAT="app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt"
STORE="app/src/main/java/com/aarishkhan/aarishai/GestureStore.kt"
MAIN="app/src/main/java/com/aarishkhan/aarishai/MainActivity.kt"
TS=$(date +%Y%m%d_%H%M%S)

cp "$AUTO" "$AUTO.bak_$TS"
cp "$FLOAT" "$FLOAT.bak_$TS"
cp "$STORE" "$STORE.bak_$TS"
cp "$MAIN" "$MAIN.bak_$TS"

python3 - <<'PY'
from pathlib import Path
import re

auto = Path("app/src/main/java/com/aarishkhan/aarishai/AutoActionService.kt")
flo = Path("app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt")
store = Path("app/src/main/java/com/aarishkhan/aarishai/GestureStore.kt")
main = Path("app/src/main/java/com/aarishkhan/aarishai/MainActivity.kt")

# ---------------- AutoActionService.kt ----------------
s = auto.read_text()

# Fix compile typo
s = s.replace("safeNodeChildCount(", "safeChildCount(")
s = s.replace("safeNodeChild(", "safeChild(")

# Simplify active gesture decrement
s = re.sub(
    r'''    private fun decrementActiveGestureSafely\(\) \{\n        while \(true\) \{\n            val current = activeGestureCount\.get\(\)\n            if \(current <= 0\) return\n            if \(activeGestureCount\.compareAndSet\(current, current - 1\)\) return\n        \}\n    \}''',
    '''    private fun decrementActiveGestureSafely() {
        if (activeGestureCount.get() > 0) {
            activeGestureCount.decrementAndGet()
        }
    }''',
    s,
    count=1
)

# Loop cooldown between repeated loops
old = '''            if (shouldContinue && isSamePlaybackRun(runId)) {
                scheduledTasks.forEach { handler.removeCallbacks(it) }
                scheduledTasks.clear()
                playSequence(orderedGestures, runId)
            } else if (isCurrentCallbackRun(runId)) {'''
new = '''            if (shouldContinue && isSamePlaybackRun(runId)) {
                scheduledTasks.forEach { handler.removeCallbacks(it) }
                scheduledTasks.clear()

                val loopCooldownTask = object : Runnable {
                    override fun run() {
                        scheduledTasks.remove(this)
                        if (isSamePlaybackRun(runId)) {
                            playSequence(orderedGestures, runId)
                        }
                    }
                }
                scheduledTasks.add(loopCooldownTask)
                handler.postDelayed(loopCooldownTask, 700L)
            } else if (isCurrentCallbackRun(runId)) {'''
if old in s:
    s = s.replace(old, new, 1)

# Orientation mismatch guard after screenW/screenH in dispatchOneGesture
needle = '''        val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

        val fallbackX = if (recordedGesture.xPercent > 0f) {'''
insert = '''        val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

        val orientationMismatch =
            recordedGesture.recordedScreenW > 0 &&
            recordedGesture.recordedScreenH > 0 &&
            ((recordedGesture.recordedScreenW > recordedGesture.recordedScreenH) != (screenW > screenH))

        if (orientationMismatch && (movement || match == null)) {
            showTinyToast("Orientation badal gayi, gesture skip")
            return
        }

        val fallbackX = if (recordedGesture.xPercent > 0f) {'''
if needle in s and "Orientation badal gayi, gesture skip" not in s:
    s = s.replace(needle, insert, 1)

# scoreNode: move hasAnyIdentity check to top
if "// FAST EXIT: no saved identity" not in s:
    bottom_pat = r'''
            val hasAnyIdentity =
                !gesture\.targetId\.isNullOrBlank\(\) \|\|
                !gesture\.targetText\.isNullOrBlank\(\) \|\|
                !gesture\.targetDesc\.isNullOrBlank\(\) \|\|
                !gesture\.targetClass\.isNullOrBlank\(\) \|\|
                !gesture\.targetContextText\.isNullOrBlank\(\) \|\|
                !gesture\.targetChildText\.isNullOrBlank\(\) \|\|
                !gesture\.targetSiblingText\.isNullOrBlank\(\) \|\|
                !gesture\.targetRoleFlags\.isNullOrBlank\(\) \|\|
                !gesture\.targetTreePath\.isNullOrBlank\(\) \|\|
                gesture\.targetWPercent > 0f

            if \(!hasAnyIdentity\) return 0

            score'''
    s = re.sub(bottom_pat, "\n\n            score", s, count=1)

    top_old = '''            if (!safeVisible(node) || !safeEnabled(node)) return 0

            var score = 0'''
    top_new = '''            if (!safeVisible(node) || !safeEnabled(node)) return 0

            // FAST EXIT: no saved identity means smart-target scoring is useless.
            val hasAnyIdentity =
                !gesture.targetId.isNullOrBlank() ||
                !gesture.targetText.isNullOrBlank() ||
                !gesture.targetDesc.isNullOrBlank() ||
                !gesture.targetClass.isNullOrBlank() ||
                !gesture.targetContextText.isNullOrBlank() ||
                !gesture.targetChildText.isNullOrBlank() ||
                !gesture.targetSiblingText.isNullOrBlank() ||
                !gesture.targetRoleFlags.isNullOrBlank() ||
                !gesture.targetTreePath.isNullOrBlank() ||
                gesture.targetWPercent > 0f

            if (!hasAnyIdentity) return 0

            var score = 0'''
    s = s.replace(top_old, top_new, 1)

# Safer direct stale-node reads in findExactActionButtonAcrossWindows
s = s.replace("if (!node.isVisibleToUser || !node.isEnabled) {", "if (!safeVisible(node) || !safeEnabled(node)) {")
s = s.replace("val text = node.text?.toString()?.trim()?.lowercase()", "val text = safeText(node)?.trim()?.lowercase()")
s = s.replace("val desc = node.contentDescription?.toString()?.trim()?.lowercase()", "val desc = safeDesc(node)?.trim()?.lowercase()")
s = s.replace("if (clickable.isVisibleToUser && clickable.isEnabled) {", "if (safeVisible(clickable) && safeEnabled(clickable)) {")
s = s.replace("if (clickable.isClickable) score += 50", "if (safeClickable(clickable)) score += 50")

# Capture snapshot: safe properties + screen size fields
s = s.replace("clickNode.getBoundsInScreen(bounds)", "safeBounds(clickNode, bounds)")
s = s.replace("targetText = clickNode.text?.toString() ?: touchedNode.text?.toString(),", "targetText = safeText(clickNode) ?: safeText(touchedNode),")
s = s.replace("targetDesc = clickNode.contentDescription?.toString() ?: touchedNode.contentDescription?.toString(),", "targetDesc = safeDesc(clickNode) ?: safeDesc(touchedNode),")
s = s.replace("targetId = clickNode.viewIdResourceName ?: touchedNode.viewIdResourceName,", "targetId = safeId(clickNode) ?: safeId(touchedNode),")
s = s.replace("targetClass = clickNode.className?.toString() ?: touchedNode.className?.toString(),", "targetClass = safeClass(clickNode) ?: safeClass(touchedNode),")

snap_old = '''            insideXPercent = if (bounds.width() > 0) ((x - bounds.left).toFloat() / bounds.width()).coerceIn(0f, 1f) else 0.5f,
            insideYPercent = if (bounds.height() > 0) ((y - bounds.top).toFloat() / bounds.height()).coerceIn(0f, 1f) else 0.5f
        )'''
snap_new = '''            insideXPercent = if (bounds.width() > 0) ((x - bounds.left).toFloat() / bounds.width()).coerceIn(0f, 1f) else 0.5f,
            insideYPercent = if (bounds.height() > 0) ((y - bounds.top).toFloat() / bounds.height()).coerceIn(0f, 1f) else 0.5f,
            recordedScreenW = safeW.toInt(),
            recordedScreenH = safeH.toInt()
        )'''
if snap_old in s and "recordedScreenW = safeW.toInt()" not in s:
    s = s.replace(snap_old, snap_new, 1)

# Duplicate root traversal guard in selection check
dup_old = "            rootInActiveWindow?.let { roots.add(it) }"
dup_new = '''            rootInActiveWindow?.let { active ->
                val activeBounds = Rect()
                if (safeBounds(active, activeBounds)) {
                    val alreadyAdded = roots.any { root ->
                        val rootBounds = Rect()
                        safeBounds(root, rootBounds) && rootBounds == activeBounds
                    }
                    if (!alreadyAdded) roots.add(active)
                }
            }'''
if dup_old in s:
    s = s.replace(dup_old, dup_new, 1)

auto.write_text(s)

# ---------------- FloatingControlService.kt ----------------
s = flo.read_text()

# Hidden panel coordinate overwrite guard
if "private var panelHiddenForPlayback = false" not in s:
    s = s.replace(
        "    private var oldPanelY = 120\n",
        "    private var oldPanelY = 120\n    private var panelHiddenForPlayback = false\n",
        1
    )

if "if (panelHiddenForPlayback) return" not in s:
    s = s.replace(
        "    private fun hidePanelUIForPlayback() {\n",
        "    private fun hidePanelUIForPlayback() {\n        if (panelHiddenForPlayback) return\n        panelHiddenForPlayback = true\n",
        1
    )

if "panelHiddenForPlayback = false" not in s[s.find("private fun restorePanelUI()"):s.find("private fun restorePanelUI()")+250]:
    s = s.replace(
        "    private fun restorePanelUI() {\n",
        "    private fun restorePanelUI() {\n        panelHiddenForPlayback = false\n",
        1
    )

# Long-press clear should hide CLR
if "btnClear?.visibility = View.GONE" not in s[s.find("Old command clear"):s.find("Old command clear")+400]:
    s = s.replace(
        "                    btnLoop.visibility = View.GONE\n                    btnSave.visibility = View.GONE\n",
        "                    btnLoop.visibility = View.GONE\n                    btnSave.visibility = View.GONE\n                    btnClear?.visibility = View.GONE\n",
        1
    )

# Undo unsaved Case 2: preserve deleted step's gap instead of losing timing
old = '''        if (unsavedGestures.isNotEmpty()) {
            unsavedGestures = unsavedGestures.sortedBy { it.delayFromStart }.dropLast(1)

            if (unsavedGestures.isEmpty()) {
                glassHiddenAt = 0L
            } else {
                glassHiddenAt = android.os.SystemClock.uptimeMillis()
            }

            val hasOld = GestureStore.hasRecording(this)'''
new = '''        if (unsavedGestures.isNotEmpty()) {
            val sortedUnsaved = unsavedGestures.sortedBy { it.delayFromStart }
            val removedGesture = sortedUnsaved.lastOrNull()
            val remainingGestures = sortedUnsaved.dropLast(1)
            val previousEnd = remainingGestures.maxOfOrNull { g ->
                g.delayFromStart.coerceAtLeast(0L) + (g.points.lastOrNull()?.t ?: 0L).coerceAtLeast(0L)
            } ?: 0L
            val preservedGap = ((removedGesture?.delayFromStart ?: previousEnd) - previousEnd)
                .coerceAtLeast(0L)
                .coerceAtMost(120000L)

            unsavedGestures = remainingGestures

            glassHiddenAt = if (unsavedGestures.isEmpty()) {
                0L
            } else {
                (android.os.SystemClock.uptimeMillis() - preservedGap).coerceAtLeast(0L)
            }

            val hasOld = GestureStore.hasRecording(this)'''
if old in s:
    s = s.replace(old, new, 1)

# Undo saved Case 3: after saving edited memory, don't keep false unsaved buffer
old = '''            unsavedGestures = edited
            glassHiddenAt = android.os.SystemClock.uptimeMillis()

            updateUIState("+ ADD", true, false, true)
            restorePanelUI()
            Toast.makeText(this, "↩️ Saved memory ka last step delete ho gaya. + ADD karo ya SAVE dabao.", Toast.LENGTH_LONG).show()
            return'''
new = '''            unsavedGestures = emptyList()
            glassHiddenAt = 0L

            updateUIState("PLAY", false, true, true)
            restorePanelUI()
            Toast.makeText(this, "↩️ Saved memory ka last step delete ho gaya.", Toast.LENGTH_SHORT).show()
            return'''
if old in s:
    s = s.replace(old, new, 1)

# TouchCaptureView: prevent final ACTION_UP ghost tap after multitouch
if "private var multiTouchCanceled = false" not in s:
    s = s.replace(
        "    private var currentSnapshot: TargetSnapshot? = null\n",
        "    private var currentSnapshot: TargetSnapshot? = null\n    private var multiTouchCanceled = false\n",
        1
    )

old = '''        // Multi-touch/pinch/two-finger accidental input ko record mat karo.
        // Isse corrupt timestamps aur wrong path save hone se bachenge.
        if (event.pointerCount > 1) {
            currentPoints.clear()
            currentSnapshot = null
            return true
        }

        when (event.actionMasked) {'''
new = '''        // Multi-touch/pinch/two-finger accidental input ko record mat karo.
        // ACTION_POINTER_DOWN ke baad final ACTION_UP ko bhi block karo,
        // warna ek ghost tap save ho sakta hai.
        if (event.pointerCount > 1 ||
            event.actionMasked == android.view.MotionEvent.ACTION_POINTER_DOWN ||
            event.actionMasked == android.view.MotionEvent.ACTION_POINTER_UP
        ) {
            currentPoints.clear()
            currentSnapshot = null
            multiTouchCanceled = true
            return true
        }

        if (multiTouchCanceled) {
            if (event.actionMasked == android.view.MotionEvent.ACTION_UP ||
                event.actionMasked == android.view.MotionEvent.ACTION_CANCEL
            ) {
                multiTouchCanceled = false
            }
            currentPoints.clear()
            currentSnapshot = null
            return true
        }

        when (event.actionMasked) {'''
if old in s:
    s = s.replace(old, new, 1)

# Save recorded screen size in RecordedGesture
old = '''                insideXPercent = snapshot?.insideXPercent ?: 0.5f,
                insideYPercent = snapshot?.insideYPercent ?: 0.5f
            )'''
new = '''                insideXPercent = snapshot?.insideXPercent ?: 0.5f,
                insideYPercent = snapshot?.insideYPercent ?: 0.5f,
                recordedScreenW = snapshot?.recordedScreenW ?: metrics.widthPixels,
                recordedScreenH = snapshot?.recordedScreenH ?: metrics.heightPixels
            )'''
if old in s and "recordedScreenW = snapshot?.recordedScreenW" not in s:
    s = s.replace(old, new, 1)

flo.write_text(s)

# ---------------- GestureStore.kt ----------------
s = store.read_text()

# Add screen size fields to TargetSnapshot + RecordedGesture
if "val recordedScreenW: Int = 0" not in s:
    s = s.replace(
        "    val insideYPercent: Float = 0.5f\n)",
        "    val insideYPercent: Float = 0.5f,\n    val recordedScreenW: Int = 0,\n    val recordedScreenH: Int = 0\n)",
        2
    )

# Config name sanitization: remove comma, quotes, JSON-hostile symbols
if 'Regex("[^\\\\p{L}\\\\p{N} _.-]+")' not in s:
    s = s.replace(
        '            .replace(Regex("\\\\s+"), " ")\n            .trim()',
        '            .replace(Regex("\\\\s+"), " ")\n            .replace(Regex("[^\\\\p{L}\\\\p{N} _.-]+"), "")\n            .trim()',
        1
    )

# Save recorded screen size
old = '''            gestureObject.put("insideXPercent", gesture.insideXPercent.coerceIn(0f, 1f).toDouble())
            gestureObject.put("insideYPercent", gesture.insideYPercent.coerceIn(0f, 1f).toDouble())'''
new = '''            gestureObject.put("insideXPercent", gesture.insideXPercent.coerceIn(0f, 1f).toDouble())
            gestureObject.put("insideYPercent", gesture.insideYPercent.coerceIn(0f, 1f).toDouble())
            gestureObject.put("recordedScreenW", gesture.recordedScreenW.coerceAtLeast(0))
            gestureObject.put("recordedScreenH", gesture.recordedScreenH.coerceAtLeast(0))'''
if old in s and "gestureObject.put(\"recordedScreenW\"" not in s:
    s = s.replace(old, new, 1)

# Load recorded screen size
old = '''                        insideXPercent = gestureObject.optDouble("insideXPercent", 0.5).toFloat().coerceIn(0f, 1f),
                        insideYPercent = gestureObject.optDouble("insideYPercent", 0.5).toFloat().coerceIn(0f, 1f)
                    )'''
new = '''                        insideXPercent = gestureObject.optDouble("insideXPercent", 0.5).toFloat().coerceIn(0f, 1f),
                        insideYPercent = gestureObject.optDouble("insideYPercent", 0.5).toFloat().coerceIn(0f, 1f),
                        recordedScreenW = gestureObject.optInt("recordedScreenW", 0).coerceAtLeast(0),
                        recordedScreenH = gestureObject.optInt("recordedScreenH", 0).coerceAtLeast(0)
                    )'''
if old in s and "recordedScreenW = gestureObject.optInt" not in s:
    s = s.replace(old, new, 1)

store.write_text(s)

# ---------------- MainActivity.kt ----------------
s = main.read_text()

# elapsedRealtime should not be restored across recreate/reboot
s = re.sub(
    r'lastPermissionLaunchAt = savedInstanceState\?\.getLong\(KEY_LAST_PERMISSION_LAUNCH_AT, 0L\) \?: 0L',
    'lastPermissionLaunchAt = 0L',
    s,
    count=1
)
s = s.replace(
    'outState.putLong(KEY_LAST_PERMISSION_LAUNCH_AT, lastPermissionLaunchAt)',
    'outState.putLong(KEY_LAST_PERMISSION_LAUNCH_AT, 0L)'
)

main.write_text(s)

print("✅ Kotlin files patched")
PY

echo "🔎 Verify compile typo..."
if grep -R "safeNodeChild" -n app/src/main/java/com/aarishkhan/aarishai; then
  echo "❌ safeNodeChild typo abhi bhi bacha hai"
  exit 1
fi

echo "🚀 Build check..."
./gradlew assembleDebug
