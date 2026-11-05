package com.payup.data

import android.content.Context
import com.payup.domain.Currency

/** The currency picked for the last game, offered again on the next New game page. */
class CurrencyStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var lastUsed: Currency
        get() = Currency.fromCode(prefs.getString(KEY, null))
        set(value) = prefs.edit().putString(KEY, value.name).apply()

    private companion object {
        const val KEY = "currency"
    }
}
