package com.craftworks.music.ui.playing

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.craftworks.music.data.repository.LyricsRepository
import com.craftworks.music.data.repository.SongRepository
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.managers.settings.PlaybackSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class NowPlayingViewModel @Inject constructor (
    @ApplicationContext private val context: Context,
    val songRepository: SongRepository,
    val lyricsRepository: LyricsRepository,
    val appearanceSettingsManager: AppearanceSettingsManager,
    val playbackSettingsManager: PlaybackSettingsManager,
) : ViewModel() {
    private val _lyricsOpen = MutableStateFlow(false)
    val lyricsOpen = _lyricsOpen.asStateFlow()

    private val _playQueueOpen = MutableStateFlow(false)
    val playQueueOpen = _playQueueOpen.asStateFlow()

    private val _detailsOpen = MutableStateFlow(false)
    val detailsOpen = _detailsOpen.asStateFlow()

    private val _sleepTimerDialogOpen = MutableStateFlow(false)
    val sleepTimerDialogOpen = _sleepTimerDialogOpen.asStateFlow()

    fun setLyricsOpen(open: Boolean) { _lyricsOpen.value = open }
    fun setPlayQueueOpen(open: Boolean) { _playQueueOpen.value = open }
    fun setDetailsOpen(open: Boolean) { _detailsOpen.value = open }
    fun setSleepTimerDialogOpen(open: Boolean) { _sleepTimerDialogOpen.value = open }

    val backgroundStyle = appearanceSettingsManager.npBackgroundFlow
    val oledProtectionMode = appearanceSettingsManager.oledProtectionMode

    private val _paletteColors = MutableStateFlow<List<Color>>(emptyList())
    val paletteColors = _paletteColors.asStateFlow()

    private val _iconTextColor = MutableStateFlow<Color>(Color.White)
    val iconTextColor = _iconTextColor.asStateFlow()

    private val _isBackgroundDark = MutableStateFlow(false)
    val isBackgroundDark = _isBackgroundDark.asStateFlow()

    private val _meta = MutableStateFlow(MediaItem.EMPTY)
    val metadata = _meta.asStateFlow()

    val autoPlay = playbackSettingsManager.autoPlayFlow
    fun setAutoPlay(autoPlay: Boolean) =
        viewModelScope.launch {
            playbackSettingsManager.setAutoPlay(autoPlay)
        }

    fun refreshLyrics(mediaMetadata: MediaMetadata?) {
        viewModelScope.launch {
            lyricsRepository.getLyrics(mediaMetadata, true)
        }
    }

    fun updatePaletteFromUri(uri: Uri?, currentBackgroundStyle: NowPlayingBackground, isSystemDark: Boolean) {
        if (currentBackgroundStyle == NowPlayingBackground.PLAIN) {
            _paletteColors.value = emptyList()
            _isBackgroundDark.value = isSystemDark
            _iconTextColor.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (_isBackgroundDark.value) dynamicDarkColorScheme(context).onBackground
                else dynamicLightColorScheme(context).onBackground
            } else {
                if (_isBackgroundDark.value) Color.White
                else Color.Black
            }

            return
        }

        if (uri == null) return

        viewModelScope.launch {
            val palette = extractColorsFromUri(uri.toString(), context)
            val extractedColors = palette.filterNotNull()
            _paletteColors.value = extractedColors

            val averageLuminance = extractedColors.map { ColorUtils.calculateLuminance(it.toArgb()) }.average()
            _isBackgroundDark.value = averageLuminance <= 0.5f

            _iconTextColor.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (_isBackgroundDark.value) dynamicDarkColorScheme(context).onBackground
                else dynamicLightColorScheme(context).onBackground
            } else {
                if (_isBackgroundDark.value) Color.White
                else Color.Black
            }
        }
    }

    private suspend fun extractColorsFromUri(uri: String, context: Context): List<Color?> = coroutineScope {
        val loader = context.imageLoader
        val request = ImageRequest.Builder(context)
            .data(uri.replace(Regex("size=\\d+"), "size=64"))
            .allowHardware(false)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()

        val result = (loader.execute(request) as? SuccessResult)?.drawable
        val bitmap = result?.toBitmap()

        bitmap?.let { bitmapImage ->
            withContext(Dispatchers.Default) {
                val palette = Palette.Builder(bitmapImage).generate()

                // Find swatches with rich saturation that aren't near-black or near-white
                val vividSwatches = palette.swatches
                    .filter { it.hsl[1] >= 0.35f && it.hsl[2] in 0.12f..0.88f }
                    .sortedByDescending { it.hsl[1] }

                val primaryAccentSwatch = vividSwatches.firstOrNull() ?: palette.vibrantSwatch ?: palette.dominantSwatch

                val orderedSwatches = listOfNotNull(
                    primaryAccentSwatch,
                    palette.vibrantSwatch.takeIf { it != primaryAccentSwatch },
                    palette.darkVibrantSwatch.takeIf { it != primaryAccentSwatch },
                    palette.dominantSwatch.takeIf { it != primaryAccentSwatch },
                    palette.lightVibrantSwatch.takeIf { it != primaryAccentSwatch },
                    palette.mutedSwatch.takeIf { it != primaryAccentSwatch },
                    palette.lightMutedSwatch.takeIf { it != primaryAccentSwatch }
                )

                orderedSwatches.map { Color(it.rgb) }
            }
        } ?: listOf()
    }
}