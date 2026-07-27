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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    }

    private fun refreshCurrentFolder() {
        currentTreeUri?.let { loadSongsFromTree(it) }
    }

    override fun onApplyBulkTags(fields: TagFields) {
        val selected = adapter.getSelectedSongs()
        lifecycleScope.launch(Dispatchers.IO) {
            var success = 0
            for (song in selected) {
                if (TagIO.writeTags(applicationContext, song, fields)) success++
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    "Tags aplicados en $success de ${selected.size} archivo(s)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onConvertTagToFilename(pattern: String) {
        val selected = adapter.getSelectedSongs()
        lifecycleScope.launch(Dispatchers.IO) {
            var renamed = 0
            for (song in selected) {
                val tags = TagIO.readTags(applicationContext, song) ?: continue
                val (_, ext) = PatternEngine.splitExtension(song.displayName)
                val newName = PatternEngine.tagsToFilename(pattern, tags) + ext
                if (TagIO.renameFile(applicationContext, song, newName)) renamed++
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    "Renombrados $renamed de ${selected.size} archivo(s)",
                    Toast.LENGTH_LONG
                ).show()
                refreshCurrentFolder()
            }
        }
    }

    override fun onConvertFilenameToTag(pattern: String) {
        val selected = adapter.getSelectedSongs()
        lifecycleScope.launch(Dispatchers.IO) {
            var success = 0
            for (song in selected) {
                val (base, _) = PatternEngine.splitExtension(song.displayName)
                val tags = PatternEngine.filenameToTags(pattern, base) ?: continue
                if (TagIO.writeTags(applicationContext, song, tags)) success++
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    "Tags escritos en $success de ${selected.size} archivo(s)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onApplyNumbering(startAt: Int, padding: Int) {
        val selected = adapter.getSelectedSongs()
        lifecycleScope.launch(Dispatchers.IO) {
            var success = 0
            selected.forEachIndexed { index, song ->
                val trackNumber = (startAt + index).toString().padStart(padding, '0')
                if (TagIO.writeTags(applicationContext, song, TagFields(track = trackNumber))) success++
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    "Numerados $success de ${selected.size} archivo(s)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
