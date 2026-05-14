#!/usr/bin/env python3
from pathlib import Path
import re
import time
import sys

TARGET = Path("app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt")

def die(msg):
    print("❌ " + msg)
    sys.exit(1)

def find_function_span(text: str, fun_name: str):
    m = re.search(
        r'(?m)^[ \t]*(?:private\s+)?fun\s+' + re.escape(fun_name) + r'\s*\(',
        text
    )
    if not m:
        die(f"Function not found: {fun_name}")

    open_brace = text.find("{", m.start())
    if open_brace == -1:
        die(f"Opening brace not found for: {fun_name}")

    depth = 0
    i = open_brace
    in_str = False
    triple = False
    escaped = False
    in_line_comment = False
    in_block_comment = False

    while i < len(text):
        ch = text[i]
        nxt = text[i + 1] if i + 1 < len(text) else ""
        nxt2 = text[i:i+3]

        if in_line_comment:
            if ch == "\n":
                in_line_comment = False
            i += 1
            continue

        if in_block_comment:
            if ch == "*" and nxt == "/":
                in_block_comment = False
                i += 2
            else:
                i += 1
            continue

        if in_str:
            if triple:
                if nxt2 == '"""':
                    in_str = False
                    triple = False
                    i += 3
                else:
                    i += 1
                continue
            else:
                if escaped:
                    escaped = False
                elif ch == "\\":
                    escaped = True
                elif ch == '"':
                    in_str = False
                i += 1
                continue

        if ch == "/" and nxt == "/":
            in_line_comment = True
            i += 2
            continue

        if ch == "/" and nxt == "*":
            in_block_comment = True
            i += 2
            continue

        if nxt2 == '"""':
            in_str = True
            triple = True
            i += 3
            continue

        if ch == '"':
            in_str = True
            escaped = False
            i += 1
            continue

        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return m.start(), i + 1

        i += 1

    die(f"Closing brace not found for: {fun_name}")

def replace_function(text: str, fun_name: str, new_func: str):
    start, end = find_function_span(text, fun_name)
    return text[:start] + new_func.strip("\n") + "\n" + text[end:]

NEW_RECOVER = r'''
    private fun recoverOverlayStackToFrontNow() {
        if (instance !== this@FloatingControlService) return
        if (!::windowManager.isInitialized) return

        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastOverlayRecoverAt < 105L) return
        lastOverlayRecoverAt = now

        val glass = captureView
        val glassParams = glass?.layoutParams as? WindowManager.LayoutParams

        val panel = panelView
        val params = panelParams

        // AARISH_NO_FLICKER_GUARD_V1:
        // Live replay ke waqt overlay stack recover ho sakta hai,
        // lekin restorePanelUI() call nahi karna. Warna glass/panel visible ho kar
        // synthetic tap ko block karta hai aur blink/flicker loop aata hai.
        val keepLiveGhost = isRecording &&
            (liveReplayActive || liveReplayQueueDraining || liveReplayQueue.isNotEmpty())

        if (keepLiveGhost) {
            if (glass != null && glassParams != null) {
                reAddOverlayViewSilently(glass, glassParams)
            }

            if (panel != null && params != null) {
                reAddOverlayViewSilently(panel, params)
            }

            handler.post {
                if (
                    instance === this@FloatingControlService &&
                    isRecording &&
                    (liveReplayActive || liveReplayQueueDraining || liveReplayQueue.isNotEmpty())
                ) {
                    aarishSetGlassGhostModeSafe(true)
                }
            }
            return
        }

        if (isRecording && glass != null && glassParams != null) {
            reAddOverlayViewSilently(glass, glassParams)
        }

        if (panel == null) {
            showFloatingPanel()
            return
        }

        if (params != null) {
            if (AutoActionService.isPlaying()) {
                hidePanelUIForPlayback()
            } else {
                restorePanelUI()
            }
            reAddOverlayViewSilently(panel, params)
        }
    }
'''

NEW_GHOST = r'''
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
                    panelLp.flags = (oldPanelFlags ?: panelLp.flags) and android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                }

                glass.visibility = android.view.View.VISIBLE
                glass.alpha = 1f
                glass.setBackgroundColor(android.graphics.Color.argb(26, 0, 200, 0))

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
            glassParams.flags = if (ghost) {
                glassParams.flags or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                glassParams.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }

            if (panelLp != null) {
                panelLp.flags = if (ghost) {
                    panelLp.flags or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                } else {
                    panelLp.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                }
            }

            if (ghost) {
                glass.visibility = android.view.View.INVISIBLE
                glass.alpha = 0f
                glass.setBackgroundColor(android.graphics.Color.TRANSPARENT)

                panel?.visibility = android.view.View.INVISIBLE
                panel?.alpha = 0f
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
            // AARISH_GHOST_FAILSAFE_V1:
            // Agar WindowManager update fail ho jaye to panel/glass invisible ya not-touchable me lock na rahe.
            solidRollbackQuietly()
            false
        }
    }
'''

def main():
    if not TARGET.exists():
        die(f"File not found: {TARGET}")

    original = TARGET.read_text(encoding="utf-8")
    text = original

    text = replace_function(text, "recoverOverlayStackToFrontNow", NEW_RECOVER)
    text = replace_function(text, "aarishSetGlassGhostModeSafe", NEW_GHOST)

    # Workflow builder ka silent chain-delete bug remove.
    text2 = re.sub(
        r'\n[ \t]*GestureStore\.setNextConfig\(this,\s*nextConfig,\s*null\)\s*\n[ \t]*\n(?=[ \t]*if \(GestureStore\.wouldCreateCycle)',
        '\n\n',
        text,
        count=1
    )
    text = text2

    if text == original:
        die("No change applied. File already patched or pattern mismatch.")

    stamp = time.strftime("%Y%m%d_%H%M%S")
    backup = TARGET.with_suffix(TARGET.suffix + f".bak_no_flicker_{stamp}")
    backup.write_text(original, encoding="utf-8")
    TARGET.write_text(text, encoding="utf-8")

    checks = [
        "AARISH_NO_FLICKER_GUARD_V1",
        "AARISH_GHOST_FAILSAFE_V1",
        "solidRollbackQuietly"
    ]

    for marker in checks:
        if marker not in text:
            die(f"Patch marker missing after write: {marker}")

    if "GestureStore.setNextConfig(this, nextConfig, null)" in text:
        die("Dangerous workflow line still exists.")

    print("✅ NO-FLICKER patch applied successfully.")
    print(f"✅ Backup created: {backup}")
    print("✅ Patched file:", TARGET)

if __name__ == "__main__":
    main()
