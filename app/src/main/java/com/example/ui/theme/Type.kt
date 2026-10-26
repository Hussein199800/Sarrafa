package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.ReadingFontFamily

val AmiriFontFamily = FontFamily(
    Font(R.font.amiri, FontWeight.Normal),
    Font(R.font.amiri, FontWeight.Bold)
)

val NotoNaskhArabicFontFamily = FontFamily(
    Font(R.font.noto_naskh_arabic, FontWeight.Normal),
    Font(R.font.noto_naskh_arabic, FontWeight.Bold)
)

fun resolveReadingFontFamily(readingFont: ReadingFontFamily): FontFamily {
    return when (readingFont) {
        ReadingFontFamily.SYSTEM -> FontFamily.Default
        ReadingFontFamily.AMIRI -> AmiriFontFamily
        ReadingFontFamily.NOTO_NASKH -> NotoNaskhArabicFontFamily
    }
}

/**
 * Generates a TextStyle specifically tuned for vocalized Arabic (Tashkeel & Quranic stop marks)
 * with includeFontPadding = true and generous lineHeight so diacritics are never clipped even at 200% system font scale.
 */
fun buildArabicReadingTextStyle(
    readingFont: ReadingFontFamily,
    scale: Float,
    baseFontSizeSp: Float = 22f,
    fontWeight: FontWeight = FontWeight.Normal
): TextStyle {
    val safeScale = scale.coerceIn(0.85f, 1.6f)
    val fontSize = (baseFontSizeSp * safeScale).sp
    val lineHeight = (baseFontSizeSp * safeScale * 1.95f).sp
    return TextStyle(
        fontFamily = resolveReadingFontFamily(readingFont),
        fontWeight = fontWeight,
        fontSize = fontSize,
        lineHeight = lineHeight,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None
        )
    )
}

// Apple-inspired Typography scale using System Font for UI:
// Large Title: 34sp Bold | Section Title: 22sp Bold | Body: 17sp | Caption: 13sp
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 46.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 46.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 32.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 32.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 27.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    )
)
