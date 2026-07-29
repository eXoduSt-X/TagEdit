package com.exodust.tagedit

/**
 * Acciones que puede disparar el diálogo de edición masiva. La actividad que lo abre
 * implementa esta interface y decide cómo aplicar cada acción sobre las canciones
 * seleccionadas.
 */
interface BulkEditListener {
    /** [fields] con valores no nulos deben pisar el tag correspondiente en todos los seleccionados. */
    fun onApplyBulkTags(fields: TagFields)

    /** Aplica [imageBytes] (con su [mimeType]) como carátula de todos los seleccionados. */
    fun onApplyArtwork(imageBytes: ByteArray, mimeType: String)

    /** Generar nombre de archivo a partir de los tags reales de cada canción, usando [pattern]. */
    fun onConvertTagToFilename(pattern: String)

    /** Mostrar, sin renombrar todavía, el nombre que resultaría de aplicar [pattern]. */
    fun onPreviewTagToFilename(pattern: String)

    /** Parsear el nombre de archivo de cada canción con [pattern] y volcar los valores a sus tags. */
    fun onConvertFilenameToTag(pattern: String)

    /** Asignar track number secuencial empezando en [startAt], con [padding] dígitos (ej. 2 -> "01"). */
    fun onApplyNumbering(startAt: Int, padding: Int)
}
