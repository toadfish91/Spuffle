package com.toadfish.spuffle

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.toadfish.spuffle.databinding.ItemPlaylistBinding

class PlaylistAdapter(
    private val playlists: List<Playlist>,
    private val onPlaylistSelected: (Playlist) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

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
            tvTrackCount.text = "${playlist.trackCount} songs"

            // Green music note icon for Liked Songs, generic icon for others
            ivPlaylistIcon.setImageResource(
                if (playlist.isLikedSongs) R.drawable.ic_liked_songs
                else R.drawable.ic_playlist
            )

            root.setOnClickListener { onPlaylistSelected(playlist) }
        }
    }

    override fun getItemCount() = playlists.size
}