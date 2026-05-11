#!/usr/bin/env bash
set -euo pipefail

FILE="app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt"

if [ ! -f "$FILE" ]; then
  echo "❌ File nahi mili: $FILE"
  exit 1
fi

cp "$FILE" "$FILE.bak_persistent_undo_$(date +%Y%m%d_%H%M%S)"

python3 << 'PY'
from pathlib import Path
import re
import sys

p = Path("app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt")
s = p.read_text(encoding="utf-8")

def fail(msg):
    print("❌", msg)
    sys.exit(1)

def replace_once(old, new, name):
    global s
    if old not in s:
        fail(f"Anchor missing: {name}")
    s = s.replace(old, new, 1)

# 1) btnUndo field
if "private lateinit var btnUndo: Button" not in s:
    replace_once(
        "    private lateinit var btnLoop: Button\n"
        "    private lateinit var btnSave: Button\n"
        "    private lateinit var btnCut: Button",
        "    private lateinit var btnLoop: Button\n"
        "    private lateinit var btnSave: Button\n"
        "    private lateinit var btnUndo: Button\n"
        "    private lateinit var btnCut: Button",
        "btnUndo field"
    )

# 2) btnUndo create
if 'text = "UNDO"' not in s:
    replace_once(
        '''        btnSave = Button(this).apply {
            text = "SAVE"
            visibility = View.GONE
            setOnClickListener { saveRecording() }
        }

        btnCut = Button(this).apply {''',
        '''        btnSave = Button(this).apply {
            text = "SAVE"
            visibility = View.GONE
            setOnClickListener { saveRecording() }
        }

        btnUndo = Button(this).apply {
            text = "UNDO"
            visibility = View.GONE
            setOnClickListener { undoLastStep() }
        }

        btnCut = Button(this).apply {''',
        "btnUndo create"
    )

# 3) add btnUndo to root
if "root.addView(btnUndo)" not in s:
    replace_once(
        '''        root.addView(btnLoop)
        root.addView(btnSave)
        root.addView(btnCut)''',
        '''        root.addView(btnLoop)
        root.addView(btnSave)
        root.addView(btnUndo)
        root.addView(btnCut)''',
        "root add btnUndo"
    )

# 4) helper function: saved + unsaved dono ke liye undo visibility
if "private fun hasAnythingToUndo()" not in s:
    insert = r'''
    private fun hasAnythingToUndo(): Boolean {
        if (AutoActionService.isPlaying()) return false
        if (isRecording && captureView?.hasRecordedSomething() == true) return true
        if (unsavedGestures.isNotEmpty()) return true
        return GestureStore.hasRecording(this)
    }

'''
    replace_once(
        "    // ✅ SAFE UI STATE FIX: Default parameter added to prevent compile errors\n"
        "    private fun closeSettingsPanel() {}",
        "    // ✅ SAFE UI STATE FIX: Default parameter added to prevent compile errors\n"
        "    private fun closeSettingsPanel() {}\n\n" + insert.rstrip(),
        "hasAnythingToUndo insert"
    )

# 5) replace updateUIState fully
pattern = re.compile(
    r'''    private fun updateUIState\(startText: String, showSave: Boolean, showOthers: Boolean, showCut: Boolean = true\) \{.*?    \}\n\n    // ✅ EXACT NAVIGATION GAP FIX''',
    re.S
)

new_update = '''    private fun updateUIState(startText: String, showSave: Boolean, showOthers: Boolean, showCut: Boolean = true) {
        if (::btnStart.isInitialized) btnStart.text = startText
        if (::btnSave.isInitialized) btnSave.visibility = if (showSave) View.VISIBLE else View.GONE
        if (::btnUndo.isInitialized) {
            btnUndo.visibility = if (hasAnythingToUndo()) View.VISIBLE else View.GONE
        }
        if (::btnLoop.isInitialized) btnLoop.visibility = if (showOthers && GestureStore.hasRecording(this)) View.VISIBLE else View.GONE
        if (::btnCut.isInitialized) btnCut.visibility = if (showCut) View.VISIBLE else View.GONE
        btnClear?.visibility = if (showOthers) View.VISIBLE else View.GONE
        btnSettings?.visibility = if (showOthers) View.VISIBLE else View.GONE
        btnSpeed?.visibility = if (showOthers) View.VISIBLE else View.GONE
    }

    // ✅ EXACT NAVIGATION GAP FIX'''

if pattern.search(s):
    s = pattern.sub(new_update, s, count=1)
else:
    fail("updateUIState function replace nahi hua")

# 6) restorePanelUI: Undo saved/play ke baad bhi visible rahe
pattern_restore = re.compile(
    r'''    private fun restorePanelUI\(\) \{.*?        val params = panelParams''',
    re.S
)

new_restore_head = '''    private fun restorePanelUI() {
        btnSave.visibility = if (unsavedGestures.isNotEmpty()) View.VISIBLE else View.GONE
        btnUndo.visibility = if (hasAnythingToUndo()) View.VISIBLE else View.GONE
        btnCut.visibility = View.VISIBLE
        label.visibility = View.VISIBLE
        btnLoop.visibility = if (GestureStore.hasRecording(this)) View.VISIBLE else View.GONE

        val params = panelParams'''

if pattern_restore.search(s):
    s = pattern_restore.sub(new_restore_head, s, count=1)
else:
    fail("restorePanelUI replace nahi hua")

