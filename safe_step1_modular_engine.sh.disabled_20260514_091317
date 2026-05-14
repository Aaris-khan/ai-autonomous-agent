#!/usr/bin/env bash
set -euo pipefail

GS="app/src/main/java/com/aarishkhan/aarishai/GestureStore.kt"
FCS="app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt"

[ -f "$GS" ] || { echo "❌ File nahi mili: $GS"; exit 1; }
[ -f "$FCS" ] || { echo "❌ File nahi mili: $FCS"; exit 1; }

cp "$GS" "$GS.bak_modular_$(date +%Y%m%d_%H%M%S)"
cp "$FCS" "$FCS.bak_modular_$(date +%Y%m%d_%H%M%S)"

python3 << 'PY'
from pathlib import Path
import re
import sys

GS = Path("app/src/main/java/com/aarishkhan/aarishai/GestureStore.kt")
FCS = Path("app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt")

gs = GS.read_text(encoding="utf-8")
fcs = FCS.read_text(encoding="utf-8")

def fail(msg):
    print("❌ " + msg)
    sys.exit(1)

# ==========================================================
# 1) GestureStore: safe multi-config vault with migration
# ==========================================================
m = re.search(r'object\s+GestureStore\s*\{', gs)
if not m:
    fail("GestureStore object nahi mila")

