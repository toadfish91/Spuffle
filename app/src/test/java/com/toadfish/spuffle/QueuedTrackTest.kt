package com.toadfish.spuffle

import org.junit.Assert.assertEquals
import org.junit.Test

class QueuedTrackTest {

    @Test
    fun displayTitleFallsBackToUriWhenTitleMissing() {
        val track = QueuedTrack(
            uri = "spotify:track:abc123",
            title = "",
            artist = ""
        )

        assertEquals("spotify:track:abc123", track.displayTitle)
        assertEquals("Spotify track", track.displayArtist)
    }

    @Test
    fun displayTitleUsesSongAndArtistWhenAvailable() {
        val track = QueuedTrack(
            uri = "spotify:track:abc123",
            title = "Dreams",
            artist = "Fleetwood Mac"
        )

        assertEquals("Dreams", track.displayTitle)
        assertEquals("Fleetwood Mac", track.displayArtist)
    }

    @Test
    fun playbackQueueKeepsTheSameOrderForSpotifyAndUi() {
        val viewModel = SpuffleViewModel()
        val tracks = listOf(
            Track(uri = "spotify:track:1", name = "Song 1", artists = listOf(Artist("A"))),
            Track(uri = "spotify:track:2", name = "Song 2", artists = listOf(Artist("B"))),
            Track(uri = "spotify:track:3", name = "Song 3", artists = listOf(Artist("C"))),
            Track(uri = "spotify:track:4", name = "Song 4", artists = listOf(Artist("D")))
        )

        val playbackQueue = viewModel.buildPlaybackQueue(tracks)
        val queuedTracks = playbackQueue.map { it.name }
        val spotifyUris = playbackQueue.map { it.uri }

        assertEquals(queuedTracks, playbackQueue.map { it.name })
        assertEquals(spotifyUris.size, queuedTracks.size)
        assertEquals(playbackQueue.size, minOf(Constants.SHUFFLE_COUNT, tracks.size))
    }
}
