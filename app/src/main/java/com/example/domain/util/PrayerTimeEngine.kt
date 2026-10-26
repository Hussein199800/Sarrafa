package com.example.domain.util

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.HighLatitudeRule
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.example.domain.model.AsrMadhab
import com.example.domain.model.HighLatitudeRuleSetting
import com.example.domain.model.PrayerCalcMethod
import com.example.domain.model.PrayerManualOffsets
import com.example.domain.model.PrayerType
import com.example.domain.model.UserSettings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class DailyPrayerSchedule(
    val date: LocalDate,
    val fajr: ZonedDateTime,
    val sunrise: ZonedDateTime,
    val dhuhr: ZonedDateTime,
    val asr: ZonedDateTime,
    val maghrib: ZonedDateTime,
    val isha: ZonedDateTime
) {
    fun getTimeFor(prayer: PrayerType): ZonedDateTime = when (prayer) {
        PrayerType.FAJR -> fajr
        PrayerType.SUNRISE -> sunrise
        PrayerType.DHUHR -> dhuhr
        PrayerType.ASR -> asr
        PrayerType.MAGHRIB -> maghrib
        PrayerType.ISHA -> isha
    }

    fun timeFor(prayer: PrayerType): ZonedDateTime = getTimeFor(prayer)

    fun allOrdered(): List<Pair<PrayerType, ZonedDateTime>> = listOf(
        PrayerType.FAJR to fajr,
        PrayerType.SUNRISE to sunrise,
        PrayerType.DHUHR to dhuhr,
        PrayerType.ASR to asr,
        PrayerType.MAGHRIB to maghrib,
        PrayerType.ISHA to isha
    )
}

data class SunnahTimesInfo(
    val duhaStart: ZonedDateTime,
    val duhaEnd: ZonedDateTime,
    val islamicMidnight: ZonedDateTime,
    val lastThirdOfNightStart: ZonedDateTime,
    val isDuhaValid: Boolean = true,
    val isNightValid: Boolean = true
)

data class NextPrayerInfo(
    val prayerType: PrayerType,
    val prayerTime: ZonedDateTime,
    val remainingSeconds: Long,
    val isTomorrowFajr: Boolean
)

data class OfficialComparisonRow(
    val date: String,
    val prayerType: PrayerType,
    val officialTime: String,
    val calculatedTime: String,
    val differenceMinutes: Long
)

object PrayerTimeEngine {

    fun getTypicalIqamahOffsetMinutes(prayer: PrayerType): Int = when (prayer) {
        PrayerType.FAJR -> 20
        PrayerType.SUNRISE -> 0
        PrayerType.DHUHR -> 15
        PrayerType.ASR -> 15
        PrayerType.MAGHRIB -> 7
        PrayerType.ISHA -> 15
    }

    fun buildParameters(
        method: PrayerCalcMethod,
        madhab: AsrMadhab,
        offsets: PrayerManualOffsets,
        highLatitudeRule: HighLatitudeRuleSetting = HighLatitudeRuleSetting.MIDDLE_OF_THE_NIGHT
    ): CalculationParameters {
        val params = when (method) {
            PrayerCalcMethod.SYRIA_MAWAQIT -> CalculationMethod.OTHER.parameters.apply {
                fajrAngle = 18.5
                ishaAngle = 18.0
            }
            PrayerCalcMethod.MUSLIM_WORLD_LEAGUE -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
            PrayerCalcMethod.UMM_AL_QURA -> CalculationMethod.UMM_AL_QURA.parameters
            PrayerCalcMethod.EGYPTIAN -> CalculationMethod.EGYPTIAN.parameters
            PrayerCalcMethod.KUWAIT -> CalculationMethod.KUWAIT.parameters
            PrayerCalcMethod.QATAR -> CalculationMethod.QATAR.parameters
            PrayerCalcMethod.KARACHI -> CalculationMethod.KARACHI.parameters
            PrayerCalcMethod.NORTH_AMERICA -> CalculationMethod.NORTH_AMERICA.parameters
            PrayerCalcMethod.DUBAI -> CalculationMethod.DUBAI.parameters
            PrayerCalcMethod.SINGAPORE -> CalculationMethod.SINGAPORE.parameters
        }
        params.madhab = when (madhab) {
            AsrMadhab.SHAFI -> Madhab.SHAFI
            AsrMadhab.HANAFI -> Madhab.HANAFI
        }
        params.highLatitudeRule = when (highLatitudeRule) {
            HighLatitudeRuleSetting.MIDDLE_OF_THE_NIGHT -> HighLatitudeRule.MIDDLE_OF_THE_NIGHT
            HighLatitudeRuleSetting.SEVENTH_OF_THE_NIGHT -> HighLatitudeRule.SEVENTH_OF_THE_NIGHT
            HighLatitudeRuleSetting.TWILIGHT_ANGLE -> HighLatitudeRule.TWILIGHT_ANGLE
        }
        val baseDhuhrPrecaution = if (method == PrayerCalcMethod.SYRIA_MAWAQIT) 1 else 0
        val baseAsrPrecaution = if (method == PrayerCalcMethod.SYRIA_MAWAQIT) 1 else 0
        val baseMaghribPrecaution = if (method == PrayerCalcMethod.SYRIA_MAWAQIT) 2 else 0

        params.adjustments.fajr = offsets.fajr.coerceIn(-60, 60)
        params.adjustments.sunrise = offsets.sunrise.coerceIn(-60, 60)
        params.adjustments.dhuhr = baseDhuhrPrecaution + offsets.dhuhr.coerceIn(-60, 60)
        params.adjustments.asr = baseAsrPrecaution + offsets.asr.coerceIn(-60, 60)
        params.adjustments.maghrib = baseMaghribPrecaution + offsets.maghrib.coerceIn(-60, 60)
        params.adjustments.isha = offsets.isha.coerceIn(-60, 60)
        return params
    }

