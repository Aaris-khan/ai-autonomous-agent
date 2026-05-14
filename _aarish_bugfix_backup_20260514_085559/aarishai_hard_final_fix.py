from pathlib import Path
import time, sys

MAIN = Path("app/src/main/java/com/aarishkhan/aarishai/MainActivity.kt")
FLOAT = Path("app/src/main/java/com/aarishkhan/aarishai/FloatingControlService.kt")
AUTO = Path("app/src/main/java/com/aarishkhan/aarishai/AutoActionService.kt")

def backup(p):
    if p.exists():
        p.with_suffix(p.suffix + f".bak_{int(time.time())}").write_text(p.read_text(), encoding="utf-8")

def read(p):
    return p.read_text(encoding="utf-8")

def write(p, s):
    p.write_text(s, encoding="utf-8")

def replace_between(s, start, end, new):
    a = s.find(start)
    if a == -1:
        print(f"⚠️ start not found: {start[:70]}")
        return s
    b = s.find(end, a + len(start))
    if b == -1:
        print(f"⚠️ end not found after: {start[:70]}")
        return s
    return s[:a] + new.rstrip() + "\n\n    " + s[b:]

for p in [MAIN, FLOAT, AUTO]:
    if not p.exists():
        print(f"❌ Missing file: {p}")
        sys.exit(1)
    backup(p)

# ======================================================
# MainActivity.kt hard rewrite
# ======================================================
write(MAIN, r'''package com.aarishkhan.aarishai

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var btnScreenCommand: Button
    private var isWaitingForPermission = false
    private var lastPermissionLaunchAt = 0L
    private var notificationPermissionAskedThisSession = false
    private var lastPermissionScreen: String? = null
    private var autoPermissionPromptDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isWaitingForPermission = savedInstanceState?.getBoolean(KEY_WAITING_PERMISSION, false) ?: false
        lastPermissionLaunchAt = savedInstanceState?.getLong(KEY_LAST_PERMISSION_LAUNCH_AT, 0L) ?: 0L
        notificationPermissionAskedThisSession = savedInstanceState?.getBoolean(KEY_NOTIFICATION_ASKED, false) ?: false
        lastPermissionScreen = savedInstanceState?.getString(KEY_LAST_PERMISSION_SCREEN)
        autoPermissionPromptDone = savedInstanceState?.getBoolean(KEY_AUTO_PERMISSION_PROMPT_DONE, false) ?: false

        setContentView(R.layout.activity_main)

        btnScreenCommand = findViewById(R.id.btnScreenCommand)
        btnScreenCommand.setOnClickListener {
            lastPermissionScreen = null
            autoPermissionPromptDone = false
            startScreenCommandSystem(forceOpenSettings = true)
        }
    }

    override fun onResume() {
        super.onResume()

        if (isWaitingForPermission) {
            isWaitingForPermission = false
            val returnedFrom = lastPermissionScreen
            lastPermissionScreen = null

            if (hasOverlayPermission() && isAccessibilityServiceEnabled()) {
                autoPermissionPromptDone = false
                startScreenCommandSystem(forceOpenSettings = false)
                return
            }

            if (returnedFrom == "overlay" && hasOverlayPermission() && !isAccessibilityServiceEnabled()) {
                startScreenCommandSystem(forceOpenSettings = true)
                return
            }

            Toast.makeText(
                this,
                "Permission enable karke SCREEN COMMAND dobara dabao",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if ((!hasOverlayPermission() || !isAccessibilityServiceEnabled()) && !autoPermissionPromptDone) {
            autoPermissionPromptDone = true
            startScreenCommandSystem(forceOpenSettings = true)
        }
    }

    private fun startScreenCommandSystem(forceOpenSettings: Boolean) {
        if (!hasOverlayPermission()) {
            if (forceOpenSettings && canLaunchPermissionScreenSafely()) {
                Toast.makeText(this, "Overlay permission ON karo", Toast.LENGTH_LONG).show()
                isWaitingForPermission = true
                lastPermissionScreen = "overlay"
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            if (forceOpenSettings && canLaunchPermissionScreenSafely()) {
                Toast.makeText(this, "Accessibility Service ON karo", Toast.LENGTH_LONG).show()
                isWaitingForPermission = true
                lastPermissionScreen = "accessibility"
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !notificationPermissionAskedThisSession
        ) {
            notificationPermissionAskedThisSession = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATION_PERMISSION)
            return
        }

        autoPermissionPromptDone = false

        if (FloatingControlService.instance != null) {
            moveTaskToBack(true)
            return
        }

        try {
            val serviceIntent = Intent(this, FloatingControlService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            moveTaskToBack(true)
        } catch (e: Exception) {
            Toast.makeText(this, "Service start nahi hua: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun canLaunchPermissionScreenSafely(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastPermissionLaunchAt < 1200L) return false
        lastPermissionLaunchAt = now
        return true
    }

    private fun hasOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                    this,
                    "Notification permission deny hai. Service phir bhi start ho sakti hai.",
                    Toast.LENGTH_LONG
                ).show()
            }
            startScreenCommandSystem(forceOpenSettings = false)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = ComponentName(this, AutoActionService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        for (service in splitter) {
            if (service.equals(expectedService, ignoreCase = true)) return true
        }
        return false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_WAITING_PERMISSION, isWaitingForPermission)
        outState.putLong(KEY_LAST_PERMISSION_LAUNCH_AT, lastPermissionLaunchAt)
        outState.putBoolean(KEY_NOTIFICATION_ASKED, notificationPermissionAskedThisSession)
        outState.putString(KEY_LAST_PERMISSION_SCREEN, lastPermissionScreen)
        outState.putBoolean(KEY_AUTO_PERMISSION_PROMPT_DONE, autoPermissionPromptDone)
    }

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 5001
        private const val KEY_WAITING_PERMISSION = "waiting_permission"
        private const val KEY_LAST_PERMISSION_LAUNCH_AT = "last_permission_launch_at"
        private const val KEY_NOTIFICATION_ASKED = "notification_asked"
        private const val KEY_LAST_PERMISSION_SCREEN = "last_permission_screen"
        private const val KEY_AUTO_PERMISSION_PROMPT_DONE = "auto_permission_prompt_done"
    }
}
''')

