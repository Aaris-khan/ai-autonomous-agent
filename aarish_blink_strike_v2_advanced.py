from pathlib import Path
import re, shutil, time, subprocess, sys

ROOT = Path(".").resolve()
BASE = ROOT / "app/src/main/java/com/aarishkhan/aarishai"
AAS = BASE / "AutoActionService.kt"
FCS = BASE / "FloatingControlService.kt"

for p in (AAS, FCS):
    if not p.exists():
        print(f"❌ Missing: {p}")
        print("👉 Project root folder me run karo: cd ~/aarishai")
        sys.exit(1)

STAMP = time.strftime("%Y%m%d_%H%M%S")
BACKUP = ROOT / f"_backup_before_blink_v2_{STAMP}"
BACKUP.mkdir(parents=True, exist_ok=True)
shutil.copy2(AAS, BACKUP / AAS.name)
shutil.copy2(FCS, BACKUP / FCS.name)

def read(p):
    return p.read_text(encoding="utf-8", errors="replace")

def write(p, s):
    p.write_text(s, encoding="utf-8")

def run(cmd):
    return subprocess.run(cmd, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)

def find_matching_brace(s, open_i):
    depth = 0
    i = open_i
    mode = "code"
    quote = ""
    esc = False

    while i < len(s):
        ch = s[i]
        nxt = s[i + 1] if i + 1 < len(s) else ""

        if mode == "line":
            if ch == "\n":
                mode = "code"
            i += 1
            continue

        if mode == "block":
            if ch == "*" and nxt == "/":
                mode = "code"
                i += 2
            else:
                i += 1
            continue

        if mode == "str":
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == quote:
                mode = "code"
            i += 1
            continue

        if ch == "/" and nxt == "/":
            mode = "line"
            i += 2
            continue
        if ch == "/" and nxt == "*":
            mode = "block"
            i += 2
            continue
        if ch in ("'", '"'):
            mode = "str"
            quote = ch
            i += 1
            continue

        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1

    return -1

def brace_balance(s):
    score = 0
    i = 0
    mode = "code"
    quote = ""
    esc = False

    while i < len(s):
        ch = s[i]
        nxt = s[i + 1] if i + 1 < len(s) else ""

        if mode == "line":
            if ch == "\n":
                mode = "code"
            i += 1
            continue

        if mode == "block":
            if ch == "*" and nxt == "/":
                mode = "code"
                i += 2
            else:
                i += 1
            continue

        if mode == "str":
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == quote:
                mode = "code"
            i += 1
            continue

        if ch == "/" and nxt == "/":
            mode = "line"
            i += 2
            continue
        if ch == "/" and nxt == "*":
            mode = "block"
            i += 2
            continue
        if ch in ("'", '"'):
            mode = "str"
            quote = ch
            i += 1
            continue

        if ch == "{":
            score += 1
        elif ch == "}":
            score -= 1
        i += 1

    return score

def function_ranges(code, name):
    pat = re.compile(
        r"(?m)^[ \t]*(?:private\s+|public\s+|internal\s+|protected\s+)?"
        r"(?:final\s+|inline\s+|suspend\s+|operator\s+)*fun\s+"
        + re.escape(name) + r"\s*\("
    )
    out = []

    for m in pat.finditer(code):
        open_i = code.find("{", m.end())
        if open_i == -1:
            continue
        close_i = find_matching_brace(code, open_i)
        if close_i == -1:
            continue
        end = close_i + 1
        while end < len(code) and code[end] in " \t\r\n":
            end += 1
        out.append((m.start(), end))

    return out

def remove_functions(code, names):
    ranges = []
    removed = {}
    for name in names:
        rs = function_ranges(code, name)
        removed[name] = len(rs)
        ranges.extend(rs)

    ranges.sort(reverse=True)
    for a, b in ranges:
        code = code[:a] + code[b:]

    return code, removed

def class_end(code, class_name):
    m = re.search(r"\bclass\s+" + re.escape(class_name) + r"\b", code)
    if not m:
        return -1
    open_i = code.find("{", m.end())
    if open_i == -1:
        return -1
    return find_matching_brace(code, open_i)

def companion_range(code):
    m = re.search(r"\bcompanion\s+object\b", code)
    if not m:
        return None
    open_i = code.find("{", m.end())
    if open_i == -1:
        return None
    close_i = find_matching_brace(code, open_i)
    if close_i == -1:
        return None
    return open_i, close_i

