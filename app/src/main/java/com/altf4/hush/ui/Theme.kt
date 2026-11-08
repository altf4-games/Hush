package com.altf4.hush.ui

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.altf4.hush.data.ThemeMode

// Design notes. A neutral base with one accent (iris); depth comes from tonal surface steps, not
// borders or shadows. Meaning colours (protected, paused, warning) are fixed and always shown with
// an icon and words, never by colour alone.

private val Iris = Color(0xFF5B5BD6)
private val IrisLight = Color(0xFF8E8EF5)

private val LightScheme = lightColorScheme(
    primary = Iris,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E6FD),
    onPrimaryContainer = Color(0xFF26236B),
    secondary = Color(0xFF5E5F72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E8F2),
    onSecondaryContainer = Color(0xFF1C1C2B),
    tertiary = Color(0xFF128C5A),
    background = Color(0xFFF3F3F6),
    onBackground = Color(0xFF111117),
    surface = Color(0xFFF3F3F6),
    onSurface = Color(0xFF111117),
    surfaceVariant = Color(0xFFE9E9EF),
    onSurfaceVariant = Color(0xFF62626E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDEDF2),
    surfaceContainerHighest = Color(0xFFE4E4EB),
    outline = Color(0xFFC2C2CC),
    outlineVariant = Color(0xFFE6E6EC),
    error = Color(0xFFD93D42),
)

private val DarkScheme = darkColorScheme(
    primary = IrisLight,
    onPrimary = Color(0xFF14123D),
    primaryContainer = Color(0xFF2B2A63),
    onPrimaryContainer = Color(0xFFE2E1FF),
    secondary = Color(0xFFB4B4C8),
    onSecondary = Color(0xFF1C1C2B),
    secondaryContainer = Color(0xFF26262F),
    onSecondaryContainer = Color(0xFFE4E4EF),
    tertiary = Color(0xFF3DD68C),
    background = Color(0xFF0A0A0D),
    onBackground = Color(0xFFEDEDF2),
    surface = Color(0xFF0A0A0D),
    onSurface = Color(0xFFEDEDF2),
    surfaceVariant = Color(0xFF1B1B22),
    onSurfaceVariant = Color(0xFF9C9CAB),
    surfaceContainerLowest = Color(0xFF070709),
    surfaceContainerLow = Color(0xFF131318),
    surfaceContainer = Color(0xFF16161C),
    surfaceContainerHigh = Color(0xFF1F1F27),
    surfaceContainerHighest = Color(0xFF292932),
    outline = Color(0xFF45454F),
    outlineVariant = Color(0xFF26262E),
    error = Color(0xFFFF6B6F),
)

/** Colours with a fixed meaning, kept the same whatever the accent or wallpaper. */
@Immutable
class StatusColors(
    val protected: Color,
    val protectedContainer: Color,
    val paused: Color,
    val pausedContainer: Color,
    val warning: Color,
    val warningContainer: Color,
)

private val LightStatus = StatusColors(
    protected = Color(0xFF128C5A),
    protectedContainer = Color(0xFFDDF5E8),
    paused = Color(0xFFB4530A),
    pausedContainer = Color(0xFFFCEBDD),
    warning = Color(0xFFB4530A),
    warningContainer = Color(0xFFFCEBDD),
)

private val DarkStatus = StatusColors(
    protected = Color(0xFF3DD68C),
    protectedContainer = Color(0xFF10301F),
    paused = Color(0xFFFFA057),
    pausedContainer = Color(0xFF3A2312),
    warning = Color(0xFFFFB86B),
    warningContainer = Color(0xFF3A2312),
)

val LocalStatusColors = staticCompositionLocalOf { DarkStatus }

/** Fixed-meaning colours for the current theme. */
val statusColors: StatusColors
    @Composable get() = LocalStatusColors.current

/**
 * One corner scale everywhere: small controls 12, rows and tiles 20, cards 24, the hero 32.
 * Matches the Material 3 Expressive corner tokens (12/16/20/28/32).
 */
private val HushShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Tabular figures so counters do not jitter as they change. */
private const val TABULAR = "tnum"

private val base = Typography()

/**
 * About five working styles: screen title, hero/stat numbers, titles, body, labels. Numbers use
 * the medium-weight "emphasized" treatment from Material 3 Expressive.
 */
private val HushTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1.5).sp, fontFeatureSettings = TABULAR),
    displayMedium = base.displayMedium.copy(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp, fontFeatureSettings = TABULAR),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp, fontFeatureSettings = TABULAR),
    headlineLarge = base.headlineLarge.copy(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp, fontFeatureSettings = TABULAR),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, fontFeatureSettings = TABULAR),
    titleLarge = base.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.copy(letterSpacing = 0.sp),
    bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

/** Text style for big counters on tiles. */
val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = TABULAR)

/**
 * Spring motion in the spirit of Material 3 Expressive: a bouncy spring for the hero control and a
 * calm one for everything else.
 */
object HushMotion {
    fun <T> expressive(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

    fun <T> standard(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
}

/** True when wallpaper-based colours are available (Android 12 and later). */
val dynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun HushTheme(mode: ThemeMode, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && dynamicColorSupported -> {
            val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            // Keep the calm neutral surfaces; take only the accent from the wallpaper.
            val neutral = if (dark) DarkScheme else LightScheme
            neutral.copy(
                primary = dynamic.primary,
                onPrimary = dynamic.onPrimary,
                primaryContainer = dynamic.primaryContainer,
                onPrimaryContainer = dynamic.onPrimaryContainer,
                secondaryContainer = dynamic.secondaryContainer,
                onSecondaryContainer = dynamic.onSecondaryContainer,
            )
        }
        dark -> DarkScheme
        else -> LightScheme
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

    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HushTypography,
            shapes = HushShapes,
            content = content,
        )
    }
}
