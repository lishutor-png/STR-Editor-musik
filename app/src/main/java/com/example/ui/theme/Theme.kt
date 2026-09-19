package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SageGreen,
    onPrimary = Color(0xFF0F261B),
    primaryContainer = SageGreenContainerDark,
    onPrimaryContainer = SageGreenLight,
    secondary = AccentEmerald,
    onSecondary = Color(0xFF06332E),
    secondaryContainer = Color(0xFF1B403B),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = AccentAmber,
    onTertiary = Color(0xFF381F04),
    tertiaryContainer = Color(0xFF4D3215),
    onTertiaryContainer = Color(0xFFFDE68A),
    error = AccentRose,
    onError = Color(0xFF380808),
    errorContainer = Color(0xFF521C1C),
    onErrorContainer = Color(0xFFFCD8D8),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkTextSecondary,
    outline = Color(0xFF33404B),
    outlineVariant = Color(0xFF253038)
)

private val LightColorScheme = lightColorScheme(
    primary = SageGreenDark,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = SageGreenContainerLight,
    onPrimaryContainer = Color(0xFF133624),
    secondary = Color(0xFF2E8B80),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F4F1),
    onSecondaryContainer = Color(0xFF0F4039),
    tertiary = Color(0xFFB5702A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBEBD8),
    onTertiaryContainer = Color(0xFF593006),
    error = Color(0xFFC94A4A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCE8E8),
    onErrorContainer = Color(0xFF6B1818),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightCard,
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0xFFC3CFC9),
    outlineVariant = Color(0xFFDDE6E1)
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
