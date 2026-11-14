package com.payup.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class LinkRoutesTest {
    private val id = "AbCdEfGhIjKlMnOpQrSt-_"
    private val key = "ZyXwVuTsRqPoNmLkJiHg-_"

    @Test
    fun viewer_link_opens_the_watch_screen() =
        assertEquals(Route.Watch(id), routeForLink("https://payup.web.app/g/$id"))

    @Test
    fun host_link_opens_the_take_over_screen() =
        assertEquals(Route.TakeOver(id, key), routeForLink("https://payup.web.app/h/$id#$key"))

    @Test
    fun anything_else_opens_nothing() {
        assertEquals(null, routeForLink("https://payup.web.app/"))
        assertEquals(null, routeForLink("https://payup.web.app/g/short"))
        assertEquals(null, routeForLink("https://payup.web.app/h/$id"))
        assertEquals(null, routeForLink(id))
    }
}
