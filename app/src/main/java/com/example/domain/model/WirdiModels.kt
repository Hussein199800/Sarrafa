package com.example.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
enum class DhikrCategory(
    val titleAr: String,
    val subtitleAr: String,
    val isStandaloneGate: Boolean = false
) {
    // 1. أذكار اليوم والمناسبات
    MORNING("أذكار الصباح", "حصن الصباح وطمأنينة اليوم", false),
    EVENING("أذكار المساء", "سكينة المساء وحفظ الليلة", false),
    AFTER_PRAYER("أذكار بعد الصلاة", "الأذكار المشروعة دبر الصلوات", false),
    SLEEP("أذكار النوم", "أذكار ختام اليوم قبل المنام", false),
    WAKEUP("أذكار الاستيقاظ", "الحمد والذكر عند الانتباه", false),
    ATHAN_ABLUTION("أذكار الأذان والوضوء", "إجابة المؤذن وذكر الطهارة", false),
    MOSQUE_HOME("أذكار المسجد والمنزل", "أذكار الدخول والخروج", false),
    FOOD_TRAVEL("أذكار الطعام والسفر", "التسمية والحمد ودعاء الركوب", false),
    FAVORITES("المفضلة", "أذكارك المختارة للوصول السريع", false),
    CUSTOM("أذكاري", "وردك الخاص الذي أضفته بنفسك", false),

    // 2. أبواب الذكر المستقلة (كل بوابة لحالها بصيغ مختصرة مركّزة)
    GATE_ISTIGHFAR("بوابة الاستغفار", "صيغ الاستغفار والتوبة النبوية", true),
    GATE_HAMD("بوابة الحمد والثناء", "جوامع الحمد والشكر لله تعالى", true),
    GATE_HAWQALA("بوابة الحوقلة والفرج", "كنز الجنة وأدعية الكرب والتوكل", true),
    GATE_TASBIH("بوابة التسبيح والباقيات", "الباقيات الصالحات والتهليل", true),
    GATE_SALAWAT("الصلاة على النبي ﷺ", "صيغ الصلاة والسلام على رسول الله ﷺ", true);

    companion object {
        val dailyAndOccasionCategories: List<DhikrCategory>
            get() = entries.filter { !it.isStandaloneGate }

        val standaloneGateCategories: List<DhikrCategory>
            get() = entries.filter { it.isStandaloneGate }
    }
}

@Serializable
enum class HomeSectionId(val titleAr: String, val subtitleAr: String) {
    SHORTCUTS("اختصارات القبلة والمسبحة", "وصول سريع للقبلة والمسبحة وأبواب الذكر"),
    PINNED_GATES("أبواب الذكر المثبتة", "البوابات المختارة للوصول المباشر من الرئيسية"),
    DAILY_HIGHLIGHT("ذكر اليوم", "بطاقة يومية هادئة مع التخريج والنسخ"),
    DEDICATION("ركن الدعاء والإهداء", "سطر دعاء هادئ في ختام الصفحة")
}

@Serializable
data class DhikrItem(
    val id: String,
    val category: String,
    val text: String,
    val repeat: Int,
    val virtue: String? = null,
    val source: String? = null,
    val grade: String? = null,
    val note: String? = null,
    val isCustom: Boolean = false
)

@Serializable
data class HadithItem(
    val id: String,
    val number: Int,
    val text: String,
    val narrator: String,
    val source: String? = null,
    val grade: String? = null
)

@Serializable
data class AsmaItem(
    val id: Int,
    val name: String,
    val meaning: String,
    val listReference: String,
    val needsHumanReview: Boolean = false
)

@Serializable
data class DuaItem(
    val id: String,
    val text: String,
    val source: String? = null,
    val grade: String? = null,
    val category: String // QURANIC or PROPHETIC
)

@Serializable
data class FridaySunnahItem(
    val id: String,
    val title: String,
    val evidence: String,
    val source: String? = null,
    val grade: String? = null
)

