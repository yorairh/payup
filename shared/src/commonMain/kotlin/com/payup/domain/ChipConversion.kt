package com.payup.domain

object ChipConversion {

    /**
     * Splits the [pot] by each player's share of all chips, for games where chips have no set value. Players without a
     * chip count make it unknown (everything null), and so does a total of zero chips.
     *
     * Each share is floored to the agora (or to a whole shekel with [roundToWhole]); what is left of the pot goes to the
     * biggest loser among the players who hold chips (lowest result against what they put in), ties to the first in
     * the list. The amounts always add up to the pot.
     */
    fun shares(chips: List<Int?>, totalIn: List<Int>, pot: Int, roundToWhole: Boolean): List<Int?> {
        require(chips.size == totalIn.size)
        val counts = chips.map { it ?: return chips.map { null } }
        val total = counts.sumOf { it.toLong() }
        if (total <= 0) return chips.map { null }
        val exact = counts.map { (pot.toLong() * it / total).toInt() }
        val parts = (if (roundToWhole) exact.map(Money::floorToShekel) else exact).toMutableList()
        val leftover = pot - parts.sum()
        if (leftover != 0) {
            val biggestLoser = counts.indices.filter { counts[it] > 0 }.minByOrNull { exact[it] - totalIn[it] }!!
            parts[biggestLoser] += leftover
        }
        return parts
    }

    /**
     * Converts each player's chip count into money (agorot). Players without a chip count get null.
     *
     * With [roundToWhole], every amount is rounded down to a whole shekel and the fractions taken off are
     * pooled (rounded to the nearest shekel) and given to the biggest loser among the chip-counted players:
     * the one with the lowest result against what they put in ([totalIn]); ties go to the first in the list.
     * When the exact total is a whole number of shekels, the rounded total equals it exactly.
     */
    fun convert(chips: List<Int?>, totalIn: List<Int>, chipValue: Int, roundToWhole: Boolean): List<Int?> {
        require(chips.size == totalIn.size)
        val exact = chips.map { count -> count?.let { it * chipValue } }
        if (!roundToWhole) return exact

        val rounded = exact.map { it?.let(Money::floorToShekel) }.toMutableList()
        val leftover = exact.indices.sumOf { i -> (exact[i] ?: 0) - (rounded[i] ?: 0) }
        val leftoverShekels = (leftover + Money.PER_SHEKEL / 2) / Money.PER_SHEKEL * Money.PER_SHEKEL
        if (leftoverShekels > 0) {
            val biggestLoser = exact.indices
                .filter { exact[it] != null }
                .minByOrNull { exact[it]!! - totalIn[it] }
            if (biggestLoser != null) rounded[biggestLoser] = rounded[biggestLoser]!! + leftoverShekels
        }
        return rounded
    }
}