# ======================================================
# FloatingControlService.kt targeted patch
# ======================================================
s = read(FLOAT)

s = s.replace("    private var btnSpeed: android.widget.Button? = null\n", "")
s = s.replace("    private var btnSettings: android.widget.Button? = null\n", "")
s = s.replace("        btnSettings?.visibility = if (showOthers) View.VISIBLE else View.GONE\n", "")
s = s.replace("        btnSpeed?.visibility = if (showOthers) View.VISIBLE else View.GONE\n", "")

old = '''        btnLoop.visibility = if (GestureStore.hasRecording(this)) View.VISIBLE else View.GONE

        val params = panelParams'''
new = '''        val hasSavedRecording = GestureStore.hasRecording(this)
        btnLoop.visibility = if (hasSavedRecording) View.VISIBLE else View.GONE
        btnClear?.visibility = if (hasSavedRecording && unsavedGestures.isEmpty()) View.VISIBLE else View.GONE

        val params = panelParams'''
s = s.replace(old, new)

old = '''        if (isRecording) {
            extractAndAppendGestures()
            isRecording = false
            safeRemoveView(captureView)
            captureView = null
        }'''
new = '''        if (isRecording) {
            val liveView = captureView
            if (liveView != null) {
                extractAndAppendGestures()
                safeRemoveView(liveView)
            } else {
                Toast.makeText(this, "⚠️ Recording layer missing thi. Saved buffer hi save hoga.", Toast.LENGTH_LONG).show()
            }
            isRecording = false
            captureView = null
        }'''
s = s.replace(old, new)

write(FLOAT, s)

# ======================================================
# AutoActionService.kt hard patch
# ======================================================
s = read(AUTO)

