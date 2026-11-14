package com.payup.ui.table

import com.payup.ui.common.isRtl
import org.jetbrains.compose.resources.getString
import com.payup.resources.Res
import com.payup.resources.add
import com.payup.resources.add_player
import com.payup.resources.buy_ins_count
import com.payup.resources.custom
import com.payup.resources.custom_buy_in_title
import com.payup.resources.dealer_badge
import com.payup.resources.edit_buy_in
import com.payup.resources.end_game
import com.payup.resources.name_taken
import com.payup.resources.pot
import com.payup.resources.share_failed
import com.payup.resources.share_game
import com.payup.resources.sync_offline
import com.payup.resources.sync_stopped
import com.payup.resources.sync_syncing
import com.payup.resources.table_title
import com.payup.resources.undo_last
import com.payup.resources.undone
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.payup.data.GameRepository
import com.payup.data.GameWithDetails
import com.payup.data.PlayerWithBuyIns
import com.payup.ui.common.AmountDialog
import com.payup.ui.common.LocalCurrency
import com.payup.ui.common.NameDialog
import com.payup.ui.common.PayUpTopBar
import com.payup.ui.common.ProvideCurrency
import com.payup.ui.common.money
import com.payup.ui.common.moneyText
import com.payup.ui.common.signedMoney
import com.payup.ui.common.payUpViewModel
import com.payup.sync.ShareLinks
import com.payup.sync.ShareResult
import com.payup.sync.SyncStatus
import com.payup.ui.theme.Gold
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun TableScreen(gameId: Long, onBack: () -> Unit, onEndGame: () -> Unit) {
    val vm = payUpViewModel { TableViewModel(it.repository, it.sharedGames, gameId) }
    val details by vm.game.collectAsStateWithLifecycle()
    val game = details ?: return
    ProvideCurrency(game.game.currency) { TableContent(vm, game, onBack, onEndGame) }
}

