package com.payup

import androidx.compose.runtime.staticCompositionLocalOf
import com.payup.data.CurrencyStore
import com.payup.data.GameRepository
import com.payup.data.ThemeStore
import com.payup.domain.AppLanguage

/** Everything the screens and ViewModels need from the platform. Each platform builds one when the app starts. */
class AppDependencies(
    val repository: GameRepository,
    val themeStore: ThemeStore,
    val currencyStore: CurrencyStore,
    val appLanguage: AppLanguage,
)

/** Set once at the root of the app; there is no default, so a missing provider fails loudly. */
val LocalAppDependencies = staticCompositionLocalOf<AppDependencies> {
    error("No AppDependencies provided: set LocalAppDependencies at the root of the app")
}
