package com.payup.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoFillTest {

    @Test
    fun oneBlankGetsTheRemainder() {
        assertEquals(2 to 30, AutoFill.lastFieldRemainder(200, listOf(120, 50, null, 0)))
    }

    @Test
    fun twoBlanksDoNothing() {
        assertNull(AutoFill.lastFieldRemainder(200, listOf(120, 80, null, null)))
    }

    @Test
    fun noBlanksDoNothing() {
        assertNull(AutoFill.lastFieldRemainder(200, listOf(120, 80)))
    }

    @Test
    fun negativeRemainderDoesNothing() {
        assertNull(AutoFill.lastFieldRemainder(100, listOf(120, null)))
    }

    @Test
    fun remainderOfZeroIsFilled() {
        assertEquals(1 to 0, AutoFill.lastFieldRemainder(200, listOf(120, null, 80)))
    }

    @Test
    fun explicitZeroCountsAsFilled() {
        // Noa typed 0, so Yael is the only blank one.
        assertEquals(3 to 0, AutoFill.lastFieldRemainder(200, listOf(120, 80, 0, null)))
    }

    @Test
    fun fractionalRemainder() {
        // Pot ₪100, others ₪66.5 -> ₪33.5 left
        assertEquals(1 to 3350, AutoFill.lastFieldRemainder(10000, listOf(6650, null)))
    }
}
