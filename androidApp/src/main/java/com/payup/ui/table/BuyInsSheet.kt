package com.payup.ui.table

import com.payup.resources.Res
import com.payup.resources.delete
import com.payup.resources.edit_buy_in
import com.payup.resources.edit_buy_in_title
import com.payup.resources.no_buy_ins
import com.payup.resources.put_in
import com.payup.resources.remove_buy_in
import com.payup.resources.remove_buy_in_message
import com.payup.resources.remove_buy_in_title
import com.payup.resources.remove_player
import com.payup.resources.remove_player_message
import com.payup.resources.remove_player_min
import com.payup.resources.remove_player_title
import com.payup.resources.save
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.payup.data.BuyInEntity
import com.payup.data.PlayerWithBuyIns
import com.payup.ui.common.AmountDialog
import com.payup.ui.common.ConfirmDialog
import com.payup.ui.common.money
import com.payup.ui.theme.Gold
import java.text.DateFormat
import java.util.Date

/** Every buy-in of one player, with edit and delete, plus removing the whole player. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyInsSheet(
    player: PlayerWithBuyIns,
    canRemovePlayer: Boolean,
    onEdit: (buyInId: Long, amount: Int) -> Unit,
    onDelete: (buyInId: Long) -> Unit,
    onRemovePlayer: () -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<BuyInEntity?>(null) }
    var deleting by remember { mutableStateOf<BuyInEntity?>(null) }
    var removingPlayer by remember { mutableStateOf(false) }
    val buyIns = remember(player.buyIns) { player.buyIns.sortedWith(compareBy({ it.createdAt }, { it.id })) }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(player.player.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(Res.string.put_in, money(player.totalIn)), color = Gold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            if (buyIns.isEmpty()) {
                Text(stringResource(Res.string.no_buy_ins), style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(buyIns, key = { it.id }) { buyIn ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(money(buyIn.amount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(timeFormat.format(Date(buyIn.createdAt)), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { editing = buyIn }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.edit_buy_in))
                        }
                        IconButton(onClick = { deleting = buyIn }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.remove_buy_in))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { removingPlayer = true },
                enabled = canRemovePlayer,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(Res.string.remove_player)) }
            if (!canRemovePlayer) {
                Text(
                    stringResource(Res.string.remove_player_min),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    editing?.let { buyIn ->
        AmountDialog(
            title = stringResource(Res.string.edit_buy_in_title, player.player.name),
            confirmLabel = stringResource(Res.string.save),
            initial = buyIn.amount,
            onConfirm = { onEdit(buyIn.id, it); editing = null },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { buyIn ->
        ConfirmDialog(
            title = stringResource(Res.string.remove_buy_in_title, money(buyIn.amount), player.player.name),
            message = stringResource(Res.string.remove_buy_in_message),
            confirmLabel = stringResource(Res.string.delete),
            onConfirm = { onDelete(buyIn.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
    if (removingPlayer) {
        ConfirmDialog(
            title = stringResource(Res.string.remove_player_title, player.player.name),
            message = stringResource(Res.string.remove_player_message, money(player.totalIn)),
            confirmLabel = stringResource(Res.string.delete),
            onConfirm = { removingPlayer = false; onRemovePlayer() },
            onDismiss = { removingPlayer = false },
        )
    }
}
