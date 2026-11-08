package com.payup.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.payup.data.GameRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FriendsViewModel(private val repository: GameRepository) : ViewModel() {
    val friends = repository.friends.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun add(name: String) = viewModelScope.launch { repository.addFriend(name) }
    fun rename(id: Long, name: String, onTaken: () -> Unit) = viewModelScope.launch {
        if (!repository.renameFriend(id, name)) onTaken()
    }
    fun delete(id: Long) = viewModelScope.launch { repository.deleteFriend(id) }
}
