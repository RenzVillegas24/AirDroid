package io.moonbase.airdroid

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

enum class TintMode(val label: String) {
    CUSTOM("CrossPair"),
    SYSTEM("System"),
}

private val AirDroidDarkColors = darkColorScheme(
    primary = Color(0xFF3DDC84),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF0E5A36),
    onPrimaryContainer = Color(0xFFB8F5D2),
    secondary = Color(0xFF9AD6B9),
    onSecondary = Color(0xFF003824),
    secondaryContainer = Color(0xFF164E38),
    onSecondaryContainer = Color(0xFFB6F2D4),
    tertiary = Color(0xFF8DD8E8),
    onTertiary = Color(0xFF00363E),
    tertiaryContainer = Color(0xFF004F5A),
    onTertiaryContainer = Color(0xFFB4EBF5),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF071426),
    onBackground = Color(0xFFE3EDF7),
    surface = Color(0xFF071426),
    onSurface = Color(0xFFE3EDF7),
    surfaceVariant = Color(0xFF24364A),
    onSurfaceVariant = Color(0xFFC2CFDD),
    outline = Color(0xFF8C9BAB),
    outlineVariant = Color(0xFF3B4D61),
    surfaceTint = Color(0xFF3DDC84),
    inverseSurface = Color(0xFFE3EDF7),
    inverseOnSurface = Color(0xFF1B2B3D),
    inversePrimary = Color(0xFF0B6B42),
    surfaceDim = Color(0xFF071426),
    surfaceBright = Color(0xFF2B3E55),
    surfaceContainerLowest = Color(0xFF04101E),
    surfaceContainerLow = Color(0xFF0B1B33),
    surfaceContainer = Color(0xFF10243D),
    surfaceContainerHigh = Color(0xFF172C47),
    surfaceContainerHighest = Color(0xFF1F3652),
)

private val AirDroidLightColors = lightColorScheme(
    primary = Color(0xFF0B1B33),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB9F3D0),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF1B6B45),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6F5E3),
    onSecondaryContainer = Color(0xFF002114),
    tertiary = Color(0xFF006874),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF97F0FF),
    onTertiaryContainer = Color(0xFF001F24),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FBF7),
    onBackground = Color(0xFF171C19),
    surface = Color(0xFFF6FBF7),
    onSurface = Color(0xFF171C19),
    surfaceVariant = Color(0xFFDCE5DF),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFC0C9C3),
    surfaceTint = Color(0xFF0B1B33),
    inverseSurface = Color(0xFF2B322D),
    inverseOnSurface = Color(0xFFECF2EC),
    inversePrimary = Color(0xFF9DD7B4),
    surfaceDim = Color(0xFFD6DBD7),
    surfaceBright = Color(0xFFF6FBF7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5F0),
    surfaceContainer = Color(0xFFEAEFEA),
    surfaceContainerHigh = Color(0xFFE4EAE4),
    surfaceContainerHighest = Color(0xFFDEE4DE),
)

private val AirDroidTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

private val AirDroidShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun AirDroidTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    tintMode: TintMode = TintMode.CUSTOM,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val useDynamicTint = tintMode == TintMode.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        useDynamicTint && darkTheme -> dynamicDarkColorScheme(context)
        useDynamicTint -> dynamicLightColorScheme(context)
        darkTheme -> AirDroidDarkColors
        else -> AirDroidLightColors
    }
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AirDroidTypography,
        shapes = AirDroidShapes,
        content = content,
    )
}
