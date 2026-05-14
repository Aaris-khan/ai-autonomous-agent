#!/usr/bin/env python3
from pathlib import Path
import shutil, time, sys, re

ROOT = Path(".").resolve()
TS = time.strftime("%Y%m%d_%H%M%S")

FCS = ROOT / "app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt"
AAS = ROOT / "app/src/main/java/com/aarishkhan/aarishai/AutoActionService.kt"

DRY = "--dry-run" in sys.argv

def log(msg):
    print(msg)

def backup(path: Path):
    if not path.exists():
        raise FileNotFoundError(f"Missing file: {path}")
    bak = path.with_suffix(path.suffix + f".bak_{TS}")
    if not DRY:
        shutil.copy2(path, bak)
    log(f"✅ Backup: {bak}")

def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")

def write(path: Path, text: str):
    if DRY:
        log(f"🧪 Dry-run: would write {path}")
        return
    path.write_text(text, encoding="utf-8")
    log(f"✅ Patched: {path}")

def matching_brace(text: str, open_idx: int) -> int:
    depth = 0
    i = open_idx
    n = len(text)
    in_line = False
    in_block = False
    in_str = False
    in_char = False
    in_triple = False
    esc = False

    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        tri = text[i:i+3]

        if in_line:
            if c == "\n":
                in_line = False
            i += 1
            continue

        if in_block:
            if c == "*" and nxt == "/":
                in_block = False
                i += 2
            else:
                i += 1
            continue

        if in_triple:
            if tri == '"""':
                in_triple = False
                i += 3
            else:
                i += 1
            continue

        if in_str:
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == '"':
                in_str = False
            i += 1
            continue

        if in_char:
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == "'":
                in_char = False
            i += 1
            continue

        if c == "/" and nxt == "/":
            in_line = True
            i += 2
            continue
        if c == "/" and nxt == "*":
            in_block = True
            i += 2
            continue
        if tri == '"""':
            in_triple = True
            i += 3
            continue
        if c == '"':
            in_str = True
            i += 1
            continue
        if c == "'":
            in_char = True
            i += 1
            continue

        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return i + 1

        i += 1

    raise RuntimeError("Matching brace not found")

def replace_function(src: str, signature: str, replacement: str) -> str:
    idx = src.find(signature)
    if idx == -1:
        raise RuntimeError(f"Function not found: {signature}")

    line_start = src.rfind("\n", 0, idx) + 1
    open_idx = src.find("{", idx)
    if open_idx == -1:
        raise RuntimeError(f"Opening brace not found for: {signature}")

    end_idx = matching_brace(src, open_idx)
    return src[:line_start] + replacement.rstrip() + "\n\n" + src[end_idx:].lstrip("\n")

