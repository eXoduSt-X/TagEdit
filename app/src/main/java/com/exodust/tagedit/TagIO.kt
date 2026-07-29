package com.exodust.tagedit

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.PictureTypes
import org.jaudiotagger.tag.images.StandardArtwork
import java.io.File
import java.io.IOException

/**
 * jaudiotagger trabaja con java.io.File, no con Uri de SAF. Por eso cada operación
 * copia el contenido a un archivo temporal en cacheDir, opera ahí, y si escribe,
 * vuelca el resultado de vuelta al Uri original. Pensado para llamarse siempre
 * desde un hilo de background (Dispatchers.IO), nunca desde el hilo principal.
 *
 * Cada función devuelve Result<T> en vez de null/false para no tragarse el motivo
 * real del fallo: revisá logcat con el tag "TagIO" para ver el stacktrace completo,
 * o leé exceptionOrNull()?.message en el caller para un mensaje corto.
 */
object TagIO {

    private const val TAG = "TagIO"

    private fun copyToCache(context: Context, song: Song): File {
        val (_, ext) = PatternEngine.splitExtension(song.displayName)
        val tempFile = File.createTempFile("tag_", ext.ifBlank { ".tmp" }, context.cacheDir)
        context.contentResolver.openInputStream(song.uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("No se pudo abrir ${song.uri} para lectura")
        return tempFile
    }

    private fun copyBack(context: Context, tempFile: File, song: Song) {
        context.contentResolver.openOutputStream(song.uri, "wt")?.use { output ->
            tempFile.inputStream().use { input -> input.copyTo(output) }
        } ?: throw IOException("No se pudo abrir ${song.uri} para escritura")
    }

    /** Lee los tags de [song]. */
    fun readTags(context: Context, song: Song): Result<TagFields> = runCatching {
        val tempFile = copyToCache(context, song)
        try {
            val tag = AudioFileIO.read(tempFile).tag
            TagFields(
                artist = tag?.getFirst(FieldKey.ARTIST)?.ifBlank { null },
                title = tag?.getFirst(FieldKey.TITLE)?.ifBlank { null },
                album = tag?.getFirst(FieldKey.ALBUM)?.ifBlank { null },
                albumArtist = tag?.getFirst(FieldKey.ALBUM_ARTIST)?.ifBlank { null },
                track = tag?.getFirst(FieldKey.TRACK)?.ifBlank { null },
                disc = tag?.getFirst(FieldKey.DISC_NO)?.ifBlank { null },
                year = tag?.getFirst(FieldKey.YEAR)?.ifBlank { null },
                genre = tag?.getFirst(FieldKey.GENRE)?.ifBlank { null },
                comment = tag?.getFirst(FieldKey.COMMENT)?.ifBlank { null }
            )
        } finally {
            tempFile.delete()
        }
    }.onFailure { Log.e(TAG, "readTags falló para ${song.displayName}", it) }

    /**
     * Escribe únicamente los campos no nulos de [fields] en [song] (los null se dejan
     * como están).
     */
    fun writeTags(context: Context, song: Song, fields: TagFields): Result<Unit> = runCatching {
        val tempFile = copyToCache(context, song)
        try {
            val audioFile = AudioFileIO.read(tempFile)
            val tag = audioFile.tagOrCreateAndSetDefault

            fields.artist?.let { tag.setField(FieldKey.ARTIST, it) }
            fields.title?.let { tag.setField(FieldKey.TITLE, it) }
            fields.album?.let { tag.setField(FieldKey.ALBUM, it) }
            fields.albumArtist?.let { tag.setField(FieldKey.ALBUM_ARTIST, it) }
            fields.track?.let { tag.setField(FieldKey.TRACK, it) }
            fields.disc?.let { tag.setField(FieldKey.DISC_NO, it) }
            fields.year?.let { tag.setField(FieldKey.YEAR, it) }
            fields.genre?.let { tag.setField(FieldKey.GENRE, it) }
            fields.comment?.let { tag.setField(FieldKey.COMMENT, it) }

            audioFile.commit()
            copyBack(context, tempFile, song)
        } finally {
            tempFile.delete()
        }
    }.onFailure { Log.e(TAG, "writeTags falló para ${song.displayName}", it) }

    /** Lee la carátula embebida de [song], si tiene. */
    fun readArtwork(context: Context, song: Song): Result<ByteArray?> = runCatching {
        val tempFile = copyToCache(context, song)
        try {
            AudioFileIO.read(tempFile).tag?.firstArtwork?.binaryData
        } finally {
            tempFile.delete()
        }
    }.onFailure { Log.e(TAG, "readArtwork falló para ${song.displayName}", it) }

    /** Reemplaza (o agrega) la carátula embebida de [song] con [imageBytes]. */
    fun writeArtwork(context: Context, song: Song, imageBytes: ByteArray, mimeType: String): Result<Unit> =
        runCatching {
            val tempFile = copyToCache(context, song)
            try {
                val audioFile = AudioFileIO.read(tempFile)
                val tag = audioFile.tagOrCreateAndSetDefault
                val artwork = StandardArtwork().apply {
                    binaryData = imageBytes
                    this.mimeType = mimeType
                    pictureType = PictureTypes.DEFAULT_ID
                }
                tag.setField(artwork)
                audioFile.commit()
                copyBack(context, tempFile, song)
            } finally {
                tempFile.delete()
            }
        }.onFailure { Log.e(TAG, "writeArtwork falló para ${song.displayName}", it) }

    /**
     * Renombra el archivo apuntado por [song] a [newDisplayName] (con extensión incluida).
     *
     * Algunos proveedores de SAF (notoriamente el de MIUI/Xiaomi) no implementan el rename
     * directo y tiran UnsupportedOperationException. Si eso pasa y se pasó [parentDir], se
     * hace un fallback: crear un documento nuevo con el nombre deseado, copiar el contenido,
     * y borrar el original.
     */
    fun renameFile(
        context: Context,
        song: Song,
        newDisplayName: String,
        parentDir: DocumentFile? = null
    ): Result<Unit> = runCatching {
        val doc = DocumentFile.fromSingleUri(context, song.uri)
            ?: throw IOException("No se encontró el DocumentFile para ${song.uri}")

        val directRenameOk = try {
            doc.renameTo(newDisplayName)
        } catch (e: Exception) {
            Log.w(TAG, "renameTo directo falló para ${song.displayName}, se prueba fallback", e)
            false
        }

        if (directRenameOk) return@runCatching

        if (parentDir == null) {
            throw IOException("El proveedor no soporta rename directo y no hay carpeta padre para el fallback")
        }

        val mimeType = context.contentResolver.getType(song.uri) ?: "application/octet-stream"
        val newDoc = parentDir.createFile(mimeType, newDisplayName)
            ?: throw IOException("No se pudo crear $newDisplayName (fallback copiar+borrar)")

        context.contentResolver.openInputStream(song.uri)?.use { input ->
            context.contentResolver.openOutputStream(newDoc.uri)?.use { output ->
                input.copyTo(output)
            } ?: throw IOException("No se pudo abrir $newDisplayName para escritura")
        } ?: throw IOException("No se pudo abrir ${song.uri} para lectura")

        if (!doc.delete()) {
            Log.w(TAG, "Se copió a $newDisplayName pero no se pudo borrar el original ${song.displayName}")
        }
    }.onFailure { Log.e(TAG, "renameFile falló para ${song.displayName} -> $newDisplayName", it) }
}
