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

    /**
     * Orden de TOQUE, no de posición en la lista: se usa tal cual para numerar pistas,
     * así el track number sigue el orden real del álbum aunque la carpeta esté en
     * otro orden. Volver a tocar un ítem ya seleccionado lo saca y lo vuelve a poner
     * al final de la cola (para poder corregir el orden sin deseleccionar todo).
     */
    private val selectedPositions = LinkedHashSet<Int>()

    /** Tags leídos en background, indexados por Uri. Ausente = todavía no se leyó. */
    private val tagsCache = mutableMapOf<Uri, TagFields>()

    class SongViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.checkboxSelected)
        val selectionOrder: TextView = view.findViewById(R.id.textSelectionOrder)
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
        val order = selectedPositions.indexOf(position).let { if (it >= 0) it + 1 else null }

        holder.checkbox.isChecked = order != null
        holder.selectionOrder.text = order?.toString().orEmpty()
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
        // Se refresca todo: sacar/agregar un ítem corre el número de orden de los demás.
        notifyDataSetChanged()
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

    /** Devuelve las canciones seleccionadas en el orden en que se tocaron (no el de la lista). */
    fun getSelectedSongs(): List<Song> =
        selectedPositions.map { songs[it] }
}