helper = r'''    private data class SmartMatch(
        val node: AccessibilityNodeInfo,
        val bounds: Rect,
        val score: Int
    ) {
        val centerX: Float get() = bounds.exactCenterX()
        val centerY: Float get() = bounds.exactCenterY()
    }

    private fun safeVisible(node: AccessibilityNodeInfo?): Boolean =
        try { node?.isVisibleToUser == true } catch (_: Exception) { false }

    private fun safeEnabled(node: AccessibilityNodeInfo?): Boolean =
        try { node?.isEnabled == true } catch (_: Exception) { false }

    private fun safeClickable(node: AccessibilityNodeInfo?): Boolean =
        try { node?.isClickable == true } catch (_: Exception) { false }

    private fun safeChildCount(node: AccessibilityNodeInfo?): Int =
        try { node?.childCount ?: 0 } catch (_: Exception) { 0 }

    private fun safeChild(node: AccessibilityNodeInfo?, index: Int): AccessibilityNodeInfo? =
        try { node?.getChild(index) } catch (_: Exception) { null }

    private fun safeParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
        try { node?.parent } catch (_: Exception) { null }

    private fun safeText(node: AccessibilityNodeInfo?): String? =
        try { node?.text?.toString() } catch (_: Exception) { null }

    private fun safeDesc(node: AccessibilityNodeInfo?): String? =
        try { node?.contentDescription?.toString() } catch (_: Exception) { null }

    private fun safeId(node: AccessibilityNodeInfo?): String? =
        try { node?.viewIdResourceName } catch (_: Exception) { null }

    private fun safeClass(node: AccessibilityNodeInfo?): String? =
        try { node?.className?.toString() } catch (_: Exception) { null }

    private fun safeBounds(node: AccessibilityNodeInfo?, out: Rect): Boolean =
        try {
            node?.getBoundsInScreen(out)
            node != null
        } catch (_: Exception) {
            false
        }
'''

s = replace_between(
    s,
    "    private data class SmartMatch(",
    "private fun normalizeUltraText",
    helper
)

s = replace_between(
    s,
    "private fun ownLabelOf",
    "private fun collectNodeTextLimited",
r'''private fun ownLabelOf(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        val text = safeText(node).orEmpty()
        val desc = safeDesc(node).orEmpty()
        val id = safeId(node)?.substringAfterLast("/").orEmpty()

        return listOf(text, desc, id)
            .filter { it.isNotBlank() }
            .joinToString(" | ")
    }'''
)

s = replace_between(
    s,
    "private fun collectNodeTextLimited",
    "private fun collectSiblingText",
r'''private fun collectNodeTextLimited(
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

            val count = safeChildCount(n)
            for (i in 0 until count) {
                val child = safeChild(n, i)
                if (child != null) stack.add(child)
            }
        }

        return out.toString().take(maxChars)
    }'''
)

s = replace_between(
    s,
    "private fun collectSiblingText",
    "private fun roleFlagsOf",
r'''private fun collectSiblingText(
        node: AccessibilityNodeInfo?,
        maxChars: Int = 300
    ): String {
        val parent = safeParent(node) ?: return ""
        val nodeBounds = Rect()
        val nodeClass = safeClass(node).orEmpty()
        if (!safeBounds(node, nodeBounds)) return ""

        val out = StringBuilder()
        val count = safeChildCount(parent)

        for (i in 0 until count) {
            val child = safeChild(parent, i) ?: continue
            val childBounds = Rect()
            if (!safeBounds(child, childBounds)) continue

            val sameNode = childBounds == nodeBounds && safeClass(child).orEmpty() == nodeClass
            if (sameNode) continue

            val label = collectNodeTextLimited(child, maxNodes = 14, maxChars = 100)
            if (label.isNotBlank()) {
                if (out.isNotEmpty()) out.append(" | ")
                out.append(label)
            }
            if (out.length >= maxChars) break
        }

        return out.toString().take(maxChars)
    }'''
)

