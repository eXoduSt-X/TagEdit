package com.exodust.tagedit

import android.net.Uri
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

    /** Tags leídos en background, indexados por Uri. Ausente = todavía no se leyó. */
    private val tagsCache = mutableMapOf<Uri, TagFields>()

    class SongViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.checkboxSelected)
        val track: TextView = view.findViewById(R.id.textTrack)
        val title: TextView = view.findViewById(R.id.textTitle)
        val subtitle: TextView = view.findViewById(R.id.textSubtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        val tags = tagsCache[song.uri]

        holder.checkbox.isChecked = selectedPositions.contains(position)
        holder.track.text = tags?.track?.takeIf { it.isNotBlank() } ?: "–"
        holder.title.text = tags?.title?.takeIf { it.isNotBlank() } ?: song.displayName
        holder.subtitle.text = when {
            tags == null -> "Leyendo tags…"
            else -> listOfNotNull(tags.artist, tags.album)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        }

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
        tagsCache.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    /** Llamado desde MainActivity a medida que se leen los tags reales en background. */
    fun updateTags(uri: Uri, tags: TagFields) {
        tagsCache[uri] = tags
        val index = songs.indexOfFirst { it.uri == uri }
        if (index >= 0) notifyItemChanged(index)
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
