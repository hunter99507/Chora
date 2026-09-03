package com.craftworks.music.data.datasource.emby

import android.annotation.SuppressLint
import android.util.Log
import androidx.media3.common.MediaItem
import com.craftworks.music.data.EmbyJellyfinLibrary
import com.craftworks.music.data.EmbyJellyfinProvider
import com.craftworks.music.data.model.MediaData
import com.craftworks.music.managers.EmbyJellyfinManager
import com.craftworks.music.providers.emby.EmbyAuthRequest
import com.craftworks.music.providers.emby.EmbyAuthResponse
import com.craftworks.music.providers.emby.EmbyItem
import com.craftworks.music.providers.emby.EmbyItemsResponse
import com.craftworks.music.providers.emby.toAlbumMediaItem
import com.craftworks.music.providers.emby.toArtistData
import com.craftworks.music.providers.emby.toPlaylistData
import com.craftworks.music.providers.emby.toSongMediaItem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Singleton
class EmbyJellyfinDataSource @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }

    private val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpCache)
            install(Logging) {
                level = LogLevel.INFO
                logger = Logger.SIMPLE
            }
        }
    }

    private val insecureClient: HttpClient by lazy { buildInsecureClient() }

    private fun buildInsecureClient(): HttpClient {
        val trustAllCerts = arrayOf<TrustManager>(
            @SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                @SuppressLint("TrustAllX509TrustManager")
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                @SuppressLint("TrustAllX509TrustManager")
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        )

        return HttpClient(OkHttp) {
            engine {
                config {
                    sslSocketFactory(
                        javax.net.ssl.SSLContext.getInstance("TLS").apply {
                            init(null, trustAllCerts, java.security.SecureRandom())
                        }.socketFactory,
                        trustAllCerts[0] as X509TrustManager
                    )
                    hostnameVerifier { _, _ -> true }
                }
            }
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpCache)
            install(Logging) {
                level = LogLevel.INFO
                logger = Logger.SIMPLE
            }
        }
    }

    private fun getClient(allowSelfSigned: Boolean = false): HttpClient {
        return if (allowSelfSigned) insecureClient else client
    }

    private fun getAuthHeader(token: String? = null): String {
        val base = "MediaBrowser Client=\"Chora\", Device=\"Android\", DeviceId=\"chora-android-app\", Version=\"1.31.1\""
        return if (!token.isNullOrBlank()) "$base, Token=\"$token\"" else base
    }

    suspend fun authenticate(
        serverUrl: String,
        username: String,
        password: String,
        allowSelfSignedCert: Boolean = false
    ): EmbyAuthResponse? = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val effectiveClient = getClient(allowSelfSignedCert)

        val endpoints = listOf(
            "$cleanUrl/Users/AuthenticateByName",
            "$cleanUrl/emby/Users/AuthenticateByName"
        )

        for (endpoint in endpoints) {
            try {
                val response = effectiveClient.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    headers {
                        append("X-Emby-Authorization", getAuthHeader())
                        append("Accept", "application/json")
                    }
                    setBody(EmbyAuthRequest(username, password))
                }

                if (response.status == HttpStatusCode.OK) {
                    val responseText = response.bodyAsText()
                    val auth = json.decodeFromString<EmbyAuthResponse>(responseText)
                    if (auth.accessToken != null) {
                        return@withContext auth
                    }
                }
            } catch (e: Exception) {
                Log.w("EMBY_JELLYFIN", "Auth failed at $endpoint: ${e.message}")
            }
        }
        null
    }

    private suspend fun ensureAuthenticated(server: EmbyJellyfinProvider): EmbyJellyfinProvider? {
        if (!server.token.isNullOrBlank() && !server.userId.isNullOrBlank()) {
            return server
        }
        val auth = authenticate(server.url, server.username, server.password, server.allowSelfSignedCert == true)
        if (auth?.accessToken != null && auth.user?.id != null) {
            server.token = auth.accessToken
            server.userId = auth.user.id
            server.serverId = auth.serverId
            return server
        }
        return null
    }

    suspend fun getLibraries(targetServer: EmbyJellyfinProvider? = null): List<EmbyJellyfinLibrary> = withContext(Dispatchers.IO) {
        val server = targetServer ?: EmbyJellyfinManager.getCurrentServer() ?: return@withContext emptyList()
        val authServer = ensureAuthenticated(server) ?: return@withContext emptyList()
        val cleanUrl = authServer.url.trimEnd('/')
        val token = authServer.token ?: ""
        val userId = authServer.userId ?: ""

        val endpoints = listOf(
            "$cleanUrl/Users/$userId/Views",
            "$cleanUrl/emby/Users/$userId/Views"
        )

        for (endpoint in endpoints) {
            try {
                val response = getClient(authServer.allowSelfSignedCert == true).get(endpoint) {
                    headers {
                        append("X-Emby-Authorization", getAuthHeader(token))
                        append("X-Emby-Token", token)
                        append("Accept", "application/json")
                    }
                }
                if (response.status == HttpStatusCode.OK) {
                    val itemsResponse = json.decodeFromString<EmbyItemsResponse>(response.bodyAsText())
                    val libraries = itemsResponse.items
                        .filter { it.collectionType == "music" || it.collectionType == null || it.type == "CollectionFolder" }
                        .map { EmbyJellyfinLibrary(it.id, it.name) }
                    if (libraries.isNotEmpty()) return@withContext libraries
                }
            } catch (e: Exception) {
                Log.w("EMBY_JELLYFIN", "Failed to fetch views: ${e.message}")
            }
        }
        emptyList()
    }

    suspend fun getViews(targetServer: EmbyJellyfinProvider? = null): List<EmbyItem> = withContext(Dispatchers.IO) {
        val server = targetServer ?: EmbyJellyfinManager.getCurrentServer() ?: return@withContext emptyList()
        val authServer = ensureAuthenticated(server) ?: return@withContext emptyList()
        val cleanUrl = authServer.url.trimEnd('/')
        val token = authServer.token ?: ""
        val userId = authServer.userId ?: ""

        val endpoints = listOf(
            "$cleanUrl/Users/$userId/Views",
            "$cleanUrl/emby/Users/$userId/Views"
        )

        for (endpoint in endpoints) {
            try {
                val response = getClient(authServer.allowSelfSignedCert == true).get(endpoint) {
                    headers {
                        append("X-Emby-Authorization", getAuthHeader(token))
                        append("X-Emby-Token", token)
                        append("Accept", "application/json")
                    }
                }
                if (response.status == HttpStatusCode.OK) {
                    val itemsResponse = json.decodeFromString<EmbyItemsResponse>(response.bodyAsText())
                    if (itemsResponse.items.isNotEmpty()) return@withContext itemsResponse.items
                }
            } catch (e: Exception) {
                Log.w("EMBY_JELLYFIN", "Failed to fetch views: ${e.message}")
            }
        }
        emptyList()
    }

    suspend fun getSongs(
        query: String? = "",
        count: Int = 100,
        offset: Int = 0,
        ignoreCachedResponse: Boolean = false,
        favoritesOnly: Boolean = false
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = EmbyJellyfinManager.getCurrentServer() ?: return@withContext emptyList()
        val authServer = ensureAuthenticated(server) ?: return@withContext emptyList()
        val cleanUrl = authServer.url.trimEnd('/')
        val token = authServer.token ?: ""
        val userId = authServer.userId ?: ""
        val parentIds = EmbyJellyfinManager.getEnabledLibraryIdsForCurrentServer()?.joinToString(",") ?: ""

        val queryParams = mutableListOf(
            "IncludeItemTypes=Audio",
            "Recursive=true",
            "Fields=ItemCounts,PrimaryImageAspectRatio,MediaSources,SortName,Overview,ArtistItems,AlbumArtist,AlbumId,Album,Genres,RunTimeTicks,IndexNumber,ParentIndexNumber,ProductionYear,DateCreated,UserData,Path",
            "Limit=$count",
            "StartIndex=$offset",
            "SortBy=SortName",
            "SortOrder=Ascending"
        )
        if (favoritesOnly) queryParams.add("Filters=IsFavorite")
        if (!query.isNullOrBlank()) queryParams.add("SearchTerm=$query")
        if (parentIds.isNotBlank()) queryParams.add("ParentId=$parentIds")

        val url = "$cleanUrl/Users/$userId/Items?" + queryParams.joinToString("&")
        try {
            val response = getClient(authServer.allowSelfSignedCert == true).get(url) {
                headers {
                    append("X-Emby-Authorization", getAuthHeader(token))
                    append("X-Emby-Token", token)
                    if (ignoreCachedResponse) append("Cache-Control", "no-cache")
                }
            }
            if (response.status == HttpStatusCode.OK) {
                val data = json.decodeFromString<EmbyItemsResponse>(response.bodyAsText())
                return@withContext data.items.map { it.toSongMediaItem(cleanUrl, token) }
            }
        } catch (e: Exception) {
            Log.e("EMBY_JELLYFIN", "Error getting songs: ${e.message}")
        }
        emptyList()
    }

    suspend fun getSong(songId: String, ignoreCachedResponse: Boolean = false): MediaItem? = withContext(Dispatchers.IO) {
        val cleanSongId = songId.removePrefix("emby_")
        val server = EmbyJellyfinManager.getCurrentServer() ?: return@withContext null
        val authServer = ensureAuthenticated(server) ?: return@withContext null
        val cleanUrl = authServer.url.trimEnd('/')
        val token = authServer.token ?: ""
        val userId = authServer.userId ?: ""

        val url = "$cleanUrl/Users/$userId/Items/$cleanSongId"
        try {
            val response = getClient(authServer.allowSelfSignedCert == true).get(url) {
                headers {
                    append("X-Emby-Authorization", getAuthHeader(token))
                    append("X-Emby-Token", token)
                    if (ignoreCachedResponse) append("Cache-Control", "no-cache")
                }
            }
            if (response.status == HttpStatusCode.OK) {
                val item = json.decodeFromString<EmbyItem>(response.bodyAsText())
                return@withContext item.toSongMediaItem(cleanUrl, token)
            }
        } catch (e: Exception) {
            Log.e("EMBY_JELLYFIN", "Error getting song $cleanSongId: ${e.message}")
        }
        null
    }

    suspend fun getAlbums(
        sort: String? = "alphabeticalByName",
        size: Int? = 100,
        offset: Int? = 0,
        ignoreCachedResponse: Boolean = false,
        favoritesOnly: Boolean = false,
        query: String? = null
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = EmbyJellyfinManager.getCurrentServer() ?: return@withContext emptyList()
        val authServer = ensureAuthenticated(server) ?: return@withContext emptyList()
        val cleanUrl = authServer.url.trimEnd('/')
        val token = authServer.token ?: ""
        val userId = authServer.userId ?: ""
        val parentIds = EmbyJellyfinManager.getEnabledLibraryIdsForCurrentServer()?.joinToString(",") ?: ""

        val sortBy = when (sort) {
            "newest", "recentlyAdded" -> "DateCreated"
            "frequent", "mostPlayed" -> "PlayCount"
            "recent", "recentlyPlayed" -> "DatePlayed"
            "random" -> "Random"
            else -> "SortName"
        }
        val sortOrder = if (sortBy == "SortName") "Ascending" else "Descending"

        val queryParams = mutableListOf(
            "IncludeItemTypes=MusicAlbum",
            "Recursive=true",
            "Fields=ItemCounts,PrimaryImageAspectRatio,Overview,ArtistItems,AlbumArtist,Genres,ProductionYear,DateCreated,UserData",
            "Limit=${size ?: 100}",
            "StartIndex=${offset ?: 0}",
            "SortBy=$sortBy",
            "SortOrder=$sortOrder"
        )
        if (!query.isNullOrBlank()) queryParams.add("SearchTerm=$query")
        if (favoritesOnly) queryParams.add("Filters=IsFavorite")
        if (parentIds.isNotBlank()) queryParams.add("ParentId=$parentIds")

        val url = "$cleanUrl/Users/$userId/Items?" + queryParams.joinToString("&")
        try {
            val response = getClient(authServer.allowSelfSignedCert == true).get(url) {
                headers {
                    append("X-Emby-Authorization", getAuthHeader(token))
                    append("X-Emby-Token", token)
                    if (ignoreCachedResponse) append("Cache-Control", "no-cache")
                }
            }
            if (response.status == HttpStatusCode.OK) {
                val data = json.decodeFromString<EmbyItemsResponse>(response.bodyAsText())
                return@withContext data.items.map { it.toAlbumMediaItem(cleanUrl, token) }
            }
        } catch (e: Exception) {
            Log.e("EMBY_JELLYFIN", "Error getting albums: ${e.message}")
        }
        emptyList()
    }

    suspend fun getAlbum(albumId: String, ignoreCachedResponse: Boolean = false): List<MediaItem>? = withContext(Dispatchers.IO) {
        val cleanAlbumId = albumId.removePrefix("emby_").removePrefix("folder_album_emby_").removePrefix("folder_album_")
        val server = EmbyJellyfinManager.getCurrentServer() ?: return@withContext null
        val authServer = ensureAuthenticated(server) ?: return@withContext null
        val cleanUrl = authServer.url.trimEnd('/')
        val token = authServer.token ?: ""
        val userId = authServer.userId ?: ""

        val result = mutableListOf<MediaItem>()
        try {
            // 1. Fetch Album metadata
            val albumUrl = "$cleanUrl/Users/$userId/Items/$cleanAlbumId"
            val albumResp = getClient(authServer.allowSelfSignedCert == true).get(albumUrl) {
                headers {
                    append("X-Emby-Authorization", getAuthHeader(token))
                    append("X-Emby-Token", token)
                }
            }
            if (albumResp.status == HttpStatusCode.OK) {
                val albumItem = json.decodeFromString<EmbyItem>(albumResp.bodyAsText())
                result.add(albumItem.toAlbumMediaItem(cleanUrl, token))
            }

            // 2. Fetch Album Songs
            val songsUrl = "$cleanUrl/Users/$userId/Items?ParentId=$cleanAlbumId&IncludeItemTypes=Audio&Fields=ItemCounts,PrimaryImageAspectRatio,MediaSources,SortName,Overview,ArtistItems,AlbumArtist,AlbumId,Album,Genres,RunTimeTicks,IndexNumber,ParentIndexNumber,ProductionYear,DateCreated,UserData,Path&SortBy=ParentIndexNumber,IndexNumber,SortName&SortOrder=Ascending"
            val songsResp = getClient(authServer.allowSelfSignedCert == true).get(songsUrl) {
                headers {
                    append("X-Emby-Authorization", getAuthHeader(token))
                    append("X-Emby-Token", token)
                }
            }
            if (songsResp.status == HttpStatusCode.OK) {
                val songsData = json.decodeFromString<EmbyItemsResponse>(songsResp.bodyAsText())
                result.addAll(songsData.items.map { it.toSongMediaItem(cleanUrl, token) })
            }
            return@withContext if (result.isNotEmpty()) result else null
        } catch (e: Exception) {
            Log.e("EMBY_JELLYFIN", "Error getting album $cleanAlbumId: ${e.message}")
        }
        null
    }

    suspend fun getArtists(size: Int = 100, offset: Int = 0, ignoreCachedResponse: Boolean = false, searchTerm: String? = null): List<MediaData.Artist> = withContext(Dispatchers.IO) {
        val server = EmbyJellyfinManager.getCurrentServer() ?: return@withContext emptyList()
        val authServer = ensureAuthenticated(server) ?: return@withContext emptyList()
        val cleanUrl = authServer.url.trimEnd('/')
        val token = authServer.token ?: ""
        val userId = authServer.userId ?: ""
        val parentIds = EmbyJellyfinManager.getEnabledLibraryIdsForCurrentServer()?.joinToString(",") ?: ""

        val queryParams = mutableListOf(
            "Recursive=true",
            "Fields=Overview,PrimaryImageAspectRatio",
            "Limit=$size",
            "StartIndex=$offset",
            "SortBy=SortName",
            "SortOrder=Ascending"
        )
        if (!searchTerm.isNullOrBlank()) queryParams.add("SearchTerm=$searchTerm")
        if (parentIds.isNotBlank()) queryParams.add("ParentId=$parentIds")

        val url = "$cleanUrl/Artists/AlbumArtists?" + queryParams.joinToString("&")
        try {
            val response = getClient(authServer.allowSelfSignedCert == true).get(url) {
                headers {
                    append("X-Emby-Authorization", getAuthHeader(token))
                    append("X-Emby-Token", token)
                }
            }
            if (response.status == HttpStatusCode.OK) {
                val data = json.decodeFromString<EmbyItemsResponse>(response.bodyAsText())
                return@withContext data.items.map { it.toArtistData(cleanUrl, token) }
            }
        } catch (e: Exception) {
            Log.e("EMBY_JELLYFIN", "Error getting artists: ${e.message}")
        }
        emptyList()
    }

    suspend fun getArtistAlbums(artistId: String, ignoreCachedResponse: Boolean = false): List<MediaItem> = withContext(Dispatchers.IO) {
        val cleanArtistId = artistId.removePrefix("emby_")
        val server = EmbyJellyfinManager.getCurrentServer() ?: return@withContext emptyList()
        val authServer = ensureAuthenticated(server) ?: return@withContext emptyList()
        val cleanUrl = authServer.url.trimEnd('/')
        val token = authServer.token ?: ""
        val userId = authServer.userId ?: ""

        val url = "$cleanUrl/Users/$userId/Items?IncludeItemTypes=MusicAlbum&Recursive=true&ArtistIds=$cleanArtistId&Fields=ItemCounts,PrimaryImageAspectRatio,Overview,ArtistItems,AlbumArtist,Genres,ProductionYear,DateCreated,UserData&SortBy=ProductionYear,SortName&SortOrder=Descending"
        try {
            val response = getClient(authServer.allowSelfSignedCert == true).get(url) {
                headers {
                    append("X-Emby-Authorization", getAuthHeader(token))
                    append("X-Emby-Token", token)
                }
            }
            if (response.status == HttpStatusCode.OK) {
                val data = json.decodeFromString<EmbyItemsResponse>(response.bodyAsText())
                return@withContext data.items.map { it.toAlbumMediaItem(cleanUrl, token) }
            }
        } catch (e: Exception) {
            Log.e("EMBY_JELLYFIN", "Error getting artist albums: ${e.message}")
        }
        emptyList()
    }

    suspend fun getArtistBiography(artistId: String): MediaData.ArtistInfo = withContext(Dispatchers.IO) {
        val cleanArtistId = artistId.removePrefix("emby_")
        val server = EmbyJellyfinManager.getCurrentServer() ?: return@withContext MediaData.ArtistInfo()
        val authServer = ensureAuthenticated(server) ?: return@withContext MediaData.ArtistInfo()
        val cleanUrl = authServer.url.trimEnd('/')
        val token = authServer.token ?: ""
        val userId = authServer.userId ?: ""

        val url = "$cleanUrl/Users/$userId/Items/$cleanArtistId"
        try {
            val response = getClient(authServer.allowSelfSignedCert == true).get(url) {
                headers {
                    append("X-Emby-Authorization", getAuthHeader(token))
                    append("X-Emby-Token", token)
                }
            }
            if (response.status == HttpStatusCode.OK) {
                val item = json.decodeFromString<EmbyItem>(response.bodyAsText())
                return@withContext MediaData.ArtistInfo(biography = item.overview ?: "")
            }
        } catch (e: Exception) {
            Log.e("EMBY_JELLYFIN", "Error getting artist biography: ${e.message}")
        }
        MediaData.ArtistInfo()
    }

    suspend fun getPlaylists(): List<MediaData.Playlist> = withContext(Dispatchers.IO) {
        val server = EmbyJellyfinManager.getCurrentServer() ?: return@withContext emptyList()
        val authServer = ensureAuthenticated(server) ?: return@withContext emptyList()
        val cleanUrl = authServer.url.trimEnd('/')
        val token = authServer.token ?: ""
        val userId = authServer.userId ?: ""

        val url = "$cleanUrl/Users/$userId/Items?IncludeItemTypes=Playlist&Recursive=true"
        try {
            val response = getClient(authServer.allowSelfSignedCert == true).get(url) {
                headers {
                    append("X-Emby-Authorization", getAuthHeader(token))
                    append("X-Emby-Token", token)
                }
            }
            if (response.status == HttpStatusCode.OK) {
                val data = json.decodeFromString<EmbyItemsResponse>(response.bodyAsText())
                return@withContext data.items.map { it.toPlaylistData(cleanUrl, token) }
            }
        } catch (e: Exception) {
            Log.e("EMBY_JELLYFIN", "Error getting playlists: ${e.message}")
        }
        emptyList()
    }

    suspend fun getPlaylist(playlistId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val cleanPlaylistId = playlistId.removePrefix("emby_")
        val server = EmbyJellyfinManager.getCurrentServer() ?: return@withContext emptyList()
        val authServer = ensureAuthenticated(server) ?: return@withContext emptyList()
        val cleanUrl = authServer.url.trimEnd('/')
        val token = authServer.token ?: ""
        val userId = authServer.userId ?: ""

        val url = "$cleanUrl/Playlists/$cleanPlaylistId/Items?UserId=$userId&Fields=ItemCounts,PrimaryImageAspectRatio,MediaSources,SortName,Overview,ArtistItems,AlbumArtist,AlbumId,Album,Genres,RunTimeTicks,IndexNumber,ParentIndexNumber,ProductionYear,DateCreated,UserData,Path"
        try {
            val response = getClient(authServer.allowSelfSignedCert == true).get(url) {
                headers {
                    append("X-Emby-Authorization", getAuthHeader(token))
                    append("X-Emby-Token", token)
                }
            }
            if (response.status == HttpStatusCode.OK) {
                val data = json.decodeFromString<EmbyItemsResponse>(response.bodyAsText())
                return@withContext data.items.map { it.toSongMediaItem(cleanUrl, token) }
            }
        } catch (e: Exception) {
            Log.e("EMBY_JELLYFIN", "Error getting playlist items: ${e.message}")
        }
        emptyList()
    }

    suspend fun getStarredSongs(): List<MediaItem> = getSongs(favoritesOnly = true)
    suspend fun getStarredAlbums(): List<MediaItem> = getAlbums(favoritesOnly = true)

    suspend fun getStarredArtists(): List<MediaData.Artist> = withContext(Dispatchers.IO) {
        val server = EmbyJellyfinManager.getCurrentServer() ?: return@withContext emptyList()
        val authServer = ensureAuthenticated(server) ?: return@withContext emptyList()
        val cleanUrl = authServer.url.trimEnd('/')
        val token = authServer.token ?: ""
        val userId = authServer.userId ?: ""

        val url = "$cleanUrl/Artists/AlbumArtists?Recursive=true&Filters=IsFavorite&Fields=Overview,PrimaryImageAspectRatio"
        try {
            val response = getClient(authServer.allowSelfSignedCert == true).get(url) {
                headers {
                    append("X-Emby-Authorization", getAuthHeader(token))
                    append("X-Emby-Token", token)
                }
            }
            if (response.status == HttpStatusCode.OK) {
                val data = json.decodeFromString<EmbyItemsResponse>(response.bodyAsText())
                return@withContext data.items.map { it.toArtistData(cleanUrl, token) }
            }
        } catch (e: Exception) {
            Log.e("EMBY_JELLYFIN", "Error getting starred artists: ${e.message}")
        }
        emptyList()
    }

    suspend fun starItem(itemId: String, star: Boolean) = withContext(Dispatchers.IO) {
        val cleanId = itemId.removePrefix("emby_").removePrefix("folder_album_emby_")
        val server = EmbyJellyfinManager.getCurrentServer() ?: return@withContext
        val authServer = ensureAuthenticated(server) ?: return@withContext
        val cleanUrl = authServer.url.trimEnd('/')
        val token = authServer.token ?: ""
        val userId = authServer.userId ?: ""

        val url = "$cleanUrl/Users/$userId/FavoriteItems/$cleanId"
        try {
            if (star) {
                getClient(authServer.allowSelfSignedCert == true).post(url) {
                    headers {
                        append("X-Emby-Authorization", getAuthHeader(token))
                        append("X-Emby-Token", token)
                    }
                }
            } else {
                getClient(authServer.allowSelfSignedCert == true).delete(url) {
                    headers {
                        append("X-Emby-Authorization", getAuthHeader(token))
                        append("X-Emby-Token", token)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("EMBY_JELLYFIN", "Error toggling favorite for $cleanId: ${e.message}")
        }
    }

    suspend fun scrobbleSong(songId: String, submission: Boolean) = withContext(Dispatchers.IO) {
        val cleanSongId = songId.removePrefix("emby_")
        val server = EmbyJellyfinManager.getCurrentServer() ?: return@withContext
        val authServer = ensureAuthenticated(server) ?: return@withContext
        val cleanUrl = authServer.url.trimEnd('/')
        val token = authServer.token ?: ""

        val endpoint = if (submission) "$cleanUrl/Sessions/Playing/Stopped" else "$cleanUrl/Sessions/Playing"
        try {
            getClient(authServer.allowSelfSignedCert == true).post(endpoint) {
                contentType(ContentType.Application.Json)
                headers {
                    append("X-Emby-Authorization", getAuthHeader(token))
                    append("X-Emby-Token", token)
                }
                setBody("{\"ItemId\":\"$cleanSongId\"}")
            }
        } catch (e: Exception) {
            Log.e("EMBY_JELLYFIN", "Error scrobbling song: ${e.message}")
        }
    }

    suspend fun getSimilarSongs(songId: String, count: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        val cleanSongId = songId.removePrefix("emby_")
        val server = EmbyJellyfinManager.getCurrentServer() ?: return@withContext emptyList()
        val authServer = ensureAuthenticated(server) ?: return@withContext emptyList()
        val cleanUrl = authServer.url.trimEnd('/')
        val token = authServer.token ?: ""
        val userId = authServer.userId ?: ""

        val url = "$cleanUrl/Items/$cleanSongId/InstantMix?UserId=$userId&Limit=$count"
        try {
            val response = getClient(authServer.allowSelfSignedCert == true).get(url) {
                headers {
                    append("X-Emby-Authorization", getAuthHeader(token))
                    append("X-Emby-Token", token)
                }
            }
            if (response.status == HttpStatusCode.OK) {
                val data = json.decodeFromString<EmbyItemsResponse>(response.bodyAsText())
                return@withContext data.items.map { it.toSongMediaItem(cleanUrl, token) }
            }
        } catch (e: Exception) {
            Log.e("EMBY_JELLYFIN", "Error getting instant mix: ${e.message}")
        }
        emptyList()
    }
}
