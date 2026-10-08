package com.toadfish.spuffle

import android.content.Context
import android.content.SharedPreferences

object PlaylistCache {

    private const val PREFS_NAME = "spuffle_cache"
    private const val LIKED_SONGS_CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours
    private const val ENTRY_SEPARATOR = "||"
    private const val FIELD_SEPARATOR = "\u0001"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Playlist snapshot caching ---

    fun getSnapshotId(context: Context, playlistId: String): String? =
        prefs(context).getString("snapshot_$playlistId", null)

    fun getCachedTracks(context: Context, playlistId: String): List<String>? =
        getCachedTrackDetails(context, playlistId)?.map { it.uri }

    fun getCachedTrackDetails(context: Context, playlistId: String): List<Track>? {
        val raw = prefs(context).getString("tracks_$playlistId", null) ?: return null

        if (!raw.contains(FIELD_SEPARATOR) && !raw.contains(ENTRY_SEPARATOR)) {
            return null
        }

        return raw.split(ENTRY_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val parts = entry.split(FIELD_SEPARATOR)
                if (parts.size < 3 || parts[0].isBlank()) return@mapNotNull null
                Track(
                    uri = parts[0],
                    name = parts[1],
                    artists = parts[2].split(",").filter { it.isNotBlank() }.map { Artist(it) }
                )
            }
    }

    fun savePlaylistCache(context: Context, playlistId: String, snapshotId: String, tracks: List<Track>) {
        prefs(context).edit().apply {
            putString("snapshot_$playlistId", snapshotId)
            putString("tracks_$playlistId", encodeTracks(tracks))
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

    fun getCachedLikedSongs(context: Context): List<Track>? =
        getCachedTrackDetails(context, "liked_songs")

    fun saveLikedSongsCache(context: Context, tracks: List<Track>) {
        prefs(context).edit().apply {
            putString("tracks_liked_songs", encodeTracks(tracks))
            putLong("liked_songs_saved_at", System.currentTimeMillis())
            apply()
        }
    }

    private fun encodeTracks(tracks: List<Track>): String =
        tracks.joinToString(ENTRY_SEPARATOR) { track ->
            val artistNames = track.artists.joinToString(",") { it.name }
            listOf(track.uri, track.name, artistNames).joinToString(FIELD_SEPARATOR)
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