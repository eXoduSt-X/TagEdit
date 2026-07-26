package com.exodust.tagedit

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

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

    /** Se setea desde la actividad antes de mostrar el diálogo. */
    var listener: BulkEditListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val selectedCount = arguments?.getInt(ARG_SELECTED_COUNT) ?: 0
        val view = layoutInflater.inflate(R.layout.dialog_bulk_edit, null)

        view.findViewById<TextView>(R.id.textSelectionCount).text =
            "$selectedCount canción(es) seleccionada(s)"

        val editArtist = view.findViewById<EditText>(R.id.editArtist)
        val editAlbum = view.findViewById<EditText>(R.id.editAlbum)
        val editAlbumArtist = view.findViewById<EditText>(R.id.editAlbumArtist)
        val editYear = view.findViewById<EditText>(R.id.editYear)
        val editGenre = view.findViewById<EditText>(R.id.editGenre)
        val editComment = view.findViewById<EditText>(R.id.editComment)
        val editPattern = view.findViewById<EditText>(R.id.editPattern)
        val editStartNumber = view.findViewById<EditText>(R.id.editStartNumber)
        val editPadding = view.findViewById<EditText>(R.id.editPadding)

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
