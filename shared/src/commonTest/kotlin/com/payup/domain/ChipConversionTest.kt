package com.payup.domain

import kotlin.test.assertEquals
import kotlin.test.Test

class ChipConversionTest {

    private val halfShekel = 50
    private val fiveShekels = 500

    @Test
    fun exactKeepsFractions() {
        val result = ChipConversion.convert(listOf(37), listOf(2000), halfShekel, roundToWhole = false)
        assertEquals(listOf(1850), result)
    }

    @Test
    fun wholeChipValuesNeedNoRounding() {
        val result = ChipConversion.convert(listOf(12, 3), listOf(5000, 5000), fiveShekels, roundToWhole = true)
        assertEquals(listOf(6000, 1500), result)
    }

    @Test
    fun roundingGivesTheHalvesToTheBiggestLoser() {
        // Dan 67 chips = 33.5, Avi 67 = 33.5, Noa 66 = 33 (total ₪100). Noa put in the most, so she lost the most.
        val result = ChipConversion.convert(
            chips = listOf(67, 67, 66),
            totalIn = listOf(2000, 3000, 5000),
            chipValue = halfShekel,
            roundToWhole = true,
        )
        assertEquals(listOf(3300, 3300, 3400), result)
        assertEquals(10000, result.sumOf { it!! })
    }

    @Test
    fun biggestLoserTieGoesToFirstInList() {
        val result = ChipConversion.convert(listOf(1, 1), listOf(100, 100), halfShekel, roundToWhole = true)
        assertEquals(listOf(100, 0), result)
    }

    @Test
    fun singleHalfIsRoundedUpForTheLoser() {
        // Only one half-shekel left over: pooled 0.5 rounds to ₪1 and goes to the biggest loser.
        val result = ChipConversion.convert(listOf(3, 2), listOf(1000, 0), halfShekel, roundToWhole = true)
        assertEquals(listOf(200, 100), result)
    }

    @Test
    fun playersWithoutChipsAreLeftAlone() {
        val result = ChipConversion.convert(listOf(null, 67, 67), listOf(9000, 5000, 3000), halfShekel, roundToWhole = true)
        assertEquals(listOf(null, 3400, 3300), result)
    }

    @Test
    fun noChipsAtAll() {
        assertEquals(listOf(null, null), ChipConversion.convert(listOf(null, null), listOf(0, 0), halfShekel, true))
    }

    @Test
    fun sharesSplitThePotByChipRatio() {
        val result = ChipConversion.shares(listOf(100, 50, 50), listOf(5000, 5000, 5000), 15000, roundToWhole = false)
        assertEquals(listOf(7500, 3750, 3750), result)
    }

    @Test
    fun sharesRoundDownToShekelsAndGiveTheLeftoverToTheBiggestLoser() {
        // ₪100 pot, three equal stacks: ₪33.33 each floors to ₪33, the missing ₪1 goes to Noa, who put in the most.
        val result = ChipConversion.shares(listOf(1, 1, 1), listOf(3333, 3333, 3334), 10000, roundToWhole = true)
        assertEquals(listOf(3300, 3300, 3400), result)
        assertEquals(10000, result.sumOf { it!! })
    }

    @Test
    fun sharesNeverGiveTheLeftoverToAPlayerWithoutChips() {
        val result = ChipConversion.shares(listOf(0, 1, 2), listOf(8000, 1000, 1000), 10000, roundToWhole = false)
        assertEquals(listOf(0, 3334, 6666), result)
    }

    @Test
    fun sharesAreUnknownUntilEveryoneHasACount() {
        assertEquals(listOf<Int?>(null, null), ChipConversion.shares(listOf(10, null), listOf(5000, 5000), 10000, roundToWhole = true))
    }

    @Test
    fun sharesAreUnknownWhenThereAreNoChips() {
        assertEquals(listOf<Int?>(null, null), ChipConversion.shares(listOf(0, 0), listOf(5000, 5000), 10000, roundToWhole = true))
    }

    @Test
    fun oneHolderOfAllChipsGetsTheWholePot() {
        assertEquals(listOf(0, 10000), ChipConversion.shares(listOf(0, 40), listOf(5000, 5000), 10000, roundToWhole = true))
    }
}
