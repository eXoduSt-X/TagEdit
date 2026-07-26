package com.exodust.tagedit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity(), BulkEditListener {

    private lateinit var statusText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SongAdapter

    private val AUDIO_EXTENSIONS = listOf(".mp3", ".flac", ".m4a", ".ogg", ".wav")

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult

        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        val tree = DocumentFile.fromTreeUri(this, uri)
        val songs = tree?.listFiles()
            ?.filter { doc ->
                doc.isFile && AUDIO_EXTENSIONS.any { ext ->
                    doc.name?.endsWith(ext, ignoreCase = true) == true
                }
            }
            ?.map { doc -> Song(doc.uri, doc.name ?: "(sin nombre)") }
            ?: emptyList()

        adapter.submitList(songs)
        statusText.text = "${songs.size} archivos de audio"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.textStatus)
        recyclerView = findViewById(R.id.recyclerSongs)

        adapter = SongAdapter { selectedCount ->
            val total = adapter.itemCount
            statusText.text = if (total == 0) {
                "Ninguna carpeta seleccionada"
            } else {
                "$selectedCount de $total seleccionadas"
            }
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.buttonPickFolder).setOnClickListener {
            pickFolder.launch(null)
        }

        findViewById<Button>(R.id.buttonSelectAll).setOnClickListener {
            adapter.selectAll()
        }

        findViewById<Button>(R.id.buttonClearSelection).setOnClickListener {
            adapter.clearSelection()
        }

        findViewById<Button>(R.id.buttonBulkEdit).setOnClickListener {
            val count = adapter.getSelectedSongs().size
            if (count == 0) {
                Toast.makeText(this, "Seleccioná al menos una canción", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val dialog = BulkEditDialogFragment.newInstance(count)
            dialog.listener = this
            dialog.show(supportFragmentManager, "bulk_edit")
        }
    }

    override fun onApplyBulkTags(fields: TagFields) {
        val selected = adapter.getSelectedSongs()
        // TODO: escribir 'fields' en cada archivo con jaudiotagger (falta esa integración).
        Toast.makeText(
            this,
            "TODO: aplicar tags a ${selected.size} archivo(s) (falta integrar jaudiotagger)",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onConvertTagToFilename(pattern: String) {
        // TODO: necesita leer los tags reales de cada archivo con jaudiotagger antes de
        // poder generar el nombre con PatternEngine.tagsToFilename(pattern, tags).
        Toast.makeText(
            this,
            "TODO: falta leer tags reales (jaudiotagger) para generar el nombre",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onConvertFilenameToTag(pattern: String) {
        val selected = adapter.getSelectedSongs()
        val preview = selected.mapNotNull { song ->
            val (base, _) = PatternEngine.splitExtension(song.displayName)
            PatternEngine.filenameToTags(pattern, base)?.let { song.displayName to it }
        }
        // TODO: una vez integrado jaudiotagger, escribir cada TagFields de 'preview'
        // en el archivo real correspondiente en lugar de solo mostrarlo.
        val summary = preview.joinToString("\n") { (name, tags) ->
            "$name -> artista=${tags.artist}, título=${tags.title}"
        }
        Toast.makeText(
            this,
            summary.ifBlank { "Ningún nombre matcheó el patrón" },
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onApplyNumbering(startAt: Int, padding: Int) {
        val selected = adapter.getSelectedSongs()
        // TODO: esto todavía no persiste nada; falta escribir el track number real
        // con jaudiotagger para cada canción de 'selected', en orden.
        val last = startAt + selected.size - 1
        Toast.makeText(
            this,
            "TODO: asignar tracks $startAt..$last (falta integrar jaudiotagger)",
            Toast.LENGTH_LONG
        ).show()
    }
}
