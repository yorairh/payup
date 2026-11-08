package com.payup.ui.setup

import com.payup.resources.Res
import com.payup.resources.add
import com.payup.resources.add_player
import com.payup.resources.buy_in_amount
import com.payup.resources.currency
import com.payup.resources.no_friends_yet
import com.payup.resources.pick_two
import com.payup.resources.players
import com.payup.resources.random_dealer
import com.payup.resources.random_dealer_hint
import com.payup.resources.setup_hint
import com.payup.resources.setup_title
import com.payup.resources.start_game
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import com.payup.data.CurrencyStore
import com.payup.data.GameRepository
import com.payup.domain.Currency
import com.payup.ui.common.PayUpTopBar
import com.payup.domain.Money
import com.payup.ui.common.MoneyKeyboard
import com.payup.ui.common.payUpViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(onBack: () -> Unit, onStarted: (gameId: Long, deal: Boolean) -> Unit) {
    val vm = payUpViewModel { SetupViewModel(it.repository, it.currencyStore) }
    val currency by vm.currency.collectAsStateWithLifecycle()
    val friends by vm.friends.collectAsStateWithLifecycle()
    val buyIn by vm.buyIn.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val randomDealer by vm.randomDealer.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }

    val canStart = selected.size >= 2 && (Money.parseAgorot(buyIn) ?: 0) > 0
    // Names picked but not (yet) in the friends list still need a row so they can be unticked.
    val allNames = (friends.map { it.name } + selected).distinct()

    fun addTyped() {
        vm.addNew(newName)
        newName = ""
    }

    Scaffold(
        topBar = { PayUpTopBar(stringResource(Res.string.setup_title), onBack) },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                if (selected.size < 2) {
                    Text(
                        stringResource(Res.string.pick_two),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                    )
                }
                Button(
                    onClick = { vm.start(onStarted) },
                    enabled = canStart,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(
                        pluralStringResource(Res.plurals.start_game, selected.size, selected.size),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(padding),
        ) {
            item {
                Text(stringResource(Res.string.currency), style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp).horizontalScroll(rememberScrollState()),
                ) {
                    Currency.entries.forEach {
                        FilterChip(
                            selected = it == currency,
                            onClick = { vm.currency.value = it },
                            label = { Text(it.symbol, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                        )
                    }
                }
                OutlinedTextField(
                    value = buyIn,
                    onValueChange = vm::setBuyIn,
                    label = { Text(stringResource(Res.string.buy_in_amount)) },
                    prefix = { Text(currency.symbol + " ") },
                    singleLine = true,
                    keyboardOptions = MoneyKeyboard,
                    supportingText = { Text(stringResource(Res.string.setup_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.random_dealer), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(Res.string.random_dealer_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = randomDealer, onCheckedChange = { vm.randomDealer.value = it })
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(Res.string.players), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(30) },
                        label = { Text(stringResource(Res.string.add_player)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { addTyped() }),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.padding(4.dp))
                    FilledIconButton(onClick = ::addTyped, enabled = newName.isNotBlank()) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add))
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (allNames.isEmpty()) {
                    Text(stringResource(Res.string.no_friends_yet), style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(allNames, key = { it }) { name ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.toggle(name) }
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(checked = name in selected, onCheckedChange = { vm.toggle(name) })
                    Text(name, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
