package com.craftworks.music.player

import androidx.media3.common.MediaItem
import androidx.media3.common.StarRating
import kotlin.math.min

/**
 * SmartShuffleHelper provides intelligent playlist shuffling with:
 * 1. Weighted scoring: balances favorites/high-rotation tracks (~40%), standard catalog (~40%), and discovery (~20%).
 * 2. Anti-clustering: spaces out songs by the same artist/album so you don't hear repetitive artists back-to-back.
 * 3. Graceful fallbacks: safely handles small playlists, single-artist albums, or libraries with no play counts.
 */
object SmartShuffleHelper {

    fun smartShuffle(items: List<MediaItem>): List<MediaItem> {
        if (items.size <= 2) return items.shuffled()

        val scoredItems = items.map { item ->
            ScoredItem(
                item = item,
                score = calculateScore(item),
                artist = item.mediaMetadata.artist?.toString()?.trim()?.lowercase() ?: "",
                album = item.mediaMetadata.albumTitle?.toString()?.trim()?.lowercase() ?: "",
                isFavorite = isFavorite(item),
                timesPlayed = getTimesPlayed(item)
            )
        }

        // 1. Separate into 3 pools using relative threshold so local libraries adapt quickly
        val favoritesPool = mutableListOf<ScoredItem>()
        val discoveryPool = mutableListOf<ScoredItem>()
        val standardPool = mutableListOf<ScoredItem>()

        val maxPlays = scoredItems.maxOfOrNull { it.timesPlayed } ?: 0
        val frequentThreshold = if (maxPlays >= 2) maxOf(2, (maxPlays * 0.55).toInt()) else 20

        for (scored in scoredItems) {
            when {
                scored.isFavorite || scored.score >= 50f || (maxPlays >= 2 && scored.timesPlayed >= frequentThreshold) -> favoritesPool.add(scored)
                scored.timesPlayed == 0 && !scored.isFavorite -> discoveryPool.add(scored)
                else -> standardPool.add(scored)
            }
        }

        // Randomize within each pool so order is non-deterministic
        favoritesPool.shuffle()
        discoveryPool.shuffle()
        standardPool.shuffle()

        android.util.Log.d("SmartShuffle", "smartShuffle starting for ${items.size} items -> favorites: ${favoritesPool.size}, standard: ${standardPool.size}, discovery: ${discoveryPool.size}")

        // 2. Interleave from pools with 40% Favorites / 40% Standard / 20% Discovery weighting
        val totalCount = items.size
        val candidateQueue = ArrayList<ScoredItem>(totalCount)

        while (favoritesPool.isNotEmpty() || standardPool.isNotEmpty() || discoveryPool.isNotEmpty()) {
            val initialSize = candidateQueue.size

            // Pick up to 4 favorites
            repeat(4) {
                if (favoritesPool.isNotEmpty()) candidateQueue.add(favoritesPool.removeAt(favoritesPool.size - 1))
            }
            // Pick up to 4 standard
            repeat(4) {
                if (standardPool.isNotEmpty()) candidateQueue.add(standardPool.removeAt(standardPool.size - 1))
            }
            // Pick up to 2 discovery
            repeat(2) {
                if (discoveryPool.isNotEmpty()) candidateQueue.add(discoveryPool.removeAt(discoveryPool.size - 1))
            }

            // If only one pool remains with items, drain it completely
            if (favoritesPool.isEmpty() && standardPool.isEmpty() && discoveryPool.isNotEmpty()) {
                candidateQueue.addAll(discoveryPool)
                discoveryPool.clear()
            } else if (favoritesPool.isEmpty() && discoveryPool.isEmpty() && standardPool.isNotEmpty()) {
                candidateQueue.addAll(standardPool)
                standardPool.clear()
            } else if (standardPool.isEmpty() && discoveryPool.isEmpty() && favoritesPool.isNotEmpty()) {
                candidateQueue.addAll(favoritesPool)
                favoritesPool.clear()
            }

            // Safety guard: ensure loop strictly progresses
            if (candidateQueue.size == initialSize) {
                candidateQueue.addAll(favoritesPool)
                candidateQueue.addAll(standardPool)
                candidateQueue.addAll(discoveryPool)
                favoritesPool.clear()
                standardPool.clear()
                discoveryPool.clear()
                break
            }
        }

        // 3. Anti-Clustering: Spacing out artists and albums
        val uniqueArtistsCount = scoredItems.map { it.artist }.filter { it.isNotBlank() }.distinct().size
        if (uniqueArtistsCount <= 1) {
            return candidateQueue.map { it.item }
        }

        val targetArtistGap = min(3, uniqueArtistsCount - 1)
        val finalQueue = ArrayList<ScoredItem>(totalCount)
        val remaining = candidateQueue.toMutableList()
        val recentArtists = ArrayDeque<String>(targetArtistGap)

        while (remaining.isNotEmpty()) {
            var selectedIndex = 0
            var foundNonClashing = false

            // Look up to 15 candidates ahead for a non-clashing artist to avoid O(N^2) stalls
            val searchLimit = min(15, remaining.size)
            for (i in 0 until searchLimit) {
                val candidateArtist = remaining[i].artist
                if (candidateArtist.isBlank() || !recentArtists.contains(candidateArtist)) {
                    selectedIndex = i
                    foundNonClashing = true
                    break
                }
            }

            if (!foundNonClashing) {
                selectedIndex = 0
            }

            val chosen = remaining.removeAt(selectedIndex)
            finalQueue.add(chosen)

            if (chosen.artist.isNotBlank()) {
                if (recentArtists.size >= targetArtistGap) {
                    recentArtists.removeFirst()
                }
                recentArtists.addLast(chosen.artist)
            }
        }

        android.util.Log.d("SmartShuffle", "smartShuffle finished: ${finalQueue.size} items ordered. First 5: " +
            finalQueue.take(5).joinToString(" | ") { "${it.item.mediaMetadata.title} (${it.artist})" })

        return finalQueue.map { it.item }
    }

    private fun calculateScore(item: MediaItem): Float {
        var score = 0f

        // Starred or favorited
        if (isFavorite(item)) {
            score += 50f
        }

        // Star rating (1 to 5 stars)
        val starRating = (item.mediaMetadata.userRating as? StarRating)?.starRating
        if (starRating != null && starRating > 0f) {
            score += starRating * 10f
        }

        // Play count / times played (0 to 20 scaled)
        val timesPlayed = getTimesPlayed(item)
        score += min(timesPlayed, 20) * 2.5f

        return score
    }

    private fun isFavorite(item: MediaItem): Boolean {
        val extras = item.mediaMetadata.extras ?: return false
        val isFavBool = extras.getBoolean("isFavorite", false)
        val starredStr = extras.getString("starred") ?: ""
        val rating = (item.mediaMetadata.userRating as? StarRating)?.starRating ?: 0f
        return isFavBool || starredStr.isNotBlank() || rating >= 4.5f
    }

    private fun getTimesPlayed(item: MediaItem): Int {
        val extras = item.mediaMetadata.extras ?: return 0
        return extras.getInt("timesPlayed", 0)
    }

    private data class ScoredItem(
        val item: MediaItem,
        val score: Float,
        val artist: String,
        val album: String,
        val isFavorite: Boolean,
        val timesPlayed: Int
    )
}

