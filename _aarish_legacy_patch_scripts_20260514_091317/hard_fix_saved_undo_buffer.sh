#!/usr/bin/env bash
set -euo pipefail

FILE="app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt"

[ -f "$FILE" ] || { echo "❌ File nahi mili: $FILE"; exit 1; }

cp "$FILE" "$FILE.bak_hard_buffer_$(date +%Y%m%d_%H%M%S)"

python3 << 'PY'
from pathlib import Path
import re
import sys

file = Path("app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt")
s = file.read_text(encoding="utf-8")

old = s

# ✅ Remove direct permanent save from saved-undo case
s = re.sub(
    r'\n\s*val\s+ok\s*=\s*GestureStore\.save\s*\(\s*this\s*,\s*edited\s*\)\s*'
    r'\n\s*if\s*\(\s*!\s*ok\s*\)\s*\{.*?'
    r'\n\s*return\s*'
    r'\n\s*\}',
    '',
    s,
    count=1,
    flags=re.DOTALL
)

# ✅ Replace toast message
s = s.replace(
    'Toast.makeText(this, "↩️ Saved memory ka last step delete ho gaya. Ab + ADD se sahi step jodo.", Toast.LENGTH_LONG).show()',
    'Toast.makeText(this, "↩️ Last step buffer se delete hua. + ADD karo ya SAVE se commit karo.", Toast.LENGTH_LONG).show()'
)

if "GestureStore.save(this, edited)" in s:
    print("❌ Direct saved overwrite abhi bhi bacha hai")
    sys.exit(1)

if old == s:
    print("ℹ️ Koi change nahi hua. Shayad block already fixed hai.")
else:
    print("✅ Saved UNDO ab permanent memory ko direct overwrite nahi karega")

file.write_text(s, encoding="utf-8")
PY

echo "🔎 Final verify..."
grep -n "GestureStore.save(this, edited)" "$FILE" && {
  echo "❌ Abhi bhi bug bacha hai"
  exit 1
} || echo "✅ Direct overwrite bug remove ho gaya"

./gradlew assembleDebug
