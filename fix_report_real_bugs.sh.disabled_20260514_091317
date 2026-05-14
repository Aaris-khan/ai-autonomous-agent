#!/usr/bin/env bash
set -e

python3 - <<'PY'
from pathlib import Path
import re, time, shutil, sys

files = {
    "MAIN": Path("app/src/main/java/com/aarishkhan/aarishai/MainActivity.kt"),
    "FCS": Path("app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt"),
    "AUTO": Path("app/src/main/java/com/aarishkhan/aarishai/AutoActionService.kt"),
}

for name, p in files.items():
    if not p.exists():
        sys.exit(f"❌ Missing file: {p}")

stamp = time.strftime("%Y%m%d_%H%M%S")
for p in files.values():
    shutil.copy2(p, p.with_suffix(p.suffix + f".bak_{stamp}"))

main = files["MAIN"].read_text(encoding="utf-8")
fcs = files["FCS"].read_text(encoding="utf-8")
auto = files["AUTO"].read_text(encoding="utf-8")

changes = []

# -----------------------------
# MainActivity: settings re-open loop guard
# -----------------------------
if "private var lastPermissionScreen: String? = null" not in main:
    main = main.replace(
        "private var notificationPermissionAskedThisSession = false",
        "private var notificationPermissionAskedThisSession = false\n    private var lastPermissionScreen: String? = null"
    )
    changes.append("✅ MainActivity lastPermissionScreen state added")

old_click = '''btnScreenCommand.setOnClickListener {
            startScreenCommandSystem(forceOpenSettings = true)
        }'''
new_click = '''btnScreenCommand.setOnClickListener {
            lastPermissionScreen = null
            startScreenCommandSystem(forceOpenSettings = true)
        }'''
if old_click in main:
    main = main.replace(old_click, new_click)
    changes.append("✅ Button click resets permission state")

old_resume = '''override fun onResume() {
        super.onResume()

        if (isWaitingForPermission) {
            isWaitingForPermission = false
            startScreenCommandSystem(forceOpenSettings = true)
            return
        }

        if (!hasOverlayPermission() || !isAccessibilityServiceEnabled()) {
            startScreenCommandSystem(forceOpenSettings = true)
        }
    }'''
new_resume = '''override fun onResume() {
        super.onResume()

        if (isWaitingForPermission) {
            isWaitingForPermission = false
            val returnedFrom = lastPermissionScreen
            lastPermissionScreen = null

            if (hasOverlayPermission() && isAccessibilityServiceEnabled()) {
                startScreenCommandSystem(forceOpenSettings = false)
                return
            }

            if (returnedFrom == "overlay" && hasOverlayPermission() && !isAccessibilityServiceEnabled()) {
                startScreenCommandSystem(forceOpenSettings = true)
                return
            }

            Toast.makeText(
                this,
                "Permission enable karke SCREEN COMMAND dobara dabao",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!hasOverlayPermission() || !isAccessibilityServiceEnabled()) {
            startScreenCommandSystem(forceOpenSettings = true)
        }
    }'''
if old_resume in main:
    main = main.replace(old_resume, new_resume)
    changes.append("✅ onResume settings-loop guard fixed")
elif "val returnedFrom = lastPermissionScreen" in main:
    changes.append("ℹ️ onResume settings-loop guard already present")
else:
    print("⚠️ MainActivity onResume exact block nahi mila")

old_overlay = '''Toast.makeText(this, "Overlay permission ON karo", Toast.LENGTH_LONG).show()
                isWaitingForPermission = true
                startActivity('''
new_overlay = '''Toast.makeText(this, "Overlay permission ON karo", Toast.LENGTH_LONG).show()
                isWaitingForPermission = true
                lastPermissionScreen = "overlay"
                startActivity('''
if old_overlay in main:
    main = main.replace(old_overlay, new_overlay)
    changes.append("✅ Overlay permission screen tracked")

