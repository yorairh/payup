package com.payup

import android.app.Application
import com.payup.data.AppDatabase
import com.payup.data.CurrencyStore
import com.payup.data.GameRepository
import com.payup.data.build
import com.payup.data.ThemeStore

class PayUpApp : Application() {
    val repository: GameRepository by lazy { GameRepository(AppDatabase.build(this)) }
    val themeStore: ThemeStore by lazy { ThemeStore(this) }
    val currencyStore: CurrencyStore by lazy { CurrencyStore(this) }
}
