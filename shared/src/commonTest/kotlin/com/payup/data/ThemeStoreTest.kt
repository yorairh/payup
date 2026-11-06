package com.payup.data

import com.payup.domain.AppTheme
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeStoreTest {

    @Test
    fun defaultsToOriginal() {
        assertEquals(AppTheme.ORIGINAL, ThemeStore(MapSettings()).theme.value)
    }

    @Test
    fun setIsSavedAndReadBackByANewStore() {
        val settings = MapSettings()
        val store = ThemeStore(settings)
        store.set(AppTheme.DARK)
        assertEquals(AppTheme.DARK, store.theme.value)
        assertEquals(AppTheme.DARK, ThemeStore(settings).theme.value)
    }

    @Test
    fun readsTheNameAlreadySavedByTheOldVersion() {
        // The Android app wrote the enum name under the key "theme" in the "settings" file.
        assertEquals(AppTheme.COLOR_BLIND, ThemeStore(MapSettings("theme" to "COLOR_BLIND")).theme.value)
    }

    @Test
    fun unknownOrBlankNameFallsBackToOriginal() {
        assertEquals(AppTheme.ORIGINAL, ThemeStore(MapSettings("theme" to "NEON")).theme.value)
        assertEquals(AppTheme.ORIGINAL, ThemeStore(MapSettings("theme" to "")).theme.value)
    }
}
