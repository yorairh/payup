package com.payup.data

import com.payup.domain.Currency
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencyStoreTest {

    @Test
    fun defaultsToTheShekel() {
        assertEquals(Currency.ILS, CurrencyStore(MapSettings()).lastUsed)
    }

    @Test
    fun setIsSavedAndReadBackByANewStore() {
        val settings = MapSettings()
        CurrencyStore(settings).lastUsed = Currency.USD
        assertEquals(Currency.USD, CurrencyStore(settings).lastUsed)
    }

    @Test
    fun readsTheNameAlreadySavedByTheOldVersion() {
        assertEquals(Currency.USD, CurrencyStore(MapSettings("currency" to "USD")).lastUsed)
    }

    @Test
    fun unknownNameFallsBackToTheDefault() {
        assertEquals(Currency.ILS, CurrencyStore(MapSettings("currency" to "XYZ")).lastUsed)
    }
}
