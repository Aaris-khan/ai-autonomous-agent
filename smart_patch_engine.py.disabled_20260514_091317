import os, re, sys, time, shutil
from pathlib import Path

# Source reviewed from uploaded project: 0
# Android AccessibilityService guidance researched from official Android docs: 1

BASE_REL = Path("app/src/main/java/com/aarishkhan/aarishai")
XML_REL = Path("app/src/main/res/xml/accessibility_service_config.xml")
BTN_BG_REL = Path("app/src/main/res/drawable/bg_screen_command_button.xml")
MAIN_BG_REL = Path("app/src/main/res/drawable/bg_main_soft.xml")

def find_project_root() -> Path:
    candidates = [
        Path.cwd(),
        Path.home() / "aarishai",
        Path.home() / "AarishAI",
        Path.home() / "AarishNative",
        Path("/sdcard/Download/aarishai"),
        Path("/storage/emulated/0/Download/aarishai"),
    ]
    for c in candidates:
        if (c / BASE_REL).exists():
            return c

    search_roots = [Path.cwd(), Path.home(), Path("/sdcard/Download"), Path("/storage/emulated/0/Download")]
    seen = set()
    for root in search_roots:
        try:
            root = root.resolve()
        except Exception:
            continue
        if str(root) in seen or not root.exists():
            continue
        seen.add(str(root))
        try:
            for hit in root.rglob("app/src/main/java/com/aarishkhan/aarishai/AutoActionService.kt"):
                return hit.parents[6]
        except Exception:
            pass

    print("❌ ERROR: Project root nahi mila. Is command ko project folder ke andar run karo.")
    sys.exit(1)

ROOT = find_project_root()
AUTO = ROOT / BASE_REL / "AutoActionService.kt"
FLOATING = ROOT / BASE_REL / "FloatingControlService.kt"
XML = ROOT / XML_REL
BTN_BG = ROOT / BTN_BG_REL
MAIN_BG = ROOT / MAIN_BG_REL

for p in [AUTO, FLOATING]:
    if not p.exists():
        print(f"❌ ERROR: File nahi mili: {p}")
        sys.exit(1)

STAMP = time.strftime("%Y%m%d_%H%M%S")
BACKUP_DIR = ROOT / f"aarish_patch_backup_{STAMP}"
BACKUP_DIR.mkdir(parents=True, exist_ok=True)

def backup(path: Path):
    if path.exists():
        dest = BACKUP_DIR / path.relative_to(ROOT)
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, dest)

def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")

