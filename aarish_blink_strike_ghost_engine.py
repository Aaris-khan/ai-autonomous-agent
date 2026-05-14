from pathlib import Path
import re, shutil, time, subprocess, sys

ROOT = Path(".").resolve()
BASE = ROOT / "app/src/main/java/com/aarishkhan/aarishai"
AAS = BASE / "AutoActionService.kt"
FCS = BASE / "FloatingControlService.kt"

for p in (AAS, FCS):
    if not p.exists():
        print(f"❌ Missing: {p}")
        sys.exit(1)

STAMP = time.strftime("%Y%m%d_%H%M%S")
BACKUP = ROOT / f"_backup_before_ghost_blink_{STAMP}"
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
    mode = "code"
    quote = ""
    esc = False
    i = 0
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
            mode = "line"; i += 2; continue
        if ch == "/" and nxt == "*":
            mode = "block"; i += 2; continue
        if ch in ("'", '"'):
            mode = "str"; quote = ch; i += 1; continue
        if ch == "{":
            score += 1
        elif ch == "}":
            score -= 1
        i += 1
    return score

def function_ranges(code, name):
    pat = re.compile(
        r"(?m)^[ \t]*(?:private\s+|public\s+|internal\s+|protected\s+)?"
        r"(?:final\s+|inline\s+|suspend\s+)*fun\s+" + re.escape(name) + r"\s*\("
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
    removed = {}
    ranges = []
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

def insert_before_class_end(code, class_name, block):
    end = class_end(code, class_name)
    if end == -1:
        raise RuntimeError(f"class end not found: {class_name}")
    return code[:end].rstrip() + "\n\n" + block.rstrip() + "\n" + code[end:]

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

print("🚀 AarishAI Advanced Blink & Strike Ghost Engine")
print(f"💾 Backup: {BACKUP}")

aas = read(AAS)
fcs = read(FCS)

critical_markers = [
    "Function declaration must have a name",
    "safeNodeRead(false) { node?.isVisibleToUser == true }",
    "private fun safeVisible(node: AccessibilityNodeInfo?): Boolean = safeVisible(node)",
]
if any(x in aas + fcs for x in critical_markers):
    print("❌ Current Kotlin files already corrupted lag rahi hain.")
    print("👉 Pehle latest clean backup restore karo, phir ye script run karo.")
    print(f"💾 Safe backup created at: {BACKUP}")
    sys.exit(2)

# =========================================================
# AutoActionService: one clean bridge + one clean dispatch impl
# =========================================================
aas, removed_aas = remove_functions(aas, [
    "playSingleGestureLive",
    "playSingleLiveGestureSafe",
    "performLiveSystemActionSafe",
    "aarishBlinkStrikeDispatchGesture",
    "aarishBlinkStrikePlayGestureSafe",
])

aas_companion = r'''
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

if "fun isPlaying()" not in aas and "fun isPlaying():" not in aas:
    aas_companion += r'''

        fun isPlaying(): Boolean = false
'''

aas = insert_in_companion(aas, "AutoActionService", aas_companion)

aas_impl = r'''
    // AARISH_ADVANCED_BLINK_STRIKE_GHOST_ENGINE_V1
    // Floating glass remove/add nahi hota. FCS glass ko ghost banata hai,
    // yeh service same gesture ko behind-app par dispatch karti hai.
    private fun aarishBlinkStrikeDispatchGesture(gesture: RecordedGesture, onDone: () -> Unit) {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        var finished = false

        fun finishOnce() {
            if (finished) return
            finished = true
            main.post { onDone() }
        }

        try {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
                finishOnce()
                return
            }

            val raw = gesture.points
                .filter { p ->
                    !p.x.isNaN() && !p.x.isInfinite() &&
                    !p.y.isNaN() && !p.y.isInfinite()
                }
                .sortedBy { it.t.coerceAtLeast(0L) }

            if (raw.isEmpty()) {
                finishOnce()
                return
            }

            val first = raw.first()

            // System-action virtual points.
            if (first.x <= -50f) {
                val ok = when (first.x.toInt()) {
                    -100 -> performGlobalAction(GLOBAL_ACTION_BACK)
                    -200 -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                    else -> false
                }
                main.postDelayed({ finishOnce() }, if (ok) 180L else 0L)
                return
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

            // Tap gesture ko valid Stroke banane ke liye micro-line.
            if (!movement) {
                path.lineTo((startX + 1.5f).coerceIn(2f, maxX), (startY + 1.5f).coerceIn(2f, maxY))
            }

            val startT = raw.first().t.coerceAtLeast(0L)
            val endT = raw.last().t.coerceAtLeast(startT)
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

            // Callback miss safety.
            main.postDelayed({
                finishOnce()
            }, (duration + 1600L).coerceAtMost(610000L))
        } catch (_: Throwable) {
            finishOnce()
        }
    }
'''
aas = insert_before_class_end(aas, "AutoActionService", aas_impl)

# =========================================================
# FloatingControlService: remove duplicate fragile names, add one queue engine
# =========================================================
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
])

if "AARISH_ADVANCED_BLINK_STRIKE_FIELDS_V1" not in fcs:
    fields = r'''
    // AARISH_ADVANCED_BLINK_STRIKE_FIELDS_V1
    @Volatile private var liveReplayActive = false
    private var liveReplaySerial = 0
    private var lastLiveReplayAt = 0L
    private var lastLiveReplayX = Float.NaN
    private var lastLiveReplayY = Float.NaN
    private val liveReplayQueue = java.util.ArrayDeque<RecordedGesture>()
    private var liveReplayQueueDraining = false
'''
    m = re.search(r"(?m)^[ \t]*private\s+val\s+handler\s*=\s*Handler\(Looper\.getMainLooper\(\)\)", fcs)
    if m:
        eol = fcs.find("\n", m.end())
        fcs = fcs[:eol + 1] + fields + fcs[eol + 1:]
    else:
        fcs = insert_before_class_end(fcs, "FloatingControlService", fields)

fcs_impl = r'''
    // AARISH_ADVANCED_BLINK_STRIKE_GHOST_ENGINE_V1
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

        // Same callback duplicate block; real fast double-tap ko block nahi karta.
        if (now - lastLiveReplayAt < 28L && dx < 2f && dy < 2f) return true

        lastLiveReplayAt = now
        lastLiveReplayX = first.x
        lastLiveReplayY = first.y
        return false
    }

    private fun aarishSetGlassGhostModeSafe(ghost: Boolean): Boolean {
        if (!::windowManager.isInitialized) return false

        val glass = captureView ?: return false
        val params = glass.layoutParams as? WindowManager.LayoutParams ?: return false

        params.flags = if (ghost) {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }

        glass.alpha = 1f
        glass.setBackgroundColor(if (ghost) Color.TRANSPARENT else Color.argb(24, 0, 200, 0))

        return try {
            windowManager.updateViewLayout(glass, params)
            true
        } catch (_: Throwable) {
            false
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
                liveReplayQueue.clear()
                liveReplayQueueDraining = false
                liveReplayActive = false
                aarishSetGlassGhostModeSafe(false)
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
                liveReplayQueue.clear()
                liveReplayQueueDraining = false
                liveReplayActive = false
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
                        liveReplayQueue.clear()
                        liveReplayQueueDraining = false
                        liveReplayActive = false
                        aarishSetGlassGhostModeSafe(false)
                        return@postDelayed
                    }

                    if (liveReplayQueue.isNotEmpty()) {
                        drainNextLiveReplaySafe()
                    } else {
                        liveReplayQueueDraining = false
                    }
                }, 78L)
            }

            // Android ko FLAG_NOT_TOUCHABLE settle karne ke liye small delay.
            handler.postDelayed({
                if (serial != liveReplaySerial || instance !== this@FloatingControlService || !isRecording) {
                    finishAndContinue()
                    return@postDelayed
                }

                AutoActionService.playSingleLiveGestureSafe(gesture) {
                    handler.postDelayed({
                        finishAndContinue()
                    }, 55L)
                }
            }, 38L)

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

            if (liveReplayQueue.size >= 96) {
                liveReplayQueue.clear()
                liveReplayQueueDraining = false
                liveReplayActive = false
                aarishSetGlassGhostModeSafe(false)
                Toast.makeText(this, "⚠️ Input flood skip hua. Thoda dheere tap karo.", Toast.LENGTH_SHORT).show()
                return@post
            }

            liveReplayQueue.addLast(gesture)

            if (!liveReplayQueueDraining) {
                liveReplayQueueDraining = true

                val firstDelay = if (!localGestureHasRealMovement(gesture) && liveReplayDurationMs(gesture) <= 240L) {
                    155L
                } else {
                    38L
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
                }, 80L)
            }
        }, 45L)

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

