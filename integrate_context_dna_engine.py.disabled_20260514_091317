import os
import re
import sys

BASE = "app/src/main/java/com/aarishkhan/aarishai"
GS = f"{BASE}/GestureStore.kt"
FCS = f"{BASE}/FloatingControlService.kt"
AAS = f"{BASE}/AutoActionService.kt"

print("🚀 Context + DNA Engine integration start...")

for path in [GS, FCS, AAS]:
    if not os.path.exists(path):
        print(f"❌ File nahi mili: {path}")
        sys.exit(1)

# ==========================================================
# 1) GestureStore.kt — TargetSnapshot + RecordedGesture fields
# ==========================================================
with open(GS, "r", encoding="utf-8") as f:
    gs = f.read()

if "targetContextText" not in gs:
    # TargetSnapshot direct bounds pattern
    gs = gs.replace(
'''    val targetClass: String? = null,
    val targetLeft: Int = -1,''',
'''    val targetClass: String? = null,

    // 🔥 Context + DNA Fingerprint
    val targetContextText: String? = null,
    val targetChildText: String? = null,
    val targetSiblingText: String? = null,
    val targetRoleFlags: String? = null,
    val targetTreePath: String? = null,

    val targetLeft: Int = -1,'''
    )

    # RecordedGesture pattern
    gs = gs.replace(
'''    val targetClass: String? = null,

    // Recording time bounds''',
'''    val targetClass: String? = null,

    // 🔥 Context + DNA Fingerprint
    val targetContextText: String? = null,
    val targetChildText: String? = null,
    val targetSiblingText: String? = null,
    val targetRoleFlags: String? = null,
    val targetTreePath: String? = null,

    // Recording time bounds'''
    )

if 'gestureObject.put("targetContextText"' not in gs:
    gs = gs.replace(
'''            gestureObject.put("targetClass", gesture.targetClass ?: "")''',
'''            gestureObject.put("targetClass", gesture.targetClass ?: "")
            gestureObject.put("targetContextText", gesture.targetContextText ?: "")
            gestureObject.put("targetChildText", gesture.targetChildText ?: "")
            gestureObject.put("targetSiblingText", gesture.targetSiblingText ?: "")
            gestureObject.put("targetRoleFlags", gesture.targetRoleFlags ?: "")
            gestureObject.put("targetTreePath", gesture.targetTreePath ?: "")'''
    )

if "val targetContextText = gestureObject.optString" not in gs:
    gs = gs.replace(
'''                val targetClass = gestureObject.optString("targetClass", "").takeIf { it.isNotBlank() }''',
'''                val targetClass = gestureObject.optString("targetClass", "").takeIf { it.isNotBlank() }
                val targetContextText = gestureObject.optString("targetContextText", "").takeIf { it.isNotBlank() }
                val targetChildText = gestureObject.optString("targetChildText", "").takeIf { it.isNotBlank() }
                val targetSiblingText = gestureObject.optString("targetSiblingText", "").takeIf { it.isNotBlank() }
                val targetRoleFlags = gestureObject.optString("targetRoleFlags", "").takeIf { it.isNotBlank() }
                val targetTreePath = gestureObject.optString("targetTreePath", "").takeIf { it.isNotBlank() }'''
    )

if "targetContextText = targetContextText" not in gs:
    gs = gs.replace(
'''                        targetClass = targetClass,
                        targetLeft = targetLeft,''',
'''                        targetClass = targetClass,
                        targetContextText = targetContextText,
                        targetChildText = targetChildText,
                        targetSiblingText = targetSiblingText,
                        targetRoleFlags = targetRoleFlags,
                        targetTreePath = targetTreePath,
                        targetLeft = targetLeft,'''
    )

with open(GS, "w", encoding="utf-8") as f:
    f.write(gs)

print("✅ GestureStore.kt patched")


# ==========================================================
# 2) FloatingControlService.kt — save snapshot extra fields
# ==========================================================
with open(FCS, "r", encoding="utf-8") as f:
    fcs = f.read()

if "targetContextText = snapshot?.targetContextText" not in fcs:
    fcs = fcs.replace(
'''                targetClass = snapshot?.targetClass,

                targetLeft = snapshot?.targetLeft ?: -1,''',
'''                targetClass = snapshot?.targetClass,

                targetContextText = snapshot?.targetContextText,
                targetChildText = snapshot?.targetChildText,
                targetSiblingText = snapshot?.targetSiblingText,
                targetRoleFlags = snapshot?.targetRoleFlags,
                targetTreePath = snapshot?.targetTreePath,

                targetLeft = snapshot?.targetLeft ?: -1,'''
    )

with open(FCS, "w", encoding="utf-8") as f:
    f.write(fcs)

print("✅ FloatingControlService.kt patched")


# ==========================================================
# 3) AutoActionService.kt — helpers + capture + scoring
# ==========================================================
with open(AAS, "r", encoding="utf-8") as f:
    aas = f.read()

