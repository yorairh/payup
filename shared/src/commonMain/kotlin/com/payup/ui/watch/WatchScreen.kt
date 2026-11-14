package com.payup.ui.watch

import com.payup.ui.common.UnreachableMessage
import com.payup.ui.common.StatusMessage
import com.payup.ui.common.CenteredColumn
import com.payup.sync.watchPatiently
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.payup.data.GameRepository
import com.payup.data.GameWithDetails
import com.payup.data.PlayerWithBuyIns
import com.payup.resources.Res
import com.payup.resources.buy_ins_count
import com.payup.resources.watch_cashed_out
import com.payup.resources.watch_live
import com.payup.resources.watch_not_found
import com.payup.resources.watch_offline
import com.payup.resources.watch_title
import com.payup.sync.GameSync
import com.payup.sync.GameSyncException
import com.payup.sync.WatchState
import com.payup.sync.toGameWithDetails
import com.payup.ui.Route
import com.payup.ui.common.PayUpTopBar
import com.payup.ui.common.ProvideCurrency
import com.payup.ui.common.money
import com.payup.ui.common.payUpViewModel
import com.payup.ui.table.PotHeader
import com.payup.ui.theme.Gold
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** What the watch screen shows. */
sealed interface WatchUi {
    data object Loading : WatchUi

    /** No game has this id: a wrong link, or the host removed it. */
    data object NotFound : WatchUi

    /** Nothing is known about the game and the service cannot be reached. */
    data object Unreachable : WatchUi

    /** The game as last heard; [offline] is true while only a saved copy or an old answer is known. */
    data class Live(val game: GameWithDetails, val offline: Boolean) : WatchUi

    /** The game is already on this device (hosted here, or finished and saved): go to its own screen instead. */
    data class Open(val route: Route) : WatchUi
}

/**
 * Follows one shared game read-only. When the game is seen finished it is saved into the history (once) and the screen
 * hands over to the result.
 */
class WatchViewModel(
    private val repository: GameRepository,
    private val sync: GameSync,
    private val shareId: String,
) : ViewModel() {
    private val state = MutableStateFlow<WatchUi>(WatchUi.Loading)
    val ui: StateFlow<WatchUi> = state
    private var job: Job? = null

    init {
        start()
    }

    fun retry() {
        state.value = WatchUi.Loading
        start()
    }

    private fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            val local = repository.gameBySharedId(shareId)
            if (local != null && (local.isHost || local.finished)) {
                state.value = WatchUi.Open(if (local.finished) Route.Result(local.id) else Route.Table(local.id))
                return@launch
            }
            sync.watchPatiently(shareId).collect { heard -> handle(heard) }
        }
    }

    private suspend fun handle(heard: WatchState) {
        when (heard) {
            is WatchState.Found -> {
                val shared = heard.online.game
                if (shared.finished) {
                    val id = repository.saveWatchedGame(shareId, shared)
                    state.value = WatchUi.Open(Route.Result(id))
                } else {
                    state.value = WatchUi.Live(shared.toGameWithDetails(shareId), offline = heard.fromCache)
                }
            }
            WatchState.NotFound -> state.value = WatchUi.NotFound
            is WatchState.Failed -> lostConnection(heard.error)
        }
    }

    private fun lostConnection(error: GameSyncException) {
        val known = state.value
        state.value = when {
            known is WatchUi.Live -> known.copy(offline = true)
            error.kind == GameSyncException.Kind.Denied -> WatchUi.NotFound
            else -> WatchUi.Unreachable
        }
    }
}

@Composable
fun WatchScreen(shareId: String, onBack: () -> Unit, onOpen: (Route) -> Unit) {
    val vm = payUpViewModel { WatchViewModel(it.repository, it.gameSync, shareId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val shown = ui
    LaunchedEffect(shown) { if (shown is WatchUi.Open) onOpen(shown.route) }
    Scaffold(
        topBar = {
            PayUpTopBar(stringResource(Res.string.watch_title), onBack) {
                if (shown is WatchUi.Live) {
                    Text(
                        stringResource(if (shown.offline) Res.string.watch_offline else Res.string.watch_live),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (shown.offline) MaterialTheme.colorScheme.error else Gold,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            when (shown) {
                WatchUi.Loading, is WatchUi.Open -> CenteredColumn { CircularProgressIndicator() }
                WatchUi.NotFound -> CenteredColumn { StatusMessage(stringResource(Res.string.watch_not_found)) }
                WatchUi.Unreachable -> CenteredColumn { UnreachableMessage(vm::retry) }
                is WatchUi.Live -> ProvideCurrency(shown.game.game.currency) { LiveGame(shown.game) }
            }
        }
    }
}

@Composable
private fun LiveGame(game: GameWithDetails) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PotHeader(pot = game.pot, buyIns = game.players.sumOf { it.buyIns.size }) }
        items(game.players, key = { it.player.id }) { PlayerRow(it) }
    }
}

@Composable
private fun PlayerRow(player: PlayerWithBuyIns) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(player.player.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    pluralStringResource(Res.plurals.buy_ins_count, player.buyIns.size, player.buyIns.size),
                    style = MaterialTheme.typography.bodySmall,
                )
                player.player.finalAmount?.let {
                    Text(stringResource(Res.string.watch_cashed_out, money(it)), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(money(player.totalIn), style = MaterialTheme.typography.titleLarge, color = Gold)
        }
    }
}
