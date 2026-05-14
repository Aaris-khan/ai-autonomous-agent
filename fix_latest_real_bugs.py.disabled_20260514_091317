from pathlib import Path
import re, time, shutil, sys

FCS = Path("app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt")
AS = Path("app/src/main/java/com/aarishkhan/aarishai/AutoActionService.kt")

for p in [FCS, AS]:
    if not p.exists():
        sys.exit(f"❌ File missing: {p}")

stamp = time.strftime("%Y%m%d_%H%M%S")
for p in [FCS, AS]:
    shutil.copy2(p, p.with_suffix(p.suffix + f".bak_{stamp}"))

fcs = FCS.read_text(encoding="utf-8")
auto = AS.read_text(encoding="utf-8")

changes = []

# 1) Saved Undo ko permanent bhi karo, taaki app close hone par old data wapas na aaye
old_undo = '''            unsavedGestures = edited
            glassHiddenAt = android.os.SystemClock.uptimeMillis()

            updateUIState("+ ADD", true, false, true)
            restorePanelUI()
            Toast.makeText(this, "↩️ Last step buffer se delete hua. + ADD karo ya SAVE se commit karo.", Toast.LENGTH_LONG).show()
            return'''

new_undo = '''            val ok = GestureStore.save(this, edited)
            if (!ok) {
                Toast.makeText(this, "❌ Undo save nahi ho paya", Toast.LENGTH_LONG).show()
                return
            }

            unsavedGestures = edited
            glassHiddenAt = android.os.SystemClock.uptimeMillis()

            updateUIState("+ ADD", true, false, true)
            restorePanelUI()
            Toast.makeText(this, "↩️ Saved memory ka last step delete ho gaya. + ADD karo ya SAVE dabao.", Toast.LENGTH_LONG).show()
            return'''

if old_undo in fcs:
    fcs = fcs.replace(old_undo, new_undo)
    changes.append("✅ Undo Case 3 permanent-save fix")
elif "Saved memory ka last step delete ho gaya. + ADD karo ya SAVE dabao." in fcs:
    changes.append("ℹ️ Undo permanent-save fix already applied")
else:
    print("⚠️ Undo block exact match nahi mila; manually check undoLastStep() Case 3")

# 2) Config switch/create ke baad loop text refresh
old_switch = '''refreshConfigLabel()

                    unsavedGestures = emptyList()'''
new_switch = '''refreshConfigLabel()
                    if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)

                    unsavedGestures = emptyList()'''

if old_switch in fcs and new_switch not in fcs:
    fcs = fcs.replace(old_switch, new_switch)
    changes.append("✅ Config switch loop-text refresh fix")

old_create = '''refreshConfigLabel()

                unsavedGestures = emptyList()'''
new_create = '''refreshConfigLabel()
                if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)

                unsavedGestures = emptyList()'''

if old_create in fcs and new_create not in fcs:
    fcs = fcs.replace(old_create, new_create)
    changes.append("✅ Config create loop-text refresh fix")

# 3) bringPanelToFront retry race guard
old_race = '''        if (!firstAdded) {
            handler.postDelayed({
                val retryAdded = safeAddView(panel, params, "Panel retry error")
                if (!retryAdded) {
                    panelView = null
                    Toast.makeText(
                        this,
                        "Panel wapas nahi aa paya, service band kar rahe hain",
                        Toast.LENGTH_LONG
                    ).show()
                    stopSelf()
                }
            }, 250L)
        }'''

new_race = '''        if (!firstAdded) {
            handler.postDelayed({
                if (instance == this@FloatingControlService && panelView === panel) {
                    val retryAdded = safeAddView(panel, params, "Panel retry error")
                    if (!retryAdded) {
                        panelView = null
                        Toast.makeText(
                            this,
                            "Panel wapas nahi aa paya, service band kar rahe hain",
                            Toast.LENGTH_LONG
                        ).show()
                        stopSelf()
                    }
                }
            }, 250L)
        }'''

if old_race in fcs:
    fcs = fcs.replace(old_race, new_race)
    changes.append("✅ bringPanelToFront retry race fix")
elif "panelView === panel" in fcs:
    changes.append("ℹ️ bringPanelToFront race fix already applied")
else:
    print("⚠️ bringPanelToFront retry block exact match nahi mila")

# 4) extractTreePathDNA circular parent guard
old_parent = '''            val parent = current.parent

            var index = -1'''

new_parent = '''            val parent = current.parent
            if (parent === current) break

            var index = -1'''

if old_parent in auto and "if (parent === current) break" not in auto:
    auto = auto.replace(old_parent, new_parent, 1)
    changes.append("✅ extractTreePathDNA circular-parent guard")
elif "if (parent === current) break" in auto:
    changes.append("ℹ️ circular-parent guard already applied")
else:
    print("⚠️ extractTreePathDNA parent block exact match nahi mila")

FCS.write_text(fcs, encoding="utf-8")
AS.write_text(auto, encoding="utf-8")

print("\\n".join(changes) if changes else "ℹ️ Koi new change apply nahi hua")
print("✅ Patch complete. Backup files created with:", stamp)
