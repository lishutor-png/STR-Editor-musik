package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = LimeGreenBright,
    onPrimary = Color(0xFF142407),
    primaryContainer = LimeGreenDeep,
    onPrimaryContainer = LimeGreenLight,
    secondary = AccentEmerald,
    onSecondary = Color(0xFF064E3B),
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = AccentAmber,
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFDE68A),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkTextSecondary,
    outline = Color(0xFF3B5037),
    outlineVariant = Color(0xFF233520)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4D7C0F), // Lime 700 for accessible contrast on light backgrounds
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFECFCCB),
    onPrimaryContainer = Color(0xFF1A2E05),
    secondary = Color(0xFF059669),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Color(0xFFD97706),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightCard,
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0xFFBDCEB4),
    outlineVariant = Color(0xFFDFECDA)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