def insert_in_companion(code, class_name, block):
    rg = companion_range(code)
    if rg:
        open_i, _ = rg
        return code[:open_i + 1] + "\n" + block.rstrip() + "\n" + code[open_i + 1:]

    end = class_end(code, class_name)
    if end == -1:
        raise RuntimeError(f"class end not found: {class_name}")

    comp = "\n    companion object {\n" + block.rstrip() + "\n    }\n"
    return code[:end] + comp + code[end:]

def insert_before_class_end(code, class_name, block):
    end = class_end(code, class_name)
    if end == -1:
        raise RuntimeError(f"class end not found: {class_name}")
    return code[:end].rstrip() + "\n\n" + block.rstrip() + "\n" + code[end:]

def ensure_import(code, imp):
    if imp in code:
        return code
    m = re.search(r"(?m)^package\s+[^\n]+\n", code)
    if not m:
        return code
    return code[:m.end()] + imp + "\n" + code[m.end():]

def restore_and_exit(code):
    print("↩️ Backup restore ho raha hai...")
    shutil.copy2(BACKUP / AAS.name, AAS)
    shutil.copy2(BACKUP / FCS.name, FCS)
    sys.exit(code)

print("🚀 AarishAI Blink & Strike Ghost Engine V2 Advanced")
print(f"💾 Backup: {BACKUP}")

aas = read(AAS)
fcs = read(FCS)

bad_markers = [
    "Function declaration must have a name",
    "private fun safeVisible(node: AccessibilityNodeInfo?): Boolean = safeVisible(node)",
    "safeNodeRead(false) { node?.isVisibleToUser == true }",
]
if any(x in aas + fcs for x in bad_markers):
    print("❌ Kotlin files already corrupted lag rahi hain. Pehle clean backup restore karo.")
    restore_and_exit(2)

if "class AutoActionService" not in aas or "class FloatingControlService" not in fcs:
    print("❌ Required service classes nahi mili.")
    restore_and_exit(2)

# =========================================================
# AutoActionService V2 bridge + dispatch
# =========================================================
aas, removed_aas = remove_functions(aas, [
    "playSingleGestureLive",
    "playSingleLiveGestureSafe",
    "performLiveSystemActionSafe",
    "aarishBlinkStrikeDispatchGesture",
    "aarishBlinkStrikePlayGestureSafe",
])

aas_bridge = r'''
        // AARISH_BLINK_STRIKE_AAS_BRIDGE_V2
        fun playSingleLiveGestureSafe(gesture: RecordedGesture, onDone: () -> Unit) {
            val svc = instance
            if (svc == null) {
                onDone()
                return
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (instance !== svc) {
                    onDone()
                } else {
                    svc.aarishBlinkStrikeDispatchGesture(gesture, onDone)
                }
            }
        }

        fun performLiveSystemActionSafe(actionType: Int, onDone: () -> Unit) {
            val svc = instance
            if (svc == null) {
                onDone()
                return
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                val ok = try {
                    when (actionType) {
                        1 -> svc.performGlobalAction(GLOBAL_ACTION_BACK)
                        2 -> svc.performGlobalAction(GLOBAL_ACTION_RECENTS)
                        else -> false
                    }
                } catch (_: Throwable) {
                    false
                }

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    onDone()
                }, if (ok) 180L else 0L)
            }
        }
'''

if not re.search(r"\bfun\s+isPlaying\s*\(", aas):
    aas_bridge += r'''

        fun isPlaying(): Boolean = instance?.isPlayingInternal == true
'''

aas = insert_in_companion(aas, "AutoActionService", aas_bridge)

aas_impl = r'''
    // AARISH_ADVANCED_BLINK_STRIKE_GHOST_ENGINE_V2
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
                raw0.filterIndexed { index, _ -> index == 0 || index == raw0.lastIndex || index % step == 0 }
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

            val desc = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(
                    android.accessibilityservice.GestureDescription.StrokeDescription(
                        path,
                        0L,
                        duration
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
            }, (duration + 1650L).coerceAtMost(610000L))
        } catch (_: Throwable) {
            finishOnce()
        }
    }
'''

aas = insert_before_class_end(aas, "AutoActionService", aas_impl)

