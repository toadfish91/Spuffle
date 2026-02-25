package com.toadfish.spuffle

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class SpuffleState {
    object Idle : SpuffleState()
    object Loading : SpuffleState()
    data class FetchingTracks(val fetched: Int, val total: Int) : SpuffleState()
    data class PlaylistsLoaded(val playlists: List<Playlist>) : SpuffleState()
    data class Success(val message: String) : SpuffleState()
    data class Error(val message: String) : SpuffleState()
}

class SpuffleViewModel : ViewModel() {

    private val _state = MutableStateFlow<SpuffleState>(SpuffleState.Idle)
    val state: StateFlow<SpuffleState> = _state

    companion object {
        private const val BATCH_SIZE = 10         // requests per batch
        private const val BATCH_DELAY_MS = 200L   // ms between batches
    }

    fun handleAuthCode(context: Context, code: String) {
        viewModelScope.launch {
            val verifier = SpotifyAuthManager.getCodeVerifier(context) ?: run {
                _state.value = SpuffleState.Error("Missing code verifier. Please try logging in again.")
                return@launch
            }
            val response = RetrofitClient.accountsService.getToken(
                grantType = "authorization_code",
                code = code,
                redirectUri = Constants.REDIRECT_URI,
                clientId = Constants.CLIENT_ID,
                codeVerifier = verifier
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                SpotifyAuthManager.saveTokens(
                    context,
                    body.access_token,
                    body.refresh_token ?: "",
                    body.expires_in
                )
                _state.value = SpuffleState.Success("Logged in! Tap Spuffle to get started.")
            } else {
                _state.value = SpuffleState.Error("Auth failed: ${response.code()}")
            }
        }
    }

    /**
     * Fetches all playlists and emits them for the picker screen.
     */
    fun loadPlaylists(context: Context) {
        viewModelScope.launch {
            _state.value = SpuffleState.Loading

            val token = getValidToken(context) ?: run {
                _state.value = SpuffleState.Error("Not logged in.")
                return@launch
            }
            val auth = "Bearer $token"

            // Fetch all playlist pages
            val allPlaylists = mutableListOf<Playlist>()

            // Add Liked Songs as the first entry always
            val likedSongsCount = getLikedSongsCount(auth)
            allPlaylists.add(
                Playlist(
                    id = Constants.LIKED_SONGS_ID,
                    name = "Liked Songs",
                    trackCount = likedSongsCount,
                    imageUrl = null,
                    isLikedSongs = true
                )
            )
            android.util.Log.d("Spuffle", "Liked Songs count: $likedSongsCount")
// Fetch user playlists in pages
            var offset = 0
            var total = Int.MAX_VALUE

            do {
                val response = RetrofitClient.apiService.getUserPlaylists(auth, limit = 50, offset = offset)

                when {
                    response.code() == 429 -> {
                        val retryAfter = response.headers()["Retry-After"]?.toLongOrNull() ?: 60
                        val waitMinutes = (retryAfter / 60) + 1
                        _state.value = SpuffleState.Error(
                            "Spotify is rate limiting requests. Please wait about $waitMinutes minutes and try again."
                        )
                        return@launch
                    }
                    !response.isSuccessful -> {
                        _state.value = SpuffleState.Error("Failed to load playlists: ${response.code()}")
                        return@launch
                    }
                }

                val body = response.body() ?: break
                total = body.total

                body.items.forEach { item ->
                    val blockedNames = setOf("starred", "liked from radio", "windows media player")
                    if (item.name.lowercase() in blockedNames) return@forEach

                    val cachedTracks = PlaylistCache.getCachedTracks(context, item.id)
                    val trackCount = item.tracks?.total ?: cachedTracks?.size ?: 0

                    allPlaylists.add(
                        Playlist(
                            id = item.id,
                            name = item.name,
                            trackCount = trackCount,
                            imageUrl = item.images?.firstOrNull()?.url
                        )
                    )
                }

                offset += 50
            } while (offset < total)

            _state.value = SpuffleState.PlaylistsLoaded(allPlaylists)
        }
    }

