package com.payup.ui

import com.payup.sync.ShareLinks

/**
 * The screen a game link opens: take over for a host link, watch for a viewer link, null for anything else. Pasted text
 * may also be just a game id ([allowBareId]); an address that was opened may not.
 */
fun routeForLink(text: String, allowBareId: Boolean = false): Route? {
    ShareLinks.parseHostLink(text)?.let { return Route.TakeOver(it.gameId, it.hostKey) }
    if (!allowBareId && !text.contains("/g/")) return null
    return ShareLinks.parseViewerId(text)?.let { Route.Watch(it) }
}
