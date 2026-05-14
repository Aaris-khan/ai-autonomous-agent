BASE="app/src/main/java/com/aarishkhan/aarishai"
GS=f"{BASE}/GestureStore.kt"

print("🚀 Final small polish apply ho raha hai...")

shutil.copy2(GS, GS + f".bak_{int(time.time())}")
with open(GS, "r", encoding="utf-8") as f:
    gs = f.read()

# hasRecording ko real load validation bana do
old_has = '''    fun hasRecording(context: Context): Boolean {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GESTURES, null) ?: return false

        return try {
            JSONArray(raw).length() > 0
        } catch (_: Exception) {
            false
        }
    }'''

new_has = '''    fun hasRecording(context: Context): Boolean {
        return load(context).isNotEmpty()
    }'''

gs = gs.replace(old_has, new_has)

# clear ko commit banao, taaki old recording rare case mein wapas na aaye
gs = gs.replace(
'''.remove(KEY_GESTURES)
            .putString(KEY_LOOP_MODE, "ONCE")
            .putInt(KEY_LOOP_VALUE, 1)
            .apply()''',
'''.remove(KEY_GESTURES)
            .putString(KEY_LOOP_MODE, "ONCE")
            .putInt(KEY_LOOP_VALUE, 1)
            .commit()'''
)

with open(GS, "w", encoding="utf-8") as f:
    f.write(gs)

print("✅ DONE: hasRecording real validation + clear commit fixed.")
