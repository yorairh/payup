package com.payup.domain

import org.junit.Assert.assertEquals
import org.junit.Test

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
