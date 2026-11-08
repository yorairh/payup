package com.payup.domain

import kotlin.math.abs

/**
 * All money in the app is an Int number of agorot (1 shekel = 100), so fractions like ₪0.5 stay exact.
 * Text fields always use "." as the decimal separator; "," is accepted while typing.
 */
object Money {
    const val PER_SHEKEL = 100
    private const val MAX_WHOLE_DIGITS = 7

    fun shekels(whole: Int): Int = whole * PER_SHEKEL

    /** "12" -> 1200, "0.5" -> 50, "0,5" -> 50, ".25" -> 25. Blank or invalid -> null. */
    fun parseAgorot(text: String): Int? {
        val normalized = text.trim().replace(',', '.')
        if (normalized.isEmpty() || normalized == ".") return null
        if (!Regex("""\d{0,$MAX_WHOLE_DIGITS}(\.\d{0,2})?""").matches(normalized)) return null
        val whole = normalized.substringBefore('.').ifEmpty { "0" }.toInt()
        val fraction = normalized.substringAfter('.', "").padEnd(2, '0').toInt()
        return whole * PER_SHEKEL + fraction
    }

    /** Keeps what the user typed as long as it can become an amount: digits, one separator, two decimals. */
    fun sanitizeInput(text: String): String {
        val builder = StringBuilder()
        var separatorSeen = false
        var decimals = 0
        var wholeDigits = 0
        for (c in text.replace(',', '.')) {
            when {
                c == '.' && !separatorSeen -> { separatorSeen = true; builder.append(c) }
                c.isDigit() && separatorSeen && decimals < 2 -> { decimals++; builder.append(c) }
                c.isDigit() && !separatorSeen && wholeDigits < MAX_WHOLE_DIGITS -> { wholeDigits++; builder.append(c) }
            }
        }
        return builder.toString()
    }

    /** 1200 -> "12", 50 -> "0.5", 1825 -> "18.25". Sign is not included. */
    fun format(agorot: Int): String {
        val value = abs(agorot)
        val whole = value / PER_SHEKEL
        val fraction = value % PER_SHEKEL
        return when {
            fraction == 0 -> whole.toString()
            fraction % 10 == 0 -> "$whole.${fraction / 10}"
            else -> "$whole.${fraction.toString().padStart(2, '0')}"
        }
    }

    /** Rounds down to a whole shekel (non-negative amounts only, as counted money always is). */
    fun floorToShekel(agorot: Int): Int = agorot - agorot % PER_SHEKEL
}