def write(path: Path, data: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(data, encoding="utf-8")

def replace_between(content: str, start_pat: str, end_pat: str, new_block: str, label: str) -> str:
    start = re.search(start_pat, content, re.S)
    if not start:
        print(f"❌ ERROR: Start marker nahi mila -> {label}")
        sys.exit(1)
    end = re.search(end_pat, content[start.end():], re.S)
    if not end:
        print(f"❌ ERROR: End marker nahi mila -> {label}")
        sys.exit(1)
    a = start.start()
    b = start.end() + end.start()
    return content[:a] + new_block.strip("\n") + "\n\n    " + content[b:].lstrip()

def replace_first(content: str, old: str, new: str, label: str) -> str:
    if old not in content:
        print(f"⚠️ WARNING: Exact block nahi mila -> {label}")
        return content
    return content.replace(old, new, 1)

# ==========================================================
# 🧠 AutoActionService.kt — Offline Smart Click v5
# ==========================================================
backup(AUTO)
auto = read(AUTO)

smart_ambiguity_and_identity = r'''
private fun smartIdentityConfidence(node: AccessibilityNodeInfo?, gesture: RecordedGesture): Float {
    if (node == null) return 0f

    return try {
        val actionNode = findClickableParent(node) ?: node

        val nodeText = safeText(node)
        val nodeDesc = safeDesc(node)
        val nodeId = safeId(node)
        val actionText = safeText(actionNode)
        val actionDesc = safeDesc(actionNode)
        val actionId = safeId(actionNode)

        val nodeOwn = ownLabelOf(node)
        val actionOwn = ownLabelOf(actionNode)
        val nodeContext = collectNodeTextLimited(node, 36, 420)
        val actionContext = collectNodeTextLimited(actionNode, 72, 760)
        val parentContext = collectNodeTextLimited(safeParent(actionNode), 28, 360)
        val siblingContext = collectSiblingText(actionNode, 420)
        val mergedContext = listOf(nodeOwn, actionOwn, nodeContext, actionContext, parentContext, siblingContext)
            .filter { it.isNotBlank() }
            .joinToString(" | ")
            .take(1400)

        var best = 0f

        val savedId = gesture.targetId?.trim()
        if (!savedId.isNullOrBlank()) {
            val savedTail = idTail(savedId)
            val nodeTail = idTail(nodeId)
            val actionTail = idTail(actionId)

            if (nodeId == savedId || actionId == savedId) best = maxOf(best, 1f)
            else if (savedTail.isNotBlank() && (savedTail == nodeTail || savedTail == actionTail)) best = maxOf(best, 0.88f)
            else best = maxOf(best, tokenSimilarity(savedTail, mergedContext) * 0.72f)
        }

        val savedText = gesture.targetText?.trim()
        if (!savedText.isNullOrBlank()) {
            best = maxOf(
                best,
                tokenSimilarity(savedText, nodeText),
                tokenSimilarity(savedText, actionText),
                tokenSimilarity(savedText, nodeDesc) * 0.94f,
                tokenSimilarity(savedText, actionDesc) * 0.94f,
                tokenSimilarity(savedText, mergedContext) * 0.86f
            )
        }

        val savedDesc = gesture.targetDesc?.trim()
        if (!savedDesc.isNullOrBlank()) {
            best = maxOf(
                best,
                tokenSimilarity(savedDesc, nodeDesc),
                tokenSimilarity(savedDesc, actionDesc),
                tokenSimilarity(savedDesc, nodeText) * 0.92f,
                tokenSimilarity(savedDesc, actionText) * 0.92f,
                tokenSimilarity(savedDesc, mergedContext) * 0.84f
            )
        }

        best = maxOf(best, tokenSimilarity(gesture.targetContextText, mergedContext) * 0.82f)
        best = maxOf(best, tokenSimilarity(gesture.targetChildText, nodeContext) * 0.72f)
        best = maxOf(best, tokenSimilarity(gesture.targetSiblingText, siblingContext) * 0.68f)
        best = maxOf(best, roleSimilarity(gesture.targetRoleFlags, roleFlagsOf(actionNode)) * 0.58f)
        best = maxOf(best, dnaSimilarity(gesture.targetTreePath, extractTreePathDNA(actionNode)) * 0.74f)

        if (best >= 0.58f && hasSavedPercentAnchor(gesture)) {
            val b = Rect()
            if (safeBounds(actionNode, b)) {
                val dist = projectedTapDistance(b, gesture)
                if (dist < 0.08f) best += 0.10f
                else if (dist < 0.18f) best += 0.05f
            }
        }

        best.coerceIn(0f, 1f)
    } catch (_: Exception) {
        0f
    }
}

private fun isAmbiguousSmartMatch(
    best: SmartMatch,
    runnerUp: SmartMatch,
    gesture: RecordedGesture
): Boolean {
    val gap = best.score - runnerUp.score
    if (gap >= 22) return false

    val bestExact = exactIdentityHit(best.node, gesture)
    val runnerExact = exactIdentityHit(runnerUp.node, gesture)

    if (bestExact && !runnerExact) return false
    if (!bestExact && runnerExact && gap < 30) return true

    val bestConfidence = smartIdentityConfidence(best.node, gesture)
    val runnerConfidence = smartIdentityConfidence(runnerUp.node, gesture)

    if (bestConfidence >= 0.86f && bestConfidence - runnerConfidence >= 0.10f) return false
    if (bestConfidence >= 0.76f && gap >= 12 && bestConfidence >= runnerConfidence) return false

    if (hasSavedPercentAnchor(gesture)) {
        val bestDistance = projectedTapDistance(best.bounds, gesture)
        val runnerDistance = projectedTapDistance(runnerUp.bounds, gesture)

        if (bestDistance + 0.050f < runnerDistance) return false
        if (bestExact && runnerExact && bestDistance + 0.030f < runnerDistance) return false
        if (bestConfidence >= 0.70f && bestDistance + 0.070f < runnerDistance) return false
    }

    return runnerConfidence >= (bestConfidence - 0.045f) && gap < 14
}
'''

auto = replace_between(
    auto,
    r'private\s+fun\s+isAmbiguousSmartMatch\s*\(',
    r'private\s+fun\s+exactIdentityHit\s*\(',
    smart_ambiguity_and_identity,
    "smart ambiguity guard"
)

exact_identity = r'''
private fun exactIdentityHit(node: AccessibilityNodeInfo?, gesture: RecordedGesture): Boolean {
    if (node == null) return false

    return try {
        val actionNode = findClickableParent(node) ?: node

        val nodeText = safeText(node)
        val nodeDesc = safeDesc(node)
        val nodeId = safeId(node)
        val actionText = safeText(actionNode)
        val actionDesc = safeDesc(actionNode)
        val actionId = safeId(actionNode)

        val nodeContext = collectNodeTextLimited(node, 26, 340)
        val actionContext = collectNodeTextLimited(actionNode, 60, 680)
        val merged = listOf(ownLabelOf(node), ownLabelOf(actionNode), nodeContext, actionContext)
            .filter { it.isNotBlank() }
            .joinToString(" | ")

        val savedId = gesture.targetId?.trim()
        val idOk = !savedId.isNullOrBlank() &&
            (
                nodeId == savedId ||
                    actionId == savedId ||
                    (idTail(savedId).isNotBlank() && (idTail(nodeId) == idTail(savedId) || idTail(actionId) == idTail(savedId)))
                )

        val savedText = gesture.targetText?.trim()
        val textOk = !savedText.isNullOrBlank() &&
            (
                labelsEqual(nodeText, savedText) ||
                    labelsEqual(actionText, savedText) ||
                    labelsEqual(nodeDesc, savedText) ||
                    labelsEqual(actionDesc, savedText) ||
                    labelContains(merged, savedText) ||
                    tokenSimilarity(savedText, merged) >= 0.93f
                )

        val savedDesc = gesture.targetDesc?.trim()
        val descOk = !savedDesc.isNullOrBlank() &&
            (
                labelsEqual(nodeDesc, savedDesc) ||
                    labelsEqual(actionDesc, savedDesc) ||
                    labelsEqual(nodeText, savedDesc) ||
                    labelsEqual(actionText, savedDesc) ||
                    labelContains(merged, savedDesc) ||
                    tokenSimilarity(savedDesc, merged) >= 0.93f
                )

        idOk || textOk || descOk
    } catch (_: Exception) {
        false
    }
}
'''

auto = replace_between(
    auto,
    r'private\s+fun\s+exactIdentityHit\s*\(',
    r'private\s+fun\s+hasStrongSavedIdentity\s*\(',
    exact_identity,
    "exact identity"
)

threshold = r'''
private fun smartMatchThreshold(match: SmartMatch, gesture: RecordedGesture): Int {
    if (exactIdentityHit(match.node, gesture)) return 56

    val confidence = smartIdentityConfidence(match.node, gesture)

    if (confidence >= 0.90f) return 60
    if (confidence >= 0.80f) return 66
    if (confidence >= 0.70f) return 74

    if (!hasStrongSavedIdentity(gesture)) return 104

    if (!gesture.targetText.isNullOrBlank() ||
        !gesture.targetDesc.isNullOrBlank() ||
        !gesture.targetId.isNullOrBlank()
    ) {
        return 78
    }

    return 88
}
'''

auto = replace_between(
    auto,
    r'private\s+fun\s+smartMatchThreshold\s*\(',
    r'private\s+fun\s+collectSmartSearchRoots\s*\(',
    threshold,
    "smart threshold"
)

capture_snapshot = r'''
private fun captureTargetSnapshotInternal(
    x: Int,
    y: Int,
    screenW: Float,
    screenH: Float
): TargetSnapshot? {
    val root = getRealAppRootForPoint(x, y) ?: return null
    val touchedNode = findDeepestNodeAtCoordinate(root, x, y) ?: return null
    val clickNode = findClickableParent(touchedNode) ?: touchedNode

    val clickBounds = Rect()
    if (!safeBounds(clickNode, clickBounds) || clickBounds.width() <= 0 || clickBounds.height() <= 0) return null

    val safeW = screenW.coerceAtLeast(1f)
    val safeH = screenH.coerceAtLeast(1f)

    fun clean(value: String?): String? {
        return value?.replace(Regex("\\s+"), " ")?.trim()?.take(180)?.takeIf { it.isNotBlank() }
    }

    fun firstClean(vararg values: String?): String? {
        for (v in values) {
            val cleaned = clean(v)
            if (!cleaned.isNullOrBlank()) return cleaned
        }
        return null
    }

    val clickText = clean(safeText(clickNode))
    val touchText = clean(safeText(touchedNode))
    val clickDesc = clean(safeDesc(clickNode))
    val touchDesc = clean(safeDesc(touchedNode))
    val clickId = clean(safeId(clickNode))
    val touchId = clean(safeId(touchedNode))

    val clickOwn = ownLabelOf(clickNode)
    val touchOwn = ownLabelOf(touchedNode)
    val clickContext = collectNodeTextLimited(clickNode, 84, 860)
    val touchContext = collectNodeTextLimited(touchedNode, 52, 560)
    val siblingContext = collectSiblingText(clickNode, 620)
    val parentContext = collectNodeTextLimited(safeParent(clickNode), 50, 560)
    val grandParentContext = collectNodeTextLimited(safeParent(safeParent(clickNode)), 34, 360)

    val primaryText = firstClean(clickText, touchText, clickDesc, touchDesc, idTail(clickId), idTail(touchId))
    val primaryDesc = firstClean(clickDesc, touchDesc, clickText, touchText)

    return TargetSnapshot(
        targetText = primaryText,
        targetDesc = primaryDesc,
        targetId = firstClean(clickId, touchId),
        targetClass = firstClean(safeClass(clickNode), safeClass(touchedNode)),

        targetContextText = listOf(clickOwn, touchOwn, clickContext, touchContext, parentContext, grandParentContext)
            .filter { it.isNotBlank() }
            .joinToString(" | ")
            .take(1100),
        targetChildText = listOf(touchContext, clickContext)
            .filter { it.isNotBlank() }
            .joinToString(" | ")
            .take(700),
        targetSiblingText = siblingContext.take(700),
        targetRoleFlags = roleFlagsOf(clickNode),
        targetTreePath = extractTreePathDNA(clickNode),

        targetLeft = clickBounds.left,
        targetTop = clickBounds.top,
        targetRight = clickBounds.right,
        targetBottom = clickBounds.bottom,
        xPercent = (x / safeW).coerceIn(0f, 1f),
        yPercent = (y / safeH).coerceIn(0f, 1f),
        targetWPercent = (clickBounds.width() / safeW).coerceIn(0f, 1f),
        targetHPercent = (clickBounds.height() / safeH).coerceIn(0f, 1f),
        insideXPercent = if (clickBounds.width() > 0) ((x - clickBounds.left).toFloat() / clickBounds.width()).coerceIn(0f, 1f) else 0.5f,
        insideYPercent = if (clickBounds.height() > 0) ((y - clickBounds.top).toFloat() / clickBounds.height()).coerceIn(0f, 1f) else 0.5f,
        recordedScreenW = safeW.toInt(),
        recordedScreenH = safeH.toInt()
    )
}
'''

auto = replace_between(
    auto,
    r'private\s+fun\s+captureTargetSnapshotInternal\s*\(',
    r'private\s+fun\s+findDeepestNodeAtCoordinate\s*\(',
    capture_snapshot,
    "recording snapshot"
)

old_score_marker = '''            val siblingText = collectSiblingText(actionNode, 460)
            // AARISH_SMART_SCORE_V3: larger offline fingerprint window for shuffled UI
'''
new_score_marker = '''            val siblingText = collectSiblingText(actionNode, 460)
            val parentText = collectNodeTextLimited(safeParent(actionNode), 32, 360)
            val semanticWindow = listOf(
                ownLabelOf(node),
                ownLabelOf(actionNode),
                nodeSubtree,
                actionContext,
                parentText,
                siblingText
            ).filter { it.isNotBlank() }.joinToString(" | ").take(1500)

            val semanticConfidence = smartIdentityConfidence(actionNode, gesture)
            if (semanticConfidence >= 0.92f) score += 76
            else if (semanticConfidence >= 0.80f) score += 48
            else if (semanticConfidence >= 0.68f) score += 25
            else if (semanticConfidence >= 0.55f) score += 10

            val primarySavedLabel = listOfNotNull(
                gesture.targetText?.takeIf { it.isNotBlank() },
                gesture.targetDesc?.takeIf { it.isNotBlank() },
                idTail(gesture.targetId).takeIf { it.isNotBlank() }
            ).joinToString(" ")

            val semanticPrimarySim = tokenSimilarity(primarySavedLabel, semanticWindow)
            if (semanticPrimarySim >= 0.90f) score += 44
            else if (semanticPrimarySim >= 0.72f) score += 24
            else if (semanticPrimarySim >= 0.54f) score += 9

            // AARISH_SMART_SCORE_V5: offline semantic + geometry + role matching for shuffled UI
'''
auto = replace_first(auto, old_score_marker, new_score_marker, "score semantic booster")

old_area_penalty = '''            val areaRatio = (bounds.width().toFloat() * bounds.height().toFloat()) / (screenW * screenH).coerceAtLeast(1f)
            if (areaRatio > 0.72f && !exactIdentityHit(node, gesture) && !exactIdentityHit(actionNode, gesture)) score -= 22
            if (bounds.width() < 10 || bounds.height() < 10) score -= 18
'''
new_area_penalty = '''            val areaRatio = (bounds.width().toFloat() * bounds.height().toFloat()) / (screenW * screenH).coerceAtLeast(1f)
            val semanticConfidenceLate = smartIdentityConfidence(actionNode, gesture)
            if (areaRatio > 0.72f && !exactIdentityHit(node, gesture) && !exactIdentityHit(actionNode, gesture)) score -= 28
            if (areaRatio > 0.42f && semanticConfidenceLate < 0.62f && !exactIdentityHit(actionNode, gesture)) score -= 14
            if (bounds.width() < 10 || bounds.height() < 10) score -= 18
'''
auto = replace_first(auto, old_area_penalty, new_area_penalty, "area penalty")

old_visited = "while (!stack.isEmpty() && visitedNodes < 2400) {"
auto = auto.replace(old_visited, "while (!stack.isEmpty() && visitedNodes < 4200) {", 1)

old_click_trusted = '''        val trusted =
            match.score >= smartMatchThreshold(match, gesture) ||
                exactIdentityHit(actionNode, gesture) ||
                (match.score >= 78 && hasStrongSavedIdentity(gesture))

        if (!trusted) return false
'''
new_click_trusted = '''        val trusted =
            match.score >= smartMatchThreshold(match, gesture) ||
                exactIdentityHit(actionNode, gesture) ||
                smartIdentityConfidence(actionNode, gesture) >= 0.74f ||
                (match.score >= 74 && hasStrongSavedIdentity(gesture))

        if (!trusted) return false
'''
auto = replace_first(auto, old_click_trusted, new_click_trusted, "smart click trust gate")

write(AUTO, auto)

# ==========================================================
# 🎨 FloatingControlService.kt — smooth liquid-glass color refinement
# ==========================================================
backup(FLOATING)
floating = read(FLOATING)

floating = floating.replace(
'''    stylePanelButton(btnStart, startBg, Color.WHITE, 48)
    if (::btnLoop.isInitialized) stylePanelButton(btnLoop, Color.rgb(14, 165, 233), Color.WHITE, 38)
    if (::btnWorkflow.isInitialized) stylePanelButton(btnWorkflow, Color.rgb(124, 58, 237), Color.WHITE, 40)
    btnClear?.let { stylePanelButton(it, Color.rgb(51, 65, 85), Color.WHITE, 34) }
    if (::btnSave.isInitialized) stylePanelButton(btnSave, Color.rgb(20, 184, 166), Color.WHITE, 42)
    if (::btnUndo.isInitialized) stylePanelButton(btnUndo, Color.rgb(14, 165, 233), Color.WHITE, 42)
    if (::btnCut.isInitialized) stylePanelButton(btnCut, Color.rgb(244, 63, 94), Color.WHITE, 34)
''',
'''    stylePanelButton(btnStart, startBg, Color.WHITE, 48)
    if (::btnLoop.isInitialized) stylePanelButton(btnLoop, Color.rgb(6, 182, 212), Color.WHITE, 38)
    if (::btnWorkflow.isInitialized) stylePanelButton(btnWorkflow, Color.rgb(139, 92, 246), Color.WHITE, 40)
    btnClear?.let { stylePanelButton(it, Color.rgb(30, 41, 59), Color.WHITE, 34) }
    if (::btnSave.isInitialized) stylePanelButton(btnSave, Color.rgb(13, 148, 136), Color.WHITE, 42)
    if (::btnUndo.isInitialized) stylePanelButton(btnUndo, Color.rgb(37, 99, 235), Color.WHITE, 42)
    if (::btnCut.isInitialized) stylePanelButton(btnCut, Color.rgb(225, 29, 72), Color.WHITE, 34)
''',
1)

floating = floating.replace(
'''            intArrayOf(Color.rgb(15, 23, 42), Color.rgb(79, 70, 229), Color.rgb(20, 184, 166))
''',
'''            intArrayOf(Color.rgb(2, 6, 23), Color.rgb(67, 56, 202), Color.rgb(8, 145, 178), Color.rgb(13, 148, 136))
''',
1)

floating = floating.replace(
'''            cornerRadius = 24f * resources.displayMetrics.density
            setStroke(dp(1), Color.argb(170, 204, 251, 241))
''',
'''            cornerRadius = 26f * resources.displayMetrics.density
            setStroke(dp(1), Color.argb(190, 224, 242, 254))
''',
1)

write(FLOATING, floating)

# ==========================================================
# ⚙️ Accessibility config — faster screen-content freshness
# ==========================================================
if XML.exists():
    backup(XML)
    xml = read(XML)
    xml = xml.replace('android:notificationTimeout="120"', 'android:notificationTimeout="80"')
    write(XML, xml)

# ==========================================================
# 🎨 Main screen drawable — gentle modern glass palette
# ==========================================================
backup(BTN_BG)
write(BTN_BG, '''<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true">
        <shape android:shape="rectangle">
            <corners android:radius="26dp" />
            <gradient
                android:angle="0"
                android:startColor="#0F766E"
                android:centerColor="#1D4ED8"
                android:endColor="#6D28D9" />
            <stroke android:width="1dp" android:color="#BAE6FD" />
            <padding android:left="18dp" android:right="18dp" android:top="10dp" android:bottom="10dp" />
        </shape>
    </item>

    <item>
        <shape android:shape="rectangle">
            <corners android:radius="26dp" />
            <gradient
                android:angle="0"
                android:startColor="#14B8A6"
                android:centerColor="#2563EB"
                android:endColor="#8B5CF6" />
            <stroke android:width="1dp" android:color="#E0F2FE" />
            <padding android:left="18dp" android:right="18dp" android:top="10dp" android:bottom="10dp" />
        </shape>
    </item>
</selector>
''')

backup(MAIN_BG)
write(MAIN_BG, '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient
        android:angle="135"
        android:startColor="#020617"
        android:centerColor="#172554"
        android:endColor="#0F766E"
        android:type="linear" />
</shape>
''')

# ==========================================================
# ✅ Basic sanity checks
# ==========================================================
final_auto = read(AUTO)
required = [
    "smartIdentityConfidence",
    "AARISH_SMART_SCORE_V5",
    "captureTargetSnapshotInternal",
    "notificationTimeout=\"80\"",
]
if "smartIdentityConfidence" not in final_auto or "AARISH_SMART_SCORE_V5" not in final_auto:
    print("❌ ERROR: Smart patch verify fail hua.")
    sys.exit(1)

print("✅ SUCCESS: Offline Smart Click v5 + smooth UI patch apply ho gaya.")
print(f"📦 Backup: {BACKUP_DIR}")

gradlew = ROOT / "gradlew"
if gradlew.exists():
    try:
        os.chmod(gradlew, 0o755)
    except Exception:
        pass
    print("🔨 Gradle build check start...")
    os.chdir(ROOT)
    code = os.system("./gradlew assembleDebug")
    if code == 0:
        print("✅ BUILD SUCCESS: APK compile ho gayi.")
    else:
        print("⚠️ BUILD WARNING: Patch apply ho gaya, lekin Gradle build fail hua. Backup folder safe hai.")
else:
    print("⚠️ Gradle wrapper nahi mila, build auto-run skip hua.")
