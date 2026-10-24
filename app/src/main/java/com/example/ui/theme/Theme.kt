package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SageGreen,
    onPrimary = Color(0xFF0F1E16),
    primaryContainer = SageGreenContainerDark,
    onPrimaryContainer = SageGreenLight,
    secondary = AccentEmerald,
    onSecondary = Color(0xFF003822),
    secondaryContainer = Color(0xFF005234),
    onSecondaryContainer = Color(0xFF8CF5B0),
    tertiary = AccentAmber,
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF633F00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkTextSecondary,
    error = AccentRose,
    onError = Color(0xFF60001B),
    errorContainer = Color(0xFF8C002B),
    onErrorContainer = Color(0xFFFFD9DF),
    outline = Color(0xFF3E4D44),
    outlineVariant = Color(0xFF2B362F)
)

private val LightColorScheme = lightColorScheme(
    primary = SageGreenDark,
    onPrimary = Color.White,
    primaryContainer = SageGreenContainerLight,
    onPrimaryContainer = Color(0xFF132F22),
    secondary = Color(0xFF0F8A5A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB5F2D1),
    onSecondaryContainer = Color(0xFF002113),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFECC8),
    onTertiaryContainer = Color(0xFF2E1500),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightCard,
    onSurfaceVariant = LightTextSecondary,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFFB4C4BA),
    outlineVariant = Color(0xFFD6E2DB)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme: ColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
