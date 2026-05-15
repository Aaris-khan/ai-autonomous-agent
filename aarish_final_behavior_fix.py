from pathlib import Path
import time
import re

FCS = Path("app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt")

if not FCS.exists():
    raise SystemExit("❌ FloatingControlService.kt nahi mila. Project root se script run karo.")

text = FCS.read_text(encoding="utf-8")

backup = FCS.with_suffix(".kt.bak_final_behavior_" + time.strftime("%Y%m%d_%H%M%S"))
backup.write_text(text, encoding="utf-8")
print(f"✅ Backup saved: {backup}")

def find_function_span(src: str, func_name: str):
    pattern = re.compile(r'(?m)^[ \t]*(?:private[ \t]+)?fun[ \t]+' + re.escape(func_name) + r'[ \t]*\(')
    m = pattern.search(src)
    if not m:
        raise RuntimeError(f"Function not found: {func_name}")

    brace = src.find("{", m.end())
    if brace < 0:
        raise RuntimeError(f"Opening brace not found: {func_name}")

    depth = 0
    i = brace
    in_line_comment = False
    in_block_comment = False
    in_str = False
    triple = False
    esc = False

    while i < len(src):
        ch = src[i]
        nxt = src[i:i+2]
        tri = src[i:i+3]

        if in_line_comment:
            if ch == "\n":
                in_line_comment = False
            i += 1
            continue

        if in_block_comment:
            if nxt == "*/":
                in_block_comment = False
                i += 2
            else:
                i += 1
            continue

        if in_str:
            if triple:
                if tri == '"""':
                    in_str = False
                    triple = False
                    i += 3
                else:
                    i += 1
                continue
            else:
                if esc:
                    esc = False
                    i += 1
                    continue
                if ch == "\\":
                    esc = True
                    i += 1
                    continue
                if ch == '"':
                    in_str = False
                    i += 1
                    continue
                i += 1
                continue

        if nxt == "//":
            in_line_comment = True
            i += 2
            continue
        if nxt == "/*":
            in_block_comment = True
            i += 2
            continue
        if tri == '"""':
            in_str = True
            triple = True
            i += 3
            continue
        if ch == '"':
            in_str = True
            triple = False
            i += 1
            continue

        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return m.start(), i + 1

        i += 1

    raise RuntimeError(f"Closing brace not found: {func_name}")

def replace_function(src: str, func_name: str, new_code: str):
    start, end = find_function_span(src, func_name)
    return src[:start] + new_code.strip("\n") + "\n" + src[end:]

new_reset = r'''
private fun aarishResetLiveReplayStateSafe(forceSolid: Boolean = true) {
    liveReplaySerial++
    liveReplayActive = false
    liveReplayQueue.clear()
    liveReplayQueueDraining = false
    lastLiveReplayAt = 0L
    lastLiveReplayX = Float.NaN
    lastLiveReplayY = Float.NaN

    // Pending idle replay timer ko hard-cancel karo.
    liveIdleTimerRunnable?.let { handler.removeCallbacks(it) }
    liveIdleTimerRunnable = null

    // IMPORTANT:
    // Naya glass/panel aane par purana cached ghost state valid nahi hota.
    // Isko null nahi kiya to code soch sakta hai "already ghost/solid hai",
    // aur actual WindowManager flags update nahi honge.
    lastGhostState = null

    val notTouchable = android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    val notFocusable = android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

    fun makePanelTouchableSilently() {
        try {
            val panel = panelView ?: return
            val lp = (panel.layoutParams as? android.view.WindowManager.LayoutParams) ?: panelParams ?: return
            lp.flags = (lp.flags and notTouchable.inv()) or notFocusable
            panelParams = lp

            if (::windowManager.isInitialized && panel.parent != null) {
                windowManager.updateViewLayout(panel, lp)
            }
        } catch (_: Throwable) {
        }
    }

    fun makeGlassTouchableSilently() {
        try {
            val glass = captureView ?: return
            val lp = glass.layoutParams as? android.view.WindowManager.LayoutParams ?: return
            glass.alpha = 1f
            lp.flags = (lp.flags and notTouchable.inv()) or notFocusable

            if (::windowManager.isInitialized && glass.parent != null) {
                windowManager.updateViewLayout(glass, lp)
            }
        } catch (_: Throwable) {
        }
    }

    if (forceSolid) {
        // Normal restore: glass + panel dono touchable.
        aarishSetGlassGhostModeSafe(false)
    } else {
        // View remove hone wali ho tab bhi panel ko touchable zaroor restore karo,
        // warna DONE/SAVE/CUT next session me dead ho sakte hain.
        makeGlassTouchableSilently()
        makePanelTouchableSilently()
    }
}
'''

