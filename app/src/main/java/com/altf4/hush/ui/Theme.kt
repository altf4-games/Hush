package com.altf4.hush.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.altf4.hush.data.ThemeMode

// Dark: near-black surfaces, one calm blue accent. Static scheme (no dynamic colour) so the app
// looks the same on every device.
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7C9CFF),
    onPrimary = Color(0xFF0A0C10),
    primaryContainer = Color(0xFF1E2A4A),
    onPrimaryContainer = Color(0xFFD6E0FF),
    secondary = Color(0xFF7C9CFF),
    onSecondary = Color(0xFF0A0C10),
    secondaryContainer = Color(0xFF1E2A4A),
    onSecondaryContainer = Color(0xFFD6E0FF),
    background = Color(0xFF0A0C10),
    onBackground = Color(0xFFE8EBF0),
    surface = Color(0xFF0A0C10),
    onSurface = Color(0xFFE8EBF0),
    surfaceVariant = Color(0xFF1A1E26),
    onSurfaceVariant = Color(0xFF98A1B0),
    surfaceContainerLowest = Color(0xFF07090C),
    surfaceContainerLow = Color(0xFF0F1217),
    surfaceContainer = Color(0xFF12151B),
    surfaceContainerHigh = Color(0xFF1A1E26),
    surfaceContainerHighest = Color(0xFF222733),
    outline = Color(0xFF3A4150),
    outlineVariant = Color(0xFF232832),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3F5EDB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF16286B),
    secondary = Color(0xFF3F5EDB),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF16286B),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF14171C),
    surface = Color(0xFFF6F7F9),
    onSurface = Color(0xFF14171C),
    surfaceVariant = Color(0xFFEEF0F4),
    onSurfaceVariant = Color(0xFF5B6472),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBFC),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEEF0F4),
    surfaceContainerHighest = Color(0xFFE6E9EF),
    outline = Color(0xFFB4BAC6),
    outlineVariant = Color(0xFFE2E5EB),
)

private val HushShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val base = Typography()
private val HushTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
)

@Composable
fun HushTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }

    // Keep status/navigation bar icons readable against the chosen theme, not the system one.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = HushTypography,
        shapes = HushShapes,
        content = content,
    )
}