    fun resolveZoneId(timeZoneId: String): ZoneId {
        // Syria (including Idlib, Aleppo, Damascus) adopted permanent UTC+3 year-round in Oct 2022.
        // Using UTC+03:00 prevents older Android tzdata tables from shifting Syrian winter times by -1 hour.
        if (timeZoneId == "Asia/Damascus") {
            return ZoneId.of("UTC+03:00")
        }
        return try {
            ZoneId.of(timeZoneId)
        } catch (e: Exception) {
            ZoneId.systemDefault()
        }
    }

    fun calculateForDate(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        timeZoneId: String,
        method: PrayerCalcMethod,
        madhab: AsrMadhab,
        offsets: PrayerManualOffsets,
        highLatitudeRule: HighLatitudeRuleSetting = HighLatitudeRuleSetting.MIDDLE_OF_THE_NIGHT
    ): DailyPrayerSchedule {
        val zoneId = resolveZoneId(timeZoneId)
        val safeLat = if (latitude.isNaN() || latitude.isInfinite()) 35.9306 else latitude.coerceIn(-89.9, 89.9)
        val safeLon = if (longitude.isNaN() || longitude.isInfinite()) 36.6339 else longitude.coerceIn(-180.0, 180.0)
        val coordinates = Coordinates(safeLat, safeLon)
        val dateComponents = DateComponents(date.year, date.monthValue, date.dayOfMonth)
        val params = buildParameters(method, madhab, offsets, highLatitudeRule)
        val prayerTimes = PrayerTimes(coordinates, dateComponents, params)

        fun toZoned(javaDate: java.util.Date): ZonedDateTime =
            Instant.ofEpochMilli(javaDate.time).atZone(zoneId)

        return DailyPrayerSchedule(
            date = date,
            fajr = toZoned(prayerTimes.fajr),
            sunrise = toZoned(prayerTimes.sunrise),
            dhuhr = toZoned(prayerTimes.dhuhr),
            asr = toZoned(prayerTimes.asr),
            maghrib = toZoned(prayerTimes.maghrib),
            isha = toZoned(prayerTimes.isha)
        )
    }

    fun calculateForSettings(date: LocalDate, settings: UserSettings): DailyPrayerSchedule {
        return calculateForDate(
            date = date,
            latitude = settings.latitude,
            longitude = settings.longitude,
            timeZoneId = settings.timeZoneId,
            method = settings.calcMethod,
            madhab = settings.asrMadhab,
            offsets = settings.manualOffsets,
            highLatitudeRule = settings.highLatitudeRule
        )
    }

    /**
     * Calculates verified Sunnah times for a given date:
     * - Duha Start: 15 minutes after Sunrise (when the sun rises a spear's height)
     * - Duha End: 10 minutes before Dhuhr (prior to Zawal)
     * - Islamic Midnight: Exact midpoint between today's Maghrib and tomorrow's Fajr
     * - Last Third of the Night: Maghrib + 2/3 of the night duration until tomorrow's Fajr
     */
    fun calculateSunnahTimes(date: LocalDate, settings: UserSettings): SunnahTimesInfo {
        val todaySchedule = calculateForSettings(date, settings)
        val tomorrowSchedule = calculateForSettings(date.plusDays(1), settings)
        val rawNightSeconds = ChronoUnit.SECONDS.between(todaySchedule.maghrib, tomorrowSchedule.fajr)
        val nightSeconds = rawNightSeconds.coerceAtLeast(0L)
        val rawDuhaStart = todaySchedule.sunrise.plusMinutes(15)
        val rawDuhaEnd = todaySchedule.dhuhr.minusMinutes(10)
        val isDuhaValid = rawDuhaEnd.isAfter(rawDuhaStart)
        val safeDuhaEnd = if (isDuhaValid) rawDuhaEnd else rawDuhaStart
        return SunnahTimesInfo(
            duhaStart = rawDuhaStart,
            duhaEnd = safeDuhaEnd,
            islamicMidnight = todaySchedule.maghrib.plusSeconds(nightSeconds / 2L),
            lastThirdOfNightStart = todaySchedule.maghrib.plusSeconds((nightSeconds * 2L) / 3L),
            isDuhaValid = isDuhaValid,
            isNightValid = rawNightSeconds > 60L
        )
    }

