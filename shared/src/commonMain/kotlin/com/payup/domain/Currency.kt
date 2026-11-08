package com.payup.domain

/** Only the symbol changes: amounts are never converted, and every currency is stored in hundredths like the shekel. */
enum class Currency(val symbol: String) {
    ILS("₪"),
    USD("$"),
    EUR("€"),
    GBP("£"),
    RUB("₽"),
    JPY("¥");

    companion object {
        /** Unknown or missing codes fall back to the shekel, which every game before currencies used. */
        fun fromCode(code: String?): Currency = entries.firstOrNull { it.name == code } ?: ILS
    }
}
