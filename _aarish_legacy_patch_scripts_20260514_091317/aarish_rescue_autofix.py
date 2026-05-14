from pathlib import Path
import re, shutil, time, sys, os

ROOT = Path(".").resolve()
SRC = ROOT / "app/src/main/java/com/aarishkhan/aarishai"
AUTO = SRC / "AutoActionService.kt"
FCS = SRC / "FloatingControlService.kt"
MAIN = SRC / "MainActivity.kt"
GS = SRC / "GestureStore.kt"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
ACC = ROOT / "app/src/main/res/xml/accessibility_service_config.xml"

STAMP = time.strftime("%Y%m%d_%H%M%S")
BAK = ROOT / f"_rescue_autofix_backup_{STAMP}"
BAK.mkdir(exist_ok=True)

def need(p):
    if not p.exists():
        print(f"❌ Missing: {p}")
        sys.exit(1)

def read(p):
    return p.read_text(encoding="utf-8")

def write(p, s):
    p.write_text(s, encoding="utf-8")

def backup(p):
    if p.exists():
        dst = BAK / p.name
        shutil.copy2(p, dst)

for p in [AUTO, FCS, MAIN, GS]:
    need(p)
    backup(p)

for p in [MANIFEST, ACC]:
    if p.exists():
        backup(p)

print(f"📦 Backup: {BAK}")

# 1) remove broken placeholder script if present
placeholder = ROOT / "aarishai_supreme_offline_agent_fix.py"
if placeholder.exists() and "YAHAN poori Python script paste karo" in read(placeholder):
    placeholder.unlink()
    print("✅ Broken placeholder script removed")

# 2) AutoActionService compile rescue
a = read(AUTO)

a = a.replace("safeNodeChildCount(", "safeChildCount(")
a = a.replace("safeNodeChild(", "safeChild(")

if "private fun safePackage(node: AccessibilityNodeInfo?)" not in a:
    a = a.replace(
'''    private fun safeClass(node: AccessibilityNodeInfo?): String? =
        try { node?.className?.toString() } catch (_: Exception) { null }''',
'''    private fun safeClass(node: AccessibilityNodeInfo?): String? =
        try { node?.className?.toString() } catch (_: Exception) { null }

    private fun safePackage(node: AccessibilityNodeInfo?): String? =
        try { node?.packageName?.toString() } catch (_: Exception) { null }'''
    )

a = a.replace('val pkg = root.packageName?.toString() ?: ""', 'val pkg = safePackage(root).orEmpty()')
a = a.replace('val fallbackPkg = fallbackRoot.packageName?.toString() ?: ""', 'val fallbackPkg = safePackage(fallbackRoot).orEmpty()')

a = a.replace(
'''                    val bounds = Rect()
                    root.getBoundsInScreen(bounds)

                    if (bounds.width() <= 0 || bounds.height() <= 0) continue''',
'''                    val bounds = Rect()
                    if (!safeBounds(root, bounds)) continue

                    if (bounds.width() <= 0 || bounds.height() <= 0) continue'''
)

# 3) Share/Termux guard safety if missing
if "termux" not in a.lower() and "isSafeToDispatch" not in a:
    guard = '''
    // Rescue guard: avoid firing replay into Termux/wrong foreground shell
    private fun rescueForegroundPackage(): String? = try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            windows?.firstOrNull {
                it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused
            }?.root?.packageName?.toString()
        } else {
            rootInActiveWindow?.packageName?.toString()
        }
    } catch (_: Exception) { null }

    private fun rescueSafeToDispatch(): Boolean {
        val pkg = rescueForegroundPackage() ?: return true
        if (pkg.contains("termux", ignoreCase = true)) return false
        return true
    }

'''
    a = a.replace("override fun onInterrupt()", guard + "\n    override fun onInterrupt()", 1)

write(AUTO, a)
print("✅ AutoActionService rescue patched")

# 4) FloatingControlService rescue
f = read(FCS)

# remove forbidden/dead UI lines softly
for pat in [
    r'(?im)^.*anti[\s_-]*deduction.*\n?',
    r'(?im)^.*load\s*configuration.*\n?',
    r'(?im)^.*btnSettings\?.*\n?',
    r'(?im)^.*btnSpeed\?.*\n?',
    r'(?im)^.*btnAntiDeduction.*\n?',
    r'(?im)^.*btnLoadConfiguration.*\n?',
    r'(?im)^.*btnConfigure.*\n?',
    r'(?im)^.*btnPencil.*\n?',
    r'(?im)^.*ivPencil.*\n?',
]:
    f = re.sub(pat, "", f)

# fix invisible overlay stealing buttons
f = re.sub(
    r"(WindowManager\.LayoutParams\.FLAG_NOT_FOCUSABLE\b)(?!\s*\n?\s*or\s+WindowManager\.LayoutParams\.FLAG_NOT_TOUCH_MODAL)",
    r"\1\n                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL\n                    or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH",
    f,
    count=1
)

# clear button only when saved and no unsaved buffer
f = f.replace(
    "btnClear?.visibility = if (showOthers) View.VISIBLE else View.GONE",
    "btnClear?.visibility = if (showOthers && GestureStore.hasRecording(this) && unsavedGestures.isEmpty()) View.VISIBLE else View.GONE"
)

