package dev.itszain.termuxfilemanager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

private val DarkPalette = darkColorScheme(primary = Color(0xFF70D9C4), onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF194D43), onPrimaryContainer = Color(0xFFB4F1DF),
    background = Color(0xFF101416), surface = Color(0xFF101416), surfaceContainer = Color(0xFF1B2124),
    surfaceContainerHigh = Color(0xFF252D30), surfaceVariant = Color(0xFF293335),
    onSurface = Color(0xFFE3E9E8), onSurfaceVariant = Color(0xFFA6B6B2), outline = Color(0xFF61706C))
private val LightPalette = lightColorScheme(primary = Color(0xFF006B58), onPrimary = Color.White,
    primaryContainer = Color(0xFFB5F2DF), onPrimaryContainer = Color(0xFF002119),
    background = Color(0xFFF6F9F6), surface = Color(0xFFF6F9F6), surfaceContainer = Color(0xFFEDF2EE),
    surfaceContainerHigh = Color(0xFFE4EBE6), onSurface = Color(0xFF17201C), onSurfaceVariant = Color(0xFF4B6158))
@Composable fun TfmTheme(name: String, content: @Composable () -> Unit) {
    val dark = when(name) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    val colors = if (name == "amoled") DarkPalette.copy(background = Color.Black, surface = Color.Black, surfaceContainer = Color(0xFF0D1110)) else if (dark) DarkPalette else LightPalette
    MaterialTheme(colorScheme = colors, typography = Typography().run { copy(
        headlineLarge = headlineLarge.copy(fontSize = 32.sp), titleLarge = titleLarge.copy(letterSpacing = (-0.3).sp),
        labelSmall = labelSmall.copy(letterSpacing = 0.3.sp), bodyMedium = bodyMedium.copy(lineHeight = 22.sp)) }, content = content)
}
