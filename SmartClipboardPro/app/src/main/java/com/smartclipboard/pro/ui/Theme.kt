package com.smartclipboard.pro.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Dark = darkColorScheme(
    primary = Color(0xFF8E9BFF), onPrimary = Color(0xFF0A0E33),
    primaryContainer = Color(0xFF2B3170), onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFF4FD8C4), onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF0F4A41), onSecondaryContainer = Color(0xFFB5F5EA),
    background = Color(0xFF0D0F1E), onBackground = Color(0xFFE6E8F5),
    surface = Color(0xFF161931), onSurface = Color(0xFFE6E8F5),
    surfaceVariant = Color(0xFF232744), onSurfaceVariant = Color(0xFFA9AFCB),
    outline = Color(0xFF5B6188), outlineVariant = Color(0xFF2E3354),
    error = Color(0xFFFF8A80)
)

private val Light = lightColorScheme(
    primary = Color(0xFF4F46E5), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E2FF), onPrimaryContainer = Color(0xFF1B1B6B),
    secondary = Color(0xFF0E9F8E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCF5EE), onSecondaryContainer = Color(0xFF00382F),
    background = Color(0xFFF4F5FF), onBackground = Color(0xFF1A1C2E),
    surface = Color.White, onSurface = Color(0xFF1A1C2E),
    surfaceVariant = Color(0xFFE8EAFB), onSurfaceVariant = Color(0xFF565B7A),
    outline = Color(0xFF8A90B4), outlineVariant = Color(0xFFD8DBF2),
    error = Color(0xFFD93025)
)

// Letter spacing must be zero for Persian/Arabic, otherwise the joined letters get pulled apart.
private fun TextStyle.fa(weight: FontWeight? = null) =
    copy(letterSpacing = 0.sp, fontWeight = weight ?: fontWeight)

private val base = Typography()
private val AppTypography = Typography(
    displayLarge = base.displayLarge.fa(), displayMedium = base.displayMedium.fa(),
    displaySmall = base.displaySmall.fa(),
    headlineLarge = base.headlineLarge.fa(FontWeight.Bold),
    headlineMedium = base.headlineMedium.fa(FontWeight.Bold),
    headlineSmall = base.headlineSmall.fa(FontWeight.Bold),
    titleLarge = base.titleLarge.fa(FontWeight.Bold),
    titleMedium = base.titleMedium.fa(FontWeight.SemiBold),
    titleSmall = base.titleSmall.fa(FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.fa(), bodyMedium = base.bodyMedium.fa(), bodySmall = base.bodySmall.fa(),
    labelLarge = base.labelLarge.fa(FontWeight.SemiBold),
    labelMedium = base.labelMedium.fa(FontWeight.Medium),
    labelSmall = base.labelSmall.fa(FontWeight.Medium)
)

@Composable
fun SmartClipTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content
    )
}
