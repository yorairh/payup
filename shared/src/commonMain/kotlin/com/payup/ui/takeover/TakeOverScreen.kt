package com.payup.ui.takeover

import com.payup.ui.common.ConfirmDialog
import com.payup.ui.common.UnreachableMessage
import com.payup.ui.common.StatusMessage
import com.payup.ui.common.CenteredColumn
import com.payup.sync.watchPatiently
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.payup.LocalAppDependencies
import com.payup.data.GameRepository
import com.payup.data.GameWithDetails
import com.payup.resources.Res
import com.payup.resources.replaced_message
import com.payup.resources.replaced_ok
import com.payup.resources.replaced_title
import com.payup.resources.takeover_action
import com.payup.resources.takeover_body
import com.payup.resources.takeover_discard_message
import com.payup.resources.takeover_discard_title
import com.payup.resources.takeover_invalid
import com.payup.resources.takeover_title
import com.payup.resources.takeover_watch
import com.payup.resources.watch_not_found
import com.payup.sync.GameSync
import com.payup.sync.GameSyncException
import com.payup.sync.SharedGame
import com.payup.sync.WatchState
import com.payup.sync.toGameWithDetails
import com.payup.ui.common.PayUpTopBar
import com.payup.ui.common.ProvideCurrency
import com.payup.ui.common.money
import com.payup.ui.common.payUpViewModel
import com.payup.ui.theme.Gold
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** What the take-over screen shows. */
sealed interface TakeOverUi {
    data object Loading : TakeOverUi

    data object NotFound : TakeOverUi

    data object Unreachable : TakeOverUi

    /** The key was refused: the link is old or wrong. */
    data object Invalid : TakeOverUi

    /** The game, and whether taking over would discard an unfinished game on this device. */
    data class Ready(val shared: SharedGame, val game: GameWithDetails, val discardsActiveGame: Boolean) : TakeOverUi

    data object Working : TakeOverUi

    /** Leave this screen: [hostedGameId] is the local id of the game now hosted here, or null to just watch. */
    data class Done(val hostedGameId: Long?) : TakeOverUi
}

/** Offers to become the host of a shared game, using the key from its host link. */
class TakeOverViewModel(
    private val repository: GameRepository,
    private val sync: GameSync,
    private val shareId: String,
    private val hostKey: String,
) : ViewModel() {
    private val state = MutableStateFlow<TakeOverUi>(TakeOverUi.Loading)
    val ui: StateFlow<TakeOverUi> = state
    private var job: Job? = null

    init {
        start()
    }

    fun retry() {
        state.value = TakeOverUi.Loading
        start()
    }

    private fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            sync.watchPatiently(shareId).collect { heard ->
                val current = state.value
                if (current is TakeOverUi.Working || current is TakeOverUi.Done) return@collect
                state.value = when (heard) {
                    is WatchState.Found -> {
                        val game = heard.online.game
                        // A finished game has no host to take over: it is simply watched (and saved).
                        if (game.finished) TakeOverUi.Done(null)
                        else TakeOverUi.Ready(game, game.toGameWithDetails(shareId), repository.hasActiveGame())
                    }
                    WatchState.NotFound -> TakeOverUi.NotFound
                    is WatchState.Failed -> TakeOverUi.Unreachable
                }
            }
        }
    }

    fun takeOver() {
        val game = (state.value as? TakeOverUi.Ready)?.shared ?: return
        state.value = TakeOverUi.Working
        viewModelScope.launch {
            try {
                sync.takeOver(shareId, hostKey)
                state.value = TakeOverUi.Done(repository.adoptHostedGame(shareId, hostKey, game))
            } catch (e: GameSyncException) {
                state.value = if (e.kind == GameSyncException.Kind.Denied) TakeOverUi.Invalid else TakeOverUi.Unreachable
            }
        }
    }
}

@Composable
fun TakeOverScreen(shareId: String, hostKey: String, onBack: () -> Unit, onWatch: () -> Unit, onHosting: (Long) -> Unit) {
    val vm = payUpViewModel { TakeOverViewModel(it.repository, it.gameSync, shareId, hostKey) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val shown = ui
    var confirmDiscard by remember { mutableStateOf(false) }
    LaunchedEffect(shown) {
        if (shown is TakeOverUi.Done) shown.hostedGameId?.let(onHosting) ?: onWatch()
    }
    Scaffold(topBar = { PayUpTopBar(stringResource(Res.string.takeover_title), onBack) }) { padding ->
        CenteredColumn(Modifier.padding(padding)) {
            when (shown) {
                TakeOverUi.Loading, TakeOverUi.Working, is TakeOverUi.Done -> CircularProgressIndicator()
                TakeOverUi.NotFound -> StatusMessage(stringResource(Res.string.watch_not_found))
                TakeOverUi.Invalid -> StatusMessage(stringResource(Res.string.takeover_invalid))
                TakeOverUi.Unreachable -> UnreachableMessage(vm::retry)
                is TakeOverUi.Ready -> ProvideCurrency(shown.game.game.currency) {
                    StatusMessage(stringResource(Res.string.takeover_body))
                    Text(money(shown.game.pot), style = MaterialTheme.typography.displayMedium, color = Gold)
                    Text(shown.game.players.joinToString(", ") { it.player.name }, textAlign = TextAlign.Center)
                    Button(
                        onClick = { if (shown.discardsActiveGame) confirmDiscard = true else vm.takeOver() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(Res.string.takeover_action)) }
                    OutlinedButton(onClick = onWatch, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.takeover_watch))
                    }
                }
            }
        }
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(Res.string.takeover_discard_title),
            message = stringResource(Res.string.takeover_discard_message),
            confirmLabel = stringResource(Res.string.takeover_action),
            onConfirm = { confirmDiscard = false; vm.takeOver() },
            onDismiss = { confirmDiscard = false },
        )
    }
}

/**
 * Shown on any screen when someone else took over a game this device was hosting. Taking the person to the live game
 * as a viewer replaces their local copy; anything they changed while offline and never uploaded is lost.
 */
@Composable
fun HostLostDialog(onWatch: (String) -> Unit) {
    val dependencies = LocalAppDependencies.current
    val lost by dependencies.sharedGames.lostHosting.collectAsStateWithLifecycle()
    val shareId = lost ?: return
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(Res.string.replaced_title)) },
        text = { Text(stringResource(Res.string.replaced_message)) },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    dependencies.repository.deleteHostedGame(shareId)
                    dependencies.sharedGames.acknowledgeLostHosting()
                    onWatch(shareId)
                }
            }) { Text(stringResource(Res.string.replaced_ok)) }
        },
    )
}