NEW_GHOST_FUNCTION = r'''
private fun aarishSetGlassGhostModeSafe(ghost: Boolean): Boolean {
    if (!::windowManager.isInitialized) return false
    val glass = captureView ?: return false
    val glassParams = glass.layoutParams as? android.view.WindowManager.LayoutParams ?: return false

    val panel = panelView
    val panelLp = panel?.layoutParams as? android.view.WindowManager.LayoutParams ?: panelParams

    val oldGlassFlags = glassParams.flags
    val oldPanelFlags = panelLp?.flags
    val oldGlassVisibility = glass.visibility
    val oldGlassAlpha = glass.alpha
    val oldPanelVisibility = panel?.visibility
    val oldPanelAlpha = panel?.alpha

    fun solidRollbackQuietly() {
        try {
            glassParams.flags = oldGlassFlags and android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()

            if (panelLp != null) {
                panelLp.flags = (oldPanelFlags ?: panelLp.flags) and
                    android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }

            glass.visibility = android.view.View.VISIBLE
            glass.alpha = 1f
            glass.setBackgroundColor(android.graphics.Color.argb(26, 0, 200, 0))

            // Emergency controls hamesha visible/clickable.
            panel?.visibility = android.view.View.VISIBLE
            panel?.alpha = 1f

            if (glass.parent != null) {
                windowManager.updateViewLayout(glass, glassParams)
            }

            if (panel != null && panelLp != null && panel.parent != null) {
                windowManager.updateViewLayout(panel, panelLp)
            }

            lastGhostState = false
        } catch (_: Throwable) {
            try {
                glass.visibility = oldGlassVisibility
                glass.alpha = oldGlassAlpha
                if (panel != null && oldPanelVisibility != null && oldPanelAlpha != null) {
                    panel.visibility = oldPanelVisibility
                    panel.alpha = oldPanelAlpha
                }
            } catch (_: Throwable) {
            }
        }
    }

    return try {
        // Glass pass-through rahega, taaki live replay real app par fire ho.
        glassParams.flags = if (ghost) {
            glassParams.flags or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            glassParams.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }

        // SMART FIX:
        // Panel ko live replay ke time invisible/not-touchable mat banao.
        // Warna long press/long gesture me DONE/CUT emergency control lock ho sakta hai.
        if (panelLp != null) {
            panelLp.flags = panelLp.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }

        if (ghost) {
            glass.visibility = android.view.View.INVISIBLE
            glass.alpha = 0f
            glass.setBackgroundColor(android.graphics.Color.TRANSPARENT)

            // Panel emergency control ke liye ON rahega.
            panel?.visibility = android.view.View.VISIBLE
            panel?.alpha = 1f
        } else {
            glass.visibility = android.view.View.VISIBLE
            glass.alpha = 1f
            glass.setBackgroundColor(android.graphics.Color.argb(26, 0, 200, 0))

            panel?.visibility = android.view.View.VISIBLE
            panel?.alpha = 1f
        }

        if (glass.parent != null) {
            windowManager.updateViewLayout(glass, glassParams)
        }

        if (panel != null && panelLp != null && panel.parent != null) {
            windowManager.updateViewLayout(panel, panelLp)
        }

        lastGhostState = ghost

        if (!ghost && instance === this@FloatingControlService && isRecording) {
            handler.post {
                if (
                    instance === this@FloatingControlService &&
                    isRecording &&
                    !liveReplayActive &&
                    !liveReplayQueueDraining &&
                    liveReplayQueue.isEmpty()
                ) {
                    restorePanelUI()
                }
            }
        }

        true
    } catch (_: Throwable) {
        // Failsafe: invisible/not-touchable lock se bachao.
        solidRollbackQuietly()
        false
    }
}
'''.strip()

NEW_BLINK_FUNCTION = r'''
    private fun aarishBlinkStrikeDispatchGesture(gesture: RecordedGesture, onDone: () -> Unit) {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)

        fun finishOnce() {
            if (finished.compareAndSet(false, true)) {
                main.post { onDone() }
            }
        }

        try {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
                finishOnce()
                return
            }

            val raw0 = gesture.points
                .filter { p ->
                    !p.x.isNaN() && !p.x.isInfinite() &&
                        !p.y.isNaN() && !p.y.isInfinite()
                }
                .sortedBy { it.t.coerceAtLeast(0L) }

            if (raw0.isEmpty()) {
                finishOnce()
                return
            }

            val first = raw0.first()

            // Virtual system actions.
            if (first.x <= -50f) {
                val ok = when (first.x.toInt()) {
                    -100 -> performGlobalAction(GLOBAL_ACTION_BACK)
                    -200 -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                    else -> false
                }
                main.postDelayed({ finishOnce() }, if (ok) 180L else 0L)
                return
            }

            val raw = if (raw0.size <= 180) {
                raw0
            } else {
                val step = kotlin.math.ceil(raw0.size / 180.0).toInt().coerceAtLeast(1)
                raw0.filterIndexed { index, _ ->
                    index == 0 || index == raw0.lastIndex || index % step == 0
                }
            }

            val maxX = (resources.displayMetrics.widthPixels.toFloat() - 2f).coerceAtLeast(2f)
            val maxY = (resources.displayMetrics.heightPixels.toFloat() - 2f).coerceAtLeast(2f)

            val startX = first.x.coerceIn(2f, maxX)
            val startY = first.y.coerceIn(2f, maxY)

            val path = android.graphics.Path()
            path.moveTo(startX, startY)

            var movement = false
            for (i in 1 until raw.size) {
                val p = raw[i]
                val dx = p.x - first.x
                val dy = p.y - first.y

                if (kotlin.math.abs(dx) > 7f || kotlin.math.abs(dy) > 7f) {
                    movement = true
                }

                path.lineTo(
                    (startX + dx).coerceIn(2f, maxX),
                    (startY + dy).coerceIn(2f, maxY)
                )
            }

            if (!movement) {
                path.lineTo(
                    (startX + 1.5f).coerceIn(2f, maxX),
                    (startY + 1.5f).coerceIn(2f, maxY)
                )
            }

            val startT = raw0.first().t.coerceAtLeast(0L)
            val endT = raw0.last().t.coerceAtLeast(startT)
            val duration = (endT - startT).coerceIn(55L, 600000L)

            // Long tap ko chunks me dispatch karo.
            if (!movement && duration > 59000L) {
                dispatchLongPressChunksSafe(startX, startY, duration, null) {
                    finishOnce()
                }
                return
            }

            // SMART FIX:
            // Moving/swipe live replay me 59000ms se zyada duration direct StrokeDescription
            // me dena unsafe hai. Clamp karo, warna live replay skip/crash/stuck ho sakta hai.
            val safeDuration = duration.coerceAtMost(59000L)

            val desc = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(
                    android.accessibilityservice.GestureDescription.StrokeDescription(
                        path,
                        0L,
                        safeDuration
                    )
                )
                .build()

            val accepted = dispatchGesture(
                desc,
                object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        finishOnce()
                    }

                    override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        finishOnce()
                    }
                },
                null
            )

            if (!accepted) {
                finishOnce()
                return
            }

            main.postDelayed({
                finishOnce()
            }, (safeDuration + 1650L).coerceAtMost(610000L))
        } catch (_: Throwable) {
            finishOnce()
        }
    }
'''.rstrip()

