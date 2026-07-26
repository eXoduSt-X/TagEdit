package com.exodust.tagedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PatternEngineTest {

    @Test
    fun `tagsToFilename reemplaza placeholders en orden`() {
        val tags = TagFields(artist = "Soda Stereo", title = "De Musica Ligera", track = "01")
        val result = PatternEngine.tagsToFilename("%track% - %artist% - %title%", tags)
        assertEquals("01 - Soda Stereo - De Musica Ligera", result)
    }

    @Test
    fun `tagsToFilename deja vacio un placeholder sin valor`() {
        val tags = TagFields(artist = "Soda Stereo", title = null)
        val result = PatternEngine.tagsToFilename("%artist% - %title%", tags)
        assertEquals("Soda Stereo -", result)
    }

    @Test
    fun `tagsToFilename sanitiza caracteres invalidos`() {
        val tags = TagFields(artist = "AC/DC", title = "T.N.T: Live?")
        val result = PatternEngine.tagsToFilename("%artist% - %title%", tags)
        assertEquals("AC_DC - T.N.T_ Live_", result)
    }

    @Test
    fun `filenameToTags extrae valores segun el patron`() {
        val tags = PatternEngine.filenameToTags(
            "%track% - %artist% - %title%",
            "01 - Soda Stereo - De Musica Ligera"
        )
        assertEquals("01", tags?.track)
        assertEquals("Soda Stereo", tags?.artist)
        assertEquals("De Musica Ligera", tags?.title)
    }

    @Test
    fun `filenameToTags devuelve null si el nombre no matchea el patron`() {
        val tags = PatternEngine.filenameToTags(
            "%track% - %artist% - %title%",
            "un nombre cualquiera sin separadores"
        )
        assertNull(tags)
    }

    @Test
    fun `filenameToTags con placeholder en medio no se come el separador`() {
        // El artista tiene un guion propio; el ultimo placeholder debe quedarse
        // con todo el resto sin cortar de mas.
        val tags = PatternEngine.filenameToTags(
            "%artist% - %title%",
            "Sui Generis - Instituciones - Vivo"
        )
        assertEquals("Sui Generis", tags?.artist)
        assertEquals("Instituciones - Vivo", tags?.title)
    }

    @Test
    fun `splitExtension separa nombre y extension`() {
        val (base, ext) = PatternEngine.splitExtension("cancion.mp3")
        assertEquals("cancion", base)
        assertEquals(".mp3", ext)
    }

    @Test
    fun `splitExtension sin punto devuelve extension vacia`() {
        val (base, ext) = PatternEngine.splitExtension("cancion_sin_extension")
        assertEquals("cancion_sin_extension", base)
        assertEquals("", ext)
    }
}

