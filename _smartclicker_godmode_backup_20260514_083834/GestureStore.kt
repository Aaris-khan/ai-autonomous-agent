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
    val recordedScreenH: Int = 0,
    val targetParentText: String? = null,
    val targetParentId: String? = null,
    val targetParentClass: String? = null,
    val targetNodeIndex: Int = -1,
    val targetClickable: Boolean = false,
    val targetStableKey: String? = null,
    val zeroWrongTap: Boolean = true,
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
    val recordedScreenH: Int = 0,
    val targetParentText: String? = null,
    val targetParentId: String? = null,
    val targetParentClass: String? = null,
    val targetNodeIndex: Int = -1,
    val targetClickable: Boolean = false,
    val targetStableKey: String? = null,
    val zeroWrongTap: Boolean = true,
)

object GestureStore {
    private var snakeLastCleanupAt = 0L

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
                        targetHPercent = cleanPercent(gestureObject.optDouble("targetHPercent",
                        insideXPercent = gestureObject.optDouble("insideXPercent", 0.5).toFloat().coerceIn(0f, 1f),
                        insideYPercent = gestureObject.optDouble("insideYPercent", 0.5).toFloat().coerceIn(0f, 1f), 0.0).toFloat()),
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


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // REPLAY SPEED MULTIPLIER  0.5x / 1x / 2x / 3x
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private const val KEY_REPLAY_SPEED = "replay_speed_x10"
    private val SPEED_STEPS = intArrayOf(5, 10, 20, 30)   // ÷10 → actual multiplier

    fun getReplaySpeedFloat(context: android.content.Context): Float {
        val v = context.getSharedPreferences("aarishai_prefs", android.content.Context.MODE_PRIVATE)
            .getInt(KEY_REPLAY_SPEED, 10)
        return if (v in SPEED_STEPS.toList()) v / 10f else 1f
    }

    fun cycleReplaySpeed(context: android.content.Context): Float {
        val prefs = context.getSharedPreferences("aarishai_prefs", android.content.Context.MODE_PRIVATE)
        val cur   = prefs.getInt(KEY_REPLAY_SPEED, 10)
        val idx   = SPEED_STEPS.indexOf(cur).let { if (it < 0) 0 else it }
        val next  = SPEED_STEPS[(idx + 1) % SPEED_STEPS.size]
        prefs.edit().putInt(KEY_REPLAY_SPEED, next).apply()
        return next / 10f
    }

