package com.aarishkhan.aarishai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class GesturePoint(
    val x: Float,
    val y: Float,
    val t: Long
)

data class TargetSnapshot(
    val targetText: String? = null,
    val targetDesc: String? = null,
    val targetId: String? = null,
    val targetClass: String? = null,
    val targetPackage: String? = null,
    val targetContextText: String? = null,
    val targetChildText: String? = null,
    val targetSiblingText: String? = null,
    val targetRoleFlags: String? = null,
    val targetTreePath: String? = null,
    val targetLeft: Int = -1,
    val targetTop: Int = -1,
    val targetRight: Int = -1,
    val targetBottom: Int = -1,
    val xPercent: Float = 0f,
    val yPercent: Float = 0f,
    val targetWPercent: Float = 0f,
    val targetHPercent: Float = 0f,
    val insideXPercent: Float = 0.5f,
    val insideYPercent: Float = 0.5f,
    val recordedScreenW: Int = 0,
    val recordedScreenH: Int = 0
)

data class RecordedGesture(
    val delayFromStart: Long,
    val points: List<GesturePoint>,
    val targetText: String? = null,
    val targetDesc: String? = null,
    val targetId: String? = null,
    val targetClass: String? = null,
    val targetPackage: String? = null,
    val targetContextText: String? = null,
    val targetChildText: String? = null,
    val targetSiblingText: String? = null,
    val targetRoleFlags: String? = null,
    val targetTreePath: String? = null,
    val targetLeft: Int = -1,
    val targetTop: Int = -1,
    val targetRight: Int = -1,
    val targetBottom: Int = -1,
    val xPercent: Float = 0f,
    val yPercent: Float = 0f,
    val targetWPercent: Float = 0f,
    val targetHPercent: Float = 0f,
    val insideXPercent: Float = 0.5f,
    val insideYPercent: Float = 0.5f,
    val recordedScreenW: Int = 0,
    val recordedScreenH: Int = 0
)

object GestureStore {
    private const val PREF_NAME = "screen_command_store"

    private const val KEY_GESTURES = "recorded_gestures"

    private const val KEY_LOOP_MODE = "loop_mode"
    private const val KEY_LOOP_VALUE = "loop_value"

    private const val KEY_ACTIVE_CONFIG = "active_config"
    private const val KEY_CONFIG_LIST = "config_list_json"
    private const val DEFAULT_CONFIG = "Default Config"
    private const val CONFIG_KEY_PREFIX = "config_"
    private const val KEY_NEXT_CONFIG_PREFIX = "next_config_"
    private const val NEXT_LIST_SEPARATOR = "|||"
    private const val KEY_TAP_ACCURACY_MODE = "tap_accuracy_mode"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun normalizeConfigName(name: String?): String {
        val cleaned = (name ?: DEFAULT_CONFIG)
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[^\\p{L}\\p{N} _.-]+"), "")
            .trim()
            .trim('.', '-', '_')
            .take(40)

        return cleaned.ifBlank { DEFAULT_CONFIG }
    }

    private fun configKey(name: String): String {
        return CONFIG_KEY_PREFIX + normalizeConfigName(name)
    }

    private fun currentGestureKey(context: Context): String {
        return configKey(getActiveConfigName(context))
    }

    private fun loopModeKeyForName(name: String): String {
        return KEY_LOOP_MODE + "_" + normalizeConfigName(name)
    }

    private fun loopValueKeyForName(name: String): String {
        return KEY_LOOP_VALUE + "_" + normalizeConfigName(name)
    }

    private fun nextKeyForName(name: String): String {
        return KEY_NEXT_CONFIG_PREFIX + normalizeConfigName(name)
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

    p.all.keys
        .filter { key -> key != KEY_CONFIG_LIST && key.startsWith(CONFIG_KEY_PREFIX) }
        .map { key -> normalizeConfigName(key.removePrefix(CONFIG_KEY_PREFIX)) }
        .filter { it.isNotBlank() }
        .forEach { names.add(it) }

    names.add(getActiveConfigName(context))

    // AARISH_CONFIG_LIST_REPAIR_V1: corrupt/duplicate config names ko clean + limit karo.
    val finalNames = names
        .map { normalizeConfigName(it) }
        .filter { it.isNotBlank() }
        .distinct()
        .ifEmpty { listOf(DEFAULT_CONFIG) }
        .take(80)

    p.edit().putString(KEY_CONFIG_LIST, JSONArray(finalNames).toString()).apply()

    return finalNames
}

