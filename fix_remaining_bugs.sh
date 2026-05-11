#!/bin/bash
set -e

AUTO="app/src/main/java/com/aarishkhan/aarishai/AutoActionService.kt"
FLOAT="app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt"
TS=$(date +%Y%m%d_%H%M%S)

cp "$AUTO" "$AUTO.bak_$TS"
cp "$FLOAT" "$FLOAT.bak_$TS"

echo "🔧 Fix 1: safeNodeChild typo compile error..."
python3 - <<'PY'
from pathlib import Path

p = Path("app/src/main/java/com/aarishkhan/aarishai/AutoActionService.kt")
s = p.read_text()

s = s.replace("safeNodeChildCount(", "safeChildCount(")
s = s.replace("safeNodeChild(", "safeChild(")

# Dead code: getRealAppRoot() remove if present
s = s.replace('''
    private fun getRealAppRoot(): AccessibilityNodeInfo? {
        return getRealAppRootForPoint(null, null)
    }

''', "")

p.write_text(s)
print("✅ AutoActionService compile typo fixed")
PY

echo "🔧 Fix 2: playback watcher stale reference cleanup..."
python3 - <<'PY'
from pathlib import Path

p = Path("app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt")
s = p.read_text()

old = '''        if (AutoActionService.isPlaying()) {
            AutoActionService.stopPlayback(this)
            updateUIState("PLAY", false, true, true)
            restorePanelUI()
            Toast.makeText(this, "Playback stopped", Toast.LENGTH_SHORT).show()
            return
        }'''

new = '''        if (AutoActionService.isPlaying()) {
            AutoActionService.stopPlayback(this)
            playbackWatcherRunnable?.let { handler.removeCallbacks(it) }
            playbackWatcherRunnable = null
            updateUIState("PLAY", false, true, true)
            restorePanelUI()
            Toast.makeText(this, "Playback stopped", Toast.LENGTH_SHORT).show()
            return
        }'''

if old not in s:
    raise SystemExit("❌ STOP branch pattern nahi mila")
s = s.replace(old, new, 1)

p.write_text(s)
print("✅ FloatingControlService watcher cleanup fixed")
PY

echo "🔎 Verify undefined typo..."
if grep -R "safeNodeChild" -n app/src/main/java/com/aarishkhan/aarishai; then
  echo "❌ Abhi bhi safeNodeChild typo bacha hai"
  exit 1
else
  echo "✅ safeNodeChild typo gone"
fi

echo "🚀 Building..."
./gradlew assembleDebug