old_access = '''Toast.makeText(this, "Accessibility Service ON karo", Toast.LENGTH_LONG).show()
                isWaitingForPermission = true
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))'''
new_access = '''Toast.makeText(this, "Accessibility Service ON karo", Toast.LENGTH_LONG).show()
                isWaitingForPermission = true
                lastPermissionScreen = "accessibility"
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))'''
if old_access in main:
    main = main.replace(old_access, new_access)
    changes.append("✅ Accessibility permission screen tracked")

# -----------------------------
# FloatingControlService: config switch/create loop text refresh
# -----------------------------
fcs2 = fcs.replace(
    '''refreshConfigLabel()

                    unsavedGestures = emptyList()''',
    '''refreshConfigLabel()
                    if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)

                    unsavedGestures = emptyList()'''
)
fcs2 = fcs2.replace(
    '''refreshConfigLabel()

                unsavedGestures = emptyList()''',
    '''refreshConfigLabel()
                if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)

                unsavedGestures = emptyList()'''
)
if fcs2 != fcs:
    fcs = fcs2
    changes.append("✅ Config switch/create loop text refresh fixed")

# -----------------------------
# FloatingControlService: bringPanelToFront race guard
# -----------------------------
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
    changes.append("✅ bringPanelToFront zombie-view race fixed")
elif "panelView === panel" in fcs:
    changes.append("ℹ️ bringPanelToFront race guard already present")
else:
    print("⚠️ bringPanelToFront exact retry block nahi mila")

# -----------------------------
# FloatingControlService: Undo Case 3 should persist immediately
# -----------------------------
old_one_step = '''            if (edited.isEmpty()) {
                Toast.makeText(
                    this,
                    "Sirf 1 step hai. Use permanently clear karna ho to PLAY/START par long-press karo.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            unsavedGestures = edited
            glassHiddenAt = android.os.SystemClock.uptimeMillis()

            updateUIState("+ ADD", true, false, true)
            restorePanelUI()
            Toast.makeText(this, "↩️ Last step buffer se delete hua. + ADD karo ya SAVE se commit karo.", Toast.LENGTH_LONG).show()
            return'''
new_one_step = '''            if (edited.isEmpty()) {
                GestureStore.clear(this)
                unsavedGestures = emptyList()
                glassHiddenAt = 0L
                updateUIState("START", false, false, true)
                restorePanelUI()
                Toast.makeText(this, "↩️ Last step delete hua. Ab memory empty hai.", Toast.LENGTH_SHORT).show()
                return
            }

            val ok = GestureStore.save(this, edited)
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
if old_one_step in fcs:
    fcs = fcs.replace(old_one_step, new_one_step)
    changes.append("✅ Undo after SAVE/PLAY now persists + one-step undo clears memory")
elif "Saved memory ka last step delete ho gaya. + ADD karo ya SAVE dabao." in fcs:
    changes.append("ℹ️ Undo persistent-save fix already present")
else:
    print("⚠️ Undo Case 3 exact block nahi mila")

# -----------------------------
# AutoActionService: circular parent guard
# -----------------------------
old_parent = '''            val parent = current.parent

            var index = -1'''
new_parent = '''            val parent = current.parent
            if (parent === current) break

            var index = -1'''
if old_parent in auto and "if (parent === current) break" not in auto:
    auto = auto.replace(old_parent, new_parent, 1)
    changes.append("✅ extractTreePathDNA circular parent guard fixed")
elif "if (parent === current) break" in auto:
    changes.append("ℹ️ extractTreePathDNA circular guard already present")
else:
    print("⚠️ extractTreePathDNA parent block exact match nahi mila")

files["MAIN"].write_text(main, encoding="utf-8")
files["FCS"].write_text(fcs, encoding="utf-8")
files["AUTO"].write_text(auto, encoding="utf-8")

print("\\n".join(changes) if changes else "ℹ️ No new patch applied")
print(f"✅ Backups created with stamp: {stamp}")
PY

./gradlew assembleDebug
