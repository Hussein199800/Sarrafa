package com.example.domain.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit

data class FormattedHijriDate(
    val day: Int,
    val monthNumber: Int,
    val monthNameAr: String,
    val year: Int,
    val fullFormattedAr: String
)

data class IslamicEventItem(
    val id: String,
    val titleAr: String,
    val subtitleAr: String,
    val hijriTargetLabel: String,
    val targetGregorianDate: LocalDate,
    val daysRemaining: Long
)

object HijriCalendarCalculator {

    private val HIJRI_MONTHS_AR = arrayOf(
        "محرّم",
        "صفر",
        "ربيع الأول",
        "ربيع الآخر",
        "جمادى الأولى",
        "جمادى الآخرة",
        "رجب",
        "شعبان",
        "رمضان",
        "شوّال",
        "ذو القعدة",
        "ذو الحجة"
    )

    fun getMonthNameAr(month: Int): String {
        return HIJRI_MONTHS_AR.getOrElse((month - 1).coerceIn(0, 11)) { "" }
    }

    /**
     * Converts a Gregorian LocalDate to Umm al-Qura HijrahDate with user moon-sighting offset (-2..+2 days).
     */
    fun toAdjustedHijriDate(gregorianDate: LocalDate, offsetDays: Int): HijrahDate {
        val clampedOffset = offsetDays.coerceIn(-2, 2).toLong()
        val adjustedGregorian = gregorianDate.plusDays(clampedOffset)
        return HijrahChronology.INSTANCE.date(adjustedGregorian)
    }

    fun formatHijriDate(
        gregorianDate: LocalDate,
        offsetDays: Int,
        useArabicIndic: Boolean = true
    ): FormattedHijriDate {
        val hijri = toAdjustedHijriDate(gregorianDate, offsetDays)
        val day = hijri.get(ChronoField.DAY_OF_MONTH)
        val month = hijri.get(ChronoField.MONTH_OF_YEAR)
        val year = hijri.get(ChronoField.YEAR)
        val monthName = getMonthNameAr(month)

        val dayStr = NumeralFormatter.format(day, useArabicIndic)
        val yearStr = NumeralFormatter.format(year, useArabicIndic)
        val full = "$dayStr $monthName $yearStr هـ"

        return FormattedHijriDate(
            day = day,
            monthNumber = month,
            monthNameAr = monthName,
            year = year,
            fullFormattedAr = full
        )
    }

    /**
     * Finds the Gregorian LocalDate corresponding to a target Hijri (year, month, day),
     * accounting for the user's hijriOffsetDays.
     */
    fun toGregorianDate(hijriYear: Int, hijriMonth: Int, hijriDay: Int, offsetDays: Int): LocalDate {
        val clampedOffset = offsetDays.coerceIn(-2, 2).toLong()
        val hijriDate = try {
            HijrahChronology.INSTANCE.date(hijriYear, hijriMonth, hijriDay)
        } catch (e: Exception) {
            // If a month has 29 days in Umm al-Qura and day 30 was requested, fallback to 29
            HijrahChronology.INSTANCE.date(hijriYear, hijriMonth, 29)
        }
        return LocalDate.from(hijriDate).minusDays(clampedOffset)
    }

