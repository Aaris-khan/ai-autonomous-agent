#!/usr/bin/env bash
set -e

python3 - <<'PY'
from pathlib import Path
import time, shutil, sys

FCS = Path("app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt")

if not FCS.exists():
    sys.exit("❌ FloatingControlService.kt nahi mila")

stamp = time.strftime("%Y%m%d_%H%M%S")
shutil.copy2(FCS, FCS.with_suffix(FCS.suffix + f".bak_{stamp}"))

s = FCS.read_text(encoding="utf-8")
changes = []

# 1) active dialog holder add
if "private var activeConfigDialog: android.app.AlertDialog? = null" not in s:
    s = s.replace(
        "private var playbackWatcherRunnable: Runnable? = null",
        "private var playbackWatcherRunnable: Runnable? = null\n    private var activeConfigDialog: android.app.AlertDialog? = null"
    )
    changes.append("✅ activeConfigDialog holder added")

# 2) closeSettingsPanel empty fix
old = "private fun closeSettingsPanel() {}"
new = '''private fun closeSettingsPanel() {
        try {
            activeConfigDialog?.dismiss()
        } catch (_: Exception) {
        }
        activeConfigDialog = null
    }'''
if old in s:
    s = s.replace(old, new)
    changes.append("✅ closeSettingsPanel now dismisses config popup")

# 3) showOverlayDialogSafely lifecycle fix
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
if old in s:
    s = s.replace(old, new)
    changes.append("✅ config dialog lifecycle fixed")

# 4) dismiss dialog in onDestroy
old = '''playbackWatcherRunnable = null
        handler.removeCallbacksAndMessages(null)'''
new = '''playbackWatcherRunnable = null
        closeSettingsPanel()
        handler.removeCallbacksAndMessages(null)'''
if old in s and "closeSettingsPanel()\n        handler.removeCallbacksAndMessages(null)" not in s:
    s = s.replace(old, new)
    changes.append("✅ dialog dismissed in onDestroy")

# 5) Empty HIDE branch fix
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
if old in s:
    s = s.replace(old, new)
    changes.append("✅ empty HIDE / +ADD UI bug fixed")

# 6) Undo one-step saved macro clear fix
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
if old in s:
    s = s.replace(old, new)
    changes.append("✅ one-step saved undo now clears memory")

FCS.write_text(s, encoding="utf-8")

print("\\n".join(changes) if changes else "ℹ️ Ye fixes pehle se applied lag rahe hain")
print(f"✅ Backup created: {FCS}.bak_{stamp}")
PY

./gradlew assembleDebug
