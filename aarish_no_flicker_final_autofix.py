from pathlib import Path
import time, sys

ROOT = Path.cwd()
FCS = ROOT / "app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt"

if not FCS.exists():
    print("❌ FloatingControlService.kt nahi mila. Pehle cd ~/aarishai karo.")
    sys.exit(1)

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

s = FCS.read_text(encoding="utf-8")
backup = FCS.with_name("FloatingControlService.kt.no_flicker_backup_" + str(int(time.time())))
backup.write_text(s, encoding="utf-8")

notify_new = r'''
fun notifyExternalWindowChangedFromAccessibility() {
        handler.post {
            if (instance !== this@FloatingControlService) return@post
            if (!::windowManager.isInitialized) return@post

            val now = android.os.SystemClock.uptimeMillis()

            // FINAL NO-FLICKER RULE:
            // Queue waiting state ko replayBusy mat mano.
            // 550ms idle wait ke dauran green glass permanent rahega.
            val replayBusy = isRecording &&
                captureView != null &&
                (liveReplayActive || liveReplayQueueDraining)

            if (replayBusy) {
                semanticClickMuteUntil = kotlin.math.max(semanticClickMuteUntil, now + 1800L)
                aarishSetGlassGhostModeSafe(true)
                return@post
            }

            if (isRecording && captureView != null && !AutoActionService.isPlaying()) {
                if (lastGhostState == true && !liveReplayActive && !liveReplayQueueDraining) {
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
        // Recording mode me glass/card stable rakho.
        // Queue waiting ke dauran ghost mat karo. Ghost sirf actual replay drain me.
        if (isRecording && captureView != null && !AutoActionService.isPlaying()) {
            if (liveReplayActive || liveReplayQueueDraining) {
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
                if (liveReplayActive || liveReplayQueueDraining) {
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

        // Recording ke dauran remove/add bilkul nahi.
        // Remove/add hi blink ka common reason hota hai.
        if (isRecording && captureView != null && !AutoActionService.isPlaying()) {
            if (liveReplayActive || liveReplayQueueDraining) {
                aarishSetGlassGhostModeSafe(true)
            } else if (lastGhostState == true) {
                aarishSetGlassGhostModeSafe(false)
            }
            return
        }

        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastOverlayRecoverAt < 220L) return
        lastOverlayRecoverAt = now

        val panel = panelView
        val params = panelParams

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

reset_new = r'''
private fun aarishResetLiveReplayStateSafe(forceSolid: Boolean = true) {
        liveReplaySerial++
        liveReplayActive = false
        liveReplayQueue.clear()
        liveReplayQueueDraining = false
        lastLiveReplayAt = 0L
        lastLiveReplayX = Float.NaN
        lastLiveReplayY = Float.NaN
        if (forceSolid) {
            aarishSetGlassGhostModeSafe(false)
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
    val notFocusable = android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

    fun clearButtonState(view: android.view.View?) {
        if (view == null) return
        try {
            view.isPressed = false
            view.isSelected = false
            view.isActivated = false
            view.clearFocus()
            view.jumpDrawablesToCurrentState()
        } catch (_: Throwable) {}

        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                clearButtonState(view.getChildAt(i))
            }
        }
    }

    fun setTreeEnabled(view: android.view.View?, enabled: Boolean) {
        if (view == null) return
        try {
            view.isEnabled = enabled
            view.isClickable = enabled
            view.isFocusable = enabled
            view.importantForAccessibility =
                if (enabled) android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                else android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } catch (_: Throwable) {}

        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                setTreeEnabled(view.getChildAt(i), enabled)
            }
        }
    }

    fun updateSafe(view: android.view.View?, lp: android.view.WindowManager.LayoutParams?) {
        if (view == null || lp == null || view.parent == null) return
        try { windowManager.updateViewLayout(view, lp) } catch (_: Throwable) {}
    }

    val targetVisibility = if (ghost) android.view.View.INVISIBLE else android.view.View.VISIBLE
    val targetAlpha = if (ghost) 0f else 1f

    val glassTouchOk = ((glassParams.flags and notTouchable) != 0) == ghost
    val panelTouchOk = panelLp == null || ((panelLp.flags and notTouchable) != 0) == ghost
    val glassVisualOk = glass.visibility == targetVisibility && glass.alpha == targetAlpha && glass.isEnabled == !ghost
    val panelVisualOk = panel == null || (
        panel.visibility == targetVisibility &&
            panel.alpha == targetAlpha &&
            panel.isEnabled == !ghost
        )

    if (lastGhostState == ghost && glassTouchOk && panelTouchOk && glassVisualOk && panelVisualOk) {
        return true
    }

    return try {
        if (ghost) {
            // Replay start:
            // Panel ko sabse pehle non-touchable + invisible banao,
            // warna synthetic tap panel/icons par lag sakta hai.
            panelLp?.let {
                it.flags = it.flags or notTouchable or notFocusable
            }

            panel?.let {
                clearButtonState(it)
                setTreeEnabled(it, false)
                it.alpha = 0f
                it.visibility = android.view.View.INVISIBLE
                updateSafe(it, panelLp)
            }

            glassParams.flags = glassParams.flags or notTouchable or notFocusable
            clearButtonState(glass)
            setTreeEnabled(glass, false)
            glass.alpha = 0f
            glass.visibility = android.view.View.INVISIBLE
            glass.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            updateSafe(glass, glassParams)
        } else {
            // Replay complete:
            // Green glass wapas stable. Button state clean.
            glassParams.flags = (glassParams.flags and notTouchable.inv()) or notFocusable
            setTreeEnabled(glass, true)
            clearButtonState(glass)
            glass.setBackgroundColor(android.graphics.Color.argb(26, 0, 200, 0))
            glass.visibility = android.view.View.VISIBLE
            glass.alpha = 1f
            updateSafe(glass, glassParams)

            panelLp?.let {
                it.flags = (it.flags and notTouchable.inv()) or notFocusable
            }

            panel?.let {
                setTreeEnabled(it, true)
                clearButtonState(it)
                it.visibility = android.view.View.VISIBLE
                it.alpha = 1f
                updateSafe(it, panelLp)
            }
        }

        lastGhostState = ghost

        if (!ghost && instance === this@FloatingControlService && isRecording) {
            handler.post {
                if (
                    instance === this@FloatingControlService &&
                    isRecording &&
                    !liveReplayActive &&
                    !liveReplayQueueDraining
                ) {
                    restorePanelUI()
                    panelView?.let { clearButtonState(it) }
                }
            }
        }

        true
    } catch (_: Throwable) {
        try {
            glassParams.flags = (glassParams.flags and notTouchable.inv()) or notFocusable
            panelLp?.let { it.flags = (it.flags and notTouchable.inv()) or notFocusable }

            setTreeEnabled(glass, true)
            clearButtonState(glass)
            glass.setBackgroundColor(android.graphics.Color.argb(26, 0, 200, 0))
            glass.visibility = android.view.View.VISIBLE
            glass.alpha = 1f
            updateSafe(glass, glassParams)

            panel?.let {
                setTreeEnabled(it, true)
                clearButtonState(it)
                it.visibility = android.view.View.VISIBLE
                it.alpha = 1f
                updateSafe(it, panelLp)
            }

            lastGhostState = false
        } catch (_: Throwable) {}
        false
    }
}
'''

drain_new = r'''
private fun drainNextLiveReplaySafe() {
        handler.post {
            if (instance !== this@FloatingControlService || !isRecording || AutoActionService.isPlaying()) {
                aarishResetLiveReplayStateSafe()
                return@post
            }

            if (liveReplayQueue.isEmpty()) {
                liveReplayQueueDraining = false
                liveReplayActive = false
                aarishSetGlassGhostModeSafe(false)
                return@post
            }

            val serial = liveReplaySerial
            liveReplayQueueDraining = true
            liveReplayActive = true

            semanticClickMuteUntil = android.os.SystemClock.uptimeMillis() + 2200L

            if (!aarishSetGlassGhostModeSafe(true)) {
                aarishResetLiveReplayStateSafe(forceSolid = false)
                return@post
            }

            fun restoreAfterQueueDone() {
                if (serial != liveReplaySerial || instance !== this@FloatingControlService) return
                liveReplayQueueDraining = false
                liveReplayActive = false

                handler.postDelayed({
                    if (
                        serial == liveReplaySerial &&
                        instance === this@FloatingControlService &&
                        isRecording &&
                        !AutoActionService.isPlaying() &&
                        !liveReplayActive &&
                        !liveReplayQueueDraining
                    ) {
                        aarishSetGlassGhostModeSafe(false)
                    }
                }, 95L)
            }

            fun fireNextQueuedGesture() {
                if (serial != liveReplaySerial || instance !== this@FloatingControlService || !isRecording || AutoActionService.isPlaying()) {
                    aarishResetLiveReplayStateSafe()
                    return
                }

                val gesture = liveReplayQueue.pollFirst()

                if (gesture == null) {
                    restoreAfterQueueDone()
                    return
                }

                val duration = liveReplayDurationMs(gesture)
                val watchdogMs = (duration + 4500L).coerceAtMost(610000L)
                var finished = false

                fun finishAndContinue() {
                    if (finished) return
                    finished = true

                    if (serial != liveReplaySerial || instance !== this@FloatingControlService || !isRecording || AutoActionService.isPlaying()) {
                        aarishResetLiveReplayStateSafe()
                        return
                    }

                    if (liveReplayQueue.isNotEmpty()) {
                        handler.postDelayed({
                            fireNextQueuedGesture()
                        }, 55L)
                    } else {
                        restoreAfterQueueDone()
                    }
                }

                semanticClickMuteUntil = android.os.SystemClock.uptimeMillis() + duration + 1700L

                AutoActionService.playSingleLiveGestureSafe(gesture) {
                    handler.postDelayed({
                        finishAndContinue()
                    }, 70L)
                }

                handler.postDelayed({
                    if (serial == liveReplaySerial && liveReplayActive && !finished) {
                        finishAndContinue()
                    }
                }, watchdogMs)
            }

            // WindowManager ko panel/glass non-touchable apply karne ka proper time do.
            handler.postDelayed({
                fireNextQueuedGesture()
            }, 125L)
        }
    }
