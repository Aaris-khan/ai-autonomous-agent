from pathlib import Path
import time

FILE = Path("app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt")

def find_function_end(src: str, start: int) -> int:
    brace = src.find("{", start)
    if brace < 0:
        raise SystemExit("Opening brace not found")
    depth = 0
    i = brace
    in_str = False
    esc = False
    while i < len(src):
        ch = src[i]
        if in_str:
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                in_str = False
        else:
            if ch == '"':
                in_str = True
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return i + 1
        i += 1
    raise SystemExit("Function end not found")

def replace_function(src: str, signature: str, new_code: str) -> str:
    start = src.find(signature)
    if start < 0:
        raise SystemExit(f"Function not found: {signature}")
    end = find_function_end(src, start)
    return src[:start] + new_code.strip() + src[end:]

s = FILE.read_text(encoding="utf-8")
backup = FILE.with_name("FloatingControlService.kt.true_green_glass_backup_" + str(int(time.time())))
backup.write_text(s, encoding="utf-8")
print("Backup:", backup)

notify_new = r'''
fun notifyExternalWindowChangedFromAccessibility() {
        handler.post {
            if (instance !== this@FloatingControlService) return@post
            if (!::windowManager.isInitialized) return@post

            val now = android.os.SystemClock.uptimeMillis()
            val replayBusy = isRecording &&
                captureView != null &&
                (liveReplayActive || liveReplayQueueDraining || liveReplayQueue.isNotEmpty())

            // TRUE GREEN GLASS RULE:
            // Recording ke time green glass permanent rahega.
            // Window change / accessibility events par overlay ko remove-add/recover nahi karna,
            // warna card/icons blink/select hote hain.
            if (replayBusy) {
                semanticClickMuteUntil = kotlin.math.max(semanticClickMuteUntil, now + 1600L)
                aarishSetGlassGhostModeSafe(true)
                return@post
            }

            if (isRecording && captureView != null && !AutoActionService.isPlaying()) {
                if (lastGhostState == true) {
                    aarishSetGlassGhostModeSafe(false)
                }

                if (now > semanticClickMuteUntil) {
                    semanticAccessibilityBridgeUntil = kotlin.math.max(
                        semanticAccessibilityBridgeUntil,
                        now + 9000L
                    )
                }
                return@post
            }

            if (AutoActionService.isPlaying()) {
                recoverOverlayStackToFrontDebounced(180L)
                return@post
            }

            recoverOverlayStackToFrontDebounced(170L)
        }
    }
'''

debounce_new = r'''
private fun recoverOverlayStackToFrontDebounced(delayMs: Long = 170L) {
        // Recording mode me glass/card ko stable rakho.
        // Recover/remove-add sirf recording ke bahar ya playback mode me.
        if (isRecording && captureView != null && !AutoActionService.isPlaying()) {
            if (liveReplayActive || liveReplayQueueDraining || liveReplayQueue.isNotEmpty()) {
                aarishSetGlassGhostModeSafe(true)
            } else if (lastGhostState == true) {
                aarishSetGlassGhostModeSafe(false)
            }
            return
        }

        overlayRecoverRunnable?.let { handler.removeCallbacks(it) }

        val task = Runnable {
            overlayRecoverRunnable = null

            if (isRecording && captureView != null && !AutoActionService.isPlaying()) {
                if (liveReplayActive || liveReplayQueueDraining || liveReplayQueue.isNotEmpty()) {
                    aarishSetGlassGhostModeSafe(true)
                } else if (lastGhostState == true) {
                    aarishSetGlassGhostModeSafe(false)
                }
                return@Runnable
            }

            recoverOverlayStackToFrontNow()
        }

        overlayRecoverRunnable = task
        handler.postDelayed(task, delayMs.coerceIn(40L, 420L))
    }
'''

