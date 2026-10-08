package com.toadfish.spuffle

import retrofit2.Response
import retrofit2.http.*

// --- Data Models ---

data class TokenResponse(
    val access_token: String,
    val refresh_token: String?,
    val expires_in: Int,
    val token_type: String
)

data class SavedTracksResponse(
    val total: Int,
    val items: List<SavedTrackItem>,
    val next: String?
)

data class SavedTrackItem(
    val track: Track
)

data class Track(
    val uri: String,
    val name: String,
    val artists: List<Artist>
)

data class QueuedTrack(
    val uri: String,
    val title: String,
    val artist: String
) {
    val displayTitle: String
        get() = title.ifBlank { uri }

    val displayArtist: String
        get() = artist.ifBlank { "Spotify track" }
}

data class Artist(val name: String)

data class PlaybackRequest(
    val uris: List<String>
)

data class UserPlaylistsResponse(
    val total: Int,
    val items: List<PlaylistItem>,
    val next: String?
)

data class PlaylistItem(
    val id: String,
    val name: String,
    val tracks: PlaylistTrackCount?,  // nullable
    val images: List<SpotifyImage>?
)

data class PlaylistTrackCount(val total: Int)

data class SpotifyImage(val url: String)

data class PlaylistTracksResponse(
    val total: Int,
    val items: List<PlaylistTrackItem>,
    val next: String?
)

data class PlaylistTrackItem(
    val track: Track? = null,  // used by /me/tracks
    val item: Track? = null    // used by /playlists/{id}
)

data class FullPlaylistResponse(
    val id: String,
    val name: String,
    val items: PlaylistTracksPage?
)

data class PlaylistTracksPage(
    val total: Int,
    val items: List<PlaylistTrackItem>,
    val next: String?,
    val limit: Int,
    val offset: Int
)

data class PlaylistSnapshotResponse(
    val snapshot_id: String
)

// --- Spotify Accounts API (for tokens) ---

interface SpotifyAccountsService {
    @FormUrlEncoded
    @POST("token")
    suspend fun getToken(
        @Field("grant_type") grantType: String,
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("client_id") clientId: String,
        @Field("code_verifier") codeVerifier: String
    ): Response<TokenResponse>

    @FormUrlEncoded
    @POST("token")
    suspend fun refreshToken(
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String
    ): Response<TokenResponse>
}

// --- Spotify Web API ---

interface SpotifyApiService {
    @GET("me/tracks")
    suspend fun getSavedTracks(
        @Header("Authorization") auth: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<SavedTracksResponse>

    @PUT("me/player/play")
    suspend fun startPlayback(
        @Header("Authorization") auth: String,
        @Body request: PlaybackRequest,
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>

    @PUT("me/player/shuffle")
    suspend fun setShuffleMode(
        @Header("Authorization") auth: String,
        @Query("state") state: Boolean,
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>

    @GET("me/playlists")
    suspend fun getUserPlaylists(
        @Header("Authorization") auth: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<UserPlaylistsResponse>

    @GET("playlists/{playlist_id}/tracks")
    suspend fun getPlaylistTracks(
        @Header("Authorization") auth: String,
        @Path("playlist_id") playlistId: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String = "total,items(track(uri,name,artists))"
    ): Response<PlaylistTracksResponse>

    @GET("playlists/{playlist_id}")
    suspend fun getPlaylist(
        @Header("Authorization") auth: String,
        @Path("playlist_id") playlistId: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 50
    ): Response<FullPlaylistResponse>

    @GET("playlists/{playlist_id}")
    suspend fun getPlaylistSnapshot(
        @Header("Authorization") auth: String,
        @Path("playlist_id") playlistId: String,
        @Query("fields") fields: String = "snapshot_id"
    ): Response<PlaylistSnapshotResponse>
}