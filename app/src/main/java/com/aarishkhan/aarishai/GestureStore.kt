package com.aarishkhan.aarishai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class GesturePoint(val x: Float, val y: Float, val t: Long)
data class RecordedGesture(val delayFromStart: Long, val points: List<GesturePoint>)

object GestureStore {
    private const val PREF_NAME = "screen_command_store"
    private const val KEY_GESTURES = "recorded_gestures"

    private const val KEY_LOOP_MODE = "loop_mode"
    private const val KEY_LOOP_VALUE = "loop_value"

    fun save(context: Context, gestures: List<RecordedGesture>) {
        val mainArray = JSONArray()
        gestures.forEach { gesture ->
            val gestureObject = JSONObject()
            gestureObject.put("delayFromStart", gesture.delayFromStart)
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
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GESTURES, mainArray.toString())
            .apply()
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
                result.add(RecordedGesture(delayFromStart = delayFromStart, points = points))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

                 fun hasRecording(context: Context): Boolean {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_GESTURES, null)
        return !raw.isNullOrEmpty() && raw != "[]"
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
            .apply()
    }

    fun saveLoopSettings(context: Context, mode: String, value: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
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
