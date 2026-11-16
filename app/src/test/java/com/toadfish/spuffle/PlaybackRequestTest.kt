package com.toadfish.spuffle

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRequestTest {

    @Test
    fun startingAtFirstExplicitlyStartsAtTheFirstTrack() {
        val request = PlaybackRequest.startingAtFirst(
            listOf("spotify:track:first", "spotify:track:second")
        )

        val gson = Gson()
        val json = gson.fromJson(gson.toJson(request), JsonObject::class.java)

        assertEquals("spotify:track:first", json.getAsJsonArray("uris").get(0).getAsString())
        assertEquals(
            "spotify:track:first",
            json.getAsJsonObject("offset").get("uri").getAsString()
        )
        assertEquals(0, json.get("position_ms").getAsInt())
    }
}
