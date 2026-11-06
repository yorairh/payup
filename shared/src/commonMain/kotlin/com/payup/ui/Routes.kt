package com.payup.ui

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/** The screens of the app. The back stack is a list of these, saved and restored with the app state. */
@Serializable
sealed interface Route : NavKey {
    @Serializable data object Home : Route
    @Serializable data object Settings : Route
    @Serializable data object Leaderboard : Route
    @Serializable data object Setup : Route
    @Serializable data class Deal(val gameId: Long) : Route
    @Serializable data class Table(val gameId: Long) : Route
    @Serializable data class CashOut(val gameId: Long) : Route
    @Serializable data class Result(val gameId: Long, val celebrate: Boolean = false) : Route
    @Serializable data object History : Route
    @Serializable data object Friends : Route
}

/** Tells the saved-state code how to write every [Route] (needed on platforms without reflection). */
val RouteSavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Route.Home::class)
            subclass(Route.Settings::class)
            subclass(Route.Leaderboard::class)
            subclass(Route.Setup::class)
            subclass(Route.Deal::class)
            subclass(Route.Table::class)
            subclass(Route.CashOut::class)
            subclass(Route.Result::class)
            subclass(Route.History::class)
            subclass(Route.Friends::class)
        }
    }
}
