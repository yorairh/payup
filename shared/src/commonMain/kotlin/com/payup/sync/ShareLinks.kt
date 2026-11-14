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

    /** The two halves of a host link: the game id and the secret host key. */
    data class HostLink(val gameId: String, val hostKey: String)

    /** The id and key inside a pasted host link (`/h/<id>#<key>`), or null if [text] holds none. */
    fun parseHostLink(text: String): HostLink? {
        val trimmed = text.trim()
        val marker = trimmed.indexOf("/h/")
        if (marker < 0) return null
        val afterMarker = trimmed.substring(marker + 3)
        val id = afterMarker.takeWhile(::isIdChar)
        val rest = afterMarker.removePrefix(id)
        if (id.length != ID_LENGTH || !rest.startsWith("#")) return null
        val key = rest.substring(1).takeWhile(::isIdChar)
        return if (key.length == ID_LENGTH) HostLink(id, key) else null
    }

    private const val ID_LENGTH = 22

    private fun isIdChar(c: Char) = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_'

    /** Opens the game for editing; the key after the # is never sent to a server. */
    fun host(gameId: String, hostKey: String) = "$SITE/h/$gameId#$hostKey"
}
