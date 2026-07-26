package com.exodust.tagedit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult

        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        val tree = DocumentFile.fromTreeUri(this, uri)
        val audioFiles = tree?.listFiles()?.filter {
            it.isFile && (it.name?.endsWith(".mp3", true) == true ||
                it.name?.endsWith(".flac", true) == true ||
                it.name?.endsWith(".m4a", true) == true ||
                it.name?.endsWith(".ogg", true) == true)
        } ?: emptyList()

        // TODO: pasar audioFiles a un adapter de RecyclerView con selección múltiple.
        // TODO: leer/escribir tags con jaudiotagger (requiere un File real o un
        //       stream intermedio, ya que jaudiotagger no trabaja directo con Uri de SAF).
        statusText.text = "Carpeta seleccionada: ${audioFiles.size} archivos de audio"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
        }

        val pickButton = Button(this).apply {
            text = "Elegir carpeta de música"
            setOnClickListener { pickFolder.launch(null) }
        }

        statusText = TextView(this).apply {
            text = "Ninguna carpeta seleccionada"
            setPadding(0, 32, 0, 0)
        }

        layout.addView(pickButton)
        layout.addView(statusText)
        setContentView(layout)
    }
}