s = replace_between(
    s,
    "private fun roleFlagsOf",
    "private fun roleSimilarity",
r'''private fun roleFlagsOf(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        val flags = mutableListOf<String>()

        try { if (node.isClickable) flags.add("click") } catch (_: Exception) {}
        try { if (node.isLongClickable) flags.add("long") } catch (_: Exception) {}
        try { if (node.isEditable) flags.add("edit") } catch (_: Exception) {}
        try { if (node.isScrollable) flags.add("scroll") } catch (_: Exception) {}
        try { if (node.isCheckable) flags.add("check") } catch (_: Exception) {}
        try { if (node.isChecked) flags.add("checked") } catch (_: Exception) {}
        try { if (node.isEnabled) flags.add("enabled") } catch (_: Exception) {}
        try { if (node.isVisibleToUser) flags.add("visible") } catch (_: Exception) {}
        try { if (node.isFocusable) flags.add("focus") } catch (_: Exception) {}

        return flags.joinToString("|")
    }'''
)

s = replace_between(
    s,
    "private fun extractTreePathDNA",
    "private fun dnaSimilarity",
r'''private fun extractTreePathDNA(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        val parts = mutableListOf<String>()
        var current: AccessibilityNodeInfo? = node
        var depth = 0

        while (current != null && depth < 12) {
            val cls = safeClass(current)?.substringAfterLast(".") ?: "N"
            val parent = safeParent(current)
            if (parent === current) break

            var index = -1

            if (parent != null) {
                val currentBounds = Rect()
                safeBounds(current, currentBounds)

                val count = safeChildCount(parent)
                for (i in 0 until count) {
                    val child = safeChild(parent, i) ?: continue
                    val childBounds = Rect()
                    safeBounds(child, childBounds)

                    val sameClass = safeClass(child) == safeClass(current)
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
    }'''
)

s = replace_between(
    s,
    "private fun findBestSmartTarget",
    "private fun scoreNode",
r'''private fun findBestSmartTarget(gesture: RecordedGesture): SmartMatch? {
        return try {
            findExactActionButtonAcrossWindows(gesture)?.let {
                return it
            }

            val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
            val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

            val fallbackX = if (gesture.xPercent > 0f) {
                gesture.xPercent * screenW
            } else {
                gesture.points.firstOrNull()?.x ?: 0f
            }

            val fallbackY = if (gesture.yPercent > 0f) {
                gesture.yPercent * screenH
            } else {
                gesture.points.firstOrNull()?.y ?: 0f
            }

            val root = getRealAppRootForPoint(fallbackX.toInt(), fallbackY.toInt()) ?: return null

            var best: SmartMatch? = null
            val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
            stack.add(root)

            var visitedNodes = 0

            while (!stack.isEmpty() && visitedNodes < 900) {
                visitedNodes++
                val node = stack.removeLast()

                val bounds = Rect()
                if (safeBounds(node, bounds) && bounds.width() > 0 && bounds.height() > 0) {
                    val score = scoreNode(node, bounds, gesture)
                    if (score > 0) {
                        val targetNode = findClickableParent(node) ?: node
                        if (!safeVisible(targetNode) || !safeEnabled(targetNode)) {
                            continue
                        }

                        val targetBounds = Rect()
                        if (safeBounds(targetNode, targetBounds) &&
                            targetBounds.width() > 0 &&
                            targetBounds.height() > 0
                        ) {
                            var finalScore = score + if (safeClickable(targetNode)) 12 else 0

                            if (gesture.targetWPercent > 0f && gesture.targetHPercent > 0f) {
                                val wNow = targetBounds.width() / screenW
                                val hNow = targetBounds.height() / screenH
                                val wDiff = kotlin.math.abs(wNow - gesture.targetWPercent)
                                val hDiff = kotlin.math.abs(hNow - gesture.targetHPercent)

                                if (wDiff < 0.04f && hDiff < 0.04f) finalScore += 25
                                else if (wDiff < 0.08f && hDiff < 0.08f) finalScore += 12
                            }

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
                        }
                    }
                }

                val count = safeChildCount(node)
                for (i in 0 until count) {
                    val child = safeChild(node, i)
                    if (child != null) stack.add(child)
                }
            }

            val finalBest = best ?: return null
            if (finalBest.score >= 55) finalBest else null
        } catch (_: Exception) {
            null
        }
    }'''
)

