package com.toadfish.spuffle

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.toadfish.spuffle.databinding.ItemQueuedTrackBinding

class QueuedTrackAdapter(
    private val tracks: List<QueuedTrack>
) : RecyclerView.Adapter<QueuedTrackAdapter.QueuedTrackViewHolder>() {

    class QueuedTrackViewHolder(
        private val binding: ItemQueuedTrackBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(track: QueuedTrack) {
            binding.tvTrackTitle.text = track.displayTitle
            binding.tvTrackArtist.text = track.displayArtist
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueuedTrackViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemQueuedTrackBinding.inflate(inflater, parent, false)
        return QueuedTrackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QueuedTrackViewHolder, position: Int) {
        holder.bind(tracks[position])
    }

    override fun getItemCount(): Int = tracks.size
}
