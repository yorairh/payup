package com.payup

import android.app.Application
import android.content.Context
import com.payup.data.AppDatabase
import com.payup.data.CurrencyStore
import com.payup.data.GameRepository
import com.payup.data.ThemeStore
import com.payup.data.build
import com.russhwolf.settings.SharedPreferencesSettings

class PayUpApp : Application() {
    val repository: GameRepository by lazy { GameRepository(AppDatabase.build(this)) }

    // The same "settings" file and keys as before, so the saved theme and currency carry over.
    private val settings by lazy { SharedPreferencesSettings(getSharedPreferences("settings", Context.MODE_PRIVATE)) }
    val themeStore: ThemeStore by lazy { ThemeStore(settings) }
    val currencyStore: CurrencyStore by lazy { CurrencyStore(settings) }
}
