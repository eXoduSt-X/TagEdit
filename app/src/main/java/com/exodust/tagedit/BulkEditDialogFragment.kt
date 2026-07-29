package com.exodust.tagedit

import android.app.Dialog
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BulkEditDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_SELECTED_COUNT = "selected_count"

        fun newInstance(selectedCount: Int): BulkEditDialogFragment {
            val fragment = BulkEditDialogFragment()
            fragment.arguments = Bundle().apply {
                putInt(ARG_SELECTED_COUNT, selectedCount)
            }
            return fragment
        }
    }

    /** Se setean desde la actividad antes de mostrar el diálogo. */
    var listener: BulkEditListener? = null
    var selectedSongs: List<Song> = emptyList()

    private var pickedArtworkBytes: ByteArray? = null
    private var pickedArtworkMime: String? = null
    private var artworkPreview: ImageView? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val context = requireContext()
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) {
            Toast.makeText(context, "No se pudo leer la imagen", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        pickedArtworkBytes = bytes
        pickedArtworkMime = mime
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        artworkPreview?.apply {
            setImageBitmap(bitmap)
            visibility = android.view.View.VISIBLE
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val selectedCount = arguments?.getInt(ARG_SELECTED_COUNT) ?: 0
        val view = layoutInflater.inflate(R.layout.dialog_bulk_edit, null)

        view.findViewById<TextView>(R.id.textSelectionCount).text =
            "$selectedCount canción(es) seleccionada(s)  ·  build ${BuildConfig.VERSION_NAME}"

        val imageArtwork = view.findViewById<ImageView>(R.id.imageArtwork)
        artworkPreview = imageArtwork
        if (selectedSongs.isNotEmpty()) {
            loadCommonArtwork(selectedSongs, imageArtwork)
        }

        view.findViewById<android.widget.Button>(R.id.buttonPickArtwork).setOnClickListener {
            pickImage.launch("image/*")
        }

        view.findViewById<android.widget.Button>(R.id.buttonApplyArtwork).setOnClickListener {
            val bytes = pickedArtworkBytes
            val mime = pickedArtworkMime
            if (bytes == null || mime == null) {
                Toast.makeText(requireContext(), "Elegí una imagen primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedCount == 0) {
                toastNoSelection()
                return@setOnClickListener
            }
            listener?.onApplyArtwork(bytes, mime)
        }

        val editArtist = view.findViewById<EditText>(R.id.editArtist)
        val editAlbum = view.findViewById<EditText>(R.id.editAlbum)
        val editAlbumArtist = view.findViewById<EditText>(R.id.editAlbumArtist)
        val editYear = view.findViewById<EditText>(R.id.editYear)
        val editGenre = view.findViewById<EditText>(R.id.editGenre)
        val editComment = view.findViewById<EditText>(R.id.editComment)
        val editPattern = view.findViewById<EditText>(R.id.editPattern)
        val editStartNumber = view.findViewById<EditText>(R.id.editStartNumber)
        val editPadding = view.findViewById<EditText>(R.id.editPadding)

        val placeholderChips = mapOf(
            R.id.chipTrack to "%track%",
            R.id.chipTitle to "%title%",
            R.id.chipArtist to "%artist%",
            R.id.chipAlbum to "%album%",
            R.id.chipAlbumArtist to "%albumartist%",
            R.id.chipYear to "%year%",
            R.id.chipGenre to "%genre%",
            R.id.chipDisc to "%disc%",
            R.id.chipSeparator to " - "
        )
        placeholderChips.forEach { (viewId, token) ->
            view.findViewById<android.widget.Button>(viewId).setOnClickListener {
                insertAtCursor(editPattern, token)
            }
        }

        view.findViewById<android.widget.Button>(R.id.buttonApplyTags).setOnClickListener {
            if (selectedCount == 0) {
                toastNoSelection()
                return@setOnClickListener
            }
            val fields = TagFields(
                artist = editArtist.text.toString().ifBlank { null },
                album = editAlbum.text.toString().ifBlank { null },
                albumArtist = editAlbumArtist.text.toString().ifBlank { null },
                year = editYear.text.toString().ifBlank { null },
                genre = editGenre.text.toString().ifBlank { null },
                comment = editComment.text.toString().ifBlank { null }
            )
            listener?.onApplyBulkTags(fields)
        }

        view.findViewById<android.widget.Button>(R.id.buttonPreviewRename).setOnClickListener {
            val pattern = editPattern.text.toString()
            if (!validatePattern(pattern) || selectedCount == 0) return@setOnClickListener
            listener?.onPreviewTagToFilename(pattern)
        }

        view.findViewById<android.widget.Button>(R.id.buttonTagToFilename).setOnClickListener {
            val pattern = editPattern.text.toString()
            if (!validatePattern(pattern) || selectedCount == 0) return@setOnClickListener
            listener?.onConvertTagToFilename(pattern)
        }

        view.findViewById<android.widget.Button>(R.id.buttonFilenameToTag).setOnClickListener {
            val pattern = editPattern.text.toString()
            if (!validatePattern(pattern) || selectedCount == 0) return@setOnClickListener
            listener?.onConvertFilenameToTag(pattern)
        }

        view.findViewById<android.widget.Button>(R.id.buttonApplyNumbering).setOnClickListener {
            if (selectedCount == 0) {
                toastNoSelection()
                return@setOnClickListener
            }
            val startAt = editStartNumber.text.toString().toIntOrNull() ?: 1
            val padding = editPadding.text.toString().toIntOrNull() ?: 2
            listener?.onApplyNumbering(startAt, padding)
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .setNegativeButton("Cerrar", null)
            .create()
    }

    /**
     * Muestra la carátula solo si TODAS las canciones seleccionadas tienen exactamente
     * la misma carátula embebida. Si alguna no tiene, o difieren, la imagen queda oculta.
     */
    private fun loadCommonArtwork(songs: List<Song>, imageView: ImageView) {
        val context = requireContext().applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            var common: ByteArray? = null
            var allSame = true
            for (song in songs) {
                val artwork = TagIO.readArtwork(context, song).getOrNull()
                if (artwork == null) {
                    allSame = false
                    break
                }
                if (common == null) {
                    common = artwork
                } else if (!common.contentEquals(artwork)) {
                    allSame = false
                    break
                }
            }
            val finalArtwork = if (allSame) common else null
            withContext(Dispatchers.Main) {
                val bitmap = finalArtwork?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    imageView.visibility = android.view.View.VISIBLE
                } else {
                    imageView.visibility = android.view.View.GONE
                }
            }
        }
    }

    private fun insertAtCursor(editText: EditText, token: String) {
        val start = editText.selectionStart.coerceAtLeast(0)
        val end = editText.selectionEnd.coerceAtLeast(0)
        editText.text.replace(minOf(start, end), maxOf(start, end), token)
        editText.setSelection(minOf(start, end) + token.length)
    }

    private fun validatePattern(pattern: String): Boolean {
        if (pattern.isBlank()) {
            Toast.makeText(requireContext(), "Ingresá un patrón", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun toastNoSelection() {
        Toast.makeText(requireContext(), "No hay canciones seleccionadas", Toast.LENGTH_SHORT).show()
    }
}
