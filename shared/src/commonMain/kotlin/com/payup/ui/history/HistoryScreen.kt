package com.payup.ui.history

import com.payup.platform.LocalPlatform
import com.payup.resources.Res
import com.payup.resources.delete
import com.payup.resources.delete_game_message
import com.payup.resources.delete_game_title
import com.payup.resources.history
import com.payup.resources.history_empty
import com.payup.resources.players_count
import com.payup.resources.pot_value
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.payup.data.GameRepository
import com.payup.data.GameWithDetails
import com.payup.ui.common.ConfirmDialog
import com.payup.ui.common.PayUpTopBar
import com.payup.ui.common.ProvideCurrency
import com.payup.ui.common.money
import com.payup.ui.common.payUpViewModel
import com.payup.ui.common.signedMoney
import com.payup.data.results
import com.payup.domain.Outcome
import com.payup.ui.theme.Gold
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenGame: (Long) -> Unit) {
    val vm = payUpViewModel { HistoryViewModel(it.repository) }
    val games by vm.games.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Long?>(null) }

    Scaffold(topBar = { PayUpTopBar(stringResource(Res.string.history), onBack) }) { padding ->
        val list = games ?: return@Scaffold
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.history_empty), textAlign = TextAlign.Center)
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding),
        ) {
            items(list, key = { it.game.id }) { game ->
                HistoryCard(game, onClick = { onOpenGame(game.game.id) }, onDelete = { toDelete = game.game.id })
            }
        }
    }

    toDelete?.let { id ->
        ConfirmDialog(
            title = stringResource(Res.string.delete_game_title),
            message = stringResource(Res.string.delete_game_message),
            confirmLabel = stringResource(Res.string.delete),
            onConfirm = { vm.delete(id); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun HistoryCard(game: GameWithDetails, onClick: () -> Unit, onDelete: () -> Unit) {
    val dates = LocalPlatform.current.dates
    val date = remember(game.game.id) { dates.dateTime(game.game.endedAt ?: game.game.startedAt) }
    val topWinner = remember(game) { game.results().firstOrNull { it.outcome == Outcome.TopWinner } }

    ProvideCurrency(game.game.currency) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.clickable(onClick = onClick),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(date, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        pluralStringResource(Res.plurals.players_count, game.players.size, game.players.size) +
                            " · " + stringResource(Res.string.pot_value, money(game.pot)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    topWinner?.let {
                        Text(
                            "👑 ${it.name} ${signedMoney(it.net)}",
                            color = Gold,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.delete))
                }
            }
        }
    }
}
