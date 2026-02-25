package com.toadfish.spuffle

import android.content.Context
import android.content.SharedPreferences

object PlaylistCache {

    private const val PREFS_NAME = "spuffle_cache"
    private const val LIKED_SONGS_CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Playlist snapshot caching ---

    fun getSnapshotId(context: Context, playlistId: String): String? =
        prefs(context).getString("snapshot_$playlistId", null)

    fun getCachedTracks(context: Context, playlistId: String): List<String>? {
        val raw = prefs(context).getString("tracks_$playlistId", null) ?: return null
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun savePlaylistCache(context: Context, playlistId: String, snapshotId: String, tracks: List<String>) {
        prefs(context).edit().apply {
            putString("snapshot_$playlistId", snapshotId)
            putString("tracks_$playlistId", tracks.joinToString(","))
            apply()
        }
    }

    // --- Liked Songs timestamp caching ---

    fun getLikedSongsCacheAge(context: Context): Long {
        val savedAt = prefs(context).getLong("liked_songs_saved_at", 0L)
        return System.currentTimeMillis() - savedAt
    }

    fun isLikedSongsCacheValid(context: Context): Boolean =
        getLikedSongsCacheAge(context) < LIKED_SONGS_CACHE_DURATION_MS

    fun getCachedLikedSongs(context: Context): List<String>? =
        getCachedTracks(context, "liked_songs")

    fun saveLikedSongsCache(context: Context, tracks: List<String>) {
        prefs(context).edit().apply {
            putString("tracks_liked_songs", tracks.joinToString(","))
            putLong("liked_songs_saved_at", System.currentTimeMillis())
            apply()
        }
    }

    fun clearCache(context: Context) {
        prefs(context).edit().clear().apply()
    }

    // --- Last used playlist ---

    fun saveLastPlaylist(context: Context, playlist: Playlist) {
        prefs(context).edit().apply {
            putString("last_playlist_id", playlist.id)
            putString("last_playlist_name", playlist.name)
            putInt("last_playlist_track_count", playlist.trackCount)
            putBoolean("last_playlist_is_liked_songs", playlist.isLikedSongs)
            apply()
        }
    }

    fun getLastPlaylist(context: Context): Playlist? {
        val id = prefs(context).getString("last_playlist_id", null) ?: return null
        return Playlist(
            id = id,
            name = prefs(context).getString("last_playlist_name", "Last Playlist") ?: "Last Playlist",
            trackCount = prefs(context).getInt("last_playlist_track_count", 0),
            imageUrl = null,
            isLikedSongs = prefs(context).getBoolean("last_playlist_is_liked_songs", false)
        )
    }
}