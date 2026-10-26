package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.DhikrCategory
import com.example.domain.model.PrayerType
import com.example.domain.model.WirdiThemePalette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Centralized Design Tokens for «سكينة معاصرة» (Contemporary Serenity).
 * Avoids scattered hex literals across screens and enforces 3 distinct surface tiers,
 * subtle borders, and consistent corner radii.
 */
object WirdiDesignTokens {
    val cardCornerRadius = 22.dp
    val heroCornerRadius = 24.dp
    val smallCornerRadius = 14.dp
    val pillCornerPercent = 50
    val hairlineBorderWidth = 0.5.dp
    val screenHorizontalPadding = 20.dp
    val screenHorizontalPaddingTablet = 24.dp
    val sectionVerticalSpacing = 16.dp
    val shortMotionMillis = 200
}

// =========================================================================================
// 1. سمة «أُفق | UFUQ» — الافتراضية (أزرق رمادي هادئ وبنفسجي دخاني بلمسات محدودة)
// الخلفية: #11161D | السطح الأساسي: #182029 | السطح المرتفع: #222C37
// اللون الرئيسي: #93A9C2 | اللون الثانوي: #B2A0C4 | النص الرئيسي: #F0EDE7 | النص الثانوي: #B4BBC3
// =========================================================================================
val UfuqDark = darkColorScheme(
    primary = Color(0xFF93A9C2),
    onPrimary = Color(0xFF11161D),
    primaryContainer = Color(0xFF222C37),
    onPrimaryContainer = Color(0xFFF0EDE7),
    secondary = Color(0xFFB2A0C4),
    onSecondary = Color(0xFF191421),
    secondaryContainer = Color(0xFF282436),
    onSecondaryContainer = Color(0xFFF0EDE7),
    tertiary = Color(0xFFA6B4C4),
    onTertiary = Color(0xFF121820),
    tertiaryContainer = Color(0xFF222C37),
    onTertiaryContainer = Color(0xFFF0EDE7),
    background = Color(0xFF11161D),
    onBackground = Color(0xFFF0EDE7),
    surface = Color(0xFF182029),
    onSurface = Color(0xFFF0EDE7),
    surfaceVariant = Color(0xFF222C37),
    onSurfaceVariant = Color(0xFFB4BBC3),
    outline = Color(0xFF3A4654),
    outlineVariant = Color(0xFF293440),
    error = Color(0xFFE58F8A),
    onError = Color(0xFF2B1110),
    errorContainer = Color(0xFF3D1F1D),
    onErrorContainer = Color(0xFFF7DEDA)
)

val UfuqLight = lightColorScheme(
    primary = Color(0xFF354C63),
    onPrimary = Color(0xFFF2F4F7),
    primaryContainer = Color(0xFFD5DFE9),
    onPrimaryContainer = Color(0xFF16202B),
    secondary = Color(0xFF564569),
    onSecondary = Color(0xFFF4F1F7),
    secondaryContainer = Color(0xFFE1DBE8),
    onSecondaryContainer = Color(0xFF201829),
    tertiary = Color(0xFF425669),
    onTertiary = Color(0xFFF2F4F7),
    tertiaryContainer = Color(0xFFD8E1EA),
    onTertiaryContainer = Color(0xFF18222C),
    background = Color(0xFFE5E9EE),
    onBackground = Color(0xFF182029),
    surface = Color(0xFFEEF1F5),
    onSurface = Color(0xFF182029),
    surfaceVariant = Color(0xFFDAE0E7),
    onSurfaceVariant = Color(0xFF404C59),
    outline = Color(0xFFB0BCC8),
    outlineVariant = Color(0xFFC7D0DA),
    error = Color(0xFF9E3A34),
    onError = Color(0xFFF7F2F2),
    errorContainer = Color(0xFFEAD4D2),
    onErrorContainer = Color(0xFF2E100E)
)

