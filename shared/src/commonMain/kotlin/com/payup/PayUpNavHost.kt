package com.payup

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.payup.ui.Route
import com.payup.ui.RouteSavedStateConfiguration
import com.payup.ui.cashout.CashOutScreen
import com.payup.ui.common.WithLatestCurrency
import com.payup.ui.deal.DealScreen
import com.payup.ui.friends.FriendsScreen
import com.payup.ui.history.HistoryScreen
import com.payup.ui.home.HomeScreen
import com.payup.ui.leaderboard.LeaderboardScreen
import com.payup.ui.result.ResultScreen
import com.payup.ui.settings.SettingsScreen
import com.payup.ui.setup.SetupScreen
import com.payup.ui.table.TableScreen

/** Screens fade into each other, as they always have. */
private val fade = ContentTransform(fadeIn(tween(700)), fadeOut(tween(700)))

/** Moves to [route] and drops every screen between it and Home, so Back from it goes Home. */
private fun NavBackStack<NavKey>.replaceAboveHome(route: Route) {
    while (size > 1) removeAt(lastIndex)
    add(route)
}

private fun NavBackStack<NavKey>.pop() {
    if (size > 1) removeAt(lastIndex)
}

/** The screens and how they lead to each other, the same on every platform. The web passes [bindToBrowser] to tie the back stack to the browser history. */
@Composable
fun PayUpNavHost(bindToBrowser: @Composable (NavBackStack<NavKey>) -> Unit = {}) {
    val back = rememberNavBackStack(RouteSavedStateConfiguration, Route.Home)
    bindToBrowser(back)
    NavDisplay(
        backStack = back,
        onBack = { back.pop() },
        transitionSpec = { fade },
        popTransitionSpec = { fade },
        predictivePopTransitionSpec = { fade },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Route.Home> {
                HomeScreen(
                    onNewGame = { back.add(Route.Setup) },
                    onContinue = { back.add(Route.Table(it)) },
                    onHistory = { back.add(Route.History) },
                    onLeaderboard = { back.add(Route.Leaderboard) },
                    onFriends = { back.add(Route.Friends) },
                    onSettings = { back.add(Route.Settings) },
                )
            }
            entry<Route.Settings> {
                SettingsScreen(onBack = { back.pop() })
            }
            entry<Route.Leaderboard> {
                WithLatestCurrency { LeaderboardScreen(onBack = { back.pop() }) }
            }
            entry<Route.Setup> {
                SetupScreen(
                    onBack = { back.pop() },
                    onStarted = { gameId, deal ->
                        back.replaceAboveHome(if (deal) Route.Deal(gameId) else Route.Table(gameId))
                    },
                )
            }
            entry<Route.Deal> { key ->
                DealScreen(key.gameId, onContinue = { back.replaceAboveHome(Route.Table(key.gameId)) })
            }
            entry<Route.Table> { key ->
                TableScreen(
                    key.gameId,
                    onBack = { back.pop() },
                    onEndGame = { back.add(Route.CashOut(key.gameId)) },
                )
            }
            entry<Route.CashOut> { key ->
                CashOutScreen(
                    key.gameId,
                    onBack = { back.pop() },
                    onFinished = { back.replaceAboveHome(Route.Result(key.gameId, celebrate = true)) },
                )
            }
            entry<Route.Result> { key ->
                ResultScreen(key.gameId, key.celebrate, onDone = { back.pop() })
            }
            entry<Route.History> {
                HistoryScreen(
                    onBack = { back.pop() },
                    onOpenGame = { back.add(Route.Result(it)) },
                )
            }
            entry<Route.Friends> {
                FriendsScreen(onBack = { back.pop() })
            }
        },
    )
}
