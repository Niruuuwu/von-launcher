package app.olauncher.media

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.sin

/**
 * Android 13+ style squiggly/wavy progress bar.
 * Renders an animated sine wave for the played portion and a straight line for the unplayed portion.
 * Supports interactive touch scrubbing and seeking.
 */
class WavyProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onSeekListener: ((Long) -> Unit)? = null

    private var durationMs: Long = 0L
    private var positionMs: Long = 0L
    private var lastPositionUpdateTime: Long = 0L
    private var playbackSpeed: Float = 1f
    private var isPlaying: Boolean = false

    private var isScrubbing = false
    private var scrubProgress = 0f

    private val wavePath = Path()

    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2F2F2") // ol_text_primary
        style = Paint.Style.STROKE
        strokeWidth = 2.5f.dpToPx()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val unplayedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40FFFFFF") // 25% white
        style = Paint.Style.STROKE
        strokeWidth = 2.0f.dpToPx()
        strokeCap = Paint.Cap.ROUND
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL
    }

    private val thumbRadius = 4.5f.dpToPx()
    private val waveAmplitude = 3.5f.dpToPx()
    private val waveLength = 32f.dpToPx()

    fun updateProgress(
        position: Long,
        duration: Long,
        speed: Float,
        updateTime: Long,
        playing: Boolean
    ) {
        positionMs = position
        durationMs = duration
        playbackSpeed = speed
        lastPositionUpdateTime = updateTime
        isPlaying = playing
        invalidate()
    }

    private fun getCurrentProgress(): Float {
        if (isScrubbing) return scrubProgress
        if (durationMs <= 0) return 0f

        val currentPos = if (isPlaying && lastPositionUpdateTime > 0) {
            val elapsed = SystemClock.elapsedRealtime() - lastPositionUpdateTime
            (positionMs + (elapsed * playbackSpeed).toLong()).coerceIn(0L, durationMs)
        } else {
            positionMs.coerceIn(0L, durationMs)
        }

        return (currentPos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val centerY = h / 2f
        val progress = getCurrentProgress()
        val playedEndX = w * progress

        // 1. Draw unplayed portion (straight line)
        if (playedEndX < w) {
            canvas.drawLine(playedEndX, centerY, w, centerY, unplayedPaint)
        }

        // 2. Draw played portion (wavy sine line)
        if (playedEndX > 0f) {
            wavePath.reset()
            wavePath.moveTo(0f, centerY)

            // Wave phase advances while playing
            val phase = if (isPlaying) {
                ((SystemClock.uptimeMillis() % 10000) / 1000f) * (2f * PI.toFloat() * 1.2f)
            } else {
                0f
            }

            val step = 3f // px step for smooth curves
            var x = 0f
            var lastY = centerY

            while (x <= playedEndX) {
                // Taper amplitude near the start (0..16dp) and near the thumb for a smooth look
                val startTaper = (x / 20f.dpToPx()).coerceIn(0f, 1f)
                val endDist = (playedEndX - x)
                val endTaper = (endDist / 12f.dpToPx()).coerceIn(0f, 1f)
                val currentAmp = waveAmplitude * startTaper * endTaper

                val angle = (2f * PI.toFloat() * (x / waveLength)) - phase
                val y = centerY + currentAmp * sin(angle)
                wavePath.lineTo(x, y)
                lastY = y
                x += step
            }

            // Ensure path reaches exact playedEndX
            if (x < playedEndX + step) {
                wavePath.lineTo(playedEndX, lastY)
            }

            canvas.drawPath(wavePath, playedPaint)

            // 3. Draw small thumb at the wave end
            canvas.drawCircle(playedEndX, centerY, thumbRadius, thumbPaint)
        }

        // Keep wave swimming smoothly at 60fps only while playing and visible
        if (isPlaying && isShown) {
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (durationMs <= 0) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isScrubbing = true
                parent?.requestDisallowInterceptTouchEvent(true)
                scrubProgress = (event.x / width.toFloat()).coerceIn(0f, 1f)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                scrubProgress = (event.x / width.toFloat()).coerceIn(0f, 1f)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                isScrubbing = false
                parent?.requestDisallowInterceptTouchEvent(false)
                scrubProgress = (event.x / width.toFloat()).coerceIn(0f, 1f)
                val targetMs = (scrubProgress * durationMs).toLong()
                onSeekListener?.invoke(targetMs)
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isScrubbing = false
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun Float.dpToPx(): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics)
}
