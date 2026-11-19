package dev.itszain.termuxfilemanager.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class TfmExtendedColors(
    val isDark: Boolean,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val codeBg: Color,
    val codeHeaderBg: Color,
    val codeBorder: Color,
    val gutterText: Color,
    val diffAddBg: Color,
    val diffAddText: Color,
    val diffDelBg: Color,
    val diffDelText: Color,
    val syntaxKeyword: Color,
    val syntaxString: Color,
    val syntaxComment: Color,
    val syntaxNumber: Color,
    val syntaxType: Color,
    val syntaxFunction: Color,
    val syntaxProperty: Color,
    val folderTint: Color,
    val codeTint: Color,
    val docTint: Color,
    val mediaTint: Color,
    val archiveTint: Color,
    val symlinkTint: Color
)

private val DarkExtended = TfmExtendedColors(
    isDark = true,
    success = Color(0xFF4ADE80),
    onSuccess = Color(0xFF052E16),
    successContainer = Color(0xFF103324),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF33260D),
    info = Color(0xFF60A5FA),
    infoContainer = Color(0xFF12263D),
    codeBg = Color(0xFF0D1418),
    codeHeaderBg = Color(0xFF152026),
    codeBorder = Color(0xFF23323A),
    gutterText = Color(0xFF5D747E),
    diffAddBg = Color(0x2A22C55E),
    diffAddText = Color(0xFF86EFAC),
    diffDelBg = Color(0x2AEF4444),
    diffDelText = Color(0xFFFCA5A5),
    syntaxKeyword = Color(0xFFC084FC),
    syntaxString = Color(0xFF7EE787),
    syntaxComment = Color(0xFF6E808A),
    syntaxNumber = Color(0xFFF59E0B),
    syntaxType = Color(0xFF5EEAD4),
    syntaxFunction = Color(0xFF79C0FF),
    syntaxProperty = Color(0xFFF9A8D4),
    folderTint = Color(0xFF4CE0C2),
    codeTint = Color(0xFF79C0FF),
    docTint = Color(0xFFFBBF24),
    mediaTint = Color(0xFFF472B6),
    archiveTint = Color(0xFFFB923C),
    symlinkTint = Color(0xFFC084FC)
)

private val LightExtended = TfmExtendedColors(
    isDark = false,
    success = Color(0xFF15803D),
    onSuccess = Color.White,
    successContainer = Color(0xFFDCFCE7),
    warning = Color(0xFFB45309),
    warningContainer = Color(0xFFFEF3C7),
    info = Color(0xFF1D4ED8),
    infoContainer = Color(0xFFDBEAFE),
    codeBg = Color(0xFFF0F4F2),
    codeHeaderBg = Color(0xFFE4ECE8),
    codeBorder = Color(0xFFCFDCD5),
    gutterText = Color(0xFF6B8077),
    diffAddBg = Color(0x2816A34A),
    diffAddText = Color(0xFF166534),
    diffDelBg = Color(0x24DC2626),
    diffDelText = Color(0xFF991B1B),
    syntaxKeyword = Color(0xFF7C3AED),
    syntaxString = Color(0xFF15803D),
    syntaxComment = Color(0xFF64748B),
    syntaxNumber = Color(0xFFC2410C),
    syntaxType = Color(0xFF0F766E),
    syntaxFunction = Color(0xFF1D4ED8),
    syntaxProperty = Color(0xFFBE185D),
    folderTint = Color(0xFF006B58),
    codeTint = Color(0xFF1D4ED8),
    docTint = Color(0xFFB45309),
    mediaTint = Color(0xFFBE185D),
    archiveTint = Color(0xFFC2410C),
    symlinkTint = Color(0xFF7C3AED)
)

val LocalTfmColors = staticCompositionLocalOf { DarkExtended }

private val MidnightPalette = darkColorScheme(
    primary = Color(0xFF4CE0C2),
    onPrimary = Color(0xFF00372D),
    primaryContainer = Color(0xFF12473D),
    onPrimaryContainer = Color(0xFFB5F7E7),
    secondary = Color(0xFF79B8FF),
    onSecondary = Color(0xFF062E52),
    secondaryContainer = Color(0xFF173652),
    onSecondaryContainer = Color(0xFFD0E7FF),
    tertiary = Color(0xFFF4B860),
    onTertiary = Color(0xFF3F2700),
    tertiaryContainer = Color(0xFF4E3512),
    onTertiaryContainer = Color(0xFFFFE2B8),
    background = Color(0xFF0B1014),
    onBackground = Color(0xFFE4ECEB),
    surface = Color(0xFF0B1014),
    onSurface = Color(0xFFE4ECEB),
    surfaceContainerLowest = Color(0xFF070B0E),
    surfaceContainerLow = Color(0xFF10171C),
    surfaceContainer = Color(0xFF151E24),
    surfaceContainerHigh = Color(0xFF1C272E),
    surfaceContainerHighest = Color(0xFF24323B),
    surfaceVariant = Color(0xFF212E35),
    onSurfaceVariant = Color(0xFF9CB0B4),
    outline = Color(0xFF4B5E65),
    outlineVariant = Color(0xFF25343C),
    error = Color(0xFFFF7B72),
    onError = Color(0xFF4F0E0B),
    errorContainer = Color(0xFF4A1818),
    onErrorContainer = Color(0xFFFFD8D5)
)

