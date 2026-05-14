import re

BASE = "app/src/main/java/com/aarishkhan/aarishai"
AAS = f"{BASE}/AutoActionService.kt"

print("🚀 Toolbar Exact Action Button Fix apply ho raha hai...")

with open(AAS, "r", encoding="utf-8") as f:
    aas = f.read()

# ==========================================================
# 1) Helper functions: exact toolbar/action button scanner
# ==========================================================
helpers = r'''
    private fun isToolbarActionLabel(label: String?): Boolean {
        val v = label?.trim()?.lowercase() ?: return false

        return v in setOf(
            "copy",
            "cut",
            "paste",
            "select",
            "select all",
            "share",
            "translate",
            "कॉपी",
            "कट",
            "पेस्ट",
            "चुनें",
            "सब चुनें",
            "साझा करें",
            "अनुवाद"
        )
    }

    private fun findExactActionButtonAcrossWindows(
        gesture: RecordedGesture
    ): SmartMatch? {
        val wantedText = gesture.targetText?.trim()
        val wantedDesc = gesture.targetDesc?.trim()

        val wanted = when {
            isToolbarActionLabel(wantedText) -> wantedText
            isToolbarActionLabel(wantedDesc) -> wantedDesc
            else -> null
        } ?: return null

        val wantedLower = wanted.lowercase()
        val roots = mutableListOf<AccessibilityNodeInfo>()

        fun skipRoot(root: AccessibilityNodeInfo): Boolean {
            val pkg = root.packageName?.toString() ?: ""
            if (pkg == packageName) return true
            if (pkg.contains("keyboard", ignoreCase = true)) return true
            if (pkg.contains("inputmethod", ignoreCase = true)) return true
            return false
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (window in windows) {
                    if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        continue
                    }

                    val root = window.root ?: continue
                    if (skipRoot(root)) continue

                    val b = Rect()
                    root.getBoundsInScreen(b)
                    if (b.width() > 0 && b.height() > 0) {
                        roots.add(root)
                    }
                }
            } catch (_: Exception) {
            }
        }

        val activeRoot = rootInActiveWindow
        if (activeRoot != null && !skipRoot(activeRoot)) {
            val b = Rect()
            activeRoot.getBoundsInScreen(b)
            if (b.width() > 0 && b.height() > 0) {
                roots.add(activeRoot)
            }
        }

        var best: SmartMatch? = null
        var visited = 0

        for (root in roots) {
            val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
            stack.add(root)

            while (!stack.isEmpty() && visited < 1200) {
                visited++

                val node = stack.removeLast() ?: continue

                if (!node.isVisibleToUser || !node.isEnabled) {
                    continue
                }

                val text = node.text?.toString()?.trim()?.lowercase()
                val desc = node.contentDescription?.toString()?.trim()?.lowercase()

                val exact =
                    text == wantedLower ||
                    desc == wantedLower

                if (exact) {
                    val clickable = findClickableParent(node) ?: node
                    if (clickable.isVisibleToUser && clickable.isEnabled) {
                        val bounds = Rect()
                        clickable.getBoundsInScreen(bounds)

                        if (bounds.width() > 0 && bounds.height() > 0) {
                            var score = 500

                            // Agar multiple same action mil jaye, screen ke visible/compact toolbar ko prefer karo.
                            if (clickable.isClickable) score += 50

                            // Old coordinate ko sirf tie-breaker banao, main condition nahi.
                            if (gesture.xPercent > 0f && gesture.yPercent > 0f) {
                                val sw = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
                                val sh = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
                                val cxP = bounds.exactCenterX() / sw
                                val cyP = bounds.exactCenterY() / sh
                                val dx = abs(cxP - gesture.xPercent)
                                val dy = abs(cyP - gesture.yPercent)

                                if (dx < 0.25f && dy < 0.25f) score += 20
                            }

                            if (best == null || score > best!!.score) {
                                best = SmartMatch(clickable, Rect(bounds), score)
                            }
                        }
                    }
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) stack.add(child)
                }
            }
        }

        return best
    }

'''

if "private fun findExactActionButtonAcrossWindows(" not in aas:
    aas = aas.replace(
        "    private fun findBestSmartTarget(",
        helpers + "\n    private fun findBestSmartTarget("
    )

# ==========================================================
# 2) Hook inside findBestSmartTarget: exact toolbar action first
# ==========================================================
old_start = '''    private fun findBestSmartTarget(gesture: RecordedGesture): SmartMatch? {
        val screenW = resources.displayMetrics.widthPixels.toFloat()'''

new_start = '''    private fun findBestSmartTarget(gesture: RecordedGesture): SmartMatch? {
        // 🔥 Toolbar action buttons: Copy/Cut/Paste/Share/Translate
        // Inka order change ho sakta hai, isliye exact text ko sabse pehle dhoondo.
        findExactActionButtonAcrossWindows(gesture)?.let {
            return it
        }

        val screenW = resources.displayMetrics.widthPixels.toFloat()'''

if old_start in aas:
    aas = aas.replace(old_start, new_start)
else:
    print("⚠️ findBestSmartTarget start exact match nahi mila. Manual check zaroor karna.")

# ==========================================================
# 3) Smart click wait guard, agar missing ho
# ==========================================================
old_click = '''                val clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    showTinyToast("🎯 Smart Click")
                    return
                }'''

new_click = '''                val clicked = try {
                    clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } catch (_: Exception) {
                    false
                }

                if (clicked) {
                    showTinyToast("🎯 Smart Click")
                    activeGestureCount.incrementAndGet()
                    handler.postDelayed({
                        decrementActiveGestureSafely()
                    }, 400L)
                    return
                }'''

aas = aas.replace(old_click, new_click)

with open(AAS, "w", encoding="utf-8") as f:
    f.write(aas)

print("✅ DONE: Toolbar exact action button scanner applied.")
