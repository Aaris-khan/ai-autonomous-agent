#!/usr/bin/env bash
set -euo pipefail

FILE="app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt"

if [ ! -f "$FILE" ]; then
  echo "❌ File nahi mili: $FILE"
  exit 1
fi

cp "$FILE" "$FILE.bak_undo_delay_fix_$(date +%Y%m%d_%H%M%S)"

python3 << 'PY'
from pathlib import Path
import re
import sys

p = Path("app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt")
s = p.read_text(encoding="utf-8")
changes = []

# BUG 1: Saved UNDO ke baad delay double/compound ho raha tha.
pattern_saved_undo = re.compile(
    r'''(\s*unsavedGestures\s*=\s*edited\s*)\n\s*val lastEnd\s*=\s*edited\.maxOfOrNull\s*\{\s*g\s*->\s*
\s*g\.delayFromStart\.coerceAtLeast\(0L\)\s*\+\s*\(g\.points\.lastOrNull\(\)\?\.t\s*\?:\s*0L\)\.coerceAtLeast\(0L\)\s*
\s*\}\s*\?:\s*0L\s*
\s*glassHiddenAt\s*=\s*android\.os\.SystemClock\.uptimeMillis\(\)\s*-\s*lastEnd\.coerceAtLeast\(0L\)''',
    re.S
)

s2, n = pattern_saved_undo.subn(
    r'''\1
            glassHiddenAt = android.os.SystemClock.uptimeMillis()''',
    s,
    count=1
)

if n:
    s = s2
    changes.append("✅ Saved UNDO double-delay math fixed")
elif "glassHiddenAt = android.os.SystemClock.uptimeMillis() - lastEnd" in s:
    print("❌ Saved UNDO bad math mila, lekin regex replace nahi kar paya")
    sys.exit(1)
else:
    changes.append("ℹ️ Saved UNDO double-delay already fixed / bad pattern not found")

# BUG 2: Unsaved UNDO ke baad old glassHiddenAt stale reh sakta tha.
old_case2 = '''            if (unsavedGestures.isEmpty()) {
                glassHiddenAt = 0L
            }

            val hasOld = GestureStore.hasRecording(this)'''

new_case2 = '''            if (unsavedGestures.isEmpty()) {
                glassHiddenAt = 0L
            } else {
                glassHiddenAt = android.os.SystemClock.uptimeMillis()
            }

            val hasOld = GestureStore.hasRecording(this)'''

if old_case2 in s:
    s = s.replace(old_case2, new_case2, 1)
    changes.append("✅ Unsaved UNDO stale-gap timer fixed")
elif new_case2 in s:
    changes.append("ℹ️ Unsaved UNDO stale-gap already fixed")
else:
    print("❌ Case 2 unsaved undo timer block nahi mila")
    sys.exit(1)

# BUG 3: Fresh service start par saved recording hai to UNDO initially visible hona chahiye.
old_undo_button = '''        btnUndo = Button(this).apply {
            text = "UNDO"
            visibility = View.GONE
            setOnClickListener { undoLastStep() }
        }'''

new_undo_button = '''        btnUndo = Button(this).apply {
            text = "UNDO"
            visibility = if (GestureStore.hasRecording(this@FloatingControlService)) View.VISIBLE else View.GONE
            setOnClickListener { undoLastStep() }
        }'''

if old_undo_button in s:
    s = s.replace(old_undo_button, new_undo_button, 1)
    changes.append("✅ Initial UNDO visibility fixed")
elif new_undo_button in s:
    changes.append("ℹ️ Initial UNDO visibility already fixed")
else:
    print("❌ btnUndo creation block nahi mila")
    sys.exit(1)

# BUG 4: Long press clear ke baad UNDO stale visible reh sakta tha.
old_clear_ui = '''                    updateLoopButtonText(btnLoop)
                    btnLoop.visibility = View.GONE
                    btnSave.visibility = View.GONE
                    Toast.makeText('''

new_clear_ui = '''                    updateLoopButtonText(btnLoop)
                    btnLoop.visibility = View.GONE
                    btnSave.visibility = View.GONE
                    if (::btnUndo.isInitialized) btnUndo.visibility = View.GONE
                    Toast.makeText('''

if old_clear_ui in s:
    s = s.replace(old_clear_ui, new_clear_ui, 1)
    changes.append("✅ Clear ke baad stale UNDO hide fixed")
elif "if (::btnUndo.isInitialized) btnUndo.visibility = View.GONE" in s:
    changes.append("ℹ️ Clear stale UNDO already fixed")
else:
    print("❌ Long-click clear UI block nahi mila")
    sys.exit(1)

p.write_text(s, encoding="utf-8")

print("\\n".join(changes))
print("✅ FloatingControlService.kt patch complete")
PY

echo "🔎 Bad math check..."
if grep -R "uptimeMillis() - lastEnd" app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt; then
  echo "❌ Abhi bhi bad delay math bacha hai"
  exit 1
else
  echo "✅ Bad delay math remove ho gaya"
fi

./gradlew assembleDebug
