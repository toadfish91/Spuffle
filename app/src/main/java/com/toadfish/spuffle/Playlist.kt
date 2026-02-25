package com.toadfish.spuffle

data class Playlist(
    val id: String,          // Spotify playlist ID, or "liked_songs" for Liked Songs
    val name: String,
    val trackCount: Int,
    val imageUrl: String?,
    val isLikedSongs: Boolean = false
)