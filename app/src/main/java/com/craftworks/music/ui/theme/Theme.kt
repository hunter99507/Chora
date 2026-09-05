package com.craftworks.music.ui.theme

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.tv.material3.MaterialTheme

import com.craftworks.music.managers.settings.AppTheme

private val DarkGreyBackground = Color(0xFF1E1F22)
private val DarkGreySurface = Color(0xFF232428)
private val DarkGreySurfaceContainer = Color(0xFF2B2D31)
private val DarkGreySurfaceContainerLow = Color(0xFF232428)
private val DarkGreySurfaceContainerHigh = Color(0xFF313338)
private val DarkGreySurfaceContainerHighest = Color(0xFF383A40)
private val DarkGreySurfaceVariant = Color(0xFF2E3035)

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = DarkGreyBackground,
    surface = DarkGreySurface,
    surfaceContainer = DarkGreySurfaceContainer,
    surfaceContainerLow = DarkGreySurfaceContainerLow,
    surfaceContainerHigh = DarkGreySurfaceContainerHigh,
    surfaceContainerHighest = DarkGreySurfaceContainerHighest,
    surfaceVariant = DarkGreySurfaceVariant,
    onBackground = Color(0xFFE3E5E8),
    onSurface = Color(0xFFE3E5E8),
    onSurfaceVariant = Color(0xFFC4C7C5)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val ModernEditorialColorScheme = darkColorScheme(
    primary = Color(0xFFF59E0B),
    onPrimary = Color(0xFF121110),
    primaryContainer = Color(0xFFB45309),
    onPrimaryContainer = Color(0xFFEDE8DF),
    secondary = Color(0xFFD97706),
    onSecondary = Color(0xFF121110),
    secondaryContainer = Color(0xFF24211D),
    onSecondaryContainer = Color(0xFFEDE8DF),
    tertiary = Color(0xFFC86D51),
    onTertiary = Color(0xFF121110),
    background = Color(0xFF121110),
    onBackground = Color(0xFFEDE8DF),
    surface = Color(0xFF1A1816),
    onSurface = Color(0xFFEDE8DF),
    surfaceContainer = Color(0xFF24211D),
    surfaceContainerLow = Color(0xFF1A1816),
    surfaceContainerHigh = Color(0xFF2C2824),
    surfaceContainerHighest = Color(0xFF38332E),
    surfaceVariant = Color(0xFF24211D),
    onSurfaceVariant = Color(0xFFA39E93),
    surfaceDim = Color(0xFF0F0E0D),
    surfaceBright = Color(0xFF38332E)
)

private val NordicSlateColorScheme = darkColorScheme(
    primary = Color(0xFF7EC4B0),
    onPrimary = Color(0xFF0C0F11),
    primaryContainer = Color(0xFF56A892),
    onPrimaryContainer = Color(0xFFF4F6F5),
    secondary = Color(0xFFA3D9C9),
    onSecondary = Color(0xFF0C0F11),
    secondaryContainer = Color(0xFF1B2226),
    onSecondaryContainer = Color(0xFFF4F6F5),
    tertiary = Color(0xFFBCECE0),
    onTertiary = Color(0xFF0C0F11),
    background = Color(0xFF0C0F11),
    onBackground = Color(0xFFF4F6F5),
    surface = Color(0xFF151A1D),
    onSurface = Color(0xFFF4F6F5),
    surfaceContainer = Color(0xFF1B2226),
    surfaceContainerLow = Color(0xFF101416),
    surfaceContainerHigh = Color(0xFF212A30),
    surfaceContainerHighest = Color(0xFF263137),
    surfaceVariant = Color(0xFF1B2226),
    onSurfaceVariant = Color(0xFF8C9BA0),
    surfaceDim = Color(0xFF080A0C),
    surfaceBright = Color(0xFF263137)
)

private val AppleMusicColorScheme = lightColorScheme(
    primary = Color(0xFFFA243C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDADE),
    onPrimaryContainer = Color(0xFF40000A),
    secondary = Color(0xFFE0162D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF2F2F7),
    onSecondaryContainer = Color(0xFF000000),
    tertiary = Color(0xFFFA243C),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFF2F2F7),
    onSurface = Color(0xFF000000),
    surfaceContainer = Color(0xFFE5E5EA),
    surfaceContainerLow = Color(0xFFF8F8FA),
    surfaceContainerHigh = Color(0xFFDCDCE2),
    surfaceContainerHighest = Color(0xFFD1D1D6),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF6C6C70),
    surfaceDim = Color(0xFFE5E5EA),
    surfaceBright = Color(0xFFFFFFFF)
)

