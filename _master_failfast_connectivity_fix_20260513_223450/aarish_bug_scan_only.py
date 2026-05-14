from pathlib import Path
import re

ROOT = Path(".").resolve()

FILES = {
    "MainActivity.kt": ROOT / "app/src/main/java/com/aarishkhan/aarishai/MainActivity.kt",
    "FloatingControlService.kt": ROOT / "app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt",
    "AutoActionService.kt": ROOT / "app/src/main/java/com/aarishkhan/aarishai/AutoActionService.kt",
    "GestureStore.kt": ROOT / "app/src/main/java/com/aarishkhan/aarishai/GestureStore.kt",
    "AndroidManifest.xml": ROOT / "app/src/main/AndroidManifest.xml",
    "accessibility_service_config.xml": ROOT / "app/src/main/res/xml/accessibility_service_config.xml",
}

bugs = []

def read(path):
    if not path.exists():
        bugs.append(("CRITICAL", str(path), "File missing"))
        return ""
    return path.read_text(encoding="utf-8", errors="replace")

def bug(level, file, msg):
    bugs.append((level, file, msg))

MAIN = read(FILES["MainActivity.kt"])
FCS = read(FILES["FloatingControlService.kt"])
AUTO = read(FILES["AutoActionService.kt"])
GS = read(FILES["GestureStore.kt"])
MANIFEST = read(FILES["AndroidManifest.xml"])
ACC = read(FILES["accessibility_service_config.xml"])

print("🔎 AARISHAI BUG SCAN ONLY")
print("📁 Root:", ROOT)
print("=" * 70)

# MainActivity bugs
if MAIN:
    if "autoPermissionPromptDone" not in MAIN:
        bug("HIGH", "MainActivity.kt", "Permission resume-loop guard missing: settings baar-baar open ho sakti hai.")
    if "override fun onSaveInstanceState" not in MAIN:
        bug("HIGH", "MainActivity.kt", "Permission state save/restore missing: rotate/recreate par state lose ho sakti hai.")
    if "canLaunchPermissionScreenSafely" not in MAIN:
        bug("MEDIUM", "MainActivity.kt", "Permission screen throttle missing: fast resume/click par repeated launch ho sakta hai.")
    if "ACTION_MANAGE_OVERLAY_PERMISSION" in MAIN and "Uri.parse(\"package:$packageName\")" not in MAIN:
        bug("MEDIUM", "MainActivity.kt", "Overlay permission intent direct app page par nahi ja raha.")
    if "POST_NOTIFICATIONS" in MANIFEST and "POST_NOTIFICATIONS" not in MAIN:
        bug("MEDIUM", "MainActivity.kt", "Android 13+ notification runtime permission flow missing.")

# FloatingControlService bugs
if FCS:
    if "hidePanelUIForPlayback" in FCS and "panelHiddenForPlayback" not in FCS:
        bug("HIGH", "FloatingControlService.kt", "Replay panel hide duplicate guard missing: oldPanelX/oldPanelY overwrite ho sakta hai.")
    if re.search(r"btnClear\?\.visibility\s*=\s*if\s*\(\s*showOthers\s*\)", FCS):
        bug("MEDIUM", "FloatingControlService.kt", "CLR button wrong visibility: saved recording na hone par bhi show ho sakta hai.")
    if "extractAndAppendGestures()" in FCS and "val liveView = captureView" not in FCS:
        bug("HIGH", "FloatingControlService.kt", "saveRecording captureView null guard missing: crash/data-loss ka risk.")
    if "btnSettings?.visibility" in FCS or "btnSpeed?.visibility" in FCS:
        bug("LOW", "FloatingControlService.kt", "Dead settings/speed UI references found.")

