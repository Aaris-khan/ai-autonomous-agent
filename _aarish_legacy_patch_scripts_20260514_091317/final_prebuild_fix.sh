#!/usr/bin/env bash
set -e

echo "🚀 FINAL PRE-BUILD FIX START..."

mkdir -p _old_patch_backups

echo "📦 Old .bak files side me move kar raha hoon..."
find app/src/main -type f \( -name "*.bak_*" -o -name "*.kt.bak_*" -o -name "*.xml.bak_*" \) -print0 2>/dev/null | while IFS= read -r -d '' f; do
  safe_name="$(echo "$f" | sed 's#[/ ]#_#g')"
  mv "$f" "_old_patch_backups/$safe_name"
done || true

echo "🧠 AutoActionService typo hard-fix..."
python3 - <<'PY'
from pathlib import Path

p = Path("app/src/main/java/com/aarishkhan/aarishai/AutoActionService.kt")
if p.exists():
    s = p.read_text()
    s = s.replace("safeNodeChildCount(", "safeChildCount(")
    s = s.replace("safeNodeChild(", "safeChild(")
    p.write_text(s)
PY

echo "🧾 Manifest accessibility meta-data fix..."
python3 - <<'PY'
from pathlib import Path
import re

p = Path("app/src/main/AndroidManifest.xml")
if not p.exists():
    raise SystemExit("❌ AndroidManifest.xml nahi mila")

s = p.read_text()

s = re.sub(
    r'android:resource="@xml/acc[^"]*"',
    'android:resource="@xml/accessibility_service_config"',
    s
)

if '@xml/accessibility_service_config' not in s:
    s = s.replace(
        '<meta-data\n                android:name="android.accessibilityservice"\n                android:resource="@xml/acc',
        '<meta-data\n                android:name="android.accessibilityservice"\n                android:resource="@xml/accessibility_service_config"'
    )

p.write_text(s)
PY

echo "🔍 Critical typo scan..."
if grep -R "safeNodeChild" -n app/src/main/java --include="*.kt"; then
  echo "❌ safeNodeChild typo abhi bhi main Kotlin files me hai"
  exit 1
else
  echo "✅ safeNodeChild typo clean"
fi

echo "🔍 Manifest resource scan..."
grep -n 'android:resource="@xml/accessibility_service_config"' app/src/main/AndroidManifest.xml >/dev/null || {
  echo "❌ Manifest accessibility resource line galat hai"
  exit 1
}
echo "✅ Manifest resource clean"

echo "🏗️ Gradle build start..."
chmod +x ./gradlew
./gradlew assembleDebug

echo "✅ FINAL OK: Build pass ho gaya. Ab APK test karo."
