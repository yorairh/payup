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

data class ChipSettings(val enabled: Boolean = false, val valueText: String = "", val roundToWhole: Boolean = true) {
    val value: Int? get() = Money.parseAgorot(valueText)?.takeIf { it > 0 }
}

data class CashOutState(val rows: List<CashOutRow>, val pot: Int, val chips: ChipSettings) {
    /** Empty fields count as 0 so busted players don't need typing. */
    val counted: Int = rows.sumOf { it.amount ?: 0 }
    /** Positive = more money counted than the pot, negative = money missing. */
    val difference: Int = counted - pot
}
