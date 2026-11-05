package com.payup.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.payup.R
import com.payup.data.GameWithDetails
import com.payup.ui.common.money
import com.payup.ui.common.signedMoney
import com.payup.ui.theme.Cream
import com.payup.ui.theme.Gold
import com.payup.ui.theme.LoseRed
import com.payup.ui.theme.WinGreen
import java.text.DateFormat
import java.util.Date

/** Everything this game knows about one player: buy-ins, what they finished with, the result, and who they settle with. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDetailsSheet(game: GameWithDetails, playerName: String, onDismiss: () -> Unit) {
    val player = game.players.firstOrNull { it.player.name == playerName } ?: return
    val buyIns = remember(player.buyIns) { player.buyIns.sortedWith(compareBy({ it.createdAt }, { it.id })) }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val net = player.player.net ?: 0
    val netColor: Color = when {
        net > 0 -> WinGreen
        net < 0 -> LoseRed
        else -> Cream
    }
    val pays = game.transfers.filter { it.fromName == playerName }
    val gets = game.transfers.filter { it.toName == playerName }
    val chips = player.player.chips
    val chipValue = game.game.chipValue

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(playerName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(signedMoney(net), color = netColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)

            Section(stringResource(R.string.buy_ins_title))
            if (buyIns.isEmpty()) Text(stringResource(R.string.no_buy_ins), style = MaterialTheme.typography.bodyMedium)
            buyIns.forEach {
                Line(timeFormat.format(Date(it.createdAt)), money(it.amount))
            }
            Line(stringResource(R.string.total_put_in), money(player.totalIn), bold = true, valueColor = Gold)

            Section(stringResource(R.string.final_amount))
            if (chips != null && chipValue != null) {
                Line(stringResource(R.string.chips_at, chips, money(chipValue)), money(player.player.finalAmount ?: 0), bold = true, valueColor = Gold)
            } else {
                Line(stringResource(R.string.final_amount), money(player.player.finalAmount ?: 0), bold = true, valueColor = Gold)
            }

            Section(stringResource(R.string.payments))
            if (pays.isEmpty() && gets.isEmpty()) Text(stringResource(R.string.no_payments), style = MaterialTheme.typography.bodyMedium)
            pays.forEach { Line(stringResource(R.string.pays_to, it.toName), money(it.amount), valueColor = LoseRed) }
            gets.forEach { Line(stringResource(R.string.gets_from, it.fromName), money(it.amount), valueColor = WinGreen) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = valueColor,
        )
    }
}
