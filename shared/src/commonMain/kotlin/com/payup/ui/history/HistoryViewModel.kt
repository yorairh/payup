package com.payup.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.payup.data.GameRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: GameRepository) : ViewModel() {
    val games = repository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun delete(gameId: Long) = viewModelScope.launch { repository.deleteGame(gameId) }
}