@Serializable
data class DuasAndFridayBundle(
    val quranicDuas: List<DuaItem>,
    val propheticDuas: List<DuaItem>,
    val fridaySunan: List<FridaySunnahItem>
)

@Serializable
data class CityLocation(
    val id: String,
    val nameAr: String,
    val countryAr: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
    val defaultMethod: String
)

@Serializable
enum class PrayerCalcMethod(val titleAr: String, val descAr: String) {
    SYRIA_MAWAQIT("توقيت مساجد سوريا وإدلب (مواقيت / الأوقاف)", "الفجر 18.5°، العشاء 18° مع ضبط مساجد الشام"),
    MUSLIM_WORLD_LEAGUE("رابطة العالم الإسلامي", "الفجر 18°، العشاء 17°"),
    UMM_AL_QURA("أم القرى (مكة المكرمة)", "الفجر 18.5°، العشاء 90 دقيقة (120 في رمضان)"),
    EGYPTIAN("الهيئة المصرية العامة للمساحة", "الفجر 19.5°، العشاء 17.5°"),
    KUWAIT("الكويت", "الفجر 18°، العشاء 17.5°"),
    QATAR("قطر", "الفجر 18°، العشاء 90 دقيقة"),
    KARACHI("جامعة العلوم الإسلامية بكراتشي", "الفجر 18°، العشاء 18°"),
    NORTH_AMERICA("أمريكا الشمالية (ISNA)", "الفجر 15°، العشاء 15°"),
    DUBAI("دبي والإمارات", "الفجر 18.2°، العشاء 18.2°"),
    SINGAPORE("سنغافورة وماليزيا", "الفجر 20°، العشاء 18°")
}

@Serializable
enum class AsrMadhab(val titleAr: String) {
    SHAFI("الجمهور (الشافعي، المالكي، الحنبلي)"),
    HANAFI("المذهب الحنفي")
}

@Serializable
enum class HighLatitudeRuleSetting(val titleAr: String, val descAr: String) {
    MIDDLE_OF_THE_NIGHT("منتصف الليل (الافتراضي)", "لا يتجاوز الفجر والعشاء نصف الليل — مناسب لمعظم العروض"),
    SEVENTH_OF_THE_NIGHT("سُبع الليل", "يُقدّر الفجر والعشاء بسُبع الليل في العروض العليا"),
    TWILIGHT_ANGLE("نسبة زاوية الشفق", "تقسيم الليل بنسبة زاوية الفجر والعشاء (أدق لشمال أوروبا)")
}

object WirdiThemePaletteSerializer : KSerializer<WirdiThemePalette> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("WirdiThemePalette", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: WirdiThemePalette) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): WirdiThemePalette {
        val raw = runCatching { decoder.decodeString().uppercase() }.getOrDefault("UFUQ")
        return WirdiThemePalette.fromRawKey(raw)
    }
}

@Serializable(with = WirdiThemePaletteSerializer::class)
enum class WirdiThemePalette(
    val titleAr: String,
    val subtitleAr: String,
    val prefersLightByDefault: Boolean = false
) {
    UFUQ(
        titleAr = "أُفق (الافتراضية)",
        subtitleAr = "أزرق رمادي هادئ مع لمسات بنفسجي دخاني",
        prefersLightByDefault = false
    ),
    SADEEM(
        titleAr = "سديم",
        subtitleAr = "كحلي بنفسجي داكن وأسطح عنابية رمادية وليلكي مطفأ",
        prefersLightByDefault = false
    ),
    WAHA_LAYLIYA(
        titleAr = "واحة ليلية",
        subtitleAr = "درجات فحمية مائلة للأزرق مع أزرق ضبابي ووردي ترابي",
        prefersLightByDefault = false
    ),
    DIF(
        titleAr = "دِفء",
        subtitleAr = "خلفية بنية فحمية دافئة وأسطح كاكاو مطفأة ورملي هادئ",
        prefersLightByDefault = false
    ),
    HAJAR(
        titleAr = "حجر (نهارية دافئة)",
        subtitleAr = "أسطح حجرية دافئة غير بيضاء وأزرق صخري هادئ",
        prefersLightByDefault = true
    );

    companion object {
        // Legacy aliases for safe migration and backwards compatibility
        val FAJR: WirdiThemePalette get() = UFUQ
        val LAYL: WirdiThemePalette get() = SADEEM
        val SAHAB: WirdiThemePalette get() = WAHA_LAYLIYA
        val RAML: WirdiThemePalette get() = DIF
        val GHUROOB: WirdiThemePalette get() = HAJAR

        fun fromRawKey(raw: String): WirdiThemePalette = when (raw.trim().uppercase()) {
            "UFUQ", "FAJR" -> UFUQ
            "SADEEM", "LAYL" -> SADEEM
            "WAHA_LAYLIYA", "SAHAB", "YAQOUT" -> WAHA_LAYLIYA
            "DIF", "RAML", "SAHRA" -> DIF
            "HAJAR", "GHUROOB", "BARADA" -> HAJAR
            else -> UFUQ
        }

        fun fromStoredName(raw: String): WirdiThemePalette = fromRawKey(raw)
    }
}

