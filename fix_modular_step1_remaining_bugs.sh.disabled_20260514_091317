#!/usr/bin/env bash
set -euo pipefail

FCS="app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt"
GS="app/src/main/java/com/aarishkhan/aarishai/GestureStore.kt"

[ -f "$FCS" ] || { echo "❌ File nahi mili: $FCS"; exit 1; }
[ -f "$GS" ] || { echo "❌ File nahi mili: $GS"; exit 1; }

cp "$FCS" "$FCS.bak_remaining_modular_$(date +%Y%m%d_%H%M%S)"
cp "$GS" "$GS.bak_remaining_modular_$(date +%Y%m%d_%H%M%S)"

python3 << 'PY'
from pathlib import Path
import re
import sys

FCS = Path("app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt")
GS = Path("app/src/main/java/com/aarishkhan/aarishai/GestureStore.kt")

fcs = FCS.read_text(encoding="utf-8")
gs = GS.read_text(encoding="utf-8")

# ==========================================================
# FIX 1: Label long config name panel ko screen se bahar na kare
# ==========================================================
old_label_part = '''            setPadding(12, 0, 18, 0)
            isClickable = true
            setOnClickListener { showConfigManagerDialog() }'''

new_label_part = '''            setPadding(12, 0, 18, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = (210 * resources.displayMetrics.density).toInt()
            isClickable = true
            setOnClickListener { showConfigManagerDialog() }'''

if old_label_part in fcs and "ellipsize = android.text.TextUtils.TruncateAt.END" not in fcs:
    fcs = fcs.replace(old_label_part, new_label_part, 1)
    print("✅ Label width/ellipsis fix applied")
else:
    print("ℹ️ Label width fix already applied or anchor changed")

# ==========================================================
# FIX 2: Overlay dialog show ko safe try/catch mein rakho
# ==========================================================
if "private fun showOverlayDialogSafely(" not in fcs:
    insert_after = '''    private fun prepareOverlayDialog(dialog: android.app.AlertDialog) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
    }
'''
    safe_func = insert_after + '''
    private fun showOverlayDialogSafely(dialog: android.app.AlertDialog) {
        try {
            prepareOverlayDialog(dialog)
            dialog.show()
        } catch (e: Exception) {
            Toast.makeText(this, "Config popup open nahi hua: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
'''
    if insert_after in fcs:
        fcs = fcs.replace(insert_after, safe_func, 1)
        print("✅ Safe overlay dialog function added")
    else:
        print("❌ prepareOverlayDialog block nahi mila")
        sys.exit(1)

fcs = fcs.replace(
    '''        prepareOverlayDialog(dialog)
        dialog.show()''',
    '''        showOverlayDialogSafely(dialog)'''
)

# ==========================================================
# FIX 3: One-step saved macro par UNDO accidental permanent clear na kare
# ==========================================================
old_one_step = '''            if (edited.isEmpty()) {
                GestureStore.clear(this)
                unsavedGestures = emptyList()
                glassHiddenAt = 0L
                updateUIState("START", false, false, true)
                restorePanelUI()
                Toast.makeText(this, "↩️ Last step delete hua. Ab memory empty hai.", Toast.LENGTH_SHORT).show()
                return
            }'''

new_one_step = '''            if (edited.isEmpty()) {
                Toast.makeText(
                    this,
                    "Sirf 1 step hai. Use permanently clear karna ho to PLAY/START par long-press karo.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }'''

if old_one_step in fcs:
    fcs = fcs.replace(old_one_step, new_one_step, 1)
    print("✅ One-step saved UNDO safety fixed")
else:
    print("ℹ️ One-step UNDO block already fixed or anchor changed")

FCS.write_text(fcs, encoding="utf-8")

# ==========================================================
# FIX 4: Loop mode/value ko per-config banao
# ==========================================================
if "private fun loopModeKey(context: Context)" not in gs:
    anchor = '''    fun saveLoopSettings(context: Context, mode: String, value: Int) {'''
    insert = '''    private fun loopModeKey(context: Context): String {
        return KEY_LOOP_MODE + "_" + normalizeConfigName(getActiveConfigName(context))
    }

    private fun loopValueKey(context: Context): String {
        return KEY_LOOP_VALUE + "_" + normalizeConfigName(getActiveConfigName(context))
    }

'''
    if anchor not in gs:
        print("❌ saveLoopSettings anchor nahi mila")
        sys.exit(1)
    gs = gs.replace(anchor, insert + anchor, 1)
    print("✅ Per-config loop key functions added")

# clear() ke loop reset ko current config specific banao
gs = gs.replace(
    '''            .putString(KEY_LOOP_MODE, "ONCE")
            .putInt(KEY_LOOP_VALUE, 1)''',
    '''            .putString(loopModeKey(context), "ONCE")
            .putInt(loopValueKey(context), 1)'''
)

# saveLoopSettings mein current config key use karo
gs = gs.replace(
    '''            .putString(KEY_LOOP_MODE, safeMode)
            .putInt(KEY_LOOP_VALUE, safeValue)''',
    '''            .putString(loopModeKey(context), safeMode)
            .putInt(loopValueKey(context), safeValue)'''
)

# getLoopMode function replace
gs = re.sub(
    r'''    fun getLoopMode\(context: Context\): String \{
        val mode = prefs\(context\)\.getString\(KEY_LOOP_MODE, "ONCE"\) \?: "ONCE"
        return when \(mode\) \{
            "COUNT", "INFINITE", "TIME" -> mode
            else -> "ONCE"
        \}
    \}''',
    '''    fun getLoopMode(context: Context): String {
        val p = prefs(context)
        val mode = p.getString(
            loopModeKey(context),
            p.getString(KEY_LOOP_MODE, "ONCE")
        ) ?: "ONCE"

        return when (mode) {
            "COUNT", "INFINITE", "TIME" -> mode
            else -> "ONCE"
        }
    }''',
    gs,
    count=1
)

# getLoopValue function replace
gs = re.sub(
    r'''    fun getLoopValue\(context: Context\): Int \{
        val p = prefs\(context\)
        return when \(getLoopMode\(context\)\) \{
            "COUNT" -> p\.getInt\(KEY_LOOP_VALUE, 10\)\.coerceIn\(1, 999\)
            "TIME" -> p\.getInt\(KEY_LOOP_VALUE, 5\)\.coerceIn\(1, 240\)
            "INFINITE" -> 0
            else -> 1
        \}
    \}''',
    '''    fun getLoopValue(context: Context): Int {
        val p = prefs(context)
        return when (getLoopMode(context)) {
            "COUNT" -> p.getInt(loopValueKey(context), p.getInt(KEY_LOOP_VALUE, 10)).coerceIn(1, 999)
            "TIME" -> p.getInt(loopValueKey(context), p.getInt(KEY_LOOP_VALUE, 5)).coerceIn(1, 240)
            "INFINITE" -> 0
            else -> 1
        }
    }''',
    gs,
    count=1
)

GS.write_text(gs, encoding="utf-8")

print("✅ Remaining Modular Step-1 bugs fixed")
PY

echo "🔎 Verify..."
grep -q "showOverlayDialogSafely" "$FCS" || { echo "❌ Safe dialog fix missing"; exit 1; }
grep -q "Sirf 1 step hai" "$FCS" || { echo "❌ One-step undo safety missing"; exit 1; }
grep -q "loopModeKey" "$GS" || { echo "❌ Per-config loop fix missing"; exit 1; }

./gradlew assembleDebug
