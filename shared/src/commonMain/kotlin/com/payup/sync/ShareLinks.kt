package com.payup.sync

/** The web addresses of shared games (docs/SHARED_GAMES.md). */
object ShareLinks {
    private const val SITE = "https://payup-ae6fc.web.app"

    /** What the host hands out: opens the game for watching. */
    fun viewer(gameId: String) = "$SITE/g/$gameId"

    /** The game id inside a pasted viewer link (or a bare id), or null if [text] holds none. */
    fun parseViewerId(text: String): String? {
        val trimmed = text.trim()
        val marker = trimmed.indexOf("/g/")
        val candidate = if (marker >= 0) trimmed.substring(marker + 3).takeWhile(::isIdChar) else trimmed
        return candidate.takeIf { it.length == ID_LENGTH && it.all(::isIdChar) }
    }

    private const val ID_LENGTH = 22

    private fun isIdChar(c: Char) = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_'

    /** Opens the game for editing; the key after the # is never sent to a server. */
    fun host(gameId: String, hostKey: String) = "$SITE/h/$gameId#$hostKey"
}