@Serializable
enum class DarkModeConfig(val titleAr: String) {
    SYSTEM("تلقائي حسب النظام"),
    LIGHT("نهاري دائماً"),
    DARK("داكن دائماً")
}

@Serializable
enum class ReadingFontFamily(val titleAr: String) {
    SYSTEM("خط النظام"),
    AMIRI("خط أميري (Amiri)"),
    NOTO_NASKH("خط نوتو نسخ (Noto Naskh Arabic)")
}

@Serializable
enum class PrayerType(val nameAr: String) {
    FAJR("الفجر"),
    SUNRISE("الشروق"),
    DHUHR("الظهر"),
    ASR("العصر"),
    MAGHRIB("المغرب"),
    ISHA("العشاء")
}

@Serializable
data class PrayerManualOffsets(
    val fajr: Int = 0,
    val sunrise: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0
) {
    fun getOffset(prayer: PrayerType): Int = when (prayer) {
        PrayerType.FAJR -> fajr
        PrayerType.SUNRISE -> sunrise
        PrayerType.DHUHR -> dhuhr
        PrayerType.ASR -> asr
        PrayerType.MAGHRIB -> maghrib
        PrayerType.ISHA -> isha
    }

    fun withOffset(prayer: PrayerType, value: Int): PrayerManualOffsets {
        val clamped = value.coerceIn(-30, 30)
        return when (prayer) {
            PrayerType.FAJR -> copy(fajr = clamped)
            PrayerType.SUNRISE -> copy(sunrise = clamped)
            PrayerType.DHUHR -> copy(dhuhr = clamped)
            PrayerType.ASR -> copy(asr = clamped)
            PrayerType.MAGHRIB -> copy(maghrib = clamped)
            PrayerType.ISHA -> copy(isha = clamped)
        }
    }

    fun sanitized(): PrayerManualOffsets = copy(
        fajr = fajr.coerceIn(-30, 30),
        sunrise = sunrise.coerceIn(-30, 30),
        dhuhr = dhuhr.coerceIn(-30, 30),
        asr = asr.coerceIn(-30, 30),
        maghrib = maghrib.coerceIn(-30, 30),
        isha = isha.coerceIn(-30, 30)
    )
}

@Serializable
data class PrayerNotificationConfig(
    val enabled: Boolean = true,
    val minutesBefore: Int = 0 // 0 = at exact prayer time, or 5/10/15 mins before
)

