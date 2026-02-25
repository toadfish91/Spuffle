package com.toadfish.spuffle

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WidgetSpuffleService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                runSpuffle()
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runSpuffle() {
        val context = applicationContext

        // Check login
        if (!SpotifyAuthManager.isLoggedIn(context)) {
            showWidgetError("Open Spuffle app to log in")
            return
        }

        // Get last used playlist
        val playlist = PlaylistCache.getLastPlaylist(context) ?: run {
            showWidgetError("Open app and Spuffle first")
            return
        }

        showWidgetStatus("Shuffling ${playlist.name}...")

        // Get a valid token
        val token = getValidToken(context) ?: run {
            showWidgetError("Login expired — open app")
            return
        }
        val auth = "Bearer $token"

        // Get tracks — from cache only, no full fetch from widget
        // If cache is empty user needs to open the app first
        val allTracks = if (playlist.isLikedSongs) {
            PlaylistCache.getCachedLikedSongs(context)
        } else {
            PlaylistCache.getCachedTracks(context, playlist.id)
        }

        if (allTracks.isNullOrEmpty()) {
            showWidgetError("Open app to load tracks first")
            return
        }

        // Shuffle and play
        val shuffleCount = minOf(Constants.SHUFFLE_COUNT, allTracks.size)
        val randomTracks = allTracks.shuffled().take(shuffleCount)

        val response = RetrofitClient.apiService.startPlayback(
            auth = auth,
            request = PlaybackRequest(uris = randomTracks)
        )

        if (response.isSuccessful || response.code() == 204) {
            showWidgetStatus("Playing ${playlist.name} ✓")
            // Reset display after 3 seconds
            kotlinx.coroutines.delay(3000)
            showWidgetStatus("${playlist.trackCount} songs")
        } else {
            showWidgetError("Open Spotify first")
        }
    }

    private fun showWidgetStatus(message: String) {
        updateWidgetText(subtitle = message)
    }

    private fun showWidgetError(message: String) {
        updateWidgetText(subtitle = message)
    }

    private fun updateWidgetText(subtitle: String) {
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, SpuffleWidget::class.java))
        val playlist = PlaylistCache.getLastPlaylist(this)

        for (id in ids) {
            val views = RemoteViews(packageName, R.layout.widget_spuffle)
            views.setTextViewText(R.id.widgetTitle, playlist?.name ?: "Spuffle")
            views.setTextViewText(R.id.widgetSubtitle, subtitle)
            manager.updateAppWidget(id, views)
        }
    }

    private suspend fun getValidToken(context: android.content.Context): String? {
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

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}