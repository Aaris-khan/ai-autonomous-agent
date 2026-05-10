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
    val targetLeft: Int = -1,
    val targetTop: Int = -1,
    val targetRight: Int = -1,
    val targetBottom: Int = -1,
    val xPercent: Float = 0f,
    val yPercent: Float = 0f,
    val targetWPercent: Float = 0f,
    val targetHPercent: Float = 0f,
    val insideXPercent: Float = 0.5f,
    val insideYPercent: Float = 0.5f
)

data class RecordedGesture(
    val delayFromStart: Long,
    val points: List<GesturePoint>,

    // 🔥 Level 1 Smart Fingerprint
    val targetText: String? = null,
    val targetDesc: String? = null,
    val targetId: String? = null,
    val targetClass: String? = null,

    // Recording time bounds
    val targetLeft: Int = -1,
    val targetTop: Int = -1,
    val targetRight: Int = -1,
    val targetBottom: Int = -1,

    // Screen percentage fallback
    val xPercent: Float = 0f,
    val yPercent: Float = 0f,

    // Button size fingerprint
    val targetWPercent: Float = 0f,
    val targetHPercent: Float = 0f,
    val insideXPercent: Float = 0.5f,
    val insideYPercent: Float = 0.5f
)

object GestureStore {
    private const val PREF_NAME = "screen_command_store"
    private const val KEY_GESTURES = "recorded_gestures"

    private const val KEY_LOOP_MODE = "loop_mode"
    private const val KEY_LOOP_VALUE = "loop_value"

    fun save(context: Context, gestures: List<RecordedGesture>): Boolean {
        val mainArray = JSONArray()

        gestures.forEach { gesture ->
            val gestureObject = JSONObject()

            gestureObject.put("delayFromStart", gesture.delayFromStart)

            gestureObject.put("targetText", gesture.targetText ?: "")
            gestureObject.put("targetDesc", gesture.targetDesc ?: "")
            gestureObject.put("targetId", gesture.targetId ?: "")
            gestureObject.put("targetClass", gesture.targetClass ?: "")

            gestureObject.put("targetLeft", gesture.targetLeft)
            gestureObject.put("targetTop", gesture.targetTop)
            gestureObject.put("targetRight", gesture.targetRight)
            gestureObject.put("targetBottom", gesture.targetBottom)

            gestureObject.put("xPercent", gesture.xPercent.toDouble())
            gestureObject.put("yPercent", gesture.yPercent.toDouble())
            gestureObject.put("targetWPercent", gesture.targetWPercent.toDouble())
            gestureObject.put("targetHPercent", gesture.targetHPercent.toDouble())
            gestureObject.put("insideXPercent", gesture.insideXPercent.toDouble())
            gestureObject.put("insideYPercent", gesture.insideYPercent.toDouble())

            val pointsArray = JSONArray()
            gesture.points.forEach { point ->
                val pointObject = JSONObject()
                pointObject.put("x", point.x.toDouble())
                pointObject.put("y", point.y.toDouble())
                pointObject.put("t", point.t)
                pointsArray.put(pointObject)
            }

            gestureObject.put("points", pointsArray)
            mainArray.put(gestureObject)
        }

        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GESTURES, mainArray.toString())
            .commit()
    }

    fun load(context: Context): List<RecordedGesture> {
        val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GESTURES, null) ?: return emptyList()

        return try {
            val mainArray = JSONArray(json)
            val result = mutableListOf<RecordedGesture>()

            for (i in 0 until mainArray.length()) {
                val gestureObject = mainArray.getJSONObject(i)

                val delayFromStart = gestureObject.getLong("delayFromStart")

                val targetText = gestureObject.optString("targetText", "").takeIf { it.isNotBlank() }
                val targetDesc = gestureObject.optString("targetDesc", "").takeIf { it.isNotBlank() }
                val targetId = gestureObject.optString("targetId", "").takeIf { it.isNotBlank() }
                val targetClass = gestureObject.optString("targetClass", "").takeIf { it.isNotBlank() }

                val targetLeft = gestureObject.optInt("targetLeft", -1)
                val targetTop = gestureObject.optInt("targetTop", -1)
                val targetRight = gestureObject.optInt("targetRight", -1)
                val targetBottom = gestureObject.optInt("targetBottom", -1)

                val xPercent = gestureObject.optDouble("xPercent", 0.0).toFloat()
                val yPercent = gestureObject.optDouble("yPercent", 0.0).toFloat()
                val targetWPercent = gestureObject.optDouble("targetWPercent", 0.0).toFloat()
                val targetHPercent = gestureObject.optDouble("targetHPercent", 0.0).toFloat()
                val insideXPercent = gestureObject.optDouble("insideXPercent", 0.5).toFloat()
                val insideYPercent = gestureObject.optDouble("insideYPercent", 0.5).toFloat()

                val pointsArray = gestureObject.getJSONArray("points")
                val points = mutableListOf<GesturePoint>()

                for (j in 0 until pointsArray.length()) {
                    val pointObject = pointsArray.getJSONObject(j)
                    points.add(
                        GesturePoint(
                            x = pointObject.getDouble("x").toFloat(),
                            y = pointObject.getDouble("y").toFloat(),
                            t = pointObject.getLong("t")
                        )
                    )
                }

                result.add(
                    RecordedGesture(
                        delayFromStart = delayFromStart,
                        points = points,
                        targetText = targetText,
                        targetDesc = targetDesc,
                        targetId = targetId,
                        targetClass = targetClass,
                        targetLeft = targetLeft,
                        targetTop = targetTop,
                        targetRight = targetRight,
                        targetBottom = targetBottom,
                        xPercent = xPercent,
                        yPercent = yPercent,
                        targetWPercent = targetWPercent,
                        targetHPercent = targetHPercent,
                        insideXPercent = insideXPercent,
                        insideYPercent = insideYPercent
                    )
                )
            }

            result
        } catch (e: Exception) {
            android.util.Log.e("GestureStore", "Recording load failed", e)
            emptyList()
        }
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
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_GESTURES)
            .putString(KEY_LOOP_MODE, "ONCE")
            .putInt(KEY_LOOP_VALUE, 1)
            .commit()
    }

    fun saveLoopSettings(context: Context, mode: String, value: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOOP_MODE, mode)
            .putInt(KEY_LOOP_VALUE, value)
            .apply()
    }

    fun getLoopMode(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOOP_MODE, "ONCE") ?: "ONCE"
    }

    fun getLoopValue(context: Context): Int {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LOOP_VALUE, 1)
    }
}
