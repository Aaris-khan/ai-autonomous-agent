from pathlib import Path
from datetime import datetime
import re
import shutil
import sys

BASE = Path(".")
GS = BASE / "app/src/main/java/com/aarishkhan/aarishai/GestureStore.kt"
AAS = BASE / "app/src/main/java/com/aarishkhan/aarishai/AutoActionService.kt"
FCS = BASE / "app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt"

STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")

def fail(msg):
    print("❌ " + msg)
    sys.exit(1)

def backup(path: Path):
    if not path.exists():
        fail(f"File missing: {path}")
    bak = path.with_suffix(path.suffix + f".bak_next_cycle_{STAMP}")
    shutil.copy2(path, bak)
    print(f"🧷 Backup: {bak}")

def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")

def write(path: Path, text: str):
    path.write_text(text, encoding="utf-8")

for p in (GS, AAS, FCS):
    backup(p)

# =========================================================
# 1) GestureStore.kt — NEXT single ko NEXT cycle/list banao
# =========================================================
gs = read(GS)

if 'private const val NEXT_LIST_SEPARATOR = "|||"' not in gs:
    gs = gs.replace(
        'private const val KEY_NEXT_CONFIG_PREFIX = "next_config_"',
        'private const val KEY_NEXT_CONFIG_PREFIX = "next_config_"\n    private const val NEXT_LIST_SEPARATOR = "|||"'
    )

markers = [
    "\n    private fun splitNextConfigRaw(",
    "\n    fun getNextConfigList(",
    "\n    fun getNextConfig(context: Context, currentName: String): String?"
]
starts = [gs.find(m) for m in markers if gs.find(m) != -1]
if not starts:
    fail("GestureStore.kt me getNextConfig block nahi mila")
start = min(starts)

end = gs.find("\n    fun deleteConfig(context: Context, name: String): Boolean", start)
if end == -1:
    fail("GestureStore.kt me deleteConfig marker nahi mila")

new_gs_block = r'''
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
'''

gs = gs[:start] + "\n" + new_gs_block.rstrip() + gs[end:]
write(GS, gs)
print("✅ GestureStore.kt patched")

# =========================================================
# 2) AutoActionService.kt — playback me NEXT list ko cycle karo
# =========================================================
aas = read(AAS)

if "private val configCycleCounters = mutableMapOf<String, Int>()" not in aas:
    aas = aas.replace(
        "private val chainVisitedInRun = linkedSetOf<String>()",
        "private val chainVisitedInRun = linkedSetOf<String>()\n    private val configCycleCounters = mutableMapOf<String, Int>()"
    )

aas = aas.replace(
    "resetActiveGestures()\n        chainVisitedInRun.clear()\n        chainVisitedInRun.add(initialConfigName)",
    "resetActiveGestures()\n        chainVisitedInRun.clear()\n        configCycleCounters.clear()\n        chainVisitedInRun.add(initialConfigName)"
)

aas = aas.replace(
    "resetActiveGestures()\n        chainVisitedInRun.clear()\n\n        if (instance == this)",
    "resetActiveGestures()\n        chainVisitedInRun.clear()\n        configCycleCounters.clear()\n\n        if (instance == this)"
)

aas = aas.replace(
    "resetActiveGestures()\n    chainVisitedInRun.clear()\n\n    if (showToast)",
    "resetActiveGestures()\n    chainVisitedInRun.clear()\n    configCycleCounters.clear()\n\n    if (showToast)"
)

old_next = """            val nextConfig = GestureStore.getNextConfig(this@AutoActionService, currentPlayingConfig)

            if (!nextConfig.isNullOrBlank() && !chainVisitedInRun.contains(nextConfig)) {"""

new_next = """            val nextOptions = GestureStore.getNextConfigList(this@AutoActionService, currentPlayingConfig)
                .filter { option ->
                    option.isNotBlank() &&
                        !chainVisitedInRun.contains(option) &&
                        GestureStore.hasRecordingForConfig(this@AutoActionService, option)
                }
                .distinct()

            val nextConfig = if (nextOptions.isNotEmpty()) {
                val cycleIndex = (configCycleCounters[currentPlayingConfig] ?: 0).coerceAtLeast(0)
                configCycleCounters[currentPlayingConfig] = cycleIndex + 1
                nextOptions[cycleIndex % nextOptions.size]
            } else {
                null
            }

            if (!nextConfig.isNullOrBlank()) {"""