// =========================================================================================
// 2. سمة «سديم | SADEEM» — خلفية كحلية بنفسجية داكنة، أسطح عنابية رمادية، وليلكي مطفأ
// =========================================================================================
val SadeemDark = darkColorScheme(
    primary = Color(0xFFADA2C9),
    onPrimary = Color(0xFF141220),
    primaryContainer = Color(0xFF2A263B),
    onPrimaryContainer = Color(0xFFF0ECF5),
    secondary = Color(0xFFC2A0B8),
    onSecondary = Color(0xFF21131D),
    secondaryContainer = Color(0xFF322430),
    onSecondaryContainer = Color(0xFFF2EAF0),
    tertiary = Color(0xFF9EADC9),
    onTertiary = Color(0xFF121824),
    tertiaryContainer = Color(0xFF242B3B),
    onTertiaryContainer = Color(0xFFECF0F7),
    background = Color(0xFF13121D),
    onBackground = Color(0xFFF0ECF5),
    surface = Color(0xFF1C1A28),
    onSurface = Color(0xFFF0ECF5),
    surfaceVariant = Color(0xFF272436),
    onSurfaceVariant = Color(0xFFB8B2C6),
    outline = Color(0xFF433E57),
    outlineVariant = Color(0xFF312D42),
    error = Color(0xFFE58F8A),
    onError = Color(0xFF2B1110),
    errorContainer = Color(0xFF3D1F1D),
    onErrorContainer = Color(0xFFF7DEDA)
)

val SadeemLight = lightColorScheme(
    primary = Color(0xFF4A3E68),
    onPrimary = Color(0xFFF3F1F7),
    primaryContainer = Color(0xFFDAD5E8),
    onPrimaryContainer = Color(0xFF1C172B),
    secondary = Color(0xFF63455A),
    onSecondary = Color(0xFFF5F0F4),
    secondaryContainer = Color(0xFFE5D8E1),
    onSecondaryContainer = Color(0xFF261722),
    tertiary = Color(0xFF41506B),
    onTertiary = Color(0xFFF2F4F7),
    tertiaryContainer = Color(0xFFD8DFEC),
    onTertiaryContainer = Color(0xFF182030),
    background = Color(0xFFE7E5EE),
    onBackground = Color(0xFF1C1926),
    surface = Color(0xFFEFEFF5),
    onSurface = Color(0xFF1C1926),
    surfaceVariant = Color(0xFFDBD8E6),
    onSurfaceVariant = Color(0xFF494359),
    outline = Color(0xFFB5AFC7),
    outlineVariant = Color(0xFFC9C4D8),
    error = Color(0xFF9E3A34),
    onError = Color(0xFFF7F2F2),
    errorContainer = Color(0xFFEAD4D2),
    onErrorContainer = Color(0xFF2E100E)
)

// =========================================================================================
// 3. سمة «واحة ليلية | WAHA_LAYLIYA» — فحمي مائل للأزرق مع أزرق ضبابي ووردي ترابي خافت
// =========================================================================================
val WahaLayliyaDark = darkColorScheme(
    primary = Color(0xFF8AB0C4),
    onPrimary = Color(0xFF0E171C),
    primaryContainer = Color(0xFF1F323D),
    onPrimaryContainer = Color(0xFFEEF1F2),
    secondary = Color(0xFFC4A39F),
    onSecondary = Color(0xFF211412),
    secondaryContainer = Color(0xFF332625),
    onSecondaryContainer = Color(0xFFF2EAE9),
    tertiary = Color(0xFF9CB4C2),
    onTertiary = Color(0xFF101920),
    tertiaryContainer = Color(0xFF203039),
    onTertiaryContainer = Color(0xFFEEF1F2),
    background = Color(0xFF0F171C),
    onBackground = Color(0xFFEEF1F2),
    surface = Color(0xFF162229),
    onSurface = Color(0xFFEEF1F2),
    surfaceVariant = Color(0xFF203039),
    onSurfaceVariant = Color(0xFFB0BEC4),
    outline = Color(0xFF384E59),
    outlineVariant = Color(0xFF293B45),
    error = Color(0xFFE58F8A),
    onError = Color(0xFF2B1110),
    errorContainer = Color(0xFF3D1F1D),
    onErrorContainer = Color(0xFFF7DEDA)
)

