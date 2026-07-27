package com.exodust.tagedit

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.io.IOException

/**
 * jaudiotagger trabaja con java.io.File, no con Uri de SAF. Por eso cada operación
 * copia el contenido a un archivo temporal en cacheDir, opera ahí, y si escribe,
 * vuelca el resultado de vuelta al Uri original. Pensado para llamarse siempre
 * desde un hilo de background (Dispatchers.IO), nunca desde el hilo principal.
 */
object TagIO {

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

    /** Lee los tags de [song]. Devuelve null si falla (formato no soportado, archivo corrupto, etc). */
    fun readTags(context: Context, song: Song): TagFields? {
        val tempFile = try {
            copyToCache(context, song)
        } catch (e: IOException) {
            return null
        }
        return try {
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
        } catch (e: Exception) {
            null
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Escribe únicamente los campos no nulos de [fields] en [song] (los null se dejan
     * como están). Devuelve true si se pudo escribir y persistir de vuelta al Uri original.
     */
    fun writeTags(context: Context, song: Song, fields: TagFields): Boolean {
        val tempFile = try {
            copyToCache(context, song)
        } catch (e: IOException) {
            return false
        }
        return try {
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
            true
        } catch (e: Exception) {
            false
        } finally {
            tempFile.delete()
        }
    }

    /** Renombra el archivo apuntado por [song] a [newDisplayName] (con extensión incluida). */
    fun renameFile(context: Context, song: Song, newDisplayName: String): Boolean {
        val doc = DocumentFile.fromSingleUri(context, song.uri) ?: return false
        return try {
            doc.renameTo(newDisplayName)
        } catch (e: Exception) {
            false
        }
    }
}
