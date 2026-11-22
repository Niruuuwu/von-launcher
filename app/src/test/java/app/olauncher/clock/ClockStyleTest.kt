package app.olauncher.clock

import app.olauncher.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockStyleTest {

    @Test
    fun `morning period between 05 and 11 returns ExtraLight and alpha 1_0`() {
        for (hour in 5..10) {
            val style = ClockStyle.styleFor(hour)
            assertEquals("Hour $hour should be MORNING", ClockStyle.Period.MORNING, ClockStyle.periodFor(hour))
            assertEquals("Hour $hour font", R.font.outfit_extralight, style.fontRes)
            assertEquals("Hour $hour alpha", 1.0f, style.alpha, 0.001f)
        }
    }

    @Test
    fun `day period between 11 and 17 returns Light and alpha 1_0`() {
        for (hour in 11..16) {
            val style = ClockStyle.styleFor(hour)
            assertEquals("Hour $hour should be DAY", ClockStyle.Period.DAY, ClockStyle.periodFor(hour))
            assertEquals("Hour $hour font", R.font.outfit_light, style.fontRes)
            assertEquals("Hour $hour alpha", 1.0f, style.alpha, 0.001f)
        }
    }

    @Test
    fun `evening period between 17 and 22 returns ExtraLight and alpha 0_95`() {
        for (hour in 17..21) {
            val style = ClockStyle.styleFor(hour)
            assertEquals("Hour $hour should be EVENING", ClockStyle.Period.EVENING, ClockStyle.periodFor(hour))
            assertEquals("Hour $hour font", R.font.outfit_extralight, style.fontRes)
            assertEquals("Hour $hour alpha", 0.95f, style.alpha, 0.001f)
        }
    }

    @Test
    fun `night period between 22 and 05 returns Thin and alpha 0_80`() {
        val nightHours = listOf(22, 23, 0, 1, 2, 3, 4)
        for (hour in nightHours) {
            val style = ClockStyle.styleFor(hour)
            assertEquals("Hour $hour should be NIGHT", ClockStyle.Period.NIGHT, ClockStyle.periodFor(hour))
            assertEquals("Hour $hour font", R.font.outfit_thin, style.fontRes)
            assertEquals("Hour $hour alpha", 0.80f, style.alpha, 0.001f)
        }
    }

    @Test
    fun `acceptance criteria specific hours verification`() {
        // Acceptance #2: 03:00 Thin and dimmer, 08:00 ExtraLight, 14:00 Light, 19:00 ExtraLight
        val style3am = ClockStyle.styleFor(3)
        assertEquals(R.font.outfit_thin, style3am.fontRes)
        assertEquals(0.80f, style3am.alpha, 0.001f)

        val style8am = ClockStyle.styleFor(8)
        assertEquals(R.font.outfit_extralight, style8am.fontRes)
        assertEquals(1.0f, style8am.alpha, 0.001f)

        val style2pm = ClockStyle.styleFor(14)
        assertEquals(R.font.outfit_light, style2pm.fontRes)
        assertEquals(1.0f, style2pm.alpha, 0.001f)

        val style7pm = ClockStyle.styleFor(19)
        assertEquals(R.font.outfit_extralight, style7pm.fontRes)
        assertEquals(0.95f, style7pm.alpha, 0.001f)
    }
}