val WahaLayliyaLight = lightColorScheme(
    primary = Color(0xFF2B5263),
    onPrimary = Color(0xFFF0F5F6),
    primaryContainer = Color(0xFFCFE0E5),
    onPrimaryContainer = Color(0xFF122229),
    secondary = Color(0xFF684946),
    onSecondary = Color(0xFFF6F1F0),
    secondaryContainer = Color(0xFFE6D9D7),
    onSecondaryContainer = Color(0xFF261817),
    tertiary = Color(0xFF3A5766),
    onTertiary = Color(0xFFF0F5F6),
    tertiaryContainer = Color(0xFFD5E2E7),
    onTertiaryContainer = Color(0xFF14232B),
    background = Color(0xFFE2EAEB),
    onBackground = Color(0xFF142026),
    surface = Color(0xFFECF1F2),
    onSurface = Color(0xFF142026),
    surfaceVariant = Color(0xFFD5E0E2),
    onSurfaceVariant = Color(0xFF3E4F57),
    outline = Color(0xFFABC0C4),
    outlineVariant = Color(0xFFC1CFD2),
    error = Color(0xFF9E3A34),
    onError = Color(0xFFF7F2F2),
    errorContainer = Color(0xFFEAD4D2),
    onErrorContainer = Color(0xFF2E100E)
)

// =========================================================================================
// 4. سمة «دِفء | DIF» — خلفية بنية فحمية دافئة، أسطح كاكاو مطفأة، ولون رئيسي رملي هادئ
// =========================================================================================
val DifDark = darkColorScheme(
    primary = Color(0xFFC7B097),
    onPrimary = Color(0xFF1C140E),
    primaryContainer = Color(0xFF332923),
    onPrimaryContainer = Color(0xFFF2ECE6),
    secondary = Color(0xFFC29E8E),
    onSecondary = Color(0xFF21140E),
    secondaryContainer = Color(0xFF362822),
    onSecondaryContainer = Color(0xFFF2EAE6),
    tertiary = Color(0xFFB5A89F),
    onTertiary = Color(0xFF1C1612),
    tertiaryContainer = Color(0xFF2D2521),
    onTertiaryContainer = Color(0xFFF2ECE6),
    background = Color(0xFF161210),
    onBackground = Color(0xFFF2ECE6),
    surface = Color(0xFF211B18),
    onSurface = Color(0xFFF2ECE6),
    surfaceVariant = Color(0xFF2D2521),
    onSurfaceVariant = Color(0xFFBEB3A8),
    outline = Color(0xFF4C3F38),
    outlineVariant = Color(0xFF3A302B),
    error = Color(0xFFE58F8A),
    onError = Color(0xFF2B1110),
    errorContainer = Color(0xFF3D1F1D),
    onErrorContainer = Color(0xFFF7DEDA)
)

val DifLight = lightColorScheme(
    primary = Color(0xFF5B4430),
    onPrimary = Color(0xFFF6F1EC),
    primaryContainer = Color(0xFFDECFC0),
    onPrimaryContainer = Color(0xFF221912),
    secondary = Color(0xFF6B4839),
    onSecondary = Color(0xFFF6F0ED),
    secondaryContainer = Color(0xFFE6D6CE),
    onSecondaryContainer = Color(0xFF261711),
    tertiary = Color(0xFF574A40),
    onTertiary = Color(0xFFF5F1ED),
    tertiaryContainer = Color(0xFFDFD6CE),
    onTertiaryContainer = Color(0xFF211B16),
    background = Color(0xFFE9E2DA),
    onBackground = Color(0xFF221B15),
    surface = Color(0xFFF1EBE4),
    onSurface = Color(0xFF221B15),
    surfaceVariant = Color(0xFFDDD3C9),
    onSurfaceVariant = Color(0xFF504237),
    outline = Color(0xFFBDB0A2),
    outlineVariant = Color(0xFFCFC3B6),
    error = Color(0xFF9E3A34),
    onError = Color(0xFFF7F2F2),
    errorContainer = Color(0xFFEAD4D2),
    onErrorContainer = Color(0xFF2E100E)
)

