package com.payup

import android.app.Application
import android.content.Context
import com.payup.data.AppDatabase
import com.payup.data.CurrencyStore
import com.payup.data.GameRepository
import com.payup.data.ThemeStore
import com.payup.data.build
import com.payup.platform.FirebaseGameSync
import com.payup.platform.androidRandomBytes
import com.payup.sync.ShareIds
import com.russhwolf.settings.SharedPreferencesSettings

class PayUpApp : Application() {
    val dependencies: AppDependencies by lazy {
        // The same "settings" file and keys as before, so the saved theme and currency carry over.
        val settings = SharedPreferencesSettings(getSharedPreferences("settings", Context.MODE_PRIVATE))
        AppDependencies(
            repository = GameRepository(AppDatabase.build(this)),
            themeStore = ThemeStore(settings),
            currencyStore = CurrencyStore(settings),
            appLanguage = AndroidAppLanguage(),
            appVersion = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty(),
            gameSync = FirebaseGameSync(),
            shareIds = ShareIds(androidRandomBytes),
        )
    }
}
