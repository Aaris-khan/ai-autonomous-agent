import os
import re

BASE = "app/src/main/java/com/aarishkhan/aarishai"
GS = f"{BASE}/GestureStore.kt"
FCS = f"{BASE}/FloatingControlService.kt"
AAS = f"{BASE}/AutoActionService.kt"

print("🚀 Level 1 PRO SAFE patch apply ho raha hai...")

# ==================================================
# 1) GestureStore.kt — insideX / insideY add
# ==================================================
with open(GS, "r", encoding="utf-8") as f:
    gs = f.read()

if "insideXPercent" not in gs:
    gs = gs.replace(
        "val targetHPercent: Float = 0f\n)",
        "val targetHPercent: Float = 0f,\n"
        "    val insideXPercent: Float = 0.5f,\n"
        "    val insideYPercent: Float = 0.5f\n)"
    )

    gs = gs.replace(
        'gestureObject.put("targetHPercent", gesture.targetHPercent.toDouble())',
        'gestureObject.put("targetHPercent", gesture.targetHPercent.toDouble())\n'
        '            gestureObject.put("insideXPercent", gesture.insideXPercent.toDouble())\n'
        '            gestureObject.put("insideYPercent", gesture.insideYPercent.toDouble())'
    )

    gs = gs.replace(
        'val targetHPercent = gestureObject.optDouble("targetHPercent", 0.0).toFloat()',
        'val targetHPercent = gestureObject.optDouble("targetHPercent", 0.0).toFloat()\n'
        '                val insideXPercent = gestureObject.optDouble("insideXPercent", 0.5).toFloat()\n'
        '                val insideYPercent = gestureObject.optDouble("insideYPercent", 0.5).toFloat()'
    )

    gs = gs.replace(
        "targetHPercent = targetHPercent\n                    )",
        "targetHPercent = targetHPercent,\n"
        "                        insideXPercent = insideXPercent,\n"
        "                        insideYPercent = insideYPercent\n"
        "                    )"
    )

with open(GS, "w", encoding="utf-8") as f:
    f.write(gs)

print("✅ GestureStore.kt patched")


# ==================================================
# 2) FloatingControlService.kt — save inside percent
# ==================================================
with open(FCS, "r", encoding="utf-8") as f:
    fcs = f.read()

if "insideXPercent = snapshot?.insideXPercent" not in fcs:
    fcs = fcs.replace(
        "targetHPercent = snapshot?.targetHPercent ?: 0f\n            )",
        "targetHPercent = snapshot?.targetHPercent ?: 0f,\n"
        "                insideXPercent = snapshot?.insideXPercent ?: 0.5f,\n"
        "                insideYPercent = snapshot?.insideYPercent ?: 0.5f\n"
        "            )"
    )

with open(FCS, "w", encoding="utf-8") as f:
    f.write(fcs)

print("✅ FloatingControlService.kt patched")


# ==================================================
# 3) AutoActionService.kt — real app root + score + inside click
# ==================================================
with open(AAS, "r", encoding="utf-8") as f:
    aas = f.read()

# Add real app root helper before captureTargetSnapshotInternal
if "private fun getRealAppRoot()" not in aas:
    marker = "    // ==========================================================\n    // 🔥 RECORDING TIME SNAPSHOT\n    // =========================================================="
    helper = r'''
    private fun getRealAppRoot(): AccessibilityNodeInfo? {
        val myPackage = packageName

        try {
            for (window in windows) {
                val root = window.root ?: continue
                val pkg = root.packageName?.toString() ?: ""

                // Apne AarishAI overlay/panel ko skip karo
                if (pkg == myPackage) continue

                val bounds = Rect()
                root.getBoundsInScreen(bounds)

                if (bounds.width() > 0 && bounds.height() > 0) {
                    return root
                }
            }
        } catch (_: Exception) {
        }

        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString() ?: ""
        return if (pkg == myPackage) null else root
    }

'''
    aas = aas.replace(marker, helper + marker)

# Playback root fix
aas = aas.replace(
    "val root = rootInActiveWindow ?: return null\n\n        var best: SmartMatch? = null",
    "val root = getRealAppRoot() ?: return null\n\n        var best: SmartMatch? = null"
)

# Recording root fix
aas = aas.replace(
    "val root = rootInActiveWindow ?: return null\n        val touchedNode = findDeepestNodeAtCoordinate(root, x, y) ?: return null",
    "val root = getRealAppRoot() ?: return null\n        val touchedNode = findDeepestNodeAtCoordinate(root, x, y) ?: return null"
)