// =========================================================================================
// 5. سمة «حجر | HAJAR» — نهارية غير بيضاء (#E7E3DC خلفية، #EFEBE4 و#DCD8D0 أسطح، #20272D نص، #53697D رئيسي)
// =========================================================================================
val HajarLight = lightColorScheme(
    primary = Color(0xFF475C70),           // Stone Slate (Contrast on #EFEBE4 >= 5.7:1, on #E7E3DC >= 5.3:1)
    onPrimary = Color(0xFFF4F2EE),
    primaryContainer = Color(0xFFD5DDE4),
    onPrimaryContainer = Color(0xFF1A222B),
    secondary = Color(0xFF61536B),         // Muted grey-purple
    onSecondary = Color(0xFFF4F1F6),
    secondaryContainer = Color(0xFFDED9E3),
    onSecondaryContainer = Color(0xFF221B29),
    tertiary = Color(0xFF53697D),
    onTertiary = Color(0xFFF4F2EE),
    tertiaryContainer = Color(0xFFD6DEE6),
    onTertiaryContainer = Color(0xFF1B242E),
    background = Color(0xFFE7E3DC),        // Exact requested background #E7E3DC
    onBackground = Color(0xFF20272D),      // Exact requested text #20272D
    surface = Color(0xFFEFEBE4),           // Exact requested surface #EFEBE4 (NO #FFFFFF)
    onSurface = Color(0xFF20272D),         // Exact requested text #20272D
    surfaceVariant = Color(0xFFDCD8D0),    // Exact requested elevated surface #DCD8D0
    onSurfaceVariant = Color(0xFF464E56),  // Contrast on #DCD8D0 >= 5.5:1
    outline = Color(0xFFB5B0A6),
    outlineVariant = Color(0xFFC9C4BB),
    error = Color(0xFF9E3A34),
    onError = Color(0xFFF7F2F2),
    errorContainer = Color(0xFFEAD4D2),
    onErrorContainer = Color(0xFF2E100E)
)

val HajarDark = darkColorScheme(
    primary = Color(0xFF96ADC2),
    onPrimary = Color(0xFF11161B),
    primaryContainer = Color(0xFF27313B),
    onPrimaryContainer = Color(0xFFECE9E2),
    secondary = Color(0xFFB2A3C2),
    onSecondary = Color(0xFF1A1421),
    secondaryContainer = Color(0xFF2C2636),
    onSecondaryContainer = Color(0xFFECE9E2),
    tertiary = Color(0xFFA2B3C2),
    onTertiary = Color(0xFF12181F),
    tertiaryContainer = Color(0xFF272C33),
    onTertiaryContainer = Color(0xFFECE9E2),
    background = Color(0xFF14171A),
    onBackground = Color(0xFFECE9E2),
    surface = Color(0xFF1D2126),
    onSurface = Color(0xFFECE9E2),
    surfaceVariant = Color(0xFF272C33),
    onSurfaceVariant = Color(0xFFB4B9BF),
    outline = Color(0xFF3E454F),
    outlineVariant = Color(0xFF2F353D),
    error = Color(0xFFE58F8A),
    onError = Color(0xFF2B1110),
    errorContainer = Color(0xFF3D1F1D),
    onErrorContainer = Color(0xFFF7DEDA)
)