private val AppleClassicalColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD0E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF0284C7),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1EEFB),
    onSecondaryContainer = Color(0xFF0B192C),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF001F2A),
    background = Color(0xFFEBF4FE),
    onBackground = Color(0xFF0B192C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B192C),
    surfaceContainer = Color(0xFFE1EEFB),
    surfaceContainerLow = Color(0xFFF0F7FF),
    surfaceContainerHigh = Color(0xFFD4E6F8),
    surfaceContainerHighest = Color(0xFFC5DCF4),
    surfaceVariant = Color(0xFFDDEAF8),
    onSurfaceVariant = Color(0xFF4A607C),
    surfaceDim = Color(0xFFD4E6F8),
    surfaceBright = Color(0xFFFFFFFF)
)

private val MidnightLavenderColorScheme = darkColorScheme(
    primary = Color(0xFFC7B9E8),
    onPrimary = Color(0xFF1B1826),
    primaryContainer = Color(0xFF332E42),
    onPrimaryContainer = Color(0xFFDDD2F6),
    secondary = Color(0xFFA59FC1),
    onSecondary = Color(0xFF1B1826),
    secondaryContainer = Color(0xFF25272D),
    onSecondaryContainer = Color(0xFFF4F1EA),
    tertiary = Color(0xFFDDD6FE),
    onTertiary = Color(0xFF1B1826),
    background = Color(0xFF111214),
    onBackground = Color(0xFFF4F1EA),
    surface = Color(0xFF1E2024),
    onSurface = Color(0xFFF4F1EA),
    surfaceContainer = Color(0xFF25272D),
    surfaceContainerLow = Color(0xFF18191C),
    surfaceContainerHigh = Color(0xFF2C2F36),
    surfaceContainerHighest = Color(0xFF343840),
    surfaceVariant = Color(0xFF25272D),
    onSurfaceVariant = Color(0xFFA5A6A9),
    surfaceDim = Color(0xFF0D0E10),
    surfaceBright = Color(0xFF343840)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicPlayerTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val isTv = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

    val isDark = when (appTheme) {
        AppTheme.DARK -> true
        AppTheme.LIGHT -> false
        AppTheme.MODERN_EDITORIAL -> true
        AppTheme.NORDIC_SLATE -> true
        AppTheme.APPLE_MUSIC -> false
        AppTheme.APPLE_CLASSICAL -> false
        AppTheme.MIDNIGHT_LAVENDER -> true
        AppTheme.SYSTEM -> darkTheme
    }

    println("Setting theme $appTheme dark:${isDark} for ${if (isTv) "tv" else "phone"}")

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    if (isTv) {
        val colorScheme = androidx.tv.material3.darkColorScheme()

        println("using dark theme for tv")

        androidx.tv.material3.MaterialTheme(
            colorScheme = colorScheme,
            typography = androidx.tv.material3.Typography(),
        ) {
            androidx.compose.material3.MaterialTheme(
                colorScheme = DarkColorScheme,
                content = content
            )
        }
    } else {
        val colorScheme = when (appTheme) {
            AppTheme.MODERN_EDITORIAL -> ModernEditorialColorScheme
            AppTheme.NORDIC_SLATE -> NordicSlateColorScheme
            AppTheme.APPLE_MUSIC -> AppleMusicColorScheme
            AppTheme.APPLE_CLASSICAL -> AppleClassicalColorScheme
            AppTheme.MIDNIGHT_LAVENDER -> MidnightLavenderColorScheme
            AppTheme.DARK -> DarkColorScheme
            AppTheme.LIGHT -> LightColorScheme
            AppTheme.SYSTEM -> {
                val baseColorScheme = when {
                    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                        val context = LocalContext.current
                        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    }
                    darkTheme -> DarkColorScheme
                    else -> LightColorScheme
                }
                if (darkTheme) {
                    baseColorScheme.copy(
                        background = DarkGreyBackground,
                        surface = DarkGreySurface,
                        surfaceContainer = DarkGreySurfaceContainer,
                        surfaceContainerLow = DarkGreySurfaceContainerLow,
                        surfaceContainerHigh = DarkGreySurfaceContainerHigh,
                        surfaceContainerHighest = DarkGreySurfaceContainerHighest,
                        surfaceVariant = DarkGreySurfaceVariant,
                        surfaceDim = Color(0xFF191A1D),
                        surfaceBright = Color(0xFF383A40),
                        onBackground = Color(0xFFE3E5E8),
                        onSurface = Color(0xFFE3E5E8),
                        onSurfaceVariant = Color(0xFFC4C7C5)
                    )
                } else {
                    baseColorScheme
                }
            }
        }

        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
            motionScheme = MotionScheme.expressive()
        )
    }
}