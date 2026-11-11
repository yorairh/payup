package com.payup

import androidx.compose.runtime.staticCompositionLocalOf
import com.payup.data.CurrencyStore
import com.payup.data.GameRepository
import com.payup.data.ThemeStore
import com.payup.domain.AppLanguage
import com.payup.sync.GameSync
import com.payup.sync.ShareIds
import com.payup.sync.SharedGameSyncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Everything the screens and ViewModels need from the platform. Each platform builds one when the app starts. */
class AppDependencies(
    val repository: GameRepository,
    val themeStore: ThemeStore,
    val currencyStore: CurrencyStore,
    val appLanguage: AppLanguage,
    /** The release name shown on the home screen, like "0.4.0". */
    val appVersion: String,
    /** The online copy of shared games (Firebase). */
    val gameSync: GameSync,
    /** Makes the ids and host keys of shared games. */
    val shareIds: ShareIds,
) {
    /** Keeps the shared game this device hosts online; lives as long as the app, so it keeps going on every screen. */
    val sharedGames = SharedGameSyncer(repository, gameSync, shareIds, CoroutineScope(SupervisorJob() + Dispatchers.Default))
}

/** Set once at the root of the app; there is no default, so a missing provider fails loudly. */
val LocalAppDependencies = staticCompositionLocalOf<AppDependencies> {
    error("No AppDependencies provided: set LocalAppDependencies at the root of the app")
}
