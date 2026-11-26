package com.example.control

import android.app.KeyguardManager
import android.content.Context
import com.example.data.ActionLogItem
import com.example.data.AyeshaRepository
import com.example.data.SensitiveDataFilter
import com.example.data.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

data class PendingActionConfirmation(
    val id: Long,
    val titleBn: String,
    val descriptionBn: String,
    val deferred: CompletableDeferred<Boolean>
)

data class PhoneControlUiState(
    val isTaskRunning: Boolean = false,
    val activeTaskDescription: String = "",
    val stepCount: Int = 0,
    val maxSteps: Int = 25,
    val pendingConfirmation: PendingActionConfirmation? = null,
    val lastStatusMessage: String? = null
)

/**
 * Coordinates all phone-control safety policies in Kotlin code:
 * - Master switch + per-tool switches
 * - Lock screen refusal
 * - Blocked banking/wallet apps (bKash, Nagad, Rocket, etc.)
 * - Password/OTP/PIN field protection
 * - User confirmation (spoken or on-screen) before risky actions
 * - 25-step limit, 3x consecutive failure stop, immediate "থামো" cancellation
 */
class PhoneControlCoordinator(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val repository: AyeshaRepository
) {
    private val _uiState = MutableStateFlow(PhoneControlUiState())
    val uiState: StateFlow<PhoneControlUiState> = _uiState.asStateFlow()

    private val stopRequested = AtomicBoolean(false)
    private val actionMutex = Mutex()

    private var consecutiveFailures = 0
    private var lastErrorSignature: String? = null
    private val callActionLogs = mutableListOf<ActionLogItem>()

    fun beginCallSession() {
        stopRequested.set(false)
        consecutiveFailures = 0
        lastErrorSignature = null
        callActionLogs.clear()
        _uiState.value = PhoneControlUiState()
    }

    fun getSessionActionLogs(): List<ActionLogItem> = synchronized(callActionLogs) {
        callActionLogs.toList()
    }

    fun stopTaskNow(reasonBn: String = "ব্যবহারকারীর নির্দেশে কাজ থামানো হয়েছে।") {
        stopRequested.set(true)
        _uiState.value.pendingConfirmation?.deferred?.complete(false)
        _uiState.value = _uiState.value.copy(
            isTaskRunning = false,
            activeTaskDescription = "",
            pendingConfirmation = null,
            lastStatusMessage = reasonBn
        )
    }

    fun resolveConfirmation(approved: Boolean) {
        val pending = _uiState.value.pendingConfirmation ?: return
        pending.deferred.complete(approved)
        _uiState.value = _uiState.value.copy(pendingConfirmation = null)
    }

    /**
     * Allows voice recognition / Live transcript to confirm or cancel pending prompts.
     * Returns true if a pending confirmation was resolved by voice.
     */
    fun handleVoiceTextForSafety(transcript: String): Boolean {
        val normalized = transcript.trim().lowercase()
        if (normalized.contains("থামো") || normalized.contains("থাম") ||
            normalized == "stop" || normalized.contains("stop task")
        ) {
            stopTaskNow("কণ্ঠের নির্দেশে ('থামো') কাজ বন্ধ করা হয়েছে।")
            return true
        }

        val pending = _uiState.value.pendingConfirmation ?: return false
        val affirmative = listOf("হ্যাঁ", "হ্যা", "জি", "করো", "করুন", "ঠিক আছে", "yes", "ok", "confirm")
        val negative = listOf("না", "বাতিল", "থাক", "করো না", "no", "cancel")

        return when {
            negative.any { normalized.contains(it) } -> {
                pending.deferred.complete(false)
                _uiState.value = _uiState.value.copy(pendingConfirmation = null)
                true
            }
            affirmative.any { normalized.contains(it) } -> {
                pending.deferred.complete(true)
                _uiState.value = _uiState.value.copy(pendingConfirmation = null)
                true
            }
            else -> false
        }
    }

    suspend fun executeTool(
        toolName: String,
        args: Map<String, Any?>
    ): Map<String, Any?> = actionMutex.withLock {
        if (toolName == "stop_task") {
            stopTaskNow()
            recordLog(toolName, "কাজ বন্ধ করা হয়েছে", true)
            return@withLock mapOf("success" to true, "message" to "কাজ থামানো হয়েছে।")
        }

        if (stopRequested.get()) {
            stopRequested.set(false)
            return@withLock mapOf(
                "success" to false,
                "error" to "ব্যবহারকারী 'থামো' নির্দেশ দিয়েছেন। নতুন কাজ শুরু করার আগে ব্যবহারকারীর কথা শুনুন।"
            )
        }

        val settings = settingsRepository.settings.first()
        if (!settings.phoneControlMasterEnabled) {
            return@withLock refusal(
                toolName,
                "ফোন নিয়ন্ত্রণ বন্ধ আছে। সেটিংস > ফোন নিয়ন্ত্রণ থেকে প্রধান সুইচটি চালু করুন।"
            )
        }

        val service = AyeshaAccessibilityService.getInstance()
            ?: return@withLock refusal(
                toolName,
                "অ্যাকসেসিবিলিটি সেবা চালু নেই। সেটিংস > ফোন নিয়ন্ত্রণ থেকে 'আয়শা' অ্যাকসেসিবিলিটি সেবা চালু করুন।"
            )

        // Never bypass lock screen
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isKeyguardLocked == true) {
            return@withLock refusal(
                toolName,
                "ফোনের পর্দা লক করা আছে। নিরাপত্তার জন্য লক স্ক্রিন খোলা বা এড়িয়ে যাওয়া যাবে না। অনুগ্রহ করে নিজে আনলক করুন।"
            )
        }

        // Per-action switch enforcement
        when (toolName) {
            "open_app" -> if (!settings.allowAppOpening) {
                return@withLock refusal(
                    toolName,
                    "অ্যাপ খোলার অনুমতি বন্ধ আছে। সেটিংস > ফোন নিয়ন্ত্রণ > অ্যাপ খোলা চালু করুন।"
                )
            }
            "tap", "long_press", "scroll", "swipe" -> if (!settings.allowTapping) {
                return@withLock refusal(
                    toolName,
                    "ট্যাপ ও স্ক্রল করার অনুমতি বন্ধ আছে। সেটিংস > ফোন নিয়ন্ত্রণ > ট্যাপ ও স্ক্রল চালু করুন।"
                )
            }
            "type_text", "clear_text" -> if (!settings.allowTyping) {
                return@withLock refusal(
                    toolName,
                    "টাইপ করার অনুমতি বন্ধ আছে। সেটিংস > ফোন নিয়ন্ত্রণ > টাইপিং চালু করুন।"
                )
            }
        }

        // Step budget check (excluding passive read_screen/list_apps/wait_for_screen? Keep total bounded at 25!)
        val nextStep = _uiState.value.stepCount + 1
        if (nextStep > 25) {
            stopTaskNow("২৫ ধাপের সীমা পূর্ণ হয়েছে। নিরাপত্তার জন্য কাজটি এখানেই থামানো হলো।")
            return@withLock refusal(
                toolName,
                "২৫ ধাপের সর্বোচ্চ সীমা পূর্ণ হয়েছে। কাজটি সম্পূর্ণ করা যায়নি—ব্যবহারকারীকে সত্য কথা জানান।"
            )
        }

        // Check blocked foreground app before any screen interaction
        val blockedPackages = repository.getBlockedPackagesSet()
        val currentForegroundPkg = service.getCurrentForegroundPackage()
        if (currentForegroundPkg != null &&
            SensitiveDataFilter.looksLikeBankingOrWalletApp(currentForegroundPkg, null, blockedPackages)
        ) {
            return@withLock refusal(
                toolName,
                "বর্তমান অ্যাপটি ($currentForegroundPkg) ব্যাংকিং বা মোবাইল ওয়ালেট হিসেবে ব্লক করা আছে। নিরাপত্তার জন্য এখানে কোনো কাজ করা যাবে না।"
            )
        }

        _uiState.value = _uiState.value.copy(
            isTaskRunning = true,
            activeTaskDescription = describeToolActionBn(toolName, args),
            stepCount = nextStep
        )

        // Risky-action confirmation check (on by default)
        if (settings.requireActionConfirmation && isRiskyAction(toolName, args, service)) {
            val confirmTitle = "নিশ্চিতকরণ প্রয়োজন"
            val confirmDesc = buildConfirmationMessageBn(toolName, args, service)
            val deferred = CompletableDeferred<Boolean>()
            _uiState.value = _uiState.value.copy(
                pendingConfirmation = PendingActionConfirmation(
                    id = System.currentTimeMillis(),
                    titleBn = confirmTitle,
                    descriptionBn = confirmDesc,
                    deferred = deferred
                )
            )
            val approved = deferred.await()
            if (!approved) {
                recordLog(toolName, "ব্যবহারকারী বাতিল করেছেন: $confirmDesc", false)
                _uiState.value = _uiState.value.copy(
                    isTaskRunning = false,
                    lastStatusMessage = "কাজটি বাতিল করা হয়েছে।"
                )
                return@withLock mapOf(
                    "success" to false,
                    "error" to "ব্যবহারকারী এই কাজের অনুমতি দেননি বা বাতিল করেছেন।"
                )
            }
        }

        // Execute actual tool via AccessibilityService
        val result = when (toolName) {
            "list_apps" -> service.toolListApps(blockedPackages)
            "open_app" -> {
                val query = (args["app_name"] ?: args["package_name"] ?: "").toString()
                service.toolOpenApp(query, blockedPackages)
            }
            "read_screen" -> service.toolReadScreen(blockedPackages)
            "tap" -> {
                val nodeId = args["node_id"]?.toString()
                val x = (args["x"] as? Number)?.toFloat()
                val y = (args["y"] as? Number)?.toFloat()
                service.toolTap(nodeId, x, y)
            }
            "long_press" -> {
                val nodeId = args["node_id"]?.toString()
                val x = (args["x"] as? Number)?.toFloat()
                val y = (args["y"] as? Number)?.toFloat()
                service.toolLongPress(nodeId, x, y)
            }
            "scroll" -> {
                val dir = args["direction"]?.toString() ?: "down"
                val nodeId = args["node_id"]?.toString()
                service.toolScroll(dir, nodeId)
            }
            "swipe" -> {
                val x1 = (args["x1"] as? Number)?.toFloat() ?: 500f
                val y1 = (args["y1"] as? Number)?.toFloat() ?: 1200f
                val x2 = (args["x2"] as? Number)?.toFloat() ?: 500f
                val y2 = (args["y2"] as? Number)?.toFloat() ?: 400f
                val dur = (args["duration_ms"] as? Number)?.toLong() ?: 320L
                service.toolSwipe(x1, y1, x2, y2, dur)
            }
            "type_text" -> {
                val nodeId = args["node_id"]?.toString()
                val text = args["text"]?.toString().orEmpty()
                service.toolTypeText(
                    nodeId = nodeId,
                    text = text,
                    allowKeyboardSwitch = settings.allowKeyboardSwitch
                )
            }
            "clear_text" -> {
                val nodeId = args["node_id"]?.toString()
                service.toolClearText(nodeId)
            }
            "press_back" -> service.toolGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK, "পেছনে ফেরা")
            "go_home" -> service.toolGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME, "হোম স্ক্রিন")
            "open_recents" -> service.toolGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS, "সাম্প্রতিক অ্যাপ")
            "open_notifications" -> service.toolGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "নোটিফিকেশন")
            "wait_for_screen" -> {
                val ms = ((args["ms"] as? Number)?.toLong() ?: 1000L).coerceIn(200L, 5000L)
                kotlinx.coroutines.delay(ms)
                service.toolReadScreen(blockedPackages)
            }
            else -> mapOf("success" to false, "error" to "অজানা টুল: $toolName")
        }

        val isOk = result["success"] == true
        val detailMsg = (result["message"] ?: result["error"] ?: toolName).toString()
        recordLog(toolName, detailMsg, isOk)

        if (isOk) {
            consecutiveFailures = 0
            lastErrorSignature = null
        } else {
            val sig = "$toolName:$detailMsg"
            if (sig == lastErrorSignature) {
                consecutiveFailures++
            } else {
                consecutiveFailures = 1
                lastErrorSignature = sig
            }
            if (consecutiveFailures >= 3) {
                stopTaskNow("একই ত্রুটি বারবার হওয়ায় কাজটি থামানো হয়েছে।")
                return@withLock mapOf(
                    "success" to false,
                    "error" to "একই কাজ ৩ বার ব্যর্থ হয়েছে। কাজটি সম্পন্ন করা যায়নি—ব্যবহারকারীকে সত্য কথা জানান।"
                )
            }
        }

        // Keep running state until stop_task or call end; user can tap "থামো" anytime
        _uiState.value = _uiState.value.copy(lastStatusMessage = detailMsg)
        result
    }

    private fun refusal(toolName: String, reasonBn: String): Map<String, Any?> {
        recordLog(toolName, reasonBn, false)
        _uiState.value = _uiState.value.copy(
            isTaskRunning = false,
            lastStatusMessage = reasonBn
        )
        return mapOf("success" to false, "error" to reasonBn)
    }

    private fun recordLog(action: String, detail: String, success: Boolean) {
        synchronized(callActionLogs) {
            callActionLogs.add(ActionLogItem(action, detail, success))
        }
    }

    private fun isRiskyAction(
        toolName: String,
        args: Map<String, Any?>,
        service: AyeshaAccessibilityService
    ): Boolean {
        val riskyKeywords = listOf(
            "send", "post", "submit", "call", "dial", "delete", "remove", "buy",
            "purchase", "pay", "transfer", "install", "uninstall", "confirm", "order",
            "পাঠান", "পোস্ট", "কল", "মুছুন", "ডিলিট", "কিনুন", "পেমেন্ট", "ট্রান্সফার", "ইনস্টল"
        )
        return when (toolName) {
            "tap", "long_press" -> {
                val nodeId = args["node_id"]?.toString()
                val label = service.describeNodeLabel(nodeId)?.lowercase().orEmpty()
                riskyKeywords.any { label.contains(it) }
            }
            "open_app" -> {
                val target = (args["app_name"] ?: args["package_name"] ?: "").toString().lowercase()
                target.contains("phone") || target.contains("dialer") || target.contains("কল")
            }
            else -> false
        }
    }

    private fun buildConfirmationMessageBn(
        toolName: String,
        args: Map<String, Any?>,
        service: AyeshaAccessibilityService
    ): String {
        return when (toolName) {
            "tap", "long_press" -> {
                val label = service.describeNodeLabel(args["node_id"]?.toString()) ?: "বোতাম"
                "\"$label\"-এ চাপ দেওয়ার আগে আপনার অনুমতি প্রয়োজন।"
            }
            "open_app" -> {
                val app = (args["app_name"] ?: args["package_name"] ?: "অ্যাপ").toString()
                "\"$app\" অ্যাপটি খোলার আগে আপনার অনুমতি প্রয়োজন।"
            }
            else -> "এই সংবেদনশীল কাজটি করার আগে আপনার অনুমতি প্রয়োজন।"
        }
    }

    private fun describeToolActionBn(toolName: String, args: Map<String, Any?>): String =
        when (toolName) {
            "list_apps" -> "ফোনের অ্যাপ তালিকা দেখা হচ্ছে..."
            "open_app" -> "অ্যাপ খোলা হচ্ছে: ${args["app_name"] ?: args["package_name"] ?: ""}"
            "read_screen" -> "পর্দার তথ্য পড়া হচ্ছে..."
            "tap" -> "পর্দায় ট্যাপ করা হচ্ছে..."
            "long_press" -> "দীর্ঘক্ষণ চেপে ধরা হচ্ছে..."
            "scroll" -> "পর্দা স্ক্রল করা হচ্ছে..."
            "swipe" -> "সোয়াইপ করা হচ্ছে..."
            "type_text" -> "লেখা টাইপ করা হচ্ছে..."
            "clear_text" -> "লেখা মোছা হচ্ছে..."
            "press_back" -> "পেছনে ফেরা হচ্ছে..."
            "go_home" -> "হোম স্ক্রিনে যাওয়া হচ্ছে..."
            "open_recents" -> "সাম্প্রতিক অ্যাপ খোলা হচ্ছে..."
            "open_notifications" -> "নোটিফিকেশন খোলা হচ্ছে..."
            "wait_for_screen" -> "পর্দার পরিবর্তনের অপেক্ষা..."
            else -> "কাজ চলছে..."
        }
}