# 7) hidePanelUIForPlayback: playback ke time undo hide
if "btnUndo.visibility = View.GONE" not in s[s.find("private fun hidePanelUIForPlayback"):s.find("private fun restorePanelUI")]:
    replace_once(
        '''    private fun hidePanelUIForPlayback() {
        btnSave.visibility = View.GONE
        btnCut.visibility = View.GONE''',
        '''    private fun hidePanelUIForPlayback() {
        btnSave.visibility = View.GONE
        btnUndo.visibility = View.GONE
        btnCut.visibility = View.GONE''',
        "hide undo during playback"
    )

# 8) undoLastStep function replace or insert
undo_fn = r'''
    private fun undoLastStep() {
        if (AutoActionService.isPlaying()) {
            Toast.makeText(this, "Pehle playback STOP/complete hone do", Toast.LENGTH_SHORT).show()
            return
        }

        // Case 1: Glass ON hai, live recording ke andar last tap delete karo
        if (isRecording) {
            val removed = captureView?.removeLastGesture() == true
            if (removed) {
                Toast.makeText(this, "↩️ Last live step delete ho gaya", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Undo ke liye abhi koi live step nahi hai", Toast.LENGTH_SHORT).show()
            }
            updateUIState("HIDE", true, false, true)
            return
        }

        // Case 2: SAVE se pehle unsaved buffer ke andar last step delete karo
        if (unsavedGestures.isNotEmpty()) {
            unsavedGestures = unsavedGestures.sortedBy { it.delayFromStart }.dropLast(1)

            if (unsavedGestures.isEmpty()) {
                glassHiddenAt = 0L
            }

            val hasOld = GestureStore.hasRecording(this)
            val nextText = if (unsavedGestures.isNotEmpty()) "+ ADD" else if (hasOld) "PLAY" else "START"

            updateUIState(nextText, unsavedGestures.isNotEmpty(), hasOld, true)
            restorePanelUI()
            Toast.makeText(this, "↩️ Last unsaved step delete ho gaya", Toast.LENGTH_SHORT).show()
            return
        }

        // Case 3: SAVE + PLAY ke baad bhi saved memory se last step delete karo
        val savedGestures = GestureStore.load(this).sortedBy { it.delayFromStart }

        if (savedGestures.isNotEmpty()) {
            val edited = savedGestures.dropLast(1)

            if (edited.isEmpty()) {
                GestureStore.clear(this)
                unsavedGestures = emptyList()
                glassHiddenAt = 0L
                updateUIState("START", false, false, true)
                restorePanelUI()
                Toast.makeText(this, "↩️ Last step delete hua. Ab memory empty hai.", Toast.LENGTH_SHORT).show()
                return
            }

            val ok = GestureStore.save(this, edited)
            if (!ok) {
                Toast.makeText(this, "❌ Undo save nahi ho paya", Toast.LENGTH_LONG).show()
                return
            }

            unsavedGestures = edited
            val lastEnd = edited.maxOfOrNull { g ->
                g.delayFromStart.coerceAtLeast(0L) + (g.points.lastOrNull()?.t ?: 0L).coerceAtLeast(0L)
            } ?: 0L
            glassHiddenAt = android.os.SystemClock.uptimeMillis() - lastEnd.coerceAtLeast(0L)

            updateUIState("+ ADD", true, false, true)
            restorePanelUI()
            Toast.makeText(this, "↩️ Saved memory ka last step delete ho gaya. Ab + ADD se sahi step jodo.", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Undo karne ke liye kuch nahi hai", Toast.LENGTH_SHORT).show()
    }

'''

if "private fun undoLastStep()" in s:
    s = re.sub(
        r'''    private fun undoLastStep\(\) \{.*?    \}\n\n        private fun saveRecording\(\) \{''',
        undo_fn + "        private fun saveRecording() {",
        s,
        count=1,
        flags=re.S
    )
else:
    replace_once(
        "        private fun saveRecording() {",
        undo_fn + "        private fun saveRecording() {",
        "undoLastStep insert"
    )

# 9) TouchCaptureView helpers
if "fun hasRecordedSomething(): Boolean" not in s:
    if "fun removeLastGesture(): Boolean" in s:
        s = s.replace(
            "    fun removeLastGesture(): Boolean {",
            "    fun hasRecordedSomething(): Boolean {\n"
            "        return currentPoints.isNotEmpty() || recordedGestures.isNotEmpty()\n"
            "    }\n\n"
            "    fun removeLastGesture(): Boolean {",
            1
        )
    else:
        replace_once(
            '''    fun getRecordedGestures(): List<RecordedGesture> {
        return recordedGestures.sortedBy { it.delayFromStart }
    }''',
            '''    fun hasRecordedSomething(): Boolean {
        return currentPoints.isNotEmpty() || recordedGestures.isNotEmpty()
    }

    fun removeLastGesture(): Boolean {
        if (currentPoints.isNotEmpty()) {
            currentPoints.clear()
            currentSnapshot = null
            return true
        }

        if (recordedGestures.isNotEmpty()) {
            recordedGestures.removeAt(recordedGestures.lastIndex)
            currentSnapshot = null
            return true
        }

        return false
    }

    fun getRecordedGestures(): List<RecordedGesture> {
        return recordedGestures.sortedBy { it.delayFromStart }
    }''',
            "TouchCaptureView undo helpers"
        )

p.write_text(s, encoding="utf-8")
print("✅ Persistent UNDO apply ho gaya: SAVE + PLAY ke baad bhi last saved step undo hoga")
PY

./gradlew assembleDebug