# save null captureView guard
f = f.replace(
'''        if (isRecording) {
            extractAndAppendGestures()
            isRecording = false
            safeRemoveView(captureView)
            captureView = null
        }''',
'''        if (isRecording) {
            val liveView = captureView
            if (liveView != null) {
                extractAndAppendGestures()
                safeRemoveView(liveView)
            } else {
                Toast.makeText(this, "⚠️ Recording layer missing thi. Saved buffer hi save hoga.", Toast.LENGTH_LONG).show()
            }
            isRecording = false
            captureView = null
        }'''
)

write(FCS, f)
print("✅ FloatingControlService rescue patched")

# 5) MainActivity permission duplicate loop guard, only safe string patches
m = read(MAIN)

if "private var autoPermissionPromptDone = false" not in m:
    m = m.replace(
        "private var lastPermissionScreen: String? = null",
        "private var lastPermissionScreen: String? = null\n    private var autoPermissionPromptDone = false"
    )

m = m.replace(
'''        if (!hasOverlayPermission() || !isAccessibilityServiceEnabled()) {
            startScreenCommandSystem(forceOpenSettings = true)
        }''',
'''        if ((!hasOverlayPermission() || !isAccessibilityServiceEnabled()) && !autoPermissionPromptDone) {
            autoPermissionPromptDone = true
            startScreenCommandSystem(forceOpenSettings = true)
        }'''
)

m = m.replace(
'''        btnScreenCommand.setOnClickListener {
            lastPermissionScreen = null
            startScreenCommandSystem(forceOpenSettings = true)
        }''',
'''        btnScreenCommand.setOnClickListener {
            lastPermissionScreen = null
            autoPermissionPromptDone = false
            startScreenCommandSystem(forceOpenSettings = true)
        }'''
)

if "FloatingControlService.instance != null" not in m:
    m = m.replace(
'''        try {
            val serviceIntent = Intent(this, FloatingControlService::class.java)''',
'''        if (FloatingControlService.instance != null) {
            moveTaskToBack(true)
            return
        }

        try {
            val serviceIntent = Intent(this, FloatingControlService::class.java)'''
    )

write(MAIN, m)
print("✅ MainActivity rescue patched")

# 6) GestureStore loop fallback rescue
g = read(GS)
g = g.replace(
'''        val mode = p.getString(
            loopModeKey(context),
            p.getString(KEY_LOOP_MODE, "ONCE")
        ) ?: "ONCE"''',
'''        val mode = p.getString(
            loopModeKey(context),
            "ONCE"
        ) ?: "ONCE"'''
)
g = g.replace(
'"COUNT" -> p.getInt(loopValueKey(context), p.getInt(KEY_LOOP_VALUE, 10)).coerceIn(1, 999)',
'"COUNT" -> p.getInt(loopValueKey(context), 10).coerceIn(1, 999)'
)
g = g.replace(
'"TIME" -> p.getInt(loopValueKey(context), p.getInt(KEY_LOOP_VALUE, 5)).coerceIn(1, 240)',
'"TIME" -> p.getInt(loopValueKey(context), 5).coerceIn(1, 240)'
)
write(GS, g)
print("✅ GestureStore rescue patched")

# 7) Manifest + accessibility config rescue
if MANIFEST.exists():
    s = read(MANIFEST)
    s = re.sub(r'android:resource="@xml/acc[^"]*"', 'android:resource="@xml/accessibility_service_config"', s)
    write(MANIFEST, s)
    print("✅ Manifest accessibility resource fixed")

if ACC.exists():
    s = read(ACC)
    s = s.replace('android:accessibilityEventTypes="typeAllMask"', 'android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewClicked|typeViewScrolled"')
    s = s.replace('android:notificationTimeout="100"', 'android:notificationTimeout="200"')
    if "flagRetrieveInteractiveWindows" not in s:
        s = re.sub(
            r'android:accessibilityFlags="([^"]*)"',
            lambda mm: f'android:accessibilityFlags="{mm.group(1)}|flagRetrieveInteractiveWindows|flagReportViewIds"',
            s,
            count=1
        )
    write(ACC, s)
    print("✅ Accessibility config fixed")

# 8) old backup files side me
old_dir = ROOT / "_old_patch_backups"
old_dir.mkdir(exist_ok=True)
for p in (ROOT / "app/src/main").rglob("*"):
    if p.is_file() and (".bak_" in p.name or p.name.endswith(".bak")):
        try:
            shutil.move(str(p), str(old_dir / p.name.replace("/", "_")))
        except Exception:
            pass

# 9) final scan
bad = []
for p in [AUTO, FCS, MAIN, GS]:
    s = read(p)
    if p == AUTO and "safeNodeChild(" in s:
        bad.append("AutoActionService.kt me safeNodeChild typo abhi bhi hai")
    if p == AUTO and "safeNodeChildCount(" in s:
        bad.append("AutoActionService.kt me safeNodeChildCount typo abhi bhi hai")

if bad:
    print("❌ Risk found:")
    for x in bad:
        print(" -", x)
    sys.exit(1)

print("✅ Rescue autofix complete")
