package com.payup.ui.cashout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.payup.R
import com.payup.data.GameRepository
import com.payup.domain.CashOutPlayer
import com.payup.domain.CashOutRow
import com.payup.domain.CashOutSession
import com.payup.domain.CashOutState
import com.payup.domain.CashOutStep
import com.payup.domain.Currency
import com.payup.domain.ChipSettings
import com.payup.domain.Money
import com.payup.ui.common.ConfirmDialog
import com.payup.ui.common.LocalCurrency
import com.payup.ui.common.MoneyKeyboard
import com.payup.ui.common.PayUpTopBar
import com.payup.ui.common.ProvideCurrency
import com.payup.ui.common.money
import com.payup.ui.common.payUpViewModel
import com.payup.ui.theme.Gold
import com.payup.ui.theme.LoseRed
import com.payup.ui.theme.WinGreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Forwards the screen's events to [CashOutSession] and stores what it asks to be stored. */
class CashOutViewModel(private val repository: GameRepository, handle: SavedStateHandle) : ViewModel() {
    private val gameId: Long = checkNotNull(handle["gameId"])
    private val session = MutableStateFlow<CashOutSession?>(null)
    private var finishing = false

    val state = session.map { it?.state }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _currency = MutableStateFlow<Currency?>(null)
    val currency: StateFlow<Currency?> = _currency

    init {
        viewModelScope.launch {
            repository.game(gameId).filterNotNull().collect { details ->
                _currency.value = Currency.fromCode(details.game.currency)
                val players = details.players.map {
                    CashOutPlayer(it.player.id, it.player.name, it.totalIn, it.player.finalAmount, it.player.chips)
                }
                val existing = session.value
                session.value = if (existing != null) {
                    existing.withTable(players, details.pot)
                } else {
                    // First load: restore what was typed before the app was closed, and the chip settings (or the last game's).
                    CashOutSession.start(
                        players, details.pot, details.game.chipValue, details.game.roundToWhole, repository.lastChipSettings(),
                    )
                }
            }
        }
    }

    fun onInput(playerId: Long, text: String) = apply { it.typed(playerId, text) }

    fun onChipsInput(playerId: Long, text: String) = apply { it.typedChips(playerId, text) }

    fun setChipMode(enabled: Boolean) = apply { it.setChipMode(enabled) }

    fun setChipValue(text: String) = apply { it.setChipValue(text) }

    fun setRoundToWhole(round: Boolean) = apply { it.setRoundToWhole(round) }

    fun onFieldDone(playerId: Long) = apply { it.fieldDone(playerId) }

    fun payUp(voidDifference: Boolean, onDone: () -> Unit) {
        if (finishing) return
        finishing = true
        viewModelScope.launch {
            // Every field (including empty = 0) is stored as part of settling.
            repository.finishGame(gameId, session.value?.finalWrites().orEmpty(), voidDifference)
            onDone()
        }
    }

    private fun apply(event: (CashOutSession) -> CashOutStep) {
        val step = event(session.value ?: return)
        session.value = step.session
        if (step.writes.isNotEmpty()) viewModelScope.launch { repository.saveCashOut(gameId, step.writes) }
    }
}

@Composable
fun CashOutScreen(onBack: () -> Unit, onFinished: () -> Unit) {
    val vm = payUpViewModel { repo, handle -> CashOutViewModel(repo, handle) }
    val state by vm.state.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val current = state ?: return
    ProvideCurrency(currency ?: return) { CashOutContent(vm, current, onBack, onFinished) }
}

@Composable
private fun CashOutContent(vm: CashOutViewModel, current: CashOutState, onBack: () -> Unit, onFinished: () -> Unit) {
    var confirmVoid by remember { mutableStateOf(false) }
    val difference = current.difference
    val chipMode = current.chips.enabled

    Scaffold(
        topBar = { PayUpTopBar(stringResource(R.string.cashout_title), onBack) },
        bottomBar = {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp)
            ) {
                AnimatedVisibility(visible = difference != 0) {
                    MismatchBanner(difference = difference, onVoid = { confirmVoid = true })
                }
                Button(
                    onClick = { vm.payUp(voidDifference = false, onDone = onFinished) },
                    enabled = difference == 0 && current.pot > 0,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) {
                    Text(stringResource(R.string.payup), fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding),
        ) {
            item { CountHeader(current) }
            item {
                ChipCard(
                    settings = current.chips,
                    onEnabled = vm::setChipMode,
                    onValue = vm::setChipValue,
                    onRound = vm::setRoundToWhole,
                )
            }
            itemsIndexed(current.rows, key = { _, row -> row.playerId }) { index, row ->
                PlayerFinalRow(
                    row = row,
                    chipMode = chipMode,
                    isLast = index == current.rows.lastIndex,
                    onInput = { vm.onInput(row.playerId, it) },
                    onChipsInput = { vm.onChipsInput(row.playerId, it) },
                    onDone = { vm.onFieldDone(row.playerId) },
                )
            }
        }
    }

    if (confirmVoid) {
        val amount = money(abs(difference))
        ConfirmDialog(
            title = stringResource(R.string.void_title, amount),
            message = if (difference < 0) {
                stringResource(R.string.void_missing_message, amount)
            } else {
                stringResource(R.string.void_extra_message, amount)
            },
            confirmLabel = stringResource(R.string.payup),
            onConfirm = {
                confirmVoid = false
                vm.payUp(voidDifference = true, onDone = onFinished)
            },
            onDismiss = { confirmVoid = false },
        )
    }
}

