package app.olauncher.media

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator

object ThemeBackground {

    val IDLE_BACKGROUND = intArrayOf(Color.BLACK, Color.BLACK, Color.BLACK)

    const val TRANSITION_IDLE_TO_THEME = 350L
    const val TRANSITION_THEME_TO_THEME = 500L
    const val TRANSITION_THEME_TO_IDLE = 500L
    const val GRACE_PAUSED_MS = 3 * 60 * 1000L
    const val GRACE_INACTIVE_MS = 5 * 1000L

    private var currentColors = IDLE_BACKGROUND.clone()
    private var targetColors = IDLE_BACKGROUND.clone()
    private var colorAnimator: ValueAnimator? = null
    private var waveAnimator: ValueAnimator? = null

    private var waveStartTime = SystemClock.uptimeMillis()
    private var currentIntensity = 0f

    private val attachedViews = mutableSetOf<View>()

    val isIdle: Boolean
        get() = targetColors.contentEquals(IDLE_BACKGROUND)

    val drawable: Drawable = createThemeDrawable()

    fun attachView(view: View) {
        attachedViews.add(view)
        view.background = drawable
        updateDrawableState()
        if (targetColors.contentEquals(IDLE_BACKGROUND).not()) {
            startWaveAnimation()
        }
    }

    fun detachView(view: View) {
        attachedViews.remove(view)
        if (attachedViews.isEmpty()) {
            stopWaveAnimation()
        }
    }