    /**
     * Spuffles from whichever playlist was selected.
     */
    fun spuffle(context: Context, playlist: Playlist) {
        viewModelScope.launch {
            _state.value = SpuffleState.Loading

            PlaylistCache.saveLastPlaylist(context, playlist)

            val token = getValidToken(context) ?: run {
                _state.value = SpuffleState.Error("Not logged in.")
                return@launch
            }
            val auth = "Bearer $token"

            val allTracks = if (playlist.isLikedSongs) {
                getLikedSongsWithCache(context, auth, playlist.trackCount)
            } else {
                getPlaylistTracksWithCache(context, auth, playlist)
            }

            if (allTracks == null) return@launch

            if (allTracks.isEmpty()) {
                _state.value = SpuffleState.Error("This playlist appears to be empty.")
                return@launch
            }

            val shuffleCount = minOf(Constants.SHUFFLE_COUNT, allTracks.size)
            val randomTracks = allTracks.shuffled().take(shuffleCount)

            val playResponse = RetrofitClient.apiService.startPlayback(
                auth = auth,
                request = PlaybackRequest(uris = randomTracks)
            )

            if (playResponse.isSuccessful || playResponse.code() == 204) {
                _state.value = SpuffleState.Success(
                    "🎵 Playing $shuffleCount random tracks from \"${playlist.name}\" (${allTracks.size} total songs)"
                )
                SpuffleWidget.updateAllWidgets(context)
            } else {
                val err = playResponse.errorBody()?.string()
                _state.value = SpuffleState.Error(
                    "Playback failed (${playResponse.code()}): $err\n\nMake sure Spotify is open on your device."
                )
            }
        }
    }
    // --- Private helpers ---
    private suspend fun getLikedSongsWithCache(context: Context, auth: String, total: Int): List<String>? {
        // Check if we have a valid cache
        if (PlaylistCache.isLikedSongsCacheValid(context)) {
            val cached = PlaylistCache.getCachedLikedSongs(context)
            if (!cached.isNullOrEmpty()) {
                val ageMinutes = PlaylistCache.getLikedSongsCacheAge(context) / 60_000
                android.util.Log.d("Spuffle", "Using cached Liked Songs (${cached.size} tracks, ${ageMinutes}m old)")
                _state.value = SpuffleState.FetchingTracks(cached.size, cached.size)
                return cached
            }
        }

        // Cache miss or expired — fetch fresh
        android.util.Log.d("Spuffle", "Liked Songs cache miss or expired, fetching fresh")
        val tracks = fetchAllLikedSongs(auth, total) ?: return null

        // Save to cache
        PlaylistCache.saveLikedSongsCache(context, tracks)
        return tracks
    }

    private suspend fun getPlaylistTracksWithCache(context: Context, auth: String, playlist: Playlist): List<String>? {
        // Fetch current snapshot_id (lightweight single call)
        val snapshotResponse = RetrofitClient.apiService.getPlaylistSnapshot(auth, playlist.id)
        val currentSnapshotId = snapshotResponse.body()?.snapshot_id

        if (currentSnapshotId != null) {
            val cachedSnapshotId = PlaylistCache.getSnapshotId(context, playlist.id)
            if (currentSnapshotId == cachedSnapshotId) {
                val cached = PlaylistCache.getCachedTracks(context, playlist.id)
                if (!cached.isNullOrEmpty()) {
                    android.util.Log.d("Spuffle", "Cache hit for '${playlist.name}' (snapshot match, ${cached.size} tracks)")
                    _state.value = SpuffleState.FetchingTracks(cached.size, cached.size)
                    return cached
                }
            }
        }

        // Cache miss or snapshot changed — fetch fresh
        android.util.Log.d("Spuffle", "Cache miss for '${playlist.name}', fetching fresh")
        val tracks = fetchAllPlaylistTracks(auth, playlist.id, playlist.trackCount) ?: return null

        // Save to cache with new snapshot ID
        if (currentSnapshotId != null) {
            PlaylistCache.savePlaylistCache(context, playlist.id, currentSnapshotId, tracks)
        }

        return tracks
    }


    private suspend fun getLikedSongsCount(auth: String): Int {
        val response = RetrofitClient.apiService.getSavedTracks(auth, limit = 1, offset = 0)
        return response.body()?.total ?: 0
    }