    /**
     * Calculates the strictly approved list of Important Islamic Days:
     * 1. رمضان (1 Ramadan)
     * 2. عيد الفطر (1 Shawwal)
     * 3. ست من شوال (2 Shawwal - 29 Shawwal)
     * 4. العشر الأوائل من ذي الحجة (1 Dhu al-Hijjah)
     * 5. يوم عرفة (9 Dhu al-Hijjah)
     * 6. عيد الأضحى (10 Dhu al-Hijjah)
     * 7. رأس السنة الهجرية (1 Muharram)
     * 8. تاسوعاء وعاشوراء (9 & 10 Muharram)
     * 9. الأيام البيض (13, 14, 15 of current/next Hijri month)
     * 10. صيام الاثنين والخميس (Next Monday or Thursday)
     *
     * Strictly excludes Isra & Mi'raj, Mid-Sha'ban, and Mawlid an-Nabawi.
     */
    fun getUpcomingEvents(today: LocalDate, hijriOffsetDays: Int): List<IslamicEventItem> {
        val currentHijri = toAdjustedHijriDate(today, hijriOffsetDays)
        val hYear = currentHijri.get(ChronoField.YEAR)
        val hMonth = currentHijri.get(ChronoField.MONTH_OF_YEAR)
        val hDay = currentHijri.get(ChronoField.DAY_OF_MONTH)

        val events = mutableListOf<IslamicEventItem>()

        fun addAnnualHijriEvent(
            id: String,
            titleAr: String,
            subtitleAr: String,
            targetMonth: Int,
            targetDay: Int
        ) {
            var candidateGregorian = toGregorianDate(hYear, targetMonth, targetDay, hijriOffsetDays)
            var candidateYear = hYear
            if (candidateGregorian.isBefore(today)) {
                candidateYear = hYear + 1
                candidateGregorian = toGregorianDate(candidateYear, targetMonth, targetDay, hijriOffsetDays)
            }
            val daysRem = ChronoUnit.DAYS.between(today, candidateGregorian)
            val monthName = getMonthNameAr(targetMonth)
            events.add(
                IslamicEventItem(
                    id = id,
                    titleAr = titleAr,
                    subtitleAr = subtitleAr,
                    hijriTargetLabel = "$targetDay $monthName $candidateYear هـ",
                    targetGregorianDate = candidateGregorian,
                    daysRemaining = daysRem
                )
            )
        }

        // 1. Ramadan (1/9)
        addAnnualHijriEvent(
            id = "ramadan",
            titleAr = "شهر رمضان المبارك",
            subtitleAr = "شهر الصيام والقرآن والقيام",
            targetMonth = 9,
            targetDay = 1
        )

        // 2. Eid al-Fitr (1/10)
        addAnnualHijriEvent(
            id = "eid_fitr",
            titleAr = "عيد الفطر السعيد",
            subtitleAr = "أول أيام شهر شوال (يحرم صيام يوم العيد)",
            targetMonth = 10,
            targetDay = 1
        )

        // 3. Six days of Shawwal (starts 2/10)
        addAnnualHijriEvent(
            id = "six_shawwal",
            titleAr = "صيام ست من شوال",
            subtitleAr = "«مَنْ صَامَ رَمَضَانَ ثُمَّ أَتْبَعَهُ سِتًّا مِنْ شَوَّالٍ كَانَ كَصِيَامِ الدَّهْرِ» [مسلم: 1164]",
            targetMonth = 10,
            targetDay = 2
        )

        // 4. First 10 days of Dhu al-Hijjah (1/12)
        addAnnualHijriEvent(
            id = "ten_dhul_hijjah",
            titleAr = "العشر الأوائل من ذي الحجة",
            subtitleAr = "«مَا مِنْ أَيَّامٍ الْعَمَلُ الصَّالِحُ فِيهِنَّ أَحَبُّ إِلَى اللَّهِ مِنْ هَذِهِ الْأَيَّامِ الْعَشْرِ» [البخاري: 969]",
            targetMonth = 12,
            targetDay = 1
        )

        // 5. Day of Arafah (9/12)
        addAnnualHijriEvent(
            id = "arafah",
            titleAr = "يوم عرفة",
            subtitleAr = "صيامه يكفّر السنة الماضية والباقية لغير الحاج [مسلم: 1162]",
            targetMonth = 12,
            targetDay = 9
        )

        // 6. Eid al-Adha (10/12)
        addAnnualHijriEvent(
            id = "eid_adha",
            titleAr = "عيد الأضحى المبارك",
            subtitleAr = "يوم النحر وأعظم الأيام عند الله (يحرم صيامه وأيام التشريق)",
            targetMonth = 12,
            targetDay = 10
        )

        // 7. Islamic New Year (1/1)
        addAnnualHijriEvent(
            id = "hijri_new_year",
            titleAr = "رأس السنة الهجرية",
            subtitleAr = "غرة شهر الله المحرّم",
            targetMonth = 1,
            targetDay = 1
        )

        // 8. Tasu'a & Ashura (9 & 10 Muharram)
        addAnnualHijriEvent(
            id = "tasua",
            titleAr = "تاسوعاء (٩ محرّم)",
            subtitleAr = "يُستحب صيامه مع عاشوراء: «لَئِنْ بَقِيتُ إِلَى قَابِلٍ لَأَصُومَنَّ التَّاسِعَ» [مسلم: 1134]",
            targetMonth = 1,
            targetDay = 9
        )
        addAnnualHijriEvent(
            id = "ashura",
            titleAr = "يوم عاشوراء (١٠ محرّم)",
            subtitleAr = "صيامه يكفّر السنة التي قبله [مسلم: 1162]",
            targetMonth = 1,
            targetDay = 10
        )

        // 9. Al-Ayyam Al-Beed (13, 14, 15 of Hijri month; note: 13 Dhu al-Hijjah is Tashreeq so fasting starts 14 Dhu al-Hijjah)
        val firstAllowedWhiteDay = if (hMonth == 12) 14 else 13
        val whiteDayTarget = when {
            hDay <= firstAllowedWhiteDay -> Triple(hYear, hMonth, firstAllowedWhiteDay)
            hDay == 14 -> Triple(hYear, hMonth, 14)
            hDay == 15 -> Triple(hYear, hMonth, 15)
            else -> {
                val nextM = if (hMonth == 12) 1 else hMonth + 1
                val nextY = if (hMonth == 12) hYear + 1 else hYear
                val nextFirstWhite = if (nextM == 12) 14 else 13
                Triple(nextY, nextM, nextFirstWhite)
            }
        }
        val whiteGreg = toGregorianDate(whiteDayTarget.first, whiteDayTarget.second, whiteDayTarget.third, hijriOffsetDays)
        val whiteDaysRem = ChronoUnit.DAYS.between(today, whiteGreg).coerceAtLeast(0)
        val whiteSubtitle = if (whiteDayTarget.second == 12) {
            "وصية النبي ﷺ بصيام ثلاثة أيام من كل شهر [البخاري: 1975] (يُستثنى ١٣ ذو الحجة لأنه من أيام التشريق)"
        } else {
            "وصية النبي ﷺ بصيام ثلاثة أيام من كل شهر [البخاري: 1975]"
        }
        events.add(
            IslamicEventItem(
                id = "white_days",
                titleAr = "الأيام البيض (١٣ و١٤ و١٥)",
                subtitleAr = whiteSubtitle,
                hijriTargetLabel = "${whiteDayTarget.third} ${getMonthNameAr(whiteDayTarget.second)} ${whiteDayTarget.first} هـ",
                targetGregorianDate = whiteGreg,
                daysRemaining = whiteDaysRem
            )
        )

        // 10. Fasting Monday & Thursday (skipping prohibited Eid & Tashreeq days: 1 Shawwal and 10..13 Dhu al-Hijjah)
        var nextMonThu = today
        while (true) {
            val isMonOrThu = nextMonThu.dayOfWeek == DayOfWeek.MONDAY || nextMonThu.dayOfWeek == DayOfWeek.THURSDAY
            if (isMonOrThu) {
                val candidateHijri = toAdjustedHijriDate(nextMonThu, hijriOffsetDays)
                val cMonth = candidateHijri.get(ChronoField.MONTH_OF_YEAR)
                val cDay = candidateHijri.get(ChronoField.DAY_OF_MONTH)
                val isProhibitedFastingDay = (cMonth == 10 && cDay == 1) || (cMonth == 12 && cDay in 10..13)
                if (!isProhibitedFastingDay) break
            }
            nextMonThu = nextMonThu.plusDays(1)
        }
        val monThuHijri = formatHijriDate(nextMonThu, hijriOffsetDays, useArabicIndic = true)
        val dayNameAr = if (nextMonThu.dayOfWeek == DayOfWeek.MONDAY) "الاثنين" else "الخميس"
        events.add(
            IslamicEventItem(
                id = "monday_thursday",
                titleAr = "صيام الاثنين والخميس ($dayNameAr القادم)",
                subtitleAr = "«تُعْرَضُ الْأَعْمَالُ يَوْمَ الِاثْنَيْنِ وَالْخَمِيسِ فَأُحِبُّ أَنْ يُعْرَضَ عَمَلِي وَأَنَا صَائِمٌ» [الترمذي: 747]",
                hijriTargetLabel = monThuHijri.fullFormattedAr,
                targetGregorianDate = nextMonThu,
                daysRemaining = ChronoUnit.DAYS.between(today, nextMonThu)
            )
        )

        return events.sortedBy { it.daysRemaining }
    }
}
