#!/usr/bin/env python3
from pathlib import Path
import re, shutil, time, sys

ROOT = Path(".").resolve()
SRC = ROOT / "app/src/main/java/com/aarishkhan/aarishai"
FCS = SRC / "FloatingControlService.kt"
AUTO = SRC / "AutoActionService.kt"
GS = SRC / "GestureStore.kt"

STAMP = time.strftime("%Y%m%d_%H%M%S")
BAK = ROOT / f"_snake_engine_backup_{STAMP}"
BAK.mkdir(parents=True, exist_ok=True)

def die(msg):
    print("❌ " + msg)
    sys.exit(1)

def read(p):
    return p.read_text(encoding="utf-8")

def write(p, s):
    p.write_text(s, encoding="utf-8")

def backup(p):
    if p.exists():
        dst = BAK / p.name
        shutil.copy2(p, dst)

def add_imports(s, imports):
    lines = s.splitlines()
    existing = set()
    last_import = -1
    package_i = -1
    for i, line in enumerate(lines):
        if line.startswith("package "):
            package_i = i
        if line.startswith("import "):
            existing.add(line.strip())
            last_import = i
    missing = [f"import {x}" for x in imports if f"import {x}" not in existing]
    if not missing:
        return s
    idx = last_import + 1 if last_import >= 0 else package_i + 1
    lines[idx:idx] = missing
    return "\n".join(lines) + ("\n" if s.endswith("\n") else "")

def remove_block_by_markers(s, start_marker, end_marker):
    while start_marker in s and end_marker in s:
        a = s.find(start_marker)
        b = s.find(end_marker, a)
        if a == -1 or b == -1:
            break
        b += len(end_marker)
        s = s[:a] + s[b:]
    return s

def insert_after_class_open(s, class_name, code, sentinel):
    if sentinel in s:
        return s
    m = re.search(rf"(class\s+{re.escape(class_name)}\b[^\{{]*\{{)", s)
    if not m:
        die(f"{class_name} class open nahi mila")
    return s[:m.end()] + "\n" + code.rstrip() + "\n" + s[m.end():]

def insert_before_function(s, func_name, code, sentinel):
    if sentinel in s:
        return s
    m = re.search(rf"\n\s*(private\s+|override\s+|fun\s+)?fun\s+{re.escape(func_name)}\s*\(", s)
    if not m:
        m = re.search(r"\n\s*override\s+fun\s+onDestroy\s*\(", s)
    if not m:
        die(f"{func_name} anchor nahi mila")
    return s[:m.start()] + "\n" + code.rstrip() + "\n" + s[m.start():]

def patch_touch_listener(s):
    if "snakeEngineHandleMotionEvent(event)" in s:
        return s

    patterns = [
        r"(setOnTouchListener\s*\{\s*view\s*,\s*event\s*->)",
        r"(setOnTouchListener\s*\{\s*v\s*,\s*event\s*->)",
        r"(setOnTouchListener\s*\{\s*_\s*,\s*event\s*->)",
        r"(setOnTouchListener\s*\{\s*[^,\n]+,\s*event\s*->)",
        r"(setOnTouchListener\s*\{\s*[^,\n]+,\s*motionEvent\s*->)",
        r"(setOnTouchListener\s*\{\s*[^,\n]+,\s*e\s*->)",
    ]

    for pat in patterns:
        m = re.search(pat, s)
        if m:
            whole = m.group(1)
            event_var = "event"
            if "motionEvent" in whole:
                event_var = "motionEvent"
            elif re.search(r",\s*e\s*->", whole):
                event_var = "e"
            inject = whole + f"\n            snakeEngineHandleMotionEvent({event_var})"
            return s[:m.start(1)] + inject + s[m.end(1):]

    die("capture touch listener nahi mila; manual scan needed")
    return s

def cleanup_bad_supreme_injections(s):
    s = remove_block_by_markers(s, "// ║  SUPREME GESTURE ENGINE", "// ── END GESTURE ENGINE")
    s = remove_block_by_markers(s, "// ║  AI SMART CLICKER v3", "// ── END AI SMART CLICKER")
    s = re.sub(r"(?s)\n\s*private val gestureDetector by lazy \{.*?\n\s*// ── END GESTURE ENGINE.*?\n", "\n", s)
    s = re.sub(r"(?m)^\s*gestureDetector\.onTouchEvent\([^)]+\)\s*\n", "", s)
    s = re.sub(r"(?m)^\s*autoActionService\?\..*\n", "", s)
    s = re.sub(r"(?m)^\s*gestureStore\?\..*\n", "", s)
    return s

