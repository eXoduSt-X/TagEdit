package com.exodust.tagedit

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SongAdapter(
    private val songs: MutableList<Song> = mutableListOf(),
    private val onSelectionChanged: (Int) -> Unit = {}
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private val selectedPositions = mutableSetOf<Int>()

    class SongViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.checkboxSelected)
        val name: TextView = view.findViewById(R.id.textSongName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.name.text = song.displayName
        holder.checkbox.isChecked = selectedPositions.contains(position)

        holder.itemView.setOnClickListener {
            toggleSelection(position)
        }
        holder.itemView.setOnLongClickListener {
            toggleSelection(position)
            true
        }
    }

    override fun getItemCount(): Int = songs.size

    private fun toggleSelection(position: Int) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position)
        } else {
            selectedPositions.add(position)
        }
        notifyItemChanged(position)
        onSelectionChanged(selectedPositions.size)
    }

    fun submitList(newSongs: List<Song>) {
        songs.clear()
        songs.addAll(newSongs)
        selectedPositions.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun selectAll() {
        selectedPositions.clear()
        selectedPositions.addAll(songs.indices)
        notifyDataSetChanged()
        onSelectionChanged(selectedPositions.size)
    }

    fun clearSelection() {
        selectedPositions.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun getSelectedSongs(): List<Song> =
        selectedPositions.sorted().map { songs[it] }
}
