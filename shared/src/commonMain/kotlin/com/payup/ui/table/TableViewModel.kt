package com.payup.ui.table

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.payup.data.GameRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TableViewModel(private val repository: GameRepository, private val gameId: Long) : ViewModel() {
    val game = repository.game(gameId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addBuyIn(playerId: Long, amount: Int) = viewModelScope.launch { repository.addBuyIn(playerId, amount) }

    fun addPlayer(name: String) = viewModelScope.launch {
        val defaultBuyIn = game.value?.game?.defaultBuyIn ?: return@launch
        repository.addPlayer(gameId, name, defaultBuyIn)
    }

    fun editBuyIn(buyInId: Long, amount: Int) = viewModelScope.launch { repository.updateBuyIn(buyInId, amount) }

    fun removeBuyIn(buyInId: Long) = viewModelScope.launch { repository.removeBuyIn(buyInId) }

    fun removePlayer(playerId: Long) = viewModelScope.launch { repository.removePlayer(playerId) }

    fun undo(onUndone: (name: String, amount: Int) -> Unit) = viewModelScope.launch {
        repository.undoLastBuyIn(gameId)?.let { (name, amount) -> onUndone(name, amount) }
    }
}
