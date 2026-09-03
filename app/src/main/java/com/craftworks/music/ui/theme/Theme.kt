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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val isTv = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

    println("Setting theme dark:${darkTheme} for ${if (isTv) "tv" else "phone"}")

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
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
    }
    else {
        val baseColorScheme = when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

        val colorScheme = if (darkTheme) {
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

        MaterialExpressiveTheme (
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
            motionScheme = MotionScheme.expressive()
        )
    }
}