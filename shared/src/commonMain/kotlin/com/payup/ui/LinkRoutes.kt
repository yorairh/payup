package com.payup.ui

import com.payup.sync.ShareLinks

/** The screen a game link opens: take over for a host link, watch for a viewer link, null for anything else. */
fun routeForLink(url: String): Route? {
    ShareLinks.parseHostLink(url)?.let { return Route.TakeOver(it.gameId, it.hostKey) }
    if (!url.contains("/g/")) return null
    return ShareLinks.parseViewerId(url)?.let { Route.Watch(it) }
}
