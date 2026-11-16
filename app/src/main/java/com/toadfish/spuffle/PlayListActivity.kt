package com.toadfish.spuffle

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.toadfish.spuffle.databinding.ActivityPlaylistBinding
import kotlinx.coroutines.launch

class PlayListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaylistBinding
    private val viewModel: SpuffleViewModel by viewModels()
    private var playlists = emptyList<Playlist>()
    private var isCheckingPlaylist = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistBinding.inflate(layoutInflater)
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

        binding.rvPlaylists.layoutManager = LinearLayoutManager(this)

        observeState()
        viewModel.loadPlaylists(this)
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is SpuffleState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.tvStatus.text = "Loading your playlists..."
                        binding.rvPlaylists.visibility = View.GONE
                    }
                    is SpuffleState.CheckingPlaylist -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.tvStatus.text = "Checking ${state.playlist.name}..."
                        binding.rvPlaylists.visibility = View.VISIBLE
                    }
                    is SpuffleState.PlaylistsLoaded -> {
                        isCheckingPlaylist = false
                        playlists = state.playlists
                        binding.progressBar.visibility = View.GONE
                        binding.tvStatus.text = if (playlists.size == 1) {
                            "Spotify returned no other playlists."
                        } else {
                            "Choose a playlist to Spuffle"
                        }
                        binding.rvPlaylists.visibility = View.VISIBLE
                        showPlaylists()
                    }
                    is SpuffleState.PlaylistChecked -> {
                        isCheckingPlaylist = false
                        binding.progressBar.visibility = View.GONE
                        playlists = playlists.map {
                            if (it.id == state.playlist.id) state.playlist else it
                        }

                        if (state.playlist.trackCount == 0) {
                            binding.tvStatus.text = "This playlist is empty (0 songs). Choose another playlist."
                            binding.rvPlaylists.visibility = View.VISIBLE
                            showPlaylists()
                        } else {
                            val intent = Intent().apply {
                                putExtra("playlist_id", state.playlist.id)
                                putExtra("playlist_name", state.playlist.name)
                                putExtra("playlist_track_count", state.playlist.trackCount ?: -1)
                                putExtra("playlist_is_liked_songs", state.playlist.isLikedSongs)
                            }
                            setResult(RESULT_OK, intent)
                            finish()
                        }
                    }
                    is SpuffleState.Error -> {
                        isCheckingPlaylist = false
                        binding.progressBar.visibility = View.GONE
                        binding.tvStatus.text = state.message
                        binding.rvPlaylists.visibility =
                            if (playlists.isEmpty()) View.GONE else View.VISIBLE
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun showPlaylists() {
        binding.rvPlaylists.adapter = PlaylistAdapter(playlists) { playlist ->
            if (!isCheckingPlaylist) {
                isCheckingPlaylist = true
                viewModel.checkPlaylist(this, playlist)
            }
        }
    }
}