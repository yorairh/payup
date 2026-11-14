package com.payup.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.payup.data.GameRepository
import com.payup.sync.SharedGameSyncer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: GameRepository, private val sharedGames: SharedGameSyncer) : ViewModel() {
    val games = repository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Deletes the game (and its online copy, if this device hosts it); [onNeedsConnection] runs if that needs a connection. */
    fun delete(gameId: Long, onNeedsConnection: () -> Unit) = viewModelScope.launch {
        if (!sharedGames.deleteGame(gameId)) onNeedsConnection()
    }
}