new_ghost = r'''
private fun aarishSetGlassGhostModeSafe(ghost: Boolean): Boolean {
    if (!::windowManager.isInitialized) return false

    val glass = captureView ?: return false
    val glassParams = glass.layoutParams as? android.view.WindowManager.LayoutParams ?: return false

    val panel = panelView
    val panelLp = (panel?.layoutParams as? android.view.WindowManager.LayoutParams) ?: panelParams

    val notTouchable = android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    val notFocusable = android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

    val glassAlreadyGhost = (glassParams.flags and notTouchable) != 0
    val panelAlreadyGhost = if (panel != null && panelLp != null && panel.parent != null) {
        (panelLp.flags and notTouchable) != 0
    } else {
        ghost
    }

    // Cached state tabhi trust karo jab real WindowManager flags bhi same hon.
    if (lastGhostState == ghost && glassAlreadyGhost == ghost && panelAlreadyGhost == ghost) {
        return true
    }

    return try {
        // NO-BLINK RULE:
        // Glass ko alpha 0 mat karo. Visual green glass stable rahega.
        // Replay ke time sirf FLAG_NOT_TOUCHABLE lagta hai, isliye injected gesture
        // behind-app tak pass hota hai without remove/add blink.
        glass.alpha = 1f

        glassParams.flags = if (ghost) {
            glassParams.flags or notTouchable or notFocusable
        } else {
            (glassParams.flags and notTouchable.inv()) or notFocusable
        }

        if (glass.parent != null) {
            windowManager.updateViewLayout(glass, glassParams)
        }

        if (panel != null && panelLp != null) {
            panelLp.flags = if (ghost) {
                panelLp.flags or notTouchable or notFocusable
            } else {
                (panelLp.flags and notTouchable.inv()) or notFocusable
            }

            panelParams = panelLp

            if (panel.parent != null) {
                windowManager.updateViewLayout(panel, panelLp)
            }
        }

        lastGhostState = ghost
        true
    } catch (_: Throwable) {
        lastGhostState = null
        false
    }
}
'''

new_trigger = r'''
fun triggerLiveReplaySafe(gesture: RecordedGesture) {
    if (instance !== this@FloatingControlService) return
    if (!isRecording || AutoActionService.isPlaying()) return
    if (gesture.points.isEmpty()) return

    val firstX = gesture.points.firstOrNull()?.x ?: return
    if (firstX <= -50f) {
        val actionType = when (firstX.toInt()) {
            -100 -> 1
            -200 -> 2
            else -> 0
        }
        if (actionType != 0) triggerLiveSystemActionSafe(actionType)
        return
    }

    if (isLiveReplayBlockedDuplicate(gesture)) return

    handler.post {
        if (instance !== this@FloatingControlService || !isRecording || AutoActionService.isPlaying()) return@post

        // Queue overflow par app ko freeze nahi hone dena.
        if (liveReplayQueue.size >= 64) {
            while (liveReplayQueue.size > 32) {
                liveReplayQueue.pollFirst()
            }

            val now = android.os.SystemClock.uptimeMillis()
            if (now - liveReplayFloodToastAt > 2500L) {
                liveReplayFloodToastAt = now
                Toast.makeText(this, "Too many live taps, old pending taps trimmed", Toast.LENGTH_SHORT).show()
            }
        }

        liveReplayQueue.addLast(gesture)

        // Agar replay already chal raha hai to same drain baaki queue ko bhi fire karega.
        if (liveReplayActive || liveReplayQueueDraining) return@post

        // Idle wait ke dauran glass SOLID/touchable rahe.
        // Purani ghost state bachi ho to turant normal karo.
        if (lastGhostState == true) {
            aarishSetGlassGhostModeSafe(false)
        }

        // Fast double-tap / triple-tap miss na ho:
        // har new tap purana idle timer cancel karega.
        liveIdleTimerRunnable?.let { handler.removeCallbacks(it) }
        liveIdleTimerRunnable = null

        liveReplaySerial++
        val serial = liveReplaySerial

        val idleTask = Runnable {
            liveIdleTimerRunnable = null

            if (serial != liveReplaySerial ||
                instance !== this@FloatingControlService ||
                !isRecording ||
                AutoActionService.isPlaying()
            ) {
                return@Runnable
            }

            if (liveReplayQueue.isNotEmpty() && !liveReplayActive && !liveReplayQueueDraining) {
                drainNextLiveReplaySafe()
            }
        }

        liveIdleTimerRunnable = idleTask
        handler.postDelayed(idleTask, 550L)
    }
}
'''

text = replace_function(text, "aarishResetLiveReplayStateSafe", new_reset)
text = replace_function(text, "aarishSetGlassGhostModeSafe", new_ghost)
text = replace_function(text, "triggerLiveReplaySafe", new_trigger)

# New recording ke time cached ghost state ko definitely solid mark karo.
old_line = "    captureView = touchLayer\n    isRecording = true"
new_line = "    captureView = touchLayer\n    lastGhostState = false\n    isRecording = true"

if old_line in text and new_line not in text:
    text = text.replace(old_line, new_line, 1)
elif new_line in text:
    pass
else:
    print("⚠️ startRecording captureView line exact nahi mili; manual check karna.")

FCS.write_text(text, encoding="utf-8")
print("✅ FINAL BEHAVIOR FIX applied successfully.")
print("✅ Fixed: ghost-state stuck, panel dead-touch, no-blink live replay, double-tap idle queue safety.")
