package com.exodust.tagedit

/**
 * Motor de patrones tipo mp3tag: convierte entre tags y nombres de archivo usando
 * un patrón con placeholders, ej: "%track% - %artist% - %title%".
 *
 * Placeholders soportados: %artist% %title% %album% %albumartist% %track% %disc%
 * %year% %genre% %comment%
 */
object PatternEngine {

    private sealed class Token {
        data class Literal(val text: String) : Token()
        data class Placeholder(val name: String) : Token()
    }

    private val TOKEN_REGEX = Regex("%([a-zA-Z]+)%|[^%]+")
    private val INVALID_FILENAME_CHARS = Regex("[/\\\\:*?\"<>|]")

    private fun tokenize(pattern: String): List<Token> {
        val tokens = mutableListOf<Token>()
        for (m in TOKEN_REGEX.findAll(pattern)) {
            val placeholderName = m.groups[1]?.value
            tokens += if (placeholderName != null) {
                Token.Placeholder(placeholderName.lowercase())
            } else {
                Token.Literal(m.value)
            }
        }
        return tokens
    }

    /** Reemplaza cada placeholder del patrón con el valor correspondiente del tag. */
    fun tagsToFilename(pattern: String, tags: TagFields): String {
        val values = tags.toPlaceholderMap()
        val sb = StringBuilder()
        for (token in tokenize(pattern)) {
            when (token) {
                is Token.Literal -> sb.append(token.text)
                is Token.Placeholder -> sb.append(values[token.name].orEmpty())
            }
        }
        return sanitizeFilename(sb.toString().trim())
    }

    /**
     * Intenta parsear [filenameWithoutExtension] contra [pattern] y devuelve los
     * valores extraídos como TagFields, o null si el nombre no matchea la forma
     * del patrón.
     */
    fun filenameToTags(pattern: String, filenameWithoutExtension: String): TagFields? {
        val tokens = tokenize(pattern)
        val placeholderNames = tokens.filterIsInstance<Token.Placeholder>().map { it.name }
        if (placeholderNames.isEmpty()) return null

        val regexBuilder = StringBuilder("^")
        tokens.forEachIndexed { index, token ->
            when (token) {
                is Token.Literal -> regexBuilder.append(Regex.escape(token.text))
                is Token.Placeholder -> {
                    val isLast = index == tokens.lastIndex
                    // No-greedy salvo el último placeholder, que se queda con todo
                    // lo que sobre (evita que un separador dentro del valor corte antes de tiempo).
                    regexBuilder.append(if (isLast) "(.+)" else "(.+?)")
                }
            }
        }
        regexBuilder.append("$")

        val match = runCatching { Regex(regexBuilder.toString()) }
            .getOrNull()
            ?.matchEntire(filenameWithoutExtension)
            ?: return null

        val captured = match.groupValues.drop(1).map { it.trim() }
        val map = placeholderNames.zip(captured).toMap()
        return TagFields.fromPlaceholderMap(map)
    }

    /** Separa "cancion.mp3" en ("cancion", ".mp3"). Si no hay extensión, devuelve ("cancion", ""). */
    fun splitExtension(fileName: String): Pair<String, String> {
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex > 0) {
            fileName.substring(0, dotIndex) to fileName.substring(dotIndex)
        } else {
            fileName to ""
        }
    }

    /** Reemplaza caracteres inválidos para nombres de archivo y recorta espacios/puntos al final. */
    fun sanitizeFilename(name: String): String {
        val cleaned = name.replace(INVALID_FILENAME_CHARS, "_").trimEnd('.', ' ')
        return cleaned.ifBlank { "untitled" }
    }
}
