package com.payup.ui.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.payup.data.GameRepository
import com.payup.domain.Leaderboard
import com.payup.domain.LeaderboardMetric
import com.payup.domain.Period
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class LeaderboardViewModel(repository: GameRepository) : ViewModel() {
    val metric = MutableStateFlow(LeaderboardMetric.PROFIT)
    val period = MutableStateFlow(Period.ALL)

    val stats = combine(repository.leaderboardResults, metric, period) { results, m, p ->
        Leaderboard.build(results, m, p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