def ensure_text_file(path: Path, content: str):
    if path.exists():
        log(f"ℹ️ Exists, skipped: {path}")
        return
    if not DRY:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
    log(f"✅ Created: {path}")

def ensure_strings():
    path = ROOT / "app/src/main/res/values/strings.xml"
    if not path.exists():
        ensure_text_file(path, '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">AarishAI</string>
    <string name="accessibility_service_description">AarishAI Screen Command automation service</string>
</resources>
''')
        return

    text = path.read_text(encoding="utf-8")
    changed = False

    def add_string(src, name, value):
        nonlocal changed
        if f'name="{name}"' in src:
            return src
        changed = True
        return src.replace("</resources>", f'    <string name="{name}">{value}</string>\n</resources>')

    new = add_string(text, "app_name", "AarishAI")
    new = add_string(new, "accessibility_service_description", "AarishAI Screen Command automation service")

    if changed:
        backup(path)
        write(path, new)
    else:
        log("✅ strings.xml already OK")

def ensure_resources():
    ensure_strings()

    drawable = ROOT / "app/src/main/res/drawable"

    ensure_text_file(drawable / "bg_main_soft.xml", '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient
        android:angle="270"
        android:startColor="#F8FAFC"
        android:centerColor="#E0F2FE"
        android:endColor="#EEF2FF" />
</shape>
''')

    ensure_text_file(drawable / "bg_screen_command_button.xml", '''<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true">
        <shape>
            <corners android:radius="20dp" />
            <gradient
                android:angle="0"
                android:startColor="#0F766E"
                android:centerColor="#2563EB"
                android:endColor="#4F46E5" />
            <stroke android:width="1dp" android:color="#CCFFFFFF" />
        </shape>
    </item>
    <item>
        <shape>
            <corners android:radius="20dp" />
            <gradient
                android:angle="0"
                android:startColor="#14B8A6"
                android:centerColor="#2563EB"
                android:endColor="#7C3AED" />
            <stroke android:width="1dp" android:color="#AAFFFFFF" />
        </shape>
    </item>
</selector>
''')

    ensure_text_file(drawable / "ic_stat_aarish.xml", '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,2 L22,22 L2,22 Z" />
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M11,8 L13,8 L13,16 L11,16 Z" />
</vector>
''')

def patch_kotlin():
    backup(FCS)
    fcs_text = read(FCS)
    fcs_new = replace_function(
        fcs_text,
        "private fun aarishSetGlassGhostModeSafe(",
        NEW_GHOST_FUNCTION
    )
    write(FCS, fcs_new)

    backup(AAS)
    aas_text = read(AAS)
    aas_new = replace_function(
        aas_text,
        "private fun aarishBlinkStrikeDispatchGesture(",
        NEW_BLINK_FUNCTION
    )
    write(AAS, aas_new)

def main():
    log("🚀 AarishAI Smart Autofixer started")
    patch_kotlin()
    ensure_resources()
    log("")
    log("✅ DONE: Smart autofix complete")
    log("Now run:")
    log("  chmod +x ./gradlew 2>/dev/null || true")
    log("  ./gradlew assembleDebug --no-daemon")

if __name__ == "__main__":
    main()
