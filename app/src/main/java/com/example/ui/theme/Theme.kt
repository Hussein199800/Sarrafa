package com.example.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.domain.model.DarkModeConfig
import com.example.domain.model.WirdiThemePalette

// Design Tokens Corner Radii:
// Small elements: 12–16dp (14.dp) | Major Cards & Grouped lists: 20–24dp (22.dp) | Hero & Sheets: 24.dp
val WirdiShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(WirdiDesignTokens.smallCornerRadius),
    medium = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
    large = RoundedCornerShape(WirdiDesignTokens.cardCornerRadius),
    extraLarge = RoundedCornerShape(WirdiDesignTokens.heroCornerRadius)
)

val LocalReduceMotion = staticCompositionLocalOf { false }

fun resolveIsDarkTheme(
    palette: WirdiThemePalette,
    darkModeConfig: DarkModeConfig,
    systemDark: Boolean
): Boolean {
    return when (darkModeConfig) {
        DarkModeConfig.DARK -> true
        DarkModeConfig.LIGHT -> false
        DarkModeConfig.SYSTEM -> if (palette.prefersLightByDefault) false else systemDark
    }
}

@Composable
fun WirdiTheme(
    palette: WirdiThemePalette = WirdiThemePalette.UFUQ,
    darkModeConfig: DarkModeConfig = DarkModeConfig.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = resolveIsDarkTheme(palette, darkModeConfig, systemDark)

    val reduceMotion = remember(context) {
        runCatching {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale == 0f
        }.getOrDefault(false)
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> getPaletteColorScheme(palette, useDarkTheme)
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalReduceMotion provides reduceMotion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = WirdiShapes,
            content = content
        )
    }
}
