from pathlib import Path
import shutil, time, sys

def find_root():
    cands = [
        Path.cwd(),
        Path.home() / "aarishai",
        Path.home() / "AarishAI",
        Path("/sdcard/Download/aarishai"),
        Path("/storage/emulated/0/Download/aarishai"),
    ]
    for c in cands:
        if (c / "settings.gradle").exists() and (c / "app/src/main").exists():
            return c
    print("Project root nahi mila. Pehle project folder me cd karo.")
    sys.exit(1)

ROOT = find_root()
print("ROOT =", ROOT)

AUTO = ROOT / "app/src/main/java/com/aarishkhan/aarishai/AutoActionService.kt"
MAIN = ROOT / "app/src/main/java/com/aarishkhan/aarishai/MainActivity.kt"
ACC = ROOT / "app/src/main/res/xml/accessibility_service_config.xml"

RES = ROOT / "app/src/main/res"
VAL = RES / "values"
DRAW = RES / "drawable"
VAL.mkdir(parents=True, exist_ok=True)
DRAW.mkdir(parents=True, exist_ok=True)

stamp = time.strftime("%Y%m%d_%H%M%S")

def backup(p):
    if p.exists():
        b = p.with_suffix(p.suffix + ".bak_" + stamp)
        shutil.copy2(p, b)
        print("backup:", b)

def read(p):
    return p.read_text(encoding="utf-8", errors="replace")

def write(p, s):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(s, encoding="utf-8")

for p in [AUTO, MAIN, ACC]:
    if p.exists():
        backup(p)

# strings.xml
strings = VAL / "strings.xml"
if strings.exists():
    backup(strings)
    s = read(strings)
else:
    s = '<resources>\n</resources>\n'

if '<resources' not in s:
    s = '<resources>\n</resources>\n'

def add_string(xml, name, value):
    if f'name="{name}"' in xml:
        return xml
    return xml.replace("</resources>", f'    <string name="{name}">{value}</string>\n</resources>')

s = add_string(s, "app_name", "AarishAI")
s = add_string(s, "accessibility_service_description", "AarishAI Screen Command service")
write(strings, s)

# drawables
write(DRAW / "bg_main_soft.xml", '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient
        android:angle="135"
        android:startColor="#EEF2FF"
        android:centerColor="#E0F2FE"
        android:endColor="#ECFDF5" />
</shape>
''')

write(DRAW / "bg_screen_command_button.xml", '''<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true">
        <shape>
            <corners android:radius="22dp" />
            <gradient
                android:angle="0"
                android:startColor="#4338CA"
                android:endColor="#0F766E" />
            <stroke android:width="1dp" android:color="#CCFFFFFF" />
        </shape>
    </item>
    <item>
        <shape>
            <corners android:radius="22dp" />
            <gradient
                android:angle="0"
                android:startColor="#4F46E5"
                android:centerColor="#0891B2"
                android:endColor="#0D9488" />
            <stroke android:width="1dp" android:color="#E0F2FE" />
        </shape>
    </item>
</selector>
''')

write(DRAW / "ic_stat_aarish.xml", '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,2L3,7v10l9,5l9,-5V7L12,2zM12,5l6,3.3l-6,3.4l-6,-3.4L12,5zM5,10l6,3.4v5.7l-6,-3.3V10zM19,10v5.8l-6,3.3v-5.7L19,10z" />
</vector>
''')

# accessibility typeWindowsChanged
if ACC.exists():
    acc = read(ACC)
    if "typeWindowsChanged" not in acc:
        acc = acc.replace(
            'android:accessibilityEventTypes="',
            'android:accessibilityEventTypes="typeWindowsChanged|'
        )
        write(ACC, acc)

# MainActivity safe restore clear
if MAIN.exists():
    main = read(MAIN)
    old = '''        val editor = getSharedPreferences(PREF_SCREEN_COMMAND_STORE, Context.MODE_PRIVATE).edit()
        editor.clear()

        for ((key, type, value) in restoreItems) {'''
    new = '''        val prefFile = getSharedPreferences(PREF_SCREEN_COMMAND_STORE, Context.MODE_PRIVATE)
        val editor = prefFile.edit()

        for (oldKey in prefFile.all.keys) {
            if (isAarishBackupKey(oldKey)) {
                editor.remove(oldKey)
            }
        }

        for ((key, type, value) in restoreItems) {'''
    if old in main:
        main = main.replace(old, new)
        write(MAIN, main)

# AutoActionService live long press chunk fix
if AUTO.exists():
    auto = read(AUTO)
    if "AARISH_LIVE_LONG_PRESS_CHUNK_FIX_V1" not in auto:
        old = '''            val duration = (endT - startT).coerceIn(55L, 600000L)

            val desc = android.accessibilityservice.GestureDescription.Builder()'''
        new = '''            val duration = (endT - startT).coerceIn(55L, 600000L)

            // AARISH_LIVE_LONG_PRESS_CHUNK_FIX_V1
            if (!movement && duration > 59000L) {
                dispatchLongPressChunksSafe(startX, startY, duration, null) {
                    finishOnce()
                }
                return
            }

            val desc = android.accessibilityservice.GestureDescription.Builder()'''
        if old in auto:
            auto = auto.replace(old, new)
            write(AUTO, auto)

print("PATCH DONE")
print("Ab run karo: ./gradlew assembleDebug --no-daemon")
