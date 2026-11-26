package com.example.live

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity
import kotlin.math.abs

/**
 * Floating mini orb displayed over other apps during a backgrounded live call
 * when SYSTEM_ALERT_WINDOW permission is granted. Tap returns to the call;
 * also displays an always-visible red "থামো" button when a phone-control task is running.
 */
class FloatingOrbOverlay(
    private val context: Context,
    private val onStopTaskClicked: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: LinearLayout? = null
    private var orbCircle: View? = null
    private var stopButton: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show(isTaskRunning: Boolean) {
        if (!Settings.canDrawOverlays(context)) return
        if (rootView != null) {
            updateTaskState(isTaskRunning)
            return
        }

        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16)
            y = dp(140)
        }
        params = windowParams

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val stopPill = TextView(context).apply {
            text = "থামো"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E11D48"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#FDA4AF"))
            }
            visibility = if (isTaskRunning) View.VISIBLE else View.GONE
            setOnClickListener { onStopTaskClicked() }
        }
        stopButton = stopPill

        val orb = TextView(context).apply {
            text = "আ"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            width = dp(56)
            height = dp(56)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#E056FD"),
                    Color.parseColor("#7C3AED"),
                    Color.parseColor("#2DD4BF")
                )
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2), Color.parseColor("#F5D0FE"))
            }
            contentDescription = "আয়শা কল-এ ফিরে যান"
        }
        orbCircle = orb

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = true

        orb.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = windowParams.x
                    initialY = windowParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (initialTouchX - event.rawX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) {
                        isClick = false
                        windowParams.x = initialX + dx
                        windowParams.y = initialY + dy
                        try {
                            windowManager.updateViewLayout(container, windowParams)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            )
                        }
                        context.startActivity(intent)
                    }
                    true
                }
                else -> false
            }
        }

        container.addView(stopPill)
        val spacer = View(context).apply {
            this.layoutParams = LinearLayout.LayoutParams(dp(8), 1)
        }
        container.addView(spacer)
        container.addView(orb)

        try {
            windowManager.addView(container, windowParams)
            rootView = container
        } catch (_: Exception) {
            rootView = null
        }
    }

    fun updateTaskState(isTaskRunning: Boolean) {
        stopButton?.visibility = if (isTaskRunning) View.VISIBLE else View.GONE
    }

    fun hide() {
        val view = rootView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
        rootView = null
        orbCircle = null
        stopButton = null
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
}
