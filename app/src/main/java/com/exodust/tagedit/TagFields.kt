package com.exodust.tagedit

data class TagFields(
    val artist: String? = null,
    val title: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val track: String? = null,
    val disc: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val comment: String? = null
) {
    /** Devuelve un mapa placeholder -> valor, listo para el motor de patrones. */
    fun toPlaceholderMap(): Map<String, String?> = mapOf(
        "artist" to artist,
        "title" to title,
        "album" to album,
        "albumartist" to albumArtist,
        "track" to track,
        "disc" to disc,
        "year" to year,
        "genre" to genre,
        "comment" to comment
    )

    companion object {
        /** Reconstruye un TagFields a partir de un mapa placeholder -> valor. */
        fun fromPlaceholderMap(map: Map<String, String?>): TagFields = TagFields(
            artist = map["artist"],
            title = map["title"],
            album = map["album"],
            albumArtist = map["albumartist"],
            track = map["track"],
            disc = map["disc"],
            year = map["year"],
            genre = map["genre"],
            comment = map["comment"]
        )
    }
}