gesture_store_v2 = r'''object GestureStore {
    private const val PREF_NAME = "screen_command_store"

    // Old key ko rakho, taaki purani saved recording gayab na ho.
    private const val KEY_GESTURES = "recorded_gestures"

    private const val KEY_LOOP_MODE = "loop_mode"
    private const val KEY_LOOP_VALUE = "loop_value"

    private const val KEY_ACTIVE_CONFIG = "active_config"
    private const val KEY_CONFIG_LIST = "config_list_json"
    private const val DEFAULT_CONFIG = "Default Config"
    private const val CONFIG_KEY_PREFIX = "config_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun normalizeConfigName(name: String?): String {
        val cleaned = (name ?: DEFAULT_CONFIG)
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(40)

        return cleaned.ifBlank { DEFAULT_CONFIG }
    }

    private fun configKey(name: String): String {
        return CONFIG_KEY_PREFIX + normalizeConfigName(name)
    }

    private fun currentGestureKey(context: Context): String {
        return configKey(getActiveConfigName(context))
    }

    fun getActiveConfigName(context: Context): String {
        return normalizeConfigName(
            prefs(context).getString(KEY_ACTIVE_CONFIG, DEFAULT_CONFIG)
        )
    }

    fun setActiveConfigName(context: Context, name: String) {
        val safeName = normalizeConfigName(name)
        val allNames = (getAllConfigNames(context) + safeName)
            .map { normalizeConfigName(it) }
            .distinct()

        prefs(context).edit()
            .putString(KEY_ACTIVE_CONFIG, safeName)
            .putString(KEY_CONFIG_LIST, JSONArray(allNames).toString())
            .apply()
    }

    fun getAllConfigNames(context: Context): List<String> {
        val p = prefs(context)
        val names = linkedSetOf<String>()

        names.add(DEFAULT_CONFIG)

        val raw = p.getString(KEY_CONFIG_LIST, null)
        if (!raw.isNullOrBlank()) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val name = normalizeConfigName(arr.optString(i, ""))
                    if (name.isNotBlank()) names.add(name)
                }
            } catch (_: Exception) {
                raw.split(",")
                    .map { normalizeConfigName(it) }
                    .filter { it.isNotBlank() }
                    .forEach { names.add(it) }
            }
        }

        names.add(getActiveConfigName(context))

        return names.toList()
    }

    private fun markActiveConfigInList(context: Context) {
        setActiveConfigName(context, getActiveConfigName(context))
    }

    private fun loadJsonForActiveConfig(context: Context): String? {
        val p = prefs(context)
        val activeName = getActiveConfigName(context)
        val modern = p.getString(configKey(activeName), null)

        if (!modern.isNullOrBlank()) return modern

        // ✅ Migration fallback:
        // Agar user ka old saved macro KEY_GESTURES mein pada hai,
        // to Default Config mein woh still load hoga.
        return if (activeName == DEFAULT_CONFIG) {
            p.getString(KEY_GESTURES, null)
        } else {
            null
        }
    }

    fun save(context: Context, gestures: List<RecordedGesture>): Boolean {
        val cleanGestures = gestures
            .filter { it.points.isNotEmpty() }
            .sortedBy { it.delayFromStart.coerceAtLeast(0L) }

        if (cleanGestures.isEmpty()) return false

        markActiveConfigInList(context)

        val mainArray = JSONArray()

        cleanGestures.forEach { gesture ->
            val gestureObject = JSONObject()
            gestureObject.put("delayFromStart", gesture.delayFromStart.coerceAtLeast(0L))
            gestureObject.put("targetText", gesture.targetText.orEmpty())
            gestureObject.put("targetDesc", gesture.targetDesc.orEmpty())
            gestureObject.put("targetId", gesture.targetId.orEmpty())
            gestureObject.put("targetClass", gesture.targetClass.orEmpty())
            gestureObject.put("targetContextText", gesture.targetContextText.orEmpty())
            gestureObject.put("targetChildText", gesture.targetChildText.orEmpty())
            gestureObject.put("targetSiblingText", gesture.targetSiblingText.orEmpty())
            gestureObject.put("targetRoleFlags", gesture.targetRoleFlags.orEmpty())
            gestureObject.put("targetTreePath", gesture.targetTreePath.orEmpty())
            gestureObject.put("targetLeft", gesture.targetLeft)
            gestureObject.put("targetTop", gesture.targetTop)
            gestureObject.put("targetRight", gesture.targetRight)
            gestureObject.put("targetBottom", gesture.targetBottom)
            gestureObject.put("xPercent", gesture.xPercent.coerceIn(0f, 1f).toDouble())
            gestureObject.put("yPercent", gesture.yPercent.coerceIn(0f, 1f).toDouble())
            gestureObject.put("targetWPercent", gesture.targetWPercent.coerceIn(0f, 1f).toDouble())
            gestureObject.put("targetHPercent", gesture.targetHPercent.coerceIn(0f, 1f).toDouble())
            gestureObject.put("insideXPercent", gesture.insideXPercent.coerceIn(0f, 1f).toDouble())
            gestureObject.put("insideYPercent", gesture.insideYPercent.coerceIn(0f, 1f).toDouble())

            val pointsArray = JSONArray()
            gesture.points
                .sortedBy { it.t.coerceAtLeast(0L) }
                .take(700)
                .forEach { point ->
                    val pointObject = JSONObject()
                    pointObject.put("x", point.x.toDouble())
                    pointObject.put("y", point.y.toDouble())
                    pointObject.put("t", point.t.coerceAtLeast(0L).coerceAtMost(60000L))
                    pointsArray.put(pointObject)
                }

            if (pointsArray.length() > 0) {
                gestureObject.put("points", pointsArray)
                mainArray.put(gestureObject)
            }
        }

        if (mainArray.length() == 0) return false

        val editor = prefs(context).edit()
            .putString(currentGestureKey(context), mainArray.toString())

        // Default Config save karte waqt old key bhi migrate/remove ho jaye.
        if (getActiveConfigName(context) == DEFAULT_CONFIG) {
            editor.remove(KEY_GESTURES)
        }

        return editor.commit()
    }

    fun load(context: Context): List<RecordedGesture> {
        val json = loadJsonForActiveConfig(context) ?: return emptyList()

        return try {
            val mainArray = JSONArray(json)
            val result = mutableListOf<RecordedGesture>()

            for (i in 0 until mainArray.length()) {
                val gestureObject = mainArray.optJSONObject(i) ?: continue
                val pointsArray = gestureObject.optJSONArray("points") ?: continue
                val points = mutableListOf<GesturePoint>()

                for (j in 0 until pointsArray.length()) {
                    val pointObject = pointsArray.optJSONObject(j) ?: continue
                    points.add(
                        GesturePoint(
                            x = pointObject.optDouble("x", 0.0).toFloat(),
                            y = pointObject.optDouble("y", 0.0).toFloat(),
                            t = pointObject.optLong("t", 0L).coerceAtLeast(0L).coerceAtMost(60000L)
                        )
                    )
                }

                if (points.isEmpty()) continue

                result.add(
                    RecordedGesture(
                        delayFromStart = gestureObject.optLong("delayFromStart", 0L).coerceAtLeast(0L),
                        points = points.sortedBy { it.t },
                        targetText = cleanOpt(gestureObject, "targetText"),
                        targetDesc = cleanOpt(gestureObject, "targetDesc"),
                        targetId = cleanOpt(gestureObject, "targetId"),
                        targetClass = cleanOpt(gestureObject, "targetClass"),
                        targetContextText = cleanOpt(gestureObject, "targetContextText"),
                        targetChildText = cleanOpt(gestureObject, "targetChildText"),
                        targetSiblingText = cleanOpt(gestureObject, "targetSiblingText"),
                        targetRoleFlags = cleanOpt(gestureObject, "targetRoleFlags"),
                        targetTreePath = cleanOpt(gestureObject, "targetTreePath"),
                        targetLeft = gestureObject.optInt("targetLeft", -1),
                        targetTop = gestureObject.optInt("targetTop", -1),
                        targetRight = gestureObject.optInt("targetRight", -1),
                        targetBottom = gestureObject.optInt("targetBottom", -1),
                        xPercent = gestureObject.optDouble("xPercent", 0.0).toFloat().coerceIn(0f, 1f),
                        yPercent = gestureObject.optDouble("yPercent", 0.0).toFloat().coerceIn(0f, 1f),
                        targetWPercent = gestureObject.optDouble("targetWPercent", 0.0).toFloat().coerceIn(0f, 1f),
                        targetHPercent = gestureObject.optDouble("targetHPercent", 0.0).toFloat().coerceIn(0f, 1f),
                        insideXPercent = gestureObject.optDouble("insideXPercent", 0.5).toFloat().coerceIn(0f, 1f),
                        insideYPercent = gestureObject.optDouble("insideYPercent", 0.5).toFloat().coerceIn(0f, 1f)
                    )
                )
            }

            result.sortedBy { it.delayFromStart }
        } catch (e: Exception) {
            android.util.Log.e("GestureStore", "Recording load failed", e)
            emptyList()
        }
    }

    private fun cleanOpt(obj: JSONObject, key: String): String? {
        return obj.optString(key, "").takeIf { it.isNotBlank() }
    }

    fun hasRecording(context: Context): Boolean {
        return load(context).isNotEmpty()
    }

    fun totalDuration(context: Context): Long {
        val gestures = load(context)
        return gestures.maxOfOrNull { gesture ->
            val gestureDuration = (gesture.points.lastOrNull()?.t ?: 0L).coerceAtMost(60000L)
            gesture.delayFromStart + gestureDuration
        } ?: 0L
    }

    fun clear(context: Context) {
        val editor = prefs(context).edit()
            .remove(currentGestureKey(context))
            .putString(KEY_LOOP_MODE, "ONCE")
            .putInt(KEY_LOOP_VALUE, 1)

        if (getActiveConfigName(context) == DEFAULT_CONFIG) {
            editor.remove(KEY_GESTURES)
        }

        editor.commit()
    }

    fun saveLoopSettings(context: Context, mode: String, value: Int) {
        val safeMode = when (mode) {
            "COUNT", "INFINITE", "TIME" -> mode
            else -> "ONCE"
        }
        val safeValue = when (safeMode) {
            "COUNT" -> value.coerceIn(1, 999)
            "TIME" -> value.coerceIn(1, 240)
            "INFINITE" -> 0
            else -> 1
        }

        prefs(context).edit()
            .putString(KEY_LOOP_MODE, safeMode)
            .putInt(KEY_LOOP_VALUE, safeValue)
            .apply()
    }

    fun getLoopMode(context: Context): String {
        val mode = prefs(context).getString(KEY_LOOP_MODE, "ONCE") ?: "ONCE"
        return when (mode) {
            "COUNT", "INFINITE", "TIME" -> mode
            else -> "ONCE"
        }
    }

    fun getLoopValue(context: Context): Int {
        val p = prefs(context)
        return when (getLoopMode(context)) {
            "COUNT" -> p.getInt(KEY_LOOP_VALUE, 10).coerceIn(1, 999)
            "TIME" -> p.getInt(KEY_LOOP_VALUE, 5).coerceIn(1, 240)
            "INFINITE" -> 0
            else -> 1
        }
    }
}
'''

