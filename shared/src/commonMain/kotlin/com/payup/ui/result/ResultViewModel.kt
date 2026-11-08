package com.payup.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.payup.data.GameRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class ResultViewModel(repository: GameRepository, gameId: Long, val celebrate: Boolean) : ViewModel() {
    val game = repository.game(gameId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
