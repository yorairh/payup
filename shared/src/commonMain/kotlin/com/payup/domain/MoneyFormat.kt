package com.payup.domain

/**
 * How an amount is written: ₪50, +$18.5, −€3.25 in left-to-right languages; 50 ₪ and −3.25 ₪ in right-to-left
 * ones (Hebrew, Arabic), where the number comes first. Amounts are agorot, see [Money].
 */
object MoneyFormat {
    private const val MINUS = "−"
    private const val LEFT_TO_RIGHT_ISOLATE = "⁦"
    private const val POP_ISOLATE = "⁩"

    /** [showPlus] marks a positive amount with "+"; zero never gets a sign. */
    fun format(amount: Int, currency: Currency, rtl: Boolean, showPlus: Boolean = false): String {
        val sign = when {
            amount < 0 -> MINUS
            showPlus && amount > 0 -> "+"
            else -> ""
        }
        val number = Money.format(amount)
        return when {
            !rtl -> sign + currency.symbol + number
            // The sign stays inside the number as a left-to-right run; outside it, it would land on the wrong side of the digits.
            sign.isEmpty() -> "$number ${currency.symbol}"
            else -> "$LEFT_TO_RIGHT_ISOLATE$sign$number$POP_ISOLATE ${currency.symbol}"
        }
    }
}