# =========================================================
# FloatingControlService V2 ghost queue + cleanup
# =========================================================
fcs = ensure_import(fcs, "import android.view.WindowManager")
fcs = ensure_import(fcs, "import android.widget.Toast")

fcs, removed_fcs = remove_functions(fcs, [
    "setGlassTouchable",
    "triggerLiveReplay",
    "triggerLiveReplaySafe",
    "triggerLiveSystemActionSafe",
    "liveReplayDurationMs",
    "isLiveReplayBlockedDuplicate",
    "restoreLiveReplayGlassSafe",
    "drainNextLiveReplaySafe",
    "aarishSetGlassGhostModeSafe",
    "aarishResetLiveReplayStateSafe",
    "localGestureHasRealMovement",
])

# Remove old field lines safely.
fcs = re.sub(r"(?m)^[ \t]*// AARISH_ADVANCED_BLINK_STRIKE_FIELDS_V[0-9]+.*\n", "", fcs)
for nm in [
    "liveReplayActive",
    "liveReplaySerial",
    "lastLiveReplayAt",
    "lastLiveReplayX",
    "lastLiveReplayY",
    "liveReplayQueue",
    "liveReplayQueueDraining",
    "liveReplayFloodToastAt",
    "lastGhostState",
]:
    fcs = re.sub(
        r"(?m)^[ \t]*(?:@Volatile\s+)?private\s+(?:val|var)\s+" + re.escape(nm) + r"\b.*\n",
        "",
        fcs
    )

fields = r'''
    // AARISH_ADVANCED_BLINK_STRIKE_FIELDS_V2
    @Volatile private var liveReplayActive = false
    private var liveReplaySerial = 0
    private var lastLiveReplayAt = 0L
    private var lastLiveReplayX = Float.NaN
    private var lastLiveReplayY = Float.NaN
    private val liveReplayQueue = java.util.ArrayDeque<RecordedGesture>()
    private var liveReplayQueueDraining = false
    private var liveReplayFloodToastAt = 0L
    private var lastGhostState: Boolean? = null
'''

m = re.search(r"(?m)^([ \t]*private\s+val\s+handler\s*=\s*Handler\(Looper\.getMainLooper\(\)\).*)$", fcs)
if m:
    fcs = fcs[:m.end()] + "\n" + fields.rstrip() + fcs[m.end():]
else:
    fcs = insert_before_class_end(fcs, "FloatingControlService", fields)

