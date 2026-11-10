package com.payup.ui.result

import kotlin.test.Test
import kotlin.test.assertEquals

class TransferTextTest {
    @Test
    fun leftToRightArrowPointsRight() {
        assertEquals("⁦⁨Dana⁩ → ⁨Omer⁩⁩", transferText("Dana", "Omer", rtl = false))
    }

    @Test
    fun rightToLeftArrowPointsLeftTowardTheReceiver() {
        // In a right-to-left line the payer is drawn on the right, so the arrow has to point left.
        assertEquals("⁧⁨דדסק⁩ ← ⁨גבמר⁩⁩", transferText("דדסק", "גבמר", rtl = true))
    }

    @Test
    fun namesAreIsolatedSoHebrewNamesInAnEnglishLineKeepTheOrder() {
        // The case from the phone: English app, Hebrew names. Without the isolates the two Hebrew words and the arrow
        // form one right-to-left run and the arrow points at the payer.
        val text = transferText("דדסק", "גבמר", rtl = false)
        assertEquals("⁦⁨דדסק⁩ → ⁨גבמר⁩⁩", text)
    }
}