gs = gs[:m.start()] + gesture_store_v2 + "\n"
GS.write_text(gs, encoding="utf-8")
print("✅ GestureStore V2 safe vault applied")

# ==========================================================
# 2) FloatingControlService: label config switcher
# ==========================================================
label_pattern = re.compile(
    r'''label\s*=\s*TextView\(this\)\.apply\s*\{.*?\n\s*\}''',
    re.S
)

label_new = '''label = TextView(this).apply {
            text = "📁 " + GestureStore.getActiveConfigName(this@FloatingControlService)
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(12, 0, 18, 0)
            isClickable = true
            setOnClickListener { showConfigManagerDialog() }
        }'''

fcs2, n = label_pattern.subn(label_new, fcs, count=1)
if n == 0:
    fail("label TextView block replace nahi hua")
fcs = fcs2

# Tap + drag compatibility: OnTouchListener pehle click consume kar raha tha.
old_touch = '''                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    true
                }'''

new_touch = '''                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        dragHandle.performClick()
                    }
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    true
                }'''

if old_touch in fcs:
    fcs = fcs.replace(old_touch, new_touch, 1)
elif "dragHandle.performClick()" not in fcs:
    fail("makePanelDraggable ACTION_UP block patch nahi hua")

dialogs = r'''
    private fun refreshConfigLabel() {
        if (::label.isInitialized) {
            label.text = "📁 " + GestureStore.getActiveConfigName(this)
        }
    }

    private fun prepareOverlayDialog(dialog: android.app.AlertDialog) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
    }

    private fun showConfigManagerDialog() {
        if (isRecording || AutoActionService.isPlaying() || unsavedGestures.isNotEmpty()) {
            Toast.makeText(this, "Recording/Play/Unsaved edit ke time config change nahi kar sakte", Toast.LENGTH_SHORT).show()
            return
        }

        val savedConfigs = GestureStore.getAllConfigNames(this).toMutableList()
        val items = (savedConfigs + "➕ Create New Config").toTypedArray()

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Macro Configs")
            .setItems(items) { _, which ->
                if (which == savedConfigs.size) {
                    showCreateConfigDialog()
                } else {
                    val selected = savedConfigs[which]
                    GestureStore.setActiveConfigName(this, selected)
                    refreshConfigLabel()

                    unsavedGestures = emptyList()
                    glassHiddenAt = 0L
                    pendingDiscardConfirm = false

                    val hasOld = GestureStore.hasRecording(this)
                    updateUIState(if (hasOld) "PLAY" else "START", false, hasOld, true)
                    restorePanelUI()

                    Toast.makeText(this, "Switched: $selected", Toast.LENGTH_SHORT).show()
                }
            }
            .create()

        prepareOverlayDialog(dialog)
        dialog.show()
    }

    private fun showCreateConfigDialog() {
        if (isRecording || AutoActionService.isPlaying() || unsavedGestures.isNotEmpty()) {
            Toast.makeText(this, "Pehle current recording/save complete karo", Toast.LENGTH_SHORT).show()
            return
        }

        val input = android.widget.EditText(this).apply {
            hint = "New Config Name"
            setSingleLine(true)
        }

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Create Config Block")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text?.toString()
                    ?.replace(Regex("[\\r\\n\\t]+"), " ")
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    ?.take(40)
                    .orEmpty()

                if (name.isBlank()) {
                    Toast.makeText(this, "Config name empty nahi ho sakta", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                GestureStore.setActiveConfigName(this, name)
                refreshConfigLabel()

                unsavedGestures = emptyList()
                glassHiddenAt = 0L
                pendingDiscardConfirm = false

                updateUIState("START", false, false, true)
                restorePanelUI()

                Toast.makeText(this, "Created: $name", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()

        prepareOverlayDialog(dialog)
        dialog.show()
    }

'''

if "private fun showConfigManagerDialog()" not in fcs:
    anchor = "private fun stopEverythingAndClose() {"
    if anchor not in fcs:
        fail("stopEverythingAndClose anchor nahi mila")
    fcs = fcs.replace(anchor, dialogs + "\n    " + anchor, 1)
else:
    print("ℹ️ Config dialog functions already exist")

FCS.write_text(fcs, encoding="utf-8")
print("✅ FloatingControlService config switcher applied")
PY

echo "🔎 Verify patches..."
grep -q "KEY_ACTIVE_CONFIG" "$GS" || { echo "❌ GestureStore V2 missing"; exit 1; }
grep -q "showConfigManagerDialog" "$FCS" || { echo "❌ Config dialog missing"; exit 1; }
grep -q "dragHandle.performClick()" "$FCS" || { echo "❌ Label tap/drag fix missing"; exit 1; }

./gradlew assembleDebug