fcs_impl = r'''
    // AARISH_ADVANCED_BLINK_STRIKE_GHOST_ENGINE_V2
    private fun localGestureHasRealMovement(gesture: RecordedGesture): Boolean {
        val pts = gesture.points
        if (pts.size < 2) return false
        val first = pts.first()
        return pts.any { p ->
            kotlin.math.abs(p.x - first.x) > 7f || kotlin.math.abs(p.y - first.y) > 7f
        }
    }

    private fun liveReplayDurationMs(gesture: RecordedGesture): Long {
        val points = gesture.points
            .filter { !it.x.isNaN() && !it.x.isInfinite() && !it.y.isNaN() && !it.y.isInfinite() }
            .sortedBy { it.t.coerceAtLeast(0L) }

        if (points.isEmpty()) return 80L

        val start = points.first().t.coerceAtLeast(0L)
        val end = points.last().t.coerceAtLeast(start)
        return (end - start).coerceIn(55L, 600000L)
    }

    private fun isLiveReplayBlockedDuplicate(gesture: RecordedGesture): Boolean {
        val first = gesture.points.firstOrNull() ?: return true
        val now = android.os.SystemClock.uptimeMillis()

        val dx = if (lastLiveReplayX.isNaN()) 99999f else kotlin.math.abs(first.x - lastLiveReplayX)
        val dy = if (lastLiveReplayY.isNaN()) 99999f else kotlin.math.abs(first.y - lastLiveReplayY)

        if (now - lastLiveReplayAt < 24L && dx < 2f && dy < 2f) return true

        lastLiveReplayAt = now
        lastLiveReplayX = first.x
        lastLiveReplayY = first.y
        return false
    }

    private fun aarishSetGlassGhostModeSafe(ghost: Boolean): Boolean {
        if (!::windowManager.isInitialized) return false

        val glass = captureView ?: return false
        val params = glass.layoutParams as? WindowManager.LayoutParams ?: return false

        val newFlags = if (ghost) {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }

        if (params.flags == newFlags && lastGhostState == ghost) return true

        params.flags = newFlags

        return try {
            windowManager.updateViewLayout(glass, params)
            lastGhostState = ghost
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun aarishResetLiveReplayStateSafe(forceSolid: Boolean = true) {
        liveReplaySerial++
        liveReplayActive = false
        liveReplayQueue.clear()
        liveReplayQueueDraining = false
        if (forceSolid) {
            aarishSetGlassGhostModeSafe(false)
        }
    }

    private fun restoreLiveReplayGlassSafe(serial: Int) {
        handler.post {
            if (serial != liveReplaySerial) return@post
            liveReplayActive = false
            if (instance !== this@FloatingControlService) return@post
            if (!isRecording) return@post
            aarishSetGlassGhostModeSafe(false)
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

                restoreLiveReplayGlassSafe(serial)

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
                }, 62L)
            }

            val settleDelay = if (!localGestureHasRealMovement(gesture) && duration <= 240L) 42L else 34L

            handler.postDelayed({
                if (serial != liveReplaySerial || instance !== this@FloatingControlService || !isRecording) {
                    finishAndContinue()
                    return@postDelayed
                }

                AutoActionService.playSingleLiveGestureSafe(gesture) {
                    handler.postDelayed({
                        finishAndContinue()
                    }, 50L)
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
        if (firstX <= -50f) return

        if (isLiveReplayBlockedDuplicate(gesture)) return

        handler.post {
            if (instance !== this@FloatingControlService || !isRecording || AutoActionService.isPlaying()) return@post

            if (liveReplayQueue.size >= 32) {
                while (liveReplayQueue.size > 12) {
                    liveReplayQueue.pollFirst()
                }

                val now = android.os.SystemClock.uptimeMillis()
                if (now - liveReplayFloodToastAt > 1600L) {
                    liveReplayFloodToastAt = now
                    Toast.makeText(this, "⚠️ Input flood trim hua. Latest actions safe hain.", Toast.LENGTH_SHORT).show()
                }
            }

            liveReplayQueue.addLast(gesture)

            if (!liveReplayQueueDraining) {
                liveReplayQueueDraining = true

                val firstDelay = if (!localGestureHasRealMovement(gesture) && liveReplayDurationMs(gesture) <= 240L) {
                    135L
                } else {
                    34L
                }

                handler.postDelayed({
                    drainNextLiveReplaySafe()
                }, firstDelay)
            }
        }
    }

    private fun triggerLiveSystemActionSafe(actionType: Int) {
        if (instance !== this@FloatingControlService) return
        if (!isRecording || AutoActionService.isPlaying()) return

        val serial = liveReplaySerial + 1
        liveReplaySerial = serial
        liveReplayActive = true

        if (!aarishSetGlassGhostModeSafe(true)) return

        handler.postDelayed({
            if (serial != liveReplaySerial || instance !== this@FloatingControlService || !isRecording) {
                restoreLiveReplayGlassSafe(serial)
                return@postDelayed
            }

            AutoActionService.performLiveSystemActionSafe(actionType) {
                handler.postDelayed({
                    restoreLiveReplayGlassSafe(serial)
                }, 70L)
            }
        }, 40L)

        handler.postDelayed({
            if (serial == liveReplaySerial && liveReplayActive) {
                restoreLiveReplayGlassSafe(serial)
            }
        }, 2400L)
    }
'''

anchor = re.search(r"(?m)^[ \t]*private\s+fun\s+showSystemActionRecorderDialog\s*\(", fcs)
if anchor:
    fcs = fcs[:anchor.start()].rstrip() + "\n\n" + fcs_impl.rstrip() + "\n\n" + fcs[anchor.start():]
else:
    fcs = insert_before_class_end(fcs, "FloatingControlService", fcs_impl)

# Hook live replay after saved gesture.
if "triggerLiveReplaySafe(newGesture)" not in fcs:
    m = re.search(r"recordedGestures\.add\((\w+)\)", fcs)
    if m:
        var_name = m.group(1)
        old = f"recordedGestures.add({var_name})"
        new = (
            f"recordedGestures.add({var_name})\n\n"
            f"        // Blink & Strike V2: glass remove/add nahi, sirf ghost mode live replay.\n"
            f"        (context as? FloatingControlService)?.triggerLiveReplaySafe({var_name})"
        )
        fcs = fcs.replace(old, new, 1)