s = replace_between(
    s,
    "private fun scoreNode",
    "private fun hasRealMovement",
r'''private fun scoreNode(
        node: AccessibilityNodeInfo,
        bounds: Rect,
        gesture: RecordedGesture
    ): Int {
        return try {
            if (!safeVisible(node) || !safeEnabled(node)) return 0

            var score = 0

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

            val nodeText = safeText(node)?.trim()
            val nodeDesc = safeDesc(node)?.trim()
            val nodeId = safeId(node)?.trim()
            val nodeClass = safeClass(node)?.trim()

            if (!gesture.targetId.isNullOrBlank() && nodeId == gesture.targetId) {
                score += 100
            }

            val exactDescMatch =
                !gesture.targetDesc.isNullOrBlank() &&
                nodeDesc?.equals(gesture.targetDesc, ignoreCase = true) == true

            if (exactDescMatch) score += 90

            val exactTextMatch =
                !gesture.targetText.isNullOrBlank() &&
                nodeText?.equals(gesture.targetText, ignoreCase = true) == true

            if (exactTextMatch) score += 90

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
            }

            if (!gesture.targetClass.isNullOrBlank() && !nodeClass.isNullOrBlank()) {
                val savedSimple = gesture.targetClass.substringAfterLast('.').lowercase()
                val nowSimple = nodeClass.substringAfterLast('.').lowercase()

                if (savedSimple == nowSimple ||
                    (savedSimple.contains("button") && nowSimple.contains("button")) ||
                    (savedSimple.contains("text") && nowSimple.contains("text")) ||
                    (savedSimple.contains("image") && nowSimple.contains("image"))
                ) {
                    score += 18
                }
            }

            val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
            val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

            val wPercentNow = bounds.width() / screenW
            val hPercentNow = bounds.height() / screenH

            if (gesture.targetWPercent > 0f && gesture.targetHPercent > 0f) {
                val wDiff = abs(wPercentNow - gesture.targetWPercent)
                val hDiff = abs(hPercentNow - gesture.targetHPercent)

                if (wDiff < 0.04f && hDiff < 0.04f) score += 20
                else if (wDiff < 0.08f && hDiff < 0.08f) score += 10
            }

            if (gesture.xPercent > 0f && gesture.yPercent > 0f) {
                val cxP = bounds.exactCenterX() / screenW
                val cyP = bounds.exactCenterY() / screenH

                val dx = abs(cxP - gesture.xPercent)
                val dy = abs(cyP - gesture.yPercent)

                if (dx < 0.05f && dy < 0.05f) score += 60
                else if (dx < 0.15f && dy < 0.15f) score += 30
                else if (dx < 0.28f && dy < 0.28f) score += 10
                else if (dx > 0.40f || dy > 0.40f) {
                    val strongIdentity =
                        (!gesture.targetId.isNullOrBlank() && nodeId == gesture.targetId) ||
                        exactTextMatch ||
                        exactDescMatch ||
                        contextSimUltra >= 0.70f ||
                        childSimUltra >= 0.70f ||
                        siblingSimUltra >= 0.70f ||
                        dnaSimUltra >= 0.80f

                    score -= if (strongIdentity) 0 else 45
                }
            }

            if (safeClickable(node)) score += 8

            val hasAnyIdentity =
                !gesture.targetId.isNullOrBlank() ||
                !gesture.targetText.isNullOrBlank() ||
                !gesture.targetDesc.isNullOrBlank() ||
                !gesture.targetClass.isNullOrBlank() ||
                !gesture.targetContextText.isNullOrBlank() ||
                !gesture.targetChildText.isNullOrBlank() ||
                !gesture.targetSiblingText.isNullOrBlank() ||
                !gesture.targetRoleFlags.isNullOrBlank() ||
                !gesture.targetTreePath.isNullOrBlank() ||
                gesture.targetWPercent > 0f

            if (!hasAnyIdentity) return 0

            score
        } catch (_: Exception) {
            0
        }
    }'''
)

