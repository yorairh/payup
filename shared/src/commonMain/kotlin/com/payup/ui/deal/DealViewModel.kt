package com.payup.ui.deal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.payup.data.GameRepository
import com.payup.domain.Dealer
import com.payup.domain.DealtCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DealState(
    val names: List<String>,
    /** The cards in dealing order; empty when the dealer was already picked earlier (app restarted). */
    val dealt: List<DealtCard>,
    val dealerIndex: Int,
)

class DealViewModel(private val repository: GameRepository, private val gameId: Long) : ViewModel() {
    val state = MutableStateFlow<DealState?>(null)

    init {
        viewModelScope.launch {
            val details = repository.game(gameId).filterNotNull().first()
            val players = details.players
            val names = players.map { it.player.name }
            val existing = players.indexOfFirst { it.player.id == details.game.dealerPlayerId }
            if (existing >= 0) {
                state.value = DealState(names, emptyList(), existing)
            } else {
                val dealt = Dealer.deal(players.size)
                val dealerIndex = dealt.last().playerIndex
                repository.setDealer(gameId, players[dealerIndex].player.id)
                state.value = DealState(names, dealt, dealerIndex)
            }
        }
    }
}