# AutoActionService bugs
if AUTO:
    old_bad = [
        "safeNodeChildCount(",
        "safeNodeChild(",
        "safeNodeVisible(",
        "safeNodeEnabled(",
        "safeNodeClickable(",
        "safeNodeBounds(",
        "safeNodeParent(",
        "safeNodeText(",
        "safeNodeDesc(",
        "safeNodeId(",
        "safeNodeClass(",
    ]
    found_old = [x for x in old_bad if x in AUTO]
    if found_old:
        bug("CRITICAL", "AutoActionService.kt", "Wrong safeNode* helper names found: " + ", ".join(found_old))

    helpers = [
        "private fun safeChildCount",
        "private fun safeChild",
        "private fun safeParent",
        "private fun safeBounds",
        "private fun safeText",
        "private fun safeDesc",
        "private fun safeId",
        "private fun safeClass",
    ]
    missing = [x for x in helpers if x not in AUTO]
    if missing:
        bug("HIGH", "AutoActionService.kt", "Stale AccessibilityNodeInfo safe helpers missing: " + ", ".join(missing))

    if "dispatchHybridGesturePath" in AUTO:
        m = re.search(r"private fun dispatchHybridGesturePath[\s\S]*?(?=\n    private fun|\n    override fun|\n})", AUTO)
        block = m.group(0) if m else ""
        if "isDoubleTap" in block and "postDelayed" not in block:
            bug("HIGH", "AutoActionService.kt", "Double-tap real sequential dispatch missing.")
        if "AtomicBoolean" not in block:
            bug("MEDIUM", "AutoActionService.kt", "Gesture callback finishOnce/AtomicBoolean guard missing.")

    if "findExactActionButtonAcrossWindows" in AUTO and "TYPE_INPUT_METHOD" not in AUTO:
        bug("MEDIUM", "AutoActionService.kt", "Keyboard/inputmethod window filtering missing.")

    if "isSamePlaybackRun(runId)" not in AUTO:
        bug("HIGH", "AutoActionService.kt", "STOP ke baad old delayed gesture fire hone ka risk: runId guard missing.")

    direct_reads = []
    for pat in [".childCount", ".getChild(", ".parent", ".getBoundsInScreen(", ".packageName?"]:
        if pat in AUTO:
            direct_reads.append(pat)
    if direct_reads and "private fun safeBounds" in AUTO:
        bug("MEDIUM", "AutoActionService.kt", "Direct AccessibilityNodeInfo reads still found: " + ", ".join(direct_reads))

# GestureStore bugs
if GS:
    if "p.getString(KEY_LOOP_MODE" in GS or "p.getInt(KEY_LOOP_VALUE" in GS:
        bug("MEDIUM", "GestureStore.kt", "Global loop fallback found: config loop setting dusre config me leak ho sakti hai.")
    if "COUNT" in GS and "coerceIn(1, 999)" not in GS:
        bug("LOW", "GestureStore.kt", "Loop count clamp missing.")

# Manifest/config bugs
if MANIFEST:
    if "android.permission.SYSTEM_ALERT_WINDOW" not in MANIFEST:
        bug("CRITICAL", "AndroidManifest.xml", "Overlay permission missing.")
    if "android.permission.FOREGROUND_SERVICE" not in MANIFEST:
        bug("HIGH", "AndroidManifest.xml", "Foreground service permission missing.")
    if "android.accessibilityservice.AccessibilityService" not in MANIFEST:
        bug("CRITICAL", "AndroidManifest.xml", "Accessibility service intent-filter missing.")
    if "accessibility_service_config" not in MANIFEST:
        bug("MEDIUM", "AndroidManifest.xml", "Accessibility config resource reference verify karo.")

if ACC:
    if "flagRetrieveInteractiveWindows" not in ACC:
        bug("HIGH", "accessibility_service_config.xml", "flagRetrieveInteractiveWindows missing: smart cross-window target weak.")
    if "flagReportViewIds" not in ACC:
        bug("MEDIUM", "accessibility_service_config.xml", "flagReportViewIds missing: viewId smart click weak.")
    if "typeAllMask" in ACC:
        bug("LOW", "accessibility_service_config.xml", "typeAllMask too broad: battery/lag badh sakta hai.")

rank = {"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3}
bugs.sort(key=lambda x: rank.get(x[0], 9))

if not bugs:
    print("✅ Static scan me obvious bug pattern nahi mila.")
else:
    for i, (level, file, msg) in enumerate(bugs, 1):
        print(f"{i}. [{level}] {file}")
        print(f"   {msg}")
        print()

counts = {}
for level, _, _ in bugs:
    counts[level] = counts.get(level, 0) + 1

print("=" * 70)
print("SUMMARY:",
      f"CRITICAL={counts.get('CRITICAL',0)}",
      f"HIGH={counts.get('HIGH',0)}",
      f"MEDIUM={counts.get('MEDIUM',0)}",
      f"LOW={counts.get('LOW',0)}")
print("🛑 Build run nahi kiya. Patch apply nahi kiya. Sirf bug scan.")
print("=" * 70)
