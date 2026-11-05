package com.payup.domain

object AutoFill {

    /**
     * When exactly one "finished with" amount is blank (null), returns its index and the amount that makes the
     * counted total equal [pot]. Returns null when zero or several are blank, or when the others already add
     * up to more than the pot (a negative amount makes no sense).
     */
    fun lastFieldRemainder(pot: Int, amounts: List<Int?>): Pair<Int, Int>? {
        val blanks = amounts.indices.filter { amounts[it] == null }
        if (blanks.size != 1) return null
        val remainder = pot - amounts.sumOf { it ?: 0 }
        return if (remainder >= 0) blanks.single() to remainder else null
    }
}