fun getPaletteColorScheme(palette: WirdiThemePalette, darkTheme: Boolean): ColorScheme {
    return when (palette) {
        WirdiThemePalette.UFUQ -> if (darkTheme) UfuqDark else UfuqLight
        WirdiThemePalette.SADEEM -> if (darkTheme) SadeemDark else SadeemLight
        WirdiThemePalette.WAHA_LAYLIYA -> if (darkTheme) WahaLayliyaDark else WahaLayliyaLight
        WirdiThemePalette.DIF -> if (darkTheme) DifDark else DifLight
        WirdiThemePalette.HAJAR -> if (darkTheme) HajarDark else HajarLight
    }
}

/**
 * Calm, closely-related gradient specs for the Next Prayer Hero card («بدرجات لونية قريبة وهادئة»).
 * Zero pure #FFFFFF and verified WCAG AA contrast >= 4.5:1.
 */
data class PrayerHeroGradientSpec(
    val startColor: Color,
    val endColor: Color,
    val primaryTextColor: Color,
    val secondaryTextColor: Color,
    val ringTrackColor: Color,
    val ringProgressColor: Color
) {
    val contentColor: Color get() = primaryTextColor
    val secondaryContentColor: Color get() = secondaryTextColor
    val accentRingColor: Color get() = ringProgressColor
}

fun getPrayerHeroGradient(prayer: PrayerType, isDarkTheme: Boolean): PrayerHeroGradientSpec {
    return if (isDarkTheme) {
        when (prayer) {
            PrayerType.FAJR -> PrayerHeroGradientSpec(
                startColor = Color(0xFF182230),
                endColor = Color(0xFF24283B),
                primaryTextColor = Color(0xFFF0EDE7),
                secondaryTextColor = Color(0xFFB8C0CC),
                ringTrackColor = Color(0x3393A9C2),
                ringProgressColor = Color(0xFF9FB4CC)
            )
            PrayerType.SUNRISE -> PrayerHeroGradientSpec(
                startColor = Color(0xFF262124),
                endColor = Color(0xFF302629),
                primaryTextColor = Color(0xFFF2ECE9),
                secondaryTextColor = Color(0xFFC9B9B5),
                ringTrackColor = Color(0x33C7A9A1),
                ringProgressColor = Color(0xFFD6B5AB)
            )
            PrayerType.DHUHR -> PrayerHeroGradientSpec(
                startColor = Color(0xFF17232E),
                endColor = Color(0xFF1F2F3D),
                primaryTextColor = Color(0xFFF0F2F5),
                secondaryTextColor = Color(0xFFB6C4D1),
                ringTrackColor = Color(0x338EB0C9),
                ringProgressColor = Color(0xFF9AC0DB)
            )
            PrayerType.ASR -> PrayerHeroGradientSpec(
                startColor = Color(0xFF241F1C),
                endColor = Color(0xFF2E2722),
                primaryTextColor = Color(0xFFF2ECE6),
                secondaryTextColor = Color(0xFFC4B8AC),
                ringTrackColor = Color(0x33C7B097),
                ringProgressColor = Color(0xFFD1BA9F)
            )
            PrayerType.MAGHRIB -> PrayerHeroGradientSpec(
                startColor = Color(0xFF241C28),
                endColor = Color(0xFF2E2233),
                primaryTextColor = Color(0xFFF2ECF5),
                secondaryTextColor = Color(0xFFC4B6CC),
                ringTrackColor = Color(0x33B2A0C4),
                ringProgressColor = Color(0xFFC4B0D6)
            )
            PrayerType.ISHA -> PrayerHeroGradientSpec(
                startColor = Color(0xFF141A24),
                endColor = Color(0xFF1C2432),
                primaryTextColor = Color(0xFFF0EDE7),
                secondaryTextColor = Color(0xFFB4BBC3),
                ringTrackColor = Color(0x3393A9C2),
                ringProgressColor = Color(0xFFA3B8D1)
            )
        }
    } else {
        when (prayer) {
            PrayerType.FAJR -> PrayerHeroGradientSpec(
                startColor = Color(0xFFDCE3EC),
                endColor = Color(0xFFD3DCE8),
                primaryTextColor = Color(0xFF182029),
                secondaryTextColor = Color(0xFF3E4A59),
                ringTrackColor = Color(0x26182029),
                ringProgressColor = Color(0xFF354C63)
            )
            PrayerType.SUNRISE -> PrayerHeroGradientSpec(
                startColor = Color(0xFFEBE2DC),
                endColor = Color(0xFFE3D7CF),
                primaryTextColor = Color(0xFF241B16),
                secondaryTextColor = Color(0xFF4F4038),
                ringTrackColor = Color(0x26241B16),
                ringProgressColor = Color(0xFF66483B)
            )
            PrayerType.DHUHR -> PrayerHeroGradientSpec(
                startColor = Color(0xFFDDE6ED),
                endColor = Color(0xFFD2DFE8),
                primaryTextColor = Color(0xFF15212B),
                secondaryTextColor = Color(0xFF3B4B59),
                ringTrackColor = Color(0x2615212B),
                ringProgressColor = Color(0xFF2E506B)
            )
            PrayerType.ASR -> PrayerHeroGradientSpec(
                startColor = Color(0xFFE8E0D6),
                endColor = Color(0xFFDFD5C8),
                primaryTextColor = Color(0xFF221B14),
                secondaryTextColor = Color(0xFF4D4034),
                ringTrackColor = Color(0x26221B14),
                ringProgressColor = Color(0xFF5E4732)
            )
            PrayerType.MAGHRIB -> PrayerHeroGradientSpec(
                startColor = Color(0xFFE4DFE8),
                endColor = Color(0xFFDAD3E0),
                primaryTextColor = Color(0xFF1E1826),
                secondaryTextColor = Color(0xFF494054),
                ringTrackColor = Color(0x261E1826),
                ringProgressColor = Color(0xFF564569)
            )
            PrayerType.ISHA -> PrayerHeroGradientSpec(
                startColor = Color(0xFFDCE1E8),
                endColor = Color(0xFFD2D8E2),
                primaryTextColor = Color(0xFF182029),
                secondaryTextColor = Color(0xFF3E4854),
                ringTrackColor = Color(0x26182029),
                ringProgressColor = Color(0xFF395068)
            )
        }
    }
}

