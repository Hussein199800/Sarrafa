package com.example

import com.example.domain.model.AsrMadhab
import com.example.domain.model.PrayerCalcMethod
import com.example.domain.model.PrayerManualOffsets
import com.example.domain.model.PrayerType
import com.example.domain.model.UserSettings
import com.example.domain.util.ArabicTextNormalizer
import com.example.domain.util.HijriCalendarCalculator
import com.example.domain.util.NumeralFormatter
import com.example.domain.util.PrayerTimeEngine
import com.example.domain.util.QiblaCalculator
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoField
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `prayer times match official benchmarks for 3 cities within 1 minute`() {
        // 1. Makkah (Umm al-Qura) on 2026-03-20 (Spring Equinox)
        val makkahSchedule = PrayerTimeEngine.calculateForDate(
            date = LocalDate.of(2026, 3, 20),
            latitude = 21.4225,
            longitude = 39.8262,
            timeZoneId = "Asia/Riyadh",
            method = PrayerCalcMethod.UMM_AL_QURA,
            madhab = AsrMadhab.SHAFI,
            offsets = PrayerManualOffsets()
        )
        // Expected Makkah on Mar 20 (Umm al-Qura 18.5°): Fajr ~05:10, Dhuhr ~12:28, Asr ~15:52, Maghrib ~18:31, Isha ~20:01 (90 min after Maghrib)
        assertEquals(5, makkahSchedule.fajr.hour)
        assertTrue("Fajr minute was ${makkahSchedule.fajr.minute}", abs(makkahSchedule.fajr.minute - 10) <= 1)
        assertEquals(12, makkahSchedule.dhuhr.hour)
        assertTrue("Dhuhr minute was ${makkahSchedule.dhuhr.minute}", abs(makkahSchedule.dhuhr.minute - 28) <= 1)
        assertEquals(90L, java.time.Duration.between(makkahSchedule.maghrib, makkahSchedule.isha).toMinutes())

        // 2. Cairo (Egyptian General Authority of Survey) on 2026-03-20
        val cairoSchedule = PrayerTimeEngine.calculateForDate(
            date = LocalDate.of(2026, 3, 20),
            latitude = 30.0444,
            longitude = 31.2357,
            timeZoneId = "Africa/Cairo",
            method = PrayerCalcMethod.EGYPTIAN,
            madhab = AsrMadhab.SHAFI,
            offsets = PrayerManualOffsets()
        )
        // Expected Cairo on Mar 20 (UTC+2): Fajr ~04:33, Dhuhr ~12:03, Maghrib ~18:06
        assertEquals(4, cairoSchedule.fajr.hour)
        assertTrue("Cairo Fajr minute was ${cairoSchedule.fajr.minute}", abs(cairoSchedule.fajr.minute - 33) <= 1)
        assertEquals(12, cairoSchedule.dhuhr.hour)
        assertTrue("Cairo Dhuhr minute was ${cairoSchedule.dhuhr.minute}", abs(cairoSchedule.dhuhr.minute - 3) <= 1)

        // 3. Amman (Muslim World League) on 2026-03-20
        val ammanSchedule = PrayerTimeEngine.calculateForDate(
            date = LocalDate.of(2026, 3, 20),
            latitude = 31.9539,
            longitude = 35.9106,
            timeZoneId = "Asia/Amman",
            method = PrayerCalcMethod.MUSLIM_WORLD_LEAGUE,
            madhab = AsrMadhab.SHAFI,
            offsets = PrayerManualOffsets()
        )
        // Amman is UTC+3: Dhuhr ~12:44
        assertEquals(12, ammanSchedule.dhuhr.hour)
        assertTrue("Amman Dhuhr minute was ${ammanSchedule.dhuhr.minute}", abs(ammanSchedule.dhuhr.minute - 44) <= 1)
    }

    @Test
    fun `next prayer rollover across midnight returns tomorrow Fajr`() {
        val settings = UserSettings(
            latitude = 21.4225,
            longitude = 39.8262,
            timeZoneId = "Asia/Riyadh",
            calcMethod = PrayerCalcMethod.UMM_AL_QURA
        )
        // 23:30 at night (after Isha)
        val lateNight = ZonedDateTime.of(2026, 10, 24, 23, 30, 0, 0, ZoneId.of("Asia/Riyadh"))
        val nextPrayer = PrayerTimeEngine.getNextPrayer(lateNight, settings)
        assertEquals(PrayerType.FAJR, nextPrayer.prayerType)
        assertTrue(nextPrayer.isTomorrowFajr)
        assertEquals(LocalDate.of(2026, 10, 25), nextPrayer.prayerTime.toLocalDate())
        assertTrue(nextPrayer.remainingSeconds > 0)
    }

    @Test
    fun `hijri calendar month boundaries and plus minus 2 days offset`() {
        val sampleDate = LocalDate.of(2026, 2, 18) // Around 1 Ramadan 1447 AH in Umm al-Qura
        val baseHijri = HijriCalendarCalculator.toAdjustedHijriDate(sampleDate, 0)
        val plusTwo = HijriCalendarCalculator.toAdjustedHijriDate(sampleDate, 2)
        val minusTwo = HijriCalendarCalculator.toAdjustedHijriDate(sampleDate, -2)

        val diffPlus = java.time.temporal.ChronoUnit.DAYS.between(baseHijri, plusTwo)
        val diffMinus = java.time.temporal.ChronoUnit.DAYS.between(minusTwo, baseHijri)
        assertEquals(2L, diffPlus)
        assertEquals(2L, diffMinus)

        // Verify Hijri year boundary rollover in upcoming events
        val events = HijriCalendarCalculator.getUpcomingEvents(LocalDate.of(2026, 6, 10), 0)
        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.daysRemaining >= 0 })
        // Strictly ensure unapproved events (Isra/Miraj, Mid-Shaban, Mawlid) are NOT in the list
        val ids = events.map { it.id }
        assertFalse(ids.contains("isra_miraj"))
        assertFalse(ids.contains("mid_shaban"))
        assertFalse(ids.contains("mawlid"))
    }

    @Test
    fun `qibla great circle bearing matches spherical trigonometry reference points`() {
        // Reference points computed via standard WGS84 / Great Circle forward azimuth to Kaaba (21.422487° N, 39.826206° E):
        // 1. Cairo (30.0444 N, 31.2357 E) -> ~136.2° SE
        val cairoBearing = QiblaCalculator.calculateQiblaBearing(30.0444, 31.2357)
        assertEquals(136.2, cairoBearing, 0.5)

        // 2. London (51.5074 N, -0.1278 W) -> ~118.99° ESE
        val londonBearing = QiblaCalculator.calculateQiblaBearing(51.5074, -0.1278)
        assertEquals(119.0, londonBearing, 0.5)

        // 3. Tokyo (35.6762 N, 139.6503 E) -> ~293.0° WNW
        val tokyoBearing = QiblaCalculator.calculateQiblaBearing(35.6762, 139.6503)
        assertEquals(293.0, tokyoBearing, 0.5)

        // 4. New York (40.7128 N, -74.0060 W) -> ~58.48° NE
        val nyBearing = QiblaCalculator.calculateQiblaBearing(40.7128, -74.0060)
        assertEquals(58.5, nyBearing, 0.5)
    }

    @Test
    fun `arabic text normalizer strips tashkeel and unifies hamzas`() {
        val vocalized = "﴿ اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ﴾"
        assertTrue(ArabicTextNormalizer.matchesQuery(vocalized, "الله لا اله الا هو الحي القيوم"))
        assertTrue(ArabicTextNormalizer.matchesQuery("إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ", "انما الاعمال بالنيات"))
    }

    @Test
    fun `numeral formatter converts between Arabic Indic and Western digits`() {
        assertEquals("١٤٤٨", NumeralFormatter.format(1448, useArabicIndic = true))
        assertEquals("1448", NumeralFormatter.format(1448, useArabicIndic = false))
        assertEquals("2026", NumeralFormatter.toWestern("٢٠٢٦"))
    }

    @Test
    fun `official timetable comparison tool parses and computes minute difference`() {
        val settings = UserSettings(
            latitude = 21.4225,
            longitude = 39.8262,
            timeZoneId = "Asia/Riyadh",
            calcMethod = PrayerCalcMethod.UMM_AL_QURA
        )
        val csv = "2026-03-20,05:10,12:28,15:52,18:31,20:01"
        val rows = PrayerTimeEngine.compareWithOfficialTable(csv, settings)
        assertEquals(5, rows.size)
        assertTrue("Diffs were ${rows.map { it.differenceMinutes }}", rows.all { abs(it.differenceMinutes) <= 1L })
    }

    @Test
    fun `wcag aa contrast ratio is at least 4_5 to 1 across all 5 palettes and all 6 prayer hero gradients`() {
        for (palette in com.example.domain.model.WirdiThemePalette.entries) {
            for (isDark in listOf(false, true)) {
                val scheme = com.example.ui.theme.getPaletteColorScheme(palette, isDark)
                val bgRatio = com.example.ui.theme.WcagContrastVerifier.contrastRatio(scheme.onBackground, scheme.background)
                val surfaceRatio = com.example.ui.theme.WcagContrastVerifier.contrastRatio(scheme.onSurface, scheme.surface)
                val secSurfaceRatio = com.example.ui.theme.WcagContrastVerifier.contrastRatio(scheme.onSurfaceVariant, scheme.surface)
                val primaryOnContainerRatio = com.example.ui.theme.WcagContrastVerifier.contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer)

                assertTrue("Palette $palette (dark=$isDark) onBackground contrast $bgRatio < 4.5", bgRatio >= 4.5)
                assertTrue("Palette $palette (dark=$isDark) onSurface contrast $surfaceRatio < 4.5", surfaceRatio >= 4.5)
                assertTrue("Palette $palette (dark=$isDark) onSurfaceVariant contrast $secSurfaceRatio < 4.5", secSurfaceRatio >= 4.5)
                assertTrue("Palette $palette (dark=$isDark) onPrimaryContainer contrast $primaryOnContainerRatio < 4.5", primaryOnContainerRatio >= 4.5)
            }
        }

        for (prayer in PrayerType.entries) {
            for (isDark in listOf(false, true)) {
                val hero = com.example.ui.theme.getPrayerHeroGradient(prayer, isDark)
                val startRatio = com.example.ui.theme.WcagContrastVerifier.contrastRatio(hero.contentColor, hero.startColor)
                val endRatio = com.example.ui.theme.WcagContrastVerifier.contrastRatio(hero.contentColor, hero.endColor)
                val secStartRatio = com.example.ui.theme.WcagContrastVerifier.contrastRatio(hero.secondaryContentColor, hero.startColor)
                val secEndRatio = com.example.ui.theme.WcagContrastVerifier.contrastRatio(hero.secondaryContentColor, hero.endColor)

                assertTrue("Hero $prayer (dark=$isDark) start contrast $startRatio < 4.5", startRatio >= 4.5)
                assertTrue("Hero $prayer (dark=$isDark) end contrast $endRatio < 4.5", endRatio >= 4.5)
                assertTrue("Hero $prayer (dark=$isDark) secondary start contrast $secStartRatio < 4.5", secStartRatio >= 4.5)
                assertTrue("Hero $prayer (dark=$isDark) secondary end contrast $secEndRatio < 4.5", secEndRatio >= 4.5)
            }
        }

        for (gate in com.example.domain.model.DhikrCategory.standaloneGateCategories) {
            for (isDark in listOf(false, true)) {
                val spec = com.example.ui.theme.getDhikrGateVisualSpec(gate, isDark)
                val titleStartRatio = com.example.ui.theme.WcagContrastVerifier.contrastRatio(spec.titleColor, spec.containerStart)
                val titleEndRatio = com.example.ui.theme.WcagContrastVerifier.contrastRatio(spec.titleColor, spec.containerEnd)
                val subStartRatio = com.example.ui.theme.WcagContrastVerifier.contrastRatio(spec.subtitleColor, spec.containerStart)
                val subEndRatio = com.example.ui.theme.WcagContrastVerifier.contrastRatio(spec.subtitleColor, spec.containerEnd)

                assertTrue("Gate $gate (dark=$isDark) title start contrast $titleStartRatio < 4.5", titleStartRatio >= 4.5)
                assertTrue("Gate $gate (dark=$isDark) title end contrast $titleEndRatio < 4.5", titleEndRatio >= 4.5)
                assertTrue("Gate $gate (dark=$isDark) subtitle start contrast $subStartRatio < 4.5", subStartRatio >= 4.5)
                assertTrue("Gate $gate (dark=$isDark) subtitle end contrast $subEndRatio < 4.5", subEndRatio >= 4.5)
            }
        }

        for (palette in com.example.domain.model.WirdiThemePalette.entries) {
            for (isDark in listOf(false, true)) {
                val scheme = com.example.ui.theme.getPaletteColorScheme(palette, isDark)
                assertFalse("Palette $palette (dark=$isDark) background must not be pure white #FFFFFF", com.example.ui.theme.WcagContrastVerifier.isPureWhite(scheme.background))
                assertFalse("Palette $palette (dark=$isDark) surface must not be pure white #FFFFFF", com.example.ui.theme.WcagContrastVerifier.isPureWhite(scheme.surface))
                assertFalse("Palette $palette (dark=$isDark) surfaceVariant must not be pure white #FFFFFF", com.example.ui.theme.WcagContrastVerifier.isPureWhite(scheme.surfaceVariant))
            }
        }
    }

    @Test
    fun `legacy theme palette names migrate safely to Sakina Muasira palettes`() {
        assertEquals(com.example.domain.model.WirdiThemePalette.UFUQ, com.example.domain.model.WirdiThemePalette.fromStoredName("FAJR"))
        assertEquals(com.example.domain.model.WirdiThemePalette.SADEEM, com.example.domain.model.WirdiThemePalette.fromStoredName("LAYL"))
        assertEquals(com.example.domain.model.WirdiThemePalette.WAHA_LAYLIYA, com.example.domain.model.WirdiThemePalette.fromStoredName("YAQOUT"))
        assertEquals(com.example.domain.model.WirdiThemePalette.DIF, com.example.domain.model.WirdiThemePalette.fromStoredName("SAHRA"))
        assertEquals(com.example.domain.model.WirdiThemePalette.HAJAR, com.example.domain.model.WirdiThemePalette.fromStoredName("BARADA"))
    }

    @Test
    fun `idlib syria mawaqit prayer times match mosque schedule within 1 minute`() {
        val idlibSettings = UserSettings(
            cityNameAr = "إدلب",
            countryAr = "سوريا",
            latitude = 35.9306,
            longitude = 36.6339,
            timeZoneId = "Asia/Damascus",
            calcMethod = PrayerCalcMethod.SYRIA_MAWAQIT
        )
        val csv = "2026-10-24,05:20,12:19,15:22,17:49,19:12"
        val rows = PrayerTimeEngine.compareWithOfficialTable(csv, idlibSettings)
        assertEquals(5, rows.size)
        assertTrue(
            "Idlib diffs were ${rows.map { "${it.prayerType}: ${it.differenceMinutes} (calc=${it.calculatedTime})" }}",
            rows.all { abs(it.differenceMinutes) <= 1L }
        )
    }

    @Test
    fun `high latitude rules produce valid ordered summer prayer times in Copenhagen and Oslo`() {
        val summerSolstice = LocalDate.of(2026, 6, 21)
        for (rule in com.example.domain.model.HighLatitudeRuleSetting.entries) {
            // Copenhagen (55.6761 N, 12.5683 E) & Oslo (59.9139 N, 10.7522 E)
            for ((lat, lon, tz) in listOf(
                Triple(55.6761, 12.5683, "Europe/Copenhagen"),
                Triple(59.9139, 10.7522, "Europe/Oslo")
            )) {
                val schedule = PrayerTimeEngine.calculateForDate(
                    date = summerSolstice,
                    latitude = lat,
                    longitude = lon,
                    timeZoneId = tz,
                    method = PrayerCalcMethod.MUSLIM_WORLD_LEAGUE,
                    madhab = AsrMadhab.SHAFI,
                    offsets = PrayerManualOffsets(),
                    highLatitudeRule = rule
                )
                assertTrue("Rule $rule at lat $lat: Fajr (${schedule.fajr}) must be before Sunrise (${schedule.sunrise})", schedule.fajr.isBefore(schedule.sunrise))
                assertTrue("Rule $rule at lat $lat: Sunrise must be before Dhuhr", schedule.sunrise.isBefore(schedule.dhuhr))
                assertTrue("Rule $rule at lat $lat: Dhuhr must be before Asr", schedule.dhuhr.isBefore(schedule.asr))
                assertTrue("Rule $rule at lat $lat: Asr must be before Maghrib", schedule.asr.isBefore(schedule.maghrib))
                assertTrue("Rule $rule at lat $lat: Maghrib (${schedule.maghrib}) must be before Isha (${schedule.isha})", schedule.maghrib.isBefore(schedule.isha))
            }
        }
    }

    @Test
    fun `sunnah times calculate duha window, islamic midnight, and last third of night accurately`() {
        val settings = UserSettings(
            cityNameAr = "إدلب",
            countryAr = "سوريا",
            latitude = 35.9306,
            longitude = 36.6339,
            timeZoneId = "Asia/Damascus",
            calcMethod = PrayerCalcMethod.SYRIA_MAWAQIT
        )
        val date = LocalDate.of(2026, 10, 24)
        val schedule = PrayerTimeEngine.calculateForSettings(date, settings)
        val sunnah = PrayerTimeEngine.calculateSunnahTimes(date, settings)

        assertEquals(schedule.sunrise.plusMinutes(15), sunnah.duhaStart)
        assertEquals(schedule.dhuhr.minusMinutes(10), sunnah.duhaEnd)
        assertTrue("Islamic midnight must be after Maghrib", sunnah.islamicMidnight.isAfter(schedule.maghrib))
        assertTrue("Last third of night must be after Islamic midnight", sunnah.lastThirdOfNightStart.isAfter(sunnah.islamicMidnight))
    }

    @Test
    fun `user settings sanitized clamps out of bounds values and restores missing prayer notifications`() {
        val raw = UserSettings(
            cityNameAr = "   ",
            countryAr = "",
            latitude = 140.0,
            longitude = -250.0,
            timeZoneId = "",
            hijriOffsetDays = 99,
            readingFontScale = 5.0f,
            prayerNotifications = emptyMap(),
            morningReminderHour = 30,
            morningReminderMinute = -10,
            snoozeMinutes = 500,
            tasbihTarget = -99,
            homeVisibleSections = emptySet()
        )
        val clean = raw.sanitized()
        assertEquals("إدلب", clean.cityNameAr)
        assertEquals(89.9, clean.latitude, 0.001)
        assertEquals(-180.0, clean.longitude, 0.001)
        assertEquals(2, clean.hijriOffsetDays)
        assertEquals(1.6f, clean.readingFontScale, 0.001f)
        assertEquals(PrayerType.entries.size, clean.prayerNotifications.size)
        assertEquals(23, clean.morningReminderHour)
        assertEquals(0, clean.morningReminderMinute)
        assertEquals(120, clean.snoozeMinutes)
        assertEquals(0, clean.tasbihTarget)
        assertTrue(clean.homeVisibleSections.isNotEmpty())
    }
}
