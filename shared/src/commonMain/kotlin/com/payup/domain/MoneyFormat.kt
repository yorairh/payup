package com.payup.domain

/**
 * How an amount is written: ₪50, +$18.5, −€3.25 in left-to-right languages; 50 ₪ and −3.25 ₪ in right-to-left
 * ones (Hebrew, Arabic), where the symbol comes after the number and sits on its right. Amounts are agorot, see [Money].
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
            // In right-to-left text the amount is still drawn left to right, so the symbol sits to the right of the number
            // and the sign to its left; the isolate keeps the surrounding right-to-left text from reordering it.
            else -> "$LEFT_TO_RIGHT_ISOLATE$sign$number ${currency.symbol}$POP_ISOLATE"
        }
    }
}
