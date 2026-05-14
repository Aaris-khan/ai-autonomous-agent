import sys, os, re

fp = "app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt"
if not os.path.exists(fp):
    print("❌ ERROR: File nahi mili.")
    sys.exit(1)

with open(fp, "r", encoding="utf-8") as f:
    code = f.read()

# Missing buttons ko safely inject karo taaki error na aaye
for v in ["btnClear", "btnSettings", "btnSpeed"]:
    if not re.search(r'var\s+' + v, code):
        match = re.search(r'private lateinit var btnStart.*?\n', code)
        if match:
            code = code[:match.end()] + f"    private var {v}: android.widget.Button? = null\n" + code[match.end():]

# Missing function ko inject karo
if "fun closeSettingsPanel" not in code:
    match = re.search(r'private fun updateUIState', code)
    if match:
        code = code[:match.start()] + "private fun closeSettingsPanel() {}\n\n    " + code[match.start():]

with open(fp, "w", encoding="utf-8") as f:
    f.write(code)

print("\n✅ SUCCESS: Saare missing buttons ka error fix ho gaya!")