recover_now_new = r'''
private fun recoverOverlayStackToFrontNow() {
        if (instance !== this@FloatingControlService) return
        if (!::windowManager.isInitialized) return

        // TRUE GREEN GLASS RULE:
        // Recording ke dauran overlay stack ko remove/add mat karo.
        // Yehi remove/add card blink ka main reason ban sakta hai.
        if (isRecording && captureView != null && !AutoActionService.isPlaying()) {
            if (liveReplayActive || liveReplayQueueDraining || liveReplayQueue.isNotEmpty()) {
                aarishSetGlassGhostModeSafe(true)
            } else if (lastGhostState == true) {
                aarishSetGlassGhostModeSafe(false)
            }
            return
        }

        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastOverlayRecoverAt < 180L) return
        lastOverlayRecoverAt = now

        val glass = captureView
        val glassParams = glass?.layoutParams as? WindowManager.LayoutParams

        val panel = panelView
        val params = panelParams

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

ghost_new = r'''
private fun aarishSetGlassGhostModeSafe(ghost: Boolean): Boolean {
    if (!::windowManager.isInitialized) return false
    val glass = captureView ?: return false
    val glassParams = glass.layoutParams as? android.view.WindowManager.LayoutParams ?: return false

    val panel = panelView
    val panelLp = panel?.layoutParams as? android.view.WindowManager.LayoutParams ?: panelParams
    val notTouchable = android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    val targetVisibility = if (ghost) android.view.View.INVISIBLE else android.view.View.VISIBLE
    val targetAlpha = if (ghost) 0f else 1f

    val glassTouchOk = ((glassParams.flags and notTouchable) != 0) == ghost
    val panelTouchOk = panelLp == null || ((panelLp.flags and notTouchable) != 0) == ghost
    val glassVisualOk = glass.visibility == targetVisibility && glass.alpha == targetAlpha
    val panelVisualOk = panel == null || (panel.visibility == targetVisibility && panel.alpha == targetAlpha)

    // Same state me baar-baar updateViewLayout mat karo.
    // Repeated WindowManager updates se bhi blink/flicker aa sakta hai.
    if (lastGhostState == ghost && glassTouchOk && panelTouchOk && glassVisualOk && panelVisualOk) {
        return true
    }

    return try {
        glassParams.flags = if (ghost) {
            glassParams.flags or notTouchable
        } else {
            glassParams.flags and notTouchable.inv()
        }

        if (panelLp != null) {
            panelLp.flags = if (ghost) {
                panelLp.flags or notTouchable
            } else {
                panelLp.flags and notTouchable.inv()
            }
        }

        if (ghost) {
            // Replay moment:
            // glass + panel dono hidden + pass-through.
            // User ko actual app par click/replay clearly dikhega.
            glass.visibility = android.view.View.INVISIBLE
            glass.alpha = 0f
            glass.setBackgroundColor(android.graphics.Color.TRANSPARENT)

            panel?.visibility = android.view.View.INVISIBLE
            panel?.alpha = 0f
        } else {
            // Recording idle:
            // green glass permanent rahega.
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
        // Fail-safe: kabhi bhi invisible/not-touchable lock me mat phasna.
        try {
            glassParams.flags = glassParams.flags and notTouchable.inv()
            panelLp?.let { it.flags = it.flags and notTouchable.inv() }

            glass.visibility = android.view.View.VISIBLE
            glass.alpha = 1f
            glass.setBackgroundColor(android.graphics.Color.argb(26, 0, 200, 0))

            panel?.visibility = android.view.View.VISIBLE
            panel?.alpha = 1f

            if (glass.parent != null) windowManager.updateViewLayout(glass, glassParams)
            if (panel != null && panelLp != null && panel.parent != null) {
                windowManager.updateViewLayout(panel, panelLp)
            }

            lastGhostState = false
        } catch (_: Throwable) {
        }
        false
    }
}
'''

s = replace_function(s, "fun notifyExternalWindowChangedFromAccessibility()", notify_new)
s = replace_function(s, "private fun recoverOverlayStackToFrontDebounced", debounce_new)
s = replace_function(s, "private fun recoverOverlayStackToFrontNow()", recover_now_new)
s = replace_function(s, "private fun aarishSetGlassGhostModeSafe", ghost_new)

FILE.write_text(s, encoding="utf-8")
print("✅ TRUE GREEN GLASS NO-FLICKER patch applied")