    private fun createThemeDrawable(): Drawable {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AgslWaveDrawable()
        } else {
            FallbackGradientDrawable()
        }
    }

    fun animateTo(newColors: IntArray, durationMs: Long) {
        if (targetColors.contentEquals(newColors) && colorAnimator?.isRunning == true) {
            return
        }

        targetColors = newColors.clone()
        colorAnimator?.cancel()

        val startColors = currentColors.clone()
        val startIntensity = currentIntensity
        val targetIntensity = if (newColors.contentEquals(IDLE_BACKGROUND)) 0f else 1f
        val evaluator = ArgbEvaluator()
        val interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)

        if (targetIntensity > 0f) {
            startWaveAnimation()
        }

        colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            this.interpolator = interpolator
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                val c0 = evaluator.evaluate(fraction, startColors[0], newColors[0]) as Int
                val c1 = evaluator.evaluate(fraction, startColors[1], newColors[1]) as Int
                val c2 = evaluator.evaluate(fraction, startColors[2], newColors[2]) as Int

                currentColors[0] = c0
                currentColors[1] = c1
                currentColors[2] = c2

                currentIntensity = startIntensity + (targetIntensity - startIntensity) * fraction

                updateDrawableState()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (targetColors.contentEquals(IDLE_BACKGROUND)) {
                        stopWaveAnimation()
                    }
                }
            })
            start()
        }
    }

    fun setImmediate(newColors: IntArray) {
        colorAnimator?.cancel()
        targetColors = newColors.clone()
        currentColors = newColors.clone()
        currentIntensity = if (newColors.contentEquals(IDLE_BACKGROUND)) 0f else 1f
        if (currentIntensity == 0f) {
            stopWaveAnimation()
        } else {
            startWaveAnimation()
        }
        updateDrawableState()
    }

    fun startWaveAnimation() {
        if (waveAnimator?.isRunning == true) return
        waveStartTime = SystemClock.uptimeMillis()
        waveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val elapsed = (SystemClock.uptimeMillis() - waveStartTime) / 1000f
                if (drawable is AgslWaveDrawable) {
                    drawable.updateTime(elapsed)
                }
                // Force all host views to redraw their background at display refresh rate
                attachedViews.forEach { it.postInvalidateOnAnimation() }
            }
            start()
        }
    }

    fun stopWaveAnimation() {
        waveAnimator?.cancel()
        waveAnimator = null
    }

    private fun updateDrawableState() {
        if (drawable is AgslWaveDrawable) {
            drawable.updateColors(currentColors[2], currentColors[1], currentIntensity)
        } else if (drawable is FallbackGradientDrawable) {
            drawable.updateColors(currentColors)
        }
        attachedViews.forEach { it.postInvalidateOnAnimation() }
    }

    // --- AGSL Wave Implementation for Android 13+ (API 33+) ---
    private class AgslWaveDrawable : Drawable() {
        private val shaderSrc = """
            uniform float2 uResolution;
            uniform float uTime;
            uniform float3 uColor1;
            uniform float3 uColor2;
            uniform float uIntensity;

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / uResolution;
                
                // Multi-frequency undulating liquid waves
                float w1 = sin(uv.x * 3.2 + uTime * 1.8) * 0.09;
                float w2 = cos(uv.x * 5.4 - uTime * 1.3 + uv.y * 2.2) * 0.06;
                float w3 = sin((uv.x + uv.y) * 4.0 + uTime * 2.4) * 0.035;
                float wave = w1 + w2 + w3;
                
                // Musical beat pulse: subtle organic breathing (~105 BPM breathing)
                float beat = 1.0 + 0.12 * sin(uTime * 3.4);
                
                // Distorted vertical coordinate
                float y = uv.y + wave;
                
                // Fluid wave crest framing the music player (y ~ 0.65)
                float crestDist = abs(y - 0.65);
                float crest = exp(-crestDist * crestDist * 40.0);
                float aura = exp(-crestDist * crestDist * 12.0);
                
                // Liquid body in the lower portion behind music card
                float body = smoothstep(0.56, 0.92, y);
                
                // Horizontal fluid color swirl
                float swirl = 0.5 + 0.5 * sin(uv.x * 2.8 + uTime * 1.1);
                float3 bodyColor = mix(uColor2, uColor1, swirl * 0.45 + body * 0.55);
                
                // Radiant crest highlight
                float3 crestColor = uColor1 * 1.3 + uColor2 * 0.5;
                
                // Beautifully balanced: glowing, visible, but comfortable and moody
                float3 col = bodyColor * body * 0.70 + crestColor * (crest * 0.60 + aura * 0.25);
                
                col *= beat * uIntensity;
                
                return half4(col, 1.0);
            }
        """.trimIndent()

        private val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(shaderSrc).apply {
                setFloatUniform("uResolution", 1220f, 2712f)
                setFloatUniform("uTime", 0f)
                setFloatUniform("uColor1", 0f, 0f, 0f)
                setFloatUniform("uColor2", 0f, 0f, 0f)
                setFloatUniform("uIntensity", 0f)
            }
        } else null

        private val paint = Paint().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                this.shader = this@AgslWaveDrawable.shader
            }
        }

        fun updateTime(timeSeconds: Float) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                shader?.setFloatUniform("uTime", timeSeconds)
                invalidateSelf()
            }
        }

        fun updateColors(c1: Int, c2: Int, intensity: Float) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val r1 = Color.red(c1) / 255f
                val g1 = Color.green(c1) / 255f
                val b1 = Color.blue(c1) / 255f

                val r2 = Color.red(c2) / 255f
                val g2 = Color.green(c2) / 255f
                val b2 = Color.blue(c2) / 255f

                shader?.setFloatUniform("uColor1", r1, g1, b1)
                shader?.setFloatUniform("uColor2", r2, g2, b2)
                shader?.setFloatUniform("uIntensity", intensity)
                invalidateSelf()
            }
        }

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val w = bounds.width().toFloat().coerceAtLeast(1f)
                val h = bounds.height().toFloat().coerceAtLeast(1f)
                shader?.setFloatUniform("uResolution", w, h)
                invalidateSelf()
            }
        }

        override fun draw(canvas: Canvas) {
            canvas.drawRect(bounds, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }

    // --- Fallback Gradient for API < 33 ---
    private class FallbackGradientDrawable : Drawable() {
        private val gradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            IDLE_BACKGROUND
        ).apply {
            setDither(true)
        }

        fun updateColors(colors: IntArray) {
            gradient.colors = colors.clone()
            invalidateSelf()
        }

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            gradient.bounds = bounds
        }

        override fun draw(canvas: Canvas) {
            gradient.draw(canvas)
        }

        override fun setAlpha(alpha: Int) {
            gradient.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            gradient.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }
}
