#!/usr/bin/env bash
set -e

python3 - <<'PY'
from pathlib import Path
import shutil, time, re, sys

FCS = Path("app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt")
GS = Path("app/src/main/java/com/aarishkhan/aarishai/GestureStore.kt")

for p in [FCS, GS]:
    if not p.exists():
        sys.exit(f"❌ Missing: {p}")

stamp = time.strftime("%Y%m%d_%H%M%S")
shutil.copy2(FCS, FCS.with_suffix(FCS.suffix + f".bak_{stamp}"))
shutil.copy2(GS, GS.with_suffix(GS.suffix + f".bak_{stamp}"))

f = FCS.read_text(encoding="utf-8")
g = GS.read_text(encoding="utf-8")
changes = []

# ------------------------------------------------------------
# FloatingControlService fixes
# ------------------------------------------------------------

if "private var activeConfigDialog: android.app.AlertDialog? = null" not in f:
    f = f.replace(
        "private var playbackWatcherRunnable: Runnable? = null",
        "private var playbackWatcherRunnable: Runnable? = null\n    private var activeConfigDialog: android.app.AlertDialog? = null"
    )
    changes.append("✅ activeConfigDialog added")

old = "private fun closeSettingsPanel() {}"
new = '''private fun closeSettingsPanel() {
        try {
            activeConfigDialog?.dismiss()
        } catch (_: Exception) {
        }
        activeConfigDialog = null
    }'''
if old in f:
    f = f.replace(old, new)
    changes.append("✅ closeSettingsPanel fixed")

old = '''private fun showOverlayDialogSafely(dialog: android.app.AlertDialog) {
        try {
            prepareOverlayDialog(dialog)
            dialog.show()
        } catch (e: Exception) {
            Toast.makeText(this, "Config popup open nahi hua: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }'''
new = '''private fun showOverlayDialogSafely(dialog: android.app.AlertDialog) {
        try {
            closeSettingsPanel()
            activeConfigDialog = dialog
            dialog.setOnDismissListener {
                if (activeConfigDialog === dialog) {
                    activeConfigDialog = null
                }
            }
            prepareOverlayDialog(dialog)
            dialog.show()
        } catch (e: Exception) {
            activeConfigDialog = null
            Toast.makeText(this, "Config popup open nahi hua: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }'''
if old in f:
    f = f.replace(old, new)
    changes.append("✅ overlay dialog lifecycle fixed")

if "closeSettingsPanel()\n        handler.removeCallbacksAndMessages(null)" not in f:
    f = f.replace(
        "playbackWatcherRunnable = null\n        handler.removeCallbacksAndMessages(null)",
        "playbackWatcherRunnable = null\n        closeSettingsPanel()\n        handler.removeCallbacksAndMessages(null)"
    )
    changes.append("✅ dialog dismiss added in onDestroy")

# Add real Clear button if missing
if 'text = "CLR"' not in f:
    marker = '''btnLoop = Button(this).apply {
            updateLoopButtonText(this)
            setOnClickListener {
                toggleLoopMode()
                updateLoopButtonText(this)
            }
            visibility = if (GestureStore.hasRecording(this@FloatingControlService)) View.VISIBLE else View.GONE
        }'''
    insert = marker + '''

        btnClear = Button(this).apply {
            text = "CLR"
            visibility = if (GestureStore.hasRecording(this@FloatingControlService)) View.VISIBLE else View.GONE
            setOnClickListener { clearSavedRecordingFromPanel() }
        }'''
    if marker in f:
        f = f.replace(marker, insert)
        changes.append("✅ CLR button initialized")

    if "root.addView(btnLoop)\n        btnClear?.let { root.addView(it) }" not in f:
        f = f.replace(
            "root.addView(btnLoop)",
            "root.addView(btnLoop)\n        btnClear?.let { root.addView(it) }"
        )
        changes.append("✅ CLR button added to panel")