helpers = r'''
    private fun normalizeUltraText(value: String?): String {
        return (value ?: "")
            .lowercase()
            .replace(Regex("[0-9]+"), "#")
            .replace(Regex("[^a-z#\\s\\u0900-\\u097F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun tokenSimilarity(saved: String?, current: String?): Float {
        val a = normalizeUltraText(saved)
        val b = normalizeUltraText(current)

        if (a.isBlank() || b.isBlank()) return 0f
        if (a == b) return 1f

        val tokens = a.split(" ").filter { it.length >= 2 }.distinct()
        if (tokens.isEmpty()) return 0f

        var hits = 0
        for (t in tokens) {
            if (b.contains(t)) hits++
        }

        return hits.toFloat() / tokens.size.toFloat()
    }

    private fun ownLabelOf(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val id = node.viewIdResourceName?.substringAfterLast("/") ?: ""

        return listOf(text, desc, id)
            .filter { it.isNotBlank() }
            .joinToString(" | ")
    }

    private fun collectNodeTextLimited(
        node: AccessibilityNodeInfo?,
        maxNodes: Int = 45,
        maxChars: Int = 420
    ): String {
        if (node == null) return ""

        val out = StringBuilder()
        val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
        stack.add(node)

        var visited = 0

        while (!stack.isEmpty() && visited < maxNodes && out.length < maxChars) {
            visited++

            val n = stack.removeLast()
            val label = ownLabelOf(n)

            if (label.isNotBlank()) {
                if (out.isNotEmpty()) out.append(" | ")
                out.append(label)
            }

            for (i in 0 until n.childCount) {
                val child = n.getChild(i)
                if (child != null) stack.add(child)
            }
        }

        return out.toString().take(maxChars)
    }

    private fun collectSiblingText(
        node: AccessibilityNodeInfo?,
        maxChars: Int = 300
    ): String {
        val parent = node?.parent ?: return ""
        val out = StringBuilder()

        for (i in 0 until parent.childCount) {
            val child = parent.getChild(i) ?: continue
            if (child == node) continue

            val label = collectNodeTextLimited(child, maxNodes = 14, maxChars = 100)

            if (label.isNotBlank()) {
                if (out.isNotEmpty()) out.append(" | ")
                out.append(label)
            }

            if (out.length >= maxChars) break
        }

        return out.toString().take(maxChars)
    }

    private fun roleFlagsOf(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        val flags = mutableListOf<String>()

        if (node.isClickable) flags.add("click")
        if (node.isLongClickable) flags.add("long")
        if (node.isEditable) flags.add("edit")
        if (node.isScrollable) flags.add("scroll")
        if (node.isCheckable) flags.add("check")
        if (node.isChecked) flags.add("checked")
        if (node.isEnabled) flags.add("enabled")
        if (node.isVisibleToUser) flags.add("visible")
        if (node.isFocusable) flags.add("focus")

        return flags.joinToString("|")
    }

    private fun roleSimilarity(savedFlags: String?, currentFlags: String?): Float {
        if (savedFlags.isNullOrBlank() || currentFlags.isNullOrBlank()) return 0f

        val saved = savedFlags.split("|").filter { it.isNotBlank() }
        if (saved.isEmpty()) return 0f

        var hits = 0
        for (f in saved) {
            if (currentFlags.contains(f)) hits++
        }

        return hits.toFloat() / saved.size.toFloat()
    }

    private fun extractTreePathDNA(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        val parts = mutableListOf<String>()
        var current: AccessibilityNodeInfo? = node
        var depth = 0

        while (current != null && depth < 12) {
            val cls = current.className?.toString()?.substringAfterLast(".") ?: "N"
            val parent = current.parent

            var index = -1

            if (parent != null) {
                val currentBounds = Rect()
                current.getBoundsInScreen(currentBounds)

                for (i in 0 until parent.childCount) {
                    val child = parent.getChild(i) ?: continue
                    val childBounds = Rect()
                    child.getBoundsInScreen(childBounds)

                    val sameClass = child.className?.toString() == current.className?.toString()
                    val sameBounds = childBounds == currentBounds

                    if (sameClass && sameBounds) {
                        index = i
                        break
                    }
                }
            }

            parts.add("$cls[$index]")
            current = parent
            depth++
        }

        return parts.reversed().joinToString("/")
    }

    private fun dnaSimilarity(saved: String?, current: String?): Float {
        if (saved.isNullOrBlank() || current.isNullOrBlank()) return 0f
        if (saved == current) return 1f

        val a = saved.split("/").filter { it.isNotBlank() }
        val b = current.split("/").filter { it.isNotBlank() }

        if (a.isEmpty() || b.isEmpty()) return 0f

        val minLen = minOf(a.size, b.size)
        var leafMatches = 0

        for (i in 1..minLen) {
            if (a[a.size - i] == b[b.size - i]) leafMatches++
        }

        return leafMatches.toFloat() / maxOf(a.size, b.size).toFloat()
    }

'''

if "private fun normalizeUltraText(" not in aas:
    if "    private fun isToolbarActionLabel(" in aas:
        aas = aas.replace(
            "    private fun isToolbarActionLabel(",
            helpers + "\n    private fun isToolbarActionLabel("
        )
    elif "    private fun findBestSmartTarget(" in aas:
        aas = aas.replace(
            "    private fun findBestSmartTarget(",
            helpers + "\n    private fun findBestSmartTarget("
        )
    else:
        print("❌ AutoActionService helper insert marker nahi mila")
        sys.exit(1)

