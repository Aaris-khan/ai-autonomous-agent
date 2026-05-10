import re

BASE = "app/src/main/java/com/aarishkhan/aarishai"
FCS = f"{BASE}/FloatingControlService.kt"
AAS = f"{BASE}/AutoActionService.kt"
GS = f"{BASE}/GestureStore.kt"

print("🚀 Level 2 Safety Core Fix apply ho raha hai...")

# ==================================================
# 1) FloatingControlService.kt fixes
# ==================================================
with open(FCS, "r", encoding="utf-8") as f:
    fcs = f.read()

# CUT active recording data-loss fix
old_cut_start = '''    private fun stopEverythingAndClose() {
                                if (unsavedGestures.isNotEmpty() && !pendingDiscardConfirm) {'''

new_cut_start = '''    private fun stopEverythingAndClose() {
        val hasLiveRecording = isRecording && ((captureView as? TouchCaptureView)?.getRecordedGestures()?.isNotEmpty() == true)

        if ((unsavedGestures.isNotEmpty() || hasLiveRecording) && !pendingDiscardConfirm) {'''

fcs = fcs.replace(old_cut_start, new_cut_start)

# bringPanelToFront safer: agar re-add fail ho, service ghost na bane
old_bring = '''    private fun bringPanelToFront() {
        val panel = panelView ?: return
        val params = panelParams ?: return
        safeRemoveView(panel)
        safeAddView(panel, params, "Panel restore error")
    }'''

new_bring = '''    private fun bringPanelToFront() {
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

fcs = fcs.replace(old_bring, new_bring)

# ACTION_CANCEL should not save partial gesture
old_cancel = '''            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                addPoint(event, forceAdd = true)
                saveCurrentGesture()
            }'''

new_cancel = '''            android.view.MotionEvent.ACTION_UP -> {
                addPoint(event, forceAdd = true)
                saveCurrentGesture()
            }
            android.view.MotionEvent.ACTION_CANCEL -> {
                currentPoints.clear()
            }'''

fcs = fcs.replace(old_cancel, new_cancel)

# onStartCommand explicit START_NOT_STICKY
if "override fun onStartCommand" not in fcs:
    insert_after = "    override fun onBind(intent: Intent?): IBinder? = null\n"
    addition = '''
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }
'''
    fcs = fcs.replace(insert_after, insert_after + addition)

with open(FCS, "w", encoding="utf-8") as f:
    f.write(fcs)

print("✅ FloatingControlService.kt fixed")


# ==================================================
# 2) AutoActionService.kt fixes
# ==================================================
with open(AAS, "r", encoding="utf-8") as f:
    aas = f.read()

# targetDesc exact/partial double scoring fix
old_desc_exact = '''        if (!gesture.targetDesc.isNullOrBlank() &&
            nodeDesc?.equals(gesture.targetDesc, ignoreCase = true) == true
        ) {
            score += 90
        }'''

new_desc_exact = '''        val exactDescMatch =
            !gesture.targetDesc.isNullOrBlank() &&
            nodeDesc?.equals(gesture.targetDesc, ignoreCase = true) == true

        if (exactDescMatch) {
            score += 90
        }'''

aas = aas.replace(old_desc_exact, new_desc_exact)

old_desc_contains = '''        if (!gesture.targetDesc.isNullOrBlank() &&
            nodeDesc?.contains(gesture.targetDesc, ignoreCase = true) == true
        ) {
            score += 45
        }'''

new_desc_contains = '''        if (!exactDescMatch &&
            !gesture.targetDesc.isNullOrBlank() &&
            nodeDesc?.contains(gesture.targetDesc, ignoreCase = true) == true
        ) {
            score += 45
        }'''

aas = aas.replace(old_desc_contains, new_desc_contains)

# finishTask buffer + activeGesture wait
old_finish_post = '''        scheduledTasks.add(finishTask)
        handler.postDelayed(finishTask, totalDuration)'''

new_finish_post = '''        val guardedFinishTask = object : Runnable {
            override fun run() {
                if (activeGestureCount.get() > 0 && isPlayingInternal) {
                    handler.postDelayed(this, 150L)
                } else {
                    finishTask.run()
                }
            }
        }

        scheduledTasks.add(guardedFinishTask)
        handler.postDelayed(guardedFinishTask, totalDuration + 300L)'''

aas = aas.replace(old_finish_post, new_finish_post)

# findClickableParent cycle/depth guard
old_parent = '''    private fun findClickableParent(
        node: AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        var current = node

        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }

        return null
    }'''

new_parent = '''    private fun findClickableParent(
        node: AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        var current = node
        var depth = 0

        while (current != null && depth < 30) {
            if (current.isClickable) return current
            val next = current.parent
            if (next == current) return null
            current = next
            depth++
        }

        return null
    }'''

aas = aas.replace(old_parent, new_parent)

with open(AAS, "w", encoding="utf-8") as f:
    f.write(aas)

print("✅ AutoActionService.kt fixed")


# ==================================================
# 3) GestureStore.kt small safety fix
# ==================================================
with open(GS, "r", encoding="utf-8") as f:
    gs = f.read()

gs = gs.replace('return !raw.isNullOrEmpty() && raw != "[]"', 'return load(context).isNotEmpty()')

gs = gs.replace(
    '.putString(KEY_GESTURES, mainArray.toString())\n            .apply()',
    '.putString(KEY_GESTURES, mainArray.toString())\n            .commit()'
)

with open(GS, "w", encoding="utf-8") as f:
    f.write(gs)

print("✅ GestureStore.kt fixed")

print("")
print("🎯 DONE: Level 2 safety core bugs fixed.")