/**
 * Unified, serene visual specification for the 5 Standalone Dhikr Gates:
 * «استبدل البطاقات الملونة الكثيرة بواجهة موحدة، ويميز كل باب بلون خافت وأيقونة خطية»
 */
data class DhikrGateVisualSpec(
    val containerStart: Color,
    val containerEnd: Color,
    val accentColor: Color,
    val badgeBgColor: Color,
    val titleColor: Color,
    val subtitleColor: Color
)

fun getDhikrGateVisualSpec(
    category: DhikrCategory,
    isDarkTheme: Boolean
): DhikrGateVisualSpec {
    return if (isDarkTheme) {
        val unifiedSurfaceStart = Color(0xFF182029)
        val unifiedSurfaceEnd = Color(0xFF1D2631)
        val titleCol = Color(0xFFF0EDE7)
        val subCol = Color(0xFFB4BBC3)
        when (category) {
            DhikrCategory.GATE_ISTIGHFAR -> DhikrGateVisualSpec(
                containerStart = unifiedSurfaceStart,
                containerEnd = unifiedSurfaceEnd,
                accentColor = Color(0xFF93A9C2), // Muted Slate Blue
                badgeBgColor = Color(0xFF232F3D),
                titleColor = titleCol,
                subtitleColor = subCol
            )
            DhikrCategory.GATE_HAMD -> DhikrGateVisualSpec(
                containerStart = unifiedSurfaceStart,
                containerEnd = unifiedSurfaceEnd,
                accentColor = Color(0xFFC4A39F), // Muted Earthy Rose
                badgeBgColor = Color(0xFF2B2428),
                titleColor = titleCol,
                subtitleColor = subCol
            )
            DhikrCategory.GATE_HAWQALA -> DhikrGateVisualSpec(
                containerStart = unifiedSurfaceStart,
                containerEnd = unifiedSurfaceEnd,
                accentColor = Color(0xFF8AB0C4), // Misty Horizon Blue
                badgeBgColor = Color(0xFF202F38),
                titleColor = titleCol,
                subtitleColor = subCol
            )
            DhikrCategory.GATE_TASBIH -> DhikrGateVisualSpec(
                containerStart = unifiedSurfaceStart,
                containerEnd = unifiedSurfaceEnd,
                accentColor = Color(0xFFC7B097), // Calm Warm Sand
                badgeBgColor = Color(0xFF2B2622),
                titleColor = titleCol,
                subtitleColor = subCol
            )
            else -> DhikrGateVisualSpec(
                containerStart = unifiedSurfaceStart,
                containerEnd = unifiedSurfaceEnd,
                accentColor = Color(0xFFB2A0C4), // Smoky Lavender
                badgeBgColor = Color(0xFF282436),
                titleColor = titleCol,
                subtitleColor = subCol
            )
        }
    } else {
        val unifiedSurfaceStart = Color(0xFFEFEBE4)
        val unifiedSurfaceEnd = Color(0xFFE7E2D9)
        val titleCol = Color(0xFF20272D)
        val subCol = Color(0xFF464E56)
        when (category) {
            DhikrCategory.GATE_ISTIGHFAR -> DhikrGateVisualSpec(
                containerStart = unifiedSurfaceStart,
                containerEnd = unifiedSurfaceEnd,
                accentColor = Color(0xFF395068),
                badgeBgColor = Color(0xFFD6DFE8),
                titleColor = titleCol,
                subtitleColor = subCol
            )
            DhikrCategory.GATE_HAMD -> DhikrGateVisualSpec(
                containerStart = unifiedSurfaceStart,
                containerEnd = unifiedSurfaceEnd,
                accentColor = Color(0xFF684946),
                badgeBgColor = Color(0xFFE4D7D5),
                titleColor = titleCol,
                subtitleColor = subCol
            )
            DhikrCategory.GATE_HAWQALA -> DhikrGateVisualSpec(
                containerStart = unifiedSurfaceStart,
                containerEnd = unifiedSurfaceEnd,
                accentColor = Color(0xFF2D5566),
                badgeBgColor = Color(0xFFD2E0E4),
                titleColor = titleCol,
                subtitleColor = subCol
            )
            DhikrCategory.GATE_TASBIH -> DhikrGateVisualSpec(
                containerStart = unifiedSurfaceStart,
                containerEnd = unifiedSurfaceEnd,
                accentColor = Color(0xFF5E4732),
                badgeBgColor = Color(0xFFDFD3C6),
                titleColor = titleCol,
                subtitleColor = subCol
            )
            else -> DhikrGateVisualSpec(
                containerStart = unifiedSurfaceStart,
                containerEnd = unifiedSurfaceEnd,
                accentColor = Color(0xFF5B4A6E),
                badgeBgColor = Color(0xFFDED8E6),
                titleColor = titleCol,
                subtitleColor = subCol
            )
        }
    }
}

/**
 * WCAG 2.1 Relative Luminance and Contrast Ratio calculator for automated unit verification.
 */
object WcagContrastVerifier {
    fun relativeLuminance(color: Color): Double {
        fun channelLum(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        val r = channelLum(color.red)
        val g = channelLum(color.green)
        val b = channelLum(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun contrastRatio(foreground: Color, background: Color): Double {
        val l1 = relativeLuminance(foreground)
        val l2 = relativeLuminance(background)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun isPureWhite(color: Color): Boolean {
        return color.red >= 0.999f && color.green >= 0.999f && color.blue >= 0.999f
    }
}