# Capture snapshot me context fields add
if "targetContextText = collectNodeTextLimited(clickNode" not in aas:
    aas = aas.replace(
'''            targetClass = clickNode.className?.toString() ?: touchedNode.className?.toString(),
            targetLeft = bounds.left,''',
'''            targetClass = clickNode.className?.toString() ?: touchedNode.className?.toString(),

            targetContextText = collectNodeTextLimited(clickNode, 45, 420),
            targetChildText = collectNodeTextLimited(touchedNode, 25, 220),
            targetSiblingText = collectSiblingText(clickNode, 300),
            targetRoleFlags = roleFlagsOf(clickNode),
            targetTreePath = extractTreePathDNA(clickNode),

            targetLeft = bounds.left,'''
    )

# scoreNode me additive context scoring
if "contextNodeForUltra" not in aas:
    old = '''        var score = 0

        val nodeText = node.text?.toString()?.trim()'''

    new = '''        var score = 0

        val contextNodeForUltra = findClickableParent(node) ?: node
        val contextSimUltra = tokenSimilarity(
            gesture.targetContextText,
            collectNodeTextLimited(contextNodeForUltra, 45, 420)
        )
        val childSimUltra = tokenSimilarity(
            gesture.targetChildText,
            collectNodeTextLimited(node, 25, 220)
        )
        val siblingSimUltra = tokenSimilarity(
            gesture.targetSiblingText,
            collectSiblingText(contextNodeForUltra, 300)
        )
        val roleSimUltra = roleSimilarity(
            gesture.targetRoleFlags,
            roleFlagsOf(contextNodeForUltra)
        )
        val dnaSimUltra = dnaSimilarity(
            gesture.targetTreePath,
            extractTreePathDNA(contextNodeForUltra)
        )

        if (contextSimUltra >= 0.85f) score += 65
        else if (contextSimUltra >= 0.60f) score += 35

        if (childSimUltra >= 0.85f) score += 35
        else if (childSimUltra >= 0.60f) score += 18

        if (siblingSimUltra >= 0.85f) score += 30
        else if (siblingSimUltra >= 0.60f) score += 14

        if (roleSimUltra >= 0.75f) score += 15

        if (dnaSimUltra >= 0.90f) score += 55
        else if (dnaSimUltra >= 0.70f) score += 28
        else if (dnaSimUltra >= 0.50f) score += 12

        val nodeText = node.text?.toString()?.trim()'''

    if old not in aas:
        print("⚠️ scoreNode ka exact marker nahi mila. Context scoring inject nahi hua.")
    else:
        aas = aas.replace(old, new)

# Strong identity me context/DNA bypass add
aas = aas.replace(
'''                val strongIdentity =
                    (!gesture.targetId.isNullOrBlank() && nodeId == gesture.targetId) ||
                    exactTextMatch ||
                    exactDescMatch

                score -= if (strongIdentity) 10 else 45''',
'''                val strongIdentity =
                    (!gesture.targetId.isNullOrBlank() && nodeId == gesture.targetId) ||
                    exactTextMatch ||
                    exactDescMatch ||
                    contextSimUltra >= 0.70f ||
                    childSimUltra >= 0.70f ||
                    siblingSimUltra >= 0.70f ||
                    dnaSimUltra >= 0.80f

                score -= if (strongIdentity) 0 else 45'''
)

# hasAnyIdentity me context fields add
aas = aas.replace(
'''            !gesture.targetClass.isNullOrBlank() ||
            gesture.targetWPercent > 0f''',
'''            !gesture.targetClass.isNullOrBlank() ||
            !gesture.targetContextText.isNullOrBlank() ||
            !gesture.targetChildText.isNullOrBlank() ||
            !gesture.targetSiblingText.isNullOrBlank() ||
            !gesture.targetRoleFlags.isNullOrBlank() ||
            !gesture.targetTreePath.isNullOrBlank() ||
            gesture.targetWPercent > 0f'''
)

with open(AAS, "w", encoding="utf-8") as f:
    f.write(aas)

print("✅ AutoActionService.kt patched")


# ==========================================================
# 4) Final sanity check
# ==========================================================
checks = [
    (GS, "targetContextText"),
    (GS, "targetTreePath"),
    (FCS, "targetContextText = snapshot?.targetContextText"),
    (AAS, "normalizeUltraText"),
    (AAS, "extractTreePathDNA"),
    (AAS, "targetContextText = collectNodeTextLimited"),
    (AAS, "contextNodeForUltra"),
]

failed = False
for path, text in checks:
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    if text not in content:
        print(f"❌ Missing after patch: {text} in {path}")
        failed = True

if failed:
    print("⚠️ Kuch patch miss hua. File version alag ho sakta hai.")
    sys.exit(1)

print("")
print("🎯 DONE: Context + DNA Engine successfully integrated!")
print("⚠️ Ab purani recording clear karke fresh recording banana.")
