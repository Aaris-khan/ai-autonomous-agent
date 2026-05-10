import os
import re
from pathlib import Path

BASE = "app/src/main/java/com/aarishkhan/aarishai"
AAS = f"{BASE}/AutoActionService.kt"
FCS = f"{BASE}/FloatingControlService.kt"
MANIFEST = "app/src/main/AndroidManifest.xml"
ICON = "app/src/main/res/drawable/ic_stat_aarish.xml"

print("🚀 FINAL IMPORTANT AUTOFIXER apply ho raha hai...")

# ==========================================================
# 0) Safe notification icon create
# ==========================================================
Path("app/src/main/res/drawable").mkdir(parents=True, exist_ok=True)

icon_xml = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,2A10,10 0,1 0,12 22A10,10 0,1 0,12 2M11,7H13V9H11V7M11,11H13V17H11V11Z" />
</vector>
"""
with open(ICON, "w", encoding="utf-8") as f:
    f.write(icon_xml)

print("✅ Safe notification icon created")


# ==========================================================
# 1) FloatingControlService.kt fixes
# ==========================================================
with open(FCS, "r", encoding="utf-8") as f:
    fcs = f.read()

# Fix notification icon
fcs = fcs.replace(
    ".setSmallIcon(android.R.drawable.ic_menu_camera)",
    ".setSmallIcon(R.drawable.ic_stat_aarish)"
)
fcs = fcs.replace(
    ".setSmallIcon(android.R.drawable.ic_dialog_info)",
    ".setSmallIcon(R.drawable.ic_stat_aarish)"
)

# Fix empty recording par fake SAVE visible bug
fcs = fcs.replace(
    'updateUIState("+ ADD", true, false, true)',
    'updateUIState("+ ADD", unsavedGestures.isNotEmpty(), false, true)'
)

# Multi-touch corruption guard in TouchCaptureView
old_touch_start = '''    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {'''

new_touch_start = '''    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        // Multi-touch/pinch/two-finger accidental input ko record mat karo.
        // Isse corrupt timestamps aur wrong path save hone se bachenge.
        if (event.pointerCount > 1) {
            currentPoints.clear()
            currentSnapshot = null
            return true
        }

        when (event.actionMasked) {'''

if old_touch_start in fcs and "Multi-touch/pinch/two-finger" not in fcs:
    fcs = fcs.replace(old_touch_start, new_touch_start)

# Panel restore ko screen bounds mein rakho
old_restore_block = '''        if (params != null && panel != null) {
            params.x = oldPanelX
            params.y = oldPanelY
            try {
                windowManager.updateViewLayout(panel, params)
            } catch (_: Exception) {
            }
        }'''

new_restore_block = '''        if (params != null && panel != null) {
            val metrics = resources.displayMetrics
            val maxX = if (panel.width > 0) metrics.widthPixels - panel.width else metrics.widthPixels
            val maxY = if (panel.height > 0) metrics.heightPixels - panel.height else metrics.heightPixels

            params.x = oldPanelX.coerceIn(0, if (maxX > 0) maxX else metrics.widthPixels)
            params.y = oldPanelY.coerceIn(0, if (maxY > 0) maxY else metrics.heightPixels)

            try {
                windowManager.updateViewLayout(panel, params)
            } catch (_: Exception) {
            }
        }'''

if old_restore_block in fcs:
    fcs = fcs.replace(old_restore_block, new_restore_block)

# Recording start race: isRecording true captureView ke baad ho
# Pehle original early true ko false-ish behavior se replace karte hain
fcs = fcs.replace(
    '''        isRecording = true
        updateUIState("HIDE", true, false, true)''',
    '''        updateUIState("HIDE", true, false, true)'''
)

# safeAddView success ke baad captureView ke paas isRecording true add
fcs = fcs.replace(
    '''        captureView = touchLayer
        bringPanelToFront()''',
    '''        captureView = touchLayer
        isRecording = true
        bringPanelToFront()'''
)

with open(FCS, "w", encoding="utf-8") as f:
    f.write(fcs)

print("✅ FloatingControlService.kt patched")


# ==========================================================
# 2) AutoActionService.kt fixes
# ==========================================================
with open(AAS, "r", encoding="utf-8") as f:
    aas = f.read()

# Android 7+ guard for dispatchGesture
old_perform_start = '''    private fun performGestureAt(
        startX: Float,
        startY: Float,
        points: List<GesturePoint>
    ) {
        if (points.isEmpty()) return'''

new_perform_start = '''    private fun performGestureAt(
        startX: Float,
        startY: Float,
        points: List<GesturePoint>
    ) {
        if (points.isEmpty()) return

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            Toast.makeText(this, "Gesture playback ke liye Android 7+ required hai", Toast.LENGTH_LONG).show()
            return
        }'''

if old_perform_start in aas and "Android 7+ required" not in aas:
    aas = aas.replace(old_perform_start, new_perform_start)

# BACK/RECENTS return-value + guard delay
old_system = '''        if (firstPoint.x <= -50f) {
            when (firstPoint.x.toInt()) {
                -100 -> performGlobalAction(GLOBAL_ACTION_BACK)
                -200 -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
            return
        }'''

new_system = '''        if (firstPoint.x <= -50f) {
            activeGestureCount.incrementAndGet()

            val ok = when (firstPoint.x.toInt()) {
                -100 -> performGlobalAction(GLOBAL_ACTION_BACK)
                -200 -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                else -> false
            }

            if (!ok) {
                decrementActiveGestureSafely()
                showTinyToast("System action fail hua")
                return
            }

            // BACK/RECENTS ke baad screen transition ko settle hone do
            handler.postDelayed({
                decrementActiveGestureSafely()
            }, 450L)

            return
        }'''

if old_system in aas:
    aas = aas.replace(old_system, new_system)

# Smart ACTION_CLICK guard delay
old_click = '''                val clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    showTinyToast("🎯 Smart Click")
                    return
                }'''

new_click = '''                val clicked = try {
                    clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } catch (_: Exception) {
                    false
                }

                if (clicked) {
                    showTinyToast("🎯 Smart Click")
                    activeGestureCount.incrementAndGet()

                    // Smart click ka callback nahi hota, isliye next step se pehle short wait
                    handler.postDelayed({
                        decrementActiveGestureSafely()
                    }, 400L)

                    return
                }'''

if old_click in aas:
    aas = aas.replace(old_click, new_click)

# Density based tap/swipe slop
old_slop = '''        // 8px tak natural finger drift maan lo, swipe nahi
        return maxDx > 8f || maxDy > 8f'''

new_slop = '''        // High-density phones par tap drift zyada pixels hota hai.
        val slop = 12f * resources.displayMetrics.density
        return maxDx > slop || maxDy > slop'''

if old_slop in aas:
    aas = aas.replace(old_slop, new_slop)

# Hidden/disabled node filter in scoreNode
old_score_start = '''    private fun scoreNode(
        node: AccessibilityNodeInfo,
        bounds: Rect,
        gesture: RecordedGesture
    ): Int {
        var score = 0'''

new_score_start = '''    private fun scoreNode(
        node: AccessibilityNodeInfo,
        bounds: Rect,
        gesture: RecordedGesture
    ): Int {
        if (!node.isVisibleToUser || !node.isEnabled) return 0

        var score = 0'''

if old_score_start in aas and "if (!node.isVisibleToUser || !node.isEnabled) return 0" not in aas:
    aas = aas.replace(old_score_start, new_score_start)

# Clickable parent candidate also must be visible/enabled
old_target_node = '''                    val targetNode = findClickableParent(node) ?: node
                    val targetBounds = Rect()
                    targetNode.getBoundsInScreen(targetBounds)'''

new_target_node = '''                    val targetNode = findClickableParent(node) ?: node
                    if (!targetNode.isVisibleToUser || !targetNode.isEnabled) {
                        continue
                    }

                    val targetBounds = Rect()
                    targetNode.getBoundsInScreen(targetBounds)'''

if old_target_node in aas and "if (!targetNode.isVisibleToUser || !targetNode.isEnabled)" not in aas:
    aas = aas.replace(old_target_node, new_target_node)

# Edge tap no-op fix: tap line 2px andar tak jaaye
aas = aas.replace(
    '''        if (!movement) {
            val safeX = (startX + 0.1f).coerceIn(1f, screenW)
            val safeY = (startY + 0.1f).coerceIn(1f, screenH)
            path.lineTo(safeX, safeY)
        }''',
    '''        if (!movement) {
            val safeX = (startX + 2f).coerceIn(2f, screenW)
            val safeY = (startY + 2f).coerceIn(2f, screenH)
            path.lineTo(safeX, safeY)
        }'''
)

# Volume ACTION_UP consume bug reduce: only handled volume DOWN consumes, UP passes through
old_key_return = '''        if (fcs != null && fcs.isRecordingActive() && isVolumeKey) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
                    fcs.recordSystemAction(1)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                } else if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
                    fcs.recordSystemAction(2)
                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                }
            }

            return true
        }'''

new_key_return = '''        if (fcs != null && fcs.isRecordingActive() && isVolumeKey) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val ok = if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
                    fcs.recordSystemAction(1)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                } else {
                    fcs.recordSystemAction(2)
                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                }

                if (!ok) {
                    showTinyToast("Volume system action fail hua")
                }

                return true
            }

            // ACTION_UP consume mat karo, warna kuch devices par key stuck behavior aa sakta hai
            return false
        }'''

if old_key_return in aas:
    aas = aas.replace(old_key_return, new_key_return)

with open(AAS, "w", encoding="utf-8") as f:
    f.write(aas)

print("✅ AutoActionService.kt patched")


# ==========================================================
# 3) AndroidManifest.xml fixes
# ==========================================================
with open(MANIFEST, "r", encoding="utf-8") as f:
    manifest = f.read()

manifest = manifest.replace(
    'android:allowBackup="true"',
    'android:allowBackup="false"'
)

with open(MANIFEST, "w", encoding="utf-8") as f:
    f.write(manifest)

print("✅ AndroidManifest.xml patched")


# ==========================================================
# 4) Gradle compileSdk / targetSdk safety patch
# ==========================================================
gradle_paths = [
    "app/build.gradle",
    "app/build.gradle.kts"
]

patched_gradle = False

for gp in gradle_paths:
    if not os.path.exists(gp):
        continue

    with open(gp, "r", encoding="utf-8") as f:
        g = f.read()

    original = g

    # Groovy style
    g = re.sub(r'compileSdk\s+33\b', 'compileSdk 35', g)
    g = re.sub(r'compileSdkVersion\s+33\b', 'compileSdkVersion 35', g)
    g = re.sub(r'targetSdk\s+33\b', 'targetSdk 35', g)
    g = re.sub(r'targetSdkVersion\s+33\b', 'targetSdkVersion 35', g)

    # Kotlin DSL style
    g = re.sub(r'compileSdk\s*=\s*33\b', 'compileSdk = 35', g)
    g = re.sub(r'targetSdk\s*=\s*33\b', 'targetSdk = 35', g)

    # Also upgrade 34 to 35 if present
    g = re.sub(r'compileSdk\s+34\b', 'compileSdk 35', g)
    g = re.sub(r'targetSdk\s+34\b', 'targetSdk 35', g)
    g = re.sub(r'compileSdk\s*=\s*34\b', 'compileSdk = 35', g)
    g = re.sub(r'targetSdk\s*=\s*34\b', 'targetSdk = 35', g)

    if g != original:
        with open(gp, "w", encoding="utf-8") as f:
            f.write(g)
        patched_gradle = True
        print(f"✅ {gp} compileSdk/targetSdk patched")

if not patched_gradle:
    print("⚠️ Gradle file auto-patch nahi hui. Manually check karo: compileSdk 34+ / targetSdk 34+ required.")


print("")
print("🎯 FINAL IMPORTANT AUTOFIX DONE")
print("⚠️ Ab: Accessibility service OFF/ON karo")
print("⚠️ Purani recording clear karke fresh recording banao")
print("⚠️ Gradle mein compileSdk 34+ confirm zaroor karo")
