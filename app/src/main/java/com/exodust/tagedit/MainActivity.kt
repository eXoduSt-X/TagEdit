package com.exodust.tagedit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Nombre de clase + mensaje (si lo tiene) — más útil que solo .message, que a veces viene null. */
private fun Throwable.describe(): String =
    "${javaClass.simpleName}${message?.let { ": $it" } ?: " (sin mensaje)"}"

class MainActivity : AppCompatActivity(), BulkEditListener {

    private lateinit var statusText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SongAdapter

    private var currentTreeUri: Uri? = null

    private val AUDIO_EXTENSIONS = listOf(".mp3", ".flac", ".m4a", ".ogg", ".wav")

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult

        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        currentTreeUri = uri
        loadSongsFromTree(uri)
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

    private fun loadSongsFromTree(uri: Uri) {
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
        loadTagsInBackground(songs)
    }

    private fun loadTagsInBackground(songs: List<Song>) {
        lifecycleScope.launch(Dispatchers.IO) {
            for (song in songs) {
                val tags = TagIO.readTags(applicationContext, song).getOrNull() ?: continue
                withContext(Dispatchers.Main) {
                    adapter.updateTags(song.uri, tags)
                }
            }
        }
    }

    private fun refreshCurrentFolder() {
        currentTreeUri?.let { loadSongsFromTree(it) }
    }

    override fun onApplyBulkTags(fields: TagFields) {
        val selected = adapter.getSelectedSongs()
        lifecycleScope.launch(Dispatchers.IO) {
            var success = 0
            var firstError: String? = null
            for (song in selected) {
                val result = TagIO.writeTags(applicationContext, song, fields)
                if (result.isSuccess) {
                    success++
                } else if (firstError == null) {
                    firstError = "${song.displayName}: ${result.exceptionOrNull()?.describe()}"
                }
            }
            withContext(Dispatchers.Main) {
                showResult("Tags aplicados", success, selected.size, firstError)
            }
        }
    }

    override fun onConvertTagToFilename(pattern: String) {
        val selected = adapter.getSelectedSongs()
        val parentDir = currentTreeUri?.let { DocumentFile.fromTreeUri(applicationContext, it) }
        lifecycleScope.launch(Dispatchers.IO) {
            var renamed = 0
            var firstError: String? = null
            for (song in selected) {
                val tagsResult = TagIO.readTags(applicationContext, song)
                val tags = tagsResult.getOrNull()
                if (tags == null) {
                    if (firstError == null) {
                        firstError = "Leyendo ${song.displayName}: ${tagsResult.exceptionOrNull()?.describe()}"
                    }
                    continue
                }
                val (_, ext) = PatternEngine.splitExtension(song.displayName)
                val newName = PatternEngine.tagsToFilename(pattern, tags) + ext
                val renameResult = TagIO.renameFile(applicationContext, song, newName, parentDir)
                if (renameResult.isSuccess) {
                    renamed++
                } else if (firstError == null) {
                    firstError = "Renombrando ${song.displayName}: ${renameResult.exceptionOrNull()?.describe()}"
                }
            }
            withContext(Dispatchers.Main) {
                showResult("Renombrados", renamed, selected.size, firstError)
                refreshCurrentFolder()
            }
        }
    }

    override fun onConvertFilenameToTag(pattern: String) {
        val selected = adapter.getSelectedSongs()
        lifecycleScope.launch(Dispatchers.IO) {
            var success = 0
            var firstError: String? = null
            for (song in selected) {
                val (base, _) = PatternEngine.splitExtension(song.displayName)
                val tags = PatternEngine.filenameToTags(pattern, base)
                if (tags == null) {
                    if (firstError == null) {
                        firstError = "${song.displayName} no matchea el patrón"
                    }
                    continue
                }
                val result = TagIO.writeTags(applicationContext, song, tags)
                if (result.isSuccess) {
                    success++
                } else if (firstError == null) {
                    firstError = "${song.displayName}: ${result.exceptionOrNull()?.describe()}"
                }
            }
            withContext(Dispatchers.Main) {
                showResult("Tags escritos", success, selected.size, firstError)
            }
        }
    }

    override fun onApplyNumbering(startAt: Int, padding: Int) {
        val selected = adapter.getSelectedSongs()
        lifecycleScope.launch(Dispatchers.IO) {
            var success = 0
            var firstError: String? = null
            selected.forEachIndexed { index, song ->
                val trackNumber = (startAt + index).toString().padStart(padding, '0')
                val result = TagIO.writeTags(applicationContext, song, TagFields(track = trackNumber))
                if (result.isSuccess) {
                    success++
                } else if (firstError == null) {
                    firstError = "${song.displayName}: ${result.exceptionOrNull()?.describe()}"
                }
            }
            withContext(Dispatchers.Main) {
                showResult("Numerados", success, selected.size, firstError)
            }
        }
    }

    /** Muestra el conteo de éxito por Toast; si hubo algún fallo, abre un diálogo con el error completo. */
    private fun showResult(action: String, success: Int, total: Int, firstError: String?) {
        val base = "$action: $success de $total archivo(s)"
        Toast.makeText(this, base, Toast.LENGTH_SHORT).show()

        if (firstError != null) {
            val messageView = TextView(this).apply {
                text = firstError
                setPadding(48, 32, 48, 32)
                setTextIsSelectable(true)
            }
            AlertDialog.Builder(this)
                .setTitle(base)
                .setView(messageView)
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
