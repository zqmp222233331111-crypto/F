package com.example.control

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * Custom InputMethodService ("আয়শা কিবোর্ড") supporting Bengali and English Unicode text,
 * a prominent "কিবোর্ড পরিবর্তন" button, and programmatic text commit via InputConnection
 * when invoked by AyeshaAccessibilityService (API 30 SoftKeyboardController).
 */
class AyeshaInputMethodService : InputMethodService() {

    private var isBengaliMode = true
    private var isShiftActive = false
    private lateinit var rootLayout: LinearLayout
    private lateinit var keysContainer: LinearLayout
    private lateinit var langButton: Button

    override fun onCreate() {
        super.onCreate()
        instanceRef = WeakReference(this)
        _isServiceBound.value = true
    }

    override fun onDestroy() {
        _isServiceBound.value = false
        instanceRef = null
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B1026"))
            setPadding(dp(6), dp(8), dp(6), dp(12))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Header bar with branding + prominent Switch Keyboard button
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(2), dp(6), dp(8))
        }

        val title = TextView(this).apply {
            text = "আয়শা কিবোর্ড"
            setTextColor(Color.parseColor("#E056FD"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        langButton = makeActionButton(if (isBengaliMode) "EN" else "বাংলা") {
            isBengaliMode = !isBengaliMode
            langButton.text = if (isBengaliMode) "EN" else "বাংলা"
            rebuildKeys()
        }

        val switchKeyboardBtn = makeActionButton("কিবোর্ড পরিবর্তন", bgHex = "#1E2952") {
            switchToPreviousOrPicker()
        }

        topBar.addView(title)
        topBar.addView(langButton)
        topBar.addView(switchKeyboardBtn)
        rootLayout.addView(topBar)

        keysContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        rootLayout.addView(keysContainer)

        rebuildKeys()
        return rootLayout
    }

    private fun switchToPreviousOrPicker() {
        val switched = try {
            switchToNextInputMethod(false)
        } catch (_: Exception) {
            false
        }
        if (!switched) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        }
    }

    private fun rebuildKeys() {
        keysContainer.removeAllViews()
        val rows = if (isBengaliMode) bengaliRows(isShiftActive) else englishRows(isShiftActive)

        rows.forEach { rowChars ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(3), 0, dp(3)) }
            }
            rowChars.forEach { ch ->
                val btn = Button(this).apply {
                    text = ch
                    isAllCaps = false
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    background = roundedBg("#182040")
                    setPadding(0, dp(8), 0, dp(8))
                    minWidth = 0
                    minimumWidth = 0
                    layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                        setMargins(dp(2), 0, dp(2), 0)
                    }
                    setOnClickListener {
                        currentInputConnection?.commitText(ch, 1)
                    }
                }
                rowLayout.addView(btn)
            }
            keysContainer.addView(rowLayout)
        }

        // Bottom control row: Shift, Space, Backspace, Enter
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(4), 0, 0) }
        }

        val shiftBtn = makeControlKey(if (isBengaliMode) "যুক্ত/স্বর" else "Shift", 1.5f) {
            isShiftActive = !isShiftActive
            rebuildKeys()
        }
        val spaceBtn = makeControlKey(if (isBengaliMode) "স্পেস" else "Space", 3.5f) {
            currentInputConnection?.commitText(" ", 1)
        }
        val backspaceBtn = makeControlKey("⌫", 1.3f) {
            val ic = currentInputConnection ?: return@makeControlKey
            val selected = ic.getSelectedText(0)
            if (selected.isNullOrEmpty()) {
                ic.deleteSurroundingText(1, 0)
            } else {
                ic.commitText("", 1)
            }
        }
        val enterBtn = makeControlKey("↵", 1.5f, bgHex = "#7E22CE") {
            currentInputConnection?.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
            )
            currentInputConnection?.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
            )
        }

        bottomRow.addView(shiftBtn)
        bottomRow.addView(spaceBtn)
        bottomRow.addView(backspaceBtn)
        bottomRow.addView(enterBtn)
        keysContainer.addView(bottomRow)
    }

    private fun makeActionButton(
        label: String,
        bgHex: String = "#273058",
        onClick: () -> Unit
    ): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        background = roundedBg(bgHex)
        setPadding(dp(10), dp(4), dp(10), dp(4))
        minHeight = dp(34)
        minimumHeight = dp(34)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(4), 0, 0, 0) }
        setOnClickListener { onClick() }
    }

    private fun makeControlKey(
        label: String,
        weight: Float,
        bgHex: String = "#232C54",
        onClick: () -> Unit
    ): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        background = roundedBg(bgHex)
        minWidth = 0
        minimumWidth = 0
        layoutParams = LinearLayout.LayoutParams(0, dp(44), weight).apply {
            setMargins(dp(2), 0, dp(2), 0)
        }
        setOnClickListener { onClick() }
    }

    private fun roundedBg(hex: String): GradientDrawable = GradientDrawable().apply {
        setColor(Color.parseColor(hex))
        cornerRadius = dp(10).toFloat()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    fun commitTextFromAgent(text: String): Boolean {
        val ic = currentInputConnection ?: return false
        return ic.commitText(text, 1)
    }

    companion object {
        private var instanceRef: WeakReference<AyeshaInputMethodService>? = null

        private val _isServiceBound = MutableStateFlow(false)
        val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

        fun getActiveInstance(): AyeshaInputMethodService? = instanceRef?.get()

        fun isEnabledInSystemSettings(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_INPUT_METHODS
            ).orEmpty()
            return enabled.contains(context.packageName) &&
                enabled.contains(AyeshaInputMethodService::class.java.simpleName)
        }

        fun getImeId(context: Context): String =
            "${context.packageName}/.control.${AyeshaInputMethodService::class.java.simpleName}"

        private fun bengaliRows(alt: Boolean): List<List<String>> = if (!alt) {
            listOf(
                listOf("১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "০"),
                listOf("অ", "আ", "ই", "উ", "এ", "ও", "া", "ি", "ু", "ে"),
                listOf("ক", "খ", "গ", "চ", "জ", "ত", "দ", "ন", "প", "ব"),
                listOf("ম", "য", "র", "ল", "শ", "স", "হ", "্", "ং", "।")
            )
        } else {
            listOf(
                listOf("ঈ", "ঊ", "ঋ", "ঐ", "ঔ", "ী", "ূ", "ৃ", "ৈ", "ৌ"),
                listOf("ঘ", "ঙ", "ছ", "ঝ", "ট", "ঠ", "ড", "ঢ", "ণ", "থ"),
                listOf("ধ", "ফ", "ভ", "ড়", "ঢ়", "য়", "ষ", "ৎ", "ঁ", "ঃ"),
                listOf("?", "!", ",", ".", "-", ":", "(", ")", "\"", "'")
            )
        }

        private fun englishRows(upper: Boolean): List<List<String>> {
            val base = listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "@"),
                listOf("z", "x", "c", "v", "b", "n", "m", ",", ".", "?")
            )
            return if (upper) base.map { r -> r.map { it.uppercase() } } else base
        }
    }
}
