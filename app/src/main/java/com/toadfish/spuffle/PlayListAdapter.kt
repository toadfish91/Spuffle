package com.toadfish.spuffle

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.toadfish.spuffle.databinding.ItemPlaylistBinding

class PlaylistAdapter(
    private var playlists: List<Playlist>,
    private var selectedPlaylistIds: Set<String>,
    private var isMultiSelectMode: Boolean,
    private val onPlaylistSelected: (Playlist) -> Unit,
    private val onPlaylistLongSelected: (Playlist) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

    fun updatePlaylists(
        playlists: List<Playlist>,
        selectedPlaylistIds: Set<String>,
        isMultiSelectMode: Boolean
    ) {
        this.playlists = playlists
        this.selectedPlaylistIds = selectedPlaylistIds
        this.isMultiSelectMode = isMultiSelectMode
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemPlaylistBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlaylistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val playlist = playlists[position]
        holder.binding.apply {
            tvPlaylistName.text = playlist.name
            tvTrackCount.text = if (playlist.trackCount != null) {
                "${playlist.trackCount} songs"
            } else {
                "Select to load song count"
            }

            // Green music note icon for Liked Songs, generic icon for others
            ivPlaylistIcon.setImageResource(
                if (playlist.isLikedSongs) R.drawable.ic_liked_songs
                else R.drawable.ic_playlist
            )

            cbSelected.visibility = if (isMultiSelectMode) View.VISIBLE else View.GONE
            cbSelected.isChecked = playlist.id in selectedPlaylistIds

            root.setOnClickListener { onPlaylistSelected(playlist) }
            root.setOnLongClickListener {
                onPlaylistLongSelected(playlist)
                true
            }
        }
    }

    override fun getItemCount() = playlists.size
}