    private suspend fun fetchAllLikedSongs(auth: String, total: Int): List<String>? {
        // If total is 0, attempt to re-fetch the real count rather than giving up
        val actualTotal = if (total > 0) total else {
            val response = RetrofitClient.apiService.getSavedTracks(auth, limit = 1, offset = 0)
            android.util.Log.d("Spuffle", "Re-fetching liked songs total: ${response.body()?.total}")
            response.body()?.total ?: 0
        }

        if (actualTotal == 0) {
            _state.value = SpuffleState.Error("Could not determine Liked Songs count. Make sure you're logged in and try again.")
            return null
        }

        _state.value = SpuffleState.FetchingTracks(0, actualTotal)
        return try {
            val pageCount = (actualTotal + Constants.TRACKS_PER_PAGE - 1) / Constants.TRACKS_PER_PAGE
            val allTracks = mutableListOf<String>()

            // Process in batches to avoid rate limiting
            (0 until pageCount).chunked(BATCH_SIZE).forEach { batch ->
                val deferredPages = batch.map { page ->
                    viewModelScope.async {
                        RetrofitClient.apiService.getSavedTracks(
                            auth,
                            limit = Constants.TRACKS_PER_PAGE,
                            offset = page * Constants.TRACKS_PER_PAGE
                        )
                    }
                }
                val responses = deferredPages.awaitAll()
                for (response in responses) {
                    if (!response.isSuccessful) {
                        if (response.code() == 429) {
                            val retryAfter = response.headers()["Retry-After"]?.toLongOrNull() ?: 0
                            val waitMinutes = retryAfter / 60
                            _state.value = SpuffleState.Error(
                                "Spotify has temporarily blocked requests from this app due to too many calls. " +
                                        "Please wait about $waitMinutes minutes before trying again."
                            )
                        } else {
                            _state.value = SpuffleState.Error("Request failed: ${response.code()}")
                        }
                        return null
                    }
                    allTracks.addAll(response.body()?.items?.map { it.track.uri } ?: emptyList())
                }
                _state.value = SpuffleState.FetchingTracks(minOf(allTracks.size, actualTotal), actualTotal)

                // Small pause between batches
                kotlinx.coroutines.delay(BATCH_DELAY_MS)
            }
            allTracks
        } catch (e: Exception) {
            _state.value = SpuffleState.Error("Network error: ${e.message}")
            null
        }
    }
    private suspend fun fetchAllPlaylistTracks(auth: String, playlistId: String, total: Int): List<String>? {
        return try {
            val firstResponse = RetrofitClient.apiService.getPlaylist(auth, playlistId, offset = 0, limit = 50)
            if (!firstResponse.isSuccessful) {
                _state.value = SpuffleState.Error("Failed to access playlist (${firstResponse.code()}).")
                return null
            }

            val firstPage = firstResponse.body()?.items ?: run {
                _state.value = SpuffleState.Error("Playlist returned no data.")
                return null
            }

            val actualTotal = firstPage.total
            if (actualTotal == 0) {
                _state.value = SpuffleState.Error("This playlist appears to be empty.")
                return null
            }

            _state.value = SpuffleState.FetchingTracks(firstPage.items.size, actualTotal)

            val allTracks = mutableListOf<String>()
            allTracks.addAll(firstPage.items.mapNotNull { it.track?.uri ?: it.item?.uri })

            if (actualTotal > 50) {
                val pageCount = (actualTotal + 49) / 50

                // Process in batches
                (1 until pageCount).chunked(BATCH_SIZE).forEach { batch ->
                    val deferredPages = batch.map { page ->
                        viewModelScope.async {
                            RetrofitClient.apiService.getPlaylist(
                                auth,
                                playlistId,
                                offset = page * 50,
                                limit = 50
                            )
                        }
                    }
                    val responses = deferredPages.awaitAll()
                    for (response in responses) {
                        if (!response.isSuccessful) {
                            if (response.code() == 429) {
                                val retryAfter = response.headers()["Retry-After"]?.toLongOrNull() ?: 0
                                val waitMinutes = retryAfter / 60
                                _state.value = SpuffleState.Error(
                                    "Spotify has temporarily blocked requests from this app due to too many calls. " +
                                            "Please wait about $waitMinutes minutes before trying again."
                                )
                            } else {
                                _state.value = SpuffleState.Error("Request failed: ${response.code()}")
                            }
                            return null
                        }
                        val uris = response.body()?.items?.items?.mapNotNull { it.track?.uri ?: it.item?.uri } ?: emptyList()
                        allTracks.addAll(uris)
                    }
                    _state.value = SpuffleState.FetchingTracks(minOf(allTracks.size, actualTotal), actualTotal)
                    kotlinx.coroutines.delay(BATCH_DELAY_MS)
                }
            }
            allTracks
        } catch (e: Exception) {
            _state.value = SpuffleState.Error("Network error: ${e.message}")
            null
        }
    }
    private suspend fun getValidToken(context: Context): String? {
        if (SpotifyAuthManager.isTokenExpired(context)) {
            val refresh = SpotifyAuthManager.getRefreshToken(context) ?: return null
            val response = RetrofitClient.accountsService.refreshToken(
                refreshToken = refresh,
                clientId = Constants.CLIENT_ID
            )
            val body = response.body() ?: return null
            SpotifyAuthManager.saveTokens(
                context,
                body.access_token,
                body.refresh_token ?: refresh,
                body.expires_in
            )
        }
        return SpotifyAuthManager.getAccessToken(context)
    }
}