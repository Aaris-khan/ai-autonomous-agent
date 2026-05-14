from pathlib import Path
import shutil, time, re, sys

stamp = time.strftime("%Y%m%d_%H%M%S")
backup_dir = Path(f"safe_backups_prod_patch_{stamp}")
backup_dir.mkdir(exist_ok=True)

def backup(path: Path):
    if path.exists():
        shutil.copy2(path, backup_dir / path.name)

fcs = Path("app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt")
auto = Path("app/src/main/java/com/aarishkhan/aarishai/AutoActionService.kt")

if not fcs.exists() or not auto.exists():
    print("❌ Required Kotlin files nahi mile")
    sys.exit(1)

backup(fcs)
backup(auto)

s = fcs.read_text()

if "nextNavigationGapOverride" not in s:
    s = s.replace(
        "    private var glassHiddenAt = 0L\n",
        "    private var glassHiddenAt = 0L\n"
        "    private var nextNavigationGapOverride: Long? = null\n",
        1
    )

old_gap = """        val navigationGap = if (unsavedGestures.isNotEmpty() && glassHiddenAt > 0L) {
            (view.getRecordingStartTime() - glassHiddenAt).coerceAtLeast(0L).coerceAtMost(120000L)
        } else {
            0L
        }

        val timeOffset = if (unsavedGestures.isEmpty()) 0L else lastGestureEnd + navigationGap
"""

new_gap = """        val overrideGap = nextNavigationGapOverride
        val navigationGap = if (unsavedGestures.isNotEmpty()) {
            (overrideGap ?: if (glassHiddenAt > 0L) {
                view.getRecordingStartTime() - glassHiddenAt
            } else {
                0L
            }).coerceAtLeast(0L).coerceAtMost(120000L)
        } else {
            0L
        }
        nextNavigationGapOverride = null

        val timeOffset = if (unsavedGestures.isEmpty()) 0L else lastGestureEnd + navigationGap
"""

if old_gap in s:
    s = s.replace(old_gap, new_gap, 1)

old_undo = """            glassHiddenAt = if (unsavedGestures.isEmpty()) {
                0L
            } else {
                (android.os.SystemClock.uptimeMillis() - preservedGap).coerceAtLeast(0L)
            }
"""

new_undo = """            if (unsavedGestures.isEmpty()) {
                glassHiddenAt = 0L
                nextNavigationGapOverride = null
            } else {
                glassHiddenAt = android.os.SystemClock.uptimeMillis()
                nextNavigationGapOverride = preservedGap
            }
"""

if old_undo in s:
    s = s.replace(old_undo, new_undo, 1)

s = s.replace(
    "btnClear?.visibility = if (showOthers) View.VISIBLE else View.GONE",
    "btnClear?.visibility = if (showOthers && GestureStore.hasRecording(this) && unsavedGestures.isEmpty()) View.VISIBLE else View.GONE"
)

lines = s.splitlines()
out = []
for i, line in enumerate(lines):
    out.append(line)
    stripped = line.strip()
    next_line = lines[i + 1].strip() if i + 1 < len(lines) else ""
    indent = line[:len(line) - len(line.lstrip())]

    if stripped == "glassHiddenAt = 0L" and not next_line.startswith("nextNavigationGapOverride"):
        out.append(indent + "nextNavigationGapOverride = null")

    if stripped == "glassHiddenAt = android.os.SystemClock.uptimeMillis()" and not next_line.startswith("nextNavigationGapOverride"):
        out.append(indent + "nextNavigationGapOverride = null")

s = "\n".join(out) + "\n"
fcs.write_text(s)

a = auto.read_text()
a = a.replace("safeNodeChildCount(", "safeChildCount(")
a = a.replace("safeNodeChild(", "safeChild(")
auto.write_text(a)

print("✅ Final production hardening patch apply ho gaya")
print(f"✅ Backup saved in: {backup_dir}")
