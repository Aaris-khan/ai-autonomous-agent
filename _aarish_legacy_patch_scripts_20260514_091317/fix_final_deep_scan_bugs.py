import re

BASE = "app/src/main/java/com/aarishkhan/aarishai"
AAS = f"{BASE}/AutoActionService.kt"

print("🚀 Final Deep Scan Bugs Fix apply ho raha hai...")

with open(AAS, "r", encoding="utf-8") as f:
    aas = f.read()

# ==========================================================
# 1) System BACK/RECENTS guard delay
# ==========================================================
old_system = '''        if (firstPoint.x <= -50f) {
            when (firstPoint.x.toInt()) {
                -100 -> performGlobalAction(GLOBAL_ACTION_BACK)
                -200 -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
            return
        }'''

new_system = '''        if (firstPoint.x <= -50f) {
            activeGestureCount.incrementAndGet()

            when (firstPoint.x.toInt()) {
                -100 -> performGlobalAction(GLOBAL_ACTION_BACK)
                -200 -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            }

            // System action ka callback nahi hota, isliye transition ke liye short guard delay
            handler.postDelayed({
                decrementActiveGestureSafely()
            }, 450L)

            return
        }'''

aas = aas.replace(old_system, new_system)


# ==========================================================
# 2) Smart matching only for tap/long press, not swipe/scroll
# ==========================================================
old_match = '''        val match = findBestSmartTarget(recordedGesture)

        val screenW = resources.displayMetrics.widthPixels.toFloat()
        val screenH = resources.displayMetrics.heightPixels.toFloat()'''

new_match = '''        val movement = hasRealMovement(points)

        // Smart node matching tap/long-press ke liye.
        // Swipe/scroll mein original gesture path preserve karna safe hai.
        val match = if (!movement) findBestSmartTarget(recordedGesture) else null

        val screenW = resources.displayMetrics.widthPixels.toFloat()
        val screenH = resources.displayMetrics.heightPixels.toFloat()'''

aas = aas.replace(old_match, new_match)

# Duplicate movement declaration remove
aas = aas.replace(
'''        val movement = hasRealMovement(points)
        val duration = max(50L, points.last().t).coerceAtMost(60000L)''',
'''        val duration = max(50L, points.last().t).coerceAtMost(60000L)'''
)


# ==========================================================
# 3) findBestSmartTarget: parent bounds par size/position bonus
# ==========================================================
old_best_block = '''                    if (targetBounds.width() > 0 && targetBounds.height() > 0) {
                        val finalScore = score + if (targetNode.isClickable) 12 else 0
                        if (best == null || finalScore > best!!.score) {
                            best = SmartMatch(targetNode, Rect(targetBounds), finalScore)
                        }
                    }'''

new_best_block = '''                    if (targetBounds.width() > 0 && targetBounds.height() > 0) {
                        var finalScore = score + if (targetNode.isClickable) 12 else 0

                        val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
                        val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

                        // Recording mein button/clickable-parent ka size save hota hai.
                        // Isliye final parent bounds par bhi size score do.
                        if (gesture.targetWPercent > 0f && gesture.targetHPercent > 0f) {
                            val wNow = targetBounds.width() / screenW
                            val hNow = targetBounds.height() / screenH
                            val wDiff = kotlin.math.abs(wNow - gesture.targetWPercent)
                            val hDiff = kotlin.math.abs(hNow - gesture.targetHPercent)

                            if (wDiff < 0.04f && hDiff < 0.04f) finalScore += 25
                            else if (wDiff < 0.08f && hDiff < 0.08f) finalScore += 12
                        }

                        // Duplicate Copy/Send jaise cases mein parent/current bounds ki position bhi use karo.
                        if (gesture.xPercent > 0f && gesture.yPercent > 0f) {
                            val cxP = targetBounds.exactCenterX() / screenW
                            val cyP = targetBounds.exactCenterY() / screenH
                            val dx = kotlin.math.abs(cxP - gesture.xPercent)
                            val dy = kotlin.math.abs(cyP - gesture.yPercent)

                            if (dx < 0.06f && dy < 0.06f) finalScore += 35
                            else if (dx < 0.16f && dy < 0.16f) finalScore += 18
                            else if (dx < 0.30f && dy < 0.30f) finalScore += 8
                        }

                        if (best == null || finalScore > best!!.score) {
                            best = SmartMatch(targetNode, Rect(targetBounds), finalScore)
                        }
                    }'''

aas = aas.replace(old_best_block, new_best_block)


# ==========================================================
# 4) Better findDeepestNodeAtCoordinate
# ==========================================================
deep_pattern = r'''    private fun findDeepestNodeAtCoordinate\(\s*root: AccessibilityNodeInfo\?,\s*x: Int,\s*y: Int\s*\): AccessibilityNodeInfo\? \{.*?\n    private fun findClickableParent'''

new_deep = r'''    private fun findDeepestNodeAtCoordinate(
        root: AccessibilityNodeInfo?,
        x: Int,
        y: Int
    ): AccessibilityNodeInfo? {
        if (root == null) return null

        val stack = java.util.ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.add(Pair(root, 0))

        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = Int.MIN_VALUE

        while (!stack.isEmpty()) {
            val item = stack.removeLast()
            val node = item.first
            val depth = item.second

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            if (bounds.contains(x, y)) {
                val area = (bounds.width() * bounds.height()).coerceAtLeast(1)
                val clickableBonus = if (node.isClickable) 800 else 0
                val leafBonus = if (node.childCount == 0) 300 else 0
                val smallAreaBonus = 500000 / area.coerceAtLeast(1)

                // Deep + clickable + leaf + small area = better actual tapped node
                val candidateScore =
                    (depth * 1000) +
                    clickableBonus +
                    leafBonus +
                    smallAreaBonus

                if (candidateScore > bestScore) {
                    bestScore = candidateScore
                    bestNode = node
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        stack.add(Pair(child, depth + 1))
                    }
                }
            }
        }

        return bestNode
    }

    private fun findClickableParent'''

aas = re.sub(deep_pattern, new_deep, aas, flags=re.DOTALL)

with open(AAS, "w", encoding="utf-8") as f:
    f.write(aas)

print("✅ AutoActionService.kt patched")
print("")
print("🎯 DONE: Swipe-safe matching, better deepest node, parent-size scoring, and system-action guard fixed.")