s = replace_between(
    s,
    "private fun findDeepestNodeAtCoordinate",
    "private fun performHybridSelectionRetry",
r'''private fun findDeepestNodeAtCoordinate(
        root: AccessibilityNodeInfo?,
        x: Int,
        y: Int
    ): AccessibilityNodeInfo? {
        if (root == null) return null

        return try {
            val stack = java.util.ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
            stack.add(Pair(root, 0))

            var bestNode: AccessibilityNodeInfo? = null
            var bestScore = Int.MIN_VALUE

            while (!stack.isEmpty()) {
                val item = stack.removeLast()
                val node = item.first
                val depth = item.second

                val bounds = Rect()
                if (!safeBounds(node, bounds)) continue

                if (bounds.contains(x, y)) {
                    val area = (bounds.width() * bounds.height()).coerceAtLeast(1)
                    val clickableBonus = if (safeClickable(node)) 800 else 0
                    val leafBonus = if (safeChildCount(node) == 0) 300 else 0
                    val smallAreaBonus = 500000 / area.coerceAtLeast(1)

                    val candidateScore =
                        (depth * 1000) +
                        clickableBonus +
                        leafBonus +
                        smallAreaBonus

                    if (candidateScore > bestScore) {
                        bestScore = candidateScore
                        bestNode = node
                    }

                    val count = safeChildCount(node)
                    for (i in 0 until count) {
                        val child = safeChild(node, i)
                        if (child != null) {
                            stack.add(Pair(child, depth + 1))
                        }
                    }
                }
            }

            bestNode
        } catch (_: Exception) {
            null
        }
    }'''
)

s = replace_between(
    s,
    "private fun dispatchHybridGesturePath",
    "private fun isSelectionUiVisibleHybrid",
r'''private fun dispatchHybridGesturePath(
        startX: Float,
        startY: Float,
        points: List<GesturePoint>,
        duration: Long,
        isDoubleTap: Boolean,
        runId: Int,
        onDone: (Boolean) -> Unit
    ) {
        if (!isSamePlaybackRun(runId)) {
            onDone(false)
            return
        }

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            onDone(false)
            return
        }

        val screenW = (resources.displayMetrics.widthPixels.toFloat() - 2f).coerceAtLeast(2f)
        val screenH = (resources.displayMetrics.heightPixels.toFloat() - 2f).coerceAtLeast(2f)
        val safeX = startX.coerceIn(2f, screenW)
        val safeY = startY.coerceIn(2f, screenH)
        val holdDuration = duration.coerceAtLeast(650L).coerceAtMost(3500L)
        val callbackDelivered = java.util.concurrent.atomic.AtomicBoolean(false)

        fun finishOnce(value: Boolean) {
            if (callbackDelivered.compareAndSet(false, true)) {
                onDone(value)
            }
        }

        fun tinyPath(): Path {
            return Path().apply {
                moveTo(safeX, safeY)
                lineTo((safeX + 1f).coerceIn(2f, screenW), (safeY + 1f).coerceIn(2f, screenH))
            }
        }

        fun dispatchBuilder(builder: GestureDescription.Builder, done: (Boolean) -> Unit) {
            val gesture = try {
                builder.build()
            } catch (_: Exception) {
                done(false)
                return
            }

            val accepted = dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        done(isCurrentCallbackRun(runId))
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        done(false)
                    }
                },
                null
            )

            if (!accepted) done(false)
        }

        if (isDoubleTap) {
            val firstTap = GestureDescription.Builder().apply {
                addStroke(GestureDescription.StrokeDescription(tinyPath(), 0L, 90L))
            }

            dispatchBuilder(firstTap) { firstOk ->
                if (!firstOk || !isSamePlaybackRun(runId)) {
                    finishOnce(false)
                } else {
                    handler.postDelayed({
                        if (!isSamePlaybackRun(runId)) {
                            finishOnce(false)
                        } else {
                            val secondHold = GestureDescription.Builder().apply {
                                addStroke(GestureDescription.StrokeDescription(tinyPath(), 0L, holdDuration))
                            }
                            dispatchBuilder(secondHold) { secondOk ->
                                finishOnce(secondOk && isCurrentCallbackRun(runId))
                            }
                        }
                    }, 120L)
                }
            }
            return
        }

        val path = Path().apply { moveTo(safeX, safeY) }
        var hasLine = false
        val first = points.firstOrNull()
        if (first != null && points.size > 1) {
            for (i in 1 until points.size) {
                val p = points[i]
                val shiftedX = startX + (p.x - first.x)
                val shiftedY = startY + (p.y - first.y)
                path.lineTo(shiftedX.coerceIn(2f, screenW), shiftedY.coerceIn(2f, screenH))
                hasLine = true
            }
        }

        if (!hasLine) {
            path.lineTo((safeX + 1f).coerceIn(2f, screenW), (safeY + 1f).coerceIn(2f, screenH))
        }

        val normalBuilder = GestureDescription.Builder().apply {
            addStroke(GestureDescription.StrokeDescription(path, 0L, holdDuration))
        }

        dispatchBuilder(normalBuilder) { completed ->
            finishOnce(completed && isCurrentCallbackRun(runId))
        }
    }'''
)

