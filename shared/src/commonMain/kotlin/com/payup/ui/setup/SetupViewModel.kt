package com.payup.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.payup.data.CurrencyStore
import com.payup.data.GameRepository
import com.payup.domain.Money
import com.payup.sync.SharedGameSyncer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SetupViewModel(
    private val repository: GameRepository,
    private val currencyStore: CurrencyStore,
    private val sharedGames: SharedGameSyncer,
) : ViewModel() {
    /** Starts as the currency of the last game played. */
    val currency = MutableStateFlow(currencyStore.lastUsed)
    val friends = repository.friends.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val buyIn = MutableStateFlow("50")
    /** Selected player names, in the order they were picked (that's the seating order at the table). */
    val selected = MutableStateFlow<List<String>>(emptyList())
    /** Off by default: when on, a card deal picks the dealer before the table opens. */
    val randomDealer = MutableStateFlow(false)
    private var starting = false

    init {
        viewModelScope.launch { repository.lastDefaultBuyIn()?.let { buyIn.value = Money.format(it) } }
    }

    fun setBuyIn(text: String) { buyIn.value = Money.sanitizeInput(text) }

    fun toggle(name: String) = selected.update { if (name in it) it - name else it + name }

    fun addNew(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.addFriend(trimmed) }
        selected.update { if (trimmed in it) it else it + trimmed }
    }

    fun start(onStarted: (gameId: Long, deal: Boolean) -> Unit) {
        val amount = Money.parseAgorot(buyIn.value)?.takeIf { it > 0 } ?: return
        if (starting || selected.value.size < 2) return
        starting = true
        val deal = randomDealer.value
        val chosen = currency.value
        currencyStore.lastUsed = chosen
        viewModelScope.launch {
            // The unfinished game is about to be discarded; its online copy goes with it when that is possible.
            sharedGames.discardActiveGameOnline()
            onStarted(repository.startGame(amount, selected.value, chosen), deal)
        }
    }
}
