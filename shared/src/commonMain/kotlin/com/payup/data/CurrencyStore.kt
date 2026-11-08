package com.payup.data

import com.payup.domain.Currency
import com.russhwolf.settings.Settings

/** The currency picked for the last game, offered again on the next New game page. */
class CurrencyStore(private val settings: Settings) {
    var lastUsed: Currency
        get() = Currency.fromCode(settings.getStringOrNull(KEY))
        set(value) = settings.putString(KEY, value.name)

    private companion object {
        const val KEY = "currency"
    }
}
