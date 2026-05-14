import re, os, sys, shutil, time

FCS = "app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt"

if not os.path.exists(FCS):
    print("❌ FloatingControlService.kt nahi mila!")
    sys.exit(1)

stamp = time.strftime("%Y%m%d_%H%M%S")
backup = FCS + ".bak_chatgpt_fix_" + stamp
shutil.copy2(FCS, backup)

with open(FCS, "r", encoding="utf-8") as f:
    code = f.read()

def find_matching_brace(s, open_i):
    depth = 0; i = open_i; mode = "code"; quote = ""; esc = False
    while i < len(s):
        ch = s[i]; nxt = s[i + 1] if i + 1 < len(s) else ""
        if mode == "line":
            if ch == "\n": mode = "code"
            i += 1; continue
        if mode == "block":
            if ch == "*" and nxt == "/": mode = "code"; i += 2
            else: i += 1; continue
        if mode == "str":
            if esc: esc = False
            elif ch == "\\": esc = True
            elif ch == quote: mode = "code"
            i += 1; continue
        if ch == "/" and nxt == "/": mode = "line"; i += 2; continue
        if ch == "/" and nxt == "*": mode = "block"; i += 2; continue
        if ch in ("'", '"'): mode = "str"; quote = ch; i += 1; continue
        if ch == "{": depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0: return i
        i += 1
    return -1

def remove_func(c, name):
    pat = re.compile(r"(?m)^[ \t]*(?:private\s+|public\s+|internal\s+|protected\s+)?fun\s+" + re.escape(name) + r"\s*\(")
    while True:
        m = pat.search(c)
        if not m: break
        open_i = c.find("{", m.end())
        close_i = find_matching_brace(c, open_i)
        if close_i == -1: break
        c = c[:m.start()] + c[close_i+1:]
    return c

# ChatGPT ke purane/lapar-jhapar wale functions uda do
code = remove_func(code, "aarishSetGlassGhostModeSafe")
code = remove_func(code, "drainNextLiveReplaySafe")
code = remove_func(code, "triggerLiveReplaySafe")

# Hamara apna solid No-Blink + 550ms Idle Timer wala logic
new_logic = r'''
    private fun aarishSetGlassGhostModeSafe(ghost: Boolean): Boolean {
        if (!::windowManager.isInitialized) return false
        val glass = captureView ?: return false
        val params = glass.layoutParams as? android.view.WindowManager.LayoutParams ?: return false

        val newFlags = if (ghost) {
            params.flags or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }

        if (params.flags == newFlags && lastGhostState == ghost) return true
        params.flags = newFlags

        if (ghost) {
            glass.visibility = android.view.View.INVISIBLE
            glass.alpha = 0f
            glass.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            panelView?.visibility = android.view.View.INVISIBLE
        } else {
            glass.visibility = android.view.View.VISIBLE
            glass.alpha = 1f
            glass.setBackgroundColor(android.graphics.Color.argb(26, 0, 200, 0))
            panelView?.visibility = android.view.View.VISIBLE
        }

        return try {
            windowManager.updateViewLayout(glass, params)
            lastGhostState = ghost

            if (!ghost && instance === this@FloatingControlService && isRecording) {
                restorePanelUI()
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun drainNextLiveReplaySafe() {
        handler.post {
            if (instance !== this@FloatingControlService || !isRecording || AutoActionService.isPlaying()) {
                aarishResetLiveReplayStateSafe()
                return@post
            }

            val gesture = liveReplayQueue.pollFirst()
            if (gesture == null) {
                liveReplayQueueDraining = false
                liveReplayActive = false
                aarishSetGlassGhostModeSafe(false)
                return@post
            }

            val serial = liveReplaySerial + 1
            liveReplaySerial = serial
            liveReplayActive = true

            if (!aarishSetGlassGhostModeSafe(true)) {
                aarishResetLiveReplayStateSafe(forceSolid = false)
                return@post
            }

            val duration = liveReplayDurationMs(gesture)
            val watchdogMs = (duration + 4200L).coerceAtMost(610000L)
            var finished = false

            fun finishAndContinue() {
                if (finished) return
                finished = true

                if (liveReplayQueue.isNotEmpty()) {
                    handler.postDelayed({
                        if (instance !== this@FloatingControlService || !isRecording || AutoActionService.isPlaying()) {
                            aarishResetLiveReplayStateSafe()
                            return@postDelayed
                        }
                        drainNextLiveReplaySafe()
                    }, 35L)
                } else {
                    restoreLiveReplayGlassSafe(serial)
                    liveReplayQueueDraining = false
                }
            }

            val settleDelay = if (!localGestureHasRealMovement(gesture) && duration <= 240L) 40L else 30L

            handler.postDelayed({
                if (serial != liveReplaySerial || instance !== this@FloatingControlService || !isRecording) {
                    finishAndContinue()
                    return@postDelayed
                }

                semanticClickMuteUntil = android.os.SystemClock.uptimeMillis() + duration + 1400L

                AutoActionService.playSingleLiveGestureSafe(gesture) {
                    handler.postDelayed({ finishAndContinue() }, 50L)
                }
            }, settleDelay)

            handler.postDelayed({
                if (serial == liveReplaySerial && liveReplayActive) {
                    finishAndContinue()
                }
            }, watchdogMs)
        }
    }

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

            if (!liveReplayQueueDraining) {
                liveReplayQueueDraining = true

                val isShortTap = !localGestureHasRealMovement(gesture) && liveReplayDurationMs(gesture) <= 240L
                val drainDelay = if (isShortTap) 550L else 34L

                handler.postDelayed({
                    if (instance !== this@FloatingControlService || !isRecording || AutoActionService.isPlaying()) {
                        aarishResetLiveReplayStateSafe()
                        return@postDelayed
                    }

                    if (liveReplayQueue.isNotEmpty()) {
                        drainNextLiveReplaySafe()
                    } else {
                        liveReplayQueueDraining = false
                    }
                }, drainDelay)
            }
        }
    }
'''

# Naye code ko class me inject karna
m = re.search(r"\bclass\s+FloatingControlService\b", code)
if m:
    open_i = code.find("{", m.end())
    close_i = find_matching_brace(code, open_i)
    if close_i != -1:
        code = code[:close_i] + new_logic + "\n" + code[close_i:]

with open(FCS, "w", encoding="utf-8") as f:
    f.write(code)

print("✅ BHOOM! ChatGPT ka kachra saaf.")
print("✅ Original NO-BLINK & 550ms Guard Wapas Lag Gaya!")