    private fun markActiveConfigInList(context: Context) {
        setActiveConfigName(context, getActiveConfigName(context))
    }

    private fun cleanCoordinate(value: Float): Float {
        return if (value.isNaN() || value.isInfinite()) 0f else value
    }

    private fun cleanPercent(value: Float, fallback: Float = 0f): Float {
        val safe = if (value.isNaN() || value.isInfinite()) fallback else value
        return safe.coerceIn(0f, 1f)
    }

    // AARISH_PERCENT_PERSIST_FIX: NaN anchor ko 0,0 me convert mat karo; warna old/imported
    // macros top-left par drift kar sakte hain. Missing anchor load hote waqt NaN hi rahega.
    private fun putPercentIfFinite(obj: JSONObject, key: String, value: Float) {
        if (!value.isNaN() && !value.isInfinite() && value in 0f..1f) {
            obj.put(key, value.coerceIn(0f, 1f).toDouble())
        }
    }

    private fun readPercentOrNaN(obj: JSONObject, key: String): Float {
        if (!obj.has(key)) return Float.NaN
        val value = obj.optDouble(key, Double.NaN).toFloat()
        return if (value.isNaN() || value.isInfinite()) Float.NaN else value.coerceIn(0f, 1f)
    }

    private fun loadJsonForConfig(context: Context, configName: String): String? {
        val p = prefs(context)
        val safeName = normalizeConfigName(configName)
        val modern = p.getString(configKey(safeName), null)

        if (!modern.isNullOrBlank()) return modern

        return if (safeName == DEFAULT_CONFIG) {
            p.getString(KEY_GESTURES, null)
        } else {
            null
        }
    }

    private fun loadJsonForActiveConfig(context: Context): String? {
        return loadJsonForConfig(context, getActiveConfigName(context))
    }

