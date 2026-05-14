#!/usr/bin/env python3
from pathlib import Path
from datetime import datetime
import re, sys

ROOT = Path(".")
FCS = ROOT / "app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt"
GS  = ROOT / "app/src/main/java/com/aarishkhan/aarishai/GestureStore.kt"

STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")

def backup(path: Path):
    if path.exists():
        path.with_suffix(path.suffix + f".bak_{STAMP}").write_text(path.read_text(encoding="utf-8"), encoding="utf-8")

def must_exist(path: Path):
    if not path.exists():
        print(f"❌ Missing file: {path}")
        sys.exit(1)

def replace_once(text, old, new, label):
    if old not in text:
        print(f"⚠️ SKIP {label}: pattern not found")
        return text
    print(f"✅ PATCH {label}")
    return text.replace(old, new, 1)

def replace_regex(text, pattern, repl, label, flags=re.S):
    new_text, n = re.subn(pattern, repl, text, count=1, flags=flags)
    if n == 0:
        print(f"⚠️ SKIP {label}: regex not found")
        return text
    print(f"✅ PATCH {label}")
    return new_text

must_exist(FCS)
must_exist(GS)
backup(FCS)
backup(GS)

# =========================
# FloatingControlService.kt
# =========================
s = FCS.read_text(encoding="utf-8")

# 1) Add exact undo-gap override variable
s = replace_once(
    s,
    "    private var glassHiddenAt = 0L\n",
    "    private var glassHiddenAt = 0L\n    private var nextNavigationGapOverride: Long? = null\n",
    "FloatingControlService: nextNavigationGapOverride var"
) if "nextNavigationGapOverride" not in s else s

# 2) Strong btnClear visibility in updateUIState
s = replace_once(
    s,
    "        btnClear?.visibility = if (showOthers) View.VISIBLE else View.GONE\n",
    "        btnClear?.visibility = if (showOthers && GestureStore.hasRecording(this) && unsavedGestures.isEmpty()) View.VISIBLE else View.GONE\n",
    "FloatingControlService: btnClear visibility in updateUIState"
)

# 3) Fix extractAndAppendGestures exact navigation gap with override
old_nav = """        val navigationGap = if (unsavedGestures.isNotEmpty() && glassHiddenAt > 0L) {
            (view.getRecordingStartTime() - glassHiddenAt).coerceAtLeast(0L).coerceAtMost(120000L)
        } else {
            0L
        }

        val timeOffset = if (unsavedGestures.isEmpty()) 0L else lastGestureEnd + navigationGap
        unsavedGestures = (unsavedGestures + newGestures.map { g ->
            g.copy(delayFromStart = (g.delayFromStart + timeOffset).coerceAtLeast(0L))
        }).sortedBy { it.delayFromStart }"""
new_nav = """        val overrideGap = nextNavigationGapOverride
        val navigationGap = if (unsavedGestures.isNotEmpty()) {
            when {
                overrideGap != null -> overrideGap.coerceAtLeast(0L).coerceAtMost(120000L)
                glassHiddenAt > 0L -> (view.getRecordingStartTime() - glassHiddenAt).coerceAtLeast(0L).coerceAtMost(120000L)
                else -> 0L
            }
        } else {
            0L
        }

        nextNavigationGapOverride = null

        val timeOffset = if (unsavedGestures.isEmpty()) 0L else lastGestureEnd + navigationGap
        unsavedGestures = (unsavedGestures + newGestures.map { g ->
            g.copy(delayFromStart = (g.delayFromStart + timeOffset).coerceAtLeast(0L))
        }).sortedBy { it.delayFromStart }"""
s = replace_once(s, old_nav, new_nav, "FloatingControlService: exact undo/navigation gap")

# 4) Fix undo Case 2 to preserve removed gap without adding user's hesitation time
old_undo = """            unsavedGestures = remainingGestures

            glassHiddenAt = if (unsavedGestures.isEmpty()) {
                0L
            } else {
                (android.os.SystemClock.uptimeMillis() - preservedGap).coerceAtLeast(0L)
            }"""
new_undo = """            unsavedGestures = remainingGestures

            nextNavigationGapOverride = if (unsavedGestures.isEmpty()) null else preservedGap
            glassHiddenAt = if (unsavedGestures.isEmpty()) 0L else android.os.SystemClock.uptimeMillis()"""
s = replace_once(s, old_undo, new_undo, "FloatingControlService: undo preserved gap exact")

