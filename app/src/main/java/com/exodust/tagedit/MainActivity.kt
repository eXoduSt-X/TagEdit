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

class MainActivity : AppCompatActivity() {

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

        // TODO: botón "Aplicar" que tome adapter.getSelectedSongs() y dispare
        // el motor de patrones Tag<->Filename o el diálogo de edición masiva.
    }
}
