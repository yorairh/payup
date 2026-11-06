package com.payup.domain

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class MoneyTest {

    @Test
    fun parsesWholeAndFractions() {
        assertEquals(1200, Money.parseAgorot("12"))
        assertEquals(50, Money.parseAgorot("0.5"))
        assertEquals(50, Money.parseAgorot("0,5"))
        assertEquals(25, Money.parseAgorot(".25"))
        assertEquals(1200, Money.parseAgorot("12."))
        assertEquals(1825, Money.parseAgorot(" 18.25 "))
    }

    @Test
    fun rejectsInvalid() {
        assertNull(Money.parseAgorot(""))
        assertNull(Money.parseAgorot("."))
        assertNull(Money.parseAgorot("12.345"))
        assertNull(Money.parseAgorot("1.2.3"))
        assertNull(Money.parseAgorot("abc"))
    }

    @Test
    fun sanitizeKeepsOneSeparatorAndTwoDecimals() {
        assertEquals("12.34", Money.sanitizeInput("12.345"))
        assertEquals("0.5", Money.sanitizeInput("0,5"))
        assertEquals("1.23", Money.sanitizeInput("1.2.3"))
        assertEquals("50", Money.sanitizeInput("5a0"))
        assertEquals("1234567", Money.sanitizeInput("123456789"))
    }

    @Test
    fun formats() {
        assertEquals("12", Money.format(1200))
        assertEquals("0.5", Money.format(50))
        assertEquals("18.25", Money.format(1825))
        assertEquals("3.05", Money.format(305))
        assertEquals("7", Money.format(-700))
    }

    @Test
    fun formatThenParseRoundTrips() {
        listOf(0, 5, 50, 99, 100, 1825, 123456).forEach {
            assertEquals(it, Money.parseAgorot(Money.format(it)))
        }
    }

    @Test
    fun floorToShekel() {
        assertEquals(3300, Money.floorToShekel(3350))
        assertEquals(3300, Money.floorToShekel(3300))
        assertEquals(0, Money.floorToShekel(99))
    }
}