# TargetSnapshot inside percent add
if "insideXPercent = if (bounds.width() > 0)" not in aas:
    aas = aas.replace(
        "targetHPercent = bounds.height() / safeH\n        )",
        "targetHPercent = bounds.height() / safeH,\n"
        "            insideXPercent = if (bounds.width() > 0) ((x - bounds.left).toFloat() / bounds.width()).coerceIn(0f, 1f) else 0.5f,\n"
        "            insideYPercent = if (bounds.height() > 0) ((y - bounds.top).toFloat() / bounds.height()).coerceIn(0f, 1f) else 0.5f\n"
        "        )"
    )

# Center click fix: use inside relative point
aas = aas.replace(
    "val startX = match?.centerX ?: fallbackX\n        val startY = match?.centerY ?: fallbackY",
    "val startX = if (match != null && match.bounds.width() > 0) {\n"
    "            match.bounds.left + (recordedGesture.insideXPercent.coerceIn(0f, 1f) * match.bounds.width())\n"
    "        } else fallbackX\n\n"
    "        val startY = if (match != null && match.bounds.height() > 0) {\n"
    "            match.bounds.top + (recordedGesture.insideYPercent.coerceIn(0f, 1f) * match.bounds.height())\n"
    "        } else fallbackY"
)

# Double scoring fix: exact match ke baad partial score dobara na lage
aas = aas.replace(
    """        if (!gesture.targetText.isNullOrBlank() &&
            nodeText?.equals(gesture.targetText, ignoreCase = true) == true
        ) {
            score += 90
        }

        // Medium identity
        if (!gesture.targetText.isNullOrBlank() &&
            nodeText?.contains(gesture.targetText, ignoreCase = true) == true
        ) {
            score += 45
        }""",
    """        val exactTextMatch =
            !gesture.targetText.isNullOrBlank() &&
            nodeText?.equals(gesture.targetText, ignoreCase = true) == true

        if (exactTextMatch) {
            score += 90
        }

        // Medium identity
        if (!exactTextMatch &&
            !gesture.targetText.isNullOrBlank() &&
            nodeText?.contains(gesture.targetText, ignoreCase = true) == true
        ) {
            score += 45
        }"""
)

aas = aas.replace(
    """        if (!gesture.targetDesc.isNullOrBlank() &&
            nodeDesc?.equals(gesture.targetDesc, ignoreCase = true) == true
        ) {
            score += 90
        }

        // Medium identity
        if (!gesture.targetText.isNullOrBlank() &&
            nodeText?.contains(gesture.targetText, ignoreCase = true) == true
        ) {
            score += 45
        }

        if (!gesture.targetDesc.isNullOrBlank() &&
            nodeDesc?.contains(gesture.targetDesc, ignoreCase = true) == true
        ) {
            score += 45
        }""",
    """        val exactDescMatch =
            !gesture.targetDesc.isNullOrBlank() &&
            nodeDesc?.equals(gesture.targetDesc, ignoreCase = true) == true

        if (exactDescMatch) {
            score += 90
        }

        // Medium identity
        if (!exactTextMatch &&
            !gesture.targetText.isNullOrBlank() &&
            nodeText?.contains(gesture.targetText, ignoreCase = true) == true
        ) {
            score += 45
        }

        if (!exactDescMatch &&
            !gesture.targetDesc.isNullOrBlank() &&
            nodeDesc?.contains(gesture.targetDesc, ignoreCase = true) == true
        ) {
            score += 45
        }"""
)

# Distance scoring stronger for duplicate Copy/Send
aas = aas.replace(
    """            if (dx < 0.12f && dy < 0.12f) score += 20
            else if (dx < 0.22f && dy < 0.22f) score += 10""",
    """            if (dx < 0.05f && dy < 0.05f) score += 60
            else if (dx < 0.15f && dy < 0.15f) score += 30
            else if (dx < 0.28f && dy < 0.28f) score += 10
            else if (dx > 0.40f || dy > 0.40f) score -= 50"""
)

# ACTION_CLICK ko sirf small/clear buttons par allow karo; big rows par inside gesture better
aas = aas.replace(
    "if (!movement && duration < 650L && match != null && match.score >= 80) {",
    "if (!movement && duration < 650L && match != null && match.score >= 90 && match.bounds.width() < (resources.displayMetrics.widthPixels * 0.55f)) {"
)

with open(AAS, "w", encoding="utf-8") as f:
    f.write(aas)

print("✅ AutoActionService.kt patched")

print("")
print("🎯 DONE: Level 1 PRO SAFE fixes applied.")
print("⚠️ Important: App install ke baad Accessibility service OFF karke ON karna.")
print("⚠️ Purani recording clear karke fresh recording banana.")
