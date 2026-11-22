package app.olauncher.media

import android.graphics.Bitmap
import android.graphics.Color
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

object ArtColorExtractor {

    private val NEUTRAL_THEME = intArrayOf(
        Color.BLACK,
        Color.parseColor("#000000"),
        Color.parseColor("#1A1A1D")
    )

    private val COLOR_TEXT_PRIMARY = Color.parseColor("#F2F2F2")
    private val COLOR_TEXT_SECONDARY = Color.parseColor("#A1A1A6")

    // LruCache of 8 entries keyed by artKey per PRD 4.3
    private val cache = LruCache<String, IntArray>(8)

    suspend fun extractColors(artKey: String, bitmap: Bitmap?): IntArray =
        withContext(Dispatchers.Default) {
            if (bitmap == null) return@withContext NEUTRAL_THEME

            cache.get(artKey)?.let { return@withContext it }

            // Downscale the art to 32x32 per PRD 4.3
            val smallBitmap = if (bitmap.width == 32 && bitmap.height == 32) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, 32, 32, false)
            }

            val pixels = IntArray(32 * 32)
            smallBitmap.getPixels(pixels, 0, 32, 0, 0, 32, 32)

            val bucketWeights = FloatArray(12)
            val bucketSumR = FloatArray(12)
            val bucketSumG = FloatArray(12)
            val bucketSumB = FloatArray(12)
            val hsv = FloatArray(3)

            var totalWeight = 0f

            for (pixel in pixels) {
                Color.colorToHSV(pixel, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]

                // Skip pixels with value below 0.15 or saturation below 0.15 per PRD 4.3
                if (value < 0.15f || sat < 0.15f) continue

                val bucketIndex = ((hue / 30f).toInt()).coerceIn(0, 11)
                val weight = sat * value

                bucketWeights[bucketIndex] += weight
                bucketSumR[bucketIndex] += Color.red(pixel) * weight
                bucketSumG[bucketIndex] += Color.green(pixel) * weight
                bucketSumB[bucketIndex] += Color.blue(pixel) * weight
                totalWeight += weight
            }

            // If the total weight is tiny, return the neutral theme per PRD 4.3
            if (totalWeight < 4.0f) {
                cache.put(artKey, NEUTRAL_THEME)
                return@withContext NEUTRAL_THEME
            }

            // C1 = the average color of the heaviest bucket
            var c1Index = 0
            var c1Weight = bucketWeights[0]
            for (i in 1..11) {
                if (bucketWeights[i] > c1Weight) {
                    c1Weight = bucketWeights[i]
                    c1Index = i
                }
            }

            val c1R = (bucketSumR[c1Index] / c1Weight).toInt().coerceIn(0, 255)
            val c1G = (bucketSumG[c1Index] / c1Weight).toInt().coerceIn(0, 255)
            val c1B = (bucketSumB[c1Index] / c1Weight).toInt().coerceIn(0, 255)
            val c1Color = Color.rgb(c1R, c1G, c1B)
            val c1Hue = c1Index * 30f + 15f

            // C2 = the heaviest bucket whose hue is at least 60 deg from C1 and weight >= 30% of C1's
            var c2Index = -1
            var c2Weight = 0f
            for (i in 0..11) {
                val bucketHue = i * 30f + 15f
                val hueDiff = min(abs(bucketHue - c1Hue), 360f - abs(bucketHue - c1Hue))
                if (hueDiff >= 60f && bucketWeights[i] >= c1Weight * 0.30f) {
                    if (bucketWeights[i] > c2Weight) {
                        c2Weight = bucketWeights[i]
                        c2Index = i
                    }
                }
            }

            val c2Color = if (c2Index != -1 && c2Weight > 0f) {
                val c2R = (bucketSumR[c2Index] / c2Weight).toInt().coerceIn(0, 255)
                val c2G = (bucketSumG[c2Index] / c2Weight).toInt().coerceIn(0, 255)
                val c2B = (bucketSumB[c2Index] / c2Weight).toInt().coerceIn(0, 255)
                Color.rgb(c2R, c2G, c2B)
            } else {
                c1Color
            }

            // Prepare gradient stops:
            // Top: #000000
            // Balanced, moody dark tones for refined AGSL fluid aurora wave
            val hslC1 = FloatArray(3)
            val hslC2 = FloatArray(3)
            colorToHsl(c1Color, hslC1)
            colorToHsl(c2Color, hslC2)

            hslC1[1] = hslC1[1].coerceIn(0.60f, 0.90f)
            hslC1[2] = 0.28f

            hslC2[1] = hslC2[1].coerceIn(0.50f, 0.85f)
            hslC2[2] = 0.16f

            val adjustedC1 = hslToColor(hslC1)
            val adjustedC2 = hslToColor(hslC2)

            val result = intArrayOf(Color.BLACK, adjustedC2, adjustedC1)
            cache.put(artKey, result)
            result
        }

    fun colorToHsl(color: Int, outHsl: FloatArray) {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        val l = (max + min) / 2f
        val s = if (delta == 0f) 0f else delta / (1f - abs(2f * l - 1f))

        var h = when {
            delta == 0f -> 0f
            max == r -> ((g - b) / delta) % 6f
            max == g -> ((b - r) / delta) + 2f
            else -> ((r - g) / delta) + 4f
        } * 60f
        if (h < 0f) h += 360f

        outHsl[0] = h
        outHsl[1] = s
        outHsl[2] = l
    }

    fun hslToColor(hsl: FloatArray): Int {
        val h = hsl[0]
        val s = hsl[1]
        val l = hsl[2]

        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f

        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val r = Math.round((r1 + m) * 255f).coerceIn(0, 255)
        val g = Math.round((g1 + m) * 255f).coerceIn(0, 255)
        val b = Math.round((b1 + m) * 255f).coerceIn(0, 255)

        return Color.rgb(r, g, b)
    }

    private fun calculateLuminance(color: Int): Double {
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0

        val rLin = if (r <= 0.03928) r / 12.92 else Math.pow((r + 0.055) / 1.055, 2.4)
        val gLin = if (g <= 0.03928) g / 12.92 else Math.pow((g + 0.055) / 1.055, 2.4)
        val bLin = if (b <= 0.03928) b / 12.92 else Math.pow((b + 0.055) / 1.055, 2.4)

        return 0.2126 * rLin + 0.7152 * gLin + 0.0722 * bLin
    }

    private fun calculateContrast(foreground: Int, background: Int): Double {
        val l1 = calculateLuminance(foreground)
        val l2 = calculateLuminance(background)
        return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05)
    }
}