# Wire safe trigger without changing saveCurrentGesture return type.
if "triggerLiveReplaySafe(newGesture)" not in fcs:
    fcs = fcs.replace(
        "recordedGestures.add(newGesture)",
        "recordedGestures.add(newGesture)\n\n        // Blink & Strike: glass remove/add nahi, sirf ghost mode live replay.\n        (context as? FloatingControlService)?.triggerLiveReplaySafe(newGesture)",
        1
    )

# Fix old unsafe calls if present.
fcs = fcs.replace("triggerLiveReplay(newGesture)", "triggerLiveReplaySafe(newGesture)")
fcs = fcs.replace("triggerLiveReplay(gesture)", "triggerLiveReplaySafe(gesture)")
fcs = fcs.replace("AutoActionService.playSingleGestureLive(", "AutoActionService.playSingleLiveGestureSafe(")

# Do NOT remove captureView during each ACTION_UP; STOP/SAVE/CUT can still remove it.
fcs = re.sub(
    r"(?s)(android\.view\.MotionEvent\.ACTION_UP\s*->\s*\{.{0,1200}?)safeRemoveView\(captureView\)\s*",
    r"\1// Blink & Strike: ACTION_UP par captureView remove nahi karna; flicker isi se aata hai.\n                ",
    fcs
)