s = replace_between(
    s,
    "private fun isSelectionUiVisibleHybrid",
    "private fun findClickableParent",
r'''private fun isSelectionUiVisibleHybrid(): Boolean {
        return try {
            val roots = mutableListOf<AccessibilityNodeInfo>()

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                try {
                    for (window in windows) {
                        if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
                        val root = window.root ?: continue
                        roots.add(root)
                    }
                } catch (_: Exception) {
                }
            }

            rootInActiveWindow?.let { roots.add(it) }
            if (roots.isEmpty()) return false

            val markers = listOf(
                "copy", "select", "select all", "cut", "paste", "share", "translate",
                "कॉपी", "चुनें", "सब चुनें", "कट", "पेस्ट", "अनुवाद",
                "نسخ", "چنیں", "سب منتخب", "قص", "چسپاں", "ترجمہ"
            )

            var visited = 0
            for (root in roots) {
                val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
                stack.add(root)

                while (!stack.isEmpty() && visited < 1600) {
                    visited++
                    val node = stack.removeLast()

                    try {
                        if (node.textSelectionStart >= 0 &&
                            node.textSelectionEnd >= 0 &&
                            node.textSelectionStart != node.textSelectionEnd
                        ) return true
                    } catch (_: Exception) {
                    }

                    try {
                        if (node.isSelected) return true
                    } catch (_: Exception) {
                    }

                    val text = safeText(node)?.lowercase() ?: ""
                    val desc = safeDesc(node)?.lowercase() ?: ""
                    val id = safeId(node)?.lowercase() ?: ""
                    val label = "$text $desc $id"

                    if (label.contains("floating_toolbar") || label.contains("selection")) return true
                    if (safeVisible(node)) {
                        val clickableOrFocusable = try { node.isClickable || node.isFocusable } catch (_: Exception) { false }
                        if (clickableOrFocusable && markers.any { label.contains(it) }) return true
                    }

                    val count = safeChildCount(node)
                    for (i in 0 until count) {
                        val child = safeChild(node, i)
                        if (child != null) stack.add(child)
                    }
                }
            }

            false
        } catch (_: Exception) {
            false
        }
    }'''
)

s = replace_between(
    s,
    "private fun findClickableParent",
    "private fun showTinyToast",
r'''private fun findClickableParent(
        node: AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        var current = node
        var depth = 0

        while (current != null && depth < 30) {
            if (safeClickable(current)) return current
            val next = safeParent(current)
            if (next == current) return null
            current = next
            depth++
        }

        return null
    }'''
)

write(AUTO, s)

print("✅ Hard final fixes applied.")
print("➡️ Running build now...")
