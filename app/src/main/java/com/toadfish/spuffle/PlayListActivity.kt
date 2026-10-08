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
                    is SpuffleState.PlaylistsLoaded -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvStatus.text = "Choose a playlist to Spuffle"
                        binding.rvPlaylists.visibility = View.VISIBLE
                        binding.rvPlaylists.adapter = PlaylistAdapter(state.playlists) { playlist ->
                            // Pass selected playlist back to MainActivity
                            val intent = Intent().apply {
                                putExtra("playlist_id", playlist.id)
                                putExtra("playlist_name", playlist.name)
                                putExtra("playlist_track_count", playlist.trackCount)
                                putExtra("playlist_is_liked_songs", playlist.isLikedSongs)
                            }
                            setResult(RESULT_OK, intent)
                            finish()
                        }
                    }
                    is SpuffleState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvStatus.text = state.message
                    }
                    else -> Unit
                }
            }
        }
    }
}