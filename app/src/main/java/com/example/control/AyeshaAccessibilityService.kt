package com.example.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.SensitiveDataFilter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * AccessibilityService enabling verified, safety-guarded phone automation during live calls.
 */
class AyeshaAccessibilityService : AccessibilityService() {

    private val nodeCache = ConcurrentHashMap<String, AccessibilityNodeInfo>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Active window is queried on-demand via rootInActiveWindow
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        clearNodeCache()
        _isConnected.value = false
        instanceRef = null
        super.onDestroy()
    }

    fun getCurrentForegroundPackage(): String? =
        rootInActiveWindow?.packageName?.toString()

    fun describeNodeLabel(nodeId: String?): String? {
        if (nodeId == null) return null
        val node = nodeCache[nodeId] ?: return null
        return listOfNotNull(
            node.text?.toString()?.takeIf { it.isNotBlank() },
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() },
            node.hintText?.toString()?.takeIf { it.isNotBlank() }
        ).firstOrNull()
    }

    fun toolListApps(blockedPackages: Set<String>): Map<String, Any?> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val apps = resolveInfos
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull { info ->
                val pkg = info.activityInfo.packageName
                val label = info.loadLabel(pm).toString()
                if (SensitiveDataFilter.looksLikeBankingOrWalletApp(pkg, label, blockedPackages)) {
                    null
                } else {
                    mapOf("name" to label, "package" to pkg)
                }
            }
            .sortedBy { it["name"]?.lowercase() }
            .take(80)

        return mapOf(
            "success" to true,
            "count" to apps.size,
            "apps" to apps
        )
    }

    fun toolOpenApp(query: String, blockedPackages: Set<String>): Map<String, Any?> {
        if (query.isBlank()) {
            return mapOf("success" to false, "error" to "অ্যাপের নাম বা প্যাকেজ উল্লেখ করা হয়নি।")
        }
        val pm = packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val activities = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        val qLower = query.trim().lowercase()

        val matched = activities.firstOrNull {
            it.activityInfo.packageName.equals(query.trim(), ignoreCase = true)
        } ?: activities.firstOrNull {
            it.loadLabel(pm).toString().equals(query.trim(), ignoreCase = true)
        } ?: activities.firstOrNull {
            it.loadLabel(pm).toString().lowercase().contains(qLower) ||
                it.activityInfo.packageName.lowercase().contains(qLower)
        }

        if (matched == null) {
            return mapOf("success" to false, "error" to "'$query' অ্যাপটি ফোনে খুঁজে পাওয়া যায়নি।")
        }

        val pkg = matched.activityInfo.packageName
        val label = matched.loadLabel(pm).toString()
        if (SensitiveDataFilter.looksLikeBankingOrWalletApp(pkg, label, blockedPackages)) {
            return mapOf(
                "success" to false,
                "error" to "'$label' একটি ব্যাংকিং বা আর্থিক অ্যাপ হওয়ায় নিরাপত্তার কারণে খোলা ব্লক করা আছে।"
            )
        }

        val launch = pm.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return mapOf("success" to false, "error" to "'$label' খোলার অনুমতি পাওয়া যায়নি।")

        startActivity(launch)
        return mapOf("success" to true, "message" to "$label খোলা হয়েছে।", "package" to pkg)
    }

    fun toolReadScreen(blockedPackages: Set<String>): Map<String, Any?> {
        val root = rootInActiveWindow
            ?: return mapOf("success" to false, "error" to "বর্তমান পর্দা পড়া যাচ্ছে না।")
        val fgPackage = root.packageName?.toString().orEmpty()
        if (SensitiveDataFilter.looksLikeBankingOrWalletApp(fgPackage, null, blockedPackages)) {
            return mapOf(
                "success" to false,
                "error" to "বর্তমান অ্যাপটি ($fgPackage) ব্যাংকিং/ওয়ালেট অ্যাপ হওয়ায় পর্দার তথ্য পড়া বন্ধ রাখা হয়েছে।"
            )
        }

        clearNodeCache()
        val elements = mutableListOf<Map<String, Any?>>()
        var counter = 0

        fun traverse(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 28 || elements.size >= 90) return
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val isVisible = node.isVisibleToUser && rect.width() > 0 && rect.height() > 0

            val isSensitive = SensitiveDataFilter.isSensitiveUiField(
                isPasswordNode = node.isPassword,
                viewId = node.viewIdResourceName,
                hintText = node.hintText?.toString(),
                contentDesc = node.contentDescription?.toString(),
                labelText = node.text?.toString()
            )

            val textVal = if (isSensitive) "[সুরক্ষিত ফিল্ড - পড়া নিষিদ্ধ]" else node.text?.toString().orEmpty()
            val descVal = if (isSensitive) "[সুরক্ষিত]" else node.contentDescription?.toString().orEmpty()

            if (isVisible && (node.isClickable || node.isEditable || node.isScrollable || textVal.isNotBlank() || descVal.isNotBlank())) {
                val id = "n${++counter}"
                nodeCache[id] = node
                elements.add(
                    mapOf(
                        "id" to id,
                        "class" to (node.className?.toString()?.substringAfterLast('.') ?: ""),
                        "text" to textVal.take(120),
                        "desc" to descVal.take(100),
                        "hint" to (if (isSensitive) "[সুরক্ষিত]" else node.hintText?.toString().orEmpty().take(60)),
                        "clickable" to node.isClickable,
                        "editable" to node.isEditable,
                        "scrollable" to node.isScrollable,
                        "protected" to isSensitive,
                        "bounds" to "${rect.left},${rect.top},${rect.right},${rect.bottom}"
                    )
                )
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i), depth + 1)
            }
        }

        traverse(root, 0)
        return mapOf(
            "success" to true,
            "package" to fgPackage,
            "elements" to elements
        )
    }

    fun toolTap(nodeId: String?, x: Float?, y: Float?): Map<String, Any?> {
        if (nodeId != null) {
            val node = nodeCache[nodeId]
                ?: return mapOf("success" to false, "error" to "উপাদান $nodeId পর্দায় পাওয়া যায়নি। আগে read_screen করুন।")
            if (isNodeProtected(node)) {
                return mapOf("success" to false, "error" to "পাসওয়ার্ড/ওটিপি/পিন ফিল্ডে ট্যাপ করা নিষিদ্ধ।")
            }
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return mapOf("success" to true, "message" to "$nodeId-এ ট্যাপ করা হয়েছে।")
            }
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                val ok = dispatchTapGesture(rect.exactCenterX(), rect.exactCenterY(), 70L)
                return if (ok) {
                    mapOf("success" to true, "message" to "$nodeId অবস্থানে ট্যাপ করা হয়েছে।")
                } else {
                    mapOf("success" to false, "error" to "ট্যাপ জেসচার চালানো যায়নি।")
                }
            }
        }
        if (x != null && y != null) {
            val ok = dispatchTapGesture(x, y, 70L)
            return if (ok) {
                mapOf("success" to true, "message" to "($x, $y) অবস্থানে ট্যাপ করা হয়েছে।")
            } else {
                mapOf("success" to false, "error" to "ট্যাপ করা যায়নি।")
            }
        }
        return mapOf("success" to false, "error" to "ট্যাপ করার জন্য node_id অথবা x, y প্রয়োজন।")
    }

    fun toolLongPress(nodeId: String?, x: Float?, y: Float?): Map<String, Any?> {
        if (nodeId != null) {
            val node = nodeCache[nodeId]
                ?: return mapOf("success" to false, "error" to "উপাদান $nodeId পাওয়া যায়নি।")
            if (isNodeProtected(node)) {
                return mapOf("success" to false, "error" to "সুরক্ষিত ফিল্ডে দীর্ঘ চাপ নিষিদ্ধ।")
            }
            if (node.isLongClickable && node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                return mapOf("success" to true, "message" to "$nodeId দীর্ঘক্ষণ চেপে ধরা হয়েছে।")
            }
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val ok = dispatchTapGesture(rect.exactCenterX(), rect.exactCenterY(), 700L)
            return if (ok) mapOf("success" to true, "message" to "দীর্ঘক্ষণ চেপে ধরা হয়েছে।")
            else mapOf("success" to false, "error" to "দীর্ঘ চাপ ব্যর্থ হয়েছে।")
        }
        if (x != null && y != null) {
            val ok = dispatchTapGesture(x, y, 700L)
            return if (ok) mapOf("success" to true, "message" to "দীর্ঘক্ষণ চেপে ধরা হয়েছে।")
            else mapOf("success" to false, "error" to "দীর্ঘ চাপ ব্যর্থ হয়েছে।")
        }
        return mapOf("success" to false, "error" to "node_id বা x,y প্রয়োজন।")
    }

    fun toolScroll(direction: String, nodeId: String?): Map<String, Any?> {
        val dirLower = direction.lowercase()
        val node = nodeId?.let { nodeCache[it] }
        if (node != null && node.isScrollable) {
            val action = if (dirLower == "up" || dirLower == "left") {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
            if (node.performAction(action)) {
                return mapOf("success" to true, "message" to "$direction দিকে স্ক্রল করা হয়েছে।")
            }
        }

        val metrics = resources.displayMetrics
        val cx = metrics.widthPixels / 2f
        val cy = metrics.heightPixels / 2f
        val (x1, y1, x2, y2) = when (dirLower) {
            "up" -> listOf(cx, metrics.heightPixels * 0.30f, cx, metrics.heightPixels * 0.72f)
            "left" -> listOf(metrics.widthPixels * 0.22f, cy, metrics.widthPixels * 0.80f, cy)
            "right" -> listOf(metrics.widthPixels * 0.80f, cy, metrics.widthPixels * 0.22f, cy)
            else -> listOf(cx, metrics.heightPixels * 0.72f, cx, metrics.heightPixels * 0.30f)
        }
        val ok = dispatchSwipeGesture(x1, y1, x2, y2, 340L)
        return if (ok) mapOf("success" to true, "message" to "$direction দিকে স্ক্রল করা হয়েছে।")
        else mapOf("success" to false, "error" to "স্ক্রল করা যায়নি।")
    }

    fun toolSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Map<String, Any?> {
        val ok = dispatchSwipeGesture(x1, y1, x2, y2, durationMs.coerceIn(120L, 1500L))
        return if (ok) mapOf("success" to true, "message" to "সোয়াইপ সম্পন্ন হয়েছে।")
        else mapOf("success" to false, "error" to "সোয়াইপ চালানো যায়নি।")
    }

    suspend fun toolTypeText(
        nodeId: String?,
        text: String,
        allowKeyboardSwitch: Boolean
    ): Map<String, Any?> {
        if (text.isEmpty()) return mapOf("success" to false, "error" to "টাইপ করার মতো কোনো লেখা নেই।")
        val node = (nodeId?.let { nodeCache[it] }) ?: findFirstFocusedEditableNode()
            ?: return mapOf("success" to false, "error" to "টাইপ করার মতো ইনপুট বক্স পাওয়া যায়নি।")

        if (isNodeProtected(node)) {
            return mapOf(
                "success" to false,
                "error" to "নিরাপত্তার জন্য পাসওয়ার্ড, ওটিপি বা পিন ফিল্ডে আয়শা কখনোই টাইপ করবে না।"
            )
        }

        // Focus node first
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (!node.isFocused && rect.width() > 0 && rect.height() > 0) {
            dispatchTapGesture(rect.exactCenterX(), rect.exactCenterY(), 60L)
            delay(220)
        }

        // Prefer AyeshaInputMethodService via API 30 SoftKeyboardController when enabled
        if (allowKeyboardSwitch && AyeshaInputMethodService.isEnabledInSystemSettings(this)) {
            val previousIme = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            val ayeshaImeId = AyeshaInputMethodService.getImeId(this)
            val switched = try {
                softKeyboardController.switchToInputMethod(ayeshaImeId)
            } catch (_: Exception) {
                false
            }
            if (switched) {
                delay(280)
                val ime = AyeshaInputMethodService.getActiveInstance()
                val committed = ime?.commitTextFromAgent(text) ?: false
                delay(160)
                if (!previousIme.isNullOrBlank() && previousIme != ayeshaImeId) {
                    try {
                        softKeyboardController.switchToInputMethod(previousIme)
                    } catch (_: Exception) {
                    }
                }
                if (committed) {
                    return mapOf(
                        "success" to true,
                        "message" to "আয়শা কিবোর্ডের মাধ্যমে লেখা টাইপ করা হয়েছে।"
                    )
                }
            }
        }

        // Fallback: Accessibility ACTION_SET_TEXT
        val existing = node.text?.toString().orEmpty()
        val combined = if (existing.isEmpty()) text else "$existing$text"
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, combined)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return if (ok) {
            mapOf("success" to true, "message" to "লেখা বসানো হয়েছে।")
        } else {
            mapOf("success" to false, "error" to "ইনপুট বক্সে টাইপ করা যায়নি।")
        }
    }

    fun toolClearText(nodeId: String?): Map<String, Any?> {
        val node = (nodeId?.let { nodeCache[it] }) ?: findFirstFocusedEditableNode()
            ?: return mapOf("success" to false, "error" to "মোছার মতো ইনপুট বক্স পাওয়া যায়নি।")
        if (isNodeProtected(node)) {
            return mapOf("success" to false, "error" to "সুরক্ষিত ফিল্ডে পরিবর্তন নিষিদ্ধ।")
        }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return if (ok) mapOf("success" to true, "message" to "লেখা মুছে ফেলা হয়েছে।")
        else mapOf("success" to false, "error" to "লেখা মোছা যায়নি।")
    }

    fun toolGlobalAction(action: Int, labelBn: String): Map<String, Any?> {
        val ok = performGlobalAction(action)
        return if (ok) mapOf("success" to true, "message" to "$labelBn সম্পন্ন হয়েছে।")
        else mapOf("success" to false, "error" to "$labelBn ব্যর্থ হয়েছে।")
    }

    private fun isNodeProtected(node: AccessibilityNodeInfo): Boolean =
        SensitiveDataFilter.isSensitiveUiField(
            isPasswordNode = node.isPassword,
            viewId = node.viewIdResourceName,
            hintText = node.hintText?.toString(),
            contentDesc = node.contentDescription?.toString(),
            labelText = node.text?.toString()
        )

    private fun findFirstFocusedEditableNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        fun search(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (n == null) return null
            if (n.isEditable && n.isFocused) return n
            for (i in 0 until n.childCount) {
                search(n.getChild(i))?.let { return it }
            }
            return null
        }
        return search(root)
    }

    private fun dispatchTapGesture(x: Float, y: Float, durationMs: Long): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun dispatchSwipeGesture(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long
    ): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun clearNodeCache() {
        nodeCache.clear()
    }

    companion object {
        private var instanceRef: WeakReference<AyeshaAccessibilityService>? = null

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        fun getInstance(): AyeshaAccessibilityService? = instanceRef?.get()

        fun isEnabledInSystemSettings(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            return enabled.contains(context.packageName) &&
                enabled.contains(AyeshaAccessibilityService::class.java.simpleName)
        }
    }
}