write(AAS, aas)
write(FCS, fcs)

aas2 = read(AAS)
fcs2 = read(FCS)

checks = [
    ("AAS package", "package com.aarishkhan.aarishai" in aas2),
    ("FCS package", "package com.aarishkhan.aarishai" in fcs2),
    ("AAS class once", aas2.count("class AutoActionService") == 1),
    ("FCS class once", fcs2.count("class FloatingControlService") == 1),
    ("AAS live bridge", "fun playSingleLiveGestureSafe" in aas2),
    ("AAS live impl", "aarishBlinkStrikeDispatchGesture" in aas2),
    ("FCS trigger", "fun triggerLiveReplaySafe" in fcs2),
    ("FCS drain", "fun drainNextLiveReplaySafe" in fcs2),
    ("FCS ghost flag", "FLAG_NOT_TOUCHABLE" in fcs2),
    ("FCS newGesture hook", "triggerLiveReplaySafe(newGesture)" in fcs2),
    ("AAS brace sane", abs(brace_balance(aas2)) <= 1),
    ("FCS brace sane", abs(brace_balance(fcs2)) <= 1),
]

bad = [name for name, ok in checks if not ok]
if bad:
    print("❌ Static validation failed:")
    for b in bad:
        print(" -", b)
    print("↩️ Restoring backup...")
    shutil.copy2(BACKUP / AAS.name, AAS)
    shutil.copy2(BACKUP / FCS.name, FCS)
    sys.exit(3)

print("✅ Static validation passed.")

if (ROOT / "gradlew").exists():
    print("🧪 Running Gradle build...")
    run(["chmod", "+x", "./gradlew"])
    r = run(["./gradlew", "assembleDebug", "--no-daemon"])
    print(r.stdout[-8000:])
    if r.returncode != 0:
        print("❌ Build failed. Backup restore kar raha hoon.")
        shutil.copy2(BACKUP / AAS.name, AAS)
        shutil.copy2(BACKUP / FCS.name, FCS)
        sys.exit(4)
    print("✅ Build passed.")
else:
    print("⚠️ gradlew nahi mila. Static validation pass hai.")

print("✅ DONE: Advanced Blink & Strike Ghost Engine applied.")
print(f"💾 Backup saved at: {BACKUP}")
