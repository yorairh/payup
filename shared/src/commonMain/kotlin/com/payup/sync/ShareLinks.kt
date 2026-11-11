package com.payup.sync

/** The web addresses of shared games (docs/SHARED_GAMES.md). */
object ShareLinks {
    private const val SITE = "https://payup-ae6fc.web.app"

    /** What the host hands out: opens the game for watching. */
    fun viewer(gameId: String) = "$SITE/g/$gameId"

    /** Opens the game for editing; the key after the # is never sent to a server. */
    fun host(gameId: String, hostKey: String) = "$SITE/h/$gameId#$hostKey"
}
