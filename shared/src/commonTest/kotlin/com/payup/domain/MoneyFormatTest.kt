package com.payup.domain

import kotlin.test.assertEquals
import kotlin.test.Test

class MoneyFormatTest {
    private val LRI = "⁦"
    private val PDI = "⁩"

    private fun ltr(amount: Int, currency: Currency = Currency.ILS, plus: Boolean = false) =
        MoneyFormat.format(amount, currency, rtl = false, showPlus = plus)

    private fun rtl(amount: Int, currency: Currency = Currency.ILS, plus: Boolean = false) =
        MoneyFormat.format(amount, currency, rtl = true, showPlus = plus)

    @Test fun everyCurrencyUsesItsSymbol() {
        val symbols = mapOf(
            Currency.ILS to "₪", Currency.USD to "$", Currency.EUR to "€",
            Currency.GBP to "£", Currency.RUB to "₽", Currency.JPY to "¥",
        )
        Currency.entries.forEach { c ->
            assertEquals(symbols.getValue(c) + "50", ltr(5000, c))
            assertEquals(LRI + "50 " + symbols.getValue(c) + PDI, rtl(5000, c))
        }
    }

    @Test fun leftToRightSignGoesBeforeTheSymbol() {
        assertEquals("−₪3.25", ltr(-325))
        assertEquals("+$18.5", ltr(1850, Currency.USD, plus = true))
        assertEquals("€18.5", ltr(1850, Currency.EUR))
    }

    @Test fun rightToLeftDrawsTheAmountLeftToRightWithTheSymbolOnTheRight() {
        assertEquals(LRI + "−3.25 ₪" + PDI, rtl(-325))
        assertEquals(LRI + "+18.5 $" + PDI, rtl(1850, Currency.USD, plus = true))
    }

    @Test fun zeroNeverGetsASign() {
        assertEquals("₪0", ltr(0, plus = true))
        assertEquals(LRI + "0 ₪" + PDI, rtl(0, plus = true))
    }

    @Test fun plusIsOptionalAndNeverOnNegatives() {
        assertEquals("₪50", ltr(5000))
        assertEquals("−₪50", ltr(-5000, plus = true))
    }

    @Test fun decimalsMatchMoneyFormat() {
        assertEquals("₪0.5", ltr(50))
        assertEquals("₪18.25", ltr(1825))
    }
}