# 5) Reset override in important reset points
reset_patterns = [
    ("unsavedGestures = emptyList()\n                    glassHiddenAt = 0L\n                    pendingDiscardConfirm = false",
     "unsavedGestures = emptyList()\n                    glassHiddenAt = 0L\n                    nextNavigationGapOverride = null\n                    pendingDiscardConfirm = false",
     "reset override on config switch"),
    ("unsavedGestures = emptyList()\n                glassHiddenAt = 0L\n                pendingDiscardConfirm = false",
     "unsavedGestures = emptyList()\n                glassHiddenAt = 0L\n                nextNavigationGapOverride = null\n                pendingDiscardConfirm = false",
     "reset override on config create"),
    ("pendingDiscardConfirm = false\n        glassHiddenAt = 0L\n        unsavedGestures = emptyList()",
     "pendingDiscardConfirm = false\n        glassHiddenAt = 0L\n        nextNavigationGapOverride = null\n        unsavedGestures = emptyList()",
     "reset override on cut"),
    ("pendingDiscardConfirm = false\n        glassHiddenAt = 0L\n        safeRemoveView(captureView)",
     "pendingDiscardConfirm = false\n        glassHiddenAt = 0L\n        nextNavigationGapOverride = null\n        safeRemoveView(captureView)",
     "reset override on destroy"),
    ("unsavedGestures = emptyList()\n        glassHiddenAt = 0L\n        startRecording()",
     "unsavedGestures = emptyList()\n        glassHiddenAt = 0L\n        nextNavigationGapOverride = null\n        startRecording()",
     "reset override on fresh recording"),
    ("glassHiddenAt = 0L\n        pendingDiscardConfirm = false\n\n        if (unsavedGestures.isEmpty())",
     "glassHiddenAt = 0L\n        nextNavigationGapOverride = null\n        pendingDiscardConfirm = false\n\n        if (unsavedGestures.isEmpty())",
     "reset override on save"),
    ("GestureStore.clear(this)\n        unsavedGestures = emptyList()\n        glassHiddenAt = 0L\n        pendingDiscardConfirm = false",
     "GestureStore.clear(this)\n        unsavedGestures = emptyList()\n        glassHiddenAt = 0L\n        nextNavigationGapOverride = null\n        pendingDiscardConfirm = false",
     "reset override on clear")
]
for old, new, label in reset_patterns:
    if old in s and new not in s:
        s = replace_once(s, old, new, f"FloatingControlService: {label}")

# 6) Add delete current config option in manager dialog
if "Delete Active Config" not in s:
    old_dialog = """        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Macro Configs")
            .setItems(items) { _, which ->
                if (which == savedConfigs.size) {
                    showCreateConfigDialog()
                } else {
                    val selected = savedConfigs[which]
                    GestureStore.setActiveConfigName(this, selected)
                    refreshConfigLabel()
                    if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)

                    unsavedGestures = emptyList()
                    glassHiddenAt = 0L
                    nextNavigationGapOverride = null
                    pendingDiscardConfirm = false

                    val hasOld = GestureStore.hasRecording(this)
                    updateUIState(if (hasOld) "PLAY" else "START", false, hasOld, true)
                    restorePanelUI()

                    Toast.makeText(this, "Switched: $selected", Toast.LENGTH_SHORT).show()
                }
            }
            .create()"""
    new_dialog = """        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Macro Configs")
            .setItems(items) { _, which ->
                if (which == savedConfigs.size) {
                    showCreateConfigDialog()
                } else {
                    val selected = savedConfigs[which]
                    GestureStore.setActiveConfigName(this, selected)
                    refreshConfigLabel()
                    if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)

                    unsavedGestures = emptyList()
                    glassHiddenAt = 0L
                    nextNavigationGapOverride = null
                    pendingDiscardConfirm = false

                    val hasOld = GestureStore.hasRecording(this)
                    updateUIState(if (hasOld) "PLAY" else "START", false, hasOld, true)
                    restorePanelUI()

                    Toast.makeText(this, "Switched: $selected", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Delete Active Config") { _, _ ->
                val current = GestureStore.getActiveConfigName(this)
                val deleted = GestureStore.deleteConfig(this, current)
                unsavedGestures = emptyList()
                glassHiddenAt = 0L
                nextNavigationGapOverride = null
                pendingDiscardConfirm = false
                refreshConfigLabel()
                if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
                val hasOld = GestureStore.hasRecording(this)
                updateUIState(if (hasOld) "PLAY" else "START", false, hasOld, true)
                restorePanelUI()
                Toast.makeText(
                    this,
                    if (deleted) "Deleted: $current" else "Default Config delete nahi ho sakta",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .create()"""
    s = replace_once(s, old_dialog, new_dialog, "FloatingControlService: delete active config button")

# 7) Remove dead stopRecording function safely
s = replace_regex(
    s,
    r"\n\s*private fun stopRecording\(\) \{.*?\n\s*\}\n\s*(?=private fun clearSavedRecordingFromPanel\(\))",
    "\n",
    "FloatingControlService: remove dead stopRecording()"
)

FCS.write_text(s, encoding="utf-8")

# ================
# GestureStore.kt
# ================
g = GS.read_text(encoding="utf-8")

# Add deleteConfig function before clear()
if "fun deleteConfig(" not in g:
    delete_func = r'''
    fun deleteConfig(context: Context, name: String): Boolean {
        val safeName = normalizeConfigName(name)
        if (safeName == DEFAULT_CONFIG) return false

        val updatedNames = getAllConfigNames(context)
            .map { normalizeConfigName(it) }
            .filter { it != safeName }
            .distinct()
            .ifEmpty { listOf(DEFAULT_CONFIG) }

        val editor = prefs(context).edit()
            .remove(configKey(safeName))
            .remove(KEY_LOOP_MODE + "_" + safeName)
            .remove(KEY_LOOP_VALUE + "_" + safeName)
            .putString(KEY_ACTIVE_CONFIG, DEFAULT_CONFIG)
            .putString(KEY_CONFIG_LIST, JSONArray(updatedNames).toString())

        return editor.commit()
    }

'''
    g = replace_once(g, "    fun clear(context: Context) {\n", delete_func + "    fun clear(context: Context) {\n", "GestureStore: add deleteConfig()")

GS.write_text(g, encoding="utf-8")

print("\\n✅ FINAL PATCH DONE")
print("🔎 Checking active .kt files for old typo...")
