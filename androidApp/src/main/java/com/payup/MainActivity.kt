package com.payup

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.payup.ui.settings.SettingsScreen
import com.payup.ui.theme.palette
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.payup.ui.cashout.CashOutScreen
import com.payup.ui.common.WithLatestCurrency
import com.payup.ui.deal.DealScreen
import com.payup.ui.friends.FriendsScreen
import com.payup.ui.history.HistoryScreen
import com.payup.ui.home.HomeScreen
import com.payup.ui.leaderboard.LeaderboardScreen
import com.payup.ui.result.ResultScreen
import com.payup.ui.setup.SetupScreen
import com.payup.ui.table.TableScreen
import com.payup.ui.theme.PayUpTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = (application as PayUpApp).themeStore
        setContent {
            val theme by store.theme.collectAsStateWithLifecycle()
            val dark = theme.palette().dark
            // Status/navigation bar icons must flip with the theme (dark icons on the white theme).
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
            }
            PayUpTheme(theme) {
                PayUpNavHost()
            }
        }
    }
}

private val gameIdArg = navArgument("gameId") { type = NavType.LongType }

@androidx.compose.runtime.Composable
private fun PayUpNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNewGame = { nav.navigate("setup") },
                onContinue = { nav.navigate("table/$it") },
                onHistory = { nav.navigate("history") },
                onLeaderboard = { nav.navigate("leaderboard") },
                onFriends = { nav.navigate("friends") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable("leaderboard") {
            WithLatestCurrency { LeaderboardScreen(onBack = { nav.popBackStack() }) }
        }
        composable("setup") {
            SetupScreen(
                onBack = { nav.popBackStack() },
                onStarted = { gameId, deal ->
                    nav.navigate(if (deal) "deal/$gameId" else "table/$gameId") { popUpTo("home") }
                },
            )
        }
        composable("deal/{gameId}", listOf(gameIdArg)) { entry ->
            val gameId = entry.arguments!!.getLong("gameId")
            DealScreen(onContinue = { nav.navigate("table/$gameId") { popUpTo("home") } })
        }
        composable("table/{gameId}", listOf(gameIdArg)) { entry ->
            val gameId = entry.arguments!!.getLong("gameId")
            TableScreen(
                onBack = { nav.popBackStack() },
                onEndGame = { nav.navigate("cashout/$gameId") },
            )
        }
        composable("cashout/{gameId}", listOf(gameIdArg)) { entry ->
            val gameId = entry.arguments!!.getLong("gameId")
            CashOutScreen(
                onBack = { nav.popBackStack() },
                onFinished = { nav.navigate("result/$gameId?celebrate=true") { popUpTo("home") } },
            )
        }
        composable(
            "result/{gameId}?celebrate={celebrate}",
            listOf(gameIdArg, navArgument("celebrate") { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            ResultScreen(onDone = { nav.popBackStack() })
        }
        composable("history") {
            HistoryScreen(
                onBack = { nav.popBackStack() },
                onOpenGame = { nav.navigate("result/$it") },
            )
        }
        composable("friends") {
            FriendsScreen(onBack = { nav.popBackStack() })
        }
    }
}