'''

trigger_new = r'''
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

            if (liveReplayQueue.size >= 32) {
                while (liveReplayQueue.size > 12) {
                    liveReplayQueue.pollFirst()
                }
            }

            liveReplayQueue.addLast(gesture)

            // Agar replay already chal raha hai to usko cancel/reset mat karo.
            // Bas queue me add rehne do. Ye flicker ka bada fix hai.
            if (liveReplayActive || liveReplayQueueDraining) {
                return@post
            }

            liveReplaySerial++
            val serial = liveReplaySerial

            handler.postDelayed({
                if (
                    serial != liveReplaySerial ||
                    instance !== this@FloatingControlService ||
                    !isRecording ||
                    AutoActionService.isPlaying()
                ) {
                    return@postDelayed
                }

                if (liveReplayQueue.isNotEmpty() && !liveReplayActive && !liveReplayQueueDraining) {
                    drainNextLiveReplaySafe()
                }
            }, 550L)
        }
    }
'''

s = replace_function(s, "fun notifyExternalWindowChangedFromAccessibility()", notify_new)
s = replace_function(s, "private fun recoverOverlayStackToFrontDebounced", debounce_new)
s = replace_function(s, "private fun recoverOverlayStackToFrontNow()", recover_now_new)
s = replace_function(s, "private fun aarishResetLiveReplayStateSafe", reset_new)
s = replace_function(s, "private fun aarishSetGlassGhostModeSafe", ghost_new)
s = replace_function(s, "private fun drainNextLiveReplaySafe()", drain_new)
s = replace_function(s, "fun triggerLiveReplaySafe", trigger_new)

FCS.write_text(s, encoding="utf-8")

print("✅ FINAL NO-FLICKER PATCH APPLIED")
print("Backup:", backup)
