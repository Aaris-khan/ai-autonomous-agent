#!/usr/bin/env bash
set -euo pipefail

FILE="app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt"

if [ ! -f "$FILE" ]; then
  echo "❌ File nahi mili: $FILE"
  exit 1
fi

cp "$FILE" "$FILE.bak_saved_undo_buffer_$(date +%Y%m%d_%H%M%S)"

python3 << 'PY'
from pathlib import Path
import re
import sys

p = Path("app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt")
s = p.read_text(encoding="utf-8")

pattern = re.compile(
    r'''
            val ok = GestureStore\.save\(this, edited\)
            if \(!ok\) \{
                Toast\.makeText\(this, "❌ Undo save nahi ho paya", Toast\.LENGTH_LONG\)\.show\(\)
                return
            \}

            unsavedGestures = edited
            glassHiddenAt = android\.os\.SystemClock\.uptimeMillis\(\)
''',
    re.VERBOSE
)

replacement = '''
            // ✅ SAFE BUFFER MODE:
            // Saved memory ko abhi overwrite mat karo.
            // User + ADD karke ya direct SAVE dabakar final commit karega.
            unsavedGestures = edited
            glassHiddenAt = android.os.SystemClock.uptimeMillis()
'''

s2, n = pattern.subn(replacement, s, count=1)

if n == 0:
    if "GestureStore.save(this, edited)" in s:
        print("❌ GestureStore.save(this, edited) mila, lekin replace nahi ho paya")
        sys.exit(1)
    else:
        print("ℹ️ Saved UNDO buffer mode already fixed")
        s2 = s
else:
    print("✅ Saved UNDO ab safe buffer mode mein aa gaya")

s2 = s2.replace(
    'Toast.makeText(this, "↩️ Saved memory ka last step delete ho gaya. Ab + ADD se sahi step jodo.", Toast.LENGTH_LONG).show()',
    'Toast.makeText(this, "↩️ Last step buffer se delete hua. + ADD karo ya SAVE se commit karo.", Toast.LENGTH_LONG).show()'
)

p.write_text(s2, encoding="utf-8")
PY

echo "🔎 Verify: undoLastStep mein direct saved overwrite nahi hona chahiye..."
if grep -n "GestureStore.save(this, edited)" "$FILE"; then
  echo "❌ Abhi bhi saved UNDO direct storage overwrite kar raha hai"
  exit 1
else
  echo "✅ Saved UNDO direct overwrite remove ho gaya"
fi

./gradlew assembleDebug
