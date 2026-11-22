package app.olauncher.clock

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import app.olauncher.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ClockStyle {

    const val DEFAULT_PRIMARY_COLOR = 0xFFF2F2F2.toInt() // @color/ol_text_primary

    enum class Period {
        MORNING,
        DAY,
        EVENING,
        NIGHT
    }

    data class Style(
        @FontRes val fontRes: Int,
        val alpha: Float,
        val fallbackFamily: String,
        val fallbackStyle: Int = Typeface.NORMAL,
    )

    fun periodFor(hour: Int): Period {
        return when (hour) {
            in 5..10 -> Period.MORNING
            in 11..16 -> Period.DAY
            in 17..21 -> Period.EVENING
            else -> Period.NIGHT
        }
    }

    fun styleFor(hour: Int): Style {
        return when (periodFor(hour)) {
            Period.MORNING -> Style(
                fontRes = R.font.outfit_extralight,
                alpha = 1.0f,
                fallbackFamily = "sans-serif-light"
            )
            Period.DAY -> Style(
                fontRes = R.font.outfit_light,
                alpha = 1.0f,
                fallbackFamily = "sans-serif"
            )
            Period.EVENING -> Style(
                fontRes = R.font.outfit_extralight,
                alpha = 0.95f,
                fallbackFamily = "sans-serif-light"
            )
            Period.NIGHT -> Style(
                fontRes = R.font.outfit_thin,
                alpha = 0.80f,
                fallbackFamily = "sans-serif-thin"
            )
        }
    }

    fun getTypeface(context: Context, style: Style): Typeface {
        return try {
            ResourcesCompat.getFont(context, style.fontRes)
                ?: Typeface.create(style.fallbackFamily, style.fallbackStyle)
        } catch (e: Exception) {
            Typeface.create(style.fallbackFamily, style.fallbackStyle)
        }
    }

    fun format(time: Date, is24h: Boolean, baseColor: Int = DEFAULT_PRIMARY_COLOR): CharSequence {
        val pattern = if (is24h) "HH:mm" else "h:mm"
        val rawTime = SimpleDateFormat(pattern, Locale.getDefault()).format(time)
        val colonIndex = rawTime.indexOf(':')
        if (colonIndex == -1) return rawTime

        val colonAlpha = (Color.alpha(baseColor) * 0.40f).toInt().coerceIn(0, 255)
        val colonColor = Color.argb(
            colonAlpha,
            Color.red(baseColor),
            Color.green(baseColor),
            Color.blue(baseColor)
        )

        val spannable = SpannableString(rawTime)
        for (i in rawTime.indices) {
            if (rawTime[i] == ':') {
                spannable.setSpan(
                    ForegroundColorSpan(colonColor),
                    i,
                    i + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return spannable
    }

    fun format(calendar: Calendar, is24h: Boolean, baseColor: Int = DEFAULT_PRIMARY_COLOR): CharSequence {
        return format(calendar.time, is24h, baseColor)
    }

    fun format(timeMs: Long, is24h: Boolean, baseColor: Int = DEFAULT_PRIMARY_COLOR): CharSequence {
        return format(Date(timeMs), is24h, baseColor)
    }
}