if old_next in aas:
    aas = aas.replace(old_next, new_next)
elif "val nextOptions = GestureStore.getNextConfigList(this@AutoActionService, currentPlayingConfig)" not in aas:
    fail("AutoActionService.kt me nextConfig playback block nahi mila")

write(AAS, aas)
print("✅ AutoActionService.kt patched")

# =========================================================
# 3) FloatingControlService.kt — Change NEXT ko Configure NEXT Cycle banao
# =========================================================
fcs = read(FCS)

fcs = fcs.replace(
    '"➕ Change NEXT for Active Config"',
    '"🔀 Configure NEXT Cycle"'
)

start = fcs.find("\n    private fun showChooseNextConfigDialog(currentConfig: String) {")
if start == -1:
    start = fcs.find("\nprivate fun showChooseNextConfigDialog(currentConfig: String) {")
if start == -1:
    fail("FloatingControlService.kt me showChooseNextConfigDialog function nahi mila")

m = re.search(r"\n\s*private fun showLoopSettingsDialog\(\) \{", fcs[start:])
if not m:
    fail("FloatingControlService.kt me showLoopSettingsDialog marker nahi mila")
end = start + m.start()

new_fcs_func = r'''
    private fun showChooseNextConfigDialog(currentConfig: String) {
        val candidates = GestureStore.getAllConfigNames(this)
            .filter { configName ->
                configName != currentConfig &&
                    GestureStore.hasRecordingForConfig(this, configName)
            }
            .distinct()

        if (candidates.isEmpty()) {
            Toast.makeText(this, "NEXT Cycle ke liye koi aur saved config nahi mili", Toast.LENGTH_LONG).show()
            return
        }

        val currentList = GestureStore.getNextConfigList(this, currentConfig)
        val checkedItems = BooleanArray(candidates.size) { index ->
            currentList.contains(candidates[index])
        }

        val currentText = if (currentList.isEmpty()) {
            "Abhi koi NEXT cycle set nahi hai."
        } else {
            "Current cycle:\n" + currentList.joinToString(" ➜ ")
        }

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Configure NEXT Cycle • $currentConfig")
            .setMessage(
                "$currentText\n\n" +
                    "Jitni configs tick karoge, playback har loop me unko rotate karega.\n\n" +
                    "Example: 1-2-3 ➜ [A/B/C] ➜ 5-10"
            )
            .setMultiChoiceItems(candidates.toTypedArray(), checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Save Cycle") { _, _ ->
                val selected = candidates.filterIndexed { index, _ -> checkedItems[index] }
                val valid = selected.filterNot { next ->
                    GestureStore.wouldCreateCycle(this, currentConfig, next)
                }

                if (selected.size != valid.size) {
                    Toast.makeText(this, "Kuch cycle/circular links skip kar diye", Toast.LENGTH_LONG).show()
                }

                val ok = GestureStore.setNextConfigList(this, currentConfig, valid)

                Toast.makeText(
                    this,
                    if (ok) {
                        if (valid.isEmpty()) "NEXT Cycle clear ho gaya"
                        else "NEXT Cycle saved: ${valid.size} config"
                    } else {
                        "NEXT Cycle save nahi hua"
                    },
                    Toast.LENGTH_LONG
                ).show()

                updateWorkflowButtonUI()
                restorePanelUI()
            }
            .setNeutralButton("Clear") { _, _ ->
                val ok = GestureStore.setNextConfigList(this, currentConfig, emptyList())
                Toast.makeText(
                    this,
                    if (ok) "NEXT Cycle clear ho gaya" else "Clear save nahi hua",
                    Toast.LENGTH_SHORT
                ).show()
                updateWorkflowButtonUI()
                restorePanelUI()
            }
            .setNegativeButton("Cancel", null)
            .create()

        showOverlayDialogSafely(dialog)
    }
'''

fcs = fcs[:start] + "\n" + new_fcs_func.rstrip() + fcs[end:]
write(FCS, fcs)
print("✅ FloatingControlService.kt patched")

print("")
print("🎉 DONE: Configure NEXT Cycle logic lag gaya.")
print("👉 Ab app me: WF ➜ Configure NEXT Cycle ➜ multiple configs tick ➜ Save Cycle")
print("👉 Example: Config 1-3 ke baad [Option A/B/C] tick karo, aur un options ko Config 5-10 se link kar do.")