    /**
     * Computes the next upcoming prayer accurately across midnight boundaries.
     * If all prayers today (including Isha) have passed, returns tomorrow's Fajr.
     */
    fun getNextPrayer(now: ZonedDateTime, settings: UserSettings): NextPrayerInfo {
        val zoneId = resolveZoneId(settings.timeZoneId)
        val zonedNow = now.withZoneSameInstant(zoneId)
        val today = zonedNow.toLocalDate()
        val todaySchedule = calculateForSettings(today, settings)

        for ((prayerType, time) in todaySchedule.allOrdered()) {
            if (time.isAfter(zonedNow)) {
                val seconds = ChronoUnit.SECONDS.between(zonedNow, time).coerceAtLeast(0)
                return NextPrayerInfo(
                    prayerType = prayerType,
                    prayerTime = time,
                    remainingSeconds = seconds,
                    isTomorrowFajr = false
                )
            }
        }

        // Past Isha -> calculate tomorrow's Fajr
        val tomorrow = today.plusDays(1)
        val tomorrowSchedule = calculateForSettings(tomorrow, settings)
        val fajrTime = tomorrowSchedule.fajr
        val seconds = ChronoUnit.SECONDS.between(zonedNow, fajrTime).coerceAtLeast(0)
        return NextPrayerInfo(
            prayerType = PrayerType.FAJR,
            prayerTime = fajrTime,
            remainingSeconds = seconds,
            isTomorrowFajr = true
        )
    }

    /**
     * Generates a monthly prayer timetable for a given year and month.
     */
    fun getMonthlySchedule(year: Int, month: Int, settings: UserSettings): List<DailyPrayerSchedule> {
        val start = LocalDate.of(year, month, 1)
        val daysInMonth = start.lengthOfMonth()
        return (0 until daysInMonth).map { dayOffset ->
            calculateForSettings(start.plusDays(dayOffset.toLong()), settings)
        }
    }

    fun formatTime(time: ZonedDateTime, useArabicIndic: Boolean): String {
        val formatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.forLanguageTag("ar"))
        val formatted = time.format(formatter)
        return NumeralFormatter.format(formatted, useArabicIndic)
    }

    fun formatTime24(time: ZonedDateTime, useArabicIndic: Boolean): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        val formatted = time.format(formatter)
        return NumeralFormatter.format(formatted, useArabicIndic)
    }

    fun formatCountdown(totalSeconds: Long, useArabicIndic: Boolean): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val seconds = safe % 60
        val raw = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        return NumeralFormatter.format(raw, useArabicIndic)
    }

    /**
     * Parses an official timetable CSV or JSON/lines provided by the user to compare with calculated times.
     * Expected format per line (CSV or whitespace/comma separated):
     * YYYY-MM-DD,FAJR,DHUHR,ASR,MAGHRIB,ISHA (e.g. 2026-10-24,04:55,12:05,15:21,17:48,19:18)
     */
    fun compareWithOfficialTable(
        rawTableText: String,
        settings: UserSettings
    ): List<OfficialComparisonRow> {
        val results = mutableListOf<OfficialComparisonRow>()
        val lines = NumeralFormatter.toWestern(rawTableText)
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("date", ignoreCase = true) }

        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        for (line in lines) {
            val parts = line.split(Regex("[,;\\t|]+")).map { it.trim() }
            if (parts.size < 6) continue
            val date = try {
                LocalDate.parse(parts[0])
            } catch (e: Exception) {
                continue
            }
            val schedule = calculateForSettings(date, settings)
            // Either 6 columns (date, fajr, dhuhr, asr, maghrib, isha) or 7 columns (including sunrise)
            val mapping: List<Pair<PrayerType, String>> = if (parts.size >= 7) {
                listOf(
                    PrayerType.FAJR to parts[1],
                    PrayerType.SUNRISE to parts[2],
                    PrayerType.DHUHR to parts[3],
                    PrayerType.ASR to parts[4],
                    PrayerType.MAGHRIB to parts[5],
                    PrayerType.ISHA to parts[6]
                )
            } else {
                listOf(
                    PrayerType.FAJR to parts[1],
                    PrayerType.DHUHR to parts[2],
                    PrayerType.ASR to parts[3],
                    PrayerType.MAGHRIB to parts[4],
                    PrayerType.ISHA to parts[5]
                )
            }

            for ((prayer, officialStr) in mapping) {
                try {
                    val cleanTime = officialStr.take(5)
                    val officialLocalTime = LocalTime.parse(cleanTime, timeFormatter)
                    val calcZoned = schedule.getTimeFor(prayer)
                    val calcLocalTime = calcZoned.toLocalTime()
                    val diffMinutes = ChronoUnit.MINUTES.between(officialLocalTime, calcLocalTime)
                    results.add(
                        OfficialComparisonRow(
                            date = date.toString(),
                            prayerType = prayer,
                            officialTime = cleanTime,
                            calculatedTime = calcLocalTime.format(timeFormatter),
                            differenceMinutes = diffMinutes
                        )
                    )
                } catch (_: Exception) {
                }
            }
        }
        return results
    }
}
