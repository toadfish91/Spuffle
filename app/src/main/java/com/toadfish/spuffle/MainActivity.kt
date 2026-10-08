package com.toadfish.spuffle

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.toadfish.spuffle.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: SpuffleViewModel by viewModels()

    // Launcher that waits for PlaylistActivity to return a selection
    private val playlistLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val playlist = Playlist(
                id = data.getStringExtra("playlist_id") ?: return@registerForActivityResult,
                name = data.getStringExtra("playlist_name") ?: "Unknown",
                trackCount = data.getIntExtra("playlist_track_count", 0),
                imageUrl = null,
                isLikedSongs = data.getBooleanExtra("playlist_is_liked_songs", false)
            )
            viewModel.spuffle(this, playlist)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val initialLeftPadding = binding.root.paddingLeft
        val initialTopPadding = binding.root.paddingTop
        val initialRightPadding = binding.root.paddingRight
        val initialBottomPadding = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialLeftPadding + systemBars.left,
                initialTopPadding + systemBars.top,
                initialRightPadding + systemBars.right,
                initialBottomPadding + systemBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        binding.rvQueuedTracks.layoutManager = LinearLayoutManager(this)

        setupUI()
        observeState()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        val code = intent.getStringExtra("auth_code")
        val error = intent.getStringExtra("auth_error")
        when {
            code != null -> viewModel.handleAuthCode(this, code)
            error != null -> updateStatus("Login error: $error", isError = true)
        }
    }

    private fun setupUI() {
        binding.btnSpuffle.setOnClickListener {
            if (!SpotifyAuthManager.isLoggedIn(this)) {
                SpotifyAuthManager.launchAuthFlow(this)
            } else {
                // Open the playlist picker
                playlistLauncher.launch(Intent(this, PlayListActivity::class.java))
            }
        }

        binding.btnLogout.setOnClickListener {
            SpotifyAuthManager.clearTokens(this)
            PlaylistCache.clearCache(this)
            updateUI(loggedIn = false)
            updateStatus("Logged out.")
        }

        binding.btnLogout.setOnLongClickListener {
            PlaylistCache.clearCache(this)
            updateStatus("Cache cleared. Next Spuffle will fetch fresh data.")
            true
        }

        updateUI(loggedIn = SpotifyAuthManager.isLoggedIn(this))
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is SpuffleState.Idle -> Unit
                    is SpuffleState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        updateStatus("Starting...")
                        binding.btnSpuffle.isEnabled = false
                        binding.rvQueuedTracks.visibility = View.GONE
                        binding.tvQueuedTracksLabel.visibility = View.GONE
                    }
                    is SpuffleState.FetchingTracks -> {
                        val pct = if (state.total > 0) (state.fetched * 100 / state.total) else 0
                        updateStatus("Fetching tracks... ${state.fetched}/${state.total} ($pct%)")
                    }
                    is SpuffleState.PlaylistsLoaded -> Unit // handled in PlaylistActivity
                    is SpuffleState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnSpuffle.isEnabled = true
                        updateStatus(state.message)
                        updateUI(loggedIn = true)
                        val hasQueuedTracks = state.queuedTracks.isNotEmpty()
                        binding.rvQueuedTracks.visibility = if (hasQueuedTracks) View.VISIBLE else View.GONE
                        binding.tvQueuedTracksLabel.visibility = if (hasQueuedTracks) View.VISIBLE else View.GONE
                        binding.rvQueuedTracks.adapter = QueuedTrackAdapter(state.queuedTracks)
                    }
                    is SpuffleState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnSpuffle.isEnabled = true
                        binding.rvQueuedTracks.visibility = View.GONE
                        binding.tvQueuedTracksLabel.visibility = View.GONE
                        updateStatus(state.message, isError = true)
                    }
                }
            }
        }
    }

    private fun updateUI(loggedIn: Boolean) {
        binding.btnSpuffle.text = if (loggedIn) "Spuffle!" else "Login with Spotify"
        binding.btnLogout.visibility = if (loggedIn) View.VISIBLE else View.GONE
    }

    private fun updateStatus(message: String, isError: Boolean = false) {
        binding.tvStatus.text = message
        binding.tvStatus.setTextColor(
            if (isError) getColor(android.R.color.holo_red_light)
            else getColor(android.R.color.white)
        )
    }
}