@Composable
private fun CountHeader(state: CashOutState) {
    val color by animateColorAsState(
        when {
            state.difference == 0 -> WinGreen
            state.difference > 0 -> Gold
            else -> MaterialTheme.colorScheme.onBackground
        },
        label = "countColor",
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.cashout_hint), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.counted_of_pot, money(state.counted), money(state.pot)),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (state.pot == 0) 0f else (state.counted.toFloat() / state.pot).coerceIn(0f, 1f) },
            color = color,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ChipCard(
    settings: ChipSettings,
    onEnabled: (Boolean) -> Unit,
    onValue: (String) -> Unit,
    onRound: (Boolean) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🪙", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.count_in_chips),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = settings.enabled, onCheckedChange = onEnabled)
            }
            AnimatedVisibility(visible = settings.enabled) {
                Column {
                    OutlinedTextField(
                        value = settings.valueText,
                        onValueChange = onValue,
                        label = { Text(stringResource(R.string.chip_value)) },
                        prefix = { Text(LocalCurrency.current.symbol + " ") },
                        singleLine = true,
                        isError = settings.value == null,
                        keyboardOptions = MoneyKeyboard,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.round_to_whole), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(if (settings.roundToWhole) R.string.round_hint else R.string.exact_hint),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(checked = settings.roundToWhole, onCheckedChange = onRound)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerFinalRow(
    row: CashOutRow,
    chipMode: Boolean,
    isLast: Boolean,
    onInput: (String) -> Unit,
    onChipsInput: (String) -> Unit,
    onDone: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val moneyField = @Composable { modifier: Modifier ->
        OutlinedTextField(
            value = row.input,
            onValueChange = onInput,
            label = { Text(stringResource(R.string.final_amount)) },
            placeholder = { Text("0") },
            prefix = { Text(LocalCurrency.current.symbol + " ") },
            singleLine = true,
            supportingText = if (row.autoFilled) {
                { Text(stringResource(R.string.auto_filled), color = Gold) }
            } else null,
            keyboardOptions = MoneyKeyboard.copy(imeAction = if (isLast) ImeAction.Done else ImeAction.Next),
            // Done only hides the keyboard by default; clearing focus makes it count as "finished with this field".
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = modifier.doneOnBlur(onDone),
        )
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        if (!chipMode) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                PlayerName(row, Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                moneyField(Modifier.width(150.dp))
            }
        } else {
            Column(Modifier.padding(16.dp)) {
                PlayerName(row, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = row.chipsInput,
                        onValueChange = onChipsInput,
                        label = { Text(stringResource(R.string.chips)) },
                        placeholder = { Text("0") },
                        suffix = { Text("🪙") },
                        singleLine = true,
                        supportingText = if (row.autoFilled) {
                            { Text(stringResource(R.string.auto_filled), color = Gold) }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.weight(1f).doneOnBlur(onDone),
                    )
                    // The money is only shown: it always follows from the chips.
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.worth), style = MaterialTheme.typography.bodySmall)
                        Text(
                            money(row.amount ?: 0),
                            color = Gold,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                        )
                    }
                }
            }
        }
    }
}

/** Calls [onDone] when this field loses focus after having had it (that's when auto-fill looks). */
@Composable
private fun Modifier.doneOnBlur(onDone: () -> Unit): Modifier {
    var hadFocus by remember { mutableStateOf(false) }
    return onFocusChanged { focus ->
        if (hadFocus && !focus.isFocused) onDone()
        hadFocus = focus.isFocused
    }
}

@Composable
private fun PlayerName(row: CashOutRow, modifier: Modifier) {
    Column(modifier) {
        Text(row.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.put_in, money(row.totalIn)), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MismatchBanner(difference: Int, onVoid: () -> Unit) {
    val amount = money(abs(difference))
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (difference < 0) stringResource(R.string.missing, amount) else stringResource(R.string.extra, amount),
                    color = LoseRed,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(R.string.mismatch_hint), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onVoid) { Text(stringResource(R.string.void_amount, amount)) }
        }
    }
}