for p in [FCS, AUTO, GS]:
    if not p.exists():
        die(f"Missing file: {p}")
    backup(p)

print(f"📦 Backup: {BAK}")

f = read(FCS)
f = cleanup_bad_supreme_injections(f)

f = add_imports(f, [
    "android.graphics.Canvas",
    "android.graphics.Color",
    "android.graphics.Paint",
    "android.graphics.Path",
    "android.graphics.PixelFormat",
    "android.graphics.RectF",
    "android.os.Build",
    "android.os.SystemClock",
    "android.view.Choreographer",
    "android.view.MotionEvent",
    "android.view.View",
    "android.view.WindowManager",
    "kotlin.math.abs",
    "kotlin.math.max",
    "kotlin.math.min",
    "kotlin.math.sqrt",
])

SNAKE_FIELDS = r'''
    // ═══════════════════════════════════════════════════════════════
    // FLAWLESS SNAKE ENGINE STATE
    // zero-touch-interference + vsync laser visualizer
    // ═══════════════════════════════════════════════════════════════
    private var snakeLaserView: SnakeLaserTrailView? = null
    private var snakeLaserParams: WindowManager.LayoutParams? = null
    private var snakeLastEventAt = 0L
'''

f = insert_after_class_open(
    f,
    "FloatingControlService",
    SNAKE_FIELDS,
    "FLAWLESS SNAKE ENGINE STATE"
)

