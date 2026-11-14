package com.payup.ui.cashout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.payup.data.GameRepository
import com.payup.domain.CashOutPlayer
import com.payup.domain.CashOutSession
import com.payup.domain.CashOutStep
import com.payup.domain.Currency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Forwards the screen's events to [CashOutSession] and stores what it asks to be stored. */
class CashOutViewModel(private val repository: GameRepository, private val gameId: Long) : ViewModel() {
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
                        savedByShare = details.game.chipsByShare,
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

    fun setByShare(byShare: Boolean) = apply { it.setByShare(byShare) }

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
