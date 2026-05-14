import os, shutil, re

FILES = [
    "app/src/main/res/xml/accessibility_service_config.xml",
    "app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt",
    "app/src/main/java/com/aarishkhan/aarishai/AutoActionService.kt",
]

def read(path):
    with open(path, "r", encoding="utf-8") as f: return f.read()

def write(path, code):
    if os.path.exists(path): shutil.copy2(path, path + ".bak_clean_vol")
    with open(path, "w", encoding="utf-8") as f: f.write(code)

def patch_xml():
    path = FILES[0]
    code = read(path)
    if 'android:accessibilityFlags="flagRequestFilterKeyEvents"' not in code:
        code = code.replace(
            'android:accessibilityEventTypes="typeAllMask"',
            'android:accessibilityEventTypes="typeAllMask"\n    android:accessibilityFlags="flagRequestFilterKeyEvents"'
        )
    if 'android:canRequestFilterKeyEvents="true"' not in code:
        code = code.replace(
            'android:canPerformGestures="true"',
            'android:canRequestFilterKeyEvents="true"\n    android:canPerformGestures="true"'
        )
    write(path, code)
    print("✅ XML key filter config fixed")

def patch_floating_service():
    path = FILES[1]
    code = read(path)
    
    if "companion object {" not in code:
        code = code.replace(
            "class FloatingControlService : Service() {",
            "class FloatingControlService : Service() {\n\n    companion object {\n        @Volatile var instance: FloatingControlService? = null\n    }\n\n    fun isRecordingActive(): Boolean = isRecording\n"
        )
        
    if "instance = this" not in code:
        code = code.replace("super.onCreate()", "super.onCreate()\n        instance = this", 1)
        
    if "if (instance == this) instance = null" not in code:
        code = code.replace(
            "super.onDestroy()", 
            "if (instance == this) instance = null\n        super.onDestroy()", 
            1
        )
        
    if "fun recordSystemAction(actionType: Int)" not in code:
        method = """
    fun recordSystemAction(actionType: Int) {
        if (!isRecording) return
        val view = captureView
        if (view == null) {
            android.widget.Toast.makeText(this, "Recording glass ready nahi hai", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        view.addSystemGesture(actionType)
        val msg = if (actionType == 1) { "🔙 BACK recorded" } else { "🗂️ RECENTS recorded" }
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
"""
        code = code.replace("    override fun onDestroy()", method + "    override fun onDestroy()", 1)
        
    if "fun addSystemGesture(actionType: Int)" not in code:
        method = """
    fun addSystemGesture(actionType: Int) {
        val dummyCoord = if (actionType == 1) -100f else -200f
        val delay = (android.os.SystemClock.uptimeMillis() - recordingStartTime).coerceAtLeast(0L)
        val points = listOf(GesturePoint(dummyCoord, dummyCoord, 100L))
        recordedGestures.add(
            RecordedGesture(delayFromStart = delay, points = points)
        )
    }
"""
        code = code.replace("    fun getRecordedGestures(): List<RecordedGesture> {", method + "    fun getRecordedGestures(): List<RecordedGesture> {", 1)
        code = code.replace(
            "return recordedGestures.toList()", 
            "return recordedGestures.sortedBy { it.delayFromStart }"
        )
        
    write(path, code)
    print("✅ FloatingControlService fixed")

def patch_auto_action_service():
    path = FILES[2]
    code = read(path)
    
    if "override fun onKeyEvent(event: android.view.KeyEvent): Boolean" not in code:
        key_event = """
    override fun onKeyEvent(event: android.view.KeyEvent): Boolean {
        val fcs = FloatingControlService.instance
        val isVolumeKey = event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
        
        if (fcs != null && fcs.isRecordingActive() && isVolumeKey) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
                    fcs.recordSystemAction(1)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                } else if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
                    fcs.recordSystemAction(2)
                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                }
            }
            // DOWN aur UP dono consume karo
            return true
        }
        return super.onKeyEvent(event)
    }
"""
        code = code.replace("    override fun onAccessibilityEvent", key_event + "    override fun onAccessibilityEvent", 1)
        
    marker = "val firstPoint = points.first()"
    if "firstPoint.x <= -50f" not in code:
        # Yahan decrement wala block hata diya gaya hai (Clean Code)
        code = code.replace(
            marker, 
            marker + """
        if (firstPoint.x <= -50f) {
            when (firstPoint.x.toInt()) {
                -100 -> performGlobalAction(GLOBAL_ACTION_BACK)
                -200 -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
            return
        }""", 
            1
        )
        
    write(path, code)
    print("✅ AutoActionService fixed (Clean Version)")

print("🚀 Clean Volume Button Mapper patch start...")
for f in FILES:
    if not os.path.exists(f):
        raise SystemExit(f"❌ File nahi mili: {f}")
        
patch_xml()
patch_floating_service()
patch_auto_action_service()
print("🎯 Done. Ab build chalao.")
