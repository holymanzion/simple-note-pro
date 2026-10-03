package com.holymanzion.simplenotepro.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.holymanzion.simplenotepro.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5900),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDEA3),
    onPrimaryContainer = Color(0xFF261900),
    secondary = Color(0xFF6C5C3F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF6DFBB),
    onSecondaryContainer = Color(0xFF251A04),
    tertiary = Color(0xFF4B6546),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCDEBC4),
    onTertiaryContainer = Color(0xFF082008),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBF6),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFFBF6),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFEEE0CF),
    onSurfaceVariant = Color(0xFF4E4539),
    outline = Color(0xFF807667),
    outlineVariant = Color(0xFFD1C5B4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF2E7),
    surfaceContainer = Color(0xFFF5ECE1),
    surfaceContainerHigh = Color(0xFFEFE6DB),
    surfaceContainerHighest = Color(0xFFE9E1D6),
    surfaceDim = Color(0xFFE1D9CE),
    surfaceBright = Color(0xFFFFFBF6),
    // Snackbars use the inverse roles; left unset they fall back to Material's purple.
    inverseSurface = Color(0xFF34302A),
    inverseOnSurface = Color(0xFFF8EFE7),
    inversePrimary = Color(0xFFF6BE48),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF6BE48),
    onPrimary = Color(0xFF402D00),
    primaryContainer = Color(0xFF5C4200),
    onPrimaryContainer = Color(0xFFFFDEA3),
    secondary = Color(0xFFD9C4A0),
    onSecondary = Color(0xFF3B2F15),
    secondaryContainer = Color(0xFF53452A),
    onSecondaryContainer = Color(0xFFF6DFBB),
    tertiary = Color(0xFFB2CFA9),
    onTertiary = Color(0xFF1E361B),
    tertiaryContainer = Color(0xFF344D30),
    onTertiaryContainer = Color(0xFFCDEBC4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF17140F),
    onBackground = Color(0xFFEAE1D6),
    surface = Color(0xFF17140F),
    onSurface = Color(0xFFEAE1D6),
    surfaceVariant = Color(0xFF4E4539),
    onSurfaceVariant = Color(0xFFD1C5B4),
    outline = Color(0xFF9A8F80),
    outlineVariant = Color(0xFF4E4539),
    surfaceContainerLowest = Color(0xFF110E0A),
    surfaceContainerLow = Color(0xFF1F1B16),
    surfaceContainer = Color(0xFF231F1A),
    surfaceContainerHigh = Color(0xFF2E2924),
    surfaceContainerHighest = Color(0xFF39342E),
    surfaceDim = Color(0xFF17140F),
    surfaceBright = Color(0xFF3E3933),
    inverseSurface = Color(0xFFEAE1D6),
    inverseOnSurface = Color(0xFF34302A),
    inversePrimary = Color(0xFF7A5900),
)

/** Whether the app is currently drawn dark; note colors pick their variant from this. */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun SimpleNoteTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = themeMode.isDark()
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

data class NoteColor(val name: String, val light: Color, val dark: Color)

/** Index 0 is "no color", which renders as the theme surface. */
val NoteColors = listOf(
    NoteColor("Default", Color.Unspecified, Color.Unspecified),
    NoteColor("Coral", Color(0xFFFAAFA8), Color(0xFF77172E)),
    NoteColor("Peach", Color(0xFFF39F76), Color(0xFF692B17)),
    NoteColor("Sand", Color(0xFFFFF8B8), Color(0xFF7C4A03)),
    NoteColor("Mint", Color(0xFFE2F6D3), Color(0xFF264D3B)),
    NoteColor("Sage", Color(0xFFB4DDD3), Color(0xFF0C625D)),
    NoteColor("Fog", Color(0xFFD4E4ED), Color(0xFF256377)),
    NoteColor("Storm", Color(0xFFAECCDC), Color(0xFF284255)),
    NoteColor("Dusk", Color(0xFFD3BFDB), Color(0xFF472E5B)),
    NoteColor("Blossom", Color(0xFFF6E2DD), Color(0xFF6C394F)),
    NoteColor("Clay", Color(0xFFE9E3D4), Color(0xFF4B443A)),
    NoteColor("Chalk", Color(0xFFEFEFF1), Color(0xFF232427)),
)

/** Background for a note color index, falling back to [default] for "no color". */
@Composable
@ReadOnlyComposable
fun noteBackground(index: Int, default: Color): Color {
    val entry = NoteColors.getOrNull(index) ?: return default
    val color = if (LocalDarkTheme.current) entry.dark else entry.light
    return if (color == Color.Unspecified) default else color
}