# Old unsafe names cleanup.
fcs = fcs.replace("triggerLiveReplay(newGesture)", "triggerLiveReplaySafe(newGesture)")
fcs = fcs.replace("triggerLiveReplay(gesture)", "triggerLiveReplaySafe(gesture)")
fcs = fcs.replace("AutoActionService.playSingleGestureLive(", "AutoActionService.playSingleLiveGestureSafe(")

# ACTION_UP par captureView remove nahi hona chahiye.
fcs = re.sub(
    r"(?s)(android\.view\.MotionEvent\.ACTION_UP\s*->\s*\{.{0,1400}?)safeRemoveView\(captureView\)\s*",
    r"\1// Blink & Strike V2: ACTION_UP par captureView remove nahi karna; flicker isi se aata hai.\n                ",
    fcs
)

# Lifecycle cleanup hooks.
if "AARISH_V2_REMOVEVIEW_CLEANUP" not in fcs:
    fcs = re.sub(
        r"(?m)^([ \t]*)safeRemoveView\((captureView|liveView)\)",
        r"\1// AARISH_V2_REMOVEVIEW_CLEANUP\n\1aarishResetLiveReplayStateSafe()\n\1safeRemoveView(\2)",
        fcs
    )

if "AARISH_V2_HANDLER_CLEANUP" not in fcs:
    fcs = re.sub(
        r"(?m)^([ \t]*)handler\.removeCallbacksAndMessages\(null\)",
        r"\1// AARISH_V2_HANDLER_CLEANUP\n\1aarishResetLiveReplayStateSafe()\n\1handler.removeCallbacksAndMessages(null)",
        fcs,
        count=1
    )

# De-duplicate accidental cleanup.
fcs = fcs.replace("aarishResetLiveReplayStateSafe()\n        aarishResetLiveReplayStateSafe()", "aarishResetLiveReplayStateSafe()")
fcs = fcs.replace("aarishResetLiveReplayStateSafe()\n            aarishResetLiveReplayStateSafe()", "aarishResetLiveReplayStateSafe()")

write(AAS, aas)
write(FCS, fcs)

aas2 = read(AAS)
fcs2 = read(FCS)

checks = [
    ("AAS package", "package com.aarishkhan.aarishai" in aas2),
    ("FCS package", "package com.aarishkhan.aarishai" in fcs2),
    ("AAS class once", aas2.count("class AutoActionService") == 1),
    ("FCS class once", fcs2.count("class FloatingControlService") == 1),
    ("AAS V2 bridge", "AARISH_BLINK_STRIKE_AAS_BRIDGE_V2" in aas2),
    ("AAS V2 impl", "AARISH_ADVANCED_BLINK_STRIKE_GHOST_ENGINE_V2" in aas2),
    ("FCS V2 fields", "AARISH_ADVANCED_BLINK_STRIKE_FIELDS_V2" in fcs2),
    ("FCS V2 trigger", "fun triggerLiveReplaySafe" in fcs2),
    ("FCS V2 reset", "fun aarishResetLiveReplayStateSafe" in fcs2),
    ("FCS ghost flag", "FLAG_NOT_TOUCHABLE" in fcs2),
    ("FCS hook", "triggerLiveReplaySafe(" in fcs2),
    ("AAS brace balanced", brace_balance(aas2) == 0),
    ("FCS brace balanced", brace_balance(fcs2) == 0),
]

bad = [name for name, ok in checks if not ok]
if bad:
    print("❌ Static validation failed:")
    for b in bad:
        print(" -", b)
    restore_and_exit(3)

print("✅ Static validation passed.")

gradlew = ROOT / "gradlew"
if gradlew.exists():
    print("🧪 Running Gradle build...")
    run(["chmod", "+x", "./gradlew"])
    r = run(["./gradlew", "assembleDebug", "--no-daemon"])
    print(r.stdout[-8000:])
    if r.returncode != 0:
        print("❌ Build failed. Backup restore kar raha hoon.")
        restore_and_exit(4)
    print("✅ Build passed.")
else:
    print("⚠️ gradlew nahi mila. Static validation pass hai, build skip hua.")

print("✅ DONE: Blink & Strike Ghost Engine V2 Advanced applied.")
print(f"💾 Backup saved at: {BACKUP}")
