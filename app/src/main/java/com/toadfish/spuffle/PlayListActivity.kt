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
    private var playlistAdapter: PlaylistAdapter? = null
    private var playlists = emptyList<Playlist>()
    private var isCheckingPlaylist = false
    private var isMultiSelectMode = false
    private val selectedPlaylists = linkedMapOf<String, Playlist>()

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
        binding.btnDone.setOnClickListener {
            if (!isCheckingPlaylist && selectedPlaylists.isNotEmpty()) {
                isCheckingPlaylist = true
                viewModel.checkPlaylists(this, selectedPlaylists.values.toList())
            }
        }

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
                    is SpuffleState.CheckingPlaylists -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.tvStatus.text = "Checking ${state.playlists.size} selected playlists..."
                        binding.rvPlaylists.visibility = View.VISIBLE
                    }
                    is SpuffleState.PlaylistsLoaded -> {
                        isCheckingPlaylist = false
                        playlists = state.playlists
                        selectedPlaylists.clear()
                        isMultiSelectMode = false
                        binding.progressBar.visibility = View.GONE
                        binding.tvStatus.text = if (playlists.size == 1) {
                            "Spotify returned no other playlists."
                        } else {
                            "Tap a playlist to Spuffle, or press and hold to select multiple."
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
                            finishWithSelection(listOf(state.playlist))
                        }
                    }
                    is SpuffleState.PlaylistsChecked -> {
                        isCheckingPlaylist = false
                        binding.progressBar.visibility = View.GONE
                        val checkedById = state.playlists.associateBy { it.id }
                        playlists = playlists.map { checkedById[it.id] ?: it }
                        selectedPlaylists.clear()
                        selectedPlaylists.putAll(state.playlists.associateBy { it.id })

                        if (state.playlists.all { it.trackCount == 0 }) {
                            binding.tvStatus.text =
                                "All selected playlists are empty (0 songs). Choose another playlist."
                            binding.rvPlaylists.visibility = View.VISIBLE
                            showPlaylists()
                        } else {
                            finishWithSelection(state.playlists)
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
        binding.btnDone.visibility =
            if (isMultiSelectMode) View.VISIBLE else View.GONE
        val existingAdapter = playlistAdapter
        if (existingAdapter != null) {
            existingAdapter.updatePlaylists(
                playlists,
                selectedPlaylists.keys.toSet(),
                isMultiSelectMode
            )
        } else {
            playlistAdapter = PlaylistAdapter(
                playlists = playlists,
                selectedPlaylistIds = selectedPlaylists.keys.toSet(),
                isMultiSelectMode = isMultiSelectMode,
                onPlaylistSelected = { playlist ->
                    if (!isCheckingPlaylist) {
                        if (isMultiSelectMode) {
                            togglePlaylistSelection(playlist)
                        } else {
                            isCheckingPlaylist = true
                            viewModel.checkPlaylist(this, playlist)
                        }
                    }
                },
                onPlaylistLongSelected = { playlist ->
                    if (!isCheckingPlaylist) {
                        isMultiSelectMode = true
                        togglePlaylistSelection(playlist)
                    }
                }
            ).also { binding.rvPlaylists.adapter = it }
        }
    }

    private fun togglePlaylistSelection(playlist: Playlist) {
        if (selectedPlaylists.remove(playlist.id) == null) {
            selectedPlaylists[playlist.id] = playlist
        }
        binding.tvStatus.text = "${selectedPlaylists.size} selected. Tap Done to Spuffle together."
        showPlaylists()
    }

    private fun finishWithSelection(selected: List<Playlist>) {
        val intent = Intent()
        if (selected.size == 1) {
            val playlist = selected.first()
            intent.putExtra("playlist_id", playlist.id)
            intent.putExtra("playlist_name", playlist.name)
            intent.putExtra("playlist_track_count", playlist.trackCount ?: -1)
            intent.putExtra("playlist_is_liked_songs", playlist.isLikedSongs)
        } else {
            intent.putStringArrayListExtra(
                "playlist_ids",
                ArrayList(selected.map { it.id })
            )
            intent.putStringArrayListExtra(
                "playlist_names",
                ArrayList(selected.map { it.name })
            )
            intent.putExtra(
                "playlist_track_counts",
                selected.map { it.trackCount ?: -1 }.toIntArray()
            )
            intent.putExtra(
                "playlist_liked_songs_flags",
                selected.map { it.isLikedSongs }.toBooleanArray()
            )
        }
        setResult(RESULT_OK, intent)
        finish()
    }
}