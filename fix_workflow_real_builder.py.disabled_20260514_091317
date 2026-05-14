import re, sys, time
from pathlib import Path

FCS = Path("app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt")

if not FCS.exists():
    print("❌ FloatingControlService.kt nahi mili")
    sys.exit(1)

src = FCS.read_text(encoding="utf-8")
FCS.with_suffix(FCS.suffix + f".bak_real_wf_{int(time.time())}").write_text(src, encoding="utf-8")

def find_function_bounds(text, signature):
    start = text.find(signature)
    if start == -1:
        return None
    brace = text.find("{", start)
    if brace == -1:
        return None
    depth = 0
    for i in range(brace, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return start, i + 1
    return None

def replace_function(text, signature, new_code):
    bounds = find_function_bounds(text, signature)
    if not bounds:
        print(f"❌ Function match nahi hua: {signature}")
        sys.exit(1)
    a, b = bounds
    return text[:a] + new_code.strip() + "\n" + text[b:]

new_show_workflow = r'''
    private fun showWorkflowHubDialog() {
        if (isRecording || AutoActionService.isPlaying() || unsavedGestures.isNotEmpty()) {
            Toast.makeText(this, "Recording/Play/Unsaved edit ke time workflow change nahi kar sakte", Toast.LENGTH_SHORT).show()
            return
        }

        val active = GestureStore.getActiveConfigName(this)
        val chainText = GestureStore.getWorkflowSummary(this, active)
        val mode = GestureStore.getLoopModeForConfig(this, active)
        val value = GestureStore.getLoopValueForConfig(this, active)

        val loopText = when (mode) {
            "COUNT" -> "Loop: $value cycles"
            "INFINITE" -> "Loop: Infinity"
            "TIME" -> "Loop: $value minutes"
            else -> "Loop: Once"
        }

        val items = arrayOf(
            "🧩 Build Workflow: START ➜ NEXT ➜ LOOP",
            "➕ Change NEXT for Active Config",
            "🔁 Loop Settings for Active Config",
            "▶️ Play From Any Config",
            "👀 Preview Active Workflow",
            "🧹 Clear Active Chain Links"
        )

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Workflow Builder • $active")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showSelectStartConfigForWorkflowDialog()
                    1 -> showChooseNextConfigDialog(active)
                    2 -> showLoopChoiceForConfig(active)
                    3 -> showPlayFromAnyConfigDialog()
                    4 -> showWorkflowPreviewDialog()
                    5 -> showClearFullChainDialog()
                }
            }
            .create()

        showOverlayDialogSafely(dialog)
    }
'''

new_show_loop = r'''
    private fun showLoopSettingsDialog() {
        val active = GestureStore.getActiveConfigName(this)
        showLoopChoiceForConfig(active)
    }
'''

helper_code = r'''
    private fun showSelectStartConfigForWorkflowDialog() {
        val configs = GestureStore.getAllConfigNames(this)
            .filter { GestureStore.hasRecordingForConfig(this, it) }
            .distinct()

        if (configs.size < 2) {
            Toast.makeText(this, "Workflow ke liye kam se kam 2 saved configs chahiye", Toast.LENGTH_LONG).show()
            return
        }

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Step 1: Pehle kaun si config chale?")
            .setItems(configs.toTypedArray()) { _, which ->
                val startConfig = configs[which]
                GestureStore.setActiveConfigName(this, startConfig)
                refreshConfigLabel()
                if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
                if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()
                showSelectNextConfigForWorkflowDialog(startConfig)
            }
            .create()

        showOverlayDialogSafely(dialog)
    }

    private fun showSelectNextConfigForWorkflowDialog(startConfig: String) {
        val nextConfigs = GestureStore.getAllConfigNames(this)
            .filter { it != startConfig && GestureStore.hasRecordingForConfig(this, it) }
            .distinct()

        if (nextConfigs.isEmpty()) {
            Toast.makeText(this, "Next step ke liye koi saved config nahi mili", Toast.LENGTH_LONG).show()
            return
        }

        val options = mutableListOf("🚫 Stop after $startConfig")
        options.addAll(nextConfigs)

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Step 2: '$startConfig' ke baad kya chale?")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    GestureStore.setNextConfig(this, startConfig, null)
                    GestureStore.setActiveConfigName(this, startConfig)
                    refreshConfigLabel()
                    if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
                    if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()
                    Toast.makeText(this, "Workflow: $startConfig only", Toast.LENGTH_SHORT).show()
                    showLoopChoiceForConfig(startConfig)
                    return@setItems
                }

                val nextConfig = options[which]
                if (GestureStore.wouldCreateCycle(this, startConfig, nextConfig)) {
                    Toast.makeText(this, "Cycle ban rahi thi, link block kar di", Toast.LENGTH_LONG).show()
                    return@setItems
                }

                val ok = GestureStore.setNextConfig(this, startConfig, nextConfig)
                GestureStore.setActiveConfigName(this, startConfig)
                refreshConfigLabel()
                if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
                if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()
                restorePanelUI()

                if (ok) {
                    Toast.makeText(this, "Linked: $startConfig ➜ $nextConfig", Toast.LENGTH_LONG).show()
                    showLoopChoiceForConfig(startConfig)
                } else {
                    Toast.makeText(this, "Link save nahi hui", Toast.LENGTH_LONG).show()
                }
            }
            .create()

        showOverlayDialogSafely(dialog)
    }

    private fun showLoopChoiceForConfig(configName: String) {
        val items = arrayOf(
            "▶️ Once",
            "🔢 Number of Cycles",
            "⏱️ Minutes",
            "♾️ Infinity Loop"
        )

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Step 3: '$configName' workflow ko kaise loop karna hai?")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        GestureStore.saveLoopSettingsForConfig(this, configName, "ONCE", 1)
                        Toast.makeText(this, "Ready: $configName • Once", Toast.LENGTH_SHORT).show()
                    }

                    1 -> showNumberInputDialog(
                        title = "Kitne cycles chalana hai?",
                        hint = "Example: 5",
                        defaultValue = GestureStore.getLoopValueForConfig(this, configName).takeIf { it > 1 } ?: 10,
                        min = 1,
                        max = 9999
                    ) { value ->
                        GestureStore.saveLoopSettingsForConfig(this, configName, "COUNT", value)
                        Toast.makeText(this, "Ready: $configName • $value cycles", Toast.LENGTH_SHORT).show()
                        if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
                        if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()
                    }

                    2 -> showNumberInputDialog(
                        title = "Kitne minutes chalana hai?",
                        hint = "Example: 15",
                        defaultValue = GestureStore.getLoopValueForConfig(this, configName).takeIf { it > 1 } ?: 5,
                        min = 1,
                        max = 1440
                    ) { value ->
                        GestureStore.saveLoopSettingsForConfig(this, configName, "TIME", value)
                        Toast.makeText(this, "Ready: $configName • $value minutes", Toast.LENGTH_SHORT).show()
                        if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
                        if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()
                    }

                    3 -> {
                        GestureStore.saveLoopSettingsForConfig(this, configName, "INFINITE", 0)
                        Toast.makeText(this, "Ready: $configName • Infinity Loop", Toast.LENGTH_SHORT).show()
                    }
                }

                if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
                if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()
                restorePanelUI()
            }
            .create()

        showOverlayDialogSafely(dialog)
    }

    private fun showPlayFromAnyConfigDialog() {
        val configs = GestureStore.getAllConfigNames(this)
            .filter { GestureStore.hasRecordingForConfig(this, it) }
            .distinct()

        if (configs.isEmpty()) {
            Toast.makeText(this, "Play ke liye koi saved config nahi mili", Toast.LENGTH_LONG).show()
            return
        }

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Kaun si config se workflow start karna hai?")
            .setItems(configs.toTypedArray()) { _, which ->
                val selected = configs[which]
                GestureStore.setActiveConfigName(this, selected)
                refreshConfigLabel()
                if (::btnLoop.isInitialized) updateLoopButtonText(btnLoop)
                if (::btnWorkflow.isInitialized) updateWorkflowButtonUI()
                restorePanelUI()
                handleStartButton()
            }
            .create()

        showOverlayDialogSafely(dialog)
    }
'''

src = replace_function(src, "    private fun showWorkflowHubDialog()", new_show_workflow)
src = replace_function(src, "    private fun showLoopSettingsDialog()", new_show_loop)

insert_marker = "    private fun showChooseNextConfigDialog(currentConfig: String)"
if "private fun showSelectStartConfigForWorkflowDialog()" not in src:
    pos = src.find(insert_marker)
    if pos == -1:
        print("❌ Insert marker nahi mila: showChooseNextConfigDialog")
        sys.exit(1)
    src = src[:pos] + helper_code.strip() + "\n\n" + src[pos:]

FCS.write_text(src, encoding="utf-8")

print("✅ REAL WORKFLOW BUILDER FIXED")
print("✅ Ab WF me option aayega: START select karo ➜ NEXT select karo ➜ LOOP choose karo")
print("✅ PLAY active start config se poori linked workflow chalाएगा")
