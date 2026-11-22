package app.olauncher.timer

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import app.olauncher.R
import kotlin.math.abs

class FloatingTimerView(private val context: Context, private val onPillClicked: () -> Unit = {}) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatingView: View? = null
    private var tvTimer: TextView? = null
    private var isAdded = false

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        },
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 50
        y = 220
    }

    fun show() {
        if (isAdded || !Settings.canDrawOverlays(context)) return

        try {
            val view = LayoutInflater.from(context).inflate(R.layout.view_floating_timer, null)
            floatingView = view
            tvTimer = view.findViewById(R.id.tvTimerRemaining)

            setupDragAndClick(view)

            windowManager.addView(view, layoutParams)
            isAdded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateTime(formattedTime: String) {
        tvTimer?.text = formattedTime
    }

    fun hide() {
        if (!isAdded || floatingView == null) return
        try {
            windowManager.removeView(floatingView)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            floatingView = null
            tvTimer = null
            isAdded = false
        }
    }

    private fun setupDragAndClick(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = true

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isClick = false
                    }

                    layoutParams.x = initialX + dx
                    layoutParams.y = initialY + dy

                    if (isAdded && floatingView != null) {
                        windowManager.updateViewLayout(floatingView, layoutParams)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        onPillClicked()
                    }
                    true
                }

                else -> false
            }
        }
    }
}
