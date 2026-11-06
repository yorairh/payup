package com.payup.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.payup.data.GameRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(repository: GameRepository) : ViewModel() {
    val activeGameId = repository.activeGameId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