SNAKE_CODE = r'''
    // ═══════════════════════════════════════════════════════════════
    // FLAWLESS SNAKE LASER TRAIL ENGINE
    // FLAG_NOT_TOUCHABLE means this overlay never consumes MotionEvent.
    // Recording touch listener only mirrors coordinates into this view.
    // ═══════════════════════════════════════════════════════════════
    private inner class SnakeLaserTrailView(context: android.content.Context) :
        View(context),
        Choreographer.FrameCallback {

        private val path = Path()
        private val dirty = RectF()
        private val lastDirty = RectF()

        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 18f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.argb(115, 0, 255, 170)
        }

        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.WHITE
        }

        private var active = false
        private var framePosted = false
        private var hasPoint = false
        private var lastX = 0f
        private var lastY = 0f
        private var lastT = 0L
        private var speed = 0f

        init {
            setWillNotDraw(false)
            isClickable = false
            isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }

        fun onMirrorTouch(event: MotionEvent) {
            val x = event.rawX
            val y = event.rawY
            val now = SystemClock.uptimeMillis()

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    clearNow()
                    active = true
                    hasPoint = true
                    lastX = x
                    lastY = y
                    lastT = now
                    speed = 0f
                    path.moveTo(x, y)
                    markDirty(x, y, 36f)
                    postFrame()
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!active || !hasPoint) {
                        active = true
                        hasPoint = true
                        lastX = x
                        lastY = y
                        lastT = now
                        path.moveTo(x, y)
                        markDirty(x, y, 36f)
                        postFrame()
                        return
                    }

                    val dx = x - lastX
                    val dy = y - lastY
                    if (abs(dx) < 0.7f && abs(dy) < 0.7f) return

                    val dt = (now - lastT).coerceAtLeast(1L)
                    speed = (sqrt((dx * dx + dy * dy).toDouble()) / dt).toFloat()

                    val midX = (lastX + x) * 0.5f
                    val midY = (lastY + y) * 0.5f
                    path.quadTo(lastX, lastY, midX, midY)

                    markDirty(lastX, lastY, 42f)
                    markDirty(x, y, 42f)

                    lastX = x
                    lastY = y
                    lastT = now
                    postFrame()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    clearNow()
                }
            }
        }

        private fun markDirty(x: Float, y: Float, pad: Float) {
            val l = x - pad
            val t = y - pad
            val r = x + pad
            val b = y + pad

            if (dirty.isEmpty) dirty.set(l, t, r, b)
            else dirty.union(l, t, r, b)
        }

        private fun speedColor(): Int {
            val v = (speed / 3.0f).coerceIn(0f, 1f)
            val r = (0 + 255 * v).toInt().coerceIn(0, 255)
            val g = (255 - 130 * v).toInt().coerceIn(60, 255)
            val b = (190 - 160 * v).toInt().coerceIn(30, 255)
            return Color.rgb(r, g, b)
        }

        private fun postFrame() {
            if (framePosted) return
            framePosted = true
            Choreographer.getInstance().postFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            framePosted = false
            if (!active || path.isEmpty) return

            val rect = RectF()
            if (!dirty.isEmpty) rect.union(dirty)
            if (!lastDirty.isEmpty) rect.union(lastDirty)

            if (!rect.isEmpty) {
                val l = rect.left.toInt().coerceAtLeast(0)
                val t = rect.top.toInt().coerceAtLeast(0)
                val r = rect.right.toInt().coerceAtMost(width)
                val b = rect.bottom.toInt().coerceAtMost(height)
                if (r > l && b > t) invalidate(l, t, r, b) else invalidate()
            } else {
                invalidate()
            }

            lastDirty.set(dirty)
            dirty.setEmpty()
        }

        override fun onDraw(canvas: Canvas) {
            if (path.isEmpty) return

            val c = speedColor()

            glowPaint.color = c
            glowPaint.alpha = 90
            glowPaint.strokeWidth = 18f
            canvas.drawPath(path, glowPaint)

            glowPaint.alpha = 150
            glowPaint.strokeWidth = 10f
            canvas.drawPath(path, glowPaint)

            corePaint.alpha = 245
            canvas.drawPath(path, corePaint)
        }

        fun clearNow() {
            active = false
            hasPoint = false
            speed = 0f
            path.reset()
            dirty.set(0f, 0f, width.toFloat(), height.toFloat())
            lastDirty.set(dirty)
            invalidate()
            if (framePosted) {
                Choreographer.getInstance().removeFrameCallback(this)
                framePosted = false
            }
        }

        fun destroySnake() {
            clearNow()
            try {
                Choreographer.getInstance().removeFrameCallback(this)
            } catch (_: Exception) {
            }
        }
    }

    private fun ensureSnakeLaserOverlay() {
        if (snakeLaserView != null) return

        val overlayType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE

        val flags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = 0
            y = 0
            alpha = 1f
        }

        try {
            val view = SnakeLaserTrailView(this)
            snakeLaserView = view
            snakeLaserParams = lp
            windowManager.addView(view, lp)
        } catch (_: Exception) {
            snakeLaserView = null
            snakeLaserParams = null
        }
    }

    private fun snakeEngineHandleMotionEvent(event: MotionEvent) {
        snakeLastEventAt = SystemClock.uptimeMillis()
        ensureSnakeLaserOverlay()
        snakeLaserView?.onMirrorTouch(event)
    }

    private fun snakeEngineClearNow() {
        snakeLaserView?.clearNow()
    }

    private fun snakeEngineDestroy() {
        val v = snakeLaserView
        snakeLaserView = null
        snakeLaserParams = null
        try {
            v?.destroySnake()
        } catch (_: Exception) {
        }
        try {
            if (v != null) windowManager.removeView(v)
        } catch (_: Exception) {
        }
    }
'''

f = insert_before_function(
    f,
    "safeAddView",
    SNAKE_CODE,
    "FLAWLESS SNAKE LASER TRAIL ENGINE"
)

f = patch_touch_listener(f)

f = re.sub(
    r"(MotionEvent\.ACTION_UP\s*,\s*\n\s*MotionEvent\.ACTION_CANCEL\s*->\s*\{)",
    r"\1\n                    snakeEngineClearNow()",
    f,
    count=1
)

f = re.sub(
    r"(android\.view\.MotionEvent\.ACTION_UP\s*,\s*\n\s*android\.view\.MotionEvent\.ACTION_CANCEL\s*->\s*\{)",
    r"\1\n                    snakeEngineClearNow()",
    f,
    count=1
)

if "snakeEngineDestroy()" not in f:
    f = re.sub(
        r"(override\s+fun\s+onDestroy\s*\(\)\s*\{)",
        r"\1\n        snakeEngineDestroy()",
        f,
        count=1
    )