private val AmoledPalette = MidnightPalette.copy(
    primary = Color(0xFF52F0CE),
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080C0E),
    surfaceContainer = Color(0xFF0E1417),
    surfaceContainerHigh = Color(0xFF161F24),
    surfaceContainerHighest = Color(0xFF1E2A30),
    outlineVariant = Color(0xFF1D282E)
)

private val NordPalette = darkColorScheme(
    primary = Color(0xFF78BAFF),
    onPrimary = Color(0xFF00305E),
    primaryContainer = Color(0xFF183C66),
    onPrimaryContainer = Color(0xFFD4E9FF),
    secondary = Color(0xFFA794FF),
    onSecondary = Color(0xFF24165E),
    secondaryContainer = Color(0xFF33266B),
    onSecondaryContainer = Color(0xFFE4DDFF),
    tertiary = Color(0xFF63D9C1),
    onTertiary = Color(0xFF00382E),
    tertiaryContainer = Color(0xFF124A40),
    onTertiaryContainer = Color(0xFFB8F5E7),
    background = Color(0xFF111622),
    onBackground = Color(0xFFE6ECF7),
    surface = Color(0xFF111622),
    onSurface = Color(0xFFE6ECF7),
    surfaceContainerLowest = Color(0xFF0B0F18),
    surfaceContainerLow = Color(0xFF151C2B),
    surfaceContainer = Color(0xFF1B2335),
    surfaceContainerHigh = Color(0xFF232E44),
    surfaceContainerHighest = Color(0xFF2C3954),
    surfaceVariant = Color(0xFF253147),
    onSurfaceVariant = Color(0xFFA4B2CC),
    outline = Color(0xFF546380),
    outlineVariant = Color(0xFF2A364F),
    error = Color(0xFFFF7B89),
    onError = Color(0xFF4F0E18),
    errorContainer = Color(0xFF4A1922),
    onErrorContainer = Color(0xFFFFD9DE)
)

private val LightPalette = lightColorScheme(
    primary = Color(0xFF006B58),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F3E2),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF1D5B8C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E7FA),
    onSecondaryContainer = Color(0xFF082540),
    tertiary = Color(0xFF8C5300),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDFB5),
    onTertiaryContainer = Color(0xFF2E1800),
    background = Color(0xFFF4F8F6),
    onBackground = Color(0xFF151F1B),
    surface = Color(0xFFF4F8F6),
    onSurface = Color(0xFF151F1B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEDF3F0),
    surfaceContainer = Color(0xFFE6EEEA),
    surfaceContainerHigh = Color(0xFFDEE8E3),
    surfaceContainerHighest = Color(0xFFD5E1DB),
    surfaceVariant = Color(0xFFDAE5DF),
    onSurfaceVariant = Color(0xFF465C54),
    outline = Color(0xFF72877F),
    outlineVariant = Color(0xFFC5D4CD),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun TfmTheme(name: String, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (name) {
        "light" -> false
        "system" -> systemDark
        else -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val base = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (name == "amoled") base.copy(background = Color.Black, surface = Color.Black, surfaceContainer = Color(0xFF0C1012)) else base
        }
        name == "amoled" -> AmoledPalette
        name == "nord" -> NordPalette
        dark -> MidnightPalette
        else -> LightPalette
    }
    val extended = if (dark) {
        if (name == "amoled") DarkExtended.copy(codeBg = Color(0xFF070B0D), codeHeaderBg = Color(0xFF0F161A))
        else if (name == "nord") DarkExtended.copy(codeBg = Color(0xFF131927), codeHeaderBg = Color(0xFF1C2538), codeBorder = Color(0xFF2A3752))
        else DarkExtended
    } else LightExtended

    val typography = Typography().run {
        copy(
            headlineLarge = headlineLarge.copy(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
            headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
            titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
            titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
            titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
            labelSmall = labelSmall.copy(letterSpacing = 0.4.sp, fontWeight = FontWeight.Medium),
            bodyMedium = bodyMedium.copy(lineHeight = 22.sp)
        )
    }

    CompositionLocalProvider(LocalTfmColors provides extended) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}
