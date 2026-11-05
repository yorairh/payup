package com.payup.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CashOutSessionTest {
    // Three players, each in for 50.00 (5000 agorot): pot 150.00.
    private val players = listOf(
        CashOutPlayer(1, "Avi", 5000),
        CashOutPlayer(2, "Ben", 5000),
        CashOutPlayer(3, "Gil", 5000),
    )

    private fun fresh(chipValue: Int? = null) =
        CashOutSession.start(players, 15000, savedChipValue = chipValue, savedRoundToWhole = true, lastChipSettings = null)

    private fun CashOutSession.input(id: Long) = state.rows.first { it.playerId == id }.input

    @Test
    fun `last blank player gets what remains of the pot`() {
        val step1 = fresh().typed(1, "80")
        val step2 = step1.session.typed(2, "30").session.fieldDone(2)
        assertEquals("40", step2.session.input(3))
        assertTrue(step2.session.state.rows.first { it.playerId == 3L }.autoFilled)
        assertEquals(listOf<CashOutWrite>(CashOutWrite.FinalAmount(3, 4000)), step2.writes)
        assertEquals(0, step2.session.state.difference)
    }

    @Test
    fun `auto fill happens once and later edits never change it`() {
        var s = fresh().typed(1, "80").session.typed(2, "30").session.fieldDone(2).session
        s = s.typed(1, "70").session.fieldDone(1).session
        assertEquals("40", s.input(3))
        assertEquals(-10 * 100, s.state.difference)
    }

    @Test
    fun `editing the filled player clears the auto filled mark`() {
        var s = fresh().typed(1, "80").session.typed(2, "30").session.fieldDone(2).session
        s = s.typed(3, "45").session
        assertFalse(s.state.rows.first { it.playerId == 3L }.autoFilled)
    }

    @Test
    fun `nothing is filled when the others already exceed the pot`() {
        val s = fresh().typed(1, "100").session.typed(2, "60").session
        val step = s.fieldDone(2)
        assertEquals("", step.session.input(3))
        assertTrue(step.writes.isEmpty())
    }

    @Test
    fun `chip mode derives everyone's money from chips and rounds the fractions`() {
        // Chip worth 0.75: 100 chips = 75.00, 61 chips = 45.75, 40 chips = 30.00; fractions pool to the biggest loser.
        var s = fresh().setChipMode(true).session
        s = s.setChipValue("0.75").session
        s = s.typedChips(1, "100").session
        s = s.typedChips(2, "61").session
        val step = s.typedChips(3, "40")
        s = step.session
        // Whole shekels only: 45.75 rounds down to 45, the pooled 0.75 rounds to 1 and goes to the biggest loser (Gil: 30 vs 50 in).
        assertEquals("75", s.input(1))
        assertEquals("45", s.input(2))
        assertEquals("31", s.input(3))
        assertEquals(CashOutWrite.Chips(3, 40), step.writes.first())
        assertTrue(step.writes.drop(1).all { it is CashOutWrite.FinalAmount })
        assertEquals(3, step.writes.drop(1).size)
    }

    @Test
    fun `chip mode with no chip value blanks every amount`() {
        var s = fresh().setChipMode(true).session
        s = s.typedChips(1, "10").session
        assertEquals("", s.input(1))
        val step = s.typedChips(2, "5")
        assertEquals(listOf<Int?>(null, null, null), step.writes.drop(1).map { (it as CashOutWrite.FinalAmount).amount })
    }

    @Test
    fun `blank chips count as nothing and chip count is digits only`() {
        var s = fresh(chipValue = 100).typedChips(1, "1a2.5").session
        assertEquals("125", s.state.rows.first { it.playerId == 1L }.chipsInput)
        assertEquals("", s.state.rows.first { it.playerId == 2L }.chipsInput)
        assertEquals("125", s.input(1)) // 125 chips at 1.00 each
        s = s.typedChips(1, "").session
        assertEquals("", s.input(1))
    }

    @Test
    fun `chip auto fill only happens when the pot divides into chips`() {
        // Chip worth 1.00 (100): pot 150.00 = 150 chips, divides evenly.
        var s = fresh(chipValue = 100)
        s = s.typedChips(1, "80").session.typedChips(2, "30").session
        val filled = s.fieldDone(2)
        assertEquals("40", filled.session.state.rows.first { it.playerId == 3L }.chipsInput)
        assertEquals(CashOutWrite.Chips(3, 40), filled.writes.first())

        // Chip worth 7.00 (700): 15000 % 700 != 0, so nothing is filled.
        var odd = fresh(chipValue = 700)
        odd = odd.typedChips(1, "10").session.typedChips(2, "5").session
        val none = odd.fieldDone(2)
        assertEquals("", none.session.state.rows.first { it.playerId == 3L }.chipsInput)
        assertTrue(none.writes.isEmpty())
    }

    @Test
    fun `difference is counted minus pot and empty fields count as zero`() {
        var s = fresh()
        assertEquals(-15000, s.state.difference)
        s = s.typed(1, "100").session.typed(2, "60").session
        assertEquals(1000, s.state.difference)
    }

    @Test
    fun `final writes store every player and treat blank as zero`() {
        val s = fresh().typed(1, "80").session
        assertEquals(
            listOf<CashOutWrite>(
                CashOutWrite.FinalAmount(1, 8000),
                CashOutWrite.FinalAmount(2, 0),
                CashOutWrite.FinalAmount(3, 0),
            ),
            s.finalWrites(),
        )
    }

    @Test
    fun `start restores stored amounts, chips and the last game's chip settings`() {
        val stored = listOf(
            CashOutPlayer(1, "Avi", 5000, finalAmount = 8000, chips = 80),
            CashOutPlayer(2, "Ben", 5000),
        )
        val s = CashOutSession.start(stored, 10000, savedChipValue = null, savedRoundToWhole = true, lastChipSettings = 100 to false)
        assertEquals("80", s.input(1))
        assertEquals("80", s.state.rows[0].chipsInput)
        assertEquals("", s.input(2))
        assertFalse(s.state.chips.enabled)
        assertEquals("1", s.state.chips.valueText)
        assertFalse(s.state.chips.roundToWhole)
    }
}
