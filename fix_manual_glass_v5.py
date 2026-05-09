import sys, os, re, shutil

fp = "app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt"
if not os.path.exists(fp):
    print("❌ ERROR: File nahi mili. Project root (~/aarishai) mein run karo.")
    sys.exit(1)

shutil.copy2(fp, fp + ".bak")
with open(fp, "r", encoding="utf-8") as f:
    code = f.read()

# ✅ IDEMPOTENCY FIX: Purane variables hatao taaki duplicate error na aaye
code = re.sub(r'\n\s*private\s+var\s+glassHideTimestamp\s*=\s*0L\s*', '\n', code)
code = re.sub(r'\n\s*private\s+var\s+glassHiddenAt\s*=\s*0L\s*', '\n', code)

# 1. SAFE CLASS REMOVER (TouchCaptureView ko delete karo)
def remove_class(c, cls_name):
    pat = r'class\s+' + cls_name + r'(?:\s*\(.*?\))?(?:\s*:\s*[^{]+)?\s*\{'
    m = re.search(pat, c)
    if not m: return c
    start = m.start()
    idx = c.find('{', start)
    if idx == -1: return c
    bc = 1
    idx += 1
    while idx < len(c) and bc > 0:
        if c[idx] == '{': bc += 1
        elif c[idx] == '}': bc -= 1
        idx += 1
    return c[:start] + c[idx:]

code = remove_class(code, "TouchCaptureView")

# 2. SAFE FUNCTION REMOVER
def remove_func(c, fname):
    m = re.search(r'(?:(?:private|internal|protected|public|override|open)\s+)*fun\s+' + fname + r'\s*\(', c)
    if not m: return c
    start = m.start()
    idx = c.find('{', start)
    if idx == -1: return c
    bc = 1
    idx += 1
    while idx < len(c) and bc > 0:
        if c[idx] == '{': bc += 1
        elif c[idx] == '}': bc -= 1
        idx += 1
    return c[:start] + c[idx:]

funcs_to_remove = ["handleStartButton", "startRecording", "saveRecording", "stopRecording", "clearSavedRecordingFromPanel", "handleSingleGestureRecorded", "updateUIState", "extractAndAppendGestures"]
for fname in funcs_to_remove:
    code = remove_func(code, fname)

# NAYA CODE JO CLASS KE ANDAR JAYEGA (WITH V5 BUG FIXES)
new_funcs = """
    private var glassHiddenAt = 0L

    // ✅ SAFE UI STATE FIX: Default parameter added to prevent compile errors
    private fun updateUIState(startText: String, showSave: Boolean, showOthers: Boolean, showCut: Boolean = true) {
        btnStart.text = startText
        val sVis = if (showSave) android.view.View.VISIBLE else android.view.View.GONE
        val oVis = if (showOthers) android.view.View.VISIBLE else android.view.View.GONE
        val cVis = if (showCut) android.view.View.VISIBLE else android.view.View.GONE
        
        try { btnSave?.visibility = sVis } catch(e:Exception){}
        try { btnClear?.visibility = oVis } catch(e:Exception){}
        try { btnSettings?.visibility = oVis } catch(e:Exception){}
        try { btnLoop?.visibility = oVis } catch(e:Exception){}
        try { btnSpeed?.visibility = oVis } catch(e:Exception){}
        try { btnCut?.visibility = cVis } catch(e:Exception){}
    }

    // ✅ EXACT NAVIGATION GAP FIX
    private fun extractAndAppendGestures() {
        val view = captureView as? TouchCaptureView ?: return
        val newGestures = view.getRecordedGestures()
        
        if (newGestures.isEmpty()) return
        
        val mutable = unsavedGestures.toMutableList()
        val lastGesture = unsavedGestures.lastOrNull()
        val lastGestureEnd = (lastGesture?.delayFromStart ?: 0L) + (lastGesture?.points?.lastOrNull()?.t ?: 0L)
        
        // Asli Navigation Gap: Naye Glass ke start time mein se Purane HIDE time ko minus karo
        val navigationGap = if (unsavedGestures.isNotEmpty() && glassHiddenAt > 0L) {
            val gap = view.getRecordingStartTime() - glassHiddenAt
            if (gap > 0L) gap else 0L
        } else {
            0L
        }
        
        val timeOffset = if (unsavedGestures.isEmpty()) {
            0L
        } else {
            lastGestureEnd + navigationGap
        }
        
        newGestures.forEach { g -> 
            mutable.add(RecordedGesture(delayFromStart = g.delayFromStart + timeOffset, points = g.points))
        }
        
        unsavedGestures = mutable
    }

    private fun handleStartButton() {
        if (AutoActionService.isPlaying()) {
            AutoActionService.stopPlayback(this)
            updateUIState("PLAY", false, true, true)
            restorePanelUI()
            android.widget.Toast.makeText(this, "Playback stopped", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        if (isRecording) {
            // ✅ ORDER FIXED: Pehle extract karo, fir glass hatao, aur fir HIDE time save karo
            extractAndAppendGestures()
            isRecording = false
            safeRemoveView(captureView)
            captureView = null
            
            glassHiddenAt = android.os.SystemClock.uptimeMillis() // ACTUAL WAIT YAHAN SE START HOGA
            
            updateUIState("+ ADD", true, false, true)
            android.widget.Toast.makeText(this, "Glass hat gaya! Naya page kholo aur + ADD dabao.", android.widget.Toast.LENGTH_LONG).show()
            
        } else if (unsavedGestures.isNotEmpty()) {
            startRecording()
        } else if (GestureStore.hasRecording(this)) {
            try { closeSettingsPanel() } catch(e: Exception) {}
            val started = AutoActionService.playNow(this)
            if (started) {
                updateUIState("STOP", false, false, false) // CUT button hide
                hidePanelUIForPlayback()
                checkPlaybackStateContinuously()
            }
        } else {
            unsavedGestures = emptyList()
            startRecording()
        }
    }

    private fun startRecording() {
        try { closeSettingsPanel() } catch (e: Exception) {}
        isRecording = true
        updateUIState("HIDE", true, false, true)

        val touchLayer = TouchCaptureView(this)
        val overlayType = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            android.view.WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = android.view.Gravity.TOP or android.view.Gravity.START

        if (!safeAddView(touchLayer, params, "Recording layer error")) {
            isRecording = false
            val hasOld = GestureStore.hasRecording(this)
            updateUIState(if (unsavedGestures.isNotEmpty()) "+ ADD" else if (hasOld) "PLAY" else "START", unsavedGestures.isNotEmpty(), hasOld, true)
            return
        }

        captureView = touchLayer
        bringPanelToFront()
        android.widget.Toast.makeText(this, "🟢 Glass ON! Wait aur Tap record honge.", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun saveRecording() {
        if (isRecording) {
            extractAndAppendGestures()
            isRecording = false
            safeRemoveView(captureView)
            captureView = null
        }
        
        glassHiddenAt = 0L // ✅ RESET FIX
        
        if (unsavedGestures.isEmpty()) {
            android.widget.Toast.makeText(this, "Koi recording nahi mili", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        GestureStore.save(this, unsavedGestures)
        unsavedGestures = emptyList()
        updateUIState("PLAY", false, true, true)
        android.widget.Toast.makeText(this, "✅ Poori Memory Save ho gayi!", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        isRecording = false
        safeRemoveView(captureView)
        captureView = null
        glassHiddenAt = 0L // ✅ RESET FIX
        
        if (unsavedGestures.isNotEmpty()) {
            android.widget.Toast.makeText(this, "⚠️ Unsaved multi-page steps cancel ho gaye", android.widget.Toast.LENGTH_SHORT).show()
        }
        unsavedGestures = emptyList()
        val hasOld = GestureStore.hasRecording(this)
        updateUIState(if (hasOld) "PLAY" else "START", false, hasOld, true)
    }

    private fun clearSavedRecordingFromPanel() {
        if (isRecording || AutoActionService.isPlaying()) {
            android.widget.Toast.makeText(this, "Abhi saaf nahi kar sakte", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        GestureStore.clear(this)
        unsavedGestures = emptyList()
        glassHiddenAt = 0L // ✅ RESET FIX
        
        updateUIState("START", false, false, true)
        android.widget.Toast.makeText(this, "🗑️ Purani memory clear ho gayi!", android.widget.Toast.LENGTH_SHORT).show()
    }
"""

