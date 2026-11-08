package com.payup.domain

import kotlin.test.assertEquals
import kotlin.test.Test

class CurrencyTest {
    @Test fun knownCodesRoundTrip() {
        Currency.entries.forEach { assertEquals(it, Currency.fromCode(it.name)) }
    }

    @Test fun unknownOrMissingCodeIsShekel() {
        assertEquals(Currency.ILS, Currency.fromCode(null))
        assertEquals(Currency.ILS, Currency.fromCode("XYZ"))
    }

    @Test fun symbols() {
        assertEquals("$", Currency.USD.symbol)
        assertEquals("₪", Currency.ILS.symbol)
    }
}