# Empty HIDE fix
old = '''if (isRecording) {
            extractAndAppendGestures()
            isRecording = false
            safeRemoveView(captureView)
            captureView = null
            glassHiddenAt = android.os.SystemClock.uptimeMillis()
            updateUIState("+ ADD", unsavedGestures.isNotEmpty(), false, true)
            Toast.makeText(this, "Glass hat gaya! Naya page kholo aur + ADD dabao.", Toast.LENGTH_LONG).show()
            return
        }'''
new = '''if (isRecording) {
            extractAndAppendGestures()
            isRecording = false
            safeRemoveView(captureView)
            captureView = null
            glassHiddenAt = android.os.SystemClock.uptimeMillis()

            val hasOld = GestureStore.hasRecording(this)
            val hasUnsaved = unsavedGestures.isNotEmpty()
            val nextText = if (hasUnsaved) "+ ADD" else if (hasOld) "PLAY" else "START"

            updateUIState(nextText, hasUnsaved, hasOld, true)

            if (hasUnsaved) {
                Toast.makeText(this, "Glass hat gaya! Naya page kholo aur + ADD dabao.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Koi step record nahi hua", Toast.LENGTH_SHORT).show()
            }
            return
        }'''
if old in f:
    f = f.replace(old, new)
    changes.append("✅ empty recording HIDE bug fixed")

# Undo one-step permanent clear
old = '''if (edited.isEmpty()) {
                Toast.makeText(
                    this,
                    "Sirf 1 step hai. Use permanently clear karna ho to PLAY/START par long-press karo.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }'''
new = '''if (edited.isEmpty()) {
                GestureStore.clear(this)
                unsavedGestures = emptyList()
                glassHiddenAt = 0L
                updateUIState("START", false, false, true)
                restorePanelUI()
                Toast.makeText(this, "↩️ Last step delete hua. Ab memory empty hai.", Toast.LENGTH_SHORT).show()
                return
            }'''
if old in f:
    f = f.replace(old, new)
    changes.append("✅ one-step saved undo fixed")

# screenW/screenH shadow cleanup inside findBestSmartTarget
f2 = re.sub(
    r'(\s*var finalScore = score \+ if \(targetNode\.isClickable\) 12 else 0)\n\n\s*val screenW = resources\.displayMetrics\.widthPixels\.toFloat\(\)\.coerceAtLeast\(1f\)\n\s*val screenH = resources\.displayMetrics\.heightPixels\.toFloat\(\)\.coerceAtLeast\(1f\)',
    r'\1',
    f,
    count=1
)
if f2 != f:
    f = f2
    changes.append("✅ screenW/screenH shadow removed")

# ------------------------------------------------------------
# GestureStore loop fallback fixes
# ------------------------------------------------------------

old = '''val mode = p.getString(
            loopModeKey(context),
            p.getString(KEY_LOOP_MODE, "ONCE")
        ) ?: "ONCE"'''
new = '''val mode = p.getString(
            loopModeKey(context),
            "ONCE"
        ) ?: "ONCE"'''
if old in g:
    g = g.replace(old, new)
    changes.append("✅ getLoopMode config fallback fixed")

old = '''"COUNT" -> p.getInt(loopValueKey(context), p.getInt(KEY_LOOP_VALUE, 10)).coerceIn(1, 999)
            "TIME" -> p.getInt(loopValueKey(context), p.getInt(KEY_LOOP_VALUE, 5)).coerceIn(1, 240)'''
new = '''"COUNT" -> p.getInt(loopValueKey(context), 10).coerceIn(1, 999)
            "TIME" -> p.getInt(loopValueKey(context), 5).coerceIn(1, 240)'''
if old in g:
    g = g.replace(old, new)
    changes.append("✅ getLoopValue config fallback fixed")

FCS.write_text(f, encoding="utf-8")
GS.write_text(g, encoding="utf-8")

print("\\n".join(changes) if changes else "ℹ️ Safe fixes already applied")
print(f"✅ Backups: *.bak_{stamp}")
PY

./gradlew assembleDebug