new_tcv = """
class TouchCaptureView(context: android.content.Context) : android.view.View(context) {
    private val recordedGestures = mutableListOf<RecordedGesture>()
    private val currentPoints = mutableListOf<GesturePoint>()
    
    private val recordingStartTime = android.os.SystemClock.uptimeMillis()
    private var currentGestureDownTime = 0L

    init {
        isClickable = true
        setBackgroundColor(android.graphics.Color.argb(45, 0, 200, 0)) // 🟢 Green Glass
    }
    
    // ✅ NEW FIX: For exact Gap calculation
    fun getRecordingStartTime(): Long {
        return recordingStartTime
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                currentPoints.clear()
                currentGestureDownTime = event.eventTime
                addPoint(event, forceAdd = true)
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                addPoint(event, forceAdd = false)
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                addPoint(event, forceAdd = true)
                saveCurrentGesture()
            }
        }
        return true
    }

    private fun addPoint(event: android.view.MotionEvent, forceAdd: Boolean = false) {
        val relativeTime = event.eventTime - currentGestureDownTime
        if (currentPoints.isNotEmpty() && !forceAdd) {
            val last = currentPoints.last()
            val samePlace = kotlin.math.abs(last.x - event.rawX) < 2f && kotlin.math.abs(last.y - event.rawY) < 2f
            val tooFast = relativeTime - last.t < 12
            if (samePlace && tooFast) return
        }
        currentPoints.add(GesturePoint(x = event.rawX, y = event.rawY, t = relativeTime))
    }

    private fun saveCurrentGesture() {
        if (currentPoints.isEmpty()) return
        val delayFromStart = currentGestureDownTime - recordingStartTime
        recordedGestures.add(RecordedGesture(delayFromStart = delayFromStart, points = currentPoints.toList()))
        currentPoints.clear()
    }

    fun getRecordedGestures(): List<RecordedGesture> {
        return recordedGestures.toList()
    }
}
"""

# 3. SMART BRACKET MATCHER
service_match = re.search(r'class\s+FloatingControlService\b[^{]*\{', code)
if not service_match:
    print("❌ ERROR: FloatingControlService class nahi mili.")
    sys.exit(1)

start = service_match.end() - 1
bc = 1
idx = start + 1
service_end = -1
while idx < len(code) and bc > 0:
    if code[idx] == '{': bc += 1
    elif code[idx] == '}': bc -= 1
    if bc == 0:
        service_end = idx
        break
    idx += 1

if service_end == -1:
    print("❌ ERROR: FloatingControlService ka closing brace nahi mila.")
    sys.exit(1)

# Injection
code = code[:service_end] + "\n" + new_funcs + "\n" + code[service_end:] + "\n" + new_tcv

with open(fp, "w", encoding="utf-8") as f:
    f.write(code)

print("\n✅ SUCCESS: V5 Mastermind Edition (100% Exact Timing) install ho gaya!")