@Serializable
data class UserSettings(
    val cityNameAr: String = "إدلب",
    val countryAr: String = "سوريا",
    val latitude: Double = 35.9306,
    val longitude: Double = 36.6339,
    val timeZoneId: String = "Asia/Damascus",
    val calcMethod: PrayerCalcMethod = PrayerCalcMethod.SYRIA_MAWAQIT,
    val asrMadhab: AsrMadhab = AsrMadhab.SHAFI,
    val highLatitudeRule: HighLatitudeRuleSetting = HighLatitudeRuleSetting.MIDDLE_OF_THE_NIGHT,
    val manualOffsets: PrayerManualOffsets = PrayerManualOffsets(),
    val hijriOffsetDays: Int = 0, // -2 to +2
    // Theme & Display («أُفق» داكنة افتراضياً)
    val themePalette: WirdiThemePalette = WirdiThemePalette.UFUQ,
    val darkModeConfig: DarkModeConfig = DarkModeConfig.DARK,
    val useDynamicColor: Boolean = false,
    // Typography & Numerals
    val readingFont: ReadingFontFamily = ReadingFontFamily.AMIRI,
    val readingFontScale: Float = 1.1f, // 0.85f .. 1.6f
    val useArabicIndicNumerals: Boolean = true,
    // Adhkar, Session & Tasbih behavior
    val hapticFeedbackEnabled: Boolean = true,
    val autoAdvanceDhikr: Boolean = true,
    val tasbihKeepScreenOn: Boolean = false,
    val tasbihTarget: Int = 33, // 0 = open, 33, 99, or custom
    val sessionKeepScreenOn: Boolean = true,
    val sessionDimUi: Boolean = false,
    val sessionCompactCounter: Boolean = false,
    // Home Screen Customization
    val homeCompactMode: Boolean = false,
    val homeVisibleSections: Set<String> = setOf(
        HomeSectionId.SHORTCUTS.name,
        HomeSectionId.PINNED_GATES.name,
        HomeSectionId.DAILY_HIGHLIGHT.name,
        HomeSectionId.DEDICATION.name
    ),
    val homeSectionsOrder: List<String> = listOf(
        HomeSectionId.SHORTCUTS.name,
        HomeSectionId.PINNED_GATES.name,
        HomeSectionId.DAILY_HIGHLIGHT.name,
        HomeSectionId.DEDICATION.name
    ),
    val pinnedGateCategories: List<String> = listOf(
        DhikrCategory.GATE_ISTIGHFAR.name,
        DhikrCategory.GATE_HAMD.name,
        DhikrCategory.GATE_HAWQALA.name,
        DhikrCategory.GATE_TASBIH.name,
        DhikrCategory.GATE_SALAWAT.name
    ),
    // Onboarding & Optional Persistent Notification
    val hasCompletedOnboarding: Boolean = false,
    val persistentPrayerNotificationEnabled: Boolean = false,
    // Notifications
    val prayerNotifications: Map<PrayerType, PrayerNotificationConfig> = mapOf(
        PrayerType.FAJR to PrayerNotificationConfig(true, 0),
        PrayerType.SUNRISE to PrayerNotificationConfig(false, 0),
        PrayerType.DHUHR to PrayerNotificationConfig(true, 0),
        PrayerType.ASR to PrayerNotificationConfig(true, 0),
        PrayerType.MAGHRIB to PrayerNotificationConfig(true, 0),
        PrayerType.ISHA to PrayerNotificationConfig(true, 0)
    ),
    val morningReminderEnabled: Boolean = true,
    val morningReminderHour: Int = 6,
    val morningReminderMinute: Int = 0,
    val eveningReminderEnabled: Boolean = true,
    val eveningReminderHour: Int = 16,
    val eveningReminderMinute: Int = 30,
    val sleepReminderEnabled: Boolean = true,
    val sleepReminderHour: Int = 22,
    val sleepReminderMinute: Int = 0,
    val fridayReminderEnabled: Boolean = true,
    val fridayReminderHour: Int = 9,
    val fridayReminderMinute: Int = 30,
    val snoozeMinutes: Int = 15
) {
    fun normalizedHomeSectionsOrder(): List<HomeSectionId> {
        val valid = homeSectionsOrder.mapNotNull { name ->
            HomeSectionId.entries.firstOrNull { it.name == name }
        }.distinct()
        val missing = HomeSectionId.entries.filterNot { it in valid }
        return valid + missing
    }

    fun resolvedPinnedGates(): List<DhikrCategory> {
        val valid = pinnedGateCategories.mapNotNull { name ->
            DhikrCategory.standaloneGateCategories.firstOrNull { it.name == name }
        }.distinct()
        return if (valid.isEmpty()) DhikrCategory.standaloneGateCategories else valid
    }

    fun sanitized(): UserSettings {
        val safeLat = if (latitude.isNaN() || latitude.isInfinite()) 35.9306 else latitude.coerceIn(-89.9, 89.9)
        val safeLon = if (longitude.isNaN() || longitude.isInfinite()) 36.6339 else longitude.coerceIn(-180.0, 180.0)
        val safeScale = if (readingFontScale.isNaN() || readingFontScale.isInfinite()) 1.1f else readingFontScale.coerceIn(0.85f, 1.6f)
        val defaultNotifs = mapOf(
            PrayerType.FAJR to PrayerNotificationConfig(true, 0),
            PrayerType.SUNRISE to PrayerNotificationConfig(false, 0),
            PrayerType.DHUHR to PrayerNotificationConfig(true, 0),
            PrayerType.ASR to PrayerNotificationConfig(true, 0),
            PrayerType.MAGHRIB to PrayerNotificationConfig(true, 0),
            PrayerType.ISHA to PrayerNotificationConfig(true, 0)
        )
        val mergedNotifs = PrayerType.entries.associateWith { pType ->
            val cfg = prayerNotifications[pType] ?: defaultNotifs.getValue(pType)
            cfg.copy(minutesBefore = cfg.minutesBefore.coerceIn(0, 60))
        }
        val safeVisibleSections = homeVisibleSections
            .filter { name -> HomeSectionId.entries.any { it.name == name } }
            .toSet()
            .ifEmpty { HomeSectionId.entries.map { it.name }.toSet() }
        return copy(
            cityNameAr = cityNameAr.ifBlank { "إدلب" },
            countryAr = countryAr.ifBlank { "سوريا" },
            latitude = safeLat,
            longitude = safeLon,
            timeZoneId = timeZoneId.ifBlank { "Asia/Damascus" },
            manualOffsets = manualOffsets.sanitized(),
            hijriOffsetDays = hijriOffsetDays.coerceIn(-2, 2),
            readingFontScale = safeScale,
            tasbihTarget = tasbihTarget.coerceIn(0, 100_000),
            prayerNotifications = mergedNotifs,
            morningReminderHour = morningReminderHour.coerceIn(0, 23),
            morningReminderMinute = morningReminderMinute.coerceIn(0, 59),
            eveningReminderHour = eveningReminderHour.coerceIn(0, 23),
            eveningReminderMinute = eveningReminderMinute.coerceIn(0, 59),
            sleepReminderHour = sleepReminderHour.coerceIn(0, 23),
            sleepReminderMinute = sleepReminderMinute.coerceIn(0, 59),
            fridayReminderHour = fridayReminderHour.coerceIn(0, 23),
            fridayReminderMinute = fridayReminderMinute.coerceIn(0, 59),
            snoozeMinutes = snoozeMinutes.coerceIn(1, 120),
            homeVisibleSections = safeVisibleSections
        )
    }
}

@Serializable
data class WirdiBackupPayload(
    val version: Int = 2,
    val exportedAtEpochMillis: Long = 0L,
    val settings: UserSettings = UserSettings(),
    val favoriteIds: Set<String> = emptySet(),
    val customAdhkar: List<DhikrItem> = emptyList(),
    val todayDateIso: String = "",
    val todayDhikrCounts: Map<String, Int> = emptyMap(),
    val tasbihCurrentCount: Int = 0,
    val tasbihTodayTotal: Int = 0,
    val lastReadDhikrCategory: String = DhikrCategory.MORNING.name,
    val lastReadDhikrIndexByCategory: Map<String, Int> = emptyMap(),
    val lastReadLibraryItemBySection: Map<String, String> = emptyMap()
)
