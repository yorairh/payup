package com.payup.domain

data class CashOutRow(
    val playerId: Long,
    val name: String,
    val totalIn: Int,
    /** "Finished with" money as typed (or filled from chips), e.g. "18.5". */
    val input: String,
    val chipsInput: String = "",
    val autoFilled: Boolean = false,
) {
    val amount: Int? get() = Money.parseAgorot(input)
}

/** What was last used to count in chips: the value of one chip (null when chips had no set value), rounding, and the share mode. */
data class SavedChips(val value: Int?, val roundToWhole: Boolean, val byShare: Boolean)

/**
 * Chip counting. [byShare] means chips have no set value: the pot is split by each player's share of all chips, and
 * [valueText] is not used.
 */
data class ChipSettings(
    val enabled: Boolean = false,
    val valueText: String = "",
    val roundToWhole: Boolean = true,
    val byShare: Boolean = false,
) {
    val value: Int? get() = Money.parseAgorot(valueText)?.takeIf { it > 0 }
}

data class CashOutState(val rows: List<CashOutRow>, val pot: Int, val chips: ChipSettings) {
    /** Empty fields count as 0 so busted players don't need typing. */
    val counted: Int = rows.sumOf { it.amount ?: 0 }
    /** Positive = more money counted than the pot, negative = money missing. */
    val difference: Int = counted - pot
}
