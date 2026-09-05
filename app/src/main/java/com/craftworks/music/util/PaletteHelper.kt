package com.craftworks.music.util

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PaletteHelper {
    suspend fun extractColorsFromUri(uri: String?, context: Context): List<Color> = withContext(Dispatchers.IO) {
        if (uri.isNullOrBlank()) return@withContext emptyList()
        try {
            val loader = context.imageLoader
            val request = ImageRequest.Builder(context)
                .data(uri.replace(Regex("size=\\d+"), "size=64"))
                .allowHardware(false)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()

            val result = (loader.execute(request) as? SuccessResult)?.drawable
            val bitmap = result?.toBitmap() ?: return@withContext emptyList()

            val palette = Palette.Builder(bitmap).generate()

            val vividSwatches = palette.swatches
                .filter { it.hsl[1] >= 0.30f && it.hsl[2] in 0.12f..0.88f }
                .sortedByDescending { it.hsl[1] }

            val primaryAccentSwatch = vividSwatches.firstOrNull()
                ?: palette.vibrantSwatch
                ?: palette.dominantSwatch
                ?: palette.swatches.maxByOrNull { it.population }

            val secondarySwatch = vividSwatches.getOrNull(1)
                ?: palette.darkVibrantSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.mutedSwatch

            val tertiarySwatch = palette.darkVibrantSwatch
                ?: palette.mutedSwatch
                ?: palette.darkMutedSwatch

            val orderedSwatches = listOfNotNull(
                primaryAccentSwatch,
                secondarySwatch.takeIf { it != primaryAccentSwatch },
                tertiarySwatch.takeIf { it != primaryAccentSwatch && it != secondarySwatch },
                palette.dominantSwatch.takeIf { it != primaryAccentSwatch && it != secondarySwatch }
            )

            orderedSwatches.map { Color(it.rgb) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

@Composable
fun AmbientGradientBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val primaryColor = colors.firstOrNull()
    val secondaryColor = colors.getOrNull(1) ?: primaryColor
    val tertiaryColor = colors.getOrNull(2) ?: secondaryColor ?: primaryColor
    val backgroundColor = MaterialTheme.colorScheme.background
    val themePrimary = MaterialTheme.colorScheme.primary
    val themeTertiary = MaterialTheme.colorScheme.tertiary

    val animatedPrimary by animateColorAsState(
        targetValue = primaryColor ?: Color.Transparent,
        animationSpec = tween(durationMillis = 800),
        label = "ambientPrimary"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = secondaryColor ?: Color.Transparent,
        animationSpec = tween(durationMillis = 800),
        label = "ambientSecondary"
    )
    val animatedTertiary by animateColorAsState(
        targetValue = tertiaryColor ?: Color.Transparent,
        animationSpec = tween(durationMillis = 800),
        label = "ambientTertiary"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .drawWithCache {
                onDrawBehind {
                    if (animatedPrimary != Color.Transparent) {
                        // 1. Full-screen rich base gradient from primary down to tertiary
                        drawRect(
                            Brush.verticalGradient(
                                colors = listOf(
                                    animatedPrimary.copy(alpha = 0.75f),
                                    animatedSecondary.copy(alpha = 0.55f),
                                    animatedTertiary.copy(alpha = 0.40f)
                                ),
                                startY = 0f,
                                endY = size.height
                            )
                        )

                        // 2. Top-right intense color bloom
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(
                                    animatedPrimary.copy(alpha = 0.80f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 1.05f, size.height * 0.12f),
                                radius = size.width * 1.6f
                            )
                        )

                        // 3. Middle-left accent glow
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(
                                    animatedSecondary.copy(alpha = 0.65f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * -0.1f, size.height * 0.45f),
                                radius = size.width * 1.4f
                            )
                        )

                        // 4. Bottom ambient glow
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(
                                    animatedTertiary.copy(alpha = 0.50f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.5f, size.height * 0.95f),
                                radius = size.height * 0.9f
                            )
                        )

                        // 5. Dark translucent scrim to guarantee crisp text readability and rich deep dark saturation
                        drawRect(color = Color.Black.copy(alpha = 0.35f))
                    } else {
                        // When no artwork color is available, use the subtle grey gradient
                        val isDark = backgroundColor.luminance() < 0.5f

                        val topSlate = if (isDark) Color(0xFF1F2228) else Color(0xFFF1F3F7)
                        val midCharcoal = if (isDark) Color(0xFF17181C) else Color(0xFFE6E9EE)
                        val bottomPitch = if (isDark) Color(0xFF101114) else Color(0xFFDCDEE4)

                        drawRect(
                            Brush.verticalGradient(
                                colors = listOf(topSlate, midCharcoal, bottomPitch)
                            )
                        )
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(themePrimary.copy(alpha = if (isDark) 0.12f else 0.07f), Color.Transparent),
                                center = Offset(size.width * 0.90f, size.height * 0.08f),
                                radius = size.width * 1.5f
                            )
                        )
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(themeTertiary.copy(alpha = if (isDark) 0.07f else 0.04f), Color.Transparent),
                                center = Offset(size.width * 0.10f, size.height * 0.22f),
                                radius = size.width * 1.2f
                            )
                        )
                    }
                }
            }
    ) {
        content()
    }
}

@Composable
fun SubtleAppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val backgroundColor = MaterialTheme.colorScheme.background
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTv = (configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    val isDark = isTv || (backgroundColor.luminance() < 0.5f)

    val topColor = if (isDark) backgroundColor else backgroundColor
    val midColor = if (isDark) {
        Color(
            red = (backgroundColor.red * 0.90f).coerceIn(0f, 1f),
            green = (backgroundColor.green * 0.90f).coerceIn(0f, 1f),
            blue = (backgroundColor.blue * 0.90f).coerceIn(0f, 1f)
        )
    } else {
        Color(
            red = (backgroundColor.red * 0.97f).coerceIn(0f, 1f),
            green = (backgroundColor.green * 0.97f).coerceIn(0f, 1f),
            blue = (backgroundColor.blue * 0.97f).coerceIn(0f, 1f)
        )
    }
    val bottomColor = if (isDark) {
        Color(
            red = (backgroundColor.red * 0.80f).coerceIn(0f, 1f),
            green = (backgroundColor.green * 0.80f).coerceIn(0f, 1f),
            blue = (backgroundColor.blue * 0.80f).coerceIn(0f, 1f)
        )
    } else {
        Color(
            red = (backgroundColor.red * 0.94f).coerceIn(0f, 1f),
            green = (backgroundColor.green * 0.94f).coerceIn(0f, 1f),
            blue = (backgroundColor.blue * 0.94f).coerceIn(0f, 1f)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        topColor,
                        midColor,
                        bottomColor
                    )
                )
            )
            .drawWithCache {
                onDrawBehind {
                    if (!isTv) {
                        // Subtle primary accent ambient glow at top right
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(
                                    primary.copy(alpha = if (isDark) 0.12f else 0.07f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.90f, size.height * 0.08f),
                                radius = size.width * 1.5f
                            )
                        )
                        // Ultra subtle tertiary accent glow at top left
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(
                                    tertiary.copy(alpha = if (isDark) 0.07f else 0.04f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.10f, size.height * 0.22f),
                                radius = size.width * 1.2f
                            )
                        )
                    }
                }
            }
    ) {
        content()
    }
}