    fun save(context: Context, gestures: List<RecordedGesture>): Boolean {
        val cleanGestures = gestures
            .filter { it.points.isNotEmpty() }
            .sortedBy { it.delayFromStart.coerceAtLeast(0L) }
            .take(1600)

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
            gestureObject.put("targetPackage", gesture.targetPackage.orEmpty())
            gestureObject.put("targetContextText", gesture.targetContextText.orEmpty())
            gestureObject.put("targetChildText", gesture.targetChildText.orEmpty())
            gestureObject.put("targetSiblingText", gesture.targetSiblingText.orEmpty())
            gestureObject.put("targetRoleFlags", gesture.targetRoleFlags.orEmpty())
            gestureObject.put("targetTreePath", gesture.targetTreePath.orEmpty())
            gestureObject.put("targetLeft", gesture.targetLeft)
            gestureObject.put("targetTop", gesture.targetTop)
            gestureObject.put("targetRight", gesture.targetRight)
            gestureObject.put("targetBottom", gesture.targetBottom)
            putPercentIfFinite(gestureObject, "xPercent", gesture.xPercent)
            putPercentIfFinite(gestureObject, "yPercent", gesture.yPercent)
            gestureObject.put("targetWPercent", cleanPercent(gesture.targetWPercent).toDouble())
            gestureObject.put("targetHPercent", cleanPercent(gesture.targetHPercent).toDouble())
            gestureObject.put("insideXPercent", cleanPercent(gesture.insideXPercent, 0.5f).toDouble())
            gestureObject.put("insideYPercent", cleanPercent(gesture.insideYPercent, 0.5f).toDouble())
            gestureObject.put("recordedScreenW", gesture.recordedScreenW.coerceAtLeast(0))
            gestureObject.put("recordedScreenH", gesture.recordedScreenH.coerceAtLeast(0))

            val pointsArray = JSONArray()
            // AARISH_POINT_TIME_NORMALIZE_V1: imported/edited gestures me first point ka t > 0 ho
            // to playback duration aur path timing drift kar sakti hai. Save ke time local t ko 0 se start rakho.
            val rawPointsForSave = gesture.points
                .filter { !it.x.isNaN() && !it.x.isInfinite() && !it.y.isNaN() && !it.y.isInfinite() }
                .sortedBy { it.t.coerceAtLeast(0L) }
            val pointBaseT = rawPointsForSave.firstOrNull()?.t?.coerceAtLeast(0L) ?: 0L

            rawPointsForSave
                .take(900)
                .forEach { point ->
                    val pointObject = JSONObject()
                    pointObject.put("x", cleanCoordinate(point.x).toDouble())
                    pointObject.put("y", cleanCoordinate(point.y).toDouble())
                    pointObject.put("t", (point.t.coerceAtLeast(0L) - pointBaseT).coerceAtLeast(0L).coerceAtMost(600000L))
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

        if (getActiveConfigName(context) == DEFAULT_CONFIG) {
            // Legacy key ko turant remove nahi karte; modern key corrupt ho jaye to fallback useful rahega.
            editor.putString(KEY_GESTURES, mainArray.toString())
        }

        return editor.commit()
    }

    private fun parseGesturesFromJson(json: String): List<RecordedGesture> {
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
                            x = cleanCoordinate(pointObject.optDouble("x", 0.0).toFloat()),
                            y = cleanCoordinate(pointObject.optDouble("y", 0.0).toFloat()),
                            t = pointObject.optLong("t", 0L).coerceAtLeast(0L).coerceAtMost(600000L)
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
                        targetPackage = cleanOpt(gestureObject, "targetPackage"),
                        targetContextText = cleanOpt(gestureObject, "targetContextText"),
                        targetChildText = cleanOpt(gestureObject, "targetChildText"),
                        targetSiblingText = cleanOpt(gestureObject, "targetSiblingText"),
                        targetRoleFlags = cleanOpt(gestureObject, "targetRoleFlags"),
                        targetTreePath = cleanOpt(gestureObject, "targetTreePath"),
                        targetLeft = gestureObject.optInt("targetLeft", -1),
                        targetTop = gestureObject.optInt("targetTop", -1),
                        targetRight = gestureObject.optInt("targetRight", -1),
                        targetBottom = gestureObject.optInt("targetBottom", -1),
                        xPercent = readPercentOrNaN(gestureObject, "xPercent"),
                        yPercent = readPercentOrNaN(gestureObject, "yPercent"),
                        targetWPercent = cleanPercent(gestureObject.optDouble("targetWPercent", 0.0).toFloat()),
                        targetHPercent = cleanPercent(gestureObject.optDouble("targetHPercent", 0.0).toFloat()),
                        insideXPercent = cleanPercent(gestureObject.optDouble("insideXPercent", 0.5).toFloat(), 0.5f),
                        insideYPercent = cleanPercent(gestureObject.optDouble("insideYPercent", 0.5).toFloat(), 0.5f),
                        recordedScreenW = gestureObject.optInt("recordedScreenW", 0).coerceAtLeast(0),
                        recordedScreenH = gestureObject.optInt("recordedScreenH", 0).coerceAtLeast(0)
                    )
                )
            }

            result.sortedBy { it.delayFromStart }
        } catch (e: Exception) {
            android.util.Log.e("GestureStore", "Recording load failed", e)
            emptyList()
        }
    }

    fun load(context: Context): List<RecordedGesture> {
        val json = loadJsonForActiveConfig(context) ?: return emptyList()
        return parseGesturesFromJson(json)
    }

    fun loadConfig(context: Context, configName: String): List<RecordedGesture> {
        val json = loadJsonForConfig(context, configName) ?: return emptyList()
        return parseGesturesFromJson(json)
    }

    private fun cleanOpt(obj: JSONObject, key: String): String? {
        return obj.optString(key, "").takeIf { it.isNotBlank() }
    }

    fun hasRecording(context: Context): Boolean {
        return load(context).isNotEmpty()
    }

    fun hasRecordingForConfig(context: Context, configName: String): Boolean {
        return loadConfig(context, configName).isNotEmpty()
    }

    fun totalDuration(context: Context): Long {
        return totalDurationForConfig(context, getActiveConfigName(context))
    }

    fun totalDurationForConfig(context: Context, configName: String): Long {
        val gestures = loadConfig(context, configName)
        return gestures.maxOfOrNull { gesture ->
            val gestureDuration = (gesture.points.lastOrNull()?.t ?: 0L).coerceAtMost(600000L)
            gesture.delayFromStart + gestureDuration
        } ?: 0L
    }


    private fun splitNextConfigRaw(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()

        val parts = if (raw.contains(NEXT_LIST_SEPARATOR)) {
            raw.split(NEXT_LIST_SEPARATOR)
        } else {
            listOf(raw)
        }

        return parts
            .map { normalizeConfigName(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(60)
    }

    fun getNextConfigList(context: Context, currentName: String): List<String> {
        val safeCurrent = normalizeConfigName(currentName)
        val raw = prefs(context).getString(nextKeyForName(safeCurrent), null)
        val allNames = getAllConfigNames(context).map { normalizeConfigName(it) }.toSet()

        val cleaned = splitNextConfigRaw(raw)
            .filter { next ->
                next != safeCurrent &&
                    allNames.contains(next) &&
                    hasRecordingForConfig(context, next)
            }
            .distinct()
            .take(60)

        if (!raw.isNullOrBlank() && cleaned.joinToString(NEXT_LIST_SEPARATOR) != raw) {
            val editor = prefs(context).edit()
            if (cleaned.isEmpty()) {
                editor.remove(nextKeyForName(safeCurrent))
            } else {
                editor.putString(nextKeyForName(safeCurrent), cleaned.joinToString(NEXT_LIST_SEPARATOR))
            }
            editor.apply()
        }

        return cleaned
    }

    fun getNextConfig(context: Context, currentName: String): String? {
        return getNextConfigList(context, currentName).firstOrNull()
    }

    fun wouldCreateCycle(context: Context, currentName: String, nextName: String): Boolean {
        val current = normalizeConfigName(currentName)
        val target = normalizeConfigName(nextName)

        if (current == target) return true

        val stack = java.util.ArrayDeque<String>()
        val seen = linkedSetOf<String>()
        stack.add(target)

        var guard = 0
        while (!stack.isEmpty() && guard < 500) {
            guard++

            val cursor = normalizeConfigName(stack.removeLast())
            if (cursor == current) return true

            if (seen.add(cursor)) {
                getNextConfigList(context, cursor).forEach { next ->
                    stack.add(normalizeConfigName(next))
                }
            }
        }

        return false
    }

    fun setNextConfigList(context: Context, currentName: String, nextNames: List<String>): Boolean {
        val safeCurrent = normalizeConfigName(currentName)
        val allNames = getAllConfigNames(context).map { normalizeConfigName(it) }.toSet()

        val cleaned = nextNames
            .map { normalizeConfigName(it) }
            .filter { next ->
                next.isNotBlank() &&
                    next != safeCurrent &&
                    allNames.contains(next) &&
                    hasRecordingForConfig(context, next) &&
                    !wouldCreateCycle(context, safeCurrent, next)
            }
            .distinct()
            .take(60)

        val editor = prefs(context).edit()
        val key = nextKeyForName(safeCurrent)

        if (cleaned.isEmpty()) {
            editor.remove(key)
        } else {
            editor.putString(key, cleaned.joinToString(NEXT_LIST_SEPARATOR))
        }

        return editor.commit()
    }

    fun setNextConfig(context: Context, currentName: String, nextName: String?): Boolean {
        return if (nextName.isNullOrBlank()) {
            setNextConfigList(context, currentName, emptyList())
        } else {
            setNextConfigList(context, currentName, listOf(nextName))
        }
    }

    fun clearChainFrom(context: Context, startName: String): Int {
        val p = prefs(context)
        val editor = p.edit()
        val stack = java.util.ArrayDeque<String>()
        val seen = linkedSetOf<String>()

        stack.add(normalizeConfigName(startName))

        var removed = 0
        var guard = 0

        while (!stack.isEmpty() && guard < 500) {
            guard++

            val cursor = normalizeConfigName(stack.removeLast())
            if (!seen.add(cursor)) continue

            val key = nextKeyForName(cursor)
            val rawNext = p.getString(key, null)

            if (!rawNext.isNullOrBlank()) {
                splitNextConfigRaw(rawNext).forEach { next ->
                    stack.add(normalizeConfigName(next))
                }
                editor.remove(key)
                removed++
            }
        }

        editor.apply()
        return removed
    }

    fun getWorkflowChain(context: Context, startName: String, maxSteps: Int = 200): List<String> {
        val chain = mutableListOf<String>()
        val seen = linkedSetOf<String>()
        var cursor: String? = normalizeConfigName(startName)
        var guard = 0

        while (!cursor.isNullOrBlank() && guard < maxSteps && seen.add(cursor)) {
            chain.add(cursor)
            cursor = getNextConfigList(context, cursor).firstOrNull()
            guard++
        }

        return chain
    }

    fun getWorkflowSummary(context: Context, startName: String): String {
        val start = normalizeConfigName(startName)
        val seen = linkedSetOf<String>()

        fun build(cursorRaw: String, depth: Int): String {
            val cursor = normalizeConfigName(cursorRaw)

            if (depth >= 30) return cursor
            if (!seen.add(cursor)) return "$cursor ↩"

            val nextList = getNextConfigList(context, cursor)

            if (nextList.isEmpty()) return cursor

            return if (nextList.size == 1) {
                "$cursor ➜ ${build(nextList.first(), depth + 1)}"
            } else {
                val choices = nextList.joinToString(" / ")
                val firstContinuation = getNextConfigList(context, nextList.first()).firstOrNull()

                if (firstContinuation.isNullOrBlank()) {
                    "$cursor ➜ [🔀 $choices]"
                } else {
                    "$cursor ➜ [🔀 $choices] ➜ ${build(firstContinuation, depth + 1)}"
                }
            }
        }

        return build(start, 0)
    }

    fun deleteConfig(context: Context, name: String): Boolean {
        val safeName = normalizeConfigName(name)
        if (safeName == DEFAULT_CONFIG) return false

        val p = prefs(context)
        val oldNames = getAllConfigNames(context).map { normalizeConfigName(it) }.distinct()
        val updatedNames = oldNames
            .filter { it != safeName }
            .distinct()
            .ifEmpty { listOf(DEFAULT_CONFIG) }

        val editor = p.edit()
            .remove(configKey(safeName))
            .remove(loopModeKeyForName(safeName))
            .remove(loopValueKeyForName(safeName))
            .remove(nextKeyForName(safeName))
            .putString(KEY_ACTIVE_CONFIG, DEFAULT_CONFIG)
            .putString(KEY_CONFIG_LIST, JSONArray(updatedNames).toString())

        oldNames.forEach { configName ->
            val key = nextKeyForName(configName)
            val raw = p.getString(key, null)
            if (!raw.isNullOrBlank()) {
                val original = splitNextConfigRaw(raw)
                val cleaned = original
                    .filter { it != safeName && updatedNames.contains(it) }
                    .distinct()

                if (original != cleaned) {
                    if (cleaned.isEmpty()) {
                        editor.remove(key)
                    } else {
                        editor.putString(key, cleaned.joinToString(NEXT_LIST_SEPARATOR))
                    }
                }
            }
        }

        return editor.commit()
    }



    fun clear(context: Context) {
        val active = getActiveConfigName(context)
        val p = prefs(context)

        val editor = p.edit()
            .remove(configKey(active))
            .remove(nextKeyForName(active))
            .putString(loopModeKeyForName(active), "ONCE")
            .putInt(loopValueKeyForName(active), 1)

        // Ghost Link Fix V2:
        // Active config clear hone par kisi bhi NEXT list me active config bachi ho to usko list se remove karo.
        getAllConfigNames(context).forEach { configName ->
            val safeConfigName = normalizeConfigName(configName)
            if (safeConfigName == active) return@forEach

            val key = nextKeyForName(safeConfigName)
            val raw = p.getString(key, null)
            if (!raw.isNullOrBlank()) {
                val original = splitNextConfigRaw(raw)
                val cleaned = original
                    .filter { it != active && hasRecordingForConfig(context, it) }
                    .distinct()

                if (original != cleaned) {
                    if (cleaned.isEmpty()) {
                        editor.remove(key)
                    } else {
                        editor.putString(key, cleaned.joinToString(NEXT_LIST_SEPARATOR))
                    }
                }
            }
        }

        if (active == DEFAULT_CONFIG) {
            editor.remove(KEY_GESTURES)
        }

        editor.commit()
    }


    fun saveLoopSettings(context: Context, mode: String, value: Int) {
        saveLoopSettingsForConfig(context, getActiveConfigName(context), mode, value)
    }

    fun saveLoopSettingsForConfig(context: Context, configName: String, mode: String, value: Int) {
        val safeMode = when (mode) {
            "COUNT", "INFINITE", "TIME" -> mode
            else -> "ONCE"
        }
        val safeValue = when (safeMode) {
            "COUNT" -> value.coerceIn(1, 9999)
            "TIME" -> value.coerceIn(1, 1440)
            "INFINITE" -> 0
            else -> 1
        }

        prefs(context).edit()
            .putString(loopModeKeyForName(configName), safeMode)
            .putInt(loopValueKeyForName(configName), safeValue)
            .apply()
    }

    fun getLoopMode(context: Context): String {
        return getLoopModeForConfig(context, getActiveConfigName(context))
    }

    fun getLoopValue(context: Context): Int {
        return getLoopValueForConfig(context, getActiveConfigName(context))
    }

    fun getLoopModeForConfig(context: Context, configName: String): String {
        val mode = prefs(context).getString(loopModeKeyForName(configName), "ONCE") ?: "ONCE"
        return when (mode) {
            "COUNT", "INFINITE", "TIME" -> mode
            else -> "ONCE"
        }
    }

    fun getLoopValueForConfig(context: Context, configName: String): Int {
        return when (getLoopModeForConfig(context, configName)) {
            "COUNT" -> prefs(context).getInt(loopValueKeyForName(configName), 10).coerceIn(1, 9999)
            "TIME" -> prefs(context).getInt(loopValueKeyForName(configName), 5).coerceIn(1, 1440)
            "INFINITE" -> 0
            else -> 1
        }
    }

    // AARISH_TAP_ACCURACY_MODE_V1: global offline smart-click strictness switch.
    fun saveTapAccuracyMode(context: Context, mode: String) {
        val safeMode = when (mode.trim().uppercase()) {
            "STRICT" -> "STRICT"
            "RELAXED" -> "RELAXED"
            else -> "BALANCED"
        }
        prefs(context).edit().putString(KEY_TAP_ACCURACY_MODE, safeMode).apply()
    }

    fun getTapAccuracyMode(context: Context): String {
        val raw = prefs(context).getString(KEY_TAP_ACCURACY_MODE, "BALANCED") ?: "BALANCED"
        return when (raw.trim().uppercase()) {
            "STRICT" -> "STRICT"
            "RELAXED" -> "RELAXED"
            else -> "BALANCED"
        }
    }

    fun getTapAccuracyModeLabel(context: Context): String {
        return when (getTapAccuracyMode(context)) {
            "STRICT" -> "Strict"
            "RELAXED" -> "Relaxed"
            else -> "Balanced"
        }
    }

}