@Composable
private fun TableContent(vm: TableViewModel, game: GameWithDetails, onBack: () -> Unit, onEndGame: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val rtl = isRtl()
    val currency = LocalCurrency.current
    var customFor by remember { mutableStateOf<PlayerWithBuyIns?>(null) }
    var addingPlayer by remember { mutableStateOf(false) }
    var sheetPlayerId by remember { mutableStateOf<Long?>(null) }
    var shareLink by remember { mutableStateOf<String?>(null) }
    val hostKey = game.game.hostKey
    val sharedId = game.game.shareId
    var sharing by remember { mutableStateOf(false) }
    val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()

    val defaultBuyIn = game.game.defaultBuyIn

    Scaffold(
        topBar = {
            PayUpTopBar(stringResource(Res.string.table_title), onBack) {
                if (game.game.shareId != null && game.game.isHost) {
                    when (syncStatus) {
                        SyncStatus.Synced -> {}
                        SyncStatus.Syncing -> Text(stringResource(Res.string.sync_syncing), style = MaterialTheme.typography.labelMedium)
                        SyncStatus.Offline -> Text(stringResource(Res.string.sync_offline), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        SyncStatus.Stopped -> Text(stringResource(Res.string.sync_stopped), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
                IconButton(
                    enabled = !sharing,
                    onClick = {
                        val existing = game.game.shareId
                        if (existing != null) {
                            shareLink = ShareLinks.viewer(existing)
                        } else {
                            sharing = true
                            vm.share { result ->
                                sharing = false
                                when (result) {
                                    is ShareResult.Shared -> shareLink = result.link
                                    ShareResult.Failed -> scope.launch { snackbar.showSnackbar(getString(Res.string.share_failed)) }
                                }
                            }
                        }
                    },
                ) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(Res.string.share_game))
                }
                IconButton(onClick = { addingPlayer = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add_player))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.navigationBarsPadding().padding(16.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        vm.undo { name, amount ->
                            scope.launch {
                                snackbar.showSnackbar(getString(Res.string.undone, moneyText(amount, currency, rtl), name))
                            }
                        }
                    },
                    modifier = Modifier.height(56.dp),
                ) { Text(stringResource(Res.string.undo_last)) }
                Button(onClick = onEndGame, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text(stringResource(Res.string.end_game), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding),
        ) {
            item { PotHeader(pot = game.pot, buyIns = game.players.sumOf { it.buyIns.size }) }
            items(game.players, key = { it.player.id }) { player ->
                PlayerCard(
                    player = player,
                    defaultBuyIn = defaultBuyIn,
                    isDealer = player.player.id == game.game.dealerPlayerId,
                    onBuyIn = { vm.addBuyIn(player.player.id, defaultBuyIn) },
                    onCustom = { customFor = player },
                    onOpen = { sheetPlayerId = player.player.id },
                )
            }
        }
    }

    // Looked up by id so the sheet always shows the live data; it disappears once the player is removed.
    sheetPlayerId?.let { id ->
        game.players.firstOrNull { it.player.id == id }?.let { sheetPlayer ->
            BuyInsSheet(
                player = sheetPlayer,
                canRemovePlayer = game.players.size > 2,
                onEdit = vm::editBuyIn,
                onDelete = vm::removeBuyIn,
                onRemovePlayer = { vm.removePlayer(id) },
                onDismiss = { sheetPlayerId = null },
            )
        }
    }

    shareLink?.let { link ->
        ShareSheet(
            link,
            hostLink = if (sharedId != null && hostKey != null) ShareLinks.host(sharedId, hostKey) else null,
            onDismiss = { shareLink = null },
        )
    }

    customFor?.let { player ->
        AmountDialog(
            title = stringResource(Res.string.custom_buy_in_title, player.player.name),
            confirmLabel = stringResource(Res.string.add),
            onConfirm = { vm.addBuyIn(player.player.id, it); customFor = null },
            onDismiss = { customFor = null },
        )
    }
    if (addingPlayer) {
        val takenNames = game.players.map { it.player.name }
        NameDialog(
            title = stringResource(Res.string.add_player),
            confirmLabel = stringResource(Res.string.add),
            onConfirm = { name ->
                addingPlayer = false
                if (name in takenNames) {
                    scope.launch { snackbar.showSnackbar(getString(Res.string.name_taken, name)) }
                } else {
                    vm.addPlayer(name)
                }
            },
            onDismiss = { addingPlayer = false },
        )
    }
}

@Composable
internal fun PotHeader(pot: Int, buyIns: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.pot), style = MaterialTheme.typography.titleMedium)
        AnimatedCounter(text = money(pot), value = pot) {
            Text(it, fontSize = 48.sp, fontWeight = FontWeight.Black, color = Gold)
        }
        Text(pluralStringResource(Res.plurals.buy_ins_count, buyIns, buyIns), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PlayerCard(
    player: PlayerWithBuyIns,
    defaultBuyIn: Int,
    isDealer: Boolean,
    onBuyIn: () -> Unit,
    onCustom: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        player.player.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Hints that tapping the card opens the buy-in list (edit / delete / remove player).
                    if (isDealer) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(Res.string.dealer_badge),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .background(Gold, CircleShape)
                                .size(22.dp)
                                .wrapContentSize(Alignment.Center),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(Res.string.edit_buy_in),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                AnimatedCounter(text = money(player.totalIn), value = player.totalIn) {
                    Text(it, style = MaterialTheme.typography.titleMedium, color = Gold)
                }
                Text(
                    pluralStringResource(Res.plurals.buy_ins_count, player.buyIns.size, player.buyIns.size),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onCustom) { Text(stringResource(Res.string.custom)) }
            Spacer(Modifier.width(4.dp))
            Button(onClick = onBuyIn, modifier = Modifier.height(52.dp)) {
                Text(signedMoney(defaultBuyIn), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Rolls the number up when it grows and down when it shrinks (undo). */
@Composable
private fun AnimatedCounter(text: String, value: Int, content: @Composable (String) -> Unit) {
    AnimatedContent(
        targetState = text to value,
        transitionSpec = {
            val up = targetState.second > initialState.second
            (slideInVertically { if (up) it else -it } togetherWith slideOutVertically { if (up) -it else it })
                .using(SizeTransform(clip = false))
        },
        label = "counter",
    ) { (shown, _) -> content(shown) }
}