else:
    if not re.search(r"override\s+fun\s+onDestroy\s*\(\)\s*\{[^}]*snakeEngineDestroy\(\)", f, re.S):
        f = re.sub(
            r"(override\s+fun\s+onDestroy\s*\(\)\s*\{)",
            r"\1\n        snakeEngineDestroy()",
            f,
            count=1
        )

f = re.sub(
    r"(WindowManager\.LayoutParams\.FLAG_NOT_FOCUSABLE\b)(?!\s*\n?\s*or\s+WindowManager\.LayoutParams\.FLAG_NOT_TOUCH_MODAL)",
    r"\1\n                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL",
    f,
    count=1
)

f = re.sub(
    r"(?m)^\s*postDelayed\(\s*\{\s*trailPath\.reset\(\).*?\}\s*,\s*TRAIL_DURATION_MS\s*\)\s*\n?",
    "",
    f
)

f = f.replace(
    "trailPath.reset(); points.clear(); invalidate()",
    "snakeEngineClearNow()"
)

write(FCS, f)

a = read(AUTO)

if "private fun snakeSynergyGuardActive" not in a:
    SYNERGY = r'''
    // ═══════════════════════════════════════════════════════════════
    // SNAKE SYNERGY GUARD
    // Prevents replay callbacks from hanging wrong windows / share sheets.
    // ═══════════════════════════════════════════════════════════════
    private var snakeSynergyBlockedUntil = 0L

    private fun snakeSynergyGuardActive(): Boolean {
        return android.os.SystemClock.uptimeMillis() < snakeSynergyBlockedUntil
    }

    private fun snakeMarkTransientSystemUiGuard(event: android.view.accessibility.AccessibilityEvent?) {
        val cls = event?.className?.toString().orEmpty()
        val pkg = event?.packageName?.toString().orEmpty()
        val hit = listOf(
            "ChooserActivity",
            "ResolverActivity",
            "IntentResolver",
            "ShareSheet",
            "ShareActivity"
        ).any { cls.contains(it, true) || pkg.contains(it, true) }

        if (hit) snakeSynergyBlockedUntil = android.os.SystemClock.uptimeMillis() + 1600L
    }
'''
    a = re.sub(
        r"(class\s+AutoActionService\b[^{]*\{)",
        r"\1\n" + SYNERGY,
        a,
        count=1
    )

if "snakeMarkTransientSystemUiGuard(event)" not in a:
    a = a.replace(
        "override fun onAccessibilityEvent(event: AccessibilityEvent?) {",
        "override fun onAccessibilityEvent(event: AccessibilityEvent?) {\n        snakeMarkTransientSystemUiGuard(event)",
        1
    )

a = a.replace(
    "safeNodeChildCount(",
    "safeChildCount("
).replace(
    "safeNodeChild(",
    "safeChild("
)

a = a.replace(
    "safeChildCount(safeChildCount(",
    "safeChildCount("
).replace(
    "safeChild(safeChild(",
    "safeChild("
)

write(AUTO, a)

g = read(GS)
if "snakeLastCleanupAt" not in g and "object GestureStore" in g:
    g = re.sub(
        r"(object\s+GestureStore\s*\{)",
        r"\1\n    private var snakeLastCleanupAt = 0L\n",
        g,
        count=1
    )
write(GS, g)

bad = []
fs = read(FCS)
if "FLAG_NOT_TOUCHABLE" not in fs:
    bad.append("Snake overlay FLAG_NOT_TOUCHABLE missing")
if "setLayerType(LAYER_TYPE_HARDWARE, null)" not in fs:
    bad.append("Hardware layer missing")
if "snakeEngineDestroy()" not in fs:
    bad.append("onDestroy cleanup missing")
if "snakeEngineHandleMotionEvent" not in fs:
    bad.append("touch mirror wiring missing")
if "gestureDetector.onTouchEvent" in fs:
    bad.append("bad gestureDetector injection still present")
if "autoActionService?." in fs or "gestureStore?." in fs:
    bad.append("undefined old supreme references still present")

if bad:
    print("❌ Snake patch risk:")
    for x in bad:
        print(" - " + x)
    sys.exit(1)

print("✅ Snake Engine Master AutoFix applied")
print("➡️ Running build...")