    fun getSpeedLabel(context: android.content.Context): String {
        val f = getReplaySpeedFloat(context)
        return "⚡" + if (f == f.toLong().toFloat()) "${f.toLong()}x" else "${f}x"
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // INTERVAL RANDOMIZER  (±20 % humanisation)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private const val KEY_RAND_INTERVAL = "rand_interval_enabled"
    private val _rndRng = java.util.Random()

    fun isIntervalRandomizerOn(context: android.content.Context): Boolean =
        context.getSharedPreferences("aarishai_prefs", android.content.Context.MODE_PRIVATE)
            .getBoolean(KEY_RAND_INTERVAL, false)

    fun setIntervalRandomizer(context: android.content.Context, on: Boolean) {
        context.getSharedPreferences("aarishai_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_RAND_INTERVAL, on).apply()
    }

    /** Call this instead of using raw delayMs directly in replay loop */
    fun humaniseDelay(context: android.content.Context, baseMs: Long): Long {
        val factor = 0.80 + _rndRng.nextDouble() * 0.40   // 0.80 ‥ 1.20
        return (baseMs * factor).toLong().coerceAtLeast(16L)
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // VIBRATION FEEDBACK TOGGLE
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private const val KEY_VIBRO = "click_vibro_enabled"

    fun isVibroEnabled(context: android.content.Context): Boolean =
        context.getSharedPreferences("aarishai_prefs", android.content.Context.MODE_PRIVATE)
            .getBoolean(KEY_VIBRO, false)

    fun toggleVibro(context: android.content.Context): Boolean {
        val prefs = context.getSharedPreferences("aarishai_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_VIBRO, next).apply()
        return next
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // SESSION STATS  (in-memory, resets on app kill)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Volatile var sessionClickCount: Long = 0L
        private set
    @Volatile var sessionStartMs: Long    = 0L
        private set

    fun markSessionStart()  { sessionStartMs  = System.currentTimeMillis(); sessionClickCount = 0L }
    fun recordClick()       { sessionClickCount++ }
    fun sessionDurationSec(): Long = (System.currentTimeMillis() - sessionStartMs) / 1000L
    fun sessionCPS(): Float  {
        val secs = sessionDurationSec()
        return if (secs > 0) sessionClickCount / secs.toFloat() else 0f
    }
    fun sessionSummary(): String =
        "Clicks: $sessionClickCount  |  Time: ${sessionDurationSec()}s  |  CPS: ${"%.1f".format(sessionCPS())}"


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // AARISHAI V6 OFFLINE ENGINE SETTINGS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private val AARISH_V6_PREFS = "aarishai_prefs"
    private val AARISH_V6_KEY_RAND_INTERVAL = "aarish_v6_rand_interval"
    private val AARISH_V6_KEY_MULTI_TAP_COUNT = "aarish_v6_multi_tap_count"
    private val AARISH_V6_KEY_MULTI_TAP_GAP = "aarish_v6_multi_tap_gap"
    private val AARISH_V6_KEY_VIBRO = "aarish_v6_vibro_enabled"
    private val AARISH_V6_RNG = java.util.Random()

    fun aarishV6IsIntervalRandomizerOn(context: android.content.Context): Boolean {
        return context.getSharedPreferences(AARISH_V6_PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(AARISH_V6_KEY_RAND_INTERVAL, false)
    }

    fun aarishV6SetIntervalRandomizer(context: android.content.Context, on: Boolean) {
        context.getSharedPreferences(AARISH_V6_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(AARISH_V6_KEY_RAND_INTERVAL, on).apply()
    }

    @Synchronized
    fun aarishV6HumaniseDelay(context: android.content.Context, baseMs: Long): Long {
        if (!aarishV6IsIntervalRandomizerOn(context)) return baseMs.coerceAtLeast(0L)
        val factor = 0.82 + (AARISH_V6_RNG.nextDouble() * 0.36)
        return (baseMs * factor).toLong().coerceAtLeast(16L)
    }

    fun aarishV6GetMultiTapCount(context: android.content.Context): Int {
        return context.getSharedPreferences(AARISH_V6_PREFS, android.content.Context.MODE_PRIVATE)
            .getInt(AARISH_V6_KEY_MULTI_TAP_COUNT, 1)
            .coerceIn(1, 10)
    }

    fun aarishV6SetMultiTapCount(context: android.content.Context, count: Int) {
        context.getSharedPreferences(AARISH_V6_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putInt(AARISH_V6_KEY_MULTI_TAP_COUNT, count.coerceIn(1, 10)).apply()
    }

    fun aarishV6GetMultiTapGapMs(context: android.content.Context): Long {
        return context.getSharedPreferences(AARISH_V6_PREFS, android.content.Context.MODE_PRIVATE)
            .getLong(AARISH_V6_KEY_MULTI_TAP_GAP, 80L)
            .coerceIn(35L, 500L)
    }

    fun aarishV6SetMultiTapGapMs(context: android.content.Context, gapMs: Long) {
        context.getSharedPreferences(AARISH_V6_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putLong(AARISH_V6_KEY_MULTI_TAP_GAP, gapMs.coerceIn(35L, 500L)).apply()
    }

    fun aarishV6IsVibroEnabled(context: android.content.Context): Boolean {
        return context.getSharedPreferences(AARISH_V6_PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(AARISH_V6_KEY_VIBRO, false)
    }

    fun aarishV6ToggleVibro(context: android.content.Context): Boolean {
        val prefs = context.getSharedPreferences(AARISH_V6_PREFS, android.content.Context.MODE_PRIVATE)
        val next = !prefs.getBoolean(AARISH_V6_KEY_VIBRO, false)
        prefs.edit().putBoolean(AARISH_V6_KEY_VIBRO, next).apply()
        return next
    }

    @Volatile private var aarishV6SessionStartMs: Long = 0L
    @Volatile private var aarishV6SessionClicks: Long = 0L

    fun aarishV6MarkSessionStart() {
        aarishV6SessionStartMs = System.currentTimeMillis()
        aarishV6SessionClicks = 0L
    }

    fun aarishV6RecordClick(count: Int = 1) {
        aarishV6SessionClicks += count.coerceAtLeast(1)
    }

    fun aarishV6SessionDurationSec(): Long {
        val start = aarishV6SessionStartMs
        if (start <= 0L) return 0L
        return ((System.currentTimeMillis() - start) / 1000L).coerceAtLeast(0L)
    }

    fun aarishV6SessionSummary(): String {
        val sec = aarishV6SessionDurationSec()
        val cps = if (sec > 0L) aarishV6SessionClicks / sec.toFloat() else 0f
        return "Clicks: $aarishV6SessionClicks | Time: ${sec}s | CPS: ${"%.1f".format(cps)}"
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // AARISHAI V7 SAFE OFFLINE ENGINE SETTINGS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private const val V7_PREFS = "aarishai_prefs"
    private const val V7_RAND_INTERVAL = "v7_rand_interval"
    private const val V7_MULTI_TAP_COUNT = "v7_multi_tap_count"
    private const val V7_MULTI_TAP_GAP = "v7_multi_tap_gap_ms"
    private const val V7_VIBRO = "v7_vibro_enabled"
    private const val V7_DURATION_SCALE_X10 = "v7_duration_scale_x10"
    private val V7_DURATION_STEPS = intArrayOf(5, 7, 10, 15, 20)
    private val V7_RNG = java.util.Random()

    fun v7IsIntervalRandomizerOn(context: android.content.Context): Boolean {
        return context.getSharedPreferences(V7_PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(V7_RAND_INTERVAL, false)
    }

    fun v7SetIntervalRandomizer(context: android.content.Context, enabled: Boolean) {
        context.getSharedPreferences(V7_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(V7_RAND_INTERVAL, enabled).apply()
    }

    @Synchronized
    fun v7HumaniseDelay(context: android.content.Context, baseMs: Long): Long {
        val safeBase = baseMs.coerceAtLeast(0L)
        if (!v7IsIntervalRandomizerOn(context)) return safeBase
        val factor = 0.90 + (V7_RNG.nextDouble() * 0.20)
        return (safeBase * factor).toLong().coerceAtLeast(16L)
    }

    fun v7GetMultiTapCount(context: android.content.Context): Int {
        return context.getSharedPreferences(V7_PREFS, android.content.Context.MODE_PRIVATE)
            .getInt(V7_MULTI_TAP_COUNT, 1)
            .coerceIn(1, 10)
    }

    fun v7SetMultiTapCount(context: android.content.Context, count: Int) {
        context.getSharedPreferences(V7_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putInt(V7_MULTI_TAP_COUNT, count.coerceIn(1, 10)).apply()
    }

    fun v7GetMultiTapGapMs(context: android.content.Context): Long {
        return context.getSharedPreferences(V7_PREFS, android.content.Context.MODE_PRIVATE)
            .getLong(V7_MULTI_TAP_GAP, 80L)
            .coerceIn(35L, 500L)
    }

    fun v7SetMultiTapGapMs(context: android.content.Context, gapMs: Long) {
        context.getSharedPreferences(V7_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putLong(V7_MULTI_TAP_GAP, gapMs.coerceIn(35L, 500L)).apply()
    }

    fun v7IsVibroEnabled(context: android.content.Context): Boolean {
        return context.getSharedPreferences(V7_PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(V7_VIBRO, false)
    }

    fun v7ToggleVibro(context: android.content.Context): Boolean {
        val prefs = context.getSharedPreferences(V7_PREFS, android.content.Context.MODE_PRIVATE)
        val next = !prefs.getBoolean(V7_VIBRO, false)
        prefs.edit().putBoolean(V7_VIBRO, next).apply()
        return next
    }

    fun v7GetGestureDurationScale(context: android.content.Context): Float {
        val saved = context.getSharedPreferences(V7_PREFS, android.content.Context.MODE_PRIVATE)
            .getInt(V7_DURATION_SCALE_X10, 10)
        return if (V7_DURATION_STEPS.contains(saved)) saved / 10f else 1f
    }

    fun v7CycleGestureDurationScale(context: android.content.Context): Float {
        val prefs = context.getSharedPreferences(V7_PREFS, android.content.Context.MODE_PRIVATE)
        val cur = prefs.getInt(V7_DURATION_SCALE_X10, 10)
        val idx = V7_DURATION_STEPS.indexOf(cur).let { if (it < 0) 2 else it }
        val next = V7_DURATION_STEPS[(idx + 1) % V7_DURATION_STEPS.size]
        prefs.edit().putInt(V7_DURATION_SCALE_X10, next).apply()
        return next / 10f
    }

    fun v7ScaledDuration(context: android.content.Context, rawMs: Long): Long {
        return (rawMs * v7GetGestureDurationScale(context)).toLong().coerceIn(55L, 5000L)
    }

    @Volatile private var v7SessionStartMs: Long = 0L
    @Volatile private var v7SessionClicks: Long = 0L

    fun v7MarkSessionStart() {
        v7SessionStartMs = System.currentTimeMillis()
        v7SessionClicks = 0L
    }

    fun v7RecordClick(count: Int = 1) {
        v7SessionClicks += count.coerceAtLeast(1)
    }

    fun v7SessionDurationSec(): Long {
        if (v7SessionStartMs <= 0L) return 0L
        return ((System.currentTimeMillis() - v7SessionStartMs) / 1000L).coerceAtLeast(0L)
    }

    fun v7SessionSummary(): String {
        val sec = v7SessionDurationSec()
        val cps = if (sec > 0L) v7SessionClicks / sec.toFloat() else 0f
        return "Clicks: $v7SessionClicks | Time: ${sec}s | CPS: ${"%.1f".format(cps)}"
    }

    const val V7_MAX_MACRO_SLOTS = 5

    private fun v7MacroKey(slot: Int) = "v7_macro_data_$slot"
    private fun v7MacroNameKey(slot: Int) = "v7_macro_name_$slot"
    private fun v7MacroModeKey(slot: Int) = "v7_macro_mode_$slot"
    private fun v7MacroValueKey(slot: Int) = "v7_macro_value_$slot"

    fun v7SaveMacroSlot(context: android.content.Context, slot: Int, name: String): Boolean {
        if (slot !in 0 until V7_MAX_MACRO_SLOTS) return false
        val prefs = context.getSharedPreferences(V7_PREFS, android.content.Context.MODE_PRIVATE)
        val data = exportCurrentRecordingJson(context) ?: return false
        val loop = getLoopSettings(context)
        prefs.edit()
            .putString(v7MacroKey(slot), data)
            .putString(v7MacroNameKey(slot), name.take(24))
            .putString(v7MacroModeKey(slot), loop.mode)
            .putInt(v7MacroValueKey(slot), loop.value)
            .apply()
        return true
    }

    fun v7LoadMacroSlot(context: android.content.Context, slot: Int): Boolean {
        if (slot !in 0 until V7_MAX_MACRO_SLOTS) return false
        val prefs = context.getSharedPreferences(V7_PREFS, android.content.Context.MODE_PRIVATE)
        val data = prefs.getString(v7MacroKey(slot), null) ?: return false
        importRecordingJson(context, data)
        setLoopSettings(
            context,
            prefs.getString(v7MacroModeKey(slot), "ONCE") ?: "ONCE",
            prefs.getInt(v7MacroValueKey(slot), 1)
        )
        return true
    }

    fun v7DeleteMacroSlot(context: android.content.Context, slot: Int) {
        if (slot !in 0 until V7_MAX_MACRO_SLOTS) return
        context.getSharedPreferences(V7_PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .remove(v7MacroKey(slot))
            .remove(v7MacroNameKey(slot))
            .remove(v7MacroModeKey(slot))
            .remove(v7MacroValueKey(slot))
            .apply()
    }

    fun v7HasMacroSlot(context: android.content.Context, slot: Int): Boolean {
        if (slot !in 0 until V7_MAX_MACRO_SLOTS) return false
        return context.getSharedPreferences(V7_PREFS, android.content.Context.MODE_PRIVATE)
            .contains(v7MacroKey(slot))
    }

    fun v7MacroName(context: android.content.Context, slot: Int): String {
        return context.getSharedPreferences(V7_PREFS, android.content.Context.MODE_PRIVATE)
            .getString(v7MacroNameKey(slot), null) ?: "Slot ${slot + 1}"
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // SUPREME PRECISION SETTINGS — ZERO FALSE POSITIVE MODE
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private const val SUPREME_PRECISION_PREFS = "aarishai_prefs"
    private const val KEY_SUPREME_RADIUS = "supreme_precision_radius_percent"
    private const val KEY_SUPREME_EXACT_RADIUS = "supreme_exact_radius_percent"
    private const val KEY_SUPREME_STRICT = "supreme_strict_identity_enabled"

    fun getSupremePrecisionRadiusPercent(context: android.content.Context): Float {
        return context.getSharedPreferences(SUPREME_PRECISION_PREFS, android.content.Context.MODE_PRIVATE)
            .getFloat(KEY_SUPREME_RADIUS, 0.10f)
            .coerceIn(0.03f, 0.10f)
    }

    fun getSupremeExactRadiusPercent(context: android.content.Context): Float {
        return context.getSharedPreferences(SUPREME_PRECISION_PREFS, android.content.Context.MODE_PRIVATE)
            .getFloat(KEY_SUPREME_EXACT_RADIUS, 0.05f)
            .coerceIn(0.02f, 0.06f)
    }

    fun isSupremeStrictIdentityEnabled(context: android.content.Context): Boolean {
        return context.getSharedPreferences(SUPREME_PRECISION_PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(KEY_SUPREME_STRICT, true)
    }

    fun setSupremePrecisionMode(
        context: android.content.Context,
        strict: Boolean = true,
        radiusPercent: Float = 0.10f,
        exactRadiusPercent: Float = 0.05f
    ) {
        context.getSharedPreferences(SUPREME_PRECISION_PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SUPREME_STRICT, strict)
            .putFloat(KEY_SUPREME_RADIUS, radiusPercent.coerceIn(0.03f, 0.10f))
            .putFloat(KEY_SUPREME_EXACT_RADIUS, exactRadiusPercent.coerceIn(0.02f, 0.06f))
            .apply()
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ULTRA-STRICT STRUCTURAL PRECISION SETTINGS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private const val STRUCTURAL_PRECISION_PREFS = "aarishai_prefs"
    private const val KEY_STRUCT_RADIUS = "struct_precision_radius_percent"
    private const val KEY_STRUCT_EXACT_RADIUS = "struct_exact_radius_percent"
    private const val KEY_STRUCT_SIZE_TOLERANCE = "struct_size_tolerance_percent"
    private const val KEY_STRUCT_STRICT = "struct_strict_enabled"
    private const val KEY_STRUCT_ANTI_KEYBOARD = "struct_anti_keyboard_enabled"

    fun getStructuralRadiusPercent(context: android.content.Context): Float {
        return context.getSharedPreferences(STRUCTURAL_PRECISION_PREFS, android.content.Context.MODE_PRIVATE)
            .getFloat(KEY_STRUCT_RADIUS, 0.10f)
            .coerceIn(0.03f, 0.10f)
    }

    fun getStructuralExactRadiusPercent(context: android.content.Context): Float {
        return context.getSharedPreferences(STRUCTURAL_PRECISION_PREFS, android.content.Context.MODE_PRIVATE)
            .getFloat(KEY_STRUCT_EXACT_RADIUS, 0.05f)
            .coerceIn(0.02f, 0.06f)
    }

    fun getStructuralSizeTolerancePercent(context: android.content.Context): Float {
        return context.getSharedPreferences(STRUCTURAL_PRECISION_PREFS, android.content.Context.MODE_PRIVATE)
            .getFloat(KEY_STRUCT_SIZE_TOLERANCE, 0.10f)
            .coerceIn(0.03f, 0.15f)
    }

    fun isStructuralStrictEnabled(context: android.content.Context): Boolean {
        return context.getSharedPreferences(STRUCTURAL_PRECISION_PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(KEY_STRUCT_STRICT, true)
    }

    fun isAntiKeyboardGuardEnabled(context: android.content.Context): Boolean {
        return context.getSharedPreferences(STRUCTURAL_PRECISION_PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(KEY_STRUCT_ANTI_KEYBOARD, true)
    }

    fun setStructuralPrecisionMode(
        context: android.content.Context,
        strict: Boolean = true,
        antiKeyboard: Boolean = true,
        radiusPercent: Float = 0.10f,
        exactRadiusPercent: Float = 0.05f,
        sizeTolerancePercent: Float = 0.10f
    ) {
        context.getSharedPreferences(STRUCTURAL_PRECISION_PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_STRUCT_STRICT, strict)
            .putBoolean(KEY_STRUCT_ANTI_KEYBOARD, antiKeyboard)
            .putFloat(KEY_STRUCT_RADIUS, radiusPercent.coerceIn(0.03f, 0.10f))
            .putFloat(KEY_STRUCT_EXACT_RADIUS, exactRadiusPercent.coerceIn(0.02f, 0.06f))
            .putFloat(KEY_STRUCT_SIZE_TOLERANCE, sizeTolerancePercent.coerceIn(0.03f, 0.15f))
            .apply()
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // DEEP CONNECTIVITY / CROSS-FILE SYNERGY STATE
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private const val SYNERGY_PREFS = "aarishai_prefs"
    private const val KEY_SYNERGY_PLAYBACK_ACTIVE = "synergy_playback_active"
    private const val KEY_SYNERGY_LAST_UI_CLEANUP_AT = "synergy_last_ui_cleanup_at"
    private const val KEY_SYNERGY_BLOCKED_UNTIL = "synergy_blocked_until"
    private const val KEY_SYNERGY_LAST_COMMAND = "synergy_last_command"

    fun setSynergyPlaybackActive(context: android.content.Context, active: Boolean) {
        context.getSharedPreferences(SYNERGY_PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SYNERGY_PLAYBACK_ACTIVE, active)
            .putLong(KEY_SYNERGY_LAST_COMMAND, System.currentTimeMillis())
            .apply()
    }

    fun isSynergyPlaybackActive(context: android.content.Context): Boolean {
        return context.getSharedPreferences(SYNERGY_PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(KEY_SYNERGY_PLAYBACK_ACTIVE, false)
    }

    fun markSynergyUiCleanup(context: android.content.Context) {
        context.getSharedPreferences(SYNERGY_PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SYNERGY_LAST_UI_CLEANUP_AT, System.currentTimeMillis())
            .apply()
    }

    fun markSynergyBlockedFor(context: android.content.Context, ms: Long) {
        val until = android.os.SystemClock.uptimeMillis() + ms.coerceIn(100L, 5000L)
        context.getSharedPreferences(SYNERGY_PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SYNERGY_BLOCKED_UNTIL, until)
            .apply()
    }

    fun isSynergyBlocked(context: android.content.Context): Boolean {
        val until = context.getSharedPreferences(SYNERGY_PREFS, android.content.Context.MODE_PRIVATE)
            .getLong(KEY_SYNERGY_BLOCKED_UNTIL, 0L)
        return android.os.SystemClock.uptimeMillis() < until
    }

    fun clearSynergyState(context: android.content.Context) {
        context.getSharedPreferences(SYNERGY_PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SYNERGY_PLAYBACK_ACTIVE, false)
            .putLong(KEY_SYNERGY_BLOCKED_UNTIL, 0L)
            .apply()
    }
